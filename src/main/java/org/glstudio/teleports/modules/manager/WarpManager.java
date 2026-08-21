package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.api.Events;
import org.glstudio.teleports.api.event.WarpCreateEvent;
import org.glstudio.teleports.api.event.WarpDeleteEvent;
import org.glstudio.teleports.common.network.packets.InvalidationScope;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.core.util.TeleportNames;
import org.glstudio.teleports.modules.model.DestinationLock;
import org.glstudio.teleports.modules.model.TeleportType;
import org.glstudio.teleports.modules.model.Warp;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class WarpManager {
    private static final Pattern PERMISSION_PATTERN = Pattern.compile("[A-Za-z0-9_]+(\\.[A-Za-z0-9_*]+)*");

    private final GTeleports plugin;

    private volatile CompletableFuture<List<Warp>> warpCache;

    public WarpManager(GTeleports plugin) {
        this.plugin = plugin;
        Failures.log(getWarps(), "WarpManager#preload");
    }

    public CompletableFuture<List<Warp>> getWarps() {
        CompletableFuture<List<Warp>> cached = warpCache;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (warpCache != null) {
                return warpCache;
            }

            CompletableFuture<List<Warp>> pending = plugin.getDatabaseManager().getProvider()
                    .getAllWarps()
                    .thenApply(List::copyOf);

            pending.whenComplete((warps, error) -> {
                if (error != null) {
                    invalidateIfStill(pending);
                }
            });

            warpCache = pending;
            return pending;
        }
    }

    public List<Warp> getCachedWarps() {
        CompletableFuture<List<Warp>> cached = warpCache;

        if (cached != null && cached.isDone() && !cached.isCompletedExceptionally()) {
            return cached.join();
        }

        Failures.log(getWarps(), "WarpManager#warmCache");
        return List.of();
    }

    public CompletableFuture<Warp> getWarp(String name) {
        String normalized = TeleportNames.normalize(name);

        return getWarps().thenApply(warps -> warps.stream()
                .filter(warp -> warp.getName().equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null));
    }

    private boolean permissionLooksWrong(Player player, String permission) {
        if (permission == null || permission.isEmpty()) {
            return false;
        }

        if (!PERMISSION_PATTERN.matcher(permission).matches()) {
            plugin.getMessages().send(player, "errors.invalid-permission", "{permission}", permission);
            return true;
        }

        return false;
    }

    public CompletableFuture<Void> setWarp(Player player, String name, String permission) {
        if (!TeleportNames.isValid(name)) {
            plugin.getMessages().send(player, "errors.invalid-name",
                    "{max}", String.valueOf(TeleportNames.MAX_LENGTH));
            return CompletableFuture.completedFuture(null);
        }

        if (permissionLooksWrong(player, permission)) {
            return CompletableFuture.completedFuture(null);
        }

        String normalized = TeleportNames.normalize(name);
        String server = plugin.getCrossServerManager().currentServer();
        Location location = player.getLocation();

        CompletableFuture<Void> result = getWarp(normalized).thenCompose(existing -> {
            if (existing != null && !plugin.getCrossServerManager().isLocal(existing.getServer())) {
                plugin.getMessages().send(player, "errors.warp-on-other-server",
                        "{name}", normalized, "{server}", String.valueOf(existing.getServer()));
                return CompletableFuture.completedFuture(null);
            }

            WarpCreateEvent event = new WarpCreateEvent(player, normalized, location, existing != null);
            if (!Events.callAndExplain(event, player, event.getCancelMessage())) {
                return CompletableFuture.completedFuture(null);
            }

            Warp warp = new Warp(normalized, location, permission, server);

            if (existing != null) {
                warp.setCost(existing.getCost());
                warp.setCooldown(existing.getCooldown());
                warp.setDelay(existing.getDelay());
                warp.setCreatedAt(existing.getCreatedAt());
            }

            return plugin.getDatabaseManager().getProvider().saveWarp(warp)
                    .thenRun(() -> {
                        invalidateEverywhere();
                        plugin.getMessages().send(player, existing != null ? "warp.updated" : "warp.created",
                                "{name}", normalized);
                    });
        });

        return Failures.guard(plugin, result, "WarpManager#setWarp", player);
    }

    public CompletableFuture<Void> setWarpPermission(Player player, String name, String permission) {
        if (permissionLooksWrong(player, permission)) {
            return CompletableFuture.completedFuture(null);
        }

        String normalized = TeleportNames.normalize(name);

        CompletableFuture<Void> result = getWarp(normalized).thenCompose(existing -> {
            if (existing == null) {
                plugin.getMessages().send(player, "errors.warp-not-found", "{name}", name);
                return CompletableFuture.completedFuture(null);
            }

            Warp updated = new Warp(existing.getName(), existing.getWorld(), existing.getX(), existing.getY(),
                    existing.getZ(), existing.getYaw(), existing.getPitch(), permission,
                    existing.getCreatedAt(), existing.getServer());

            return plugin.getDatabaseManager().getProvider().saveWarp(updated)
                    .thenRun(() -> {
                        invalidateEverywhere();
                        plugin.getMessages().send(player, "warp.permission-set",
                                "{name}", existing.getName(),
                                "{permission}", permission == null || permission.isEmpty() ? "-" : permission);
                    });
        });

        return Failures.guard(plugin, result, "WarpManager#setWarpPermission", player);
    }

    public CompletableFuture<Void> setWarpOverride(Player player, String name, String field, Double value) {
        String normalized = TeleportNames.normalize(name);

        CompletableFuture<Void> result = getWarp(normalized).thenCompose(existing -> {
            if (existing == null) {
                plugin.getMessages().send(player, "errors.warp-not-found", "{name}", name);
                return CompletableFuture.completedFuture(null);
            }

            switch (field.toLowerCase(java.util.Locale.ROOT)) {
                case "cost" -> existing.setCost(value);
                case "cooldown" -> existing.setCooldown(value == null ? null : (int) Math.max(0, value.intValue()));
                case "delay" -> existing.setDelay(value == null ? null : (int) Math.max(0, value.intValue()));
                default -> {
                    plugin.getMessages().send(player, "errors.unknown-warp-field", "{field}", field);
                    return CompletableFuture.completedFuture(null);
                }
            }

            return plugin.getDatabaseManager().getProvider().saveWarp(existing)
                    .thenRun(() -> {
                        invalidateEverywhere();
                        plugin.getMessages().send(player,
                                value == null ? "warp.override-cleared" : "warp.override-set",
                                "{name}", existing.getName(),
                                "{field}", field.toLowerCase(java.util.Locale.ROOT),
                                "{value}", value == null ? "-" : trim(value));
                    });
        });

        return Failures.guard(plugin, result, "WarpManager#setWarpOverride", player);
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    public CompletableFuture<Void> deleteWarp(Player player, String name) {
        String normalized = TeleportNames.normalize(name);

        CompletableFuture<Void> result = getWarp(normalized).thenCompose(existing -> {
            if (existing == null) {
                plugin.getMessages().send(player, "errors.warp-not-found", "{name}", name);
                return CompletableFuture.completedFuture(null);
            }

            WarpDeleteEvent event = new WarpDeleteEvent(player, existing);
            if (!Events.callAndExplain(event, player, event.getCancelMessage())) {
                return CompletableFuture.completedFuture(null);
            }

            return plugin.getDatabaseManager().getProvider().deleteWarp(normalized)
                    .thenAccept(removed -> {
                        if (!removed) {
                            plugin.getMessages().send(player, "errors.warp-not-found", "{name}", name);
                            return;
                        }

                        invalidateEverywhere();
                        plugin.getMessages().send(player, "warp.deleted", "{name}", normalized);
                    });
        });

        return Failures.guard(plugin, result, "WarpManager#deleteWarp", player);
    }

    public CompletableFuture<Boolean> createWarp(String name, Location location, String permission) {
        if (!TeleportNames.isValid(name) || location == null || location.getWorld() == null) {
            return CompletableFuture.completedFuture(false);
        }

        String normalized = TeleportNames.normalize(name);

        return getWarp(normalized).thenCompose(existing -> {
            if (existing != null && !plugin.getCrossServerManager().isLocal(existing.getServer())) {
                return CompletableFuture.completedFuture(false);
            }

            Warp warp = new Warp(normalized, location, permission,
                    plugin.getCrossServerManager().currentServer());

            if (existing != null) {
                warp.setCost(existing.getCost());
                warp.setCooldown(existing.getCooldown());
                warp.setDelay(existing.getDelay());
                warp.setCreatedAt(existing.getCreatedAt());
            }

            return plugin.getDatabaseManager().getProvider().saveWarp(warp)
                    .thenApply(ignored -> {
                        invalidateEverywhere();
                        return true;
                    });
        });
    }

    public CompletableFuture<Boolean> removeWarp(String name) {
        return plugin.getDatabaseManager().getProvider()
                .deleteWarp(TeleportNames.normalize(name))
                .thenApply(removed -> {
                    if (removed) {
                        invalidateEverywhere();
                    }
                    return removed;
                });
    }

    public void invalidateCache() {
        warpCache = null;
    }

    private synchronized void invalidateIfStill(CompletableFuture<List<Warp>> expected) {
        if (warpCache == expected) {
            warpCache = null;
        }
    }

    private void invalidateEverywhere() {
        invalidateCache();
        plugin.getCrossServerManager().broadcastInvalidation(InvalidationScope.WARP, null);
    }

    public void teleportToWarp(Player player, String name) {
        Failures.log(getWarp(name).thenAccept(warp -> {
            if (warp == null) {
                plugin.getMessages().send(player, "errors.warp-not-found", "{name}", name);
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                if (warp.hasPermission() && !player.hasPermission(warp.getPermission())) {
                    plugin.getMessages().send(player, "errors.warp-not-found", "{name}", name);
                    return;
                }

                if (isClosed(player, warp)) {
                    return;
                }

                if (!plugin.getCrossServerManager().isLocal(warp.getServer())) {
                    plugin.getCrossServerManager().sendTo(player, TeleportType.WARP, warp);
                    return;
                }

                Location location = warp.toLocation();
                if (location == null) {
                    plugin.getMessages().send(player, "errors.world-not-found");
                    return;
                }

                plugin.getTeleportManager().requestTeleport(player, location, TeleportType.WARP,
                        warp, warp.getName(), success -> {
                        });
            });
        }), "WarpManager#teleportToWarp");
    }

    public boolean isClosed(Player player, Warp warp) {
        if (player.hasPermission(Permissions.BYPASS_LOCK)) {
            return false;
        }

        DestinationLock warpLock = plugin.getLockManager().find(DestinationLock.WARP, warp.getName());
        if (warpLock != null) {
            plugin.getMessages().send(player, "errors.warp-locked",
                    "{name}", warp.getName(),
                    "{reason}", warpLock.reason() == null ? "-" : warpLock.reason());
            return true;
        }

        DestinationLock serverLock = plugin.getLockManager().find(DestinationLock.SERVER, warp.getServer());
        if (serverLock != null) {
            plugin.getMessages().send(player, "errors.server-locked",
                    "{server}", String.valueOf(warp.getServer()),
                    "{reason}", serverLock.reason() == null ? "-" : serverLock.reason());
            return true;
        }

        return false;
    }

    public void safeDisable() {
        warpCache = null;
    }
}

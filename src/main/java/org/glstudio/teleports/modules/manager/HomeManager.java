package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.api.Events;
import org.glstudio.teleports.api.event.HomeCreateEvent;
import org.glstudio.teleports.api.event.HomeDeleteEvent;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.common.network.packets.InvalidationScope;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.teleports.core.util.TeleportNames;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.TeleportType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class HomeManager {
    public static final int UNLIMITED = -1;

    private static final String LIMIT_PERMISSION = Permissions.HOME_LIMIT;

    private final GTeleports plugin;

    private final ConcurrentHashMap<UUID, CompletableFuture<List<Home>>> homeCache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<UUID, List<String>> shareLabels = new ConcurrentHashMap<>();

    public HomeManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<List<Home>> getHomes(UUID owner) {
        return homeCache.computeIfAbsent(owner, id -> {
            CompletableFuture<List<Home>> pending = plugin.getDatabaseManager().getProvider()
                    .getHomes(id)
                    .thenApply(List::copyOf);

            pending.whenComplete((homes, error) -> {
                if (error != null) {
                    homeCache.remove(id, pending);
                }
            });

            return pending;
        });
    }

    public List<Home> getCachedHomes(UUID owner) {
        CompletableFuture<List<Home>> cached = homeCache.get(owner);

        if (cached != null && cached.isDone() && !cached.isCompletedExceptionally()) {
            return cached.join();
        }

        Failures.log(getHomes(owner), "HomeManager#warmCache");
        return List.of();
    }

    public CompletableFuture<Home> getHome(UUID owner, String name) {
        String normalized = TeleportNames.normalize(name);

        return getHomes(owner).thenApply(homes -> homes.stream()
                .filter(home -> home.getName().equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null));
    }

    public CompletableFuture<Void> setHome(Player player, String name) {
        if (!TeleportNames.isValid(name)) {
            plugin.getMessages().send(player, "errors.invalid-name",
                    "{max}", String.valueOf(TeleportNames.MAX_LENGTH));
            return CompletableFuture.completedFuture(null);
        }

        String normalized = TeleportNames.normalize(name);
        UUID owner = player.getUniqueId();
        Location location = player.getLocation();
        int limit = getHomeLimit(player);

        String server = plugin.getCrossServerManager().currentServer();
        String world = location.getWorld().getName();

        CompletableFuture<Void> result = getHomes(owner).thenCompose(homes -> {
            boolean exists = homes.stream().anyMatch(home -> home.getName().equalsIgnoreCase(normalized));

            if (!exists && limit != UNLIMITED && homes.size() >= limit) {
                plugin.getMessages().send(player, "errors.home-limit-reached",
                        "{limit}", String.valueOf(limit));
                return CompletableFuture.completedFuture(null);
            }

            if (scopedLimitReached(player, homes, normalized, world, server)) {
                return CompletableFuture.completedFuture(null);
            }

            if (exists && !plugin.getConfirmations().confirmed(player, "sethome:" + normalized)) {
                plugin.getMessages().send(player, "home.confirm-overwrite", "{name}", normalized);
                return CompletableFuture.completedFuture(null);
            }

            HomeCreateEvent event = new HomeCreateEvent(player, normalized, location, exists);
            if (!Events.callAndExplain(event, player, event.getCancelMessage())) {
                return CompletableFuture.completedFuture(null);
            }

            Home home = new Home(owner, normalized, location, server);

            return plugin.getDatabaseManager().getProvider().saveHome(home)
                    .thenRun(() -> {
                        invalidateEverywhere(owner);
                        plugin.getMessages().send(player, exists ? "home.updated" : "home.created",
                                "{name}", normalized);
                    });
        });

        return Failures.guard(plugin, result, "HomeManager#setHome", player);
    }

    public CompletableFuture<String> nextAutomaticName(UUID owner) {
        return getHomes(owner).thenApply(homes -> {
            for (int index = 1; ; index++) {
                String candidate = "home-" + index;
                boolean taken = homes.stream().anyMatch(home -> home.getName().equalsIgnoreCase(candidate));

                if (!taken) {
                    return candidate;
                }
            }
        });
    }

    public CompletableFuture<Void> deleteHome(Player player, String name) {
        String normalized = TeleportNames.normalize(name);

        CompletableFuture<Void> result = getHome(player.getUniqueId(), normalized).thenCompose(existing -> {
            if (existing == null) {
                plugin.getMessages().send(player, "errors.home-not-found", "{name}", name);
                return CompletableFuture.completedFuture(null);
            }

            HomeDeleteEvent event = new HomeDeleteEvent(player, existing);
            if (!Events.callAndExplain(event, player, event.getCancelMessage())) {
                return CompletableFuture.completedFuture(null);
            }

            return plugin.getDatabaseManager().getProvider().deleteHome(player.getUniqueId(), existing.getName())
                    .thenRun(() -> {
                        invalidateEverywhere(player.getUniqueId());

                        plugin.getHomeShareManager().forgetHome(player.getUniqueId(), existing.getName());

                        plugin.getMessages().send(player, "home.deleted", "{name}", existing.getName());
                    });
        });

        return Failures.guard(plugin, result, "HomeManager#deleteHome", player);
    }

    public CompletableFuture<Void> setHomeFor(CommandSender staff, UUID owner, String ownerName,
                                              String name, Location location) {
        if (!TeleportNames.isValid(name)) {
            plugin.getMessages().send(staff, "errors.invalid-name",
                    "{max}", String.valueOf(TeleportNames.MAX_LENGTH));
            return CompletableFuture.completedFuture(null);
        }

        String normalized = TeleportNames.normalize(name);

        CompletableFuture<Void> result = getHome(owner, normalized).thenCompose(existing -> {
            Home home = new Home(owner, normalized, location, plugin.getCrossServerManager().currentServer());

            return plugin.getDatabaseManager().getProvider().saveHome(home)
                    .thenRun(() -> {
                        invalidateEverywhere(owner);
                        plugin.getMessages().send(staff,
                                existing != null ? "home.admin-updated" : "home.admin-created",
                                "{name}", normalized, "{player}", ownerName);
                    });
        });

        return Failures.guard(plugin, result, "HomeManager#setHomeFor", staff);
    }

    public CompletableFuture<Void> deleteHomeFor(CommandSender staff, UUID owner, String ownerName, String name) {
        String normalized = TeleportNames.normalize(name);

        CompletableFuture<Void> result = getHome(owner, normalized).thenCompose(existing -> {
            if (existing == null) {
                plugin.getMessages().send(staff, "errors.home-not-found-other",
                        "{name}", name, "{player}", ownerName);
                return CompletableFuture.completedFuture(null);
            }

            return plugin.getDatabaseManager().getProvider().deleteHome(owner, existing.getName())
                    .thenRun(() -> {
                        invalidateEverywhere(owner);
                        plugin.getMessages().send(staff, "home.admin-deleted",
                                "{name}", existing.getName(), "{player}", ownerName);
                    });
        });

        return Failures.guard(plugin, result, "HomeManager#deleteHomeFor", staff);
    }

    public CompletableFuture<Void> renameHome(Player player, String from, String to) {
        if (!TeleportNames.isValid(to)) {
            plugin.getMessages().send(player, "errors.invalid-name",
                    "{max}", String.valueOf(TeleportNames.MAX_LENGTH));
            return CompletableFuture.completedFuture(null);
        }

        UUID owner = player.getUniqueId();
        String oldName = TeleportNames.normalize(from);
        String newName = TeleportNames.normalize(to);

        CompletableFuture<Void> result = getHomes(owner).thenCompose(homes -> {
            Home existing = homes.stream()
                    .filter(home -> home.getName().equalsIgnoreCase(oldName))
                    .findFirst()
                    .orElse(null);

            if (existing == null) {
                plugin.getMessages().send(player, "errors.home-not-found", "{name}", from);
                return CompletableFuture.completedFuture(null);
            }

            if (!oldName.equals(newName)
                    && homes.stream().anyMatch(home -> home.getName().equalsIgnoreCase(newName))) {
                plugin.getMessages().send(player, "errors.home-already-exists", "{name}", newName);
                return CompletableFuture.completedFuture(null);
            }

            Home renamed = new Home(owner, newName, existing.getWorld(), existing.getX(), existing.getY(),
                    existing.getZ(), existing.getYaw(), existing.getPitch(), existing.getCreatedAt(),
                    existing.getServer());

            return plugin.getDatabaseManager().getProvider().saveHome(renamed)
                    .thenCompose(ignored -> plugin.getDatabaseManager().getProvider().deleteHome(owner, oldName))
                    .thenRun(() -> {
                        invalidateEverywhere(owner);
                        plugin.getMessages().send(player, "home.renamed", "{old}", oldName, "{new}", newName);
                    });
        });

        return Failures.guard(plugin, result, "HomeManager#renameHome", player);
    }

    public CompletableFuture<Void> moveHome(Player player, String name) {
        UUID owner = player.getUniqueId();
        String normalized = TeleportNames.normalize(name);
        Location location = player.getLocation();

        CompletableFuture<Void> result = getHome(owner, normalized).thenCompose(existing -> {
            if (existing == null) {
                plugin.getMessages().send(player, "errors.home-not-found", "{name}", name);
                return CompletableFuture.completedFuture(null);
            }

            Home moved = new Home(owner, existing.getName(), location.getWorld().getName(),
                    location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch(), existing.getCreatedAt(),
                    plugin.getCrossServerManager().currentServer());

            return plugin.getDatabaseManager().getProvider().saveHome(moved)
                    .thenRun(() -> {
                        invalidateEverywhere(owner);
                        plugin.getMessages().send(player, "home.moved", "{name}", existing.getName());
                    });
        });

        return Failures.guard(plugin, result, "HomeManager#moveHome", player);
    }

    public CompletableFuture<Boolean> createHome(UUID owner, String name, Location location) {
        if (!TeleportNames.isValid(name) || location == null || location.getWorld() == null) {
            return CompletableFuture.completedFuture(false);
        }

        Home home = new Home(owner, TeleportNames.normalize(name), location,
                plugin.getCrossServerManager().currentServer());

        return plugin.getDatabaseManager().getProvider().saveHome(home)
                .thenApply(ignored -> {
                    invalidateEverywhere(owner);
                    return true;
                });
    }

    public CompletableFuture<Boolean> removeHome(UUID owner, String name) {
        String normalized = TeleportNames.normalize(name);

        return getHome(owner, normalized).thenCompose(existing -> {
            if (existing == null) {
                return CompletableFuture.completedFuture(false);
            }

            return plugin.getDatabaseManager().getProvider().deleteHome(owner, existing.getName())
                    .thenApply(ignored -> {
                        invalidateEverywhere(owner);
                        plugin.getHomeShareManager().forgetHome(owner, existing.getName());
                        return true;
                    });
        });
    }

    public void invalidate(UUID owner) {
        if (owner == null) {
            homeCache.clear();
            return;
        }
        homeCache.remove(owner);
    }

    public void forget(UUID owner) {
        homeCache.remove(owner);
        shareLabels.remove(owner);
    }

    private void invalidateEverywhere(UUID owner) {
        homeCache.remove(owner);
        plugin.getCrossServerManager().broadcastInvalidation(InvalidationScope.HOME, owner);
    }

    public void teleportToHome(Player player, String name) {
        Failures.log(getHome(player.getUniqueId(), name).thenAccept(home -> {
            if (home == null) {
                plugin.getMessages().send(player, "errors.home-not-found", "{name}", name);
                return;
            }

            travel(player, home, home.getName());
        }), "HomeManager#teleportToHome");
    }

    public void teleportToSharedHome(Player player, UUID owner, String ownerName, String name) {
        String normalized = TeleportNames.normalize(name);

        Failures.log(plugin.getHomeShareManager().mayUse(player.getUniqueId(), owner, normalized)
                .thenCompose(allowed -> {
                    if (!allowed) {
                        plugin.getMessages().send(player, "errors.home-not-shared",
                                "{player}", ownerName, "{name}", normalized);
                        return CompletableFuture.completedFuture(null);
                    }

                    return getHome(owner, normalized).thenAccept(home -> {
                        if (home == null) {
                            plugin.getMessages().send(player, "errors.home-not-found-other",
                                    "{name}", name, "{player}", ownerName);
                            return;
                        }

                        travel(player, home, ownerName + ":" + home.getName());
                    });
                }), "HomeManager#teleportToSharedHome");
    }

    public void teleportToHomeOf(Player player, UUID owner, String ownerName, String name) {
        Failures.log(getHome(owner, TeleportNames.normalize(name)).thenAccept(home -> {
            if (home == null) {
                plugin.getMessages().send(player, "errors.home-not-found-other",
                        "{name}", name, "{player}", ownerName);
                return;
            }

            travel(player, home, ownerName + ":" + home.getName());
        }), "HomeManager#teleportToHomeOf");
    }

    public List<String> getCachedShareLabels(UUID invitee) {
        return shareLabels.getOrDefault(invitee, List.of());
    }

    public void refreshShareLabels(UUID invitee) {
        if (!plugin.getHomeShareManager().isEnabled()) {
            return;
        }

        Failures.log(plugin.getHomeShareManager().incoming(invitee).thenAccept(shares ->
                shareLabels.put(invitee, shares.stream()
                        .map(share -> (share.ownerName() == null ? share.owner().toString()
                                : share.ownerName()) + ":" + share.home())
                        .toList())), "HomeManager#refreshShareLabels");
    }

    private void travel(Player player, Home home, String label) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            if (!plugin.getCrossServerManager().isLocal(home.getServer())) {
                plugin.getCrossServerManager().sendTo(player, TeleportType.HOME, home);
                return;
            }

            Location location = home.toLocation();
            if (location == null) {
                plugin.getMessages().send(player, "errors.world-not-found");
                return;
            }

            plugin.getTeleportManager().requestTeleport(player, location, TeleportType.HOME,
                    home, label, success -> {
                    });
        });
    }

    public int getWorldLimit(Player player, String world) {
        return scopedLimit(player, "home-limits.per-world." + world.toLowerCase(Locale.ROOT));
    }

    public int getServerLimit(Player player, String server) {
        return scopedLimit(player, "home-limits.per-server."
                + (server == null ? "unnamed-server" : server.toLowerCase(Locale.ROOT)));
    }

    private int scopedLimit(Player player, String path) {
        if (player.hasPermission(LIMIT_PERMISSION + "unlimited")) {
            return UNLIMITED;
        }

        return plugin.getSettings().isSet(path) ? plugin.getSettings().getInt(path, UNLIMITED) : UNLIMITED;
    }

    private boolean scopedLimitReached(Player player, List<Home> homes, String name,
                                       String world, String server) {
        int worldLimit = getWorldLimit(player, world);

        if (worldLimit != UNLIMITED) {
            long inWorld = homes.stream()
                    .filter(home -> !home.getName().equalsIgnoreCase(name))
                    .filter(home -> world.equalsIgnoreCase(home.getWorld()))
                    .count();

            if (inWorld >= worldLimit) {
                plugin.getMessages().send(player, "errors.home-limit-world",
                        "{limit}", String.valueOf(worldLimit), "{world}", world);
                return true;
            }
        }

        int serverLimit = getServerLimit(player, server);

        if (serverLimit != UNLIMITED) {
            long onServer = homes.stream()
                    .filter(home -> !home.getName().equalsIgnoreCase(name))
                    .filter(home -> server != null && server.equalsIgnoreCase(home.getServer()))
                    .count();

            if (onServer >= serverLimit) {
                plugin.getMessages().send(player, "errors.home-limit-server",
                        "{limit}", String.valueOf(serverLimit), "{server}", String.valueOf(server));
                return true;
            }
        }

        return false;
    }

    public int getHomeLimit(Player player) {
        if (player.hasPermission(LIMIT_PERMISSION + "unlimited")) {
            return UNLIMITED;
        }

        int limit = plugin.getSettings().getInt("home-limits.default", 3);

        for (String tier : new String[]{"vip", "staff", "owner"}) {
            if (!player.hasPermission(LIMIT_PERMISSION + tier)) {
                continue;
            }

            int tierLimit = plugin.getSettings()
                    .getInt("home-limits.permissions." + tier, limit);

            if (tierLimit == UNLIMITED) {
                return UNLIMITED;
            }
            limit = Math.max(limit, tierLimit);
        }

        return limit;
    }

    public void safeDisable() {
        homeCache.clear();
        shareLabels.clear();
    }
}

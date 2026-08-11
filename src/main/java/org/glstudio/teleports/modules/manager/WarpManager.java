package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.network.packets.InvalidationScope;
import org.glstudio.teleports.modules.model.TeleportType;
import org.glstudio.teleports.modules.model.Warp;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class WarpManager {

    private final GTeleports plugin;
    private volatile List<Warp> warpCache;
    private final AtomicBoolean warmingUp = new AtomicBoolean();

    public WarpManager(GTeleports plugin) {
        this.plugin = plugin;
        loadWarps();
    }

    private void loadWarps() {
        plugin.getDatabaseManager().getProvider().getAllWarps().thenAccept(warps -> {
            this.warpCache = warps;
        });
    }

    public CompletableFuture<List<Warp>> getWarps() {
        if (warpCache != null) {
            return CompletableFuture.completedFuture(warpCache);
        }

        return plugin.getDatabaseManager().getProvider().getAllWarps()
                .thenApply(warps -> {
                    warpCache = warps;
                    return warps;
                });
    }

    public List<Warp> getCachedWarps() {
        List<Warp> cached = warpCache;
        if (cached != null) {
            return cached;
        }

        if (warmingUp.compareAndSet(false, true)) {
            getWarps().whenComplete((warps, error) -> warmingUp.set(false));
        }
        return List.of();
    }

    public CompletableFuture<Warp> getWarp(String name) {
        return plugin.getDatabaseManager().getProvider().getWarp(name);
    }

    public CompletableFuture<Void> setWarp(Player player, String name, String permission) {
        return getWarp(name).thenCompose(existingWarp -> {
            Warp warp = new Warp(name, player.getLocation(), permission,
                    plugin.getCrossServerManager().currentServer());

            return plugin.getDatabaseManager().getProvider().saveWarp(warp)
                    .thenRun(() -> {
                        warpCache = null;
                        plugin.getCrossServerManager().broadcastInvalidation(InvalidationScope.WARP, null);
                        String message = plugin.getConfigManager().getLanguageConfig().getString(existingWarp != null ? "warp.updated" : "warp.created");
                        player.sendMessage(message.replace("{name}", name));
                    });
        });
    }

    public CompletableFuture<Void> deleteWarp(Player player, String name) {
        return plugin.getDatabaseManager().getProvider().deleteWarp(name)
                .thenRun(() -> {
                    warpCache = null;
                    plugin.getCrossServerManager().broadcastInvalidation(InvalidationScope.WARP, null);
                    String message = plugin.getConfigManager().getLanguageConfig().getString("warp.deleted");
                    player.sendMessage(message.replace("{name}", name));
                });
    }

    public void invalidateCache() {
        warpCache = null;
    }

    public void teleportToWarp(Player player, String name) {
        getWarp(name).thenAccept(warp -> {
            if (warp == null) {
                String message = plugin.getConfigManager().getLanguageConfig().getString("errors.warp-not-found");
                player.sendMessage(message.replace("{name}", name));
                return;
            }

            if (warp.hasPermission() && !player.hasPermission(warp.getPermission())) {
                String message = plugin.getConfigManager().getLanguageConfig().getString("errors.no-permission");
                player.sendMessage(message);
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.getCrossServerManager().isLocal(warp.getServer())) {
                    plugin.getCrossServerManager().sendTo(player, TeleportType.WARP, warp);
                    return;
                }

                Location location = warp.toLocation();
                if (location == null) {
                    player.sendMessage(plugin.getConfigManager().getLanguageConfig()
                            .getString("errors.world-not-found"));
                    return;
                }

                plugin.getTeleportManager().requestTeleport(player, location, TeleportType.WARP, success -> {
                });
            });
        });
    }

    public void safeDisable() {
        warpCache = null;
    }
}

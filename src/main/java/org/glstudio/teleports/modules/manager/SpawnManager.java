package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.network.packets.InvalidationScope;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.TeleportType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public class SpawnManager {

    private final GTeleports plugin;
    private SpawnLocation spawnCache;

    public SpawnManager(GTeleports plugin) {
        this.plugin = plugin;
        loadSpawn();
    }

    private void loadSpawn() {
        plugin.getDatabaseManager().getProvider().getSpawn().thenAccept(spawn -> {
            this.spawnCache = spawn;
        });
    }

    public CompletableFuture<SpawnLocation> getSpawn() {
        if (spawnCache != null) {
            return CompletableFuture.completedFuture(spawnCache);
        }

        return plugin.getDatabaseManager().getProvider().getSpawn()
                .thenApply(spawn -> {
                    spawnCache = spawn;
                    return spawn;
                });
    }

    public CompletableFuture<Void> setSpawn(Player player) {
        SpawnLocation spawn = new SpawnLocation(player.getLocation(),
                plugin.getCrossServerManager().currentServer());

        return plugin.getDatabaseManager().getProvider().saveSpawn(spawn)
                .thenRun(() -> {
                    spawnCache = spawn;
                    plugin.getCrossServerManager().broadcastInvalidation(InvalidationScope.SPAWN, null);
                    String message = plugin.getConfigManager().getLanguageConfig().getString("spawn.set");
                    player.sendMessage(message);
                });
    }

    public void invalidateCache() {
        spawnCache = null;
    }

    public void teleportToSpawn(Player player) {
        getSpawn().thenAccept(spawn -> {
            if (spawn == null) {
                String message = plugin.getConfigManager().getLanguageConfig().getString("errors.spawn-not-set");
                player.sendMessage(message);
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.getCrossServerManager().isLocal(spawn.getServer())) {
                    plugin.getCrossServerManager().sendTo(player, TeleportType.SPAWN, spawn);
                    return;
                }

                Location location = spawn.toLocation();
                if (location == null) {
                    String message = plugin.getConfigManager().getLanguageConfig()
                            .getString("errors.world-not-found");
                    player.sendMessage(message);
                    return;
                }

                plugin.getTeleportManager().requestTeleport(player, location, TeleportType.SPAWN, success -> {
                });
            });
        });
    }

    public void safeDisable() {
        spawnCache = null;
    }
}

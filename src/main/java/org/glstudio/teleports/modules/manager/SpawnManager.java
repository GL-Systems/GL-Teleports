package org.glstudio.teleports.modules.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.network.packets.InvalidationScope;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.TeleportType;

import java.util.concurrent.CompletableFuture;

/**
 * Holds the server spawn.
 */
public class SpawnManager {
    private final GTeleports plugin;

    private volatile CompletableFuture<SpawnLocation> spawnCache;

    /**
     * Creates the manager.
     */
    public SpawnManager(GTeleports plugin) {
        this.plugin = plugin;
        Futures.log(getSpawn(), "SpawnManager#preload");
    }

    /**
     * Returns the spawn.
     */
    public CompletableFuture<SpawnLocation> getSpawn() {
        CompletableFuture<SpawnLocation> cached = spawnCache;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (spawnCache != null) {
                return spawnCache;
            }

            CompletableFuture<SpawnLocation> pending = plugin.getDatabaseManager().getProvider()
                    .getSpawn();

            pending.whenComplete((spawn, error) -> {
                if (error != null) {
                    invalidateIfStill(pending);
                }
            });

            spawnCache = pending;
            return pending;
        }
    }

    /**
     * Returns the cached spawn.
     */
    public SpawnLocation getCachedSpawn() {
        CompletableFuture<SpawnLocation> cached = spawnCache;

        if (cached != null && cached.isDone() && !cached.isCompletedExceptionally()) {
            return cached.join();
        }

        Futures.log(getSpawn(), "SpawnManager#warmCache");
        return null;
    }

    /**
     * Sets the spawn.
     */
    public CompletableFuture<Void> setSpawn(Player player) {
        SpawnLocation spawn = new SpawnLocation(player.getLocation(),
                plugin.getCrossServerManager().currentServer());

        CompletableFuture<Void> result = plugin.getDatabaseManager().getProvider().saveSpawn(spawn)
                .thenRun(() -> {
                    spawnCache = CompletableFuture.completedFuture(spawn);
                    plugin.getCrossServerManager().broadcastInvalidation(InvalidationScope.SPAWN, null);
                    plugin.getMessages().send(player, "spawn.set");
                });

        return Futures.guard(result, "SpawnManager#setSpawn", player);
    }

    /**
     * Drops the cache.
     */
    public void invalidateCache() {
        spawnCache = null;
    }

    private synchronized void invalidateIfStill(CompletableFuture<SpawnLocation> expected) {
        if (spawnCache == expected) {
            spawnCache = null;
        }
    }

    /**
     * Teleports the to spawn.
     */
    public void teleportToSpawn(Player player) {
        teleportToSpawn(player, false);
    }

    /**
     * Teleports the to spawn.
     */
    public void teleportToSpawn(Player player, boolean instant) {
        Futures.log(getSpawn().thenAccept(spawn -> {
            if (spawn == null) {
                plugin.getMessages().send(player, "errors.spawn-not-set");
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                if (!plugin.getCrossServerManager().isLocal(spawn.getServer())) {
                    plugin.getCrossServerManager().sendTo(player, TeleportType.SPAWN, spawn, instant);
                    return;
                }

                Location location = spawn.toLocation();
                if (location == null) {
                    plugin.getMessages().send(player, "errors.world-not-found");
                    return;
                }

                plugin.getTeleportManager().requestTeleport(player, location, TeleportType.SPAWN,
                        null, plugin.getCrossServerManager().currentServer(), instant, success -> {
                        });
            });
        }), "SpawnManager#teleportToSpawn");
    }

    /**
     * Stops this on shutdown.
     */
    public void safeDisable() {
        spawnCache = null;
    }
}

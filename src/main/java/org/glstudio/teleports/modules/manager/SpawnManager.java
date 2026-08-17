package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.network.packets.InvalidationScope;
import org.glstudio.teleports.core.Failures;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.TeleportType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public class SpawnManager {
    private final GTeleports plugin;

    private volatile CompletableFuture<SpawnLocation> spawnCache;

    public SpawnManager(GTeleports plugin) {
        this.plugin = plugin;
        Failures.log(getSpawn(), "SpawnManager#preload");
    }

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

    public SpawnLocation getCachedSpawn() {
        CompletableFuture<SpawnLocation> cached = spawnCache;

        if (cached != null && cached.isDone() && !cached.isCompletedExceptionally()) {
            return cached.join();
        }

        Failures.log(getSpawn(), "SpawnManager#warmCache");
        return null;
    }

    public CompletableFuture<Void> setSpawn(Player player) {
        SpawnLocation spawn = new SpawnLocation(player.getLocation(),
                plugin.getCrossServerManager().currentServer());

        CompletableFuture<Void> result = plugin.getDatabaseManager().getProvider().saveSpawn(spawn)
                .thenRun(() -> {
                    spawnCache = CompletableFuture.completedFuture(spawn);
                    plugin.getCrossServerManager().broadcastInvalidation(InvalidationScope.SPAWN, null);
                    plugin.getMessages().send(player, "spawn.set");
                });

        return Failures.guard(plugin, result, "SpawnManager#setSpawn", player);
    }

    public void invalidateCache() {
        spawnCache = null;
    }

    private synchronized void invalidateIfStill(CompletableFuture<SpawnLocation> expected) {
        if (spawnCache == expected) {
            spawnCache = null;
        }
    }

    public void teleportToSpawn(Player player) {
        Failures.log(getSpawn().thenAccept(spawn -> {
            if (spawn == null) {
                plugin.getMessages().send(player, "errors.spawn-not-set");
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                if (!plugin.getCrossServerManager().isLocal(spawn.getServer())) {
                    plugin.getCrossServerManager().sendTo(player, TeleportType.SPAWN, spawn);
                    return;
                }

                Location location = spawn.toLocation();
                if (location == null) {
                    plugin.getMessages().send(player, "errors.world-not-found");
                    return;
                }

                plugin.getTeleportManager().requestTeleport(player, location, TeleportType.SPAWN,
                        null, plugin.getCrossServerManager().currentServer(), success -> {
                        });
            });
        }), "SpawnManager#teleportToSpawn");
    }

    public void safeDisable() {
        spawnCache = null;
    }
}

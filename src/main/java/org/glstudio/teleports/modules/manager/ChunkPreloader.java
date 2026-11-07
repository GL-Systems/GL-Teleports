package org.glstudio.teleports.modules.manager;

import org.bukkit.Location;
import org.bukkit.World;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Loads a destination's chunks before the player arrives.
 */
public final class ChunkPreloader {
    private static final long TIMEOUT_SECONDS = 5L;

    private ChunkPreloader() {
    }

    private static boolean enabled(GTeleports plugin) {
        return plugin.getSettings().getBoolean("performance.preload-chunks", true);
    }

    /**
     * Loads the chunks around a destination.
     */
    public static CompletableFuture<Void> prepare(GTeleports plugin, Location destination) {
        if (destination == null || !enabled(plugin)) {
            return CompletableFuture.completedFuture(null);
        }

        World world = destination.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }

        int chunkX = destination.getBlockX() >> 4;
        int chunkZ = destination.getBlockZ() >> 4;

        if (world.isChunkLoaded(chunkX, chunkZ)) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            return world.getChunkAtAsync(chunkX, chunkZ, true)
                    .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .handle((chunk, error) -> {
                        if (error != null) {
                            LoggerUtils.logDebug("ChunkPreloader", "Could not preload "
                                    + world.getName() + " " + chunkX + "," + chunkZ
                                    + "; teleporting anyway: " + error.getMessage());
                        }
                        return null;
                    });
        } catch (Throwable e) {
            LoggerUtils.logDebug("ChunkPreloader", "Async chunk loading unavailable: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }
}

package org.glstudio.teleports.modules.model;

import java.util.UUID;

/**
 * A warp marked as favourite.
 */
public record PlayerWarpFavourite(UUID player, String warpId, long addedAt) {
    /**
     * Creates one.
     */
    public static PlayerWarpFavourite of(UUID player, String warpId) {
        return new PlayerWarpFavourite(player, warpId, System.currentTimeMillis());
    }
}

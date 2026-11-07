package org.glstudio.teleports.modules.model;

import java.util.UUID;

/**
 * A rating of a warp.
 */
public record PlayerWarpRating(String warpId, UUID player, String playerName, int stars, String comment,
                               long createdAt, long updatedAt) {
    /**
     * Creates one.
     */
    public static PlayerWarpRating create(String warpId, UUID player, String playerName, int stars, String comment) {
        long now = System.currentTimeMillis();
        return new PlayerWarpRating(warpId, player, playerName, clamp(stars), comment, now, now);
    }

    /**
     * Returns a changed copy.
     */
    public PlayerWarpRating updated(int stars, String comment) {
        return new PlayerWarpRating(warpId, player, playerName, clamp(stars), comment, createdAt,
                System.currentTimeMillis());
    }

    private static int clamp(int stars) {
        return Math.max(1, Math.min(5, stars));
    }
}

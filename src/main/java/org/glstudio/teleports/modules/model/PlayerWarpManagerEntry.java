package org.glstudio.teleports.modules.model;

import java.util.UUID;

/**
 * A manager of a warp.
 */
public record PlayerWarpManagerEntry(String warpId, UUID player, String playerName, long addedAt) {
    /**
     * Creates one.
     */
    public static PlayerWarpManagerEntry of(String warpId, UUID player, String playerName) {
        return new PlayerWarpManagerEntry(warpId, player, playerName, System.currentTimeMillis());
    }
}

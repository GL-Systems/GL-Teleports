package org.glstudio.teleports.modules.model;

import java.util.UUID;

/**
 * A player allowed into a closed warp.
 */
public record PlayerWarpWhitelistEntry(String warpId, UUID player, String playerName, long addedAt) {
    /**
     * Creates one.
     */
    public static PlayerWarpWhitelistEntry of(String warpId, UUID player, String playerName) {
        return new PlayerWarpWhitelistEntry(warpId, player, playerName, System.currentTimeMillis());
    }
}

package org.glstudio.teleports.modules.model;

import java.util.UUID;

public record PlayerWarpWhitelistEntry(String warpId, UUID player, String playerName, long addedAt) {
    public static PlayerWarpWhitelistEntry of(String warpId, UUID player, String playerName) {
        return new PlayerWarpWhitelistEntry(warpId, player, playerName, System.currentTimeMillis());
    }
}

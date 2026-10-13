package org.glstudio.teleports.modules.model;

import java.util.UUID;

public record PlayerWarpManagerEntry(String warpId, UUID player, String playerName, long addedAt) {
    public static PlayerWarpManagerEntry of(String warpId, UUID player, String playerName) {
        return new PlayerWarpManagerEntry(warpId, player, playerName, System.currentTimeMillis());
    }
}

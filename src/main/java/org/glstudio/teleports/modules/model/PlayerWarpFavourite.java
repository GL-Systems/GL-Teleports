package org.glstudio.teleports.modules.model;

import java.util.UUID;

public record PlayerWarpFavourite(UUID player, String warpId, long addedAt) {
    public static PlayerWarpFavourite of(UUID player, String warpId) {
        return new PlayerWarpFavourite(player, warpId, System.currentTimeMillis());
    }
}

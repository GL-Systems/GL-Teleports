package org.glstudio.teleports.modules.model;

import java.util.UUID;

public record PlayerWarpBan(String warpId, UUID player, String playerName, String bannedBy, String bannedByName,
                            String reason, long bannedAt, long expiresAt) {
    public static final long PERMANENT = -1L;

    public static PlayerWarpBan of(String warpId, UUID player, String playerName, String bannedBy,
                                   String bannedByName, String reason, long durationSeconds) {
        long expires = durationSeconds <= 0 ? PERMANENT
                : System.currentTimeMillis() + (durationSeconds * 1000L);
        return new PlayerWarpBan(warpId, player, playerName, bannedBy, bannedByName, reason,
                System.currentTimeMillis(), expires);
    }

    public boolean isPermanent() {
        return expiresAt == PERMANENT;
    }

    public boolean isExpired() {
        return !isPermanent() && System.currentTimeMillis() >= expiresAt;
    }

    public boolean isActive() {
        return !isExpired();
    }
}

package org.glstudio.teleports.modules.model;

import java.util.UUID;

/**
 * A player banned from a warp.
 */
public record PlayerWarpBan(String warpId, UUID player, String playerName, String bannedBy, String bannedByName,
                            String reason, long bannedAt, long expiresAt) {
    public static final long PERMANENT = -1L;

    /**
     * Creates one.
     */
    public static PlayerWarpBan of(String warpId, UUID player, String playerName, String bannedBy,
                                   String bannedByName, String reason, long durationSeconds) {
        long expires = durationSeconds <= 0 ? PERMANENT
                : System.currentTimeMillis() + (durationSeconds * 1000L);
        return new PlayerWarpBan(warpId, player, playerName, bannedBy, bannedByName, reason,
                System.currentTimeMillis(), expires);
    }

    /**
     * Tells whether this never runs out.
     */
    public boolean isPermanent() {
        return expiresAt == PERMANENT;
    }

    /**
     * Tells whether this has run out.
     */
    public boolean isExpired() {
        return !isPermanent() && System.currentTimeMillis() >= expiresAt;
    }

    /**
     * Tells whether this is in force.
     */
    public boolean isActive() {
        return !isExpired();
    }
}

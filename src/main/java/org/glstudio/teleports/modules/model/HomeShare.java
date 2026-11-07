package org.glstudio.teleports.modules.model;

import java.util.UUID;

/**
 * A home shared with another player.
 */
public record HomeShare(UUID owner,
                        String ownerName,
                        String home,
                        UUID invitee,
                        String inviteeName,
                        long grantedAt,
                        long expiresAt) {
    public static final long PERMANENT = 0L;

    /**
     * Creates one with no end.
     */
    public static HomeShare permanent(UUID owner, String ownerName, String home,
                                      UUID invitee, String inviteeName) {
        return new HomeShare(owner, ownerName, home, invitee, inviteeName,
                System.currentTimeMillis(), PERMANENT);
    }

    /**
     * Creates one that expires.
     */
    public static HomeShare temporary(UUID owner, String ownerName, String home,
                                      UUID invitee, String inviteeName, long durationSeconds) {
        return new HomeShare(owner, ownerName, home, invitee, inviteeName,
                System.currentTimeMillis(), System.currentTimeMillis() + (durationSeconds * 1000L));
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
     * Returns the seconds left.
     */
    public long remainingSeconds() {
        if (isPermanent()) {
            return -1L;
        }
        long remaining = expiresAt - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (remaining + 999) / 1000;
    }
}

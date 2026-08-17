package org.glstudio.teleports.modules.model;

import java.util.UUID;

public record HomeShare(UUID owner,
                        String ownerName,
                        String home,
                        UUID invitee,
                        String inviteeName,
                        long grantedAt,
                        long expiresAt) {
    public static final long PERMANENT = 0L;

    public static HomeShare permanent(UUID owner, String ownerName, String home,
                                      UUID invitee, String inviteeName) {
        return new HomeShare(owner, ownerName, home, invitee, inviteeName,
                System.currentTimeMillis(), PERMANENT);
    }

    public static HomeShare temporary(UUID owner, String ownerName, String home,
                                      UUID invitee, String inviteeName, long durationSeconds) {
        return new HomeShare(owner, ownerName, home, invitee, inviteeName,
                System.currentTimeMillis(), System.currentTimeMillis() + (durationSeconds * 1000L));
    }

    public boolean isPermanent() {
        return expiresAt == PERMANENT;
    }

    public boolean isExpired() {
        return !isPermanent() && System.currentTimeMillis() >= expiresAt;
    }

    public long remainingSeconds() {
        if (isPermanent()) {
            return -1L;
        }
        long remaining = expiresAt - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (remaining + 999) / 1000;
    }
}

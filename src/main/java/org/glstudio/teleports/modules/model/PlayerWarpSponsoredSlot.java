package org.glstudio.teleports.modules.model;

import java.util.UUID;

/**
 * A paid slot that promotes a warp.
 */
public record PlayerWarpSponsoredSlot(int slotIndex, String warpId, UUID owner, String ownerName, long startedAt,
                                      long expiresAt, double pricePaid) {
    /**
     * Tells whether this has run out.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    /**
     * Returns the seconds left.
     */
    public long remainingSeconds() {
        long remaining = expiresAt - System.currentTimeMillis();
        return remaining <= 0 ? 0 : remaining / 1000L;
    }
}

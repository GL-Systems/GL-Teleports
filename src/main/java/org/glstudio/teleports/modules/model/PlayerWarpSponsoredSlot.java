package org.glstudio.teleports.modules.model;

import java.util.UUID;

public record PlayerWarpSponsoredSlot(int slotIndex, String warpId, UUID owner, String ownerName, long startedAt,
                                      long expiresAt, double pricePaid) {
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    public long remainingSeconds() {
        long remaining = expiresAt - System.currentTimeMillis();
        return remaining <= 0 ? 0 : remaining / 1000L;
    }
}

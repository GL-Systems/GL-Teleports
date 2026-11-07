package org.glstudio.teleports.common.network.packets;

/**
 * The possible answers to a teleport request.
 */
public enum TpaResponse {
    /**
     * Accepted.
     */
    ACCEPTED,
    /**
     * Denied.
     */
    DENIED,
    /**
     * Cancelled.
     */
    CANCELLED,
    /**
     * Expired.
     */
    EXPIRED,
    /**
     * Disabled.
     */
    DISABLED
}

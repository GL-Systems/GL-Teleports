package org.glstudio.teleports.modules.model;

/**
 * Why a warp was removed automatically.
 */
public enum PlayerWarpPurgeReason {
    /**
     * Inactive.
     */
    INACTIVE,
    /**
     * Owner never seen.
     */
    OWNER_NEVER_SEEN,
    /**
     * World missing.
     */
    WORLD_MISSING,
    /**
     * Malformed.
     */
    MALFORMED
}

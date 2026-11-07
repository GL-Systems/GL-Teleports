package org.glstudio.teleports.common.network.packets;

/**
 * What a cache invalidation covers.
 */
public enum InvalidationScope {
    /**
     * Home.
     */
    HOME,
    /**
     * Warp.
     */
    WARP,
    /**
     * Player warp.
     */
    PLAYER_WARP,
    /**
     * Sponsored slot.
     */
    SPONSORED_SLOT,
    /**
     * Spawn.
     */
    SPAWN,

    /**
     * Lock.
     */
    LOCK
}

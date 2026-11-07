package org.glstudio.teleports.modules.migration.source;

import java.util.UUID;

/**
 * A player's relation to a player warp: a favourite, a rating or a place on one of its lists.
 *
 * @param warpId   the {@link SourcePlayerWarp#sourceId()} of the warp it belongs to
 * @param stars    the rating, for {@link Kind#RATING}; 0 otherwise
 * @param text     the rating comment or the ban reason, or null
 * @param expiresAt when a ban ends, or -1 when it is permanent; unused for the other kinds
 */
public record SourcePlayerWarpLink(Kind kind, long warpId, UUID player, String playerName, int stars, String text,
                                   long expiresAt, String origin) {

    /**
     * What can be selected.
     */
    public enum Kind {
        /**
         * Favourite.
         */
        FAVOURITE,
        /**
         * Rating.
         */
        RATING,
        /**
         * Whitelist.
         */
        WHITELIST,
        /**
         * Manager.
         */
        MANAGER,
        /**
         * Ban.
         */
        BAN
    }
}

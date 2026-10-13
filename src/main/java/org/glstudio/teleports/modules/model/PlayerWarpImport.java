package org.glstudio.teleports.modules.model;

import java.util.List;

/**
 * One imported player warp together with every record that belongs to it, written as a unit.
 */
public record PlayerWarpImport(PlayerWarp warp, List<PlayerWarpFavourite> favourites,
                               List<PlayerWarpRating> ratings, List<PlayerWarpWhitelistEntry> whitelist,
                               List<PlayerWarpManagerEntry> managers, List<PlayerWarpBan> bans) {
}

package org.glstudio.teleports.modules.database;

import org.glstudio.teleports.modules.model.BackLocation;
import org.glstudio.teleports.modules.model.DestinationLock;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.HomeShare;
import org.glstudio.teleports.modules.model.PlayerRef;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpBan;
import org.glstudio.teleports.modules.model.PlayerWarpFavourite;
import org.glstudio.teleports.modules.model.PlayerWarpImport;
import org.glstudio.teleports.modules.model.PlayerWarpManagerEntry;
import org.glstudio.teleports.modules.model.PlayerWarpRating;
import org.glstudio.teleports.modules.model.PlayerWarpSponsoredSlot;
import org.glstudio.teleports.modules.model.PlayerWarpWhitelistEntry;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.StoredLocation;
import org.glstudio.teleports.modules.model.TeleportRecord;
import org.glstudio.teleports.modules.model.Warp;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Everything the plugin reads from and writes to its storage.
 */
public interface DatabaseProvider {
    /**
     * Connects and prepares the storage.
     */
    CompletableFuture<Void> initialize();
    /**
     * Shuts this down.
     */
    CompletableFuture<Void> shutdown();

    /**
     * Applies the current configuration.
     */
    default CompletableFuture<Void> reload() {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Returns the homes.
     */
    CompletableFuture<List<Home>> getHomes(UUID owner);
    /**
     * Returns the home.
     */
    CompletableFuture<Home> getHome(UUID owner, String name);
    /**
     * Saves the home.
     */
    CompletableFuture<Void> saveHome(Home home);
    /**
     * Deletes the home.
     */
    CompletableFuture<Void> deleteHome(UUID owner, String name);

    /**
     * Returns the all warps.
     */
    CompletableFuture<List<Warp>> getAllWarps();
    /**
     * Returns the warp.
     */
    CompletableFuture<Warp> getWarp(String name);
    /**
     * Saves the warp.
     */
    CompletableFuture<Void> saveWarp(Warp warp);

    /**
     * Deletes the warp.
     */
    CompletableFuture<Boolean> deleteWarp(String name);

    /**
     * Writes a batch of imported homes and warps, replacing any entry with the same key.
     *
     * Backends that support transactions write the whole batch or nothing. The default saves one entry at a time,
     * which is correct everywhere but not atomic; providers override it where they can do better.
     */
    default CompletableFuture<Void> importData(List<Home> homes, List<Warp> warps) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (Home home : homes) {
            chain = chain.thenCompose(ignored -> saveHome(home));
        }
        for (Warp warp : warps) {
            chain = chain.thenCompose(ignored -> saveWarp(warp));
        }

        return chain;
    }

    /**
     * Writes imported player warps with their favourites, ratings, whitelists, managers and bans, replacing any
     * record with the same key.
     *
     * Backends that support transactions write everything or nothing. The default saves one record at a time and
     * writes each warp only after its related records, so a run that fails half way leaves no warp without its
     * relations, and running the import again completes it.
     */
    default CompletableFuture<Void> importPlayerWarps(List<PlayerWarpImport> imports) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (PlayerWarpImport entry : imports) {
            for (PlayerWarpFavourite favourite : entry.favourites()) {
                chain = chain.thenCompose(ignored -> addPlayerWarpFavourite(favourite));
            }
            for (PlayerWarpRating rating : entry.ratings()) {
                chain = chain.thenCompose(ignored -> savePlayerWarpRating(rating));
            }
            for (PlayerWarpWhitelistEntry whitelisted : entry.whitelist()) {
                chain = chain.thenCompose(ignored -> savePlayerWarpWhitelistEntry(whitelisted));
            }
            for (PlayerWarpManagerEntry manager : entry.managers()) {
                chain = chain.thenCompose(ignored -> savePlayerWarpManagerEntry(manager));
            }
            for (PlayerWarpBan ban : entry.bans()) {
                chain = chain.thenCompose(ignored -> savePlayerWarpBan(ban));
            }
            chain = chain.thenCompose(ignored -> savePlayerWarp(entry.warp()));
        }

        return chain;
    }

    /**
     * Returns the spawn.
     */
    CompletableFuture<SpawnLocation> getSpawn();

    /**
     * Saves the spawn.
     */
    CompletableFuture<Void> saveSpawn(SpawnLocation spawn);

    /**
     * Records a player's name.
     */
    CompletableFuture<Void> rememberPlayer(UUID uuid, String name);

    /**
     * Finds the player.
     */
    CompletableFuture<PlayerRef> findPlayer(String name);

    /**
     * Saves the last location.
     */
    CompletableFuture<Void> saveLastLocation(UUID uuid, String server, StoredLocation location);

    /**
     * Returns the last location.
     */
    CompletableFuture<StoredLocation> getLastLocation(UUID uuid, String server);

    /**
     * Saves the bed respawn point.
     */
    CompletableFuture<Void> saveBedRespawnPoint(UUID uuid, StoredLocation location);

    /**
     * Returns the bed respawn point.
     */
    CompletableFuture<StoredLocation> getBedRespawnPoint(UUID uuid);

    /**
     * Clears the bed respawn point.
     */
    CompletableFuture<Void> clearBedRespawnPoint(UUID uuid);

    /**
     * Tells whether tpa blocked.
     */
    CompletableFuture<Boolean> isTpaBlocked(UUID uuid);

    /**
     * Sets the tpa blocked.
     */
    CompletableFuture<Void> setTpaBlocked(UUID uuid, boolean blocked);

    /**
     * Stores a teleport.
     */
    CompletableFuture<Void> logTeleport(TeleportRecord record);

    /**
     * Returns the teleport history.
     */
    CompletableFuture<List<TeleportRecord>> getTeleportHistory(UUID player, int limit);

    /**
     * Returns the warp history.
     */
    CompletableFuture<List<TeleportRecord>> getWarpHistory(String warp, int limit);

    /**
     * Deletes teleports older than a time.
     */
    CompletableFuture<Integer> purgeTeleportHistory(long cutoff);

    /**
     * Stores a shared home.
     */
    CompletableFuture<Void> shareHome(HomeShare share);

    /**
     * Removes a shared home.
     */
    CompletableFuture<Boolean> unshareHome(UUID owner, String home, UUID invitee);

    /**
     * Returns the shares by owner.
     */
    CompletableFuture<List<HomeShare>> getSharesByOwner(UUID owner);

    /**
     * Returns the shares for invitee.
     */
    CompletableFuture<List<HomeShare>> getSharesForInvitee(UUID invitee);

    /**
     * Clears the shares of.
     */
    CompletableFuture<Void> clearSharesOf(UUID owner, String home);

    /**
     * Returns the tpa blocks.
     */
    CompletableFuture<Set<UUID>> getTpaBlocks(UUID owner);

    /**
     * Adds the tpa block.
     */
    CompletableFuture<Void> addTpaBlock(UUID owner, UUID blocked);

    /**
     * Removes the tpa block.
     */
    CompletableFuture<Boolean> removeTpaBlock(UUID owner, UUID blocked);

    /**
     * Returns the locks.
     */
    CompletableFuture<List<DestinationLock>> getLocks();

    /**
     * Saves the lock.
     */
    CompletableFuture<Void> saveLock(DestinationLock lock);

    /**
     * Deletes the lock.
     */
    CompletableFuture<Boolean> deleteLock(String type, String target);

    // ── Player warps ────────────────────────────────────────────────────────

    /**
     * Returns the all player warps.
     */
    CompletableFuture<List<PlayerWarp>> getAllPlayerWarps();

    /**
     * Returns the player warp.
     */
    CompletableFuture<PlayerWarp> getPlayerWarp(String id);

    /**
     * Finds the player warp by name.
     */
    CompletableFuture<PlayerWarp> findPlayerWarpByName(String name);

    /**
     * Saves the player warp.
     */
    CompletableFuture<Void> savePlayerWarp(PlayerWarp warp);

    /**
     * Deletes the player warp.
     */
    CompletableFuture<Boolean> deletePlayerWarp(String id);

    /**
     * Returns the player warp whitelist.
     */
    CompletableFuture<List<PlayerWarpWhitelistEntry>> getPlayerWarpWhitelist(String warpId);

    /**
     * Saves the player warp whitelist entry.
     */
    CompletableFuture<Void> savePlayerWarpWhitelistEntry(PlayerWarpWhitelistEntry entry);

    /**
     * Removes the player warp whitelist entry.
     */
    CompletableFuture<Boolean> removePlayerWarpWhitelistEntry(String warpId, UUID player);

    /**
     * Returns the player warp bans.
     */
    CompletableFuture<List<PlayerWarpBan>> getPlayerWarpBans(String warpId);

    /**
     * Saves the player warp ban.
     */
    CompletableFuture<Void> savePlayerWarpBan(PlayerWarpBan ban);

    /**
     * Removes the player warp ban.
     */
    CompletableFuture<Boolean> removePlayerWarpBan(String warpId, UUID player);

    /**
     * Returns the player warp managers.
     */
    CompletableFuture<List<PlayerWarpManagerEntry>> getPlayerWarpManagers(String warpId);

    /**
     * Saves the player warp manager entry.
     */
    CompletableFuture<Void> savePlayerWarpManagerEntry(PlayerWarpManagerEntry entry);

    /**
     * Removes the player warp manager entry.
     */
    CompletableFuture<Boolean> removePlayerWarpManagerEntry(String warpId, UUID player);

    /**
     * Returns the player warp ratings.
     */
    CompletableFuture<List<PlayerWarpRating>> getPlayerWarpRatings(String warpId);

    /**
     * Saves the player warp rating.
     */
    CompletableFuture<Void> savePlayerWarpRating(PlayerWarpRating rating);

    /**
     * Returns the player warp favourites.
     */
    CompletableFuture<List<PlayerWarpFavourite>> getPlayerWarpFavourites(UUID player);

    /**
     * Adds the player warp favourite.
     */
    CompletableFuture<Void> addPlayerWarpFavourite(PlayerWarpFavourite favourite);

    /**
     * Removes the player warp favourite.
     */
    CompletableFuture<Boolean> removePlayerWarpFavourite(UUID player, String warpId);

    /**
     * Returns the sponsored slots.
     */
    CompletableFuture<List<PlayerWarpSponsoredSlot>> getSponsoredSlots();

    /**
     * Saves the sponsored slot.
     */
    CompletableFuture<Void> saveSponsoredSlot(PlayerWarpSponsoredSlot slot);

    /**
     * Clears the sponsored slot.
     */
    CompletableFuture<Boolean> clearSponsoredSlot(int slotIndex);

    /**
     * Returns the back location.
     */
    CompletableFuture<BackLocation> getBackLocation(UUID owner);

    /**
     * Saves the back location.
     */
    CompletableFuture<Void> saveBackLocation(BackLocation location);
}

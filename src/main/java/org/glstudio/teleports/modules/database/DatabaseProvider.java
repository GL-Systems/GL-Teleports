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
import org.glstudio.teleports.modules.model.StoredLocation;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.TeleportRecord;
import org.glstudio.teleports.modules.model.Warp;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DatabaseProvider {
    CompletableFuture<Void> initialize();
    CompletableFuture<Void> shutdown();

    default CompletableFuture<Void> reload() {
        return CompletableFuture.completedFuture(null);
    }

    CompletableFuture<List<Home>> getHomes(UUID owner);
    CompletableFuture<Home> getHome(UUID owner, String name);
    CompletableFuture<Void> saveHome(Home home);
    CompletableFuture<Void> deleteHome(UUID owner, String name);

    CompletableFuture<List<Warp>> getAllWarps();
    CompletableFuture<Warp> getWarp(String name);
    CompletableFuture<Void> saveWarp(Warp warp);

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

    CompletableFuture<SpawnLocation> getSpawn();

    CompletableFuture<Void> saveSpawn(SpawnLocation spawn);

    CompletableFuture<Void> rememberPlayer(UUID uuid, String name);

    CompletableFuture<PlayerRef> findPlayer(String name);

    CompletableFuture<Void> saveLastLocation(UUID uuid, String server, StoredLocation location);

    CompletableFuture<StoredLocation> getLastLocation(UUID uuid, String server);

    CompletableFuture<Void> saveBedRespawnPoint(UUID uuid, StoredLocation location);

    CompletableFuture<StoredLocation> getBedRespawnPoint(UUID uuid);

    CompletableFuture<Void> clearBedRespawnPoint(UUID uuid);

    CompletableFuture<Boolean> isTpaBlocked(UUID uuid);

    CompletableFuture<Void> setTpaBlocked(UUID uuid, boolean blocked);

    CompletableFuture<Void> logTeleport(TeleportRecord record);

    CompletableFuture<List<TeleportRecord>> getTeleportHistory(UUID player, int limit);

    CompletableFuture<List<TeleportRecord>> getWarpHistory(String warp, int limit);

    CompletableFuture<Integer> purgeTeleportHistory(long cutoff);

    CompletableFuture<Void> shareHome(HomeShare share);

    CompletableFuture<Boolean> unshareHome(UUID owner, String home, UUID invitee);

    CompletableFuture<List<HomeShare>> getSharesByOwner(UUID owner);

    CompletableFuture<List<HomeShare>> getSharesForInvitee(UUID invitee);

    CompletableFuture<Void> clearSharesOf(UUID owner, String home);

    CompletableFuture<Set<UUID>> getTpaBlocks(UUID owner);

    CompletableFuture<Void> addTpaBlock(UUID owner, UUID blocked);

    CompletableFuture<Boolean> removeTpaBlock(UUID owner, UUID blocked);

    CompletableFuture<List<DestinationLock>> getLocks();

    CompletableFuture<Void> saveLock(DestinationLock lock);

    CompletableFuture<Boolean> deleteLock(String type, String target);

    // ── Player warps ────────────────────────────────────────────────────────

    CompletableFuture<List<PlayerWarp>> getAllPlayerWarps();

    CompletableFuture<PlayerWarp> getPlayerWarp(String id);

    CompletableFuture<PlayerWarp> findPlayerWarpByName(String name);

    CompletableFuture<Void> savePlayerWarp(PlayerWarp warp);

    CompletableFuture<Boolean> deletePlayerWarp(String id);

    CompletableFuture<List<PlayerWarpWhitelistEntry>> getPlayerWarpWhitelist(String warpId);

    CompletableFuture<Void> savePlayerWarpWhitelistEntry(PlayerWarpWhitelistEntry entry);

    CompletableFuture<Boolean> removePlayerWarpWhitelistEntry(String warpId, UUID player);

    CompletableFuture<List<PlayerWarpBan>> getPlayerWarpBans(String warpId);

    CompletableFuture<Void> savePlayerWarpBan(PlayerWarpBan ban);

    CompletableFuture<Boolean> removePlayerWarpBan(String warpId, UUID player);

    CompletableFuture<List<PlayerWarpManagerEntry>> getPlayerWarpManagers(String warpId);

    CompletableFuture<Void> savePlayerWarpManagerEntry(PlayerWarpManagerEntry entry);

    CompletableFuture<Boolean> removePlayerWarpManagerEntry(String warpId, UUID player);

    CompletableFuture<List<PlayerWarpRating>> getPlayerWarpRatings(String warpId);

    CompletableFuture<Void> savePlayerWarpRating(PlayerWarpRating rating);

    CompletableFuture<List<PlayerWarpFavourite>> getPlayerWarpFavourites(UUID player);

    CompletableFuture<Void> addPlayerWarpFavourite(PlayerWarpFavourite favourite);

    CompletableFuture<Boolean> removePlayerWarpFavourite(UUID player, String warpId);

    CompletableFuture<List<PlayerWarpSponsoredSlot>> getSponsoredSlots();

    CompletableFuture<Void> saveSponsoredSlot(PlayerWarpSponsoredSlot slot);

    CompletableFuture<Boolean> clearSponsoredSlot(int slotIndex);

    CompletableFuture<BackLocation> getBackLocation(UUID owner);

    CompletableFuture<Void> saveBackLocation(BackLocation location);
}

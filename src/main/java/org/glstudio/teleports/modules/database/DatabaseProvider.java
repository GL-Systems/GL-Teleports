package org.glstudio.teleports.modules.database;

import org.glstudio.teleports.modules.model.DestinationLock;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.HomeShare;
import org.glstudio.teleports.modules.model.PlayerRef;
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

    CompletableFuture<SpawnLocation> getSpawn();

    CompletableFuture<Void> saveSpawn(SpawnLocation spawn);

    CompletableFuture<Void> rememberPlayer(UUID uuid, String name);

    CompletableFuture<PlayerRef> findPlayer(String name);

    CompletableFuture<Void> saveLastLocation(UUID uuid, String server, StoredLocation location);

    CompletableFuture<StoredLocation> getLastLocation(UUID uuid, String server);

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
}

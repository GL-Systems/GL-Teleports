package org.glstudio.teleports.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.api.event.HomeCreateEvent;
import org.glstudio.teleports.api.event.HomeDeleteEvent;
import org.glstudio.teleports.api.event.WarpCreateEvent;
import org.glstudio.teleports.api.event.WarpDeleteEvent;
import org.glstudio.teleports.core.util.TeleportNames;
import org.glstudio.teleports.modules.manager.HomeManager;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.HomeShare;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.TeleportRecord;
import org.glstudio.teleports.modules.model.TeleportType;
import org.glstudio.teleports.modules.model.Warp;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The implementation of {@link GLTeleportsAPI}, registered with Bukkit's service manager at
 * startup.
 *
 * <p>Not meant to be referenced by consumers: obtain {@link GLTeleportsAPI} from the service
 * manager instead. Constructing this yourself bypasses nothing useful and ties you to a class that
 * may change.
 *
 * <p>Deliberately thin. Everything it exposes already lives behind a manager; the value of the
 * interface is that it is a stable surface, which leaves the managers free to change shape.
 */
public class TeleportsAPI implements GLTeleportsAPI {

    private final GTeleports plugin;

    /**
     * Creates the teleports api.
     *
     * @param plugin the plugin
     */
    public TeleportsAPI(GTeleports plugin) {
        this.plugin = plugin;
    }

    @Override
    public String serverName() {
        return plugin.getCrossServerManager().currentServer();
    }

    @Override
    public boolean isNetworkAvailable() {
        return plugin.getCrossServerManager().isEnabled();
    }

    @Override
    public CompletableFuture<List<Home>> getHomes(UUID owner) {
        return plugin.getHomeManager().getHomes(owner);
    }

    @Override
    public CompletableFuture<Home> getHome(UUID owner, String name) {
        return plugin.getHomeManager().getHome(owner, name);
    }

    @Override
    public int getHomeLimit(Player player) {
        return plugin.getHomeManager().getHomeLimit(player);
    }

    @Override
    public void teleportToHome(Player player, String name) {
        plugin.getHomeManager().teleportToHome(player, name);
    }

    @Override
    public CompletableFuture<List<Warp>> getWarps() {
        return plugin.getWarpManager().getWarps();
    }

    @Override
    public CompletableFuture<Warp> getWarp(String name) {
        return plugin.getWarpManager().getWarp(name);
    }

    @Override
    public void teleportToWarp(Player player, String name) {
        plugin.getWarpManager().teleportToWarp(player, name);
    }

    @Override
    public CompletableFuture<SpawnLocation> getSpawn() {
        return plugin.getDatabaseManager().getProvider().getSpawn();
    }

    @Override
    public void teleportToSpawn(Player player) {
        plugin.getSpawnManager().teleportToSpawn(player);
    }

    @Override
    public void teleport(Player player, Location destination, TeleportType type) {
        plugin.getTeleportManager().requestTeleport(player, destination, type, success -> {
        });
    }

    @Override
    public boolean hasCooldown(Player player, TeleportType type) {
        return plugin.getCooldownManager().hasCooldown(player, type);
    }

    @Override
    public long remainingCooldownSeconds(Player player, TeleportType type) {
        return plugin.getCooldownManager().getRemainingCooldown(player, type);
    }

    @Override
    public CompletableFuture<List<TeleportRecord>> getHistory(UUID player, int limit) {
        return plugin.getHistoryManager().history(player, limit);
    }

    @Override
    public List<Home> cachedHomes(UUID owner) {
        return plugin.getHomeManager().getCachedHomes(owner);
    }

    @Override
    public int cachedHomeCount(UUID owner) {
        return plugin.getHomeManager().getCachedHomes(owner).size();
    }

    @Override
    public int cachedHomesRemaining(Player player) {
        int limit = plugin.getHomeManager().getHomeLimit(player);

        if (limit == HomeManager.UNLIMITED) {
            return Integer.MAX_VALUE;
        }

        return Math.max(0, limit - cachedHomeCount(player.getUniqueId()));
    }

    @Override
    public List<Warp> cachedWarps() {
        return plugin.getWarpManager().getCachedWarps();
    }

    @Override
    public CompletableFuture<Boolean> setHome(Player owner, String name, Location location) {
        String normalized = TeleportNames.normalize(name);

        return plugin.getHomeManager().getHome(owner.getUniqueId(), normalized).thenCompose(existing -> {
            HomeCreateEvent event =
                    new HomeCreateEvent(owner, normalized, location, existing != null);

            if (!Events.call(event)) {
                return CompletableFuture.completedFuture(false);
            }

            return setHome(owner.getUniqueId(), normalized, location);
        });
    }

    @Override
    public CompletableFuture<Boolean> setHome(UUID owner, String name, Location location) {
        return plugin.getHomeManager().createHome(owner, name, location);
    }

    @Override
    public CompletableFuture<Boolean> deleteHome(Player owner, String name) {
        return plugin.getHomeManager().getHome(owner.getUniqueId(), name).thenCompose(existing -> {
            if (existing == null) {
                return CompletableFuture.completedFuture(false);
            }

            if (!Events.call(new HomeDeleteEvent(owner, existing))) {
                return CompletableFuture.completedFuture(false);
            }

            return deleteHome(owner.getUniqueId(), name);
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteHome(UUID owner, String name) {
        return plugin.getHomeManager().removeHome(owner, name);
    }

    @Override
    public CompletableFuture<Boolean> setWarp(Player creator, String name, Location location,
                                              String permission) {
        String normalized = TeleportNames.normalize(name);

        return plugin.getWarpManager().getWarp(normalized).thenCompose(existing -> {
            WarpCreateEvent event =
                    new WarpCreateEvent(creator, normalized, location, existing != null);

            if (!Events.call(event)) {
                return CompletableFuture.completedFuture(false);
            }

            return setWarp(normalized, location, permission);
        });
    }

    @Override
    public CompletableFuture<Boolean> setWarp(String name, Location location, String permission) {
        return plugin.getWarpManager().createWarp(name, location, permission);
    }

    @Override
    public CompletableFuture<Boolean> deleteWarp(Player actor, String name) {
        return plugin.getWarpManager().getWarp(name).thenCompose(existing -> {
            if (existing == null) {
                return CompletableFuture.completedFuture(false);
            }

            if (!Events.call(new WarpDeleteEvent(actor, existing))) {
                return CompletableFuture.completedFuture(false);
            }

            return deleteWarp(name);
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteWarp(String name) {
        return plugin.getWarpManager().removeWarp(name);
    }

    @Override
    public boolean isHomeSharingEnabled() {
        return plugin.getHomeShareManager().isEnabled();
    }

    @Override
    public CompletableFuture<Boolean> shareHome(UUID owner, String ownerName, String home,
                                                UUID invitee, String inviteeName, long durationSeconds) {
        return plugin.getHomeShareManager()
                .grant(owner, ownerName, home, invitee, inviteeName, durationSeconds);
    }

    @Override
    public CompletableFuture<Boolean> unshareHome(UUID owner, String home, UUID invitee) {
        return plugin.getHomeShareManager().revoke(owner, home, invitee);
    }

    @Override
    public CompletableFuture<Boolean> canUseSharedHome(UUID visitor, UUID owner, String home) {
        return plugin.getHomeShareManager().mayUse(visitor, owner, TeleportNames.normalize(home));
    }

    @Override
    public CompletableFuture<List<HomeShare>> getSharedWith(UUID invitee) {
        return plugin.getHomeShareManager().incoming(invitee);
    }

    @Override
    public CompletableFuture<List<HomeShare>> getSharesBy(UUID owner) {
        return plugin.getHomeShareManager().outgoing(owner);
    }

    @Override
    public void teleportToSharedHome(Player player, UUID owner, String ownerName, String name) {
        plugin.getHomeManager().teleportToSharedHome(player, owner, ownerName, name);
    }

    @Override
    public boolean isWarpLocked(String warp) {
        return plugin.getLockManager().isWarpLocked(warp);
    }

    @Override
    public boolean isServerLocked(String server) {
        return plugin.getLockManager().isServerLocked(server);
    }
}

package org.glstudio.teleports.modules.manager;

import com.google.gson.Gson;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.glstudio.nexus.proxy.ProxyMessenger;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.api.Events;
import org.glstudio.teleports.api.event.CrossServerTransferEvent;
import org.glstudio.teleports.common.config.MultiServerSettings;
import org.glstudio.teleports.common.network.PacketHandler;
import org.glstudio.teleports.common.network.packets.CacheInvalidatePacket;
import org.glstudio.teleports.common.network.packets.InvalidationScope;
import org.glstudio.teleports.common.network.packets.ServerPresencePacket;
import org.glstudio.teleports.common.network.packets.TeleportRequestPacket;
import org.glstudio.teleports.common.storage.redis.PlayerRegistry;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.model.DestinationLock;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.PendingTeleport;
import org.glstudio.teleports.modules.model.TeleportDestination;
import org.glstudio.teleports.modules.model.TeleportType;
import org.glstudio.teleports.modules.model.Warp;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Carries out teleports between servers.
 */
@Getter
public class CrossServerManager {
    private static final long TRANSFER_CHECK_TICKS = 100L;

    private static final long PLACEMENT_CHECK_TICKS = 5L;

    private static final double PLACEMENT_TOLERANCE = 8.0D;

    private static final long PENDING_STORE_DELAY_MS = 2_000L;

    private static final Gson GSON = new Gson();

    private final GTeleports plugin;
    private final MultiServerSettings settings;

    private volatile PacketHandler packetHandler;
    private volatile ProxyMessenger transferService;
    private volatile PlayerRegistry playerRegistry;

    private final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();

    private final Set<UUID> arrivalsPending = ConcurrentHashMap.newKeySet();

    private final Map<UUID, ScheduledFuture<?>> pendingStores = new ConcurrentHashMap<>();

    /**
     * Creates the manager.
     */
    public CrossServerManager(GTeleports plugin, MultiServerSettings settings,
                              PacketHandler packetHandler, ProxyMessenger transferService,
                              PlayerRegistry playerRegistry) {
        this.plugin = plugin;
        this.settings = settings;
        this.packetHandler = packetHandler;
        this.transferService = transferService;
        this.playerRegistry = playerRegistry;
    }

    /**
     * Starts using the network pieces once they exist.
     */
    public void attach(PacketHandler packetHandler, ProxyMessenger transferService,
                       PlayerRegistry playerRegistry) {
        this.transferService = transferService;
        this.playerRegistry = playerRegistry;
        this.packetHandler = packetHandler;
    }

    /**
     * Returns this server's name.
     */
    public String currentServer() {
        return settings.getServerName();
    }

    /**
     * Tells whether this feature is on.
     */
    public boolean isEnabled() {
        return settings.isEnabled() && packetHandler != null && transferService != null
                && playerRegistry != null;
    }

    /**
     * Tells whether a server name means this server.
     */
    public boolean isLocal(String server) {
        return settings.isLocalServer(server);
    }

    /**
     * Tells whether a server can be reached.
     */
    public boolean canReach(String server) {
        return isLocal(server) || isEnabled();
    }

    /**
     * Sends the to.
     */
    public void sendTo(Player player, TeleportType type, TeleportDestination destination) {
        sendTo(player, type, destination, false);
    }

    /**
     * Sends the to.
     */
    public void sendTo(Player player, TeleportType type, TeleportDestination destination, boolean instant) {
        String targetServer = destination.getServer();

        if (!isEnabled()) {
            plugin.getMessages().send(player, "errors.server-unavailable", "{server}", String.valueOf(targetServer));
            return;
        }

        if (!playerRegistry.isKnownServer(targetServer)) {
            plugin.getMessages().send(player, "errors.server-unavailable", "{server}", String.valueOf(targetServer));
            return;
        }

        if (!player.hasPermission(Permissions.BYPASS_LOCK)) {
            DestinationLock serverLock = plugin.getLockManager().find(DestinationLock.SERVER, targetServer);

            if (serverLock != null) {
                plugin.getMessages().send(player, "errors.server-locked",
                        "{server}", String.valueOf(targetServer),
                        "{reason}", serverLock.reason() == null ? "-" : serverLock.reason());
                return;
            }
        }

        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        String destinationName = describe(destination);

        plugin.getTeleportManager().requestRemoteTeleport(player, type, destination, destinationName, instant, () -> {
            CrossServerTransferEvent event =
                    new CrossServerTransferEvent(player, type, targetServer, destinationName);

            if (!Events.callAndExplain(event, player, event.getCancelMessage())) {
                plugin.getTeleportManager().refund(player, type);
                return;
            }

            if (player.isOp()) {
                plugin.getMessages().send(player, "teleport.transferring", "{server}", targetServer);
            }

            TeleportRequestPacket packet = new TeleportRequestPacket(
                    settings.getServerName(), settings.getInstanceId(), targetServer,
                    uuid, playerName, type, destination, destinationName);

            packetHandler.send(packet).whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    LoggerUtils.logError("Aborting " + type + " transfer for " + playerName
                            + ": " + error.getMessage());
                    abandonTransfer(player, type);
                    return;
                }

                if (!player.isOnline()) {
                    return;
                }

                if (player.isDead()) {
                    abandonTransfer(player, type);
                    return;
                }

                if (!transferService.connect(player, targetServer)) {
                    abandonTransfer(player, type);
                    return;
                }

                plugin.getHistoryManager().recordCrossServer(player, type, destinationName,
                        player.getLocation(), targetServer, destination.getWorld(),
                        destination.getX(), destination.getY(), destination.getZ(), 0.0D);
                plugin.getBackManager().recordBackPoint(player, player.getLocation(), settings.getServerName());

                verifyTransfer(uuid, type);
            }));
        });
    }

    private static String describe(TeleportDestination destination) {
        if (destination instanceof Warp warp) {
            return warp.getName();
        }
        if (destination instanceof Home home) {
            return home.getName();
        }
        return null;
    }

    private void verifyTransfer(UUID uuid, TeleportType type) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null && player.isOnline()) {
                LoggerUtils.logWarn(player.getName() + " is still here " + (TRANSFER_CHECK_TICKS / 20)
                        + "s after a transfer request; treating it as failed.");
                abandonTransfer(player, type);
            }
        }, TRANSFER_CHECK_TICKS);
    }

    private void abandonTransfer(Player player, TeleportType type) {
        if (!player.isOnline()) {
            return;
        }

        plugin.getMessages().send(player, "errors.transfer-failed");
        plugin.getTeleportManager().refund(player, type);
    }

    /**
     * Broadcasts the invalidation.
     */
    public void broadcastInvalidation(InvalidationScope scope, UUID owner) {
        if (!isEnabled()) {
            return;
        }

        packetHandler.send(new CacheInvalidatePacket(settings.getServerName(), settings.getInstanceId(),
                scope, owner)).exceptionally(error -> {
            LoggerUtils.logWarn("Could not announce " + scope + " change; other servers may serve "
                    + "stale data until their next restart: " + error.getMessage());
            return null;
        });
    }

    /**
     * Tells the other servers whether this one is online.
     */
    public CompletableFuture<Void> announcePresence(boolean online) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        return packetHandler.send(new ServerPresencePacket(settings.getServerName(),
                settings.getInstanceId(), online)).exceptionally(error -> {
            LoggerUtils.logWarn("Could not announce this server as " + (online ? "online" : "offline")
                    + "; other servers will notice on their next directory refresh: "
                    + error.getMessage());
            return null;
        });
    }

    /**
     * Records that a server went online or offline.
     */
    public void handlePresence(ServerPresencePacket packet) {
        if (playerRegistry == null || packet.getSourceServer() == null) {
            return;
        }

        playerRegistry.markServer(packet.getSourceServer(), packet.isOnline());
    }

    /**
     * Drops the cached data a packet names.
     */
    public void handleInvalidation(CacheInvalidatePacket packet) {
        switch (packet.getScope()) {
            case HOME -> {
                if (plugin.getHomeManager() != null) {
                    plugin.getHomeManager().invalidate(packet.getOwner());
                }
            }
            case WARP -> {
                if (plugin.getWarpManager() != null) {
                    plugin.getWarpManager().invalidateCache();
                }
            }
            case PLAYER_WARP -> {
                if (plugin.getPlayerWarpManager() != null) {
                    plugin.getPlayerWarpManager().invalidateCache();
                }
            }
            case SPONSORED_SLOT -> {
                if (plugin.getPlayerWarpSponsorManager() != null) {
                    plugin.getPlayerWarpSponsorManager().invalidateCache();
                }
            }
            case SPAWN -> {
                if (plugin.getSpawnManager() != null) {
                    plugin.getSpawnManager().invalidateCache();
                }
            }
            case LOCK -> {
                if (plugin.getLockManager() != null) {
                    plugin.getLockManager().refresh();
                }
            }
        }
    }

    /**
     * Acts on a teleport another server asked for.
     */
    public void handleIncoming(TeleportRequestPacket packet) {
        UUID uuid = packet.getPlayerUuid();
        PendingTeleport pending = packet.toPendingTeleport(
                System.currentTimeMillis() + settings.getPendingExpiryMillis());

        pendingTeleports.put(uuid, pending);
        purgeExpired();

        runOnMain(() -> {
            if (pending.isPlacedAtLogin() || pendingTeleports.get(uuid) != pending) {

                return;
            }

            Player player = Bukkit.getPlayer(uuid);

            if (player != null && player.isOnline()) {

                pendingTeleports.remove(uuid, pending);
                claimArrival(uuid);
                teleportOnArrival(player, pending);
                return;
            }

            prepareArrival(pending);
            store(uuid, pending);
        });
    }

    private void prepareArrival(PendingTeleport pending) {
        Location raw = pending.toLocation();

        if (raw == null) {

            return;
        }

        ChunkPreloader.prepare(plugin, raw);
    }

    /**
     * Returns where a player arriving now should appear.
     */
    public Location placeAtLogin(Player player) {
        UUID uuid = player.getUniqueId();
        PendingTeleport pending = pendingTeleports.get(uuid);

        if (pending == null || pending.isExpired()) {
            return null;
        }

        Location target = destinationFor(player, pending);

        if (target == null) {

            return null;
        }

        pending.setResolved(target);
        pending.setPlacedAtLogin(true);
        claimArrival(uuid);

        return target;
    }

    private Location destinationFor(Player player, PendingTeleport pending) {
        Location raw = pending.toLocation();

        if (raw == null) {
            return null;
        }

        if (plugin.getBlacklistWorldManager().isBlacklisted(player, raw.getWorld(), pending.getType())) {
            return null;
        }

        return plugin.getTeleportSafetyManager().resolve(player, raw, pending.getType());
    }

    /**
     * Finishes a teleport once the player is here.
     */
    public void handleArrival(Player player) {
        purgeExpired();

        UUID uuid = player.getUniqueId();
        PendingTeleport pending = pendingTeleports.remove(uuid);

        if (pending != null) {
            claimArrival(uuid);
            discard(uuid);

            if (pending.isPlacedAtLogin()) {

                finishPlacedArrival(player, pending);
                return;
            }

            if (pending.isExpired()) {
                LoggerUtils.logWarn("Discarded " + player.getName() + "'s " + pending.getType()
                        + " destination: it expired before they arrived. Raise "
                        + "multi-server.pending-expiry-seconds if transfers here are slow.");
                plugin.getMessages().send(player, "errors.teleport-expired");
                return;
            }

            teleportOnArrival(player, pending);
            return;
        }

        awaitPendingOrRestore(player);
    }

    private void finishPlacedArrival(Player player, PendingTeleport pending) {
        Location target = pending.getResolved();

        plugin.getTeleportManager().completePlacedArrival(player, target, pending.getType(),
                pending.getDestinationName());
        confirmPlacement(player, target, pending);
    }

    private void awaitPendingOrRestore(Player player) {
        UUID uuid = player.getUniqueId();
        arrivalsPending.add(uuid);

        recoverStored(player).whenComplete((ignored, error) -> runOnMain(() ->
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!arrivalsPending.remove(uuid)) {
                        return;
                    }

                    if (player.isOnline() && plugin.getLastLocationManager() != null) {
                        plugin.getLastLocationManager().restore(player);
                    }
                }, arrivalGraceTicks())));
    }

    private boolean claimArrival(UUID uuid) {
        return arrivalsPending.remove(uuid);
    }

    private long arrivalGraceTicks() {
        int seconds = Math.max(0, plugin.getSettings().getInt("last-location.arrival-grace-seconds", 0));
        return seconds * 20L;
    }

    private void runOnMain(Runnable action) {
        if (!plugin.isEnabled()) {
            return;
        }

        if (Bukkit.isPrimaryThread()) {
            action.run();
            return;
        }

        try {
            Bukkit.getScheduler().runTask(plugin, action);
        } catch (IllegalPluginAccessException e) {
            LoggerUtils.logDebug("CrossServerManager", "Dropped arrival work during shutdown: "
                    + e.getMessage());
        }
    }

    /**
     * Forgets what is held for a player.
     */
    public void forget(UUID uuid) {
        pendingTeleports.remove(uuid);
        arrivalsPending.remove(uuid);

        ScheduledFuture<?> scheduled = pendingStores.remove(uuid);
        if (scheduled != null) {
            scheduled.cancel(false);
        }
    }

    private void teleportOnArrival(Player player, PendingTeleport pending) {
        Location location = pending.toLocation();

        if (location == null) {
            LoggerUtils.logError("Cannot place " + player.getName() + " at their " + pending.getType()
                    + " destination: this server has no world called '" + pending.getWorld() + "'. "
                    + "The destination was created on another server whose world names differ.");
            plugin.getMessages().send(player, "errors.world-not-found");
            return;
        }

        long delay = settings.getArrivalDelayTicks();

        if (delay <= 0) {
            placeOnArrival(player, location, pending);
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> placeOnArrival(player, location, pending), delay);
    }

    private void placeOnArrival(Player player, Location location, PendingTeleport pending) {
        if (!player.isOnline()) {
            return;
        }

        Location placed = plugin.getTeleportManager().completeArrival(player, location, pending.getType(),
                pending.getDestinationName());

        if (placed != null) {
            confirmPlacement(player, placed, pending);
        }
    }

    private void confirmPlacement(Player player, Location target, PendingTeleport pending) {
        if (target == null) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || arrived(player, target)) {
                return;
            }

            LoggerUtils.logDebug("CrossServerManager", "Something moved " + player.getName()
                    + " away from their " + pending.getType() + " destination; placing them once more.");

            player.teleport(target);
        }, PLACEMENT_CHECK_TICKS);
    }

    private boolean arrived(Player player, Location target) {
        Location current = player.getLocation();

        return current.getWorld() != null
                && current.getWorld().equals(target.getWorld())
                && current.distanceSquared(target) <= PLACEMENT_TOLERANCE * PLACEMENT_TOLERANCE;
    }

    private void store(UUID uuid, PendingTeleport pending) {
        if (plugin.getRedisService() == null) {
            return;
        }

        ScheduledFuture<?> scheduled = plugin.getExecutors().scheduler().schedule(() -> {
            pendingStores.remove(uuid);

            if (pendingTeleports.get(uuid) != pending) {
                return;
            }

            plugin.getExecutors().io().execute(() -> {
                long ttlSeconds = Math.max(1,
                        (pending.getExpiresAt() - System.currentTimeMillis()) / 1000);

                try {
                    plugin.getRedisService().set(pendingKey(uuid), GSON.toJson(pending), ttlSeconds);
                } catch (Exception error) {
                    LoggerUtils.logWarn("Could not persist the pending teleport for " + uuid
                            + "; it will be lost if this server restarts: " + error.getMessage());
                }
            });
        }, PENDING_STORE_DELAY_MS, TimeUnit.MILLISECONDS);

        pendingStores.put(uuid, scheduled);
    }

    private CompletableFuture<Void> recoverStored(Player player) {
        if (plugin.getRedisService() == null) {
            return CompletableFuture.completedFuture(null);
        }

        UUID uuid = player.getUniqueId();

        return CompletableFuture.supplyAsync(() -> plugin.getRedisService().getAndDelete(pendingKey(uuid)),
                        plugin.getExecutors().io())
                .thenAccept(raw -> {
                    if (raw == null) {
                        return;
                    }

                    PendingTeleport pending = GSON.fromJson(raw, PendingTeleport.class);
                    if (pending == null || pending.isExpired()) {
                        return;
                    }

                    runOnMain(() -> {
                        if (player.isOnline() && claimArrival(player.getUniqueId())) {
                            teleportOnArrival(player, pending);
                        }
                    });
                })
                .exceptionally(error -> {
                    LoggerUtils.logWarn("Could not read the pending teleport for " + player.getName()
                            + ": " + error.getMessage());
                    return null;
                });
    }

    private void discard(UUID uuid) {
        ScheduledFuture<?> scheduled = pendingStores.remove(uuid);

        if (scheduled != null && scheduled.cancel(false)) {
            return;
        }

        if (plugin.getRedisService() == null) {
            return;
        }

        CompletableFuture.runAsync(() -> plugin.getRedisService().delete(pendingKey(uuid)),
                plugin.getExecutors().io()).exceptionally(error -> null);
    }

    private String pendingKey(UUID uuid) {
        return settings.getChannel() + ":pending:" + uuid;
    }

    private void purgeExpired() {

        pendingTeleports.values().removeIf(pending ->
                pending.isExpired() && !pending.isPlacedAtLogin());
    }

    /**
     * Stops this on shutdown.
     */
    public void safeDisable() {
        pendingStores.values().forEach(scheduled -> scheduled.cancel(false));
        pendingStores.clear();
        pendingTeleports.clear();
        arrivalsPending.clear();
    }
}

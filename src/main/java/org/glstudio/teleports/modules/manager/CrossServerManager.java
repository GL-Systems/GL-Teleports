package org.glstudio.teleports.modules.manager;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.network.PacketHandler;
import org.glstudio.teleports.common.network.packets.CacheInvalidatePacket;
import org.glstudio.teleports.common.network.packets.InvalidationScope;
import org.glstudio.teleports.common.network.packets.TeleportRequestPacket;
import org.glstudio.teleports.common.storage.redis.PlayerRegistry;
import org.glstudio.teleports.core.MultiServerSettings;
import org.glstudio.teleports.modules.model.PendingTeleport;
import org.glstudio.teleports.modules.model.TeleportDestination;
import org.glstudio.teleports.modules.model.TeleportType;
import org.glstudio.teleports.modules.transfer.PlayerTransferService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class CrossServerManager {

    private static final long PENDING_EXPIRY_MS = 30_000L;

    private final GTeleports plugin;
    private final MultiServerSettings settings;

    private final PacketHandler packetHandler;
    private final PlayerTransferService transferService;
    private final PlayerRegistry playerRegistry;

    private final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();

    public CrossServerManager(GTeleports plugin, MultiServerSettings settings,
                              PacketHandler packetHandler, PlayerTransferService transferService,
                              PlayerRegistry playerRegistry) {
        this.plugin = plugin;
        this.settings = settings;
        this.packetHandler = packetHandler;
        this.transferService = transferService;
        this.playerRegistry = playerRegistry;
    }

    public String currentServer() {
        return settings.getServerName();
    }

    public boolean isEnabled() {
        return settings.isEnabled() && packetHandler != null && transferService != null
                && playerRegistry != null;
    }

    public boolean isLocal(String server) {
        return !isEnabled() || settings.isLocalServer(server);
    }

    public boolean sendTo(Player player, TeleportType type, TeleportDestination destination) {
        if (!isEnabled()) {
            return false;
        }

        String targetServer = destination.getServer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        plugin.getTeleportManager().requestRemoteTeleport(player, type, () -> {
            sendMessage(player, message("teleport.transferring").replace("{server}", targetServer));

            TeleportRequestPacket packet = new TeleportRequestPacket(
                    settings.getServerName(), settings.getInstanceId(), targetServer,
                    uuid, playerName, type, destination);

            packetHandler.send(packet).whenComplete((result, error) -> {
                if (error != null) {
                    LoggerUtils.logError("Aborting " + type + " transfer for " + playerName
                            + ": " + error.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            sendMessage(player, message("errors.transfer-failed"));
                        }
                    });
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && !transferService.transfer(player, targetServer)) {
                        sendMessage(player, message("errors.transfer-failed"));
                    }
                });
            });
        });

        return true;
    }

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
            case SPAWN -> {
                if (plugin.getSpawnManager() != null) {
                    plugin.getSpawnManager().invalidateCache();
                }
            }
        }
    }

    public void handleIncoming(TeleportRequestPacket packet) {
        UUID uuid = packet.getPlayerUuid();
        PendingTeleport pending = packet.toPendingTeleport(System.currentTimeMillis() + PENDING_EXPIRY_MS);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> teleportOnArrival(player, pending));
            return;
        }

        pendingTeleports.put(uuid, pending);
        purgeExpired();
    }

    public void handleArrival(Player player) {
        purgeExpired();

        PendingTeleport pending = pendingTeleports.remove(player.getUniqueId());
        if (pending == null || pending.isExpired()) {
            return;
        }

        teleportOnArrival(player, pending);
    }

    public void forget(UUID uuid) {
        pendingTeleports.remove(uuid);
    }

    private void teleportOnArrival(Player player, PendingTeleport pending) {
        Location location = pending.toLocation();
        if (location == null) {
            LoggerUtils.logWarn("Cannot place " + player.getName() + " at their " + pending.getType()
                    + " destination: world '" + pending.getWorld() + "' is not loaded here");
            sendMessage(player, message("errors.world-not-found"));
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getTeleportManager().completeArrival(player, location, pending.getType());
            }
        }, settings.getArrivalDelayTicks());
    }

    private void purgeExpired() {
        pendingTeleports.values().removeIf(PendingTeleport::isExpired);
    }

    public void safeDisable() {
        pendingTeleports.clear();
    }

    private String message(String path) {
        String message = plugin.getConfigManager().getLanguageConfig().getString(path);
        return message == null ? "" : message;
    }

    private void sendMessage(Player player, String message) {
        if (message != null && !message.isEmpty()) {
            player.sendMessage(message);
        }
    }
}

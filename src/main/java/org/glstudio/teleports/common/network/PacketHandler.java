package org.glstudio.teleports.common.network;

import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.common.network.packets.CacheInvalidatePacket;
import org.glstudio.teleports.common.network.packets.ServerPresencePacket;
import org.glstudio.teleports.common.network.packets.TeleportRequestPacket;
import org.glstudio.teleports.common.network.packets.TpaRequestPacket;
import org.glstudio.teleports.common.network.packets.TpaResponsePacket;
import org.glstudio.teleports.common.network.packets.TpaTravelPacket;
import org.glstudio.teleports.common.storage.redis.RedisService;
import org.glstudio.teleports.common.config.MultiServerSettings;
import org.glstudio.teleports.common.concurrent.TeleportExecutors;
import org.glstudio.teleports.modules.manager.CrossServerManager;
import org.glstudio.teleports.modules.manager.TpaManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PacketHandler {
    private static final long MAX_PACKET_AGE_MS = 30_000L;

    private static final int SEEN_PURGE_THRESHOLD = 512;

    private final Map<UUID, Long> seenPackets = new ConcurrentHashMap<>();

    private final MultiServerSettings settings;
    private final TeleportExecutors executors;
    private final RedisService redisService;

    private volatile CrossServerManager crossServerManager;

    public PacketHandler(MultiServerSettings settings, TeleportExecutors executors, RedisService redisService) {
        this.settings = settings;
        this.executors = executors;
        this.redisService = redisService;
    }

    public void setCrossServerManager(CrossServerManager crossServerManager) {
        this.crossServerManager = crossServerManager;
    }

    public CompletableFuture<Void> send(Packet packet) {
        return attemptSend(packet, 0);
    }

    private CompletableFuture<Void> attemptSend(Packet packet, int attemptNo) {
        return CompletableFuture.runAsync(() -> redisService.publishPacket(packet), executors.io())
                .handle((result, error) -> error)
                .thenCompose(error -> {
                    if (error == null) {
                        return CompletableFuture.completedFuture(null);
                    }

                    int next = attemptNo + 1;
                    int max = settings.getRetryAttempts();
                    if (next >= max) {
                        LoggerUtils.logError("CRITICAL: failed to publish " + packet.getType()
                                + " after " + max + " attempts: " + error.getMessage());
                        return CompletableFuture.<Void>failedFuture(error);
                    }

                    long backoff = settings.getRetryBackoffMs() * next;
                    return CompletableFuture.supplyAsync(() -> null,
                                    executors.delayed(backoff, TimeUnit.MILLISECONDS))
                            .thenCompose(ignored -> attemptSend(packet, next));
                });
    }

    public void handle(Packet packet) {
        if (packet == null) {
            return;
        }

        if (!accept(packet)) {
            return;
        }

        try {
            switch (packet.getType()) {
                case TELEPORT_REQUEST -> handleTeleportRequest((TeleportRequestPacket) packet);
                case CACHE_INVALIDATE -> handleCacheInvalidate((CacheInvalidatePacket) packet);
                case SERVER_PRESENCE -> handleServerPresence((ServerPresencePacket) packet);
                case TPA_REQUEST -> handleTpaRequest((TpaRequestPacket) packet);
                case TPA_RESPONSE -> handleTpaResponse((TpaResponsePacket) packet);
                case TPA_TRAVEL -> handleTpaTravel((TpaTravelPacket) packet);
            }
        } catch (Exception e) {
            LoggerUtils.logException("PacketHandler#handle(" + packet.getType() + ")", e);
        }
    }

    private void handleTeleportRequest(TeleportRequestPacket packet) {
        if (packet.getSourceInstance() != null
                && packet.getSourceInstance().equals(settings.getInstanceId())) {
            return;
        }

        if (packet.getTargetServer() == null
                || !packet.getTargetServer().equalsIgnoreCase(settings.getServerName())) {
            return;
        }

        if (crossServerManager != null) {
            crossServerManager.handleIncoming(packet);
        }
    }

    private void handleCacheInvalidate(CacheInvalidatePacket packet) {
        if (packet.getSourceInstance() != null
                && packet.getSourceInstance().equals(settings.getInstanceId())) {
            return;
        }

        if (crossServerManager != null) {
            crossServerManager.handleInvalidation(packet);
        }
    }

    private void handleServerPresence(ServerPresencePacket packet) {
        if (packet.getSourceInstance() != null
                && packet.getSourceInstance().equals(settings.getInstanceId())) {
            return;
        }

        if (crossServerManager != null) {
            crossServerManager.handlePresence(packet);
        }
    }

    private void handleTpaRequest(TpaRequestPacket packet) {
        if (!addressedToUs(packet.getSourceInstance(), packet.getHandlerServer())) {
            return;
        }
        if (tpaManager() != null) {
            tpaManager().handleRemoteRequest(packet);
        }
    }

    private void handleTpaResponse(TpaResponsePacket packet) {
        if (!addressedToUs(packet.getSourceInstance(), packet.getHandlerServer())) {
            return;
        }
        if (tpaManager() != null) {
            tpaManager().handleRemoteResponse(packet);
        }
    }

    private void handleTpaTravel(TpaTravelPacket packet) {
        if (!addressedToUs(packet.getSourceInstance(), packet.getHandlerServer())) {
            return;
        }
        if (tpaManager() != null) {
            tpaManager().handleRemoteTravel(packet);
        }
    }

    private boolean accept(Packet packet) {
        if (!packet.isFresh(MAX_PACKET_AGE_MS)) {
            LoggerUtils.logWarn("Discarded a " + packet.getType() + " packet that is "
                    + Math.abs(System.currentTimeMillis() - packet.getSentAt()) / 1000
                    + "s out of date. Check that the servers' clocks agree.");
            return false;
        }

        purgeSeen();

        return seenPackets.putIfAbsent(packet.getPacketId(), packet.getSentAt()) == null;
    }

    private void purgeSeen() {
        if (seenPackets.size() < SEEN_PURGE_THRESHOLD) {
            return;
        }

        long cutoff = System.currentTimeMillis() - MAX_PACKET_AGE_MS;
        seenPackets.values().removeIf(sentAt -> sentAt < cutoff);
    }

    private boolean addressedToUs(String sourceInstance, String handlerServer) {
        if (sourceInstance != null && sourceInstance.equals(settings.getInstanceId())) {
            return false;
        }
        return handlerServer != null && handlerServer.equalsIgnoreCase(settings.getServerName());
    }

    private TpaManager tpaManager() {
        return crossServerManager == null ? null : crossServerManager.getPlugin().getTpaManager();
    }
}

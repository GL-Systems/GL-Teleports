package org.glstudio.teleports.common.network.packets;

import lombok.Getter;
import org.glstudio.teleports.common.network.Packet;
import org.glstudio.teleports.common.network.PacketType;
import org.glstudio.teleports.modules.model.PendingTeleport;
import org.glstudio.teleports.modules.model.TeleportDestination;
import org.glstudio.teleports.modules.model.TeleportType;

import java.util.UUID;

@Getter
public class TeleportRequestPacket extends Packet {
    private final String sourceServer;
    private final String sourceInstance;
    private final String targetServer;

    private final UUID playerUuid;
    private final String playerName;

    private final TeleportType teleportType;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final String destinationName;

    private final long timestamp;

    public TeleportRequestPacket(String sourceServer, String sourceInstance, String targetServer,
                                 UUID playerUuid, String playerName, TeleportType teleportType,
                                 TeleportDestination destination, String destinationName) {
        this.sourceServer = sourceServer;
        this.sourceInstance = sourceInstance;
        this.targetServer = targetServer;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.teleportType = teleportType;
        this.world = destination.getWorld();
        this.x = destination.getX();
        this.y = destination.getY();
        this.z = destination.getZ();
        this.yaw = destination.getYaw();
        this.pitch = destination.getPitch();
        this.destinationName = destinationName;
        this.timestamp = System.currentTimeMillis();
    }

    public PendingTeleport toPendingTeleport(long expiresAt) {
        return new PendingTeleport(teleportType, world, x, y, z, yaw, pitch, expiresAt, destinationName);
    }

    @Override
    public String destinationServer() {
        return targetServer;
    }

    @Override
    public PacketType getType() {
        return PacketType.TELEPORT_REQUEST;
    }
}

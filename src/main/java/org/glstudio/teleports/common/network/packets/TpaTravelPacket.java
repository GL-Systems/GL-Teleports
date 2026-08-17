package org.glstudio.teleports.common.network.packets;

import lombok.Getter;
import org.bukkit.Location;
import org.glstudio.teleports.common.network.Packet;
import org.glstudio.teleports.common.network.PacketType;
import org.glstudio.teleports.modules.model.RemoteDestination;
import org.glstudio.teleports.modules.model.TeleportType;

import java.util.UUID;

@Getter
public class TpaTravelPacket extends Packet {
    private final UUID id;

    private final String sourceInstance;
    private final String handlerServer;

    private final UUID travellerUuid;
    private final String travellerName;

    private final String destinationServer;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    private final TeleportType teleportType;
    private final long timestamp;

    public TpaTravelPacket(UUID id, String sourceInstance, String handlerServer, UUID travellerUuid,
                           String travellerName, String destinationServer, Location destination,
                           TeleportType teleportType) {
        this.id = id;
        this.sourceInstance = sourceInstance;
        this.handlerServer = handlerServer;
        this.travellerUuid = travellerUuid;
        this.travellerName = travellerName;
        this.destinationServer = destinationServer;
        this.world = destination.getWorld().getName();
        this.x = destination.getX();
        this.y = destination.getY();
        this.z = destination.getZ();
        this.yaw = destination.getYaw();
        this.pitch = destination.getPitch();
        this.teleportType = teleportType;
        this.timestamp = System.currentTimeMillis();
    }

    public RemoteDestination toDestination() {
        return new RemoteDestination(destinationServer, world, x, y, z, yaw, pitch);
    }

    @Override
    public String destinationServer() {
        return handlerServer;
    }

    @Override
    public PacketType getType() {
        return PacketType.TPA_TRAVEL;
    }
}

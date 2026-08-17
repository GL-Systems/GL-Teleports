package org.glstudio.teleports.common.network.packets;

import lombok.Getter;
import org.bukkit.Location;
import org.glstudio.teleports.common.network.Packet;
import org.glstudio.teleports.common.network.PacketType;
import org.glstudio.teleports.modules.model.RemoteDestination;
import org.glstudio.teleports.modules.model.TpaRequest;

import java.util.UUID;

@Getter
public class TpaResponsePacket extends Packet {
    private final UUID id;

    private final String sourceInstance;
    private final String handlerServer;

    private final TpaResponse response;

    private final UUID requesterUuid;
    private final String requesterName;

    private final UUID targetUuid;
    private final String targetName;
    private final String targetServer;

    private final TpaRequest.TpaType tpaType;

    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    private final long timestamp;

    public TpaResponsePacket(UUID id, String sourceInstance, String handlerServer, TpaResponse response,
                             UUID requesterUuid, String requesterName, UUID targetUuid,
                             String targetName, String targetServer, TpaRequest.TpaType tpaType,
                             Location accepterLocation) {
        this.id = id;
        this.sourceInstance = sourceInstance;
        this.handlerServer = handlerServer;
        this.response = response;
        this.requesterUuid = requesterUuid;
        this.requesterName = requesterName;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.targetServer = targetServer;
        this.tpaType = tpaType;

        this.world = accepterLocation == null ? null : accepterLocation.getWorld().getName();
        this.x = accepterLocation == null ? 0 : accepterLocation.getX();
        this.y = accepterLocation == null ? 0 : accepterLocation.getY();
        this.z = accepterLocation == null ? 0 : accepterLocation.getZ();
        this.yaw = accepterLocation == null ? 0 : accepterLocation.getYaw();
        this.pitch = accepterLocation == null ? 0 : accepterLocation.getPitch();

        this.timestamp = System.currentTimeMillis();
    }

    public RemoteDestination toDestination() {
        return new RemoteDestination(targetServer, world, x, y, z, yaw, pitch);
    }

    @Override
    public String destinationServer() {
        return handlerServer;
    }

    @Override
    public PacketType getType() {
        return PacketType.TPA_RESPONSE;
    }
}

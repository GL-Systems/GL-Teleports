package org.glstudio.teleports.common.network.packets;

import lombok.Getter;
import org.glstudio.teleports.common.network.Packet;
import org.glstudio.teleports.common.network.PacketType;
import org.glstudio.teleports.modules.model.TpaRequest;

import java.util.UUID;

/**
 * A teleport request sent to a player on another server.
 */
@Getter
public class TpaRequestPacket extends Packet {
    private final UUID id;

    private final String sourceInstance;
    private final String handlerServer;

    private final UUID requesterUuid;
    private final String requesterName;
    private final String requesterServer;

    private final UUID targetUuid;
    private final String targetName;

    private final TpaRequest.TpaType tpaType;
    private final long timestamp;

    /**
     * Creates the packet.
     */
    public TpaRequestPacket(UUID id, String sourceInstance, String handlerServer, UUID requesterUuid,
                            String requesterName, String requesterServer, UUID targetUuid,
                            String targetName, TpaRequest.TpaType tpaType) {
        this.id = id;
        this.sourceInstance = sourceInstance;
        this.handlerServer = handlerServer;
        this.requesterUuid = requesterUuid;
        this.requesterName = requesterName;
        this.requesterServer = requesterServer;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.tpaType = tpaType;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public String destinationServer() {
        return handlerServer;
    }

    @Override
    public PacketType getType() {
        return PacketType.TPA_REQUEST;
    }
}

package org.glstudio.teleports.common.network.packets;

import lombok.Getter;
import org.glstudio.teleports.common.network.Packet;
import org.glstudio.teleports.common.network.PacketType;

@Getter
public class ServerPresencePacket extends Packet {
    private final String sourceServer;
    private final String sourceInstance;
    private final boolean online;

    public ServerPresencePacket(String sourceServer, String sourceInstance, boolean online) {
        this.sourceServer = sourceServer;
        this.sourceInstance = sourceInstance;
        this.online = online;
    }

    @Override
    public PacketType getType() {
        return PacketType.SERVER_PRESENCE;
    }
}

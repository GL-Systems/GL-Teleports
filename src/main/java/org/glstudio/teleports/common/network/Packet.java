package org.glstudio.teleports.common.network;

import lombok.Getter;

import java.util.UUID;

@Getter
public abstract class Packet {
    private final UUID packetId = UUID.randomUUID();
    private final long sentAt = System.currentTimeMillis();

    public abstract PacketType getType();

    public String destinationServer() {
        return null;
    }

    public boolean isFresh(long toleranceMillis) {
        return Math.abs(System.currentTimeMillis() - sentAt) <= toleranceMillis;
    }
}

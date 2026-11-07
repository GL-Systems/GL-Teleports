package org.glstudio.teleports.common.network.packets;

import lombok.Getter;
import org.glstudio.teleports.common.network.Packet;
import org.glstudio.teleports.common.network.PacketType;

import java.util.UUID;

/**
 * Tells the other servers to drop cached data.
 */
@Getter
public class CacheInvalidatePacket extends Packet {
    private final String sourceServer;
    private final String sourceInstance;

    private final InvalidationScope scope;
    private final UUID owner;

    private final long timestamp;

    /**
     * Creates the packet.
     */
    public CacheInvalidatePacket(String sourceServer, String sourceInstance,
                                 InvalidationScope scope, UUID owner) {
        this.sourceServer = sourceServer;
        this.sourceInstance = sourceInstance;
        this.scope = scope;
        this.owner = owner;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public PacketType getType() {
        return PacketType.CACHE_INVALIDATE;
    }
}

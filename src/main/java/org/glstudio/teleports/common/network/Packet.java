package org.glstudio.teleports.common.network;

import org.glstudio.nexus.network.AbstractPacket;

/**
 * Base of the Redis packets: identity and freshness come from Nexus.
 */
public abstract class Packet extends AbstractPacket {

    /**
     * Returns the type of this packet.
     *
     * @return the type
     */
    public abstract PacketType getType();

    @Override
    public final String type() {
        return getType().name();
    }
}

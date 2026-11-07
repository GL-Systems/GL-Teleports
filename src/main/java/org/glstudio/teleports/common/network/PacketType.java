package org.glstudio.teleports.common.network;

import org.glstudio.teleports.common.network.packets.CacheInvalidatePacket;
import org.glstudio.teleports.common.network.packets.ServerPresencePacket;
import org.glstudio.teleports.common.network.packets.TeleportRequestPacket;
import org.glstudio.teleports.common.network.packets.TpaRequestPacket;
import org.glstudio.teleports.common.network.packets.TpaResponsePacket;
import org.glstudio.teleports.common.network.packets.TpaTravelPacket;

/**
 * The packets the plugin exchanges.
 */
public enum PacketType {
    /** A teleport to carry out on another server. */
    TELEPORT_REQUEST(TeleportRequestPacket.class),
    /** Cached data another server must drop. */
    CACHE_INVALIDATE(CacheInvalidatePacket.class),
    /** A server went online or offline. */
    SERVER_PRESENCE(ServerPresencePacket.class),
    /** A teleport request sent to a player on another server. */
    TPA_REQUEST(TpaRequestPacket.class),
    /** The answer to a teleport request. */
    TPA_RESPONSE(TpaResponsePacket.class),
    /** A player travelling because of an accepted request. */
    TPA_TRAVEL(TpaTravelPacket.class);

    private final Class<? extends Packet> packetClass;

    PacketType(Class<? extends Packet> packetClass) {
        this.packetClass = packetClass;
    }

    /**
     * Finds the class of a packet by its wire name.
     *
     * @param name the wire name
     * @return the class, or {@code null} when unknown
     */
    public static Class<? extends Packet> classOf(String name) {
        for (PacketType type : values()) {
            if (type.name().equals(name)) {
                return type.packetClass;
            }
        }
        return null;
    }
}

package org.glstudio.teleports.common.network;

import com.google.gson.Gson;
import org.glstudio.nexus.network.PacketCodec;
import org.glstudio.teleports.common.network.packets.ServerPresencePacket;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Servers on the previous version keep talking to this one during a staggered update. */
class PacketWireFormatTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final PacketCodec<Packet> codec = new PacketCodec<>(new Gson(), PacketType::classOf);

    private static String previousVersionMessage(String secret) {
        String json = "{\"sourceServer\":\"lobby\",\"online\":true,"
                + "\"packetId\":\"11111111-2222-3333-4444-555555555555\",\"sentAt\":1700000000000}";
        String payload = "SERVER_PRESENCE:"
                + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return payload + ":" + PacketCodec.sign(payload, secret);
    }

    @Test
    void readsAMessageOfThePreviousVersion() {
        Packet packet = codec.decode(previousVersionMessage(SECRET), SECRET);

        assertInstanceOf(ServerPresencePacket.class, packet);
        assertEquals("11111111-2222-3333-4444-555555555555", packet.getPacketId().toString());
        assertEquals(1700000000000L, packet.getSentAt());
    }

    @Test
    void rejectsAMessageSignedWithAnotherSecret() {
        assertThrows(SecurityException.class,
                () -> codec.decode(previousVersionMessage("ffffffffffffffffffffffffffffffff"), SECRET));
    }

    @Test
    void writesTypeThenBase64ThenSignature() {
        String wire = codec.encode(codec.decode(previousVersionMessage(SECRET), SECRET), SECRET);

        assertTrue(wire.startsWith("SERVER_PRESENCE:"));
        assertEquals(3, wire.split(":", 3).length);
    }
}

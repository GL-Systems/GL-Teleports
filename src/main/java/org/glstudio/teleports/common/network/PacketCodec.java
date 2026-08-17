package org.glstudio.teleports.common.network;

import com.google.gson.Gson;
import org.glstudio.teleports.common.network.packets.CacheInvalidatePacket;
import org.glstudio.teleports.common.network.packets.ServerPresencePacket;
import org.glstudio.teleports.common.network.packets.TeleportRequestPacket;
import org.glstudio.teleports.common.network.packets.TpaRequestPacket;
import org.glstudio.teleports.common.network.packets.TpaResponsePacket;
import org.glstudio.teleports.common.network.packets.TpaTravelPacket;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class PacketCodec {
    private static final Gson GSON = new Gson();
    private static final String SEPARATOR = ":";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private PacketCodec() {
    }

    public static String encode(Packet packet, String secret) {
        String json = GSON.toJson(packet);
        String payload = packet.getType().name() + SEPARATOR
                + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        String signature = sign(payload, secret);
        return payload + SEPARATOR + signature;
    }

    public static Packet decode(String message, String secret) {
        String[] parts = message.split(SEPARATOR, 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed packet");
        }

        String type = parts[0];
        String base64Json = parts[1];
        String signature = parts[2];

        String payload = type + SEPARATOR + base64Json;
        String expected = sign(payload, secret);
        if (!constantTimeEquals(expected, signature)) {
            throw new SecurityException("Packet signature verification failed");
        }

        String json = new String(Base64.getDecoder().decode(base64Json), StandardCharsets.UTF_8);
        PacketType packetType = PacketType.valueOf(type);
        return switch (packetType) {
            case TELEPORT_REQUEST -> GSON.fromJson(json, TeleportRequestPacket.class);
            case CACHE_INVALIDATE -> GSON.fromJson(json, CacheInvalidatePacket.class);
            case SERVER_PRESENCE -> GSON.fromJson(json, ServerPresencePacket.class);
            case TPA_REQUEST -> GSON.fromJson(json, TpaRequestPacket.class);
            case TPA_RESPONSE -> GSON.fromJson(json, TpaResponsePacket.class);
            case TPA_TRAVEL -> GSON.fromJson(json, TpaTravelPacket.class);
        };
    }

    private static String sign(String payload, String secret) {
        if (secret == null || secret.isEmpty()) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign packet", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}

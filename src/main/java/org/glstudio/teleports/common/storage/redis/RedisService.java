package org.glstudio.teleports.common.storage.redis;

import com.google.gson.Gson;
import org.glstudio.nexus.concurrent.NexusExecutors;
import org.glstudio.nexus.network.PacketChannel;
import org.glstudio.nexus.network.PacketCodec;
import org.glstudio.nexus.network.RedisConnector;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.common.config.MultiServerSettings;
import org.glstudio.teleports.common.network.Packet;
import org.glstudio.teleports.common.network.PacketHandler;
import org.glstudio.teleports.common.network.PacketType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Connects the plugin to Redis: broadcasts over pub/sub, addressed packets over a per-server stream,
 * and the keys and sets the other services share.
 */
public class RedisService {
    private static final int MAX_CONNECTIONS = 16;
    private static final String STREAM_GROUP = "gl-teleports";

    private final NexusExecutors executors;
    private final MultiServerSettings settings;
    private final RedisConnector connector;
    private final PacketChannel channel;
    private final PacketCodec<Packet> codec = new PacketCodec<>(new Gson(), PacketType::classOf);

    private volatile PacketHandler packetHandler;

    /**
     * Creates the service without connecting.
     *
     * @param executors the plugin's executors
     * @param settings  the multi-server settings
     */
    public RedisService(NexusExecutors executors, MultiServerSettings settings) {
        this.executors = executors;
        this.settings = settings;
        this.connector = new RedisConnector(settings.getNetwork(), MAX_CONNECTIONS);
        this.channel = new PacketChannel(connector, settings.getChannel(), "GL-Teleports");
    }

    /**
     * Connects to Redis.
     *
     * @return completes when connected, fails when Redis is unreachable or misconfigured
     */
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                connector.connect("move players at will");
            } catch (Exception e) {
                LoggerUtils.logError("Failed to connect to Redis: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, executors.io());
    }

    /**
     * Sets who receives the packets.
     *
     * @param packetHandler the handler
     */
    public void setPacketHandler(PacketHandler packetHandler) {
        this.packetHandler = packetHandler;
    }

    /** Starts listening to the broadcast channel. */
    public void startSubscriber() {
        if (connector.isConnected()) {
            channel.subscribe(this::handleMessage);
        }
    }

    /** Starts reading the packets addressed to this server. */
    public void startConsumer() {
        if (connector.isConnected()) {
            channel.consumeStream(settings.getServerName(), STREAM_GROUP, this::handleMessage);
        }
    }

    private void handleMessage(String message) {
        try {
            Packet packet = codec.decode(message, settings.getSecret());
            if (packetHandler != null) {
                packetHandler.handle(packet);
            }
        } catch (SecurityException e) {
            LoggerUtils.logWarn("Rejected Redis message on " + settings.getChannel() + ": " + e.getMessage());
        } catch (Exception e) {
            LoggerUtils.logException("RedisService#handleMessage(" + settings.getChannel() + ")", e);
        }
    }

    /**
     * Sends a packet to its destination server, or to every server when it has none.
     *
     * @param packet the packet
     */
    public void publishPacket(Packet packet) {
        String payload = codec.encode(packet, settings.getSecret());
        String destination = packet.destinationServer();

        if (destination != null) {
            channel.sendTo(destination, payload);
            return;
        }

        long receivers = channel.publish(payload);
        if (receivers == 0) {
            LoggerUtils.logDebug("RedisService", "No server is subscribed to " + settings.getChannel()
                    + " right now; nothing else on the network will react to this " + packet.getType() + ".");
        }
    }

    /**
     * Reads a key and deletes it.
     *
     * @param key the key
     * @return its value, or {@code null}
     */
    public String getAndDelete(String key) {
        return connector.getAndDelete(key);
    }

    /**
     * Deletes a key if it still belongs to the owner.
     *
     * @param key   the key
     * @param owner the expected owner
     */
    public void deleteIfOwnedBy(String key, String owner) {
        connector.deleteIfOwnedBy(key, owner);
    }

    /**
     * Deletes a key if it still holds a value.
     *
     * @param key      the key
     * @param expected the expected value
     */
    public void deleteIfValueIs(String key, String expected) {
        connector.deleteIfValueIs(key, expected);
    }

    /**
     * Deletes several keys.
     *
     * @param keys the keys
     */
    public void deleteAll(Collection<String> keys) {
        connector.deleteAll(keys);
    }

    /**
     * Writes a key that expires.
     *
     * @param key        the key
     * @param value      the value
     * @param ttlSeconds when it expires
     */
    public void set(String key, String value, long ttlSeconds) {
        connector.set(key, value, ttlSeconds);
    }

    /**
     * Reads a key.
     *
     * @param key the key
     * @return its value, or {@code null}
     */
    public String get(String key) {
        return connector.get(key);
    }

    /**
     * Deletes a key.
     *
     * @param key the key
     */
    public void delete(String key) {
        connector.delete(key);
    }

    /**
     * Reads how long a key has left.
     *
     * @param key the key
     * @return what Redis answers
     */
    public long remainingTtl(String key) {
        return connector.remainingTtl(key);
    }

    /**
     * Reads how long several keys have left.
     *
     * @param keys the keys
     * @return the time left of each key
     */
    public Map<String, Long> remainingTtls(Collection<String> keys) {
        return connector.remainingTtls(keys);
    }

    /**
     * Reads several keys.
     *
     * @param keys the keys
     * @return their values, in order
     */
    public List<String> getAll(List<String> keys) {
        return connector.getAll(keys);
    }

    /**
     * Adds a member to a set.
     *
     * @param key    the set
     * @param member the member
     */
    public void addToSet(String key, String member) {
        connector.addToSet(key, member);
    }

    /**
     * Removes a member from a set.
     *
     * @param key    the set
     * @param member the member
     */
    public void removeFromSet(String key, String member) {
        connector.removeFromSet(key, member);
    }

    /**
     * Reads a set.
     *
     * @param key the set
     * @return its members
     */
    public Set<String> membersOf(String key) {
        return connector.membersOf(key);
    }

    /**
     * Tells whether the connection is up.
     *
     * @return whether it is connected
     */
    public boolean isRunning() {
        return connector.isConnected();
    }

    /** Stops listening and closes the connection. */
    public void close() {
        channel.close();
        connector.close();
    }
}

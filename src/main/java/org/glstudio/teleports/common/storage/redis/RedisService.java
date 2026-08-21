package org.glstudio.teleports.common.storage.redis;

import lombok.Getter;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.common.network.Packet;
import org.glstudio.teleports.common.network.PacketCodec;
import org.glstudio.teleports.common.network.PacketHandler;
import org.glstudio.teleports.common.config.MultiServerSettings;
import org.glstudio.teleports.common.concurrent.TeleportExecutors;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Getter
public class RedisService {
    private static final long MAX_BACKOFF_MS = 60_000L;
    private static final long INITIAL_BACKOFF_MS = 5_000L;

    private static final int MINIMUM_SECRET_LENGTH = 32;

    private static final String STREAM_GROUP = "gl-teleports";
    private static final String STREAM_FIELD = "p";
    private static final long STREAM_MAX_LENGTH = 2_000L;
    private static final int STREAM_BATCH = 32;
    private static final int STREAM_BLOCK_MS = 2_000;

    private final TeleportExecutors executors;
    private final MultiServerSettings settings;
    private volatile JedisPool jedisPool;

    private volatile PacketHandler packetHandler;

    private Thread subscriberThread;
    private Thread consumerThread;
    private volatile JedisPubSub subscription;
    private volatile boolean running;

    private volatile String teleportChannel;
    private volatile String secret;

    public RedisService(TeleportExecutors executors, MultiServerSettings settings) {
        this.executors = executors;
        this.settings = settings;
    }

    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                String host = settings.getRedisHost();
                int port = settings.getRedisPort();
                String password = settings.getRedisPassword();
                boolean allowInsecure = settings.isRedisAllowInsecure();

                if ((password == null || password.isEmpty()) && !allowInsecure) {
                    throw new IllegalStateException("Redis has no password. Set multi-server.redis.password, or "
                            + "multi-server.redis.allow-insecure: true to override (not recommended).");
                }

                this.teleportChannel = settings.getChannel();
                this.secret = settings.getSecret();

                if (secret == null || secret.length() < MINIMUM_SECRET_LENGTH) {
                    if (!allowInsecure) {
                        throw new IllegalStateException("multi-server.secret must be at least "
                                + MINIMUM_SECRET_LENGTH + " characters and shared by every server on the "
                                + "channel; without it any client that can publish to Redis could move "
                                + "players at will. Set it, or multi-server.redis.allow-insecure: true to "
                                + "override (not recommended).");
                    }

                    LoggerUtils.logWarn("multi-server.secret is empty or too short; teleport packets are "
                            + "unsigned and unauthenticated (anyone able to publish to Redis could move "
                            + "players)");
                }

                JedisPoolConfig poolConfig = new JedisPoolConfig();
                poolConfig.setMaxTotal(16);
                poolConfig.setMaxIdle(8);
                poolConfig.setMinIdle(2);

                if (password == null || password.isEmpty()) {
                    this.jedisPool = new JedisPool(poolConfig, host, port);
                } else {
                    this.jedisPool = new JedisPool(poolConfig, host, port, 2000, password);
                }

                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.ping();
                }

                this.running = true;

                LoggerUtils.logSuccess("Redis connection established");
            } catch (Exception e) {
                LoggerUtils.logError("Failed to connect to Redis: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, executors.io());
    }

    public void setPacketHandler(PacketHandler packetHandler) {
        this.packetHandler = packetHandler;
    }

    public void startSubscriber() {
        if (!running || subscriberThread != null) {
            return;
        }

        subscriberThread = new Thread(() -> {
            long backoff = INITIAL_BACKOFF_MS;
            while (running) {
                try (Jedis jedis = jedisPool.getResource()) {
                    LoggerUtils.logInfo("Redis subscriber connected");
                    backoff = INITIAL_BACKOFF_MS;
                    this.subscription = new JedisPubSub() {
                        @Override
                        public void onMessage(String channel, String message) {
                            handleMessage(channel, message);
                        }
                    };
                    jedis.subscribe(subscription, teleportChannel);
                } catch (Exception e) {
                    if (!running) {
                        break;
                    }
                    LoggerUtils.logWarn("Redis subscriber disconnected: " + e.getMessage()
                            + " (retrying in " + (backoff / 1000) + "s)");
                }

                if (!running) {
                    break;
                }

                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
            }
        }, "GL-Teleports-Redis-Subscriber");

        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    private void handleMessage(String channel, String message) {
        try {
            Packet packet = PacketCodec.decode(message, secret);
            if (packetHandler != null) {
                packetHandler.handle(packet);
            }
        } catch (SecurityException e) {
            LoggerUtils.logWarn("Rejected Redis message on " + channel + ": " + e.getMessage());
        } catch (Exception e) {
            LoggerUtils.logException("RedisService#handleMessage(" + channel + ")", e);
        }
    }

    public void publishPacket(Packet packet) {
        String destination = packet.destinationServer();

        if (destination != null) {
            deliverToStream(destination, PacketCodec.encode(packet, secret));
            return;
        }

        long receivers;
        try (Jedis jedis = jedisPool.getResource()) {
            receivers = jedis.publish(teleportChannel, PacketCodec.encode(packet, secret));
        }

        if (receivers == 0) {
            LoggerUtils.logWarn("Nobody is subscribed to '" + teleportChannel + "'; other servers will "
                    + "serve stale home/warp data until their cache expires.");
        }
    }

    private void deliverToStream(String destination, String payload) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.xadd(streamKey(destination),
                    XAddParams.xAddParams().maxLen(STREAM_MAX_LENGTH).approximateTrimming(),
                    Map.of(STREAM_FIELD, payload));
        }
    }

    public void startConsumer() {
        if (!running || consumerThread != null) {
            return;
        }

        String stream = streamKey(settings.getServerName());
        String consumer = settings.getServerName();

        consumerThread = new Thread(() -> {
            long backoff = INITIAL_BACKOFF_MS;

            StreamEntryID cursor = new StreamEntryID(0, 0);

            while (running) {
                try (Jedis jedis = jedisPool.getResource()) {
                    ensureGroup(jedis, stream);
                    backoff = INITIAL_BACKOFF_MS;

                    while (running) {
                        List<Map.Entry<String, List<StreamEntry>>> batches = jedis.xreadGroup(
                                STREAM_GROUP, consumer,
                                XReadGroupParams.xReadGroupParams().count(STREAM_BATCH).block(STREAM_BLOCK_MS),
                                Map.of(stream, cursor));

                        int handled = consume(jedis, stream, batches);

                        if (handled == 0 && !StreamEntryID.UNRECEIVED_ENTRY.equals(cursor)) {
                            cursor = StreamEntryID.UNRECEIVED_ENTRY;
                        }
                    }
                } catch (Exception e) {
                    if (!running) {
                        break;
                    }

                    LoggerUtils.logWarn("Redis stream consumer stopped: " + e.getMessage()
                            + " (retrying in " + (backoff / 1000) + "s)");

                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
                }
            }
        }, "GL-Teleports-Redis-Stream");

        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    private int consume(Jedis jedis, String stream, List<Map.Entry<String, List<StreamEntry>>> batches) {
        if (batches == null || batches.isEmpty()) {
            return 0;
        }

        int handled = 0;

        for (Map.Entry<String, List<StreamEntry>> batch : batches) {
            for (StreamEntry entry : batch.getValue()) {
                handled++;

                try {
                    String payload = entry.getFields().get(STREAM_FIELD);
                    if (payload != null) {
                        handleMessage(stream, payload);
                    }
                } finally {
                    jedis.xack(stream, STREAM_GROUP, entry.getID());
                }
            }
        }

        return handled;
    }

    private void ensureGroup(Jedis jedis, String stream) {
        try {
            jedis.xgroupCreate(stream, STREAM_GROUP, StreamEntryID.XGROUP_LAST_ENTRY, true);
        } catch (Exception e) {
            if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) {
                throw e;
            }
        }
    }

    private String streamKey(String server) {
        return teleportChannel + ":stream:" + server.toLowerCase(Locale.ROOT);
    }

    public String getAndDelete(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.getDel(key);
        }
    }

    public void deleteIfOwnedBy(String key, String owner) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.eval(
                    "local v = redis.call('GET', KEYS[1]) "
                            + "if v and string.sub(v, -string.len(ARGV[1])) == ARGV[1] then "
                            + "return redis.call('DEL', KEYS[1]) end return 0",
                    List.of(key), List.of(";" + owner));
        }
    }

    public void deleteIfValueIs(String key, String expected) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.eval(
                    "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0",
                    List.of(key), List.of(expected));
        }
    }

    public void deleteAll(Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(keys.toArray(new String[0]));
        }
    }

    public void set(String key, String value, long ttlSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(key, ttlSeconds, value);
        }
    }

    public String get(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(key);
        }
    }

    public void delete(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
        }
    }

    public long remainingTtl(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            long ttl = jedis.ttl(key);
            return ttl > 0 ? ttl : -1L;
        }
    }

    public Map<String, Long> remainingTtls(Collection<String> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }

        Map<String, Long> found = new HashMap<>();

        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            Map<String, Response<Long>> responses = new LinkedHashMap<>();

            for (String key : keys) {
                responses.put(key, pipeline.ttl(key));
            }

            pipeline.sync();

            responses.forEach((key, response) -> {
                Long ttl = response.get();
                if (ttl != null && ttl > 0) {
                    found.put(key, ttl);
                }
            });
        }

        return found;
    }

    public List<String> getAll(List<String> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.mget(keys.toArray(new String[0]));
        }
    }

    public void addToSet(String key, String member) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd(key, member);
        }
    }

    public void removeFromSet(String key, String member) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.srem(key, member);
        }
    }

    public Set<String> membersOf(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.smembers(key);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            running = false;

            JedisPubSub sub = this.subscription;
            if (sub != null && sub.isSubscribed()) {
                try {
                    sub.unsubscribe();
                } catch (Exception ignored) {
                }
            }

            if (subscriberThread != null) {
                subscriberThread.interrupt();
            }

            if (consumerThread != null) {
                consumerThread.interrupt();
            }

            if (jedisPool != null && !jedisPool.isClosed()) {
                jedisPool.close();
            }
        });
    }
}

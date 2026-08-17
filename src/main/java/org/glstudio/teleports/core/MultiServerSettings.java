package org.glstudio.teleports.core;

import lombok.Getter;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.common.storage.redis.RedisChannels;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class MultiServerSettings {
    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private static final Set<String> LEGACY_KEYS_SEEN = ConcurrentHashMap.newKeySet();

    private final boolean enabled;
    private final String serverName;

    private final String instanceId;

    private final String channel;
    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;
    private final boolean redisAllowInsecure;
    private final String secret;

    private final int retryAttempts;
    private final long retryBackoffMs;

    private final int arrivalDelayTicks;

    private final int pendingExpirySeconds;

    public MultiServerSettings(ConfigFile config) {
        this.enabled = getBoolean(config, "multi-server.enabled", "MULTI_SERVER.ENABLED");
        this.serverName = orDefault(resolve(getString(config, "multi-server.server-name",
                "MULTI_SERVER.SERVER_NAME")), "unnamed-server");
        this.instanceId = UUID.randomUUID().toString();

        this.channel = orDefault(getString(config, "multi-server.redis.channel",
                "MULTI_SERVER.REDIS.CHANNEL"), RedisChannels.TELEPORTS);
        this.redisHost = orDefault(resolve(getString(config, "multi-server.redis.host",
                "MULTI_SERVER.REDIS.HOST")), "localhost");
        this.redisPort = getInt(config, "multi-server.redis.port", "MULTI_SERVER.REDIS.PORT", 6379);
        this.redisPassword = resolve(getString(config, "multi-server.redis.password",
                "MULTI_SERVER.REDIS.PASSWORD"));
        this.redisAllowInsecure = getBoolean(config, "multi-server.redis.allow-insecure",
                "MULTI_SERVER.REDIS.ALLOW_INSECURE");
        this.secret = resolve(getString(config, "multi-server.secret", "MULTI_SERVER.SECRET"));

        this.retryAttempts = Math.max(1, getInt(config, "multi-server.retry.attempts",
                "MULTI_SERVER.RETRY.ATTEMPTS", 3));
        this.retryBackoffMs = Math.max(0, getInt(config, "multi-server.retry.backoff-ms",
                "MULTI_SERVER.RETRY.BACKOFF_MS", 100));

        this.arrivalDelayTicks = Math.max(0, getInt(config, "multi-server.arrival-delay",
                "MULTI_SERVER.ARRIVAL_DELAY", 0));

        int configuredExpiry = Math.max(5, getInt(config, "multi-server.pending-expiry-seconds",
                "MULTI_SERVER.PENDING_EXPIRY_SECONDS", 120));
        this.pendingExpirySeconds = Math.max(configuredExpiry, (arrivalDelayTicks / 20) + 5);
    }

    public long getPendingExpiryMillis() {
        return pendingExpirySeconds * 1000L;
    }

    public boolean isLocalServer(String server) {
        return server == null || server.isBlank() || server.equalsIgnoreCase(serverName);
    }

    public boolean differsFrom(MultiServerSettings other) {
        return enabled != other.enabled
                || redisPort != other.redisPort
                || redisAllowInsecure != other.redisAllowInsecure
                || retryAttempts != other.retryAttempts
                || retryBackoffMs != other.retryBackoffMs
                || arrivalDelayTicks != other.arrivalDelayTicks
                || pendingExpirySeconds != other.pendingExpirySeconds
                || !Objects.equals(serverName, other.serverName)
                || !Objects.equals(channel, other.channel)
                || !Objects.equals(redisHost, other.redisHost)
                || !Objects.equals(redisPassword, other.redisPassword)
                || !Objects.equals(secret, other.secret);
    }

    private static boolean hasLegacy(ConfigFile file, String path, String legacyPath) {
        if (file.contains(path) || !file.contains(legacyPath)) {
            return false;
        }

        LEGACY_KEYS_SEEN.add(legacyPath + " -> " + path);
        return true;
    }

    public static Set<String> legacyKeysSeen() {
        return Set.copyOf(LEGACY_KEYS_SEEN);
    }

    private static String getString(ConfigFile file, String path, String legacyPath) {
        return hasLegacy(file, path, legacyPath) ? file.getString(legacyPath) : file.getString(path);
    }

    private static boolean getBoolean(ConfigFile file, String path, String legacyPath) {
        return hasLegacy(file, path, legacyPath) ? file.getBoolean(legacyPath) : file.getBoolean(path);
    }

    private static int getInt(ConfigFile file, String path, String legacyPath, int fallback) {
        if (hasLegacy(file, path, legacyPath)) {
            return file.getInt(legacyPath);
        }
        return file.isExist(path) ? file.getInt(path) : fallback;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static String resolve(String value) {
        if (value == null || value.indexOf('$') < 0) {
            return value;
        }
        Matcher matcher = ENV_PATTERN.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String env = System.getenv(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(env == null ? "" : env));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}

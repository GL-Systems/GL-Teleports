package org.glstudio.teleports.core;

import lombok.Getter;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.common.storage.redis.RedisChannels;

import java.util.Objects;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class MultiServerSettings {

    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

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

    public MultiServerSettings(ConfigFile config) {
        this.enabled = config.getBoolean("MULTI_SERVER.ENABLED");
        this.serverName = orDefault(resolve(config.getString("MULTI_SERVER.SERVER_NAME")), "unnamed-server");
        this.instanceId = UUID.randomUUID().toString();

        this.channel = orDefault(config.getString("MULTI_SERVER.REDIS.CHANNEL"), RedisChannels.TELEPORTS);
        this.redisHost = orDefault(resolve(config.getString("MULTI_SERVER.REDIS.HOST")), "localhost");
        this.redisPort = getInt(config, "MULTI_SERVER.REDIS.PORT", 6379);
        this.redisPassword = resolve(config.getString("MULTI_SERVER.REDIS.PASSWORD"));
        this.redisAllowInsecure = config.getBoolean("MULTI_SERVER.REDIS.ALLOW_INSECURE");
        this.secret = resolve(config.getString("MULTI_SERVER.SECRET"));

        this.retryAttempts = Math.max(1, getInt(config, "MULTI_SERVER.RETRY.ATTEMPTS", 3));
        this.retryBackoffMs = Math.max(0, getInt(config, "MULTI_SERVER.RETRY.BACKOFF_MS", 100));

        this.arrivalDelayTicks = Math.max(0, getInt(config, "MULTI_SERVER.ARRIVAL_DELAY", 30));
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
                || !Objects.equals(serverName, other.serverName)
                || !Objects.equals(channel, other.channel)
                || !Objects.equals(redisHost, other.redisHost)
                || !Objects.equals(redisPassword, other.redisPassword)
                || !Objects.equals(secret, other.secret);
    }

    private static int getInt(ConfigFile file, String path, int fallback) {
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

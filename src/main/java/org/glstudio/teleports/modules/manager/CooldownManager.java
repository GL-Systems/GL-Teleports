package org.glstudio.teleports.modules.manager;

import org.bukkit.entity.Player;
import org.glstudio.nexus.utils.Cooldowns;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.model.TeleportRules;
import org.glstudio.teleports.modules.model.TeleportType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holds teleport cooldowns.
 */
public class CooldownManager {
    public static final String BYPASS_PERMISSION = Permissions.BYPASS_COOLDOWN;

    private final GTeleports plugin;
    private final Cooldowns cooldowns = new Cooldowns();

    /**
     * Creates the manager.
     */
    public CooldownManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Tells whether a player must still wait.
     */
    public boolean hasCooldown(Player player, TeleportType type) {
        return remainingMillis(player, type) > 0;
    }

    /**
     * Returns the remaining cooldown.
     */
    public long getRemainingCooldown(Player player, TeleportType type) {
        long remaining = remainingMillis(player, type);
        return remaining <= 0 ? 0 : (remaining + 999) / 1000;
    }

    private long remainingMillis(Player player, TeleportType type) {
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return 0;
        }
        return cooldowns.remainingMillis(player.getUniqueId(), type.name());
    }

    /**
     * Sets the cooldown.
     */
    public void setCooldown(Player player, TeleportType type) {
        setCooldown(player, type, TeleportRules.global(plugin, type).cooldownSeconds());
    }

    /**
     * Sets the cooldown.
     */
    public void setCooldown(Player player, TeleportType type, int seconds) {
        if (player.hasPermission(BYPASS_PERMISSION) || seconds <= 0) {
            return;
        }

        cooldowns.start(player.getUniqueId(), type.name(), Duration.ofSeconds(seconds));
        publish(player.getUniqueId(), type, seconds);
    }

    /**
     * Removes the cooldown.
     */
    public void removeCooldown(Player player, TeleportType type) {
        cooldowns.clear(player.getUniqueId(), type.name());
        withRedis(redis -> redis.delete(redisKey(player.getUniqueId(), type)));
    }

    /**
     * Clears the all cooldowns.
     */
    public void clearAllCooldowns(Player player) {
        forget(player.getUniqueId());
    }

    /**
     * Forgets what is held for a player.
     */
    public void forget(UUID uuid) {
        cooldowns.forget(uuid);
    }

    /**
     * Loads the network cooldowns.
     */
    public void loadNetworkCooldowns(UUID uuid) {
        if (!networkEnabled()) {
            return;
        }

        var redis = plugin.getRedisService();
        if (redis == null || !redis.isRunning()) {
            return;
        }

        plugin.getExecutors().io().execute(() -> {
            try {
                List<String> keys = new ArrayList<>();
                for (TeleportType type : TeleportType.values()) {
                    keys.add(redisKey(uuid, type));
                }

                Map<String, Long> found = redis.remainingTtls(keys);

                for (TeleportType type : TeleportType.values()) {
                    Long ttl = found.get(redisKey(uuid, type));
                    if (ttl != null && ttl > 0) {
                        cooldowns.start(uuid, type.name(), Duration.ofSeconds(ttl));
                    }
                }
            } catch (Exception e) {
                LoggerUtils.logDebug("CooldownManager",
                        "Could not read network cooldowns for " + uuid + ": " + e.getMessage());
            }
        });
    }

    private void publish(UUID uuid, TeleportType type, int seconds) {
        withRedis(redis -> redis.set(redisKey(uuid, type), "1", seconds));
    }

    private void withRedis(java.util.function.Consumer<org.glstudio.teleports.common.storage.redis.RedisService> action) {
        if (!networkEnabled()) {
            return;
        }

        var redis = plugin.getRedisService();
        if (redis == null || !redis.isRunning()) {
            return;
        }

        plugin.getExecutors().io().execute(() -> {
            try {
                action.accept(redis);
            } catch (Exception e) {
                LoggerUtils.logDebug("CooldownManager", "Redis cooldown write failed: " + e.getMessage());
            }
        });
    }

    private boolean networkEnabled() {
        return plugin.getSettings().getBoolean("cooldowns.network", true)
                && plugin.getCrossServerManager() != null
                && plugin.getCrossServerManager().isEnabled();
    }

    private String redisKey(UUID uuid, TeleportType type) {
        String channel = plugin.getMultiServerSettings() == null
                ? "gl-teleports" : plugin.getMultiServerSettings().getChannel();
        return channel + ":cd:" + uuid + ":" + type.name();
    }

    /**
     * Stops this on shutdown.
     */
    public void safeDisable() {
        cooldowns.clear();
    }
}

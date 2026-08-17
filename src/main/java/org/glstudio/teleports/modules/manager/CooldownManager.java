package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.Permissions;
import org.glstudio.teleports.core.TeleportRules;
import org.glstudio.teleports.modules.model.TeleportType;
import org.bukkit.entity.Player;
import org.glstudio.nexus.utils.LoggerUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {
    public static final String BYPASS_PERMISSION = Permissions.BYPASS_COOLDOWN;

    private final GTeleports plugin;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public CooldownManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    public boolean hasCooldown(Player player, TeleportType type) {
        return remainingMillis(player, type) > 0;
    }

    public long getRemainingCooldown(Player player, TeleportType type) {
        long remaining = remainingMillis(player, type);
        return remaining <= 0 ? 0 : (remaining + 999) / 1000;
    }

    private long remainingMillis(Player player, TeleportType type) {
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return 0;
        }

        Long end = cooldowns.get(key(player.getUniqueId(), type));
        if (end == null) {
            return 0;
        }

        long remaining = end - System.currentTimeMillis();
        if (remaining <= 0) {
            cooldowns.remove(key(player.getUniqueId(), type), end);
            return 0;
        }

        return remaining;
    }

    public void setCooldown(Player player, TeleportType type) {
        setCooldown(player, type, TeleportRules.global(plugin, type).cooldownSeconds());
    }

    public void setCooldown(Player player, TeleportType type, int seconds) {
        if (player.hasPermission(BYPASS_PERMISSION) || seconds <= 0) {
            return;
        }

        cooldowns.put(key(player.getUniqueId(), type), System.currentTimeMillis() + (seconds * 1000L));
        publish(player.getUniqueId(), type, seconds);
    }

    public void removeCooldown(Player player, TeleportType type) {
        cooldowns.remove(key(player.getUniqueId(), type));
        withRedis(redis -> redis.delete(redisKey(player.getUniqueId(), type)));
    }

    public void clearAllCooldowns(Player player) {
        forget(player.getUniqueId());
    }

    public void forget(UUID uuid) {
        String prefix = uuid + ":";
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
    }

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
                        cooldowns.put(key(uuid, type), System.currentTimeMillis() + (ttl * 1000L));
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

    private static String key(UUID uuid, TeleportType type) {
        return uuid + ":" + type.name();
    }

    public void safeDisable() {
        cooldowns.clear();
    }
}

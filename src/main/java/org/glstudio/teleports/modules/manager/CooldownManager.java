package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.TeleportType;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final GTeleports plugin;
    private final Map<UUID, Map<TeleportType, Long>> cooldowns;

    public CooldownManager(GTeleports plugin) {
        this.plugin = plugin;
        this.cooldowns = new ConcurrentHashMap<>();
    }

    public boolean hasCooldown(Player player, TeleportType type) {
        if (player.hasPermission("golden.teleports.bypass.cooldown")) {
            return false;
        }

        if (!cooldowns.containsKey(player.getUniqueId())) {
            return false;
        }

        Map<TeleportType, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (!playerCooldowns.containsKey(type)) {
            return false;
        }

        long cooldownEnd = playerCooldowns.get(type);
        return System.currentTimeMillis() < cooldownEnd;
    }

    public long getRemainingCooldown(Player player, TeleportType type) {
        if (!hasCooldown(player, type)) {
            return 0;
        }

        long cooldownEnd = cooldowns.get(player.getUniqueId()).get(type);
        return (cooldownEnd - System.currentTimeMillis()) / 1000;
    }

    public void setCooldown(Player player, TeleportType type) {
        if (player.hasPermission("golden.teleports.bypass.cooldown")) {
            return;
        }

        String path = "teleport-settings." + type.getConfigKey() + ".cooldown";
        int cooldownSeconds = plugin.getConfigManager().getMainConfig().getInt(path, 0);

        if (cooldownSeconds <= 0) {
            return;
        }

        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(type, System.currentTimeMillis() + (cooldownSeconds * 1000L));
    }

    public void removeCooldown(Player player, TeleportType type) {
        if (cooldowns.containsKey(player.getUniqueId())) {
            cooldowns.get(player.getUniqueId()).remove(type);
        }
    }

    public void clearAllCooldowns(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    public void safeDisable() {
        cooldowns.clear();
    }
}

package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.SafetyCheckResult;
import org.glstudio.teleports.modules.model.TeleportResult;
import org.glstudio.teleports.modules.model.TeleportType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class TeleportManager {

    private final GTeleports plugin;
    private final Map<UUID, Long> immunityMap;

    public TeleportManager(GTeleports plugin) {
        this.plugin = plugin;
        this.immunityMap = new ConcurrentHashMap<>();
    }

    public void requestTeleport(Player player, Location destination, TeleportType type, Consumer<Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            TeleportResult result = checkRequirements(player, destination, type);

            if (!result.isSuccess()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String message = plugin.getConfigManager().getLanguageConfig().getString("errors." + result.getMessage());
                    if (message != null && !message.isEmpty()) {
                        player.sendMessage(message);
                    }
                    callback.accept(false);
                });
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!applyCost(player, type)) {
                    String message = plugin.getConfigManager().getLanguageConfig().getString("errors.insufficient-funds");
                    if (message != null && !message.isEmpty()) {
                        player.sendMessage(message.replace("{cost}",
                                plugin.getEconomyManager().formatCost(type)));
                    }
                    callback.accept(false);
                    return;
                }

                plugin.getDelayManager().startDelay(player, destination, type, () -> {
                    completeTeleport(player, destination, type, callback);
                });
            });
        });
    }

    private TeleportResult checkRequirements(Player player, Location destination, TeleportType type) {
        if (plugin.getCooldownManager().hasCooldown(player, type)) {
            long remaining = plugin.getCooldownManager().getRemainingCooldown(player, type);
            return TeleportResult.failure("cooldown");
        }

        if (plugin.getBlacklistWorldManager().isBlacklisted(destination.getWorld(), type)) {
            return TeleportResult.failure("world-blacklisted");
        }

        SafetyCheckResult safetyResult = plugin.getTeleportSafetyManager().checkSafety(destination, type);
        if (!safetyResult.isSafe()) {
            return TeleportResult.failure("unsafe-location." + safetyResult.getReason());
        }

        if (plugin.getEconomyManager().isEnabled() &&
                !plugin.getEconomyManager().hasEnoughMoney(player, type)) {
            return TeleportResult.failure("insufficient-funds");
        }

        return TeleportResult.success();
    }

    private boolean applyCost(Player player, TeleportType type) {
        if (!plugin.getEconomyManager().isEnabled()) {
            return true;
        }

        return plugin.getEconomyManager().takeMoney(player, type);
    }

    public void requestRemoteTeleport(Player player, TeleportType type, Runnable onReady) {
        if (plugin.getCooldownManager().hasCooldown(player, type)) {
            sendError(player, "errors.cooldown", plugin.getCooldownManager().getRemainingCooldown(player, type));
            return;
        }

        if (plugin.getEconomyManager().isEnabled()
                && !plugin.getEconomyManager().hasEnoughMoney(player, type)) {
            sendCostError(player, type);
            return;
        }

        if (!applyCost(player, type)) {
            sendCostError(player, type);
            return;
        }

        plugin.getDelayManager().startDelay(player, player.getLocation(), type, () -> {
            plugin.getEffectManager().playTeleportEffects(player, type, EffectManager.EffectStage.TELEPORT);
            plugin.getCooldownManager().setCooldown(player, type);
            onReady.run();
        });
    }

    public void completeArrival(Player player, Location destination, TeleportType type) {
        SafetyCheckResult safetyResult = plugin.getTeleportSafetyManager().checkSafety(destination, type);
        if (!safetyResult.isSafe()) {
            String message = plugin.getConfigManager().getLanguageConfig()
                    .getString("errors.unsafe-location." + safetyResult.getReason());
            if (message != null && !message.isEmpty()) {
                player.sendMessage(message);
            }
            return;
        }

        plugin.getEffectManager().playTeleportEffects(player, type, EffectManager.EffectStage.TELEPORT);

        player.teleport(destination);

        applyImmunity(player, type);

        String message = plugin.getConfigManager().getLanguageConfig().getString("teleport.success");
        if (message != null && !message.isEmpty()) {
            player.sendMessage(message);
        }
    }

    private void sendError(Player player, String path, long time) {
        String message = plugin.getConfigManager().getLanguageConfig().getString(path);
        if (message != null && !message.isEmpty()) {
            player.sendMessage(message.replace("{time}", String.valueOf(time)));
        }
    }

    private void sendCostError(Player player, TeleportType type) {
        String message = plugin.getConfigManager().getLanguageConfig().getString("errors.insufficient-funds");
        if (message != null && !message.isEmpty()) {
            player.sendMessage(message.replace("{cost}", plugin.getEconomyManager().formatCost(type)));
        }
    }

    private void completeTeleport(Player player, Location destination, TeleportType type, Consumer<Boolean> callback) {
        plugin.getEffectManager().playTeleportEffects(player, type, EffectManager.EffectStage.TELEPORT);

        player.teleport(destination);

        plugin.getCooldownManager().setCooldown(player, type);

        applyImmunity(player, type);

        String message = plugin.getConfigManager().getLanguageConfig().getString("teleport.success");
        if (message != null && !message.isEmpty()) {
            player.sendMessage(message);
        }

        callback.accept(true);
    }

    private void applyImmunity(Player player, TeleportType type) {
        String path = "teleport-settings." + type.getConfigKey() + ".immunity";
        int immunityTicks = plugin.getConfigManager().getMainConfig().getInt(path, 0);

        if (immunityTicks > 0) {
            immunityMap.put(player.getUniqueId(), System.currentTimeMillis() + (immunityTicks * 50L));

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                immunityMap.remove(player.getUniqueId());
            }, immunityTicks);
        }
    }

    public boolean hasImmunity(Player player) {
        if (!immunityMap.containsKey(player.getUniqueId())) {
            return false;
        }

        long immunityEnd = immunityMap.get(player.getUniqueId());
        if (System.currentTimeMillis() >= immunityEnd) {
            immunityMap.remove(player.getUniqueId());
            return false;
        }

        return true;
    }

    public void safeDisable() {
        immunityMap.clear();
    }
}

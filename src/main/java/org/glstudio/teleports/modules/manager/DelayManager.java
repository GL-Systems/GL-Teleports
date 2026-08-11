package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.TeleportType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DelayManager {

    private final GTeleports plugin;
    private final Map<UUID, DelayData> delays;

    public DelayManager(GTeleports plugin) {
        this.plugin = plugin;
        this.delays = new ConcurrentHashMap<>();
    }

    public boolean hasDelay(Player player) {
        return delays.containsKey(player.getUniqueId());
    }

    public void startDelay(Player player, Location destination, TeleportType type, Runnable onComplete) {
        if (player.hasPermission("golden.teleports.bypass.delay")) {
            onComplete.run();
            return;
        }

        String path = "teleport-settings." + type.getConfigKey() + ".delay";
        int delaySeconds = plugin.getConfigManager().getMainConfig().getInt(path, 0);

        if (delaySeconds <= 0) {
            onComplete.run();
            return;
        }

        cancelDelay(player);

        Location startLocation = player.getLocation().clone();

        DelayData delayData = new DelayData(
                startLocation,
                destination,
                type,
                delaySeconds,
                onComplete
        );

        delays.put(player.getUniqueId(), delayData);

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            DelayData data = delays.get(player.getUniqueId());
            if (data == null) {
                return;
            }

            plugin.getEffectManager().playDelayEffects(player, type, data.remainingSeconds);

            data.remainingSeconds--;

            if (data.remainingSeconds <= 0) {
                delays.remove(player.getUniqueId());
                data.task.cancel();
                data.onComplete.run();
            }
        }, 0L, 20L);

        delayData.task = task;
    }

    public void cancelDelay(Player player) {
        DelayData data = delays.remove(player.getUniqueId());
        if (data != null && data.task != null) {
            data.task.cancel();

            String message = plugin.getConfigManager().getLanguageConfig().getString("teleport.delay-cancelled");
            if (message != null && !message.isEmpty()) {
                player.sendMessage(message);
            }
        }
    }

    public boolean shouldCancelOnMove(Player player) {
        if (!hasDelay(player)) {
            return false;
        }

        DelayData data = delays.get(player.getUniqueId());
        if (data == null) {
            return false;
        }

        String path = "teleport-settings." + data.type.getConfigKey() + ".cancel-on-move";
        return plugin.getConfigManager().getMainConfig().getBoolean(path, true);
    }

    public boolean hasMoved(Player player, Location from) {
        Location to = player.getLocation();

        double threshold = plugin.getConfigManager().getMainConfig().getDouble("teleport-settings.move-threshold", 0.1);

        return Math.abs(from.getX() - to.getX()) > threshold ||
                Math.abs(from.getZ() - to.getZ()) > threshold;
    }

    public void safeDisable() {
        delays.values().forEach(data -> {
            if (data.task != null) {
                data.task.cancel();
            }
        });
        delays.clear();
    }

    private static class DelayData {
        Location startLocation;
        Location destination;
        TeleportType type;
        int remainingSeconds;
        Runnable onComplete;
        BukkitTask task;

        DelayData(Location startLocation, Location destination, TeleportType type, int seconds, Runnable onComplete) {
            this.startLocation = startLocation;
            this.destination = destination;
            this.type = type;
            this.remainingSeconds = seconds;
            this.onComplete = onComplete;
        }
    }
}

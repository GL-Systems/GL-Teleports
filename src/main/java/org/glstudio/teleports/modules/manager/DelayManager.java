package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.Permissions;
import org.glstudio.teleports.modules.model.TeleportType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DelayManager {
    public static final String BYPASS_PERMISSION = Permissions.BYPASS_DELAY;

    private final GTeleports plugin;
    private final Map<UUID, DelayData> delays = new ConcurrentHashMap<>();

    public DelayManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    public boolean hasDelay(Player player) {
        return delays.containsKey(player.getUniqueId());
    }

    public void startDelay(Player player, Location destination, TeleportType type, Runnable onComplete) {
        startDelay(player, destination, type,
                plugin.getSettings().getInt("teleport-settings." + type.getConfigKey() + ".delay", 0),
                onComplete);
    }

    public void startDelay(Player player, Location destination, TeleportType type, int delaySeconds,
                           Runnable onComplete) {
        if (player.hasPermission(BYPASS_PERMISSION)) {
            onComplete.run();
            return;
        }

        if (delaySeconds <= 0) {
            onComplete.run();
            return;
        }

        cancelDelay(player, false);

        plugin.getEffectManager().playTeleportEffects(player, type, EffectManager.EffectStage.DELAY_START);

        DelayData data = new DelayData(player.getLocation().clone(), destination, type, delaySeconds, onComplete);
        delays.put(player.getUniqueId(), data);

        data.task = new BukkitRunnable() {
            @Override
            public void run() {
                DelayData current = delays.get(player.getUniqueId());
                if (current != data) {
                    cancel();
                    return;
                }

                if (!player.isOnline()) {
                    delays.remove(player.getUniqueId(), data);
                    cancel();
                    return;
                }

                if (current.remainingSeconds <= 0) {
                    delays.remove(player.getUniqueId(), current);
                    cancel();
                    plugin.getEffectManager().clearBossBar(player);
                    current.onComplete.run();
                    return;
                }

                plugin.getEffectManager().playDelayEffects(player, type,
                        current.remainingSeconds, current.totalSeconds);
                current.remainingSeconds--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void cancelDelay(Player player) {
        cancelDelay(player, true);
    }

    public void cancelDelay(Player player, boolean notify) {
        DelayData data = delays.remove(player.getUniqueId());
        if (data == null) {
            return;
        }

        if (data.task != null) {
            data.task.cancel();
        }

        plugin.getEffectManager().clearBossBar(player);

        if (notify) {
            plugin.getEffectManager().playTeleportEffects(player, data.type,
                    EffectManager.EffectStage.CANCELLED);
            plugin.getMessages().send(player, "teleport.delay-cancelled");
        }
    }

    public boolean shouldCancelOnMove(Player player) {
        DelayData data = delays.get(player.getUniqueId());
        if (data == null) {
            return false;
        }

        String path = "teleport-settings." + data.type.getConfigKey() + ".cancel-on-move";
        return plugin.getSettings().getBoolean(path, true);
    }

    public boolean hasMoved(Player player) {
        DelayData data = delays.get(player.getUniqueId());
        if (data == null) {
            return false;
        }

        Location from = data.startLocation;
        Location to = player.getLocation();

        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return true;
        }

        double threshold = plugin.getSettings()
                .getDouble("teleport-settings.move-threshold", 0.5);

        return from.distanceSquared(to) > threshold * threshold;
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
        final Location startLocation;
        final Location destination;
        final TeleportType type;
        final Runnable onComplete;
        final int totalSeconds;

        int remainingSeconds;
        BukkitTask task;

        DelayData(Location startLocation, Location destination, TeleportType type, int seconds, Runnable onComplete) {
            this.startLocation = startLocation;
            this.destination = destination;
            this.type = type;
            this.totalSeconds = seconds;
            this.remainingSeconds = seconds;
            this.onComplete = onComplete;
        }
    }
}

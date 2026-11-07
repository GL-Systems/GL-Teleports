package org.glstudio.teleports.modules.manager;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.model.TeleportType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the waits before a teleport.
 */
public class DelayManager {
    public static final String BYPASS_PERMISSION = Permissions.BYPASS_DELAY;

    private final GTeleports plugin;
    private final Map<UUID, DelayData> delays = new ConcurrentHashMap<>();

    /**
     * Creates the manager.
     */
    public DelayManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Tells whether a teleport has a wait.
     */
    public boolean hasDelay(Player player) {
        return delays.containsKey(player.getUniqueId());
    }

    /**
     * Starts the delay.
     */
    public void startDelay(Player player, Location destination, TeleportType type, Runnable onComplete) {
        startDelay(player, destination, type,
                plugin.getSettings().getInt("teleport-settings." + type.getConfigKey() + ".delay", 0),
                null, onComplete);
    }

    /**
     * Starts the delay.
     */
    public void startDelay(Player player, Location destination, TeleportType type, int delaySeconds,
                           String destinationName, Runnable onComplete) {
        if (player.hasPermission(BYPASS_PERMISSION)) {
            onComplete.run();
            return;
        }

        if (delaySeconds <= 0) {
            onComplete.run();
            return;
        }

        cancelDelay(player, false);

        plugin.getEffectManager().playTeleportEffects(player, type, EffectManager.EffectStage.DELAY_START,
                destinationName);

        DelayData data = new DelayData(player.getLocation().clone(), destination, type, delaySeconds,
                destinationName, onComplete);
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
                        current.remainingSeconds, current.totalSeconds, current.destinationName);
                current.remainingSeconds--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Cancels the delay.
     */
    public void cancelDelay(Player player) {
        cancelDelay(player, true);
    }

    /**
     * Cancels the delay.
     */
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
                    EffectManager.EffectStage.CANCELLED, data.destinationName);
            plugin.getMessages().send(player, "teleport.delay-cancelled");
        }
    }

    /**
     * Tells whether moving cancels this player's teleport.
     */
    public boolean shouldCancelOnMove(Player player) {
        DelayData data = delays.get(player.getUniqueId());
        if (data == null) {
            return false;
        }

        String path = "teleport-settings." + data.type.getConfigKey() + ".cancel-on-move";
        return plugin.getSettings().getBoolean(path, true);
    }

    /**
     * Tells whether a player has moved away.
     */
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

    /**
     * Stops this on shutdown.
     */
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
        final String destinationName;
        final Runnable onComplete;
        final int totalSeconds;

        int remainingSeconds;
        BukkitTask task;

        DelayData(Location startLocation, Location destination, TeleportType type, int seconds,
                  String destinationName, Runnable onComplete) {
            this.startLocation = startLocation;
            this.destination = destination;
            this.type = type;
            this.totalSeconds = seconds;
            this.remainingSeconds = seconds;
            this.destinationName = destinationName;
            this.onComplete = onComplete;
        }
    }
}

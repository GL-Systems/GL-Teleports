package org.glstudio.teleports.modules.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Limits how many teleports run at once.
 */
public class TeleportQueue {
    private final GTeleports plugin;

    private final Deque<Entry> pending = new ArrayDeque<>();

    private final Set<UUID> queued = new HashSet<>();

    private BukkitTask drainTask;

    /**
     * Creates the teleport queue.
     */
    public TeleportQueue(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Tells whether this feature is on.
     */
    public boolean isEnabled() {
        return plugin.getSettings().getBoolean("queue.enabled", true);
    }

    private int perTick() {
        return Math.max(1, plugin.getSettings().getInt("queue.max-per-tick", 4));
    }

    private int noticeThreshold() {
        return Math.max(1, plugin.getSettings().getInt("queue.notice-threshold", 20));
    }

    /**
     * Queues an action.
     */
    public void submit(Player player, Runnable action) {
        if (!isEnabled()) {
            action.run();
            return;
        }

        UUID uuid = player.getUniqueId();

        if (queued.contains(uuid)) {
            pending.removeIf(entry -> entry.uuid.equals(uuid));
        } else {
            queued.add(uuid);
        }

        pending.addLast(new Entry(uuid, action));

        if (pending.size() >= noticeThreshold()) {
            plugin.getMessages().send(player, "teleport.queued",
                    "{position}", String.valueOf(pending.size()));
        }

        start();
    }

    private void start() {
        if (drainTask != null) {
            return;
        }

        drainTask = Bukkit.getScheduler().runTaskTimer(plugin, this::drain, 1L, 1L);
    }

    private void drain() {
        int budget = perTick();

        while (budget > 0 && !pending.isEmpty()) {
            Entry entry = pending.pollFirst();
            queued.remove(entry.uuid);

            Player player = Bukkit.getPlayer(entry.uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }

            budget--;

            try {
                entry.action.run();
            } catch (Exception e) {
                LoggerUtils.logException("Running a queued teleport for " + player.getName(), e);
            }
        }

        if (pending.isEmpty() && drainTask != null) {
            drainTask.cancel();
            drainTask = null;
        }
    }

    /**
     * Forgets what is held for a player.
     */
    public void forget(UUID uuid) {
        if (queued.remove(uuid)) {
            pending.removeIf(entry -> entry.uuid.equals(uuid));
        }
    }

    /**
     * Returns how many entries there are.
     */
    public int size() {
        return pending.size();
    }

    /**
     * Stops this on shutdown.
     */
    public void safeDisable() {
        if (drainTask != null) {
            drainTask.cancel();
            drainTask = null;
        }
        pending.clear();
        queued.clear();
    }

    private record Entry(UUID uuid, Runnable action) {
    }
}

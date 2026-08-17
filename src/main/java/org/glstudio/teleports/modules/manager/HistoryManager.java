package org.glstudio.teleports.modules.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.Failures;
import org.glstudio.teleports.modules.model.TeleportRecord;
import org.glstudio.teleports.modules.model.TeleportType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HistoryManager {
    private static final long PURGE_PERIOD_TICKS = 20L * 60L * 60L;

    private static final long PURGE_DELAY_TICKS = 20L * 60L;

    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    private final GTeleports plugin;

    private BukkitTask purgeTask;

    public HistoryManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getSettings().getBoolean("history.enabled", true);
    }

    private boolean logsFailures() {
        return plugin.getSettings().getBoolean("history.log-failures", false);
    }

    private int retentionDays() {
        return Math.max(1, plugin.getSettings().getInt("history.retention-days", 30));
    }

    public void record(Player player, TeleportType type, String destinationName,
                       Location origin, Location destination, double cost) {
        write(player, type, destinationName, origin, destination, cost, TeleportRecord.SUCCESS);
    }

    public void recordFailure(Player player, TeleportType type, String destinationName,
                              Location origin, String reason) {
        if (!logsFailures()) {
            return;
        }
        write(player, type, destinationName, origin, null, 0.0D, reason);
    }

    private void write(Player player, TeleportType type, String destinationName,
                       Location origin, Location destination, double cost, String result) {
        if (!isEnabled()) {
            return;
        }

        String server = plugin.getCrossServerManager().currentServer();

        TeleportRecord record = TeleportRecord.of(
                player.getUniqueId(), player.getName(), type, destinationName,
                server, worldName(origin), x(origin), y(origin), z(origin),
                server, worldName(destination), x(destination), y(destination), z(destination),
                cost, result);

        Failures.log(plugin.getDatabaseManager().getProvider().logTeleport(record),
                "HistoryManager#record");
    }

    public void recordCrossServer(Player player, TeleportType type, String destinationName,
                                  Location origin, String targetServer, String targetWorld,
                                  double toX, double toY, double toZ, double cost) {
        if (!isEnabled()) {
            return;
        }

        TeleportRecord record = TeleportRecord.of(
                player.getUniqueId(), player.getName(), type, destinationName,
                plugin.getCrossServerManager().currentServer(),
                worldName(origin), x(origin), y(origin), z(origin),
                targetServer, targetWorld, toX, toY, toZ,
                cost, TeleportRecord.SUCCESS);

        Failures.log(plugin.getDatabaseManager().getProvider().logTeleport(record),
                "HistoryManager#recordCrossServer");
    }

    private static String worldName(Location location) {
        return location == null || location.getWorld() == null ? null : location.getWorld().getName();
    }

    private static double x(Location location) {
        return location == null ? 0.0D : location.getX();
    }

    private static double y(Location location) {
        return location == null ? 0.0D : location.getY();
    }

    private static double z(Location location) {
        return location == null ? 0.0D : location.getZ();
    }

    public CompletableFuture<List<TeleportRecord>> history(UUID player, int limit) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return plugin.getDatabaseManager().getProvider().getTeleportHistory(player, limit);
    }

    public CompletableFuture<List<TeleportRecord>> warpHistory(String warp, int limit) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return plugin.getDatabaseManager().getProvider().getWarpHistory(warp, limit);
    }

    public void startPurge() {
        if (purgeTask != null || !isEnabled()) {
            return;
        }

        purgeTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::purgeNow,
                PURGE_DELAY_TICKS, PURGE_PERIOD_TICKS);
    }

    public void purgeNow() {
        if (!isEnabled()) {
            return;
        }

        long cutoff = System.currentTimeMillis() - (retentionDays() * MILLIS_PER_DAY);

        Failures.log(plugin.getDatabaseManager().getProvider().purgeTeleportHistory(cutoff)
                .thenAccept(removed -> {
                    if (removed > 0) {
                        LoggerUtils.logInfo("Teleport history: discarded " + removed
                                + " record(s) older than " + retentionDays() + " days.");
                    }
                }), "HistoryManager#purge");
    }

    public void safeDisable() {
        if (purgeTask != null) {
            purgeTask.cancel();
            purgeTask = null;
        }
    }
}

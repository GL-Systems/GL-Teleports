package org.glstudio.teleports.modules.service;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpPurgeReason;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Automatically removes player warps nobody is looking after. Off by default (purge.enabled),
 * and even enabled, only removes a warp when at least one independently-toggled criterion matches
 * it - see player-warps.yml for the exact conditions. Every deletion is logged.
 */
public class PlayerWarpPurgeService {
    private static final long DELAY_TICKS = 20L * 60L;

    private final GTeleports plugin;
    private org.bukkit.scheduler.BukkitTask task;

    /**
     * Creates the service.
     */
    public PlayerWarpPurgeService(GTeleports plugin) {
        this.plugin = plugin;
    }

    private ConfigFile config() {
        return plugin.getConfigManager().getPlayerWarpsConfig();
    }

    /**
     * Starts the schedule.
     */
    public void schedule() {
        if (task != null || !config().getBoolean("purge.enabled", false)) {
            return;
        }

        long periodTicks = Math.max(1, config().getInt("purge.interval-hours", 24)) * 20L * 60L * 60L;

        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::purgeNow, DELAY_TICKS, periodTicks);
    }

    /**
     * Purges right now.
     */
    public void purgeNow() {
        if (!config().getBoolean("purge.enabled", false)) {
            return;
        }

        Futures.log(plugin.getPlayerWarpManager().getWarps().thenCompose(this::purgeMatching),
                "PlayerWarpPurgeService#purgeNow");
    }

    private CompletableFuture<Void> purgeMatching(List<PlayerWarp> warps) {
        List<PlayerWarp> toRemove = new ArrayList<>();

        for (PlayerWarp warp : warps) {
            PlayerWarpPurgeReason reason = matches(warp);
            if (reason != null) {
                toRemove.add(warp);
                LoggerUtils.logInfo("Player warp purge: removing '" + warp.getName() + "' (owner "
                        + warp.getOwnerName() + ") - " + reason);
            }
        }

        if (toRemove.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        @SuppressWarnings("unchecked")
        CompletableFuture<Boolean>[] deletions = toRemove.stream()
                .map(warp -> plugin.getDatabaseManager().getProvider().deletePlayerWarp(warp.getId()))
                .toArray(CompletableFuture[]::new);

        int count = toRemove.size();
        return CompletableFuture.allOf(deletions).thenRun(() -> {
            plugin.getPlayerWarpManager().invalidateCache();
            LoggerUtils.logSuccess("Player warp purge: removed " + count + " warp(s).");
        });
    }

    private PlayerWarpPurgeReason matches(PlayerWarp warp) {
        if (config().getBoolean("purge.malformed.enabled", true) && isMalformed(warp)) {
            return PlayerWarpPurgeReason.MALFORMED;
        }

        if (config().getBoolean("purge.world-missing.enabled", true)
                && warp.getWorld() != null && Bukkit.getWorld(warp.getWorld()) == null) {
            return PlayerWarpPurgeReason.WORLD_MISSING;
        }

        if (config().getBoolean("purge.inactive.enabled", false)) {
            long days = Math.max(1, config().getInt("purge.inactive.days", 90));
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
            if (warp.getLastUsedAt() < cutoff) {
                return PlayerWarpPurgeReason.INACTIVE;
            }
        }

        if (config().getBoolean("purge.owner-never-seen.enabled", false) && warp.getOwner() != null) {
            long days = Math.max(1, config().getInt("purge.owner-never-seen.days", 180));
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);

            OfflinePlayer owner = Bukkit.getOfflinePlayer(warp.getOwner());
            if (!owner.isOnline() && owner.getLastPlayed() > 0 && owner.getLastPlayed() < cutoff) {
                return PlayerWarpPurgeReason.OWNER_NEVER_SEEN;
            }
        }

        return null;
    }

    private boolean isMalformed(PlayerWarp warp) {
        return warp.getId() == null || warp.getId().isBlank()
                || warp.getName() == null || warp.getName().isBlank()
                || warp.getOwner() == null
                || warp.getWorld() == null || warp.getWorld().isBlank();
    }

    /**
     * Stops this on shutdown.
     */
    public void safeDisable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}

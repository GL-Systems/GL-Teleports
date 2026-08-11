package org.glstudio.teleports.core;

import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;

public class ShutdownService {

    private final GTeleports plugin;

    public ShutdownService(GTeleports plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        try {
            disableManagers();
            closeMultiServer();
            closeDatabase();

            LoggerUtils.logSuccess("Services shut down cleanly.");
        } catch (Exception e) {
            LoggerUtils.logException("ShutdownService#shutdown", e);
        }

        LoggerUtils.sendDisable(plugin);
    }

    private void disableManagers() {
        if (plugin.getHomeManager() != null) {
            plugin.getHomeManager().safeDisable();
        }

        if (plugin.getTpaManager() != null) {
            plugin.getTpaManager().safeDisable();
        }

        if (plugin.getWarpManager() != null) {
            plugin.getWarpManager().safeDisable();
        }

        if (plugin.getSpawnManager() != null) {
            plugin.getSpawnManager().safeDisable();
        }

        if (plugin.getTeleportManager() != null) {
            plugin.getTeleportManager().safeDisable();
        }

        if (plugin.getCooldownManager() != null) {
            plugin.getCooldownManager().safeDisable();
        }

        if (plugin.getDelayManager() != null) {
            plugin.getDelayManager().safeDisable();
        }

        if (plugin.getEffectManager() != null) {
            plugin.getEffectManager().safeDisable();
        }
    }

    private void closeMultiServer() {
        if (plugin.getCrossServerManager() != null) {
            plugin.getCrossServerManager().safeDisable();
        }

        if (plugin.getPlayerRegistry() != null) {
            plugin.getPlayerRegistry().stopHeartbeat();
        }

        if (plugin.getRedisService() != null) {
            plugin.getRedisService().close().join();
        }

        if (plugin.getExecutors() != null) {
            plugin.getExecutors().shutdown();
        }
    }

    private void closeDatabase() {
        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().shutdown();
        }
    }
}

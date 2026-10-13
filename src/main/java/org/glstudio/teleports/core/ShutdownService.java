package org.glstudio.teleports.core;

import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;

import java.util.concurrent.TimeUnit;

public class ShutdownService {
    private final GTeleports plugin;

    public ShutdownService(GTeleports plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        try {
            unregisterCommands();
            disableManagers();
            closeMultiServer();
            drainPendingWork();
            closeDatabase();

            LoggerUtils.logSuccess("Services shut down cleanly.");
        } catch (Exception e) {
            LoggerUtils.logException("ShutdownService#shutdown", e);
        }

        LoggerUtils.sendDisable(plugin);
    }

    private void unregisterCommands() {
        if (plugin.getCommandRegistry() != null) {
            plugin.getCommandRegistry().unregisterAll();
        }
    }

    private void drainPendingWork() {
        if (plugin.getExecutors() != null) {
            plugin.getExecutors().shutdown();
        }
    }

    private void disableManagers() {
        if (plugin.getMenuManager() != null) {
            plugin.getMenuManager().closeAll();
        }

        if (plugin.getTeleportQueue() != null) {
            plugin.getTeleportQueue().safeDisable();
        }

        if (plugin.getHistoryManager() != null) {
            plugin.getHistoryManager().safeDisable();
        }

        if (plugin.getTpaBlockManager() != null) {
            plugin.getTpaBlockManager().safeDisable();
        }

        if (plugin.getLockManager() != null) {
            plugin.getLockManager().safeDisable();
        }

        if (plugin.getHomeManager() != null) {
            plugin.getHomeManager().safeDisable();
        }

        if (plugin.getMigrationManager() != null) {
            plugin.getMigrationManager().safeDisable();
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

        if (plugin.getPlayerWarpManager() != null) {
            plugin.getPlayerWarpManager().safeDisable();
        }

        if (plugin.getPlayerWarpSponsorManager() != null) {
            plugin.getPlayerWarpSponsorManager().safeDisable();
        }

        if (plugin.getPlayerWarpPurgeService() != null) {
            plugin.getPlayerWarpPurgeService().safeDisable();
        }

        if (plugin.getChatInputManager() != null) {
            plugin.getChatInputManager().safeDisable();
        }

        if (plugin.getBackManager() != null) {
            plugin.getBackManager().safeDisable();
        }
    }

    private void closeMultiServer() {

        if (plugin.getCrossServerManager() != null) {
            try {
                plugin.getCrossServerManager().announcePresence(false).get(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LoggerUtils.logDebug("ShutdownService", "Could not announce this server as offline: "
                        + e.getMessage());
            }
        }

        if (plugin.getMultiServerSupervisor() != null) {
            plugin.getMultiServerSupervisor().stop();
        }

        if (plugin.getCrossServerManager() != null) {
            plugin.getCrossServerManager().safeDisable();
        }

        if (plugin.getPlayerRegistry() != null) {
            plugin.getPlayerRegistry().stopHeartbeat();
        }

        if (plugin.getRedisService() != null) {
            try {
                plugin.getRedisService().close().get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LoggerUtils.logWarn("Redis did not close cleanly: " + e.getMessage());
            }
        }
    }

    private void closeDatabase() {
        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().shutdown();
        }
    }
}

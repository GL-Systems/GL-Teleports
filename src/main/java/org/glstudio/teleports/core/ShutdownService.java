package org.glstudio.teleports.core;

import org.glstudio.nexus.hooks.placeholder.PlaceholderHooks;
import org.glstudio.nexus.utils.Diagnostics;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Stops the plugin. Each step is isolated, so a failure cannot keep the next ones from running.
 */
public class ShutdownService {
    private static final Duration EXECUTOR_TIMEOUT = Duration.ofSeconds(10);

    private final GTeleports plugin;

    /**
     * Creates the service.
     */
    public ShutdownService(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Stops this service.
     */
    public void stop() {
        step("commands", this::unregisterCommands);
        step("placeholders", () -> PlaceholderHooks.unregister(plugin.getPlaceholderExpansion()));
        step("managers", this::disableManagers);
        step("multi-server", this::closeMultiServer);
        step("executors", this::drainPendingWork);
        step("database", this::closeDatabase);

        LoggerUtils.sendDisable(plugin);
    }

    private void step(String name, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            Diagnostics.logFailure("ShutdownService#" + name, e);
        }
    }

    private void unregisterCommands() {
        if (plugin.getCommandManager() != null) {
            plugin.getCommandManager().unregisterAll();
        }
    }

    private void drainPendingWork() {
        if (plugin.getExecutors() != null) {
            plugin.getExecutors().shutdown(EXECUTOR_TIMEOUT);
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
            plugin.getChatInputManager().shutdown();
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
            plugin.getRedisService().close();
        }

        if (plugin.getProxyMessenger() != null) {
            plugin.getProxyMessenger().close();
        }
    }

    private void closeDatabase() {
        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().shutdown();
        }
    }
}

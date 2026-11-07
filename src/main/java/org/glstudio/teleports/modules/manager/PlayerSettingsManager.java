package org.glstudio.teleports.modules.manager;

import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Holds each player's preferences.
 */
public class PlayerSettingsManager {
    private final GTeleports plugin;

    /**
     * Creates the manager.
     */
    public PlayerSettingsManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads a player's settings.
     */
    public void load(UUID uuid) {
        Futures.log(plugin.getDatabaseManager().getProvider().isTpaBlocked(uuid)
                .thenAccept(blocked -> plugin.getServer().getScheduler().runTask(plugin,
                        () -> plugin.getTpaManager().applyStoredToggle(uuid, blocked))),
                "PlayerSettingsManager#load");
    }

    /**
     * Sets the tpa blocked.
     */
    public CompletableFuture<Void> setTpaBlocked(UUID uuid, boolean blocked) {
        return Futures.log(plugin.getDatabaseManager().getProvider().setTpaBlocked(uuid, blocked),
                "PlayerSettingsManager#setTpaBlocked")
                .exceptionally(error -> {
                    LoggerUtils.logWarn("Could not save the teleport-request preference for " + uuid
                            + "; it applies for this session only.");
                    return null;
                });
    }
}

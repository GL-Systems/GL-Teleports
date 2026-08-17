package org.glstudio.teleports.modules.manager;

import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.Failures;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerSettingsManager {
    private final GTeleports plugin;

    public PlayerSettingsManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    public void load(UUID uuid) {
        Failures.log(plugin.getDatabaseManager().getProvider().isTpaBlocked(uuid)
                .thenAccept(blocked -> plugin.getServer().getScheduler().runTask(plugin,
                        () -> plugin.getTpaManager().applyStoredToggle(uuid, blocked))),
                "PlayerSettingsManager#load");
    }

    public CompletableFuture<Void> setTpaBlocked(UUID uuid, boolean blocked) {
        return Failures.log(plugin.getDatabaseManager().getProvider().setTpaBlocked(uuid, blocked),
                "PlayerSettingsManager#setTpaBlocked")
                .exceptionally(error -> {
                    LoggerUtils.logWarn("Could not save the teleport-request preference for " + uuid
                            + "; it applies for this session only.");
                    return null;
                });
    }
}

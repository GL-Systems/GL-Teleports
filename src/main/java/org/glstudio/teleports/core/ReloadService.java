package org.glstudio.teleports.core;

import org.bukkit.command.CommandSender;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.nexus.utils.Diagnostics;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.config.MultiServerSettings;

/**
 * Applies {@code /glteleports reload}. A file with broken YAML stops the reload before anything changes.
 */
public final class ReloadService {
    private final GTeleports plugin;

    /**
     * Creates the service.
     *
     * @param plugin the owning plugin
     */
    public ReloadService(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Reloads the files and what is built from them, and tells the sender how it went.
     *
     * @param sender who asked for it
     */
    public void reload(CommandSender sender) {
        for (ConfigFile file : plugin.getConfigManager().all()) {
            String error = file.validate();
            if (error != null) {
                plugin.getMessages().send(sender, "reload.invalid", "{error}", file.getFileName() + " (" + error + ")");
                return;
            }
        }

        MultiServerSettings before = plugin.getMultiServerSettings();
        String databaseBefore = plugin.getConfigManager().getDatabaseConfig().saveToString();

        try {
            plugin.getConfigManager().reload();
        } catch (Exception e) {
            Diagnostics.logFailure("ReloadService#reload", e);
            plugin.getMessages().send(sender, "reload.invalid", "{error}", String.valueOf(e.getMessage()));
            return;
        }
        plugin.getMessages().applyTo(plugin.getCommandManager(), "errors.no-permission", "errors.player-only");

        Futures.log(plugin.getDatabaseManager().getProvider().reload(), "ReloadService#reloadProvider");
        plugin.getWarpManager().invalidateCache();
        plugin.getSpawnManager().invalidateCache();
        plugin.getHomeManager().invalidate(null);
        plugin.getPlayerWarpManager().invalidateCache();
        plugin.getPlayerWarpSponsorManager().invalidateCache();
        plugin.getPlayerWarpCategoryRegistry().load();

        plugin.getMessages().send(sender, "reload.success");
        LoggerUtils.logInfo(sender.getName() + " reloaded the configuration.");

        MultiServerSettings after = new MultiServerSettings(plugin.getConfigManager().getMainConfig());
        if (before != null && before.differsFrom(after)) {
            plugin.getMessages().send(sender, "reload.restart-required", "{section}", "multi-server");
        }

        if (!databaseBefore.equals(plugin.getConfigManager().getDatabaseConfig().saveToString())) {
            plugin.getMessages().send(sender, "reload.restart-required", "{section}", "database.yml");
        }
    }
}

package org.glstudio.teleports.modules.command;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.MultiServerSettings;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class GLTeleportsCommand extends Command {

    private static final List<String> CONFIG_FILES = List.of("config.yml", "language.yml", "database.yml");

    private final GTeleports plugin;

    public GLTeleportsCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public List<String> usage() {
        return List.of("/glteleports reload");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sendUsage(sender);
            return;
        }

        reload(sender);
    }

    private void reload(CommandSender sender) {
        String broken = firstBrokenFile();
        if (broken != null) {
            sendMessage(sender, message("reload.invalid").replace("{error}", broken));
            return;
        }

        MultiServerSettings before = plugin.getMultiServerSettings();
        String databaseBefore = plugin.getConfigManager().getDatabaseConfig().saveToString();

        try {
            plugin.getConfigManager().reload();
        } catch (Exception e) {
            LoggerUtils.logException("GLTeleportsCommand#reload", e);
            sendMessage(sender, message("reload.invalid").replace("{error}", String.valueOf(e.getMessage())));
            return;
        }

        sendMessage(sender, message("reload.success"));
        LoggerUtils.logInfo(sender.getName() + " reloaded the configuration.");

        MultiServerSettings after = new MultiServerSettings(plugin.getConfigManager().getMainConfig());
        if (before != null && before.differsFrom(after)) {
            sendMessage(sender, message("reload.restart-required").replace("{section}", "MULTI_SERVER"));
        }

        if (!databaseBefore.equals(plugin.getConfigManager().getDatabaseConfig().saveToString())) {
            sendMessage(sender, message("reload.restart-required").replace("{section}", "database.yml"));
        }
    }

    private String firstBrokenFile() {
        for (String name : CONFIG_FILES) {
            File file = new File(plugin.getDataFolder(), name);
            if (!file.exists()) {
                continue;
            }

            try {
                new YamlConfiguration().load(file);
            } catch (IOException | InvalidConfigurationException e) {
                return name + " (" + rootCause(e) + ")";
            }
        }
        return null;
    }

    private String rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        String detail = cause.getMessage();
        if (detail == null || detail.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return detail.replace('\n', ' ').replace('\r', ' ');
    }

    private String message(String path) {
        String message = plugin.getConfigManager().getLanguageConfig().getString(path);
        return message == null ? "" : message;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return filterTabCompletion(List.of("reload"), args[0]);
    }
}

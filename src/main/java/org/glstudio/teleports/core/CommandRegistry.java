package org.glstudio.teleports.core;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CommandRegistry {
    private final GTeleports plugin;
    private final List<String> registered = new ArrayList<>();

    public CommandRegistry(GTeleports plugin) {
        this.plugin = plugin;
    }

    public void track(String name, List<String> aliases) {
        registered.add(name);
        registered.addAll(aliases);
    }

    public void unregisterAll() {
        CommandMap commandMap = commandMap();
        if (commandMap == null) {
            return;
        }

        Map<String, Command> known = knownCommands(commandMap);
        String prefix = plugin.getName().toLowerCase() + ":";

        for (String name : registered) {
            Command command = commandMap.getCommand(name);
            if (command == null) {
                continue;
            }

            command.unregister(commandMap);

            if (known != null) {
                known.remove(name);
                known.remove(prefix + name);
            }
        }

        registered.clear();
        refreshClients();
    }

    private void refreshClients() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.updateCommands();
            } catch (Exception ignored) {
            }
        }
    }

    private CommandMap commandMap() {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(Bukkit.getServer());
        } catch (Exception e) {
            LoggerUtils.logDebug("CommandRegistry", "Could not reach the command map: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> knownCommands(CommandMap commandMap) {
        try {
            Field field = commandMap.getClass().getDeclaredField("knownCommands");
            field.setAccessible(true);
            return (Map<String, Command>) field.get(commandMap);
        } catch (Exception e) {
            LoggerUtils.logDebug("CommandRegistry", "Could not reach knownCommands: " + e.getMessage());
            return null;
        }
    }
}

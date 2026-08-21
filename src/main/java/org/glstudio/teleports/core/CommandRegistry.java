package org.glstudio.teleports.core;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.glstudio.teleports.GTeleports;

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
        CommandMap commandMap = Bukkit.getServer().getCommandMap();
        Map<String, Command> known = commandMap.getKnownCommands();
        String prefix = plugin.getName().toLowerCase() + ":";

        for (String name : registered) {
            Command command = commandMap.getCommand(name);
            if (command == null) {
                continue;
            }

            command.unregister(commandMap);

            known.remove(name);
            known.remove(prefix + name);
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

}

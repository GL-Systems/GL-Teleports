package org.glstudio.teleports.modules.command.home;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class HomeCommand extends Command {

    private final GTeleports plugin;

    public HomeCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public List<String> usage() {
        return List.of("/home <nombre>");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, plugin.getConfigManager().getLanguageConfig().getString("errors.player-only"));
            return;
        }

        plugin.getHomeManager().getHomes(player.getUniqueId()).thenAccept(homes -> {
            if (homes.isEmpty()) {
                sendMessage(player, plugin.getConfigManager().getLanguageConfig().getString("errors.no-homes"));
                return;
            }

            String homeName;
            if (args.length == 0) {
                if (homes.size() == 1) {
                    homeName = homes.get(0).getName();
                } else {
                    sendMessage(player, plugin.getConfigManager().getLanguageConfig().getString("errors.specify-home"));
                    return;
                }
            } else {
                homeName = args[0];
            }

            plugin.getHomeManager().teleportToHome(player, homeName);
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (Home home : plugin.getHomeManager().getCachedHomes(player.getUniqueId())) {
            names.add(home.getName());
        }

        return filterTabCompletion(names, args[0]);
    }
}

package org.glstudio.teleports.modules.command.home;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.menu.HomesMenu;

import java.util.List;

public class HomesCommand extends Command {
    private final GTeleports plugin;

    public HomesCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of("homemenu");
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.homes"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        if (!plugin.getSettings().getBoolean("menus.enabled", true)) {
            plugin.getServer().dispatchCommand(player, "listhomes");
            return;
        }

        new HomesMenu(plugin, player).open();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}

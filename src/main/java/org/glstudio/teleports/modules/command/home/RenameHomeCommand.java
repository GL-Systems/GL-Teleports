package org.glstudio.teleports.modules.command.home;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.teleports.GTeleports;

import java.util.List;

/**
 * The {@code /renamehome} command.
 */
public class RenameHomeCommand extends Command {
    private final GTeleports plugin;

    /**
     * Creates the command.
     */
    public RenameHomeCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of("homerename");
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.renamehome"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return;
        }

        plugin.getHomeManager().renameHome(player, args[0], args[1]);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }

        return filterTabCompletion(HomeAdmin.completeHomeArgument(plugin, player, args[0]), args[0]);
    }
}

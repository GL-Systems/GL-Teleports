package org.glstudio.teleports.modules.command.home;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.Home;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code /homeuninvite} command.
 */
public class HomeUninviteCommand extends Command {
    private final GTeleports plugin;

    /**
     * Creates the command.
     */
    public HomeUninviteCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of("unsharehome");
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.homeuninvite"));
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

        plugin.getHomeShareManager().uninvite(player, args[0], args[1]);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            return filterTabCompletion(HomeAdmin.completePlayerArgument(player), args[0]);
        }

        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Home home : plugin.getHomeManager().getCachedHomes(player.getUniqueId())) {
                names.add(home.getName());
            }
            return filterTabCompletion(names, args[1]);
        }

        return List.of();
    }
}

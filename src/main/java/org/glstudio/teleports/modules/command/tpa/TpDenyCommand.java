package org.glstudio.teleports.modules.command.tpa;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.teleports.GTeleports;

import java.util.List;

/**
 * The {@code /tpdeny} command.
 */
public class TpDenyCommand extends Command {
    private final GTeleports plugin;

    /**
     * Creates the command.
     */
    public TpDenyCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of("tpno", "tpadeny");
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.tpdeny"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        String requesterName = args.length > 0 ? args[0] : null;
        plugin.getTpaManager().denyRequest(player, requesterName);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }

        return filterTabCompletion(plugin.getTpaManager().pendingRequesterNames(player), args[0]);
    }
}

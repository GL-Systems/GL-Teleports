package org.glstudio.teleports.modules.command.tpa;

import org.glstudio.teleports.GTeleports;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class TpaCancelCommand extends Command {
    private final GTeleports plugin;

    public TpaCancelCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of("tpcancel");
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.tpacancel"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        String targetName = args.length > 0 ? args[0] : null;
        plugin.getTpaManager().cancelRequest(player, targetName);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }

        return filterTabCompletion(plugin.getTpaManager().outgoingTargetNames(player), args[0]);
    }
}

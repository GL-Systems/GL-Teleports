package org.glstudio.teleports.modules.command.tpa;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.TpaRequest;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class TpaHereCommand extends Command {
    private final GTeleports plugin;

    public TpaHereCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.tpahere"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        if (args.length == 0) {
            plugin.getMessages().send(player, "errors.specify-player");
            return;
        }

        plugin.getTpaManager().sendRequest(player, args[0], TpaRequest.TpaType.TPAHERE);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }

        return filterTabCompletion(TpaTargets.suggest(plugin, player), args[0]);
    }
}

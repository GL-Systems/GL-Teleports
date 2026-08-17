package org.glstudio.teleports.modules.command.warp;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.Warp;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class DelWarpCommand extends Command {
    private final GTeleports plugin;

    public DelWarpCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of("removewarp");
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.delwarp"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        if (args.length == 0) {
            plugin.getMessages().send(player, "errors.specify-warp-name");
            return;
        }

        String warpName = args[0];
        plugin.getWarpManager().deleteWarp(player, warpName);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (Warp warp : plugin.getWarpManager().getCachedWarps()) {
            names.add(warp.getName());
        }

        return filterTabCompletion(names, args[0]);
    }
}

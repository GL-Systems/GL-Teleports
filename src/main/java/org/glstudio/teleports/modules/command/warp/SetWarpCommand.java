package org.glstudio.teleports.modules.command.warp;

import org.glstudio.teleports.GTeleports;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class SetWarpCommand extends Command {

    private final GTeleports plugin;

    public SetWarpCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public List<String> usage() {
        return List.of("/setwarp <nombre> [permiso]");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, plugin.getConfigManager().getLanguageConfig().getString("errors.player-only"));
            return;
        }

        if (args.length == 0) {
            sendMessage(player, plugin.getConfigManager().getLanguageConfig().getString("errors.specify-warp-name"));
            return;
        }

        String warpName = args[0];
        String permission = args.length > 1 ? args[1] : null;

        plugin.getWarpManager().setWarp(player, warpName, permission);
    }
}

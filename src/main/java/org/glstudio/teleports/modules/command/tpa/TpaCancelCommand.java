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
        return List.of("tpacancel");
    }

    @Override
    public List<String> usage() {
        return List.of("/tpacancel [jugador]");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, plugin.getConfigManager().getLanguageConfig().getString("errors.player-only"));
            return;
        }

        String targetName = args.length > 0 ? args[0] : null;
        plugin.getTpaManager().cancelRequest(player, targetName);
    }
}

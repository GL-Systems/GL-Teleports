package org.glstudio.teleports.modules.command.tpa;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.teleports.GTeleports;

import java.util.List;

/**
 * The {@code /tpatoggle} command.
 */
public class TpaToggleCommand extends Command {
    private final GTeleports plugin;

    /**
     * Creates the command.
     */
    public TpaToggleCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of("tptoggle");
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.tpatoggle"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        plugin.getTpaManager().toggleTpa(player);
    }
}

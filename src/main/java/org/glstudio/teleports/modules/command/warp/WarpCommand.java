package org.glstudio.teleports.modules.command.warp;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.command.home.HomeAdmin;
import org.glstudio.teleports.modules.model.Warp;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code /warp} command.
 */
public class WarpCommand extends Command {
    private final GTeleports plugin;

    /**
     * Creates the command.
     */
    public WarpCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.warp"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            sendOther(sender, args[0], args[1]);
            return;
        }

        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        if (args.length == 0) {
            plugin.getMessages().send(player, "errors.specify-warp-name");
            return;
        }

        String warpName = args[0];
        plugin.getWarpManager().teleportToWarp(player, warpName);
    }

    private void sendOther(CommandSender sender, String warpName, String targetName) {
        if (!sender.hasPermission(Permissions.WARP_OTHERS)) {
            plugin.getMessages().send(sender, "errors.no-permission");
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            plugin.getMessages().send(sender, "errors.player-offline");
            return;
        }

        plugin.getWarpManager().teleportToWarp(target, warpName, true);
        if (sender != target) {
            plugin.getMessages().send(sender, "warp.sent-other",
                    "{player}", target.getName(), "{name}", warpName);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2 && sender instanceof Player player
                && sender.hasPermission(Permissions.WARP_OTHERS)) {
            return filterTabCompletion(HomeAdmin.completePlayerArgument(player), args[1]);
        }

        if (args.length != 1) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (Warp warp : plugin.getWarpManager().getCachedWarps()) {
            if (!warp.hasPermission() || sender.hasPermission(warp.getPermission())) {
                names.add(warp.getName());
            }
        }

        return filterTabCompletion(names, args[0]);
    }
}

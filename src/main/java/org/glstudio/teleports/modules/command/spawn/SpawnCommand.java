package org.glstudio.teleports.modules.command.spawn;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.command.home.HomeAdmin;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class SpawnCommand extends Command {
    private final GTeleports plugin;

    public SpawnCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.spawn"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length > 0) {
            sendOther(sender, args[0]);
            return;
        }

        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        plugin.getSpawnManager().teleportToSpawn(player);
    }

    private void sendOther(CommandSender sender, String targetName) {
        if (!sender.hasPermission(Permissions.SPAWN_OTHERS)) {
            plugin.getMessages().send(sender, "errors.no-permission");
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);

        if (target == null) {
            plugin.getMessages().send(sender, "errors.player-offline");
            return;
        }

        plugin.getSpawnManager().teleportToSpawn(target);

        if (sender != target) {
            plugin.getMessages().send(sender, "spawn.sent-other", "{player}", target.getName());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player) || !sender.hasPermission(Permissions.SPAWN_OTHERS)) {
            return List.of();
        }

        return filterTabCompletion(HomeAdmin.completePlayerArgument(player), args[0]);
    }
}

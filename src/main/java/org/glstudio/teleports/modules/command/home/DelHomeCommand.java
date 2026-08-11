package org.glstudio.teleports.modules.command.home;

import org.glstudio.teleports.GTeleports;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class DelHomeCommand extends Command {

    private final GTeleports plugin;

    public DelHomeCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of("removehome");
    }

    @Override
    public List<String> usage() {
        return List.of("/delhome <nombre>", "/delhome <usuario>:<home> (admin)");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendMessage(sender, plugin.getConfigManager().getLanguageConfig().getString("errors.specify-home"));
            return;
        }

        HomeAdmin.Target target = HomeAdmin.parse(args[0]);
        if (target != null) {
            deleteHomeOfOther(sender, target);
            return;
        }

        if (!(sender instanceof Player player)) {
            sendMessage(sender, plugin.getConfigManager().getLanguageConfig().getString("errors.player-only"));
            return;
        }

        plugin.getHomeManager().deleteHome(player, args[0]);
    }

    private void deleteHomeOfOther(CommandSender staff, HomeAdmin.Target target) {
        if (!staff.hasPermission(HomeAdmin.PERMISSION)) {
            sendMessage(staff, plugin.getConfigManager().getLanguageConfig().getString("errors.no-permission"));
            return;
        }

        if (target.homeName().isEmpty()) {
            sendUsage(staff);
            return;
        }

        OfflinePlayer owner = HomeAdmin.resolve(target.playerName());
        if (owner == null) {
            sendMessage(staff, plugin.getConfigManager().getLanguageConfig().getString("errors.player-never-played")
                    .replace("{player}", target.playerName()));
            return;
        }

        String ownerName = owner.getName() != null ? owner.getName() : target.playerName();
        plugin.getHomeManager().deleteHomeFor(staff, owner.getUniqueId(), ownerName, target.homeName());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }

        return filterTabCompletion(HomeAdmin.completeHomeArgument(plugin, player, args[0]), args[0]);
    }
}

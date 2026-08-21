package org.glstudio.teleports.modules.command.home;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
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
        return List.of(plugin.getMessages().raw("usage.delhome"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            plugin.getMessages().send(sender, "errors.specify-home");
            return;
        }

        HomeAdmin.Target target = HomeAdmin.parse(args[0]);
        if (target != null) {
            deleteHomeOfOther(sender, target);
            return;
        }

        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        plugin.getHomeManager().deleteHome(player, args[0]);
    }

    private void deleteHomeOfOther(CommandSender staff, HomeAdmin.Target target) {
        if (!staff.hasPermission(HomeAdmin.PERMISSION)) {
            plugin.getMessages().send(staff, "errors.no-permission");
            return;
        }

        if (target.homeName().isEmpty()) {
            sendUsage(staff);
            return;
        }

        Failures.guard(plugin, HomeAdmin.resolve(plugin, target.playerName()).thenAccept(owner ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (owner == null) {
                        plugin.getMessages().send(staff, "errors.player-never-played",
                                "{player}", target.playerName());
                        return;
                    }

                    plugin.getHomeManager().deleteHomeFor(staff, owner.uuid(), owner.name(),
                            target.homeName());
                })), "DelHomeCommand#resolve", staff);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }

        return filterTabCompletion(HomeAdmin.completeHomeArgument(plugin, player, args[0]), args[0]);
    }
}

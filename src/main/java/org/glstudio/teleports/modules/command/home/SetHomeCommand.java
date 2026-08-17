package org.glstudio.teleports.modules.command.home;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.Failures;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class SetHomeCommand extends Command {
    private final GTeleports plugin;

    public SetHomeCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.sethome"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        if (args.length == 0) {
            Failures.guard(plugin, plugin.getHomeManager().nextAutomaticName(player.getUniqueId())
                    .thenAccept(autoName -> plugin.getServer().getScheduler().runTask(plugin,
                            () -> plugin.getHomeManager().setHome(player, autoName))),
                    "SetHomeCommand#autoName", player);
            return;
        }

        HomeAdmin.Target target = HomeAdmin.parse(args[0]);
        if (target != null) {
            setHomeForOther(player, target);
            return;
        }

        plugin.getHomeManager().setHome(player, args[0]);
    }

    private void setHomeForOther(Player staff, HomeAdmin.Target target) {
        if (!staff.hasPermission(HomeAdmin.PERMISSION)) {
            plugin.getMessages().send(staff, "errors.no-permission");
            return;
        }

        if (target.homeName().isEmpty()) {
            sendUsage(staff);
            return;
        }

        Location location = staff.getLocation();

        Failures.guard(plugin, HomeAdmin.resolve(plugin, target.playerName()).thenAccept(owner ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (owner == null) {
                        plugin.getMessages().send(staff, "errors.player-never-played",
                                "{player}", target.playerName());
                        return;
                    }

                    plugin.getHomeManager().setHomeFor(staff, owner.uuid(), owner.name(),
                            target.homeName(), location);
                })), "SetHomeCommand#resolve", staff);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }

        return filterTabCompletion(HomeAdmin.completeHomeArgument(plugin, player, args[0]), args[0]);
    }
}

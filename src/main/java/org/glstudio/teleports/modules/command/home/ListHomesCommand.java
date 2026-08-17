package org.glstudio.teleports.modules.command.home;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.Failures;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class ListHomesCommand extends Command {
    private final GTeleports plugin;

    public ListHomesCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.listhomes"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sendUsage(sender);
                return;
            }
            list(sender, player.getUniqueId(), player.getName(), true);
            return;
        }

        if (!sender.hasPermission(HomeAdmin.PERMISSION)) {
            plugin.getMessages().send(sender, "errors.no-permission");
            return;
        }

        Failures.guard(plugin, HomeAdmin.resolve(plugin, args[0]).thenAccept(owner ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (owner == null) {
                        plugin.getMessages().send(sender, "errors.player-never-played", "{player}", args[0]);
                        return;
                    }

                    list(sender, owner.uuid(), owner.name(), false);
                })), "ListHomesCommand#resolve", sender);
    }

    private void list(CommandSender sender, UUID owner, String ownerName, boolean self) {
        Failures.guard(plugin, plugin.getHomeManager().getHomes(owner).thenAccept(homes -> {
            if (homes.isEmpty()) {
                plugin.getMessages().send(sender, self ? "errors.no-homes" : "errors.no-homes-other",
                        "{player}", ownerName);
                return;
            }

            plugin.getMessages().send(sender, "home.list-header",
                    "{player}", ownerName, "{count}", String.valueOf(homes.size()));

            for (Home home : homes) {
                plugin.getMessages().send(sender, "home.list-entry",
                        "{name}", home.getName(),
                        "{world}", home.getWorld() != null ? home.getWorld() : "?",
                        "{x}", String.valueOf(Math.round(home.getX())),
                        "{y}", String.valueOf(Math.round(home.getY())),
                        "{z}", String.valueOf(Math.round(home.getZ())),
                        "{server}", home.getServer() != null ? home.getServer() : "?");
            }
        }), "ListHomesCommand#list", sender);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)
                || !sender.hasPermission(HomeAdmin.PERMISSION)) {
            return List.of();
        }

        return filterTabCompletion(HomeAdmin.completePlayerArgument(player), args[0]);
    }
}

package org.glstudio.teleports.modules.command.home;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.Home;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The {@code /home} command.
 */
public class HomeCommand extends Command {
    private final GTeleports plugin;

    /**
     * Creates the command.
     */
    public HomeCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.home"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        if (args.length > 0) {
            HomeAdmin.Target target = HomeAdmin.parse(args[0]);

            if (target != null) {
                openOthersHome(player, target);
                return;
            }
        }

        plugin.getHomeManager().getHomes(player.getUniqueId()).thenAccept(homes -> {
            if (homes.isEmpty()) {
                plugin.getMessages().send(player, "errors.no-homes");
                return;
            }

            String homeName;
            if (args.length == 0) {
                if (homes.size() != 1) {
                    plugin.getMessages().send(player, "errors.multiple-homes",
                            "{homes}", homes.stream().map(Home::getName).collect(Collectors.joining(", ")));
                    return;
                }
                homeName = homes.get(0).getName();
            } else {
                homeName = args[0];
            }

            plugin.getHomeManager().teleportToHome(player, homeName);
        });
    }

    private void openOthersHome(Player player, HomeAdmin.Target target) {
        Futures.guard(HomeAdmin.resolve(plugin, target.playerName()).thenAccept(owner ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }

                    if (owner == null) {
                        plugin.getMessages().send(player, "errors.player-never-played",
                                "{player}", target.playerName());
                        return;
                    }

                    if (player.hasPermission(HomeAdmin.PERMISSION)) {
                        plugin.getHomeManager().teleportToHomeOf(player, owner.uuid(), owner.name(),
                                target.homeName());
                        return;
                    }

                    plugin.getHomeManager().teleportToSharedHome(player, owner.uuid(), owner.name(),
                            target.homeName());
                })), "HomeCommand#openOthersHome", player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (Home home : plugin.getHomeManager().getCachedHomes(player.getUniqueId())) {
            names.add(home.getName());
        }

        names.addAll(plugin.getHomeManager().getCachedShareLabels(player.getUniqueId()));

        if (sender.hasPermission(HomeAdmin.PERMISSION)) {
            return filterTabCompletion(HomeAdmin.completeHomeArgument(plugin, player, args[0]), args[0]);
        }

        return filterTabCompletion(names, args[0]);
    }
}

package org.glstudio.teleports.modules.command.tpoffline;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.teleports.modules.command.home.HomeAdmin;
import org.glstudio.teleports.modules.model.PlayerRef;
import org.glstudio.teleports.modules.model.StoredLocation;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Staff command: teleports to where a player was when they last left this server. The location
 * comes from {@link org.glstudio.teleports.modules.manager.LastLocationManager}, which is stored
 * per server, so a player who last logged out elsewhere on the network has nothing here.
 *
 * <p>Instant and free on purpose - it is an inspection tool, not a travel route, so cooldowns,
 * delays, costs and the safety check would only get in the way of seeing the exact spot.
 */
public class TpOfflineCommand extends Command {
    private final GTeleports plugin;

    public TpOfflineCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.tpoffline"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return;
        }

        String name = args[0];

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            if (online.equals(player)) {
                plugin.getMessages().send(player, "errors.cannot-teleport-self");
                return;
            }

            teleport(player, online.getLocation(), online.getName(), "teleport.tpoffline-online");
            return;
        }

        if (!plugin.getLastLocationManager().isEnabled()) {
            plugin.getMessages().send(player, "errors.last-location-disabled");
            return;
        }

        Failures.guard(plugin, HomeAdmin.resolve(plugin, name).thenCompose(target -> {
            if (target == null) {
                plugin.getMessages().send(player, "errors.player-never-played", "{player}", name);
                return CompletableFuture.completedFuture(null);
            }

            return plugin.getLastLocationManager().find(target.uuid()).thenAccept(stored ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> arrive(player, target, stored)));
        }), "TpOfflineCommand#execute", player);
    }

    private void arrive(Player player, PlayerRef target, StoredLocation stored) {
        if (!player.isOnline()) {
            return;
        }

        if (stored == null) {
            plugin.getMessages().send(player, "errors.no-last-location", "{player}", target.name());
            return;
        }

        Location destination = stored.toLocation();
        if (destination == null) {
            plugin.getMessages().send(player, "errors.world-not-found");
            return;
        }

        teleport(player, destination, target.name(), "teleport.tpoffline-arrived");
    }

    private void teleport(Player player, Location destination, String targetName, String messageKey) {
        Location origin = player.getLocation().clone();

        player.teleport(destination);

        plugin.getBackManager().recordBackPoint(player, origin, plugin.getCrossServerManager().currentServer());
        plugin.getMessages().send(player, messageKey, "{player}", targetName);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }

        return filterTabCompletion(HomeAdmin.completePlayerArgument(player), args[0]);
    }
}

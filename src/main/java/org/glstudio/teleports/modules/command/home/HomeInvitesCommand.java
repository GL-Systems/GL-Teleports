package org.glstudio.teleports.modules.command.home;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.Failures;
import org.glstudio.teleports.modules.model.HomeShare;

import java.util.List;

public class HomeInvitesCommand extends Command {
    private final GTeleports plugin;

    public HomeInvitesCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of("sharedhomes");
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.homeinvites"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "errors.player-only");
            return;
        }

        boolean given = args.length > 0 && args[0].equalsIgnoreCase("given");

        var lookup = given
                ? plugin.getHomeShareManager().outgoing(player.getUniqueId())
                : plugin.getHomeShareManager().incoming(player.getUniqueId());

        Failures.guard(plugin, lookup.thenAccept(shares -> {
            if (shares.isEmpty()) {
                plugin.getMessages().send(player, given ? "share.none-given" : "share.none-received");
                return;
            }

            plugin.getMessages().send(player, given ? "share.header-given" : "share.header-received",
                    "{count}", String.valueOf(shares.size()));

            for (HomeShare share : shares) {
                plugin.getMessages().send(player, given ? "share.entry-given" : "share.entry-received",
                        "{player}", given ? name(share.inviteeName(), share) : name(share.ownerName(), share),
                        "{home}", share.home(),
                        "{expires}", expiry(share));
            }
        }), "HomeInvitesCommand#list", player);
    }

    private static String name(String recorded, HomeShare share) {
        if (recorded != null && !recorded.isBlank()) {
            return recorded;
        }
        return share.owner().toString().substring(0, 8);
    }

    private String expiry(HomeShare share) {
        if (share.isPermanent()) {
            return plugin.getMessages().raw("share.permanent");
        }

        long seconds = share.remainingSeconds();

        if (seconds >= 86400) {
            return (seconds / 86400) + "d";
        }
        if (seconds >= 3600) {
            return (seconds / 3600) + "h";
        }
        if (seconds >= 60) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return filterTabCompletion(List.of("received", "given"), args[0]);
    }
}

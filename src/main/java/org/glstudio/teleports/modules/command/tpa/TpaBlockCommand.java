package org.glstudio.teleports.modules.command.tpa;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.modules.command.Command;
import org.glstudio.nexus.modules.command.CommandManager;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Failures;

import java.util.List;
import java.util.UUID;

public class TpaBlockCommand extends Command {
    private final GTeleports plugin;

    public TpaBlockCommand(CommandManager manager, String name, String permission, GTeleports plugin) {
        super(manager, name, permission);
        this.plugin = plugin;
    }

    @Override
    public List<String> aliases() {
        return List.of("tpablocklist");
    }

    @Override
    public List<String> usage() {
        return List.of(plugin.getMessages().raw("usage.tpablock"));
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

        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "list" -> list(player);
            case "add" -> {
                if (args.length < 2) {
                    sendUsage(sender);
                    return;
                }
                plugin.getTpaBlockManager().block(player, args[1]);
            }
            case "remove" -> {
                if (args.length < 2) {
                    sendUsage(sender);
                    return;
                }
                plugin.getTpaBlockManager().unblock(player, args[1]);
            }
            default -> sendUsage(sender);
        }
    }

    private void list(Player player) {
        Failures.guard(plugin, plugin.getTpaBlockManager().list(player.getUniqueId()).thenAccept(blocked -> {
            if (blocked.isEmpty()) {
                plugin.getMessages().send(player, "tpa.block-list-empty");
                return;
            }

            plugin.getMessages().send(player, "tpa.block-list-header",
                    "{count}", String.valueOf(blocked.size()));

            for (UUID uuid : blocked) {
                plugin.getMessages().send(player, "tpa.block-list-entry", "{player}", nameOf(uuid));
            }
        }), "TpaBlockCommand#list", player);
    }

    private String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }

        OfflinePlayer cached = Bukkit.getOfflinePlayer(uuid);
        String name = cached.getName();

        return name != null ? name : uuid.toString().substring(0, 8);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            return filterTabCompletion(List.of("add", "remove", "list"), args[0]);
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
            return filterTabCompletion(TpaTargets.suggest(plugin, player), args[1]);
        }

        return List.of();
    }
}

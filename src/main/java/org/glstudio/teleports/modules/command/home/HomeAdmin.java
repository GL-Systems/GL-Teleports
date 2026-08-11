package org.glstudio.teleports.modules.command.home;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.Home;

import java.util.ArrayList;
import java.util.List;

public final class HomeAdmin {

    public static final String PERMISSION = "golden.teleports.admin.homes";

    private HomeAdmin() {
    }

    public record Target(String playerName, String homeName) {
    }

    public static Target parse(String argument) {
        int separator = argument.indexOf(':');
        if (separator <= 0) {
            return null;
        }
        return new Target(argument.substring(0, separator), argument.substring(separator + 1));
    }

    public static OfflinePlayer resolve(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(playerName);
    }

    public static List<String> completeHomeArgument(GTeleports plugin, Player sender, String argument) {
        boolean staff = sender.hasPermission(PERMISSION);
        Target target = parse(argument);

        if (target != null) {
            if (!staff) {
                return List.of();
            }

            OfflinePlayer owner = resolve(target.playerName());
            if (owner == null) {
                return List.of();
            }

            String ownerName = owner.getName() != null ? owner.getName() : target.playerName();
            return homeNames(plugin, owner.getUniqueId(), ownerName + ":");
        }

        List<String> completions = homeNames(plugin, sender.getUniqueId(), "");
        if (staff) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (sender.canSee(online)) {
                    completions.add(online.getName() + ":");
                }
            }
        }
        return completions;
    }

    public static List<String> completePlayerArgument(Player sender) {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (sender.canSee(online)) {
                names.add(online.getName());
            }
        }
        return names;
    }

    private static List<String> homeNames(GTeleports plugin, java.util.UUID owner, String prefix) {
        List<String> names = new ArrayList<>();
        for (Home home : plugin.getHomeManager().getCachedHomes(owner)) {
            names.add(prefix + home.getName());
        }
        return names;
    }
}

package org.glstudio.teleports.modules.command.home;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.PlayerRef;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class HomeAdmin {
    public static final String PERMISSION = Permissions.HOME_ADMIN;

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

    public static CompletableFuture<PlayerRef> resolve(GTeleports plugin, String playerName) {
        PlayerRef local = resolveCached(playerName);

        if (local != null) {
            return CompletableFuture.completedFuture(local);
        }

        return plugin.getDatabaseManager().getProvider().findPlayer(playerName);
    }

    public static PlayerRef resolveCached(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return new PlayerRef(online.getUniqueId(), online.getName());
        }

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(playerName);
        if (cached == null) {
            return null;
        }

        return new PlayerRef(cached.getUniqueId(), cached.getName() != null ? cached.getName() : playerName);
    }

    public static List<String> completeHomeArgument(GTeleports plugin, Player sender, String argument) {
        boolean staff = sender.hasPermission(PERMISSION);
        Target target = parse(argument);

        if (target != null) {
            if (!staff) {
                return List.of();
            }

            PlayerRef owner = resolveCached(target.playerName());
            if (owner == null) {
                return List.of();
            }

            return homeNames(plugin, owner.uuid(), owner.name() + ":");
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

    private static List<String> homeNames(GTeleports plugin, UUID owner, String prefix) {
        List<String> names = new ArrayList<>();
        for (Home home : plugin.getHomeManager().getCachedHomes(owner)) {
            names.add(prefix + home.getName());
        }
        return names;
    }
}

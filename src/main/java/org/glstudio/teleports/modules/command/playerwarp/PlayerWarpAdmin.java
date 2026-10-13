package org.glstudio.teleports.modules.command.playerwarp;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.PlayerRef;
import org.glstudio.teleports.modules.model.PlayerWarp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class PlayerWarpAdmin {
    private PlayerWarpAdmin() {
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

    public static List<String> completePlayerArgument(Player sender) {
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (sender.canSee(online)) {
                names.add(online.getName());
            }
        }
        return names;
    }

    public static List<String> ownWarpNames(GTeleports plugin, Player player) {
        List<String> names = new ArrayList<>();
        for (PlayerWarp warp : plugin.getPlayerWarpManager().getCachedWarpsByOwner(player.getUniqueId())) {
            names.add(warp.getName());
        }
        return names;
    }

    public static List<String> allWarpNames(GTeleports plugin) {
        List<String> names = new ArrayList<>();
        for (PlayerWarp warp : plugin.getPlayerWarpManager().getCachedWarps()) {
            names.add(warp.getName());
        }
        return names;
    }
}

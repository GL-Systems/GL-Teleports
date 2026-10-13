package org.glstudio.teleports.modules.manager;

import org.bukkit.entity.Player;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpFavourite;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerWarpFavouriteManager {
    private final GTeleports plugin;

    public PlayerWarpFavouriteManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<List<PlayerWarpFavourite>> list(UUID player) {
        return plugin.getDatabaseManager().getProvider().getPlayerWarpFavourites(player);
    }

    public CompletableFuture<Boolean> isFavourite(UUID player, String warpId) {
        return list(player).thenApply(favs -> favs.stream().anyMatch(fav -> fav.warpId().equals(warpId)));
    }

    public CompletableFuture<Boolean> toggle(Player player, PlayerWarp warp) {
        if (!player.hasPermission(Permissions.PLAYERWARP_FAVOURITE)) {
            plugin.getMessages().send(player, "errors.no-permission");
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> result = isFavourite(player.getUniqueId(), warp.getId()).thenCompose(already -> {
            if (already) {
                return plugin.getDatabaseManager().getProvider()
                        .removePlayerWarpFavourite(player.getUniqueId(), warp.getId())
                        .thenCompose(removed -> adjustCount(warp, -1).thenApply(ignored -> {
                            plugin.getMessages().send(player, "player-warp.favourite.removed",
                                    "{warp}", warp.getName());
                            return false;
                        }));
            }

            return plugin.getDatabaseManager().getProvider()
                    .addPlayerWarpFavourite(PlayerWarpFavourite.of(player.getUniqueId(), warp.getId()))
                    .thenCompose(ignored -> adjustCount(warp, 1).thenApply(ignored2 -> {
                        plugin.getMessages().send(player, "player-warp.favourite.added", "{warp}", warp.getName());
                        return true;
                    }));
        });

        return Failures.guard(plugin, result, "PlayerWarpFavouriteManager#toggle", player);
    }

    private CompletableFuture<Void> adjustCount(PlayerWarp warp, int delta) {
        warp.setFavouriteCount(Math.max(0, warp.getFavouriteCount() + delta));
        warp.touch();
        return plugin.getDatabaseManager().getProvider().savePlayerWarp(warp)
                .thenRun(() -> plugin.getPlayerWarpManager().invalidateCache());
    }
}

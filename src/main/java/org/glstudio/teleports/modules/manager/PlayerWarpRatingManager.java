package org.glstudio.teleports.modules.manager;

import org.bukkit.entity.Player;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpRating;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Holds the ratings of player warps.
 */
public class PlayerWarpRatingManager {
    private final GTeleports plugin;

    /**
     * Creates the manager.
     */
    public PlayerWarpRatingManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Lists the ratings of a warp.
     */
    public CompletableFuture<List<PlayerWarpRating>> list(String warpId) {
        return plugin.getDatabaseManager().getProvider().getPlayerWarpRatings(warpId);
    }

    private long cooldownMillis() {
        int hours = plugin.getConfigManager().getPlayerWarpsConfig().getInt("rating.cooldown-hours", 24);
        return TimeUnit.HOURS.toMillis(Math.max(0, hours));
    }

    /**
     * Stores a player's rating.
     */
    public CompletableFuture<Void> rate(Player player, PlayerWarp warp, int stars, String comment) {
        if (!player.hasPermission(Permissions.PLAYERWARP_RATE)) {
            plugin.getMessages().send(player, "errors.no-permission");
            return CompletableFuture.completedFuture(null);
        }

        if (warp.isOwner(player.getUniqueId())) {
            plugin.getMessages().send(player, "player-warp.rate.own-warp");
            return CompletableFuture.completedFuture(null);
        }

        int maxLength = plugin.getConfigManager().getPlayerWarpsConfig().getInt("rating.max-comment-length", 96);
        String trimmedComment = comment == null ? "" : comment.trim();
        if (trimmedComment.length() > maxLength) {
            trimmedComment = trimmedComment.substring(0, maxLength);
        }
        String finalComment = trimmedComment;

        CompletableFuture<Void> result = list(warp.getId()).thenCompose(ratings -> {
            PlayerWarpRating existing = ratings.stream()
                    .filter(rating -> rating.player().equals(player.getUniqueId()))
                    .findFirst()
                    .orElse(null);

            if (existing != null
                    && System.currentTimeMillis() - existing.updatedAt() < cooldownMillis()) {
                long remainingHours = Math.max(1, (cooldownMillis()
                        - (System.currentTimeMillis() - existing.updatedAt())) / TimeUnit.HOURS.toMillis(1));
                plugin.getMessages().send(player, "player-warp.rate.cooldown",
                        "{time}", String.valueOf(remainingHours));
                return CompletableFuture.completedFuture(null);
            }

            PlayerWarpRating rating = existing == null
                    ? PlayerWarpRating.create(warp.getId(), player.getUniqueId(), player.getName(), stars,
                    finalComment)
                    : existing.updated(stars, finalComment);

            List<PlayerWarpRating> updated = new java.util.ArrayList<>(ratings.stream()
                    .filter(r -> !r.player().equals(player.getUniqueId()))
                    .toList());
            updated.add(rating);

            double average = updated.stream().mapToInt(PlayerWarpRating::stars).average().orElse(0.0D);

            warp.setRatingAverage(average);
            warp.setRatingCount(updated.size());
            warp.touch();

            return plugin.getDatabaseManager().getProvider().savePlayerWarpRating(rating)
                    .thenCompose(ignored -> plugin.getDatabaseManager().getProvider().savePlayerWarp(warp))
                    .thenRun(() -> {
                        plugin.getPlayerWarpManager().invalidateCache();
                        plugin.getMessages().send(player, "player-warp.rate.success",
                                "{warp}", warp.getName(), "{stars}", String.valueOf(rating.stars()));
                    });
        });

        return Futures.guard(result, "PlayerWarpRatingManager#rate", player);
    }
}

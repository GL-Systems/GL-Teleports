package org.glstudio.teleports.modules.manager;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.PlayerRef;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpWhitelistEntry;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manages who may enter a closed player warp.
 */
public class PlayerWarpWhitelistManager {
    private final GTeleports plugin;

    /**
     * Creates the manager.
     */
    public PlayerWarpWhitelistManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    private int maxEntries() {
        return Math.max(1, plugin.getConfigManager().getPlayerWarpsConfig().getInt("whitelist.max-entries", 50));
    }

    /**
     * Lists the whitelist of a warp.
     */
    public CompletableFuture<List<PlayerWarpWhitelistEntry>> list(String warpId) {
        return plugin.getDatabaseManager().getProvider().getPlayerWarpWhitelist(warpId);
    }

    /**
     * Tells whether a player may enter.
     */
    public CompletableFuture<Boolean> isWhitelisted(String warpId, UUID player) {
        return list(warpId).thenApply(entries -> entries.stream().anyMatch(entry -> entry.player().equals(player)));
    }

    /**
     * Adds a player to a whitelist.
     */
    public CompletableFuture<Void> add(CommandSender sender, PlayerWarp warp, String targetName) {
        if (!sender.hasPermission(org.glstudio.teleports.core.util.Permissions.PLAYERWARP_WHITELIST)) {
            plugin.getMessages().send(sender, "errors.no-permission");
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> result = resolve(targetName).thenCompose(target -> {
            if (target == null) {
                plugin.getMessages().send(sender, "errors.player-not-found", "{player}", targetName);
                return CompletableFuture.completedFuture(null);
            }

            return list(warp.getId()).thenCompose(entries -> {
                if (entries.stream().anyMatch(entry -> entry.player().equals(target.uuid()))) {
                    plugin.getMessages().send(sender, "player-warp.whitelist.already-added",
                            "{player}", target.name(), "{warp}", warp.getName());
                    return CompletableFuture.completedFuture(null);
                }

                if (entries.size() >= maxEntries()) {
                    plugin.getMessages().send(sender, "player-warp.whitelist.limit-reached",
                            "{limit}", String.valueOf(maxEntries()));
                    return CompletableFuture.completedFuture(null);
                }

                return plugin.getDatabaseManager().getProvider()
                        .savePlayerWarpWhitelistEntry(PlayerWarpWhitelistEntry.of(
                                warp.getId(), target.uuid(), target.name()))
                        .thenRun(() -> plugin.getMessages().send(sender, "player-warp.whitelist.added",
                                "{player}", target.name(), "{warp}", warp.getName()));
            });
        });

        return Futures.guard(result, "PlayerWarpWhitelistManager#add", sender);
    }

    /**
     * Removes a player from a whitelist.
     */
    public CompletableFuture<Void> remove(CommandSender sender, PlayerWarp warp, String targetName) {
        if (!sender.hasPermission(org.glstudio.teleports.core.util.Permissions.PLAYERWARP_WHITELIST)) {
            plugin.getMessages().send(sender, "errors.no-permission");
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> result = resolve(targetName).thenCompose(target -> {
            if (target == null) {
                plugin.getMessages().send(sender, "errors.player-not-found", "{player}", targetName);
                return CompletableFuture.completedFuture(null);
            }

            return plugin.getDatabaseManager().getProvider()
                    .removePlayerWarpWhitelistEntry(warp.getId(), target.uuid())
                    .thenAccept(removed -> plugin.getMessages().send(sender,
                            removed ? "player-warp.whitelist.removed" : "player-warp.whitelist.not-found",
                            "{player}", target.name(), "{warp}", warp.getName()));
        });

        return Futures.guard(result, "PlayerWarpWhitelistManager#remove", sender);
    }

    private CompletableFuture<PlayerRef> resolve(String name) {
        Player online = plugin.getServer().getPlayerExact(name);

        if (online != null) {
            return CompletableFuture.completedFuture(new PlayerRef(online.getUniqueId(), online.getName()));
        }

        return plugin.getDatabaseManager().getProvider().findPlayer(name);
    }
}

package org.glstudio.teleports.modules.manager;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.PlayerRef;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpManagerEntry;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The "managers" feature of a player warp (delegated administration rights) - named apart from
 * {@link PlayerWarpManager}, the orchestrator for the warps themselves, to avoid the two colliding.
 */
public class PlayerWarpManagerRoleManager {
    private final GTeleports plugin;

    /**
     * Creates the manager.
     */
    public PlayerWarpManagerRoleManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Lists the managers of a warp.
     */
    public CompletableFuture<List<PlayerWarpManagerEntry>> list(String warpId) {
        return plugin.getDatabaseManager().getProvider().getPlayerWarpManagers(warpId);
    }

    /**
     * Tells whether a player manages a warp.
     */
    public CompletableFuture<Boolean> isManager(String warpId, UUID player) {
        return list(warpId).thenApply(entries -> entries.stream().anyMatch(entry -> entry.player().equals(player)));
    }

    /**
     * Tells whether a player may change a warp.
     */
    public CompletableFuture<Boolean> mayManage(PlayerWarp warp, UUID player) {
        if (warp.isOwner(player)) {
            return CompletableFuture.completedFuture(true);
        }
        return isManager(warp.getId(), player);
    }

    /**
     * Makes a player a manager.
     */
    public CompletableFuture<Void> add(CommandSender sender, PlayerWarp warp, String targetName) {
        CompletableFuture<Void> result = resolve(targetName).thenCompose(target -> {
            if (target == null) {
                plugin.getMessages().send(sender, "errors.player-not-found", "{player}", targetName);
                return CompletableFuture.completedFuture(null);
            }

            if (target.uuid().equals(warp.getOwner())) {
                plugin.getMessages().send(sender, "player-warp.managers.is-owner", "{player}", target.name());
                return CompletableFuture.completedFuture(null);
            }

            return list(warp.getId()).thenCompose(entries -> {
                if (entries.stream().anyMatch(entry -> entry.player().equals(target.uuid()))) {
                    plugin.getMessages().send(sender, "player-warp.managers.already-added",
                            "{player}", target.name(), "{warp}", warp.getName());
                    return CompletableFuture.completedFuture(null);
                }

                return plugin.getDatabaseManager().getProvider()
                        .savePlayerWarpManagerEntry(PlayerWarpManagerEntry.of(warp.getId(), target.uuid(),
                                target.name()))
                        .thenRun(() -> plugin.getMessages().send(sender, "player-warp.managers.added",
                                "{player}", target.name(), "{warp}", warp.getName()));
            });
        });

        return Futures.guard(result, "PlayerWarpManagerRoleManager#add", sender);
    }

    /**
     * Removes a manager.
     */
    public CompletableFuture<Void> remove(CommandSender sender, PlayerWarp warp, String targetName) {
        CompletableFuture<Void> result = resolve(targetName).thenCompose(target -> {
            if (target == null) {
                plugin.getMessages().send(sender, "errors.player-not-found", "{player}", targetName);
                return CompletableFuture.completedFuture(null);
            }

            return plugin.getDatabaseManager().getProvider()
                    .removePlayerWarpManagerEntry(warp.getId(), target.uuid())
                    .thenAccept(removed -> plugin.getMessages().send(sender,
                            removed ? "player-warp.managers.removed" : "player-warp.managers.not-found",
                            "{player}", target.name(), "{warp}", warp.getName()));
        });

        return Futures.guard(result, "PlayerWarpManagerRoleManager#remove", sender);
    }

    private CompletableFuture<PlayerRef> resolve(String name) {
        Player online = plugin.getServer().getPlayerExact(name);

        if (online != null) {
            return CompletableFuture.completedFuture(new PlayerRef(online.getUniqueId(), online.getName()));
        }

        return plugin.getDatabaseManager().getProvider().findPlayer(name);
    }
}

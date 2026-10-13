package org.glstudio.teleports.modules.manager;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.teleports.modules.model.PlayerRef;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpBan;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerWarpBanManager {
    private final GTeleports plugin;

    public PlayerWarpBanManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    private int maxEntries() {
        return Math.max(1, plugin.getConfigManager().getPlayerWarpsConfig().getInt("bans.max-entries", 100));
    }

    public CompletableFuture<List<PlayerWarpBan>> list(String warpId) {
        return plugin.getDatabaseManager().getProvider().getPlayerWarpBans(warpId)
                .thenApply(bans -> bans.stream().filter(PlayerWarpBan::isActive).toList());
    }

    public CompletableFuture<PlayerWarpBan> find(String warpId, UUID player) {
        return list(warpId).thenApply(bans -> bans.stream()
                .filter(ban -> ban.player().equals(player))
                .findFirst()
                .orElse(null));
    }

    public CompletableFuture<Void> add(CommandSender sender, PlayerWarp warp, String targetName,
                                       long durationSeconds, String reason) {
        if (!sender.hasPermission(org.glstudio.teleports.core.util.Permissions.PLAYERWARP_BAN)) {
            plugin.getMessages().send(sender, "errors.no-permission");
            return CompletableFuture.completedFuture(null);
        }

        String bannedBy = sender instanceof Player player ? player.getUniqueId().toString() : "console";
        String bannedByName = sender.getName();

        CompletableFuture<Void> result = resolve(targetName).thenCompose(target -> {
            if (target == null) {
                plugin.getMessages().send(sender, "errors.player-not-found", "{player}", targetName);
                return CompletableFuture.completedFuture(null);
            }

            if (target.uuid().equals(warp.getOwner())) {
                plugin.getMessages().send(sender, "player-warp.ban.is-owner", "{player}", target.name());
                return CompletableFuture.completedFuture(null);
            }

            return list(warp.getId()).thenCompose(bans -> {
                if (bans.size() >= maxEntries()
                        && bans.stream().noneMatch(ban -> ban.player().equals(target.uuid()))) {
                    plugin.getMessages().send(sender, "player-warp.ban.limit-reached",
                            "{limit}", String.valueOf(maxEntries()));
                    return CompletableFuture.completedFuture(null);
                }

                PlayerWarpBan ban = PlayerWarpBan.of(warp.getId(), target.uuid(), target.name(), bannedBy,
                        bannedByName, reason, durationSeconds);

                return plugin.getDatabaseManager().getProvider().savePlayerWarpBan(ban)
                        .thenRun(() -> {
                            plugin.getMessages().send(sender,
                                    ban.isPermanent() ? "player-warp.ban.added" : "player-warp.ban.added-temporary",
                                    "{player}", target.name(), "{warp}", warp.getName(),
                                    "{reason}", reason == null || reason.isBlank() ? "-" : reason);

                            Player online = plugin.getServer().getPlayer(target.uuid());
                            if (online != null) {
                                plugin.getMessages().send(online, "player-warp.ban.notify",
                                        "{warp}", warp.getName(),
                                        "{reason}", reason == null || reason.isBlank() ? "-" : reason);
                            }
                        });
            });
        });

        return Failures.guard(plugin, result, "PlayerWarpBanManager#add", sender);
    }

    public CompletableFuture<Void> remove(CommandSender sender, PlayerWarp warp, String targetName) {
        if (!sender.hasPermission(org.glstudio.teleports.core.util.Permissions.PLAYERWARP_BAN)) {
            plugin.getMessages().send(sender, "errors.no-permission");
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> result = resolve(targetName).thenCompose(target -> {
            if (target == null) {
                plugin.getMessages().send(sender, "errors.player-not-found", "{player}", targetName);
                return CompletableFuture.completedFuture(null);
            }

            return plugin.getDatabaseManager().getProvider().removePlayerWarpBan(warp.getId(), target.uuid())
                    .thenAccept(removed -> plugin.getMessages().send(sender,
                            removed ? "player-warp.ban.removed" : "player-warp.ban.not-found",
                            "{player}", target.name(), "{warp}", warp.getName()));
        });

        return Failures.guard(plugin, result, "PlayerWarpBanManager#remove", sender);
    }

    private CompletableFuture<PlayerRef> resolve(String name) {
        Player online = plugin.getServer().getPlayerExact(name);

        if (online != null) {
            return CompletableFuture.completedFuture(new PlayerRef(online.getUniqueId(), online.getName()));
        }

        return plugin.getDatabaseManager().getProvider().findPlayer(name);
    }
}

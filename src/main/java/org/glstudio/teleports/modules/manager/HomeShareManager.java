package org.glstudio.teleports.modules.manager;

import org.bukkit.entity.Player;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.teleports.core.util.TeleportNames;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.HomeShare;
import org.glstudio.teleports.modules.model.PlayerRef;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HomeShareManager {
    private final GTeleports plugin;

    public HomeShareManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getSettings().getBoolean("home-shares.enabled", true);
    }

    private int maxSharesPerHome() {
        return Math.max(1, plugin.getSettings().getInt("home-shares.max-per-home", 10));
    }

    public CompletableFuture<Void> invite(Player owner, String targetName, String homeName,
                                          long durationSeconds) {
        if (!isEnabled()) {
            plugin.getMessages().send(owner, "errors.shares-disabled");
            return CompletableFuture.completedFuture(null);
        }

        String home = TeleportNames.normalize(homeName);

        CompletableFuture<Void> result = plugin.getHomeManager().getHome(owner.getUniqueId(), home)
                .thenCompose(existing -> {
                    if (existing == null) {
                        plugin.getMessages().send(owner, "errors.home-not-found", "{name}", homeName);
                        return CompletableFuture.completedFuture(null);
                    }

                    return resolve(targetName).thenCompose(target -> {
                        if (target == null) {
                            plugin.getMessages().send(owner, "errors.player-not-found",
                                    "{player}", targetName);
                            return CompletableFuture.completedFuture(null);
                        }

                        if (target.uuid().equals(owner.getUniqueId())) {
                            plugin.getMessages().send(owner, "errors.share-self");
                            return CompletableFuture.completedFuture(null);
                        }

                        return grant(owner, target, existing, durationSeconds);
                    });
                });

        return Failures.guard(plugin, result, "HomeShareManager#invite", owner);
    }

    private CompletableFuture<Void> grant(Player owner, PlayerRef target, Home home, long durationSeconds) {
        return plugin.getDatabaseManager().getProvider().getSharesByOwner(owner.getUniqueId())
                .thenCompose(existing -> {
                    long onThisHome = existing.stream()
                            .filter(share -> share.home().equalsIgnoreCase(home.getName()))
                            .filter(share -> !share.isExpired())
                            .filter(share -> !share.invitee().equals(target.uuid()))
                            .count();

                    if (onThisHome >= maxSharesPerHome()) {
                        plugin.getMessages().send(owner, "errors.share-limit-reached",
                                "{limit}", String.valueOf(maxSharesPerHome()));
                        return CompletableFuture.completedFuture(null);
                    }

                    HomeShare share = durationSeconds <= 0
                            ? HomeShare.permanent(owner.getUniqueId(), owner.getName(), home.getName(),
                            target.uuid(), target.name())
                            : HomeShare.temporary(owner.getUniqueId(), owner.getName(), home.getName(),
                            target.uuid(), target.name(), durationSeconds);

                    return plugin.getDatabaseManager().getProvider().shareHome(share)
                            .thenRun(() -> {
                                plugin.getMessages().send(owner, share.isPermanent()
                                                ? "share.granted" : "share.granted-temporary",
                                        "{player}", target.name(),
                                        "{home}", home.getName(),
                                        "{time}", String.valueOf(durationSeconds));

                                notifyInvitee(target.uuid(), owner.getName(), home.getName());
                            });
                });
    }

    private void notifyInvitee(UUID invitee, String ownerName, String home) {
        Player online = plugin.getServer().getPlayer(invitee);

        if (online != null && online.isOnline()) {
            plugin.getMessages().send(online, "share.received",
                    "{player}", ownerName, "{home}", home);
        }
    }

    public CompletableFuture<Void> uninvite(Player owner, String targetName, String homeName) {
        String home = TeleportNames.normalize(homeName);

        CompletableFuture<Void> result = resolve(targetName).thenCompose(target -> {
            if (target == null) {
                plugin.getMessages().send(owner, "errors.player-not-found", "{player}", targetName);
                return CompletableFuture.completedFuture(null);
            }

            return plugin.getDatabaseManager().getProvider()
                    .unshareHome(owner.getUniqueId(), home, target.uuid())
                    .thenAccept(removed -> plugin.getMessages().send(owner,
                            removed ? "share.revoked" : "errors.share-not-found",
                            "{player}", target.name(), "{home}", home));
        });

        return Failures.guard(plugin, result, "HomeShareManager#uninvite", owner);
    }

    public CompletableFuture<List<HomeShare>> outgoing(UUID owner) {
        return plugin.getDatabaseManager().getProvider().getSharesByOwner(owner)
                .thenApply(shares -> shares.stream().filter(share -> !share.isExpired()).toList());
    }

    public CompletableFuture<List<HomeShare>> incoming(UUID invitee) {
        return plugin.getDatabaseManager().getProvider().getSharesForInvitee(invitee)
                .thenApply(shares -> shares.stream().filter(share -> !share.isExpired()).toList());
    }

    public CompletableFuture<Boolean> mayUse(UUID visitor, UUID owner, String home) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(false);
        }

        return plugin.getDatabaseManager().getProvider().getSharesForInvitee(visitor)
                .thenApply(shares -> shares.stream()
                        .filter(share -> !share.isExpired())
                        .filter(share -> share.owner().equals(owner))
                        .anyMatch(share -> share.home().equalsIgnoreCase(home)));
    }

    public CompletableFuture<Boolean> grant(UUID owner, String ownerName, String home,
                                            UUID invitee, String inviteeName, long durationSeconds) {
        if (!isEnabled() || owner.equals(invitee)) {
            return CompletableFuture.completedFuture(false);
        }

        String normalized = TeleportNames.normalize(home);

        return plugin.getHomeManager().getHome(owner, normalized).thenCompose(existing -> {
            if (existing == null) {
                return CompletableFuture.completedFuture(false);
            }

            HomeShare share = durationSeconds <= 0
                    ? HomeShare.permanent(owner, ownerName, existing.getName(), invitee, inviteeName)
                    : HomeShare.temporary(owner, ownerName, existing.getName(), invitee, inviteeName,
                    durationSeconds);

            return plugin.getDatabaseManager().getProvider().shareHome(share)
                    .thenApply(ignored -> {
                        plugin.getHomeManager().refreshShareLabels(invitee);
                        return true;
                    });
        });
    }

    public CompletableFuture<Boolean> revoke(UUID owner, String home, UUID invitee) {
        return plugin.getDatabaseManager().getProvider()
                .unshareHome(owner, TeleportNames.normalize(home), invitee)
                .thenApply(removed -> {
                    if (removed) {
                        plugin.getHomeManager().refreshShareLabels(invitee);
                    }
                    return removed;
                });
    }

    public void forgetHome(UUID owner, String home) {
        Failures.log(plugin.getDatabaseManager().getProvider().clearSharesOf(owner, home),
                "HomeShareManager#forgetHome");
    }

    private CompletableFuture<PlayerRef> resolve(String name) {
        Player online = plugin.getServer().getPlayerExact(name);

        if (online != null) {
            return CompletableFuture.completedFuture(new PlayerRef(online.getUniqueId(), online.getName()));
        }

        return plugin.getDatabaseManager().getProvider().findPlayer(name);
    }
}

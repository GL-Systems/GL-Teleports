package org.glstudio.teleports.modules.manager;

import org.bukkit.entity.Player;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.PlayerRef;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds who has blocked whose teleport requests.
 */
public class TpaBlockManager {
    private final GTeleports plugin;

    private final Map<UUID, Set<UUID>> blocks = new ConcurrentHashMap<>();

    /**
     * Creates the manager.
     */
    public TpaBlockManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Tells whether this feature is on.
     */
    public boolean isEnabled() {
        return plugin.getSettings().getBoolean("tpa-blocks.enabled", true);
    }

    private int maxBlocks() {
        return Math.max(1, plugin.getSettings().getInt("tpa-blocks.max-entries", 50));
    }

    /**
     * Loads who a player blocks.
     */
    public void load(UUID uuid) {
        if (!isEnabled()) {
            return;
        }

        Futures.log(plugin.getDatabaseManager().getProvider().getTpaBlocks(uuid)
                .thenAccept(loaded -> {
                    Set<UUID> list = ConcurrentHashMap.newKeySet();
                    list.addAll(loaded);
                    blocks.put(uuid, list);
                }), "TpaBlockManager#load");
    }

    /**
     * Tells whether a player blocks another.
     */
    public boolean blocks(UUID target, UUID requester) {
        if (!isEnabled()) {
            return false;
        }

        Set<UUID> list = blocks.get(target);
        return list != null && list.contains(requester);
    }

    /**
     * Blocks a player.
     */
    public CompletableFuture<Void> block(Player owner, String targetName) {
        if (!isEnabled()) {
            plugin.getMessages().send(owner, "errors.tpa-blocks-disabled");
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> result = resolve(targetName).thenCompose(target -> {
            if (target == null) {
                plugin.getMessages().send(owner, "errors.player-not-found", "{player}", targetName);
                return CompletableFuture.completedFuture(null);
            }

            if (target.uuid().equals(owner.getUniqueId())) {
                plugin.getMessages().send(owner, "errors.block-self");
                return CompletableFuture.completedFuture(null);
            }

            Set<UUID> current = blocks.computeIfAbsent(owner.getUniqueId(),
                    id -> ConcurrentHashMap.newKeySet());

            if (current.contains(target.uuid())) {
                plugin.getMessages().send(owner, "errors.already-blocked", "{player}", target.name());
                return CompletableFuture.completedFuture(null);
            }

            if (current.size() >= maxBlocks()) {
                plugin.getMessages().send(owner, "errors.block-limit-reached",
                        "{limit}", String.valueOf(maxBlocks()));
                return CompletableFuture.completedFuture(null);
            }

            current.add(target.uuid());

            return plugin.getDatabaseManager().getProvider()
                    .addTpaBlock(owner.getUniqueId(), target.uuid())
                    .thenRun(() -> plugin.getMessages().send(owner, "tpa.blocked",
                            "{player}", target.name()));
        });

        return Futures.guard(result, "TpaBlockManager#block", owner);
    }

    /**
     * Unblocks a player.
     */
    public CompletableFuture<Void> unblock(Player owner, String targetName) {
        CompletableFuture<Void> result = resolve(targetName).thenCompose(target -> {
            if (target == null) {
                plugin.getMessages().send(owner, "errors.player-not-found", "{player}", targetName);
                return CompletableFuture.completedFuture(null);
            }

            Set<UUID> current = blocks.get(owner.getUniqueId());
            if (current != null) {
                current.remove(target.uuid());
            }

            return plugin.getDatabaseManager().getProvider()
                    .removeTpaBlock(owner.getUniqueId(), target.uuid())
                    .thenAccept(removed -> plugin.getMessages().send(owner,
                            removed ? "tpa.unblocked" : "errors.not-blocked",
                            "{player}", target.name()));
        });

        return Futures.guard(result, "TpaBlockManager#unblock", owner);
    }

    /**
     * Lists who a player blocks.
     */
    public CompletableFuture<Set<UUID>> list(UUID owner) {
        return plugin.getDatabaseManager().getProvider().getTpaBlocks(owner);
    }

    /**
     * Forgets what is held for a player.
     */
    public void forget(UUID uuid) {
        blocks.remove(uuid);
    }

    private CompletableFuture<PlayerRef> resolve(String name) {
        Player online = plugin.getServer().getPlayerExact(name);

        if (online != null) {
            return CompletableFuture.completedFuture(new PlayerRef(online.getUniqueId(), online.getName()));
        }

        return plugin.getDatabaseManager().getProvider().findPlayer(name);
    }

    /**
     * Stops this on shutdown.
     */
    public void safeDisable() {
        blocks.clear();
    }
}

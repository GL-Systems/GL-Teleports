package org.glstudio.teleports.modules.manager;

import org.bukkit.entity.Player;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.common.network.packets.InvalidationScope;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpSponsoredSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The 5 (configurable) sponsored slots shown at the top of the /pw main menu: an owner pays to
 * put one of their own warps there for a fixed duration, after which the slot frees itself up
 * automatically - nothing manual needed to reclaim it.
 */
public class PlayerWarpSponsorManager {
    private final GTeleports plugin;

    private volatile CompletableFuture<List<PlayerWarpSponsoredSlot>> cache;

    /**
     * Creates the manager.
     */
    public PlayerWarpSponsorManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Tells whether this feature is on.
     */
    public boolean isEnabled() {
        return plugin.getConfigManager().getPlayerWarpsConfig().getBoolean("sponsored.enabled", true);
    }

    /**
     * Returns how many slots exist.
     */
    public int slotCount() {
        return Math.max(0, plugin.getConfigManager().getPlayerWarpsConfig().getInt("sponsored.slots", 5));
    }

    /**
     * Returns what a slot costs.
     */
    public double cost() {
        return plugin.getConfigManager().getPlayerWarpsConfig().getDouble("sponsored.cost", 500.0D);
    }

    /**
     * Returns how long a slot lasts.
     */
    public int durationHours() {
        return Math.max(1, plugin.getConfigManager().getPlayerWarpsConfig().getInt("sponsored.duration-hours", 24));
    }

    private long durationMillis() {
        return TimeUnit.HOURS.toMillis(durationHours());
    }

    /**
     * Returns the active slots.
     */
    public CompletableFuture<List<PlayerWarpSponsoredSlot>> getActiveSlots() {
        CompletableFuture<List<PlayerWarpSponsoredSlot>> cached = cache;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (cache != null) {
                return cache;
            }

            CompletableFuture<List<PlayerWarpSponsoredSlot>> pending = plugin.getDatabaseManager().getProvider()
                    .getSponsoredSlots()
                    .thenCompose(this::dropExpired);

            pending.whenComplete((slots, error) -> {
                if (error != null) {
                    invalidateIfStill(pending);
                }
            });

            cache = pending;
            return pending;
        }
    }

    private CompletableFuture<List<PlayerWarpSponsoredSlot>> dropExpired(List<PlayerWarpSponsoredSlot> slots) {
        List<PlayerWarpSponsoredSlot> active = new ArrayList<>();
        List<CompletableFuture<Boolean>> cleanups = new ArrayList<>();

        for (PlayerWarpSponsoredSlot slot : slots) {
            if (slot.isExpired()) {
                cleanups.add(plugin.getDatabaseManager().getProvider().clearSponsoredSlot(slot.slotIndex()));
            } else {
                active.add(slot);
            }
        }

        if (cleanups.isEmpty()) {
            return CompletableFuture.completedFuture(active);
        }

        return CompletableFuture.allOf(cleanups.toArray(new CompletableFuture[0])).thenApply(ignored -> active);
    }

    /**
     * Buys a sponsored slot.
     */
    public CompletableFuture<Void> sponsor(Player player, int slotIndex, PlayerWarp warp) {
        if (!isEnabled()) {
            plugin.getMessages().send(player, "player-warp.sponsor.disabled");
            return CompletableFuture.completedFuture(null);
        }

        if (slotIndex < 0 || slotIndex >= slotCount()) {
            plugin.getMessages().send(player, "player-warp.sponsor.invalid-slot");
            return CompletableFuture.completedFuture(null);
        }

        if (!warp.isOwner(player.getUniqueId())) {
            plugin.getMessages().send(player, "errors.no-permission");
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> result = getActiveSlots().thenCompose(active -> {
            boolean taken = active.stream().anyMatch(slot -> slot.slotIndex() == slotIndex);
            if (taken) {
                plugin.getMessages().send(player, "player-warp.sponsor.slot-taken");
                return CompletableFuture.completedFuture(null);
            }

            boolean alreadySponsored = active.stream().anyMatch(slot -> slot.warpId().equals(warp.getId()));
            if (alreadySponsored) {
                plugin.getMessages().send(player, "player-warp.sponsor.already-sponsored", "{warp}", warp.getName());
                return CompletableFuture.completedFuture(null);
            }

            double price = cost();

            if (!plugin.getEconomyManager().hasEnoughMoney(player, price)) {
                plugin.getMessages().send(player, "errors.insufficient-funds",
                        "{cost}", plugin.getEconomyManager().formatCost(price));
                return CompletableFuture.completedFuture(null);
            }

            if (!plugin.getEconomyManager().takeMoney(player, price)) {
                plugin.getMessages().send(player, "errors.insufficient-funds",
                        "{cost}", plugin.getEconomyManager().formatCost(price));
                return CompletableFuture.completedFuture(null);
            }

            long now = System.currentTimeMillis();
            PlayerWarpSponsoredSlot slot = new PlayerWarpSponsoredSlot(slotIndex, warp.getId(),
                    player.getUniqueId(), player.getName(), now, now + durationMillis(), price);

            return plugin.getDatabaseManager().getProvider().saveSponsoredSlot(slot).thenRun(() -> {
                invalidateEverywhere();
                plugin.getMessages().send(player, "player-warp.sponsor.success",
                        "{warp}", warp.getName(), "{hours}", String.valueOf(durationHours()),
                        "{cost}", plugin.getEconomyManager().formatCost(price));
            });
        });

        return Futures.guard(result, "PlayerWarpSponsorManager#sponsor", player);
    }

    /**
     * Drops the cache.
     */
    public void invalidateCache() {
        cache = null;
    }

    private synchronized void invalidateIfStill(CompletableFuture<List<PlayerWarpSponsoredSlot>> expected) {
        if (cache == expected) {
            cache = null;
        }
    }

    private void invalidateEverywhere() {
        invalidateCache();
        plugin.getCrossServerManager().broadcastInvalidation(InvalidationScope.SPONSORED_SLOT, null);
    }

    /**
     * Stops this on shutdown.
     */
    public void safeDisable() {
        cache = null;
    }
}

package org.glstudio.teleports.modules.manager;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.api.Events;
import org.glstudio.teleports.api.event.PostTeleportEvent;
import org.glstudio.teleports.api.event.PreTeleportEvent;
import org.glstudio.teleports.modules.model.TeleportRules;
import org.glstudio.teleports.modules.model.SafetyCheckResult;
import org.glstudio.teleports.modules.model.TeleportDestination;
import org.glstudio.teleports.modules.model.TeleportResult;
import org.glstudio.teleports.modules.model.TeleportType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.glstudio.nexus.utils.LoggerUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class TeleportManager {
    private final GTeleports plugin;
    private final Map<UUID, Long> immunityMap;

    public TeleportManager(GTeleports plugin) {
        this.plugin = plugin;
        this.immunityMap = new ConcurrentHashMap<>();
    }

    public void requestTeleport(Player player, Location destination, TeleportType type, Consumer<Boolean> callback) {
        requestTeleport(player, destination, type, null, null, callback);
    }

    public void requestTeleport(Player player, Location destination, TeleportType type,
                                TeleportDestination destinationObject, String destinationName,
                                Consumer<Boolean> callback) {
        requestTeleport(player, destination, type, destinationObject, destinationName, false, callback);
    }

    public void requestTeleport(Player player, Location destination, TeleportType type,
                                TeleportDestination destinationObject, String destinationName,
                                boolean instant, Consumer<Boolean> callback) {
        TeleportRules rules = TeleportRules.resolve(plugin, player, type, destinationObject);

        run(player, destination, type, rules, destinationName, true, instant, callback,
                target -> completeTeleport(player, target, type, rules, destinationName, callback));
    }

    public void requestRemoteTeleport(Player player, TeleportType type, Runnable onReady) {
        requestRemoteTeleport(player, type, null, null, onReady);
    }

    public void requestRemoteTeleport(Player player, TeleportType type,
                                      TeleportDestination destinationObject, String destinationName,
                                      Runnable onReady) {
        requestRemoteTeleport(player, type, destinationObject, destinationName, false, onReady);
    }

    public void requestRemoteTeleport(Player player, TeleportType type,
                                      TeleportDestination destinationObject, String destinationName,
                                      boolean instant, Runnable onReady) {
        TeleportRules rules = TeleportRules.resolve(plugin, player, type, destinationObject);

        run(player, player.getLocation(), type, rules, destinationName, false, instant, success -> {
        }, target -> {
            plugin.getEffectManager().playTeleportEffects(player, type, EffectManager.EffectStage.TELEPORT,
                    destinationName);
            setCooldown(player, type, rules, destinationName);
            onReady.run();
        });
    }

    private void run(Player player, Location destination, TeleportType type, TeleportRules rules,
                     String destinationName, boolean inspectDestination, boolean instant,
                     Consumer<Boolean> callback, Consumer<Location> onReady) {
        TeleportResult result = checkRequirements(player, destination, type, rules, inspectDestination);

        if (!result.isSuccess()) {
            plugin.getMessages().send(player, "errors." + result.getMessage(), result.getPlaceholders());
            plugin.getHistoryManager().recordFailure(player, type, destinationName,
                    player.getLocation(), result.getMessage());
            callback.accept(false);
            return;
        }

        Location target = destination;

        if (inspectDestination) {
            target = plugin.getTeleportSafetyManager().resolve(player, destination, type);

            if (target == null) {
                SafetyCheckResult safety = plugin.getTeleportSafetyManager().checkSafety(destination, type);
                plugin.getMessages().send(player, "errors.unsafe-location." + safety.getReason());
                plugin.getHistoryManager().recordFailure(player, type, destinationName,
                        player.getLocation(), "unsafe-" + safety.getReason());
                callback.accept(false);
                return;
            }
        }

        Location resolved = target;
        int delaySeconds = instant ? 0 : rules.delaySeconds();

        plugin.getDelayManager().startDelay(player, resolved, type, delaySeconds, destinationName, () -> {
            if (!player.isOnline()) {
                callback.accept(false);
                return;
            }

            if (!chargeOrExplain(player, type, rules)) {
                callback.accept(false);
                return;
            }

            onReady.accept(resolved);
        });
    }

    private TeleportResult checkRequirements(Player player, Location destination, TeleportType type,
                                             TeleportRules rules, boolean inspectDestination) {
        if (plugin.getCooldownManager().hasCooldown(player, type)) {
            long remaining = plugin.getCooldownManager().getRemainingCooldown(player, type);
            return TeleportResult.failure("cooldown", "{time}", String.valueOf(remaining));
        }

        if (inspectDestination
                && plugin.getBlacklistWorldManager().isBlacklisted(player, destination.getWorld(), type)) {
            return TeleportResult.failure("world-blacklisted");
        }

        if (!plugin.getEconomyManager().hasEnoughMoney(player, rules.cost())) {
            return TeleportResult.failure("insufficient-funds",
                    "{cost}", plugin.getEconomyManager().formatCost(rules.cost()));
        }

        return TeleportResult.success();
    }

    public void refund(Player player, TeleportType type) {
        refund(player, type, TeleportRules.global(plugin, type));
    }

    public void refund(Player player, TeleportType type, TeleportRules rules) {
        plugin.getCooldownManager().removeCooldown(player, type);

        if (plugin.getEconomyManager().refund(player, rules.cost())) {
            plugin.getMessages().send(player, "teleport.refunded",
                    "{cost}", plugin.getEconomyManager().formatCost(rules.cost()));
        }
    }

    public Location completeArrival(Player player, Location destination, TeleportType type,
                                    String destinationName) {
        if (plugin.getBlacklistWorldManager().isBlacklisted(player, destination.getWorld(), type)) {
            LoggerUtils.logWarn("Not placing " + player.getName() + " at their " + type
                    + " destination: world '" + destination.getWorld().getName() + "' is in blacklist."
                    + type.getConfigKey() + " on this server.");
            plugin.getMessages().send(player, "errors.world-blacklisted");
            return null;
        }

        Location target = plugin.getTeleportSafetyManager().resolve(player, destination, type);
        if (target == null) {
            SafetyCheckResult safety = plugin.getTeleportSafetyManager().checkSafety(destination, type);

            LoggerUtils.logWarn("Not placing " + player.getName() + " at their " + type + " destination ("
                    + destination.getWorld().getName() + " " + Math.round(destination.getX()) + " "
                    + Math.round(destination.getY()) + " " + Math.round(destination.getZ())
                    + "): the safety check reports '" + safety.getReason() + "' and nothing within "
                    + "safety.relocate-radius is better. They have been left where they landed.");

            plugin.getMessages().send(player, "errors.unsafe-location." + safety.getReason());
            return null;
        }

        Location origin = player.getLocation().clone();

        plugin.getEffectManager().playTeleportEffects(player, type, EffectManager.EffectStage.TELEPORT_ARRIVE,
                destinationName);
        plugin.getEffectManager().clearBossBar(player);

        player.teleport(target);

        applyImmunity(player, type);

        sendArrivalMessage(player, destinationName);

        Events.call(new PostTeleportEvent(player, type, origin, target, destinationName, true));
        return target;
    }

    public void completePlacedArrival(Player player, Location destination, TeleportType type,
                                      String destinationName) {
        plugin.getEffectManager().playTeleportEffects(player, type, EffectManager.EffectStage.TELEPORT_ARRIVE,
                destinationName);
        plugin.getEffectManager().clearBossBar(player);

        applyImmunity(player, type);

        sendArrivalMessage(player, destinationName);

        Events.call(new PostTeleportEvent(player, type, destination, destination, destinationName, true));
    }

    private void completeTeleport(Player player, Location destination, TeleportType type,
                                  TeleportRules rules, String destinationName, Consumer<Boolean> callback) {
        Location origin = player.getLocation().clone();

        PreTeleportEvent event = new PreTeleportEvent(player, type, origin, destination, destinationName);

        if (!Events.callAndExplain(event, player, event.getCancelMessage())) {
            plugin.getEffectManager().clearBossBar(player);
            plugin.getEffectManager().playTeleportEffects(player, type, EffectManager.EffectStage.CANCELLED,
                    destinationName);
            plugin.getEconomyManager().refund(player, rules.cost());
            plugin.getHistoryManager().recordFailure(player, type, destinationName, origin, "CANCELLED_BY_PLUGIN");
            callback.accept(false);
            return;
        }

        Location target = event.getDestination() == null ? destination : event.getDestination();

        Location safe = plugin.getTeleportSafetyManager().resolve(player, target, type);
        Location finalTarget = safe == null ? target : safe;

        ChunkPreloader.prepare(plugin, finalTarget).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                callback.accept(false);
                return;
            }

            plugin.getTeleportQueue().submit(player,
                    () -> move(player, origin, finalTarget, type, rules, destinationName, callback));
        }));
    }

    private void move(Player player, Location origin, Location destination, TeleportType type,
                      TeleportRules rules, String destinationName, Consumer<Boolean> callback) {
        plugin.getEffectManager().playTeleportEffects(player, type, EffectManager.EffectStage.TELEPORT,
                destinationName);
        plugin.getEffectManager().clearBossBar(player);

        player.teleport(destination);

        plugin.getEffectManager().playTeleportEffects(player, type, EffectManager.EffectStage.TELEPORT_ARRIVE,
                destinationName);

        setCooldown(player, type, rules, destinationName);

        applyImmunity(player, type);

        sendArrivalMessage(player, destinationName);

        plugin.getHistoryManager().record(player, type, destinationName, origin, destination, rules.cost());
        plugin.getBackManager().recordBackPoint(player, origin, plugin.getCrossServerManager().currentServer());
        Events.call(new PostTeleportEvent(player, type, origin, destination, destinationName, false));

        callback.accept(true);
    }

    private void sendArrivalMessage(Player player, String destinationName) {
        plugin.getMessages().send(player, "teleport.success",
                "{name}", destinationName == null ? "" : destinationName);
    }

    private void setCooldown(Player player, TeleportType type, TeleportRules rules, String destinationName) {
        plugin.getCooldownManager().setCooldown(player, type, rules.cooldownSeconds());

        if (rules.cooldownSeconds() > 0) {
            plugin.getEffectManager().playTeleportEffects(player, type,
                    EffectManager.EffectStage.COOLDOWN_START, destinationName);
        }
    }

    private boolean chargeOrExplain(Player player, TeleportType type, TeleportRules rules) {
        if (plugin.getEconomyManager().takeMoney(player, rules.cost())) {
            return true;
        }

        plugin.getMessages().send(player, "errors.insufficient-funds",
                "{cost}", plugin.getEconomyManager().formatCost(rules.cost()));
        plugin.getEffectManager().clearBossBar(player);
        return false;
    }

    private void applyImmunity(Player player, TeleportType type) {
        String path = "teleport-settings." + type.getConfigKey() + ".immunity";
        int immunityTicks = plugin.getSettings().getInt(path, 0);

        if (immunityTicks > 0) {
            immunityMap.put(player.getUniqueId(), System.currentTimeMillis() + (immunityTicks * 50L));

            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    immunityMap.remove(player.getUniqueId()), immunityTicks);
        }
    }

    public boolean hasImmunity(Player player) {
        Long immunityEnd = immunityMap.get(player.getUniqueId());

        if (immunityEnd == null) {
            return false;
        }

        if (System.currentTimeMillis() >= immunityEnd) {
            immunityMap.remove(player.getUniqueId());
            return false;
        }

        return true;
    }

    public void forget(UUID uuid) {
        immunityMap.remove(uuid);
    }

    public void safeDisable() {
        immunityMap.clear();
    }
}

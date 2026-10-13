package org.glstudio.teleports.modules.manager;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.teleports.core.util.Permissions;
import org.glstudio.teleports.modules.model.BackLocation;
import org.glstudio.teleports.modules.model.TeleportType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /back - returns a player to wherever they were right before their last teleport.
 *
 * <p>A point is recorded after every completed teleport (see the call sites in
 * {@link TeleportManager#move} for local arrivals and {@link CrossServerManager#sendTo} for
 * cross-server departures), which is also what gives /back its toggle behaviour: using it counts
 * as a teleport in its own right, so it sets a fresh point at the spot you just left.
 *
 * <p>Two independent blacklists keep a point from ever pointing somewhere off-limits:
 * {@code blacklist.back} (worlds, checked the same way as every other teleport type) and
 * {@code back.blocked-servers} (servers, since cross-server sends bypass that generic check).
 * Both are enforced twice - once so a point is never saved from a blocked place, and once so
 * /back refuses to use one even if the blacklist changed after the point was saved.
 */
public class BackManager {
    private final GTeleports plugin;
    private final Map<UUID, BackLocation> cache = new ConcurrentHashMap<>();

    public BackManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getSettings().getBoolean("back.enabled", true);
    }

    private boolean isWorldBlocked(String world) {
        return plugin.getSettings().getStringList("blacklist.back").stream()
                .anyMatch(blocked -> blocked.equalsIgnoreCase(world));
    }

    private boolean isServerBlocked(String server) {
        if (server == null) {
            return false;
        }
        return plugin.getSettings().getStringList("back.blocked-servers").stream()
                .anyMatch(blocked -> blocked.equalsIgnoreCase(server));
    }

    /**
     * Called right after a teleport completes, with the location the player teleported
     * <em>from</em>. Silently skipped if that spot is blacklisted, if /back is disabled, or if
     * the mover has no permission - none of those should ever overwrite a perfectly good
     * existing point with one that can never be used.
     */
    public void recordBackPoint(Player player, Location origin, String originServer) {
        if (!isEnabled() || !player.hasPermission(Permissions.BACK_USE)) {
            return;
        }

        if (origin == null || origin.getWorld() == null) {
            return;
        }

        if (isWorldBlocked(origin.getWorld().getName()) || isServerBlocked(originServer)) {
            return;
        }

        BackLocation location = new BackLocation(player.getUniqueId(), origin, originServer);
        cache.put(player.getUniqueId(), location);

        Failures.log(plugin.getDatabaseManager().getProvider().saveBackLocation(location),
                "BackManager#recordBackPoint");
    }

    public void load(UUID uuid) {
        Failures.log(plugin.getDatabaseManager().getProvider().getBackLocation(uuid).thenAccept(location -> {
            if (location != null) {
                cache.put(uuid, location);
            }
        }), "BackManager#load");
    }

    public void forget(UUID uuid) {
        cache.remove(uuid);
    }

    public void teleportBack(Player player) {
        if (!isEnabled()) {
            plugin.getMessages().send(player, "errors.back-disabled");
            return;
        }

        BackLocation location = cache.get(player.getUniqueId());
        if (location == null) {
            plugin.getMessages().send(player, "errors.back-not-set");
            return;
        }

        if (isWorldBlocked(location.getWorld()) || isServerBlocked(location.getServer())) {
            plugin.getMessages().send(player, "errors.back-destination-blacklisted");
            return;
        }

        if (!plugin.getCrossServerManager().isLocal(location.getServer())) {
            plugin.getCrossServerManager().sendTo(player, TeleportType.BACK, location);
            return;
        }

        Location destination = location.toLocation();
        if (destination == null) {
            plugin.getMessages().send(player, "errors.world-not-found");
            return;
        }

        plugin.getTeleportManager().requestTeleport(player, destination, TeleportType.BACK, location, "back",
                success -> {
                });
    }

    public void safeDisable() {
        cache.clear();
    }
}

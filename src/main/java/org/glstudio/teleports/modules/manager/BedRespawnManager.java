package org.glstudio.teleports.modules.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.StoredLocation;
import org.glstudio.teleports.modules.model.TeleportType;

/**
 * Remembers each player's bed across servers.
 */
public class BedRespawnManager {
    private final GTeleports plugin;

    /**
     * Creates the manager.
     */
    public BedRespawnManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the server this belongs to.
     */
    public String server() {
        return plugin.getSettings().getString("spawn.bed-respawn-server", "");
    }

    /**
     * Tells whether this feature is on.
     */
    public boolean isEnabled() {
        return !server().isBlank();
    }

    /**
     * Tells whether this server handles bed respawns.
     */
    public boolean isBedRespawnServer() {
        return isEnabled() && plugin.getCrossServerManager().isLocal(server());
    }

    /**
     * Stores a player's bed.
     */
    public void save(Player player, Location location) {
        if (!isBedRespawnServer() || location == null || location.getWorld() == null) {
            return;
        }

        Futures.log(plugin.getDatabaseManager().getProvider()
                        .saveBedRespawnPoint(player.getUniqueId(), StoredLocation.of(location)),
                "BedRespawnManager#save");
    }

    /**
     * Forgets a player's bed.
     */
    public void clear(Player player) {
        if (!isBedRespawnServer()) {
            return;
        }

        Futures.log(plugin.getDatabaseManager().getProvider().clearBedRespawnPoint(player.getUniqueId()),
                "BedRespawnManager#clear");
    }

    /**
     * Sends the to stored or spawn.
     */
    public void sendToStoredOrSpawn(Player player) {
        if (!isEnabled()) {
            plugin.getSpawnManager().teleportToSpawn(player, true);
            return;
        }

        Futures.log(plugin.getDatabaseManager().getProvider().getBedRespawnPoint(player.getUniqueId())
                .thenAccept(stored -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }

                    if (stored == null) {
                        plugin.getSpawnManager().teleportToSpawn(player, true);
                        return;
                    }

                    SpawnLocation destination = new SpawnLocation(stored.world(), stored.x(), stored.y(),
                            stored.z(), stored.yaw(), stored.pitch(), server());

                    plugin.getCrossServerManager().sendTo(player, TeleportType.SPAWN, destination, true);
                })), "BedRespawnManager#sendToStoredOrSpawn");
    }
}

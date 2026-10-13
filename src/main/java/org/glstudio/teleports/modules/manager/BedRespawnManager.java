package org.glstudio.teleports.modules.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.StoredLocation;
import org.glstudio.teleports.modules.model.TeleportType;

public class BedRespawnManager {
    private final GTeleports plugin;

    public BedRespawnManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    public String server() {
        return plugin.getSettings().getString("spawn.bed-respawn-server", "");
    }

    public boolean isEnabled() {
        return !server().isBlank();
    }

    public boolean isBedRespawnServer() {
        return isEnabled() && plugin.getCrossServerManager().isLocal(server());
    }

    public void save(Player player, Location location) {
        if (!isBedRespawnServer() || location == null || location.getWorld() == null) {
            return;
        }

        Failures.log(plugin.getDatabaseManager().getProvider()
                        .saveBedRespawnPoint(player.getUniqueId(), StoredLocation.of(location)),
                "BedRespawnManager#save");
    }

    public void clear(Player player) {
        if (!isBedRespawnServer()) {
            return;
        }

        Failures.log(plugin.getDatabaseManager().getProvider().clearBedRespawnPoint(player.getUniqueId()),
                "BedRespawnManager#clear");
    }

    public void sendToStoredOrSpawn(Player player) {
        if (!isEnabled()) {
            plugin.getSpawnManager().teleportToSpawn(player, true);
            return;
        }

        Failures.log(plugin.getDatabaseManager().getProvider().getBedRespawnPoint(player.getUniqueId())
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

package org.glstudio.teleports.modules.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.manager.BedRespawnManager;
import org.glstudio.teleports.modules.model.SpawnLocation;

/**
 * Sends players to their respawn point.
 */
public class RespawnListener implements Listener {
    private final GTeleports plugin;

    private boolean warnedAboutMissingSpawn;

    /**
     * Creates the listener.
     */
    public RespawnListener(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles {@link PlayerRespawnEvent}.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.getSettings().getBoolean("spawn.respawn-at-spawn", false)) {
            return;
        }

        if (plugin.getSettings().getBoolean("spawn.respawn-respects-bed", true)
                && (event.isBedSpawn() || event.isAnchorSpawn())) {
            return;
        }

        Player player = event.getPlayer();
        BedRespawnManager bedRespawn = plugin.getBedRespawnManager();

        if (bedRespawn.isEnabled() && !bedRespawn.isBedRespawnServer()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    bedRespawn.sendToStoredOrSpawn(player);
                }
            }, 1L);

            return;
        }

        SpawnLocation spawn = plugin.getSpawnManager().getCachedSpawn();

        if (spawn == null) {
            if (!warnedAboutMissingSpawn) {
                warnedAboutMissingSpawn = true;
                LoggerUtils.logWarn("spawn.respawn-at-spawn is on but the network has no spawn yet. "
                        + "Run /setspawn where players should respawn; until then the world spawn is used.");
            }
            return;
        }

        if (!plugin.getCrossServerManager().isLocal(spawn.getServer())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getSpawnManager().teleportToSpawn(player, true);
                }
            }, 1L);

            return;
        }

        Location target = spawn.toLocation();

        if (target == null) {
            LoggerUtils.logWarn("Not respawning " + event.getPlayer().getName() + " at spawn: world '"
                    + spawn.getWorld() + "' is not loaded on this server. Run /setspawn here, or turn "
                    + "spawn.respawn-at-spawn off.");
            return;
        }

        event.setRespawnLocation(target);
    }
}

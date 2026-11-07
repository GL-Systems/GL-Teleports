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
    // Set by GL-Sync while it revives a player whose stale player file on this server says they are dead.
    private static final String GLSYNC_REVIVE_METADATA = "glsync-revive";

    private static final long REVIVAL_ARRIVAL_DELAY_TICKS = 5L;

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
        Player player = event.getPlayer();

        // A revival is not a death: the player is alive on the network and was arriving here, so
        // finish that arrival (their teleport or last location) instead of sending them to spawn.
        if (player.hasMetadata(GLSYNC_REVIVE_METADATA)) {
            // A few ticks, so a plugin that finishes its own arrival on respawn (an RTP) goes first
            // and a last-location restore can see it and stay out of the way.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getCrossServerManager().runAfterRevival(player);
                }
            }, REVIVAL_ARRIVAL_DELAY_TICKS);
            return;
        }

        // A real respawn wins over an arrival that was waiting for the player to be alive.
        plugin.getCrossServerManager().discardAfterRevival(player.getUniqueId());

        // Leaving the End fires this event too; that is not a death.
        if (event.getRespawnReason() == PlayerRespawnEvent.RespawnReason.END_PORTAL) {
            return;
        }

        if (!plugin.getSettings().getBoolean("spawn.respawn-at-spawn", false)) {
            return;
        }

        if (plugin.getSettings().getBoolean("spawn.respawn-respects-bed", true)
                && (event.isBedSpawn() || event.isAnchorSpawn())) {
            return;
        }

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

package org.glstudio.teleports.modules.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.glstudio.teleports.GTeleports;

/**
 * Cancels delayed teleports when players move.
 */
public class MoveListener implements Listener {
    private final GTeleports plugin;

    /**
     * Creates the listener.
     */
    public MoveListener(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles {@link PlayerMoveEvent}.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getDelayManager().hasDelay(player)) {
            return;
        }

        if (!plugin.getDelayManager().shouldCancelOnMove(player)) {
            return;
        }

        if (plugin.getDelayManager().hasMoved(player)) {
            plugin.getDelayManager().cancelDelay(player);
        }
    }

    /**
     * Handles {@link PlayerTeleportEvent}.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getDelayManager().hasDelay(player)) {
            return;
        }

        plugin.getDelayManager().cancelDelay(player);
    }
}

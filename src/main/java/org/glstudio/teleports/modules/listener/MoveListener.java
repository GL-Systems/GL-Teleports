package org.glstudio.teleports.modules.listener;

import org.glstudio.teleports.GTeleports;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class MoveListener implements Listener {
    private final GTeleports plugin;

    public MoveListener(GTeleports plugin) {
        this.plugin = plugin;
    }

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getDelayManager().hasDelay(player)) {
            return;
        }

        plugin.getDelayManager().cancelDelay(player);
    }
}

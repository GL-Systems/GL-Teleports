package org.glstudio.teleports.modules.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.glstudio.teleports.GTeleports;

/**
 * Finishes the teleports of players arriving from another server.
 */
public class ArrivalListener implements Listener {
    private final GTeleports plugin;

    /**
     * Creates the listener.
     */
    public ArrivalListener(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles {@link PlayerJoinEvent}.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getCrossServerManager().handleArrival(event.getPlayer());
    }

    /**
     * Handles {@link PlayerQuitEvent}.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getCrossServerManager().forget(event.getPlayer().getUniqueId());
    }
}

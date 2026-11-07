package org.glstudio.teleports.modules.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.glstudio.teleports.GTeleports;

/**
 * Keeps the network player list up to date.
 */
public class PresenceListener implements Listener {
    private final GTeleports plugin;

    /**
     * Creates the listener.
     */
    public PresenceListener(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles {@link PlayerJoinEvent}.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getCrossServerManager().getPlayerRegistry().register(event.getPlayer());
    }

    /**
     * Handles {@link PlayerQuitEvent}.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getCrossServerManager().getPlayerRegistry().unregister(event.getPlayer());
    }
}

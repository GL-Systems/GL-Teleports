package org.glstudio.teleports.modules.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
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
     * Notes a teleport by a plugin or command, which right after arriving means someone else placed the player.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.PLUGIN || cause == PlayerTeleportEvent.TeleportCause.COMMAND) {
            plugin.getCrossServerManager().noteTeleport(event.getPlayer().getUniqueId());
        }
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

package org.glstudio.teleports.modules.listener;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

/**
 * Places players at their destination while they log in.
 */
public class LoginPlacementListener implements Listener {
    private final GTeleports plugin;

    /**
     * Creates the listener.
     */
    public LoginPlacementListener(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles {@link PlayerSpawnLocationEvent}.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnLocation(PlayerSpawnLocationEvent event) {
        plugin.getCrossServerManager().watchArrival(event.getPlayer().getUniqueId());

        Location destination = plugin.getCrossServerManager().placeAtLogin(event.getPlayer());

        if (destination == null) {
            return;
        }

        event.setSpawnLocation(destination);

        LoggerUtils.logDebug("LoginPlacementListener", "Placed " + event.getPlayer().getName()
                + " at their cross-server destination during login.");
    }
}

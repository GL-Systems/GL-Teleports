package org.glstudio.teleports.modules.listener;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.glstudio.nexus.utils.LoggerUtils;
import org.glstudio.teleports.GTeleports;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public class LoginPlacementListener implements Listener {
    private final GTeleports plugin;

    public LoginPlacementListener(GTeleports plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnLocation(PlayerSpawnLocationEvent event) {
        Location destination = plugin.getCrossServerManager().placeAtLogin(event.getPlayer());

        if (destination == null) {
            return;
        }

        event.setSpawnLocation(destination);

        LoggerUtils.logDebug("LoginPlacementListener", "Placed " + event.getPlayer().getName()
                + " at their cross-server destination during login.");
    }
}

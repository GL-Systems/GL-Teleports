package org.glstudio.teleports.modules.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSpawnChangeEvent;
import org.glstudio.teleports.GTeleports;

public class BedRespawnListener implements Listener {
    private final GTeleports plugin;

    public BedRespawnListener(GTeleports plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawnChange(PlayerSpawnChangeEvent event) {
        if (!plugin.getBedRespawnManager().isEnabled()) {
            return;
        }

        switch (event.getCause()) {
            case BED, RESPAWN_ANCHOR -> plugin.getBedRespawnManager().save(event.getPlayer(), event.getNewSpawn());
            case RESET -> plugin.getBedRespawnManager().clear(event.getPlayer());
            default -> {
            }
        }
    }
}

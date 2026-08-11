package org.glstudio.teleports.modules.listener;

import org.glstudio.teleports.GTeleports;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class MoveListener implements Listener {

    private final GTeleports plugin;

    public MoveListener(GTeleports plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getDelayManager().hasDelay(player)) {
            return;
        }

        if (!plugin.getDelayManager().shouldCancelOnMove(player)) {
            return;
        }

        if (plugin.getDelayManager().hasMoved(player, event.getFrom())) {
            plugin.getDelayManager().cancelDelay(player);
        }
    }
}

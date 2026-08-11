package org.glstudio.teleports.modules.listener;

import org.glstudio.teleports.GTeleports;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuitListener implements Listener {

    private final GTeleports plugin;

    public QuitListener(GTeleports plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.getDelayManager().hasDelay(player)) {
            plugin.getDelayManager().cancelDelay(player);
        }

        if (plugin.getConfigManager().getMainConfig().getBoolean("clear-cooldowns-on-quit", false)) {
            plugin.getCooldownManager().clearAllCooldowns(player);
        }
    }
}

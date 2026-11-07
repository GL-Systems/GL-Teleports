package org.glstudio.teleports.modules.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.glstudio.nexus.concurrent.Futures;
import org.glstudio.teleports.GTeleports;

/**
 * Forgets what is held for players who leave.
 */
public class QuitListener implements Listener {
    private final GTeleports plugin;

    /**
     * Creates the listener.
     */
    public QuitListener(GTeleports plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles {@link PlayerJoinEvent}.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getPlayerSettings().load(player.getUniqueId());

        Futures.log(plugin.getDatabaseManager().getProvider()
                .rememberPlayer(player.getUniqueId(), player.getName()), "QuitListener#rememberPlayer");

        plugin.getHomeManager().getCachedHomes(player.getUniqueId());

        plugin.getTpaBlockManager().load(player.getUniqueId());

        plugin.getHomeManager().refreshShareLabels(player.getUniqueId());

        plugin.getCooldownManager().loadNetworkCooldowns(player.getUniqueId());

        plugin.getBackManager().load(player.getUniqueId());
    }

    /**
     * Handles {@link PlayerQuitEvent}.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        plugin.getLastLocationManager().save(player);

        plugin.getDelayManager().cancelDelay(player, false);
        plugin.getEffectManager().clearBossBar(player);
        plugin.getHomeManager().forget(player.getUniqueId());
        plugin.getTpaManager().forget(player.getUniqueId());
        plugin.getTeleportManager().forget(player.getUniqueId());
        plugin.getConfirmations().forget(player.getUniqueId());
        plugin.getTpaBlockManager().forget(player.getUniqueId());
        plugin.getTeleportQueue().forget(player.getUniqueId());
        plugin.getPlayerWarpManager().forgetPasswordUnlocks(player.getUniqueId());
        plugin.getBackManager().forget(player.getUniqueId());

        if (plugin.getSettings().getBoolean("clear-cooldowns-on-quit", false)) {
            plugin.getCooldownManager().forget(player.getUniqueId());
        }
    }
}

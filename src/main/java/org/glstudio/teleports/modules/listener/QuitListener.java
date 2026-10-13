package org.glstudio.teleports.modules.listener;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Failures;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuitListener implements Listener {
    private final GTeleports plugin;

    public QuitListener(GTeleports plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getPlayerSettings().load(player.getUniqueId());

        Failures.log(plugin.getDatabaseManager().getProvider()
                .rememberPlayer(player.getUniqueId(), player.getName()), "QuitListener#rememberPlayer");

        plugin.getHomeManager().getCachedHomes(player.getUniqueId());

        plugin.getTpaBlockManager().load(player.getUniqueId());

        plugin.getHomeManager().refreshShareLabels(player.getUniqueId());

        plugin.getCooldownManager().loadNetworkCooldowns(player.getUniqueId());

        plugin.getBackManager().load(player.getUniqueId());
    }

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
        plugin.getChatInputManager().cancel(player.getUniqueId());
        plugin.getBackManager().forget(player.getUniqueId());

        if (plugin.getSettings().getBoolean("clear-cooldowns-on-quit", false)) {
            plugin.getCooldownManager().forget(player.getUniqueId());
        }
    }
}

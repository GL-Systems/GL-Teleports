package org.glstudio.teleports.modules.listener;

import org.glstudio.teleports.GTeleports;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class DamageListener implements Listener {

    private final GTeleports plugin;

    public DamageListener(GTeleports plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (plugin.getTeleportManager().hasImmunity(player)) {
            event.setCancelled(true);
            return;
        }

        if (plugin.getDelayManager().hasDelay(player)) {
            if (plugin.getConfigManager().getMainConfig().getBoolean("teleport-settings.cancel-on-damage", true)) {
                plugin.getDelayManager().cancelDelay(player);
            }
        }
    }
}

package org.glstudio.teleports.modules.listener;

import org.glstudio.teleports.GTeleports;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.List;
import java.util.Locale;

public class DamageListener implements Listener {
    private static final List<String> DEFAULT_IMMUNE_CAUSES =
            List.of("FALL", "SUFFOCATION", "FIRE_TICK", "HOT_FLOOR", "CONTACT", "CRAMMING");

    private final GTeleports plugin;

    public DamageListener(GTeleports plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (plugin.getTeleportManager().hasImmunity(player) && isCovered(event.getCause())) {
            event.setCancelled(true);
            return;
        }

        if (plugin.getDelayManager().hasDelay(player)
                && plugin.getSettings().getBoolean("teleport-settings.cancel-on-damage", true)) {
            plugin.getDelayManager().cancelDelay(player);
        }
    }

    private boolean isCovered(EntityDamageEvent.DamageCause cause) {
        List<String> configured = plugin.getSettings()
                .getStringList("safety.immunity-causes");

        List<String> causes = configured.isEmpty() ? DEFAULT_IMMUNE_CAUSES : configured;

        for (String candidate : causes) {
            if (candidate.toUpperCase(Locale.ROOT).equals(cause.name())) {
                return true;
            }
        }

        return false;
    }
}

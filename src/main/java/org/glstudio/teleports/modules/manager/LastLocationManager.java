package org.glstudio.teleports.modules.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.core.util.Failures;
import org.glstudio.teleports.modules.model.StoredLocation;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LastLocationManager {
    private final GTeleports plugin;

    public LastLocationManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getSettings().getBoolean("last-location.enabled", true);
    }

    public void startAutoSave() {
        if (!isEnabled()) {
            return;
        }

        int seconds = Math.max(15, plugin.getSettings().getInt("last-location.save-interval-seconds", 60));
        long ticks = seconds * 20L;

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                save(player);
            }
        }, ticks, ticks);
    }

    public CompletableFuture<Void> save(Player player) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        Location location = player.getLocation();
        if (location.getWorld() == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (isExcluded(location.getWorld().getName())) {
            return CompletableFuture.completedFuture(null);
        }

        return Failures.log(plugin.getDatabaseManager().getProvider().saveLastLocation(
                        player.getUniqueId(),
                        plugin.getCrossServerManager().currentServer(),
                        StoredLocation.of(location)),
                "LastLocationManager#save");
    }

    public void restore(Player player) {
        if (!isEnabled()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        String server = plugin.getCrossServerManager().currentServer();

        Failures.log(plugin.getDatabaseManager().getProvider().getLastLocation(uuid, server)
                .thenAccept(stored -> {
                    if (stored == null) {
                        return;
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            return;
                        }

                        Location target = stored.toLocation();
                        if (target == null) {
                            return;
                        }

                        if (isExcluded(target.getWorld().getName())) {
                            return;
                        }

                        player.teleport(target);
                        plugin.getMessages().send(player, "teleport.returned");
                    });
                }), "LastLocationManager#restore");
    }

    private boolean isExcluded(String world) {
        for (String excluded : plugin.getSettings().getStringList("last-location.excluded-worlds")) {
            if (excluded.equalsIgnoreCase(world)) {
                return true;
            }
        }
        return false;
    }
}

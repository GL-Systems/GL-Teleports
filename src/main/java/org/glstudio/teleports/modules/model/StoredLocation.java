package org.glstudio.teleports.modules.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * A location saved by world name.
 */
public record StoredLocation(String world, double x, double y, double z, float yaw, float pitch) {
    /**
     * Creates one from a location.
     */
    public static StoredLocation of(Location location) {
        return new StoredLocation(location.getWorld().getName(), location.getX(), location.getY(),
                location.getZ(), location.getYaw(), location.getPitch());
    }

    /**
     * Returns this as a location.
     */
    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }
}

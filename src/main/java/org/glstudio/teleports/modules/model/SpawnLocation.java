package org.glstudio.teleports.modules.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * The location of the spawn.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpawnLocation implements TeleportDestination {
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private String server;

    /**
     * Creates the spawn location.
     */
    public SpawnLocation(Location location, String server) {
        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
        this.server = server;
    }

    /**
     * Returns this as a location.
     */
    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }
}

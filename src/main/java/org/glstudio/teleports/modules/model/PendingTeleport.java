package org.glstudio.teleports.modules.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

@Data
@NoArgsConstructor
public class PendingTeleport {
    private TeleportType type;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private long expiresAt;
    private String destinationName;

    private transient Location resolved;

    private transient boolean placedAtLogin;

    public PendingTeleport(TeleportType type, String world, double x, double y, double z,
                           float yaw, float pitch, long expiresAt, String destinationName) {
        this.type = type;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.expiresAt = expiresAt;
        this.destinationName = destinationName;
    }

    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }
}

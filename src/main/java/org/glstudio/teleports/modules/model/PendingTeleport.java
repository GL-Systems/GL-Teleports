package org.glstudio.teleports.modules.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

@Data
@AllArgsConstructor
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

    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }
}

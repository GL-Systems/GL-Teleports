package org.glstudio.teleports.modules.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

@Data
@NoArgsConstructor
public class Warp implements TeleportDestination {
    private String name;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private String permission;
    private long createdAt;
    private String server;

    private Double cost;
    private Integer cooldown;
    private Integer delay;

    public Warp(String name, Location location, String permission, String server) {
        this.name = name;
        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
        this.permission = permission;
        this.createdAt = System.currentTimeMillis();
        this.server = server;
    }

    public Warp(String name, String world, double x, double y, double z, float yaw, float pitch,
                String permission, long createdAt, String server) {
        this(name, world, x, y, z, yaw, pitch, permission, createdAt, server, null, null, null);
    }

    public Warp(String name, String world, double x, double y, double z, float yaw, float pitch,
                String permission, long createdAt, String server,
                Double cost, Integer cooldown, Integer delay) {
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.permission = permission;
        this.createdAt = createdAt;
        this.server = server;
        this.cost = cost;
        this.cooldown = cooldown;
        this.delay = delay;
    }

    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }

    public boolean hasPermission() {
        return permission != null && !permission.isEmpty();
    }

    public boolean hasOverrides() {
        return cost != null || cooldown != null || delay != null;
    }

    public Warp movedTo(Location location, String server) {
        return new Warp(name, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(), permission, createdAt, server, cost, cooldown, delay);
    }
}

package org.glstudio.teleports.modules.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * A warp owned by a player.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerWarp implements TeleportDestination {
    private String id;
    private String name;
    private UUID owner;
    private String ownerName;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private String server;

    private String description;
    private String icon;
    private boolean useOwnerHead;
    private String categoryId;

    private Double cost;
    private Integer cooldown;
    private Integer delay;

    private boolean locked;
    private String passwordHash;
    private String passwordSalt;
    private boolean whitelistEnabled;

    private double ratingAverage;
    private int ratingCount;
    private int favouriteCount;

    private long createdAt;
    private long updatedAt;
    private long lastUsedAt;

    /**
     * Creates the player warp.
     */
    public PlayerWarp(String id, String name, UUID owner, String ownerName, Location location, String server) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.ownerName = ownerName;
        this.world = location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
        this.server = server;
        this.whitelistEnabled = false;
        this.locked = false;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.lastUsedAt = this.createdAt;
    }

    /**
     * Returns this as a location.
     */
    public Location toLocation() {
        World bukkitWorld = Bukkit.getWorld(world);
        return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
    }

    /**
     * Tells whether this warp has a password.
     */
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isEmpty();
    }

    /**
     * Tells whether entering is free.
     */
    public boolean isFree() {
        return cost == null || cost <= 0.0D;
    }

    /**
     * Tells whether a player owns this warp.
     */
    public boolean isOwner(UUID uuid) {
        return owner != null && owner.equals(uuid);
    }

    /**
     * Marks this as just changed.
     */
    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Marks the used.
     */
    public void markUsed() {
        this.lastUsedAt = System.currentTimeMillis();
    }
}

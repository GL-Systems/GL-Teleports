package org.glstudio.teleports.modules.model;

/**
 * Where a teleport leads.
 */
public interface TeleportDestination {
    /**
     * Returns the server.
     */
    String getServer();

    /**
     * Returns the world.
     */
    String getWorld();

    /**
     * Returns the x.
     */
    double getX();

    /**
     * Returns the y.
     */
    double getY();

    /**
     * Returns the z.
     */
    double getZ();

    /**
     * Returns the yaw.
     */
    float getYaw();

    /**
     * Returns the pitch.
     */
    float getPitch();
}

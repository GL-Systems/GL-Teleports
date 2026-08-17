package org.glstudio.teleports.modules.model;

public interface TeleportDestination {
    String getServer();

    String getWorld();

    double getX();

    double getY();

    double getZ();

    float getYaw();

    float getPitch();
}

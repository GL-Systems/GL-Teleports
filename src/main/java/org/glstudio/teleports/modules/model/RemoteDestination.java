package org.glstudio.teleports.modules.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Location;

@Data
@AllArgsConstructor
public class RemoteDestination implements TeleportDestination {

    private String server;
    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    public static RemoteDestination of(String server, Location location) {
        return new RemoteDestination(server, location.getWorld().getName(), location.getX(),
                location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }
}

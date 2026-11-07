package org.glstudio.teleports.modules.model;

import java.util.UUID;

/**
 * A teleport in the history.
 */
public record TeleportRecord(long id,
                             UUID player,
                             String playerName,
                             TeleportType type,
                             String destination,
                             String fromServer,
                             String fromWorld,
                             double fromX,
                             double fromY,
                             double fromZ,
                             String toServer,
                             String toWorld,
                             double toX,
                             double toY,
                             double toZ,
                             double cost,
                             long timestamp,
                             String result) {
    public static final String SUCCESS = "SUCCESS";

    /**
     * Creates a record.
     */
    public static TeleportRecord of(UUID player, String playerName, TeleportType type, String destination,
                                    String fromServer, String fromWorld, double fromX, double fromY, double fromZ,
                                    String toServer, String toWorld, double toX, double toY, double toZ,
                                    double cost, String result) {
        return new TeleportRecord(0L, player, playerName, type, destination,
                fromServer, fromWorld, fromX, fromY, fromZ,
                toServer, toWorld, toX, toY, toZ,
                cost, System.currentTimeMillis(), result);
    }

    /**
     * Tells whether it worked.
     */
    public boolean succeeded() {
        return SUCCESS.equals(result);
    }

    /**
     * Tells whether it crossed servers.
     */
    public boolean crossedServers() {
        return fromServer != null && toServer != null && !fromServer.equalsIgnoreCase(toServer);
    }
}

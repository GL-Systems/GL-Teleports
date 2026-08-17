package org.glstudio.teleports.api.event;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.glstudio.teleports.modules.model.TeleportType;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a player has been moved. Not cancellable: it has already happened.
 *
 * <p>For a cross-server teleport this fires <em>on the destination server</em>, once the player is
 * standing where they were sent — not on the origin when the request went out, which is a moment
 * at which the teleport can still fail.
 *
 * <p>Called on the main thread.
 */
@Getter
public class PostTeleportEvent extends TeleportEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    /**
     * Where the player came from; read with {@code getOrigin()}.
     *
     * <p>After a cross-server arrival this is where they materialised on this server, which is not
     * where they were when they typed the command — that was on another server entirely.
     */
    private final Location origin;

    /** Where they ended up; read with {@code getDestination()}. */
    private final Location destination;

    /** The warp or home name, the other player for a TPA, or {@code null} for spawn. */
    private final String destinationName;

    /** Whether the player arrived here from another server; read with {@code isCrossServer()}. */
    private final boolean crossServer;

    public PostTeleportEvent(Player player, TeleportType type, Location origin, Location destination,
                             String destinationName, boolean crossServer) {
        super(player, type);
        this.origin = origin;
        this.destination = destination;
        this.destinationName = destinationName;
        this.crossServer = crossServer;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

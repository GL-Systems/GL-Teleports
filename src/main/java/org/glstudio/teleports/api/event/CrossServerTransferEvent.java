package org.glstudio.teleports.api.event;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.glstudio.teleports.modules.model.TeleportType;
import org.jetbrains.annotations.NotNull;

/**
 * Fired on the origin server, immediately before the player is handed to the proxy.
 *
 * <p>The last point at which anything local can intervene: once the connect message goes out, the
 * player is the proxy's business and this server can no longer stop it.
 *
 * <p><b>Cancelling refunds the fee and lifts the cooldown</b>, both of which have already been
 * applied by this point — unlike {@link PreTeleportEvent}, where the cooldown is still pending.
 *
 * <p>Called on the main thread.
 */
@Getter
public class CrossServerTransferEvent extends TeleportEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    /** The network name of the server the player is being sent to. */
    private final String targetServer;

    /** The warp or home name driving the transfer, or {@code null}. */
    private final String destinationName;

    private boolean cancelled;

    /**
     * Shown to the player when a handler cancels; {@code null} says nothing.
     *
     * <p>Literal text, not a language.yml key. MiniMessage markup works.
     */
    @Setter
    private String cancelMessage;

    /**
     * Creates the event.
     *
     * @param player the player
     * @param type the type
     * @param targetServer the target server
     * @param destinationName the destination name
     */
    public CrossServerTransferEvent(Player player, TeleportType type, String targetServer, String destinationName) {
        super(player, type);
        this.targetServer = targetServer;
        this.destinationName = destinationName;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Returns the handler list of this event.
     *
     * @return the handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

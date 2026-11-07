package org.glstudio.teleports.api.event;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.glstudio.teleports.modules.model.TeleportType;
import org.jetbrains.annotations.NotNull;

/**
 * Fired immediately before a player is moved, once they have waited out any countdown and paid.
 *
 * <p>This is the hook for anything that needs to stop or redirect a teleport — a combat tagger, a
 * region protector, an event world that only admits certain players.
 *
 * <p><b>Cancelling refunds the fee.</b> The charge is taken before this event, so a cancelled
 * teleport would otherwise leave the player poorer for nothing. The cooldown needs no undoing: it
 * is applied after this point, so a teleport stopped here never consumes one.
 *
 * <p>{@link #setDestination(Location)} redirects instead of refusing. The replacement is put
 * through the same safety check as the original, so a handler cannot drop somebody into the void
 * by accident.
 *
 * <pre>{@code
 * @EventHandler(ignoreCancelled = true)
 * public void onPreTeleport(PreTeleportEvent event) {
 *     if (combat.isTagged(event.getPlayer())) {
 *         event.setCancelled(true);
 *         event.setCancelMessage("<red>Not while in combat.</red>");
 *     }
 * }
 * }</pre>
 *
 * <p>Called on the main thread.
 *
 * @see PostTeleportEvent for reacting once the move has happened
 */
@Getter
public class PreTeleportEvent extends TeleportEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    /** Where the player is standing now; read with {@code getOrigin()}. */
    private final Location origin;

    /** Where they are about to go. Read with {@code getDestination()}, replace with the setter. */
    @Setter
    private Location destination;

    /**
     * The warp or home name, the other player for a TPA, or {@code null} for spawn and for a
     * teleport to bare coordinates. Read with {@code getDestinationName()}.
     */
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
     * @param origin the origin
     * @param destination the destination
     * @param destinationName the destination name
     */
    public PreTeleportEvent(Player player, TeleportType type, Location origin, Location destination,
                            String destinationName) {
        super(player, type);
        this.origin = origin;
        this.destination = destination;
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

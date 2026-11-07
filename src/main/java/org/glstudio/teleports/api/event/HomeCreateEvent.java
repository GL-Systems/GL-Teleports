package org.glstudio.teleports.api.event;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a home is written, whether it is new or replacing one that exists.
 *
 * <p>Fires only once the write is actually going to happen: home limits and the overwrite
 * confirmation are checked first, so a handler never sees an attempt that was going to be refused
 * anyway. Cancelling stops the write.
 *
 * <p>Useful for restrictions the world blacklist cannot express — keeping homes out of another
 * player's claim, for instance, which is a per-location rule rather than a per-world one.
 *
 * <p>Called on the main thread.
 */
@Getter
public class HomeCreateEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    /** The home's name, already normalised to lower case. */
    private final String name;

    /** Where the home will point. */
    private final Location location;

    /** Whether a home by this name already existed; read with {@code isOverwrite()}. */
    private final boolean overwrite;

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
     * @param name the name
     * @param location the location
     * @param overwrite the overwrite
     */
    public HomeCreateEvent(Player player, String name, Location location, boolean overwrite) {
        super(player);
        this.name = name;
        this.location = location;
        this.overwrite = overwrite;
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

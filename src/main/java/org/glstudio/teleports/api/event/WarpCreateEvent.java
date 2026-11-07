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
 * Fired before a warp is written, whether new or replacing one that exists. Cancelling stops it.
 *
 * <p>Called on the main thread.
 */
@Getter
public class WarpCreateEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    /** The warp's name, already normalised to lower case. */
    private final String name;

    /** Where the warp will point. */
    private final Location location;

    /** Whether a warp by this name already existed; read with {@code isOverwrite()}. */
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
    public WarpCreateEvent(Player player, String name, Location location, boolean overwrite) {
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

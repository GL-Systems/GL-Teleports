package org.glstudio.teleports.api.event;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.glstudio.teleports.modules.model.Home;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a home is removed. Cancelling keeps it.
 *
 * <p>Called on the main thread.
 */
@Getter
public class HomeDeleteEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    /**
     * The home about to go, in full.
     *
     * <p>The whole object rather than just its name, so a handler can keep a copy of the
     * coordinates before they are gone.
     */
    private final Home home;

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
     * @param home the home
     */
    public HomeDeleteEvent(Player player, Home home) {
        super(player);
        this.home = home;
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

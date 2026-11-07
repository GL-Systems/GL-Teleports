package org.glstudio.teleports.api.event;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.glstudio.teleports.modules.model.Warp;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a warp is removed. Cancelling keeps it.
 *
 * <p>The counterpart to {@link HomeDeleteEvent}, which existed first because homes were the only
 * thing the plugin let a player destroy. Warps are now removable through the API too, so the same
 * chance to object applies to them.
 *
 * <p>Called on the main thread.
 */
@Getter
public class WarpDeleteEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    /**
     * The warp about to go, in full.
     *
     * <p>The whole object rather than just its name, so a handler can keep a copy of its
     * coordinates, permission and per-warp overrides before they are gone.
     */
    private final Warp warp;

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
     * @param warp the warp
     */
    public WarpDeleteEvent(Player player, Warp warp) {
        super(player);
        this.warp = warp;
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

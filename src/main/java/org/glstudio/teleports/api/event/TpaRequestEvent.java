package org.glstudio.teleports.api.event;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.glstudio.teleports.modules.model.TpaRequest;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired when one player asks to teleport to another, before the target is told about it.
 *
 * <p>Cancelling stops the request reaching them.
 *
 * <p>The target is identified by uuid and name rather than by {@code Player}, because on a network
 * they are frequently on another server and no {@code Player} object for them exists here.
 *
 * <p>Called on the main thread, on the requester's server.
 */
@Getter
public class TpaRequestEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    /** Who the request is for. */
    private final UUID targetId;

    /** Their name as known when the request was made. */
    private final String targetName;

    /**
     * Which direction the teleport would go.
     *
     * <p>{@code TPA} moves the requester to the target; {@code TPAHERE} moves the target to the
     * requester. Read with {@code getRequestType()}.
     */
    private final TpaRequest.TpaType requestType;

    private boolean cancelled;

    /**
     * Shown to the player when a handler cancels; {@code null} says nothing.
     *
     * <p>Literal text, not a language.yml key. MiniMessage markup works.
     */
    @Setter
    private String cancelMessage;

    public TpaRequestEvent(Player requester, UUID targetId, String targetName,
                           TpaRequest.TpaType requestType) {
        super(requester);
        this.targetId = targetId;
        this.targetName = targetName;
        this.requestType = requestType;
    }

    /**
     * The player who sent the request.
     *
     * @return the same object as {@link #getPlayer()}; named for readability at the call site
     */
    public Player getRequester() {
        return getPlayer();
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

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

package org.glstudio.teleports.api.event;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.glstudio.teleports.modules.model.TeleportType;

/**
 * Base class for the teleport events GL-Teleports fires.
 *
 * <p>Every event in this package is called on the main thread, so a handler may touch the Bukkit
 * API without checking first.
 *
 * @see PreTeleportEvent
 * @see PostTeleportEvent
 * @see CrossServerTransferEvent
 */
@Getter
public abstract class TeleportEvent extends PlayerEvent {

    /** Which kind of teleport this is; read it with {@code getType()}. Never {@code null}. */
    private final TeleportType type;

    /**
     * Creates the event.
     *
     * @param player the player
     * @param type the type
     */
    protected TeleportEvent(Player player, TeleportType type) {
        super(player);
        this.type = type;
    }
}

package org.glstudio.teleports.api;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.glstudio.nexus.utils.EventUtils;
import org.glstudio.nexus.utils.TextFormat;

/**
 * Fires the plugin's own events and reports what a handler decided.
 *
 * <p>Internal. Other plugins listen for the events in {@code org.glstudio.teleports.api.event};
 * they do not fire them.
 *
 * <p>Two things are centralised here. A handler that throws must not take the operation down with
 * it — a broken third-party plugin should cost its own feature, not everybody's teleports — so
 * every call is wrapped. And a handler that cancels may leave a message, which is delivered here
 * rather than at each of the dozen call sites.
 */
public final class Events {

    private Events() {
    }

    /**
     * Fires an event.
     *
     * @param event the event
     * @return {@code true} if so
     */
    public static boolean call(Event event) {
        return EventUtils.call(event);
    }

    /**
     * Fires an event and tells the sender when it was cancelled.
     *
     * @param event the event
     * @param sender the sender
     * @param cancelMessage the cancel message
     * @return {@code true} if so
     */
    public static boolean callAndExplain(Event event, CommandSender sender, String cancelMessage) {
        return EventUtils.callAndExplain(event, sender, cancelMessage, TextFormat.MINIMESSAGE);
    }
}

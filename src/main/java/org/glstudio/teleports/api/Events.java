package org.glstudio.teleports.api;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.glstudio.nexus.utils.CC;
import org.glstudio.nexus.utils.LoggerUtils;

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
     * Fires an event and reports whether it survived.
     *
     * <p>An exception counts as "not cancelled": a handler that crashed expressed no opinion, and
     * treating that as a veto would let one broken plugin block every teleport on the server.
     *
     * @param event the event to fire
     * @return {@code true} if the operation may proceed
     */
    public static boolean call(Event event) {
        try {
            Bukkit.getPluginManager().callEvent(event);
        } catch (Exception e) {
            LoggerUtils.logException("A handler of " + event.getEventName() + " threw", e);
            return true;
        }

        return !(event instanceof Cancellable cancellable) || !cancellable.isCancelled();
    }

    /**
     * Fires an event, and tells {@code sender} why if a handler cancelled it and left a message.
     *
     * <p>{@code cancelMessage} is literal text written by the handler, not a language.yml key, so
     * it is rendered directly. MiniMessage markup works; a plain sentence works too.
     *
     * @param event         the event to fire
     * @param sender        who to tell on cancellation; {@code null} to tell nobody
     * @param cancelMessage the handler's message, usually {@code event.getCancelMessage()}
     * @return {@code true} if the operation may proceed
     */
    public static boolean callAndExplain(Event event, CommandSender sender, String cancelMessage) {
        if (call(event)) {
            return true;
        }

        if (sender != null && cancelMessage != null && !cancelMessage.isBlank()) {
            sender.sendMessage(CC.miniMessage(cancelMessage));
        }

        return false;
    }
}

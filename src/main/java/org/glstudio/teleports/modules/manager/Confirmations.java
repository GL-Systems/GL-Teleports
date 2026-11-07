package org.glstudio.teleports.modules.manager;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers who has been asked to confirm a dangerous action.
 */
public class Confirmations {
    private static final long WINDOW_MILLIS = 15_000L;

    private final Map<String, Long> pending = new ConcurrentHashMap<>();

    /**
     * Tells whether a player has confirmed an action.
     */
    public boolean confirmed(Player player, String action) {
        String key = key(player.getUniqueId(), action);
        Long asked = pending.remove(key);

        if (asked != null && System.currentTimeMillis() - asked <= WINDOW_MILLIS) {
            return true;
        }

        purgeExpired();
        pending.put(key, System.currentTimeMillis());
        return false;
    }

    /**
     * Forgets what is held for a player.
     */
    public void forget(UUID uuid) {
        String prefix = uuid + ":";
        pending.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private void purgeExpired() {
        long cutoff = System.currentTimeMillis() - WINDOW_MILLIS;
        pending.values().removeIf(asked -> asked < cutoff);
    }

    private static String key(UUID uuid, String action) {
        return uuid + ":" + action;
    }
}

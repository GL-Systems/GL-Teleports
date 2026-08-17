package org.glstudio.teleports.core;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Confirmations {
    private static final long WINDOW_MILLIS = 15_000L;

    private final Map<String, Long> pending = new ConcurrentHashMap<>();

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

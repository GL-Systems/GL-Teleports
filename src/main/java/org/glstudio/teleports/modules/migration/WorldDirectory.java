package org.glstudio.teleports.modules.migration;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The worlds loaded on this server, captured on the main thread before a migration starts so the readers never
 * have to touch the Bukkit API from a worker thread.
 */
public final class WorldDirectory {
    private final Map<UUID, String> namesById;
    private final Set<String> lowerCaseNames;

    /**
     * Creates the world directory.
     */
    public WorldDirectory(Map<UUID, String> namesById) {
        this.namesById = Map.copyOf(namesById);
        this.lowerCaseNames = namesById.values().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Returns an empty one.
     */
    public static WorldDirectory empty() {
        return new WorldDirectory(Map.of());
    }

    /**
     * Finds a world's name.
     */
    public String nameOf(UUID id) {
        return namesById.get(id);
    }

    /**
     * Tells whether a world is loaded.
     */
    public boolean isLoaded(String worldName) {
        return worldName != null && lowerCaseNames.contains(worldName.toLowerCase(Locale.ROOT));
    }
}

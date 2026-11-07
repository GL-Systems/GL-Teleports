package org.glstudio.teleports.modules.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.PlayerWarpCategory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the categories of player warps.
 */
public class PlayerWarpCategoryRegistry {
    private final GTeleports plugin;
    private volatile Map<String, PlayerWarpCategory> categories = Map.of();

    /**
     * Creates the registry.
     */
    public PlayerWarpCategoryRegistry(GTeleports plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * Loads the data.
     */
    public void load() {
        ConfigFile config = plugin.getConfigManager().getPlayerWarpsConfig();
        ConfigurationSection section = config.getConfigurationSection("categories");

        Map<String, PlayerWarpCategory> loaded = new ConcurrentHashMap<>();

        if (section != null) {
            for (String id : section.getKeys(false)) {
                String path = id + ".";
                loaded.put(id.toLowerCase(Locale.ROOT), new PlayerWarpCategory(
                        id.toLowerCase(Locale.ROOT),
                        section.getString(path + "display-name", id),
                        section.getString(path + "icon", "PAPER"),
                        section.getInt(path + "slot", 0),
                        section.getString(path + "description", "")));
            }
        }

        this.categories = loaded;
    }

    /**
     * Finds a category.
     */
    public PlayerWarpCategory get(String id) {
        return id == null ? null : categories.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * Tells whether a category exists.
     */
    public boolean exists(String id) {
        return get(id) != null;
    }

    /**
     * Returns every category.
     */
    public List<PlayerWarpCategory> all() {
        List<PlayerWarpCategory> list = new ArrayList<>(categories.values());
        list.sort(Comparator.comparingInt(PlayerWarpCategory::slot));
        return list;
    }
}

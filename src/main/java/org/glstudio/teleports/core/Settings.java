package org.glstudio.teleports.core;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.GTeleports;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Settings {
    private final GTeleports plugin;

    private volatile FileConfiguration bundled;

    public Settings(GTeleports plugin) {
        this.plugin = plugin;
    }

    public int getInt(String path, int fallback) {
        ConfigFile config = main();

        if (config.contains(path)) {
            return config.getInt(path, fallback);
        }

        FileConfiguration defaults = bundled();
        return defaults != null && defaults.contains(path) ? defaults.getInt(path) : fallback;
    }

    public boolean getBoolean(String path, boolean fallback) {
        ConfigFile config = main();

        if (config.contains(path)) {
            return config.getBoolean(path, fallback);
        }

        FileConfiguration defaults = bundled();
        return defaults != null && defaults.contains(path) ? defaults.getBoolean(path) : fallback;
    }

    public double getDouble(String path, double fallback) {
        ConfigFile config = main();

        if (config.contains(path)) {
            return config.getDouble(path, fallback);
        }

        FileConfiguration defaults = bundled();
        return defaults != null && defaults.contains(path) ? defaults.getDouble(path) : fallback;
    }

    public String getString(String path, String fallback) {
        ConfigFile config = main();

        if (config.contains(path)) {
            return config.getString(path, fallback);
        }

        FileConfiguration defaults = bundled();
        return defaults != null && defaults.contains(path) ? defaults.getString(path) : fallback;
    }

    public List<String> getStringList(String path) {
        ConfigFile config = main();

        if (config.contains(path)) {
            return config.getStringList(path);
        }

        FileConfiguration defaults = bundled();
        return defaults != null && defaults.contains(path) ? defaults.getStringList(path) : List.of();
    }

    public boolean isSet(String path) {
        FileConfiguration defaults = bundled();
        return main().contains(path) || (defaults != null && defaults.contains(path));
    }

    public void reset() {
        bundled = null;
    }

    private ConfigFile main() {
        return plugin.getConfigManager().getMainConfig();
    }

    private FileConfiguration bundled() {
        FileConfiguration cached = bundled;
        if (cached != null) {
            return cached;
        }

        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream == null) {
                return null;
            }

            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                FileConfiguration loaded = YamlConfiguration.loadConfiguration(reader);
                bundled = loaded;
                return loaded;
            }
        } catch (Exception e) {
            return null;
        }
    }
}

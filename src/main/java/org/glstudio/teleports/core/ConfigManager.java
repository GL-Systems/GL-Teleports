package org.glstudio.teleports.core;

import lombok.Getter;
import org.glstudio.nexus.utils.ConfigFile;
import org.glstudio.teleports.GTeleports;

@Getter
public class ConfigManager {

    private final ConfigFile mainConfig, languageConfig, databaseConfig;

    public ConfigManager(GTeleports plugin) {
        this.mainConfig = new ConfigFile(plugin, "config.yml");
        this.languageConfig = new ConfigFile(plugin, "language.yml");
        this.databaseConfig = new ConfigFile(plugin, "database.yml");
    }

    public void reload() {
        mainConfig.reload();
        languageConfig.reload();
        databaseConfig.reload();
    }
}

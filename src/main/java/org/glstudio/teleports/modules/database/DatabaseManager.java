package org.glstudio.teleports.modules.database;

import lombok.Getter;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.database.impl.*;
import org.glstudio.nexus.utils.LoggerUtils;

public class DatabaseManager {

    private final GTeleports plugin;
    @Getter
    private DatabaseProvider provider;

    public DatabaseManager(GTeleports plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        DatabaseType type = DatabaseType.fromString(
                plugin.getConfigManager().getDatabaseConfig().getString("type", "yaml")
        );

        LoggerUtils.logInfo("Initializing database with type: " + type.getName());

        switch (type) {
            case YAML -> provider = new YamlProvider(plugin);
            case SQLITE -> provider = new SQLiteProvider(plugin);
            case H2 -> provider = new H2Provider(plugin);
            case MYSQL -> provider = new MySQLProvider(plugin);
            case MONGO -> provider = new MongoProvider(plugin);
        }

        provider.initialize().join();
        LoggerUtils.logSuccess("Database initialized successfully!");
    }

    public void shutdown() {
        if (provider != null) {
            LoggerUtils.logInfo("Shutting down database...");
            provider.shutdown().join();
            LoggerUtils.logSuccess("Database shut down successfully!");
        }
    }
}

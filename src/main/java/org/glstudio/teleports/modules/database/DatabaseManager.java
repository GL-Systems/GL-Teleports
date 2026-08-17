package org.glstudio.teleports.modules.database;

import lombok.Getter;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.database.impl.*;
import org.glstudio.nexus.utils.LoggerUtils;

import java.util.concurrent.TimeUnit;

public class DatabaseManager {
    private static final long STARTUP_TIMEOUT_SECONDS = 30L;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10L;

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

        provider.initialize().orTimeout(STARTUP_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
        LoggerUtils.logSuccess("Database initialized successfully!");
    }

    public void shutdown() {
        if (provider != null) {
            LoggerUtils.logInfo("Shutting down database...");
            try {
                provider.shutdown().get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LoggerUtils.logWarn("The database did not close cleanly: " + e.getMessage());
            }
            LoggerUtils.logSuccess("Database shut down successfully!");
        }
    }
}

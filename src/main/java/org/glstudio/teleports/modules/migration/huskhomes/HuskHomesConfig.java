package org.glstudio.teleports.modules.migration.huskhomes;

import org.glstudio.teleports.modules.migration.MigrationException;
import org.glstudio.teleports.modules.migration.source.YamlValues;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The parts of HuskHomes' own config.yml and server.yml the migration needs.
 *
 * Two layouts exist. 4.8 and later keep credentials under database.credentials and key table_names by the
 * upper-case table id; 3.x up to 4.7 used database.mysql.credentials and lower-case ids. Both are read.
 */
public final class HuskHomesConfig {
    // Table names end up inside SQL text, so anything beyond a plain identifier is refused outright.
    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z0-9_]{1,64}");

    public enum DatabaseType {
        SQLITE, H2, MYSQL, MARIADB, POSTGRESQL
    }

    public enum Table {
        PLAYER_DATA("huskhomes_users"),
        POSITION_DATA("huskhomes_position_data"),
        SAVED_POSITION_DATA("huskhomes_saved_positions"),
        HOME_DATA("huskhomes_homes"),
        WARP_DATA("huskhomes_warps");

        private final String defaultName;

        Table(String defaultName) {
            this.defaultName = defaultName;
        }
    }

    private final DatabaseType type;
    private final Credentials credentials;
    private final Map<Table, String> tables;
    private final boolean crossServer;
    private final boolean restrictedWarps;
    private final String serverName;

    private HuskHomesConfig(DatabaseType type, Credentials credentials, Map<Table, String> tables,
                            boolean crossServer, boolean restrictedWarps, String serverName) {
        this.type = type;
        this.credentials = credentials;
        this.tables = tables;
        this.crossServer = crossServer;
        this.restrictedWarps = restrictedWarps;
        this.serverName = serverName;
    }

    public static HuskHomesConfig load(Path folder) throws MigrationException {
        Path configFile = folder.resolve("config.yml");

        if (!Files.isRegularFile(configFile)) {
            throw new MigrationException("migrate.errors.not-found", "no config.yml in " + folder,
                    "{path}", folder.toString());
        }

        Map<?, ?> config = read(configFile, "config.yml");

        String typeName = YamlValues.string(YamlValues.path(config, "database.type"));
        DatabaseType type;
        try {
            type = typeName == null ? DatabaseType.SQLITE
                    : DatabaseType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new MigrationException("migrate.errors.unsupported-database",
                    "HuskHomes database.type is '" + typeName + "'", "{type}", typeName);
        }

        Map<?, ?> credentialsSection = YamlValues.section(config, "database.credentials");
        if (credentialsSection == null) {
            credentialsSection = YamlValues.section(config, "database.mysql.credentials");
        }

        Credentials credentials = Credentials.from(credentialsSection == null ? Map.of() : credentialsSection);
        Map<Table, String> tables = tableNames(YamlValues.section(config, "database.table_names"));

        boolean crossServer = YamlValues.bool(YamlValues.path(config, "cross_server.enabled"), false);
        boolean restrictedWarps = YamlValues.bool(YamlValues.path(config, "general.permission_restrict_warps"),
                false);

        String serverName = null;
        Path serverFile = folder.resolve("server.yml");
        if (Files.isRegularFile(serverFile)) {
            serverName = YamlValues.string(YamlValues.get(read(serverFile, "server.yml"), "name"));
        }

        return new HuskHomesConfig(type, credentials, tables, crossServer, restrictedWarps, serverName);
    }

    private static Map<?, ?> read(Path file, String label) throws MigrationException {
        try {
            return YamlValues.load(file);
        } catch (IOException | YAMLException e) {
            throw new MigrationException("migrate.errors.read-failed", "HuskHomes " + label + " is not valid YAML",
                    e, "{error}", "HuskHomes " + label + ": " + e.getClass().getSimpleName());
        }
    }

    private static Map<Table, String> tableNames(Map<?, ?> section) throws MigrationException {
        Map<Table, String> names = new EnumMap<>(Table.class);

        for (Table table : Table.values()) {
            String configured = null;

            if (section != null) {
                for (Map.Entry<?, ?> entry : section.entrySet()) {
                    if (String.valueOf(entry.getKey()).equalsIgnoreCase(table.name())) {
                        configured = YamlValues.string(entry.getValue());
                    }
                }
            }

            String name = configured == null ? table.defaultName : configured;
            if (!TABLE_NAME.matcher(name).matches()) {
                throw new MigrationException("migrate.errors.read-failed",
                        "HuskHomes table name for " + table + " is not a plain identifier: " + name,
                        "{error}", "table name '" + name + "' is not supported");
            }
            names.put(table, name);
        }

        return names;
    }

    public DatabaseType type() {
        return type;
    }

    public Credentials credentials() {
        return credentials;
    }

    public String table(Table table) {
        return tables.get(table);
    }

    /** Whether server_name in HuskHomes' rows is a real proxy server name rather than a folder-name default. */
    public boolean crossServer() {
        return crossServer;
    }

    public boolean restrictedWarps() {
        return restrictedWarps;
    }

    public String serverName() {
        return serverName;
    }

    /**
     * Connection details for a database server. {@link #toString()} leaves the password and user out, so an
     * instance is safe to log by accident.
     */
    public record Credentials(String host, int port, String database, String username, String password,
                              String parameters) {
        static Credentials from(Map<?, ?> section) {
            String host = YamlValues.string(YamlValues.get(section, "host"));
            double port = YamlValues.number(YamlValues.get(section, "port"));
            String database = YamlValues.string(YamlValues.get(section, "database"));
            String username = YamlValues.string(YamlValues.get(section, "username"));
            Object password = YamlValues.get(section, "password");
            String parameters = YamlValues.string(YamlValues.get(section, "parameters"));

            return new Credentials(host == null ? "localhost" : host,
                    Double.isNaN(port) ? 3306 : (int) port,
                    database == null ? "huskhomes" : database,
                    username == null ? "root" : username,
                    password == null ? "" : String.valueOf(password),
                    parameters == null ? "" : parameters);
        }

        @Override
        public String toString() {
            return host + ":" + port + "/" + database;
        }
    }
}

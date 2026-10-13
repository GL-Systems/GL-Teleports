package org.glstudio.teleports.modules.migration.playerwarps;

import org.glstudio.teleports.modules.migration.MigrationException;
import org.glstudio.teleports.modules.migration.source.YamlValues;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The parts of PlayerWarps' own config.yml the migration needs: where its database is and how its tables are
 * named. The connection details are the ones PlayerWarps itself uses, so nothing has to be configured twice.
 *
 * <pre>
 * settings:
 *   database:
 *     table-prefix: playerwarps_
 *     mysql:
 *       enabled: true          # false: SQLite in data/database.db
 *       server: mysql          # mysql, mariadb or postgresql
 *       hostname, port, database, username, password, arguments
 *       server-name: Survival  # this server's name on the proxy; rows record it in warps.server
 * </pre>
 */
public final class PlayerWarpsConfig {
    // The prefix ends up inside SQL text, so anything beyond a plain identifier is refused outright.
    private static final Pattern TABLE_PREFIX = Pattern.compile("[A-Za-z0-9_]{0,48}");

    public enum DatabaseType {
        SQLITE, MYSQL, MARIADB, POSTGRESQL
    }

    private final DatabaseType type;
    private final Credentials credentials;
    private final String tablePrefix;
    private final String serverName;

    private PlayerWarpsConfig(DatabaseType type, Credentials credentials, String tablePrefix, String serverName) {
        this.type = type;
        this.credentials = credentials;
        this.tablePrefix = tablePrefix;
        this.serverName = serverName;
    }

    public static PlayerWarpsConfig load(Path folder) throws MigrationException {
        Path configFile = folder.resolve("config.yml");

        if (!Files.isRegularFile(configFile)) {
            throw new MigrationException("migrate.errors.not-found", "no config.yml in " + folder,
                    "{path}", folder.toString());
        }

        Map<?, ?> config;
        try {
            config = YamlValues.load(configFile);
        } catch (IOException | YAMLException e) {
            throw new MigrationException("migrate.errors.read-failed", "PlayerWarps config.yml is not valid YAML",
                    e, "{error}", "PlayerWarps config.yml: " + e.getClass().getSimpleName());
        }

        String prefix = YamlValues.string(YamlValues.path(config, "settings.database.table-prefix"));
        prefix = prefix == null ? "playerwarps_" : prefix;
        if (!TABLE_PREFIX.matcher(prefix).matches()) {
            throw new MigrationException("migrate.errors.read-failed",
                    "PlayerWarps table-prefix is not a plain identifier: " + prefix,
                    "{error}", "table prefix '" + prefix + "' is not supported");
        }

        Map<?, ?> mysql = YamlValues.section(config, "settings.database.mysql");
        boolean remote = mysql != null && YamlValues.bool(YamlValues.get(mysql, "enabled"), false);

        if (!remote) {
            return new PlayerWarpsConfig(DatabaseType.SQLITE, null, prefix, null);
        }

        String server = YamlValues.string(YamlValues.get(mysql, "server"));
        DatabaseType type;
        try {
            type = server == null ? DatabaseType.MYSQL : DatabaseType.valueOf(server.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            type = null;
        }

        if (type == null || type == DatabaseType.SQLITE) {
            throw new MigrationException("migrate.errors.unsupported-database",
                    "PlayerWarps settings.database.mysql.server is '" + server + "'", "{type}", server);
        }

        return new PlayerWarpsConfig(type, Credentials.from(mysql), prefix,
                YamlValues.string(YamlValues.get(mysql, "server-name")));
    }

    public DatabaseType type() {
        return type;
    }

    public Credentials credentials() {
        return credentials;
    }

    public String table(String name) {
        return tablePrefix + name;
    }

    public String tablePrefix() {
        return tablePrefix;
    }

    /**
     * The proxy name of the server PlayerWarps ran on; only recorded, and only meaningful, in its MySQL mode.
     */
    public String serverName() {
        return serverName;
    }

    /**
     * {@link #toString()} leaves the password and user out, so an instance is safe to log by accident.
     *
     * @param arguments appended to the JDBC URL as PlayerWarps does, e.g. "?useSSL=false"
     */
    public record Credentials(String host, int port, String database, String username, String password,
                              String arguments) {
        static Credentials from(Map<?, ?> section) {
            String host = YamlValues.string(YamlValues.get(section, "hostname"));
            double port = YamlValues.number(YamlValues.get(section, "port"));
            String database = YamlValues.string(YamlValues.get(section, "database"));
            String username = YamlValues.string(YamlValues.get(section, "username"));
            Object password = YamlValues.get(section, "password");
            String arguments = YamlValues.string(YamlValues.get(section, "arguments"));

            return new Credentials(host == null ? "localhost" : host,
                    Double.isNaN(port) ? 3306 : (int) port,
                    database == null ? "playerwarps" : database,
                    username == null ? "root" : username,
                    password == null ? "" : String.valueOf(password),
                    arguments == null ? "" : arguments);
        }

        @Override
        public String toString() {
            return host + ":" + port + "/" + database;
        }
    }
}

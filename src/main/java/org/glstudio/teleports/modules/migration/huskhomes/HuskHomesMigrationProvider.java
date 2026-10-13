package org.glstudio.teleports.modules.migration.huskhomes;

import org.glstudio.teleports.modules.migration.MigrationException;
import org.glstudio.teleports.modules.migration.MigrationProvider;
import org.glstudio.teleports.modules.migration.WorldDirectory;
import org.glstudio.teleports.modules.migration.huskhomes.HuskHomesConfig.Table;
import org.glstudio.teleports.modules.migration.source.MigrationData;
import org.glstudio.teleports.modules.migration.source.SourceHome;
import org.glstudio.teleports.modules.migration.source.SourceLocation;
import org.glstudio.teleports.modules.migration.source.SourceProblem;
import org.glstudio.teleports.modules.migration.source.SourceWarp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

/**
 * Reads HuskHomes 3.x and 4.x homes and warps straight from its database, whichever backend it uses.
 *
 * <pre>
 * huskhomes_homes            uuid, saved_position_id -> saved_positions.id, owner_uuid -> users.uuid, public
 * huskhomes_warps            uuid, saved_position_id -> saved_positions.id
 * huskhomes_saved_positions  id, position_id -> position_data.id, name, description, tags, timestamp
 * huskhomes_position_data    id, x, y, z, yaw, pitch, world_name, world_uuid, server_name
 * huskhomes_users            uuid, username, ...
 * </pre>
 *
 * The layout has been the same on every backend since 3.0; table names come from HuskHomes' config.
 * Connections are opened read-only and only SELECTs are issued, so HuskHomes' data cannot be changed.
 */
public class HuskHomesMigrationProvider implements MigrationProvider {
    static final String SQLITE_FILE = "HuskHomesData.db";
    static final String H2_FILE = "HuskHomesData.h2";

    private static final String WARP_PERMISSION_PREFIX = "huskhomes.warp.";
    private static final int CONNECT_TIMEOUT_SECONDS = 15;

    @Override
    public String id() {
        return "huskhomes";
    }

    @Override
    public List<String> aliases() {
        return List.of("husk");
    }

    @Override
    public String displayName() {
        return "HuskHomes";
    }

    @Override
    public String defaultFolder() {
        return "HuskHomes";
    }

    @Override
    public MigrationData read(Path folder, WorldDirectory worlds) throws MigrationException {
        if (!Files.isDirectory(folder)) {
            throw new MigrationException("migrate.errors.not-found", "no HuskHomes folder at " + folder,
                    "{path}", folder.toString());
        }

        HuskHomesConfig config = HuskHomesConfig.load(folder);
        String storage = describe(config, folder);

        try (Connection connection = open(config, folder)) {
            return read(connection, config, storage, localServerName(config));
        } catch (SQLException e) {
            throw new MigrationException("migrate.errors.read-failed", "could not query HuskHomes (" + storage + ")",
                    e, "{error}", sanitize(e, config));
        }
    }

    /**
     * When cross-server is off HuskHomes still fills server_name - with server.yml's name or, failing that, the
     * server folder's name - but it is not a proxy name, so it is not passed on as one.
     */
    private static String localServerName(HuskHomesConfig config) {
        if (!config.crossServer()) {
            return null;
        }

        if (config.serverName() != null) {
            return config.serverName();
        }

        // The same default HuskHomes picks when server.yml has no name.
        Path workingDirectory = Path.of(System.getProperty("user.dir", "server")).getFileName();
        return workingDirectory == null ? "server" : workingDirectory.toString().trim();
    }

    static MigrationData read(Connection connection, HuskHomesConfig config, String storage, String localServer)
            throws SQLException {
        String q = config.type() == HuskHomesConfig.DatabaseType.POSTGRESQL ? "\"" : "`";

        List<SourceHome> homes = new ArrayList<>();
        List<SourceWarp> warps = new ArrayList<>();
        List<SourceProblem> problems = new ArrayList<>();

        String homesQuery = "SELECT h." + q + "uuid" + q + " AS entry_uuid, h." + q + "owner_uuid" + q
                + " AS owner_uuid, h." + q + "public" + q + " AS is_public, u." + q + "username" + q + " AS username, "
                + positionColumns(q)
                + " FROM " + q + config.table(Table.HOME_DATA) + q + " h"
                + positionJoins(q, config, "h")
                + " LEFT JOIN " + q + config.table(Table.PLAYER_DATA) + q + " u ON h." + q + "owner_uuid" + q
                + " = u." + q + "uuid" + q
                + " ORDER BY h." + q + "owner_uuid" + q + ", s." + q + "name" + q;

        try (PreparedStatement statement = connection.prepareStatement(homesQuery);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String origin = config.table(Table.HOME_DATA) + " " + rows.getString("entry_uuid");
                UUID owner = parseUuid(rows.getString("owner_uuid"));

                if (owner == null) {
                    problems.add(new SourceProblem(origin, "owner_uuid '" + rows.getString("owner_uuid")
                            + "' is not a UUID"));
                    continue;
                }

                String reason = brokenPosition(rows);
                if (reason != null) {
                    problems.add(new SourceProblem(origin, reason));
                    continue;
                }

                homes.add(new SourceHome(owner, rows.getString("username"), rows.getString("entry_name"),
                        location(rows, config), timestamp(rows), rows.getBoolean("is_public"), origin));
            }
        }

        String warpsQuery = "SELECT w." + q + "uuid" + q + " AS entry_uuid, " + positionColumns(q)
                + " FROM " + q + config.table(Table.WARP_DATA) + q + " w"
                + positionJoins(q, config, "w")
                + " ORDER BY s." + q + "name" + q;

        try (PreparedStatement statement = connection.prepareStatement(warpsQuery);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String origin = config.table(Table.WARP_DATA) + " " + rows.getString("entry_uuid");

                String reason = brokenPosition(rows);
                if (reason != null) {
                    problems.add(new SourceProblem(origin, reason));
                    continue;
                }

                String name = rows.getString("entry_name");
                String permission = config.restrictedWarps()
                        ? WARP_PERMISSION_PREFIX + name.toLowerCase(Locale.ROOT)
                        : null;

                warps.add(new SourceWarp(name, location(rows, config), permission, timestamp(rows), origin));
            }
        }

        return new MigrationData(storage, localServer, homes, warps, problems);
    }

    private static String positionColumns(String q) {
        return "s." + q + "name" + q + " AS entry_name, s." + q + "timestamp" + q + " AS created_at, "
                + "p." + q + "x" + q + " AS x, p." + q + "y" + q + " AS y, p." + q + "z" + q + " AS z, "
                + "p." + q + "yaw" + q + " AS yaw, p." + q + "pitch" + q + " AS pitch, "
                + "p." + q + "world_name" + q + " AS world_name, p." + q + "server_name" + q + " AS server_name";
    }

    // LEFT joins so a row whose saved position or position has gone missing is reported instead of
    // silently disappearing from the counts.
    private static String positionJoins(String q, HuskHomesConfig config, String alias) {
        return " LEFT JOIN " + q + config.table(Table.SAVED_POSITION_DATA) + q + " s ON " + alias + "."
                + q + "saved_position_id" + q + " = s." + q + "id" + q
                + " LEFT JOIN " + q + config.table(Table.POSITION_DATA) + q + " p ON s." + q + "position_id" + q
                + " = p." + q + "id" + q;
    }

    private static String brokenPosition(ResultSet rows) throws SQLException {
        if (rows.getString("entry_name") == null) {
            return "its saved position is missing";
        }

        if (rows.getObject("x") == null || rows.getObject("y") == null || rows.getObject("z") == null) {
            return "its position is missing or incomplete";
        }

        return null;
    }

    private static SourceLocation location(ResultSet rows, HuskHomesConfig config) throws SQLException {
        return new SourceLocation(rows.getString("world_name"), rows.getDouble("x"), rows.getDouble("y"),
                rows.getDouble("z"), rows.getFloat("yaw"), rows.getFloat("pitch"),
                config.crossServer() ? rows.getString("server_name") : null);
    }

    private static long timestamp(ResultSet rows) {
        try {
            Timestamp created = rows.getTimestamp("created_at");
            return created == null ? 0L : created.getTime();
        } catch (SQLException | RuntimeException e) {
            // Drivers disagree on how SQLite's datetime column comes back; the creation time is cosmetic.
            return 0L;
        }
    }

    private static Connection open(HuskHomesConfig config, Path folder) throws MigrationException {
        HuskHomesConfig.Credentials credentials = config.credentials();
        Properties properties = new Properties();
        String driver;
        String url;

        switch (config.type()) {
            case SQLITE -> {
                Path file = folder.resolve(SQLITE_FILE);
                requireFile(file);
                driver = "org.sqlite.JDBC";
                url = "jdbc:sqlite:" + file.toAbsolutePath();
                // SQLITE_OPEN_READONLY: the driver refuses every write and never creates the file.
                properties.setProperty("open_mode", "1");
            }
            case H2 -> {
                Path base = folder.resolve(H2_FILE);
                requireFile(folder.resolve(H2_FILE + ".mv.db"));
                driver = "org.h2.Driver";
                url = "jdbc:h2:" + base.toAbsolutePath() + ";ACCESS_MODE_DATA=r;IFEXISTS=TRUE";
                properties.setProperty("user", "sa");
                properties.setProperty("password", "sa");
            }
            case MYSQL, MARIADB -> {
                // Connector/J speaks to MariaDB as well, and is the driver this plugin already ships.
                driver = "com.mysql.cj.jdbc.Driver";
                url = "jdbc:mysql://" + credentials.host() + ":" + credentials.port() + "/"
                        + credentials.database() + parameters(credentials.parameters());
                properties.setProperty("user", credentials.username());
                properties.setProperty("password", credentials.password());
                properties.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS * 1000));
            }
            case POSTGRESQL -> {
                driver = "org.postgresql.Driver";
                url = "jdbc:postgresql://" + credentials.host() + ":" + credentials.port() + "/"
                        + credentials.database() + parameters(credentials.parameters());
                properties.setProperty("user", credentials.username());
                properties.setProperty("password", credentials.password());
                properties.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
                properties.setProperty("loginTimeout", String.valueOf(CONNECT_TIMEOUT_SECONDS));
            }
            default -> throw new IllegalStateException("Unhandled database type " + config.type());
        }

        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new MigrationException("migrate.errors.driver-missing", "JDBC driver " + driver
                    + " is not available", e, "{driver}", driver);
        }

        try {
            Connection connection = DriverManager.getConnection(url, properties);
            connection.setReadOnly(true);
            return connection;
        } catch (SQLException e) {
            throw new MigrationException("migrate.errors.connect-failed", "could not connect to HuskHomes' "
                    + config.type() + " database (" + describe(config, folder) + ")", e,
                    "{error}", sanitize(e, config));
        }
    }

    private static void requireFile(Path file) throws MigrationException {
        if (!Files.isRegularFile(file)) {
            throw new MigrationException("migrate.errors.not-found", "HuskHomes database file " + file
                    + " does not exist", "{path}", file.toString());
        }
    }

    private static String parameters(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return "";
        }
        return parameters.startsWith("?") ? parameters : "?" + parameters;
    }

    static String describe(HuskHomesConfig config, Path folder) {
        return switch (config.type()) {
            case SQLITE -> "HuskHomes SQLite, " + folder.resolve(SQLITE_FILE);
            case H2 -> "HuskHomes H2, " + folder.resolve(H2_FILE + ".mv.db");
            default -> "HuskHomes " + config.type() + ", " + config.credentials();
        };
    }

    /**
     * Driver messages may echo the user name back ("Access denied for user ..."); neither it nor the password
     * is allowed to reach a chat message or the log.
     */
    static String sanitize(SQLException error, HuskHomesConfig config) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        HuskHomesConfig.Credentials credentials = config.credentials();

        if (!credentials.password().isEmpty()) {
            message = message.replace(credentials.password(), "***");
        }
        if (!credentials.username().isEmpty()) {
            message = message.replace(credentials.username(), "***");
        }

        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }

        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

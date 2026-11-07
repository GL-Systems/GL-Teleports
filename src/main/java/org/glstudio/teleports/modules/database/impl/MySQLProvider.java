package org.glstudio.teleports.modules.database.impl;

import com.zaxxer.hikari.HikariDataSource;
import org.glstudio.nexus.storage.ConnectionHints;
import org.glstudio.nexus.storage.DataSources;
import org.glstudio.nexus.storage.PoolOptions;
import org.glstudio.nexus.storage.SqlDialect;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.PlayerRef;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.StoredLocation;
import org.glstudio.teleports.modules.model.Warp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Stores the data in MySQL.
 */
public class MySQLProvider extends SqlProvider {
    private HikariDataSource dataSource;

    /**
     * Creates the provider.
     */
    public MySQLProvider(GTeleports plugin) {
        super(plugin);
    }

    @Override
    protected Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    protected String replaceInto(String table, String... columns) {
        List<String> names = Arrays.asList(columns);
        return SqlDialect.MYSQL.upsert(table, List.of(), names, names);
    }

    @Override
    protected String autoIncrementId() {
        return "BIGINT AUTO_INCREMENT PRIMARY KEY";
    }

    @Override
    protected String tableOptions() {
        return " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return run(() -> {
            try {
                var dbConfig = plugin.getConfigManager().getDatabaseConfig();

                                dataSource = DataSources.create(
                        DataSources.mysqlUrl(dbConfig.getString("mysql.host"), dbConfig.getInt("mysql.port"),
                                dbConfig.getString("mysql.database"), dbConfig.getBoolean("mysql.useSSL")),
                        dbConfig.getString("mysql.username"), dbConfig.getString("mysql.password"),
                        PoolOptions.named("GL-Teleports-MySQL").maximumPoolSize(dbConfig.getInt("mysql.pool-size", 10)));

                createTables();
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize MySQL provider. " + ConnectionHints.describe(e, "mysql"), e);
            }
        });
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;

        for (int depth = 0; depth < 10 && current.getCause() != null && current.getCause() != current; depth++) {
            current = current.getCause();
        }

        return current.getMessage();
    }

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS homes (" +
                            "owner VARCHAR(36) NOT NULL, " +
                            "name VARCHAR(32) NOT NULL, " +
                            "world VARCHAR(64) NOT NULL, " +
                            "x DOUBLE NOT NULL, " +
                            "y DOUBLE NOT NULL, " +
                            "z DOUBLE NOT NULL, " +
                            "yaw FLOAT NOT NULL, " +
                            "pitch FLOAT NOT NULL, " +
                            "created_at BIGINT NOT NULL, " +
                            "server VARCHAR(64), " +
                            "PRIMARY KEY (owner, name)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS warps (" +
                            "name VARCHAR(32) PRIMARY KEY, " +
                            "world VARCHAR(64) NOT NULL, " +
                            "x DOUBLE NOT NULL, " +
                            "y DOUBLE NOT NULL, " +
                            "z DOUBLE NOT NULL, " +
                            "yaw FLOAT NOT NULL, " +
                            "pitch FLOAT NOT NULL, " +
                            "permission VARCHAR(128), " +
                            "created_at BIGINT NOT NULL, " +
                            "server VARCHAR(64)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS spawns (" +
                            "server VARCHAR(64) PRIMARY KEY, " +
                            "world VARCHAR(64) NOT NULL, " +
                            "x DOUBLE NOT NULL, " +
                            "y DOUBLE NOT NULL, " +
                            "z DOUBLE NOT NULL, " +
                            "yaw FLOAT NOT NULL, " +
                            "pitch FLOAT NOT NULL" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS player_settings (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "tpa_blocked BOOLEAN NOT NULL DEFAULT FALSE" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS players (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "name VARCHAR(16) NOT NULL, " +
                            "last_seen BIGINT NOT NULL" +
                            ")  ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS last_locations (" +
                            "uuid VARCHAR(36) NOT NULL, " +
                            "server VARCHAR(64) NOT NULL, " +
                            "world VARCHAR(64) NOT NULL, " +
                            "x DOUBLE NOT NULL, " +
                            "y DOUBLE NOT NULL, " +
                            "z DOUBLE NOT NULL, " +
                            "yaw FLOAT NOT NULL, " +
                            "pitch FLOAT NOT NULL, " +
                            "updated_at BIGINT NOT NULL, " +
                            "PRIMARY KEY (uuid, server)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS bed_respawn_points (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "world VARCHAR(64) NOT NULL, " +
                            "x DOUBLE NOT NULL, " +
                            "y DOUBLE NOT NULL, " +
                            "z DOUBLE NOT NULL, " +
                            "yaw FLOAT NOT NULL, " +
                            "pitch FLOAT NOT NULL, " +
                            "updated_at BIGINT NOT NULL" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );

            createFeatureTables(conn);

            String server = currentServer();
            SqlSchema.addColumnIfMissing(conn, "homes", "server", "VARCHAR(64)");
            SqlSchema.addColumnIfMissing(conn, "warps", "server", "VARCHAR(64)");
            addWarpOverrideColumns(conn);
            SqlSchema.backfillServer(conn, "homes", server);
            SqlSchema.backfillServer(conn, "warps", server);
            SqlSchema.migrateLegacySpawn(conn, server);
            SqlSchema.createIndexes(conn);

            SqlSchema.ensureVersionTable(conn);
            SqlSchema.writeVersion(conn,
                    "INSERT INTO schema_version (id, version, updated_at) VALUES (1, ?, ?) ON DUPLICATE KEY UPDATE version=VALUES(version), updated_at=VALUES(updated_at)",
                    SqlSchema.CURRENT_VERSION);
        }
    }

    private String currentServer() {
        return plugin.getMultiServerSettings() == null
                ? "unnamed-server"
                : plugin.getMultiServerSettings().getServerName();
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return run(() -> {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        });
    }

    @Override
    public CompletableFuture<List<Home>> getHomes(UUID owner) {
        return supply(() -> {
            List<Home> homes = new ArrayList<>();
            String query = "SELECT * FROM homes WHERE owner = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, owner.toString());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    homes.add(new Home(
                            UUID.fromString(rs.getString("owner")),
                            rs.getString("name"),
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"),
                            rs.getLong("created_at"),
                            rs.getString("server")
                    ));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get homes", e);
            }

            return homes;
        });
    }

    @Override
    public CompletableFuture<Home> getHome(UUID owner, String name) {
        return supply(() -> {
            String query = "SELECT * FROM homes WHERE owner = ? AND name = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, owner.toString());
                stmt.setString(2, name);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return new Home(
                            UUID.fromString(rs.getString("owner")),
                            rs.getString("name"),
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"),
                            rs.getLong("created_at"),
                            rs.getString("server")
                    );
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get home", e);
            }

            return null;
        });
    }

    @Override
    public CompletableFuture<Void> saveHome(Home home) {
        return run(() -> {
            String query = "INSERT INTO homes (owner, name, world, x, y, z, yaw, pitch, created_at, server) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), " +
                    "yaw=VALUES(yaw), pitch=VALUES(pitch), server=VALUES(server)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, home.getOwner().toString());
                stmt.setString(2, home.getName());
                stmt.setString(3, home.getWorld());
                stmt.setDouble(4, home.getX());
                stmt.setDouble(5, home.getY());
                stmt.setDouble(6, home.getZ());
                stmt.setFloat(7, home.getYaw());
                stmt.setFloat(8, home.getPitch());
                stmt.setLong(9, home.getCreatedAt());
                stmt.setString(10, home.getServer());

                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save home", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteHome(UUID owner, String name) {
        return run(() -> {
            String query = "DELETE FROM homes WHERE owner = ? AND name = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, owner.toString());
                stmt.setString(2, name);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete home", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<Warp>> getAllWarps() {
        return supply(() -> {
            List<Warp> warps = new ArrayList<>();
            String query = "SELECT * FROM warps";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    warps.add(readWarp(rs));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get warps", e);
            }

            return warps;
        });
    }

    @Override
    public CompletableFuture<Warp> getWarp(String name) {
        return supply(() -> {
            String query = "SELECT * FROM warps WHERE name = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, name);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return readWarp(rs);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get warp", e);
            }

            return null;
        });
    }

    @Override
    public CompletableFuture<Void> saveWarp(Warp warp) {
        return run(() -> {
            String query = replaceInto("warps", "name", "world", "x", "y", "z", "yaw", "pitch",
                    "permission", "created_at", "server", "cost", "cooldown", "delay");

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, warp.getName());
                stmt.setString(2, warp.getWorld());
                stmt.setDouble(3, warp.getX());
                stmt.setDouble(4, warp.getY());
                stmt.setDouble(5, warp.getZ());
                stmt.setFloat(6, warp.getYaw());
                stmt.setFloat(7, warp.getPitch());
                stmt.setString(8, warp.getPermission());
                stmt.setLong(9, warp.getCreatedAt());
                stmt.setString(10, warp.getServer());
                bindWarpOverrides(stmt, warp, 11);

                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save warp", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteWarp(String name) {
        return supply(() -> {
            String query = "DELETE FROM warps WHERE name = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, name);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete warp", e);
            }
        });
    }

    @Override
    public CompletableFuture<SpawnLocation> getSpawn() {
        return supply(() -> {
            String query = "SELECT * FROM spawns LIMIT 1";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return new SpawnLocation(
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"),
                            rs.getString("server")
                    );
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get spawn", e);
            }

            return null;
        });
    }

    @Override
    public CompletableFuture<Void> saveSpawn(SpawnLocation spawn) {
        return run(() -> {
            String query = "INSERT INTO spawns (server, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), " +
                    "yaw=VALUES(yaw), pitch=VALUES(pitch)";

            try (Connection conn = dataSource.getConnection()) {

                SqlSchema.clearOtherSpawns(conn, spawn.getServer());

                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, spawn.getServer());
                    stmt.setString(2, spawn.getWorld());
                    stmt.setDouble(3, spawn.getX());
                    stmt.setDouble(4, spawn.getY());
                    stmt.setDouble(5, spawn.getZ());
                    stmt.setFloat(6, spawn.getYaw());
                    stmt.setFloat(7, spawn.getPitch());

                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save spawn", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> isTpaBlocked(UUID uuid) {
        return supply(() -> {
            String query = "SELECT tpa_blocked FROM player_settings WHERE uuid = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return rs.getBoolean("tpa_blocked");
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to read player settings", e);
            }

            return false;
        });
    }

    @Override
    public CompletableFuture<Void> setTpaBlocked(UUID uuid, boolean blocked) {
        return run(() -> {
            String query = "INSERT INTO player_settings (uuid, tpa_blocked) VALUES (?, ?) ON DUPLICATE KEY UPDATE tpa_blocked=VALUES(tpa_blocked)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, uuid.toString());
                stmt.setBoolean(2, blocked);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save player settings", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> rememberPlayer(UUID uuid, String name) {
        return run(() -> {
            String query = "INSERT INTO players (uuid, name, last_seen) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE name=VALUES(name), last_seen=VALUES(last_seen)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, name);
                stmt.setLong(3, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to remember player", e);
            }
        });
    }

    @Override
    public CompletableFuture<PlayerRef> findPlayer(String name) {
        return supply(() -> {
            String query = "SELECT uuid, name FROM players WHERE LOWER(name) = LOWER(?)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, name);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return new PlayerRef(UUID.fromString(rs.getString("uuid")), rs.getString("name"));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to look up player", e);
            }

            return null;
        });
    }

    @Override
    public CompletableFuture<Void> saveLastLocation(UUID uuid, String server, StoredLocation location) {
        return run(() -> {
            String query = "INSERT INTO last_locations (uuid, server, world, x, y, z, yaw, pitch, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch), updated_at=VALUES(updated_at)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, server);
                stmt.setString(3, location.world());
                stmt.setDouble(4, location.x());
                stmt.setDouble(5, location.y());
                stmt.setDouble(6, location.z());
                stmt.setFloat(7, location.yaw());
                stmt.setFloat(8, location.pitch());
                stmt.setLong(9, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save last location", e);
            }
        });
    }

    @Override
    public CompletableFuture<StoredLocation> getLastLocation(UUID uuid, String server) {
        return supply(() -> {
            String query = "SELECT * FROM last_locations WHERE uuid = ? AND server = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, server);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return new StoredLocation(rs.getString("world"), rs.getDouble("x"),
                            rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to read last location", e);
            }

            return null;
        });
    }

    @Override
    public CompletableFuture<Void> saveBedRespawnPoint(UUID uuid, StoredLocation location) {
        return run(() -> {
            String query = "INSERT INTO bed_respawn_points (uuid, world, x, y, z, yaw, pitch, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch), updated_at=VALUES(updated_at)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, location.world());
                stmt.setDouble(3, location.x());
                stmt.setDouble(4, location.y());
                stmt.setDouble(5, location.z());
                stmt.setFloat(6, location.yaw());
                stmt.setFloat(7, location.pitch());
                stmt.setLong(8, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save bed respawn point", e);
            }
        });
    }

    @Override
    public CompletableFuture<StoredLocation> getBedRespawnPoint(UUID uuid) {
        return supply(() -> {
            String query = "SELECT * FROM bed_respawn_points WHERE uuid = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return new StoredLocation(rs.getString("world"), rs.getDouble("x"),
                            rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to read bed respawn point", e);
            }

            return null;
        });
    }

    @Override
    public CompletableFuture<Void> clearBedRespawnPoint(UUID uuid) {
        return run(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM bed_respawn_points WHERE uuid = ?")) {
                stmt.setString(1, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to clear bed respawn point", e);
            }
        });
    }
}

package org.glstudio.teleports.modules.database.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.database.DatabaseProvider;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.SpawnLocation;
import org.glstudio.teleports.modules.model.Warp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MySQLProvider implements DatabaseProvider {

    private final GTeleports plugin;
    private HikariDataSource dataSource;

    public MySQLProvider(GTeleports plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                var dbConfig = plugin.getConfigManager().getDatabaseConfig();

                HikariConfig config = new HikariConfig();
                config.setJdbcUrl("jdbc:mysql://" +
                        dbConfig.getString("mysql.host") + ":" +
                        dbConfig.getInt("mysql.port") + "/" +
                        dbConfig.getString("mysql.database") +
                        "?useSSL=" + dbConfig.getBoolean("mysql.useSSL"));
                config.setUsername(dbConfig.getString("mysql.username"));
                config.setPassword(dbConfig.getString("mysql.password"));
                config.setMaximumPoolSize(dbConfig.getInt("mysql.pool-size", 10));
                config.setConnectionTimeout(30000);

                dataSource = new HikariDataSource(config);

                createTables();
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize MySQL provider", e);
            }
        });
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
                    "CREATE TABLE IF NOT EXISTS spawn (" +
                            "id INT PRIMARY KEY, " +
                            "world VARCHAR(64) NOT NULL, " +
                            "x DOUBLE NOT NULL, " +
                            "y DOUBLE NOT NULL, " +
                            "z DOUBLE NOT NULL, " +
                            "yaw FLOAT NOT NULL, " +
                            "pitch FLOAT NOT NULL, " +
                            "server VARCHAR(64)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
        }
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        });
    }

    @Override
    public CompletableFuture<List<Home>> getHomes(UUID owner) {
        return CompletableFuture.supplyAsync(() -> {
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
        return CompletableFuture.supplyAsync(() -> {
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
        return CompletableFuture.runAsync(() -> {
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
        return CompletableFuture.runAsync(() -> {
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
    public CompletableFuture<Integer> getHomeCount(UUID owner) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT COUNT(*) FROM homes WHERE owner = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, owner.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get home count", e);
            }

            return 0;
        });
    }

    @Override
    public CompletableFuture<List<Warp>> getAllWarps() {
        return CompletableFuture.supplyAsync(() -> {
            List<Warp> warps = new ArrayList<>();
            String query = "SELECT * FROM warps";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    warps.add(new Warp(
                            rs.getString("name"),
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"),
                            rs.getString("permission"),
                            rs.getLong("created_at"),
                            rs.getString("server")
                    ));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get warps", e);
            }

            return warps;
        });
    }

    @Override
    public CompletableFuture<Warp> getWarp(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT * FROM warps WHERE name = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, name);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return new Warp(
                            rs.getString("name"),
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"),
                            rs.getString("permission"),
                            rs.getLong("created_at"),
                            rs.getString("server")
                    );
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get warp", e);
            }

            return null;
        });
    }

    @Override
    public CompletableFuture<Void> saveWarp(Warp warp) {
        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO warps (name, world, x, y, z, yaw, pitch, permission, created_at, server) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), " +
                    "yaw=VALUES(yaw), pitch=VALUES(pitch), permission=VALUES(permission), server=VALUES(server)";

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

                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save warp", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteWarp(String name) {
        return CompletableFuture.runAsync(() -> {
            String query = "DELETE FROM warps WHERE name = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, name);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete warp", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> warpExists(String name) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT COUNT(*) FROM warps WHERE name = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, name);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to check warp existence", e);
            }

            return false;
        });
    }

    @Override
    public CompletableFuture<SpawnLocation> getSpawn() {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT * FROM spawn WHERE id = 1";

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
        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO spawn (id, world, x, y, z, yaw, pitch, server) VALUES (1, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), " +
                    "yaw=VALUES(yaw), pitch=VALUES(pitch), server=VALUES(server)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, spawn.getWorld());
                stmt.setDouble(2, spawn.getX());
                stmt.setDouble(3, spawn.getY());
                stmt.setDouble(4, spawn.getZ());
                stmt.setFloat(5, spawn.getYaw());
                stmt.setFloat(6, spawn.getPitch());
                stmt.setString(7, spawn.getServer());

                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save spawn", e);
            }
        });
    }
}

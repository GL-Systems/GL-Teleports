package org.glstudio.teleports.modules.database.impl;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.BackLocation;
import org.glstudio.teleports.modules.model.DestinationLock;
import org.glstudio.teleports.modules.model.Home;
import org.glstudio.teleports.modules.model.HomeShare;
import org.glstudio.teleports.modules.model.PlayerWarp;
import org.glstudio.teleports.modules.model.PlayerWarpBan;
import org.glstudio.teleports.modules.model.PlayerWarpFavourite;
import org.glstudio.teleports.modules.model.PlayerWarpImport;
import org.glstudio.teleports.modules.model.PlayerWarpManagerEntry;
import org.glstudio.teleports.modules.model.PlayerWarpRating;
import org.glstudio.teleports.modules.model.PlayerWarpSponsoredSlot;
import org.glstudio.teleports.modules.model.PlayerWarpWhitelistEntry;
import org.glstudio.teleports.modules.model.TeleportRecord;
import org.glstudio.teleports.modules.model.TeleportType;
import org.glstudio.teleports.modules.model.Warp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * What the SQL backends share.
 */
public abstract class SqlProvider extends AbstractProvider {
    /**
     * Creates the provider.
     */
    protected SqlProvider(GTeleports plugin) {
        super(plugin);
    }

    /**
     * Borrows a connection.
     */
    protected abstract Connection connection() throws SQLException;

    /**
     * Builds the insert-or-overwrite statement of this backend.
     */
    protected abstract String replaceInto(String table, String... columns);

    /**
     * Returns the definition of an auto-increment primary key.
     */
    protected abstract String autoIncrementId();

    /**
     * Returns the text appended to each CREATE TABLE.
     */
    protected String tableOptions() {
        return "";
    }

    /**
     * Returns this server's name.
     */
    protected String currentServerName() {
        return plugin.getMultiServerSettings() == null
                ? "unnamed-server"
                : plugin.getMultiServerSettings().getServerName();
    }

    /**
     * Builds the column list and the VALUES placeholders of an insert.
     */
    protected static String columnsAndPlaceholders(String... columns) {
        StringBuilder names = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();

        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                names.append(", ");
                placeholders.append(", ");
            }
            names.append(columns[i]);
            placeholders.append('?');
        }

        return " (" + names + ") VALUES (" + placeholders + ")";
    }

    /**
     * Creates the feature tables.
     */
    protected void createFeatureTables(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS teleport_log ("
                            + "id " + autoIncrementId() + ", "
                            + "player VARCHAR(36) NOT NULL, "
                            + "player_name VARCHAR(16), "
                            + "type VARCHAR(16) NOT NULL, "
                            + "destination VARCHAR(64), "
                            + "from_server VARCHAR(64), "
                            + "from_world VARCHAR(64), "
                            + "from_x DOUBLE, from_y DOUBLE, from_z DOUBLE, "
                            + "to_server VARCHAR(64), "
                            + "to_world VARCHAR(64), "
                            + "to_x DOUBLE, to_y DOUBLE, to_z DOUBLE, "
                            + "cost DOUBLE, "
                            + "created_at BIGINT NOT NULL, "
                            + "result VARCHAR(32) NOT NULL)" + tableOptions());

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS home_shares ("
                            + "owner VARCHAR(36) NOT NULL, "
                            + "owner_name VARCHAR(16), "
                            + "home VARCHAR(32) NOT NULL, "
                            + "invitee VARCHAR(36) NOT NULL, "
                            + "invitee_name VARCHAR(16), "
                            + "granted_at BIGINT NOT NULL, "
                            + "expires_at BIGINT NOT NULL, "
                            + "PRIMARY KEY (owner, home, invitee))" + tableOptions());

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS tpa_blocks ("
                            + "owner VARCHAR(36) NOT NULL, "
                            + "blocked VARCHAR(36) NOT NULL, "
                            + "created_at BIGINT NOT NULL, "
                            + "PRIMARY KEY (owner, blocked))" + tableOptions());

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS destination_locks ("
                            + "lock_type VARCHAR(16) NOT NULL, "
                            + "target VARCHAR(64) NOT NULL, "
                            + "reason VARCHAR(255), "
                            + "locked_by VARCHAR(32), "
                            + "locked_at BIGINT NOT NULL, "
                            + "until BIGINT NOT NULL, "
                            + "PRIMARY KEY (lock_type, target))" + tableOptions());

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS player_warps ("
                            + "id VARCHAR(36) PRIMARY KEY, "
                            + "name VARCHAR(24) NOT NULL UNIQUE, "
                            + "owner VARCHAR(36) NOT NULL, "
                            + "owner_name VARCHAR(16), "
                            + "world VARCHAR(64) NOT NULL, "
                            + "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, "
                            + "yaw FLOAT NOT NULL, pitch FLOAT NOT NULL, "
                            + "server VARCHAR(64), "
                            + "description VARCHAR(256), "
                            + "icon VARCHAR(64), "
                            + "use_owner_head BOOLEAN NOT NULL DEFAULT FALSE, "
                            + "category_id VARCHAR(32), "
                            + "cost DOUBLE, cooldown INT, delay INT, "
                            + "locked BOOLEAN NOT NULL DEFAULT FALSE, "
                            + "password_hash VARCHAR(128), password_salt VARCHAR(64), "
                            + "whitelist_enabled BOOLEAN NOT NULL DEFAULT FALSE, "
                            + "rating_average DOUBLE NOT NULL DEFAULT 0, "
                            + "rating_count INT NOT NULL DEFAULT 0, "
                            + "favourite_count INT NOT NULL DEFAULT 0, "
                            + "created_at BIGINT NOT NULL, "
                            + "updated_at BIGINT NOT NULL, "
                            + "last_used_at BIGINT NOT NULL)" + tableOptions());

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS player_warp_whitelist ("
                            + "warp_id VARCHAR(36) NOT NULL, "
                            + "player VARCHAR(36) NOT NULL, "
                            + "player_name VARCHAR(16), "
                            + "added_at BIGINT NOT NULL, "
                            + "PRIMARY KEY (warp_id, player))" + tableOptions());

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS player_warp_bans ("
                            + "warp_id VARCHAR(36) NOT NULL, "
                            + "player VARCHAR(36) NOT NULL, "
                            + "player_name VARCHAR(16), "
                            + "banned_by VARCHAR(36), "
                            + "banned_by_name VARCHAR(16), "
                            + "reason VARCHAR(255), "
                            + "banned_at BIGINT NOT NULL, "
                            + "expires_at BIGINT NOT NULL, "
                            + "PRIMARY KEY (warp_id, player))" + tableOptions());

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS player_warp_managers ("
                            + "warp_id VARCHAR(36) NOT NULL, "
                            + "player VARCHAR(36) NOT NULL, "
                            + "player_name VARCHAR(16), "
                            + "added_at BIGINT NOT NULL, "
                            + "PRIMARY KEY (warp_id, player))" + tableOptions());

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS player_warp_ratings ("
                            + "warp_id VARCHAR(36) NOT NULL, "
                            + "player VARCHAR(36) NOT NULL, "
                            + "player_name VARCHAR(16), "
                            + "stars INT NOT NULL, "
                            + "comment VARCHAR(256), "
                            + "created_at BIGINT NOT NULL, "
                            + "updated_at BIGINT NOT NULL, "
                            + "PRIMARY KEY (warp_id, player))" + tableOptions());

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS player_warp_favourites ("
                            + "player VARCHAR(36) NOT NULL, "
                            + "warp_id VARCHAR(36) NOT NULL, "
                            + "added_at BIGINT NOT NULL, "
                            + "PRIMARY KEY (player, warp_id))" + tableOptions());

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS back_locations ("
                            + "owner VARCHAR(36) PRIMARY KEY, "
                            + "world VARCHAR(64) NOT NULL, "
                            + "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, "
                            + "yaw FLOAT NOT NULL, pitch FLOAT NOT NULL, "
                            + "server VARCHAR(64), "
                            + "saved_at BIGINT NOT NULL)" + tableOptions());

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS player_warp_sponsored_slots ("
                            + "slot_index INT PRIMARY KEY, "
                            + "warp_id VARCHAR(36) NOT NULL, "
                            + "owner VARCHAR(36) NOT NULL, "
                            + "owner_name VARCHAR(16), "
                            + "started_at BIGINT NOT NULL, "
                            + "expires_at BIGINT NOT NULL, "
                            + "price_paid DOUBLE NOT NULL)" + tableOptions());
        }

        createIndexQuietly(conn, "idx_log_player", "teleport_log", "player");
        createIndexQuietly(conn, "idx_log_destination", "teleport_log", "destination");
        createIndexQuietly(conn, "idx_log_created", "teleport_log", "created_at");
        createIndexQuietly(conn, "idx_shares_invitee", "home_shares", "invitee");
        createIndexQuietly(conn, "idx_pw_owner", "player_warps", "owner");
        createIndexQuietly(conn, "idx_pw_category", "player_warps", "category_id");
        createIndexQuietly(conn, "idx_pw_bans_player", "player_warp_bans", "player");
        createIndexQuietly(conn, "idx_pw_favourites_player", "player_warp_favourites", "player");
    }

    /**
     * Adds the warp override columns.
     */
    protected void addWarpOverrideColumns(Connection conn) throws SQLException {
        SqlSchema.addColumnIfMissing(conn, "warps", "cost", "DOUBLE");
        SqlSchema.addColumnIfMissing(conn, "warps", "cooldown", "INT");
        SqlSchema.addColumnIfMissing(conn, "warps", "delay", "INT");
    }

    private void createIndexQuietly(Connection conn, String index, String table, String column) {
        try (Statement statement = conn.createStatement()) {
            statement.execute("CREATE INDEX " + index + " ON " + table + " (" + column + ")");
        } catch (SQLException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            if (!message.contains("duplicate") && !message.contains("exists")) {
                throw new RuntimeException("Failed to create index " + index, e);
            }
        }
    }

    /**
     * Reads the warp.
     */
    protected static Warp readWarp(ResultSet rows) throws SQLException {
        double cost = rows.getDouble("cost");
        Double resolvedCost = rows.wasNull() ? null : cost;

        int cooldown = rows.getInt("cooldown");
        Integer resolvedCooldown = rows.wasNull() ? null : cooldown;

        int delay = rows.getInt("delay");
        Integer resolvedDelay = rows.wasNull() ? null : delay;

        return new Warp(
                rows.getString("name"),
                rows.getString("world"),
                rows.getDouble("x"),
                rows.getDouble("y"),
                rows.getDouble("z"),
                rows.getFloat("yaw"),
                rows.getFloat("pitch"),
                rows.getString("permission"),
                rows.getLong("created_at"),
                rows.getString("server"),
                resolvedCost,
                resolvedCooldown,
                resolvedDelay);
    }

    /**
     * Fills the warp settings of a statement.
     */
    protected static void bindWarpOverrides(PreparedStatement stmt, Warp warp, int firstIndex)
            throws SQLException {
        if (warp.getCost() == null) {
            stmt.setNull(firstIndex, Types.DOUBLE);
        } else {
            stmt.setDouble(firstIndex, warp.getCost());
        }

        if (warp.getCooldown() == null) {
            stmt.setNull(firstIndex + 1, Types.INTEGER);
        } else {
            stmt.setInt(firstIndex + 1, warp.getCooldown());
        }

        if (warp.getDelay() == null) {
            stmt.setNull(firstIndex + 2, Types.INTEGER);
        } else {
            stmt.setInt(firstIndex + 2, warp.getDelay());
        }
    }

    @Override
    public CompletableFuture<Void> importData(List<Home> homes, List<Warp> warps) {
        return run(() -> {
            String homeQuery = replaceInto("homes", "owner", "name", "world", "x", "y", "z", "yaw", "pitch",
                    "created_at", "server");
            String warpQuery = replaceInto("warps", "name", "world", "x", "y", "z", "yaw", "pitch",
                    "permission", "created_at", "server", "cost", "cooldown", "delay");

            try (Connection conn = connection()) {
                boolean autoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                try (PreparedStatement homeStmt = conn.prepareStatement(homeQuery);
                     PreparedStatement warpStmt = conn.prepareStatement(warpQuery)) {
                    for (Home home : homes) {
                        homeStmt.setString(1, home.getOwner().toString());
                        homeStmt.setString(2, home.getName());
                        homeStmt.setString(3, home.getWorld());
                        homeStmt.setDouble(4, home.getX());
                        homeStmt.setDouble(5, home.getY());
                        homeStmt.setDouble(6, home.getZ());
                        homeStmt.setFloat(7, home.getYaw());
                        homeStmt.setFloat(8, home.getPitch());
                        homeStmt.setLong(9, home.getCreatedAt());
                        homeStmt.setString(10, home.getServer());
                        homeStmt.addBatch();
                    }

                    for (Warp warp : warps) {
                        warpStmt.setString(1, warp.getName());
                        warpStmt.setString(2, warp.getWorld());
                        warpStmt.setDouble(3, warp.getX());
                        warpStmt.setDouble(4, warp.getY());
                        warpStmt.setDouble(5, warp.getZ());
                        warpStmt.setFloat(6, warp.getYaw());
                        warpStmt.setFloat(7, warp.getPitch());
                        warpStmt.setString(8, warp.getPermission());
                        warpStmt.setLong(9, warp.getCreatedAt());
                        warpStmt.setString(10, warp.getServer());
                        bindWarpOverrides(warpStmt, warp, 11);
                        warpStmt.addBatch();
                    }

                    if (!homes.isEmpty()) {
                        homeStmt.executeBatch();
                    }
                    if (!warps.isEmpty()) {
                        warpStmt.executeBatch();
                    }

                    conn.commit();
                } catch (SQLException | RuntimeException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(autoCommit);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to import homes and warps; nothing was written", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> logTeleport(TeleportRecord record) {
        return run(() -> {
            String query = "INSERT INTO teleport_log" + columnsAndPlaceholders(
                    "player", "player_name", "type", "destination",
                    "from_server", "from_world", "from_x", "from_y", "from_z",
                    "to_server", "to_world", "to_x", "to_y", "to_z",
                    "cost", "created_at", "result");

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, record.player().toString());
                stmt.setString(2, record.playerName());
                stmt.setString(3, record.type().name());
                stmt.setString(4, record.destination());
                stmt.setString(5, record.fromServer());
                stmt.setString(6, record.fromWorld());
                stmt.setDouble(7, record.fromX());
                stmt.setDouble(8, record.fromY());
                stmt.setDouble(9, record.fromZ());
                stmt.setString(10, record.toServer());
                stmt.setString(11, record.toWorld());
                stmt.setDouble(12, record.toX());
                stmt.setDouble(13, record.toY());
                stmt.setDouble(14, record.toZ());
                stmt.setDouble(15, record.cost());
                stmt.setLong(16, record.timestamp());
                stmt.setString(17, record.result());

                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to log teleport", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<TeleportRecord>> getTeleportHistory(UUID player, int limit) {
        return supply(() -> query(
                "SELECT * FROM teleport_log WHERE player = ? ORDER BY created_at DESC LIMIT ?",
                stmt -> {
                    stmt.setString(1, player.toString());
                    stmt.setInt(2, Math.max(1, limit));
                }));
    }

    @Override
    public CompletableFuture<List<TeleportRecord>> getWarpHistory(String warp, int limit) {
        return supply(() -> query(
                "SELECT * FROM teleport_log WHERE type = ? AND LOWER(destination) = LOWER(?) "
                        + "ORDER BY created_at DESC LIMIT ?",
                stmt -> {
                    stmt.setString(1, TeleportType.WARP.name());
                    stmt.setString(2, warp);
                    stmt.setInt(3, Math.max(1, limit));
                }));
    }

    private List<TeleportRecord> query(String sql, StatementBinder binder) {
        List<TeleportRecord> records = new ArrayList<>();

        try (Connection conn = connection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            binder.bind(stmt);

            try (ResultSet rows = stmt.executeQuery()) {
                while (rows.next()) {
                    records.add(readRecord(rows));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read teleport history", e);
        }

        return records;
    }

    private static TeleportRecord readRecord(ResultSet rows) throws SQLException {
        TeleportType type;
        try {
            type = TeleportType.valueOf(rows.getString("type"));
        } catch (IllegalArgumentException e) {
            type = TeleportType.WARP;
        }

        return new TeleportRecord(
                rows.getLong("id"),
                UUID.fromString(rows.getString("player")),
                rows.getString("player_name"),
                type,
                rows.getString("destination"),
                rows.getString("from_server"),
                rows.getString("from_world"),
                rows.getDouble("from_x"),
                rows.getDouble("from_y"),
                rows.getDouble("from_z"),
                rows.getString("to_server"),
                rows.getString("to_world"),
                rows.getDouble("to_x"),
                rows.getDouble("to_y"),
                rows.getDouble("to_z"),
                rows.getDouble("cost"),
                rows.getLong("created_at"),
                rows.getString("result"));
    }

    @Override
    public CompletableFuture<Integer> purgeTeleportHistory(long cutoff) {
        return supply(() -> {
            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM teleport_log WHERE created_at < ?")) {
                stmt.setLong(1, cutoff);
                return stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to purge teleport history", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> shareHome(HomeShare share) {
        return run(() -> {
            String query = replaceInto("home_shares",
                    "owner", "owner_name", "home", "invitee", "invitee_name", "granted_at", "expires_at");

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, share.owner().toString());
                stmt.setString(2, share.ownerName());
                stmt.setString(3, share.home());
                stmt.setString(4, share.invitee().toString());
                stmt.setString(5, share.inviteeName());
                stmt.setLong(6, share.grantedAt());
                stmt.setLong(7, share.expiresAt());

                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to share home", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> unshareHome(UUID owner, String home, UUID invitee) {
        return supply(() -> {
            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM home_shares WHERE owner = ? AND home = ? AND invitee = ?")) {
                stmt.setString(1, owner.toString());
                stmt.setString(2, home);
                stmt.setString(3, invitee.toString());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to revoke home share", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<HomeShare>> getSharesByOwner(UUID owner) {
        return supply(() -> shares("SELECT * FROM home_shares WHERE owner = ?", owner));
    }

    @Override
    public CompletableFuture<List<HomeShare>> getSharesForInvitee(UUID invitee) {
        return supply(() -> shares("SELECT * FROM home_shares WHERE invitee = ?", invitee));
    }

    private List<HomeShare> shares(String sql, UUID id) {
        List<HomeShare> found = new ArrayList<>();

        try (Connection conn = connection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id.toString());

            try (ResultSet rows = stmt.executeQuery()) {
                while (rows.next()) {
                    found.add(new HomeShare(
                            UUID.fromString(rows.getString("owner")),
                            rows.getString("owner_name"),
                            rows.getString("home"),
                            UUID.fromString(rows.getString("invitee")),
                            rows.getString("invitee_name"),
                            rows.getLong("granted_at"),
                            rows.getLong("expires_at")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read home shares", e);
        }

        return found;
    }

    @Override
    public CompletableFuture<Void> clearSharesOf(UUID owner, String home) {
        return run(() -> {
            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM home_shares WHERE owner = ? AND home = ?")) {
                stmt.setString(1, owner.toString());
                stmt.setString(2, home);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to clear home shares", e);
            }
        });
    }

    @Override
    public CompletableFuture<Set<UUID>> getTpaBlocks(UUID owner) {
        return supply(() -> {
            Set<UUID> blocked = new HashSet<>();

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT blocked FROM tpa_blocks WHERE owner = ?")) {
                stmt.setString(1, owner.toString());

                try (ResultSet rows = stmt.executeQuery()) {
                    while (rows.next()) {
                        try {
                            blocked.add(UUID.fromString(rows.getString("blocked")));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to read TPA blocks", e);
            }

            return blocked;
        });
    }

    @Override
    public CompletableFuture<Void> addTpaBlock(UUID owner, UUID blocked) {
        return run(() -> {
            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         replaceInto("tpa_blocks", "owner", "blocked", "created_at"))) {
                stmt.setString(1, owner.toString());
                stmt.setString(2, blocked.toString());
                stmt.setLong(3, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to add TPA block", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> removeTpaBlock(UUID owner, UUID blocked) {
        return supply(() -> {
            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM tpa_blocks WHERE owner = ? AND blocked = ?")) {
                stmt.setString(1, owner.toString());
                stmt.setString(2, blocked.toString());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to remove TPA block", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<DestinationLock>> getLocks() {
        return supply(() -> {
            List<DestinationLock> locks = new ArrayList<>();

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM destination_locks");
                 ResultSet rows = stmt.executeQuery()) {
                while (rows.next()) {
                    locks.add(new DestinationLock(
                            rows.getString("lock_type"),
                            rows.getString("target"),
                            rows.getString("reason"),
                            rows.getString("locked_by"),
                            rows.getLong("locked_at"),
                            rows.getLong("until")));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to read destination locks", e);
            }

            return locks;
        });
    }

    @Override
    public CompletableFuture<Void> saveLock(DestinationLock lock) {
        return run(() -> {
            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(replaceInto("destination_locks",
                         "lock_type", "target", "reason", "locked_by", "locked_at", "until"))) {
                stmt.setString(1, lock.type());
                stmt.setString(2, lock.target());
                stmt.setString(3, lock.reason());
                stmt.setString(4, lock.lockedBy());
                stmt.setLong(5, lock.lockedAt());
                stmt.setLong(6, lock.until());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save destination lock", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteLock(String type, String target) {
        return supply(() -> {
            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM destination_locks WHERE lock_type = ? AND target = ?")) {
                stmt.setString(1, DestinationLock.normalize(type));
                stmt.setString(2, DestinationLock.normalize(target));
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to remove destination lock", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<PlayerWarp>> getAllPlayerWarps() {
        return supply(() -> {
            List<PlayerWarp> warps = new ArrayList<>();

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM player_warps");
                 ResultSet rows = stmt.executeQuery()) {
                while (rows.next()) {
                    warps.add(readPlayerWarp(rows));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get player warps", e);
            }

            return warps;
        });
    }

    @Override
    public CompletableFuture<PlayerWarp> getPlayerWarp(String id) {
        return supply(() -> {
            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM player_warps WHERE id = ?")) {
                stmt.setString(1, id);

                try (ResultSet rows = stmt.executeQuery()) {
                    return rows.next() ? readPlayerWarp(rows) : null;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get player warp", e);
            }
        });
    }

    @Override
    public CompletableFuture<PlayerWarp> findPlayerWarpByName(String name) {
        return supply(() -> {
            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM player_warps WHERE LOWER(name) = LOWER(?)")) {
                stmt.setString(1, name);

                try (ResultSet rows = stmt.executeQuery()) {
                    return rows.next() ? readPlayerWarp(rows) : null;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to find player warp", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerWarp(PlayerWarp warp) {
        return run(() -> {
            String query = replaceInto("player_warps", PLAYER_WARP_COLUMNS);

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                bindPlayerWarp(stmt, warp);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save player warp", e);
            }
        });
    }

    private static final String[] PLAYER_WARP_COLUMNS = {"id", "name", "owner", "owner_name", "world", "x", "y",
            "z", "yaw", "pitch", "server", "description", "icon", "use_owner_head", "category_id", "cost",
            "cooldown", "delay", "locked", "password_hash", "password_salt", "whitelist_enabled", "rating_average",
            "rating_count", "favourite_count", "created_at", "updated_at", "last_used_at"};

    private static void bindPlayerWarp(PreparedStatement stmt, PlayerWarp warp) throws SQLException {
        stmt.setString(1, warp.getId());
        stmt.setString(2, warp.getName());
        stmt.setString(3, warp.getOwner().toString());
        stmt.setString(4, warp.getOwnerName());
        stmt.setString(5, warp.getWorld());
        stmt.setDouble(6, warp.getX());
        stmt.setDouble(7, warp.getY());
        stmt.setDouble(8, warp.getZ());
        stmt.setFloat(9, warp.getYaw());
        stmt.setFloat(10, warp.getPitch());
        stmt.setString(11, warp.getServer());
        stmt.setString(12, warp.getDescription());
        stmt.setString(13, warp.getIcon());
        stmt.setBoolean(14, warp.isUseOwnerHead());
        stmt.setString(15, warp.getCategoryId());

        if (warp.getCost() == null) {
            stmt.setNull(16, Types.DOUBLE);
        } else {
            stmt.setDouble(16, warp.getCost());
        }
        if (warp.getCooldown() == null) {
            stmt.setNull(17, Types.INTEGER);
        } else {
            stmt.setInt(17, warp.getCooldown());
        }
        if (warp.getDelay() == null) {
            stmt.setNull(18, Types.INTEGER);
        } else {
            stmt.setInt(18, warp.getDelay());
        }

        stmt.setBoolean(19, warp.isLocked());
        stmt.setString(20, warp.getPasswordHash());
        stmt.setString(21, warp.getPasswordSalt());
        stmt.setBoolean(22, warp.isWhitelistEnabled());
        stmt.setDouble(23, warp.getRatingAverage());
        stmt.setInt(24, warp.getRatingCount());
        stmt.setInt(25, warp.getFavouriteCount());
        stmt.setLong(26, warp.getCreatedAt());
        stmt.setLong(27, warp.getUpdatedAt());
        stmt.setLong(28, warp.getLastUsedAt());
    }

    @Override
    public CompletableFuture<Void> importPlayerWarps(List<PlayerWarpImport> imports) {
        return run(() -> {
            try (Connection conn = connection()) {
                writePlayerWarps(conn, imports);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to import player warps; nothing was written", e);
            }
        });
    }

    /**
     * Imported warps are always new here, so they are inserted rather than upserted: should one collide with an
     * existing warp's id or name after all - a player created it while the import ran - the whole import is
     * rolled back instead of the upsert silently overwriting or deleting that player's warp.
     */
    void writePlayerWarps(Connection conn, List<PlayerWarpImport> imports) throws SQLException {
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try (PreparedStatement warpStmt = conn.prepareStatement(
                "INSERT INTO player_warps" + columnsAndPlaceholders(PLAYER_WARP_COLUMNS));
             PreparedStatement favouriteStmt = conn.prepareStatement(
                     replaceInto("player_warp_favourites", "player", "warp_id", "added_at"));
             PreparedStatement ratingStmt = conn.prepareStatement(
                     replaceInto("player_warp_ratings", "warp_id", "player", "player_name", "stars", "comment",
                             "created_at", "updated_at"));
             PreparedStatement whitelistStmt = conn.prepareStatement(
                     replaceInto("player_warp_whitelist", "warp_id", "player", "player_name", "added_at"));
             PreparedStatement managerStmt = conn.prepareStatement(
                     replaceInto("player_warp_managers", "warp_id", "player", "player_name", "added_at"));
             PreparedStatement banStmt = conn.prepareStatement(
                     replaceInto("player_warp_bans", "warp_id", "player", "player_name", "banned_by",
                             "banned_by_name", "reason", "banned_at", "expires_at"))) {
            for (PlayerWarpImport entry : imports) {
                bindPlayerWarp(warpStmt, entry.warp());
                warpStmt.addBatch();

                for (PlayerWarpFavourite favourite : entry.favourites()) {
                    favouriteStmt.setString(1, favourite.player().toString());
                    favouriteStmt.setString(2, favourite.warpId());
                    favouriteStmt.setLong(3, favourite.addedAt());
                    favouriteStmt.addBatch();
                }

                for (PlayerWarpRating rating : entry.ratings()) {
                    ratingStmt.setString(1, rating.warpId());
                    ratingStmt.setString(2, rating.player().toString());
                    ratingStmt.setString(3, rating.playerName());
                    ratingStmt.setInt(4, rating.stars());
                    ratingStmt.setString(5, rating.comment());
                    ratingStmt.setLong(6, rating.createdAt());
                    ratingStmt.setLong(7, rating.updatedAt());
                    ratingStmt.addBatch();
                }

                for (PlayerWarpWhitelistEntry whitelisted : entry.whitelist()) {
                    whitelistStmt.setString(1, whitelisted.warpId());
                    whitelistStmt.setString(2, whitelisted.player().toString());
                    whitelistStmt.setString(3, whitelisted.playerName());
                    whitelistStmt.setLong(4, whitelisted.addedAt());
                    whitelistStmt.addBatch();
                }

                for (PlayerWarpManagerEntry manager : entry.managers()) {
                    managerStmt.setString(1, manager.warpId());
                    managerStmt.setString(2, manager.player().toString());
                    managerStmt.setString(3, manager.playerName());
                    managerStmt.setLong(4, manager.addedAt());
                    managerStmt.addBatch();
                }

                for (PlayerWarpBan ban : entry.bans()) {
                    banStmt.setString(1, ban.warpId());
                    banStmt.setString(2, ban.player().toString());
                    banStmt.setString(3, ban.playerName());
                    banStmt.setString(4, ban.bannedBy());
                    banStmt.setString(5, ban.bannedByName());
                    banStmt.setString(6, ban.reason());
                    banStmt.setLong(7, ban.bannedAt());
                    banStmt.setLong(8, ban.expiresAt());
                    banStmt.addBatch();
                }
            }

            warpStmt.executeBatch();
            favouriteStmt.executeBatch();
            ratingStmt.executeBatch();
            whitelistStmt.executeBatch();
            managerStmt.executeBatch();
            banStmt.executeBatch();

            conn.commit();
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    @Override
    public CompletableFuture<Boolean> deletePlayerWarp(String id) {
        return supply(() -> {
            try (Connection conn = connection()) {
                for (String table : new String[]{"player_warp_whitelist", "player_warp_bans",
                        "player_warp_managers", "player_warp_ratings", "player_warp_favourites"}) {
                    String column = table.equals("player_warp_favourites") ? "warp_id" : "warp_id";
                    try (PreparedStatement cleanup = conn.prepareStatement(
                            "DELETE FROM " + table + " WHERE " + column + " = ?")) {
                        cleanup.setString(1, id);
                        cleanup.executeUpdate();
                    }
                }

                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM player_warps WHERE id = ?")) {
                    stmt.setString(1, id);
                    return stmt.executeUpdate() > 0;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete player warp", e);
            }
        });
    }

    private static PlayerWarp readPlayerWarp(ResultSet rows) throws SQLException {
        double cost = rows.getDouble("cost");
        Double resolvedCost = rows.wasNull() ? null : cost;

        int cooldown = rows.getInt("cooldown");
        Integer resolvedCooldown = rows.wasNull() ? null : cooldown;

        int delay = rows.getInt("delay");
        Integer resolvedDelay = rows.wasNull() ? null : delay;

        return new PlayerWarp(
                rows.getString("id"),
                rows.getString("name"),
                UUID.fromString(rows.getString("owner")),
                rows.getString("owner_name"),
                rows.getString("world"),
                rows.getDouble("x"),
                rows.getDouble("y"),
                rows.getDouble("z"),
                rows.getFloat("yaw"),
                rows.getFloat("pitch"),
                rows.getString("server"),
                rows.getString("description"),
                rows.getString("icon"),
                rows.getBoolean("use_owner_head"),
                rows.getString("category_id"),
                resolvedCost,
                resolvedCooldown,
                resolvedDelay,
                rows.getBoolean("locked"),
                rows.getString("password_hash"),
                rows.getString("password_salt"),
                rows.getBoolean("whitelist_enabled"),
                rows.getDouble("rating_average"),
                rows.getInt("rating_count"),
                rows.getInt("favourite_count"),
                rows.getLong("created_at"),
                rows.getLong("updated_at"),
                rows.getLong("last_used_at"));
    }

    @Override
    public CompletableFuture<List<PlayerWarpWhitelistEntry>> getPlayerWarpWhitelist(String warpId) {
        return supply(() -> {
            List<PlayerWarpWhitelistEntry> entries = new ArrayList<>();

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM player_warp_whitelist WHERE warp_id = ?")) {
                stmt.setString(1, warpId);

                try (ResultSet rows = stmt.executeQuery()) {
                    while (rows.next()) {
                        entries.add(new PlayerWarpWhitelistEntry(warpId, UUID.fromString(rows.getString("player")),
                                rows.getString("player_name"), rows.getLong("added_at")));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to read whitelist", e);
            }

            return entries;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerWarpWhitelistEntry(PlayerWarpWhitelistEntry entry) {
        return run(() -> {
            String query = replaceInto("player_warp_whitelist", "warp_id", "player", "player_name", "added_at");

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, entry.warpId());
                stmt.setString(2, entry.player().toString());
                stmt.setString(3, entry.playerName());
                stmt.setLong(4, entry.addedAt());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save whitelist entry", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> removePlayerWarpWhitelistEntry(String warpId, UUID player) {
        return supply(() -> {
            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM player_warp_whitelist WHERE warp_id = ? AND player = ?")) {
                stmt.setString(1, warpId);
                stmt.setString(2, player.toString());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to remove whitelist entry", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<PlayerWarpBan>> getPlayerWarpBans(String warpId) {
        return supply(() -> {
            List<PlayerWarpBan> bans = new ArrayList<>();

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM player_warp_bans WHERE warp_id = ?")) {
                stmt.setString(1, warpId);

                try (ResultSet rows = stmt.executeQuery()) {
                    while (rows.next()) {
                        bans.add(new PlayerWarpBan(warpId, UUID.fromString(rows.getString("player")),
                                rows.getString("player_name"), rows.getString("banned_by"),
                                rows.getString("banned_by_name"), rows.getString("reason"),
                                rows.getLong("banned_at"), rows.getLong("expires_at")));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to read bans", e);
            }

            return bans;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerWarpBan(PlayerWarpBan ban) {
        return run(() -> {
            String query = replaceInto("player_warp_bans", "warp_id", "player", "player_name", "banned_by",
                    "banned_by_name", "reason", "banned_at", "expires_at");

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, ban.warpId());
                stmt.setString(2, ban.player().toString());
                stmt.setString(3, ban.playerName());
                stmt.setString(4, ban.bannedBy());
                stmt.setString(5, ban.bannedByName());
                stmt.setString(6, ban.reason());
                stmt.setLong(7, ban.bannedAt());
                stmt.setLong(8, ban.expiresAt());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save ban", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> removePlayerWarpBan(String warpId, UUID player) {
        return supply(() -> {
            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM player_warp_bans WHERE warp_id = ? AND player = ?")) {
                stmt.setString(1, warpId);
                stmt.setString(2, player.toString());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to remove ban", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<PlayerWarpManagerEntry>> getPlayerWarpManagers(String warpId) {
        return supply(() -> {
            List<PlayerWarpManagerEntry> entries = new ArrayList<>();

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM player_warp_managers WHERE warp_id = ?")) {
                stmt.setString(1, warpId);

                try (ResultSet rows = stmt.executeQuery()) {
                    while (rows.next()) {
                        entries.add(new PlayerWarpManagerEntry(warpId, UUID.fromString(rows.getString("player")),
                                rows.getString("player_name"), rows.getLong("added_at")));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to read managers", e);
            }

            return entries;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerWarpManagerEntry(PlayerWarpManagerEntry entry) {
        return run(() -> {
            String query = replaceInto("player_warp_managers", "warp_id", "player", "player_name", "added_at");

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, entry.warpId());
                stmt.setString(2, entry.player().toString());
                stmt.setString(3, entry.playerName());
                stmt.setLong(4, entry.addedAt());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save manager entry", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> removePlayerWarpManagerEntry(String warpId, UUID player) {
        return supply(() -> {
            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM player_warp_managers WHERE warp_id = ? AND player = ?")) {
                stmt.setString(1, warpId);
                stmt.setString(2, player.toString());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to remove manager entry", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<PlayerWarpRating>> getPlayerWarpRatings(String warpId) {
        return supply(() -> {
            List<PlayerWarpRating> ratings = new ArrayList<>();

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM player_warp_ratings WHERE warp_id = ?")) {
                stmt.setString(1, warpId);

                try (ResultSet rows = stmt.executeQuery()) {
                    while (rows.next()) {
                        ratings.add(new PlayerWarpRating(warpId, UUID.fromString(rows.getString("player")),
                                rows.getString("player_name"), rows.getInt("stars"), rows.getString("comment"),
                                rows.getLong("created_at"), rows.getLong("updated_at")));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to read ratings", e);
            }

            return ratings;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerWarpRating(PlayerWarpRating rating) {
        return run(() -> {
            String query = replaceInto("player_warp_ratings", "warp_id", "player", "player_name", "stars",
                    "comment", "created_at", "updated_at");

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, rating.warpId());
                stmt.setString(2, rating.player().toString());
                stmt.setString(3, rating.playerName());
                stmt.setInt(4, rating.stars());
                stmt.setString(5, rating.comment());
                stmt.setLong(6, rating.createdAt());
                stmt.setLong(7, rating.updatedAt());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save rating", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<PlayerWarpFavourite>> getPlayerWarpFavourites(UUID player) {
        return supply(() -> {
            List<PlayerWarpFavourite> favourites = new ArrayList<>();

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM player_warp_favourites WHERE player = ?")) {
                stmt.setString(1, player.toString());

                try (ResultSet rows = stmt.executeQuery()) {
                    while (rows.next()) {
                        favourites.add(new PlayerWarpFavourite(player, rows.getString("warp_id"),
                                rows.getLong("added_at")));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to read favourites", e);
            }

            return favourites;
        });
    }

    @Override
    public CompletableFuture<Void> addPlayerWarpFavourite(PlayerWarpFavourite favourite) {
        return run(() -> {
            String query = replaceInto("player_warp_favourites", "player", "warp_id", "added_at");

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, favourite.player().toString());
                stmt.setString(2, favourite.warpId());
                stmt.setLong(3, favourite.addedAt());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to add favourite", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> removePlayerWarpFavourite(UUID player, String warpId) {
        return supply(() -> {
            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM player_warp_favourites WHERE player = ? AND warp_id = ?")) {
                stmt.setString(1, player.toString());
                stmt.setString(2, warpId);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to remove favourite", e);
            }
        });
    }

    @Override
    public CompletableFuture<List<PlayerWarpSponsoredSlot>> getSponsoredSlots() {
        return supply(() -> {
            List<PlayerWarpSponsoredSlot> slots = new ArrayList<>();

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM player_warp_sponsored_slots");
                 ResultSet rows = stmt.executeQuery()) {
                while (rows.next()) {
                    slots.add(new PlayerWarpSponsoredSlot(
                            rows.getInt("slot_index"),
                            rows.getString("warp_id"),
                            UUID.fromString(rows.getString("owner")),
                            rows.getString("owner_name"),
                            rows.getLong("started_at"),
                            rows.getLong("expires_at"),
                            rows.getDouble("price_paid")));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to read sponsored slots", e);
            }

            return slots;
        });
    }

    @Override
    public CompletableFuture<Void> saveSponsoredSlot(PlayerWarpSponsoredSlot slot) {
        return run(() -> {
            String query = replaceInto("player_warp_sponsored_slots", "slot_index", "warp_id", "owner",
                    "owner_name", "started_at", "expires_at", "price_paid");

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, slot.slotIndex());
                stmt.setString(2, slot.warpId());
                stmt.setString(3, slot.owner().toString());
                stmt.setString(4, slot.ownerName());
                stmt.setLong(5, slot.startedAt());
                stmt.setLong(6, slot.expiresAt());
                stmt.setDouble(7, slot.pricePaid());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save sponsored slot", e);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> clearSponsoredSlot(int slotIndex) {
        return supply(() -> {
            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "DELETE FROM player_warp_sponsored_slots WHERE slot_index = ?")) {
                stmt.setInt(1, slotIndex);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to clear sponsored slot", e);
            }
        });
    }

    @Override
    public CompletableFuture<BackLocation> getBackLocation(UUID owner) {
        return supply(() -> {
            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM back_locations WHERE owner = ?")) {
                stmt.setString(1, owner.toString());

                try (ResultSet rows = stmt.executeQuery()) {
                    if (!rows.next()) {
                        return null;
                    }
                    return new BackLocation(owner, rows.getString("world"), rows.getDouble("x"),
                            rows.getDouble("y"), rows.getDouble("z"), rows.getFloat("yaw"), rows.getFloat("pitch"),
                            rows.getString("server"), rows.getLong("saved_at"));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to read back location", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveBackLocation(BackLocation location) {
        return run(() -> {
            String query = replaceInto("back_locations", "owner", "world", "x", "y", "z", "yaw", "pitch",
                    "server", "saved_at");

            try (Connection conn = connection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, location.getOwner().toString());
                stmt.setString(2, location.getWorld());
                stmt.setDouble(3, location.getX());
                stmt.setDouble(4, location.getY());
                stmt.setDouble(5, location.getZ());
                stmt.setFloat(6, location.getYaw());
                stmt.setFloat(7, location.getPitch());
                stmt.setString(8, location.getServer());
                stmt.setLong(9, location.getSavedAt());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save back location", e);
            }
        });
    }

    /** Fills the parameters of a statement. */
    @FunctionalInterface
    protected interface StatementBinder {
        /**
         * Fills the statement.
         */
        void bind(PreparedStatement statement) throws SQLException;
    }
}

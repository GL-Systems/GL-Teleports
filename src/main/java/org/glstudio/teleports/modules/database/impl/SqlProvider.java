package org.glstudio.teleports.modules.database.impl;

import org.glstudio.teleports.GTeleports;
import org.glstudio.teleports.modules.model.DestinationLock;
import org.glstudio.teleports.modules.model.HomeShare;
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

public abstract class SqlProvider extends AbstractProvider {
    protected SqlProvider(GTeleports plugin) {
        super(plugin);
    }

    protected abstract Connection connection() throws SQLException;

    protected abstract String replaceInto(String table, String... columns);

    protected abstract String autoIncrementId();

    protected String tableOptions() {
        return "";
    }

    protected String currentServerName() {
        return plugin.getMultiServerSettings() == null
                ? "unnamed-server"
                : plugin.getMultiServerSettings().getServerName();
    }

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
        }

        createIndexQuietly(conn, "idx_log_player", "teleport_log", "player");
        createIndexQuietly(conn, "idx_log_destination", "teleport_log", "destination");
        createIndexQuietly(conn, "idx_log_created", "teleport_log", "created_at");
        createIndexQuietly(conn, "idx_shares_invitee", "home_shares", "invitee");
    }

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

    @FunctionalInterface
    protected interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}

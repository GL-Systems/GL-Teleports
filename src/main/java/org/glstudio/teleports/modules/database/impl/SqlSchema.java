package org.glstudio.teleports.modules.database.impl;

import org.glstudio.nexus.utils.LoggerUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class SqlSchema {
    static final int CURRENT_VERSION = 1;

    private SqlSchema() {
    }

    static void ensureVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version ("
                    + "id INT PRIMARY KEY, version INT NOT NULL, updated_at BIGINT NOT NULL)");
        }
    }

    static int readVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT version FROM schema_version WHERE id = 1")) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    static void writeVersion(Connection connection, String upsert, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setInt(1, version);
            statement.setLong(2, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    static void createIndexes(Connection connection) throws SQLException {
        createIndex(connection, "idx_homes_server", "homes", "server");
        createIndex(connection, "idx_warps_server", "warps", "server");
        createIndex(connection, "idx_players_name", "players", "name");
    }

    private static void createIndex(Connection connection, String index, String table, String column)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX " + index + " ON " + table + " (" + column + ")");
        } catch (SQLException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();

            if (!message.contains("duplicate") && !message.contains("already exists")
                    && !message.contains("exists")) {
                throw e;
            }
        }
    }

    static void addColumnIfMissing(Connection connection, String table, String column, String definition)
            throws SQLException {
        if (hasColumn(connection, table, column)) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            LoggerUtils.logInfo("Migrated table '" + table + "': added column '" + column + "'.");
        }
    }

    static void backfillServer(Connection connection, String table, String server) throws SQLException {
        String sql = "UPDATE " + table + " SET server = ? WHERE server IS NULL";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, server);
            int updated = statement.executeUpdate();

            if (updated > 0) {
                LoggerUtils.logInfo("Migrated table '" + table + "': assigned " + updated
                        + " row(s) with no server to '" + server + "'.");
            }
        }
    }

    static void migrateLegacySpawn(Connection connection, String server) throws SQLException {
        if (!hasTable(connection, "spawn") || countRows(connection, "spawns") > 0) {
            return;
        }

        String select = "SELECT world, x, y, z, yaw, pitch FROM spawn WHERE id = 1";
        String insert = "INSERT INTO spawns (server, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(select)) {
            if (!rows.next()) {
                return;
            }

            try (PreparedStatement copy = connection.prepareStatement(insert)) {
                copy.setString(1, server);
                copy.setString(2, rows.getString("world"));
                copy.setDouble(3, rows.getDouble("x"));
                copy.setDouble(4, rows.getDouble("y"));
                copy.setDouble(5, rows.getDouble("z"));
                copy.setFloat(6, rows.getFloat("yaw"));
                copy.setFloat(7, rows.getFloat("pitch"));
                copy.executeUpdate();
            }

            LoggerUtils.logInfo("Migrated the existing spawn to server '" + server + "'.");
        }
    }

    static void clearOtherSpawns(Connection connection, String server) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("DELETE FROM spawns WHERE server <> ?")) {
            statement.setString(1, server);
            statement.executeUpdate();
        }
    }

    private static int countRows(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    private static boolean hasTable(Connection connection, String table) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();

        for (String candidate : new String[]{table, table.toUpperCase()}) {
            try (ResultSet tables = metaData.getTables(connection.getCatalog(), null, candidate, null)) {
                if (tables.next()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();

        for (String candidate : new String[]{table, table.toUpperCase()}) {
            try (ResultSet columns = metaData.getColumns(connection.getCatalog(), null, candidate, null)) {
                while (columns.next()) {
                    if (column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

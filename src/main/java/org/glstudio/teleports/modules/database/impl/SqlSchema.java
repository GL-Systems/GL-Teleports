package org.glstudio.teleports.modules.database.impl;

import org.glstudio.nexus.utils.LoggerUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class SqlSchema {

    private SqlSchema() {
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

    private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();

        for (String candidate : new String[]{table, table.toUpperCase()}) {
            try (ResultSet columns = metaData.getColumns(null, null, candidate, null)) {
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

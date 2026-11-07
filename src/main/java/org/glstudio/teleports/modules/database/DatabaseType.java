package org.glstudio.teleports.modules.database;

import lombok.Getter;

/**
 * The supported storage backends.
 */
@Getter
public enum DatabaseType {
    /**
     * Yaml.
     */
    YAML("yaml"),
    /**
     * Sqlite.
     */
    SQLITE("sqlite"),
    /**
     * H2.
     */
    H2("h2"),
    /**
     * Mysql.
     */
    MYSQL("mysql"),
    /**
     * Mongo.
     */
    MONGO("mongo");

    private final String name;

    DatabaseType(String name) {
        this.name = name;
    }

    /**
     * Tells whether several servers can share this backend.
     */
    public boolean isShared() {
        return this == MYSQL || this == MONGO;
    }

    /**
     * Finds a backend by name.
     */
    public static DatabaseType fromString(String type) {
        for (DatabaseType dbType : values()) {
            if (dbType.name.equalsIgnoreCase(type)) {
                return dbType;
            }
        }
        return YAML;
    }
}

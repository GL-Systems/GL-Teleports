package org.glstudio.teleports.modules.database;

import lombok.Getter;

@Getter
public enum DatabaseType {
    YAML("yaml"),
    SQLITE("sqlite"),
    H2("h2"),
    MYSQL("mysql"),
    MONGO("mongo");

    private final String name;

    DatabaseType(String name) {
        this.name = name;
    }

    public static DatabaseType fromString(String type) {
        for (DatabaseType dbType : values()) {
            if (dbType.name.equalsIgnoreCase(type)) {
                return dbType;
            }
        }
        return YAML;
    }
}

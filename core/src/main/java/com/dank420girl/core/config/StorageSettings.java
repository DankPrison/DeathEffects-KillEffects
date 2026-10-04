package com.dank420girl.core.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * The storage section of config.yml.
 */
public final class StorageSettings {
    /** Where player selections are kept. */
    public enum Type {
        YAML, SQLITE, MYSQL
    }

    private final Type type;
    private final String table;
    private final String sqliteFile;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String properties;

    StorageSettings(Type type, String table, String sqliteFile, String host, int port, String database,
                    String username, String password, String properties) {
        this.type = type;
        this.table = table;
        this.sqliteFile = sqliteFile;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.properties = properties;
    }

    static StorageSettings read(ConfigurationSection section, String defaultTable, Problems problems) {
        OptionReader o = OptionReader.of(section, "storage", problems);
        String typeName = o.string("type", "YAML").trim().toUpperCase(Locale.ROOT);
        Type type;
        if (typeName.equals("MARIADB")) {
            type = Type.MYSQL;
        } else {
            type = o.choice("type", Type.class, Type.YAML);
        }
        String table = o.string("table", defaultTable).trim();
        if (!table.matches("[A-Za-z0-9_]{1,64}")) {
            o.warn("'table' may only contain letters, digits and '_'; using " + defaultTable);
            table = defaultTable;
        }
        ConfigurationSection sqlite = OptionReader.section(section, "sqlite", "storage.sqlite", problems);
        ConfigurationSection mysql = OptionReader.section(section, "mysql", "storage.mysql", problems);
        o.markRead("sqlite", "mysql");
        o.reportUnused();
        OptionReader s = OptionReader.of(sqlite, "storage.sqlite", problems);
        OptionReader m = OptionReader.of(mysql, "storage.mysql", problems);
        StorageSettings settings = new StorageSettings(type, table,
                s.string("file", "players.db"),
                m.string("host", "localhost"),
                m.integer("port", 3306, 1, 65535),
                m.string("database", "minecraft"),
                m.string("username", "root"),
                m.string("password", ""),
                m.string("properties", "useSSL=false&characterEncoding=utf8"));
        s.reportUnused();
        m.reportUnused();
        return settings;
    }

    public Type type() {
        return type;
    }

    public String table() {
        return table;
    }

    public String sqliteFile() {
        return sqliteFile;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String database() {
        return database;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String properties() {
        return properties;
    }

    /** True if both settings describe the same storage, so a reload can keep the open connection. */
    public boolean sameAs(StorageSettings other) {
        return other != null && type == other.type && table.equals(other.table) && sqliteFile.equals(other.sqliteFile)
                && host.equals(other.host) && port == other.port && database.equals(other.database)
                && username.equals(other.username) && password.equals(other.password) && properties.equals(other.properties);
    }
}

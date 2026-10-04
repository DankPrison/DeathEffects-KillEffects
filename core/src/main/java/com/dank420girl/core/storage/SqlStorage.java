package com.dank420girl.core.storage;

import com.dank420girl.core.config.StorageSettings;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Selections in SQLite or MySQL/MariaDB, using the JDBC drivers that ship with the server.
 * Each plugin has its own table, so both can share one database.
 */
final class SqlStorage implements Storage {
    /** The table used by the old versions of both plugins. */
    private static final String LEGACY_TABLE = "currentEffects";
    private static final String LEGACY_CLEARED = "%&#@$";

    private final StorageSettings settings;
    private final File dataFolder;
    private final Logger logger;
    private final Predicate<String> isKnownEffect;
    private final boolean mysql;
    private final String table;
    private final String metaTable;
    private Connection connection;

    SqlStorage(StorageSettings settings, File dataFolder, Logger logger, Predicate<String> isKnownEffect) {
        this.settings = settings;
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.isKnownEffect = isKnownEffect;
        this.mysql = settings.type() == StorageSettings.Type.MYSQL;
        this.table = settings.table();
        this.metaTable = settings.table() + "_meta";
    }

    @Override
    public String describe() {
        if (mysql) {
            return "MySQL (" + settings.host() + ":" + settings.port() + "/" + settings.database() + ", table " + table + ")";
        }
        return "SQLite (" + settings.sqliteFile() + ", table " + table + ")";
    }

    @Override
    public void open() throws Exception {
        connection = connect();
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "uuid CHAR(36) NOT NULL PRIMARY KEY, "
                    + "name VARCHAR(64), "
                    + "effect VARCHAR(64) NOT NULL, "
                    + "updated BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + metaTable + " ("
                    + "meta_key VARCHAR(64) NOT NULL PRIMARY KEY, "
                    + "meta_value VARCHAR(255))");
        }
        importLegacyTable();
        importYamlFile();
    }

    private Connection connect() throws Exception {
        if (mysql) {
            String driver = loadDriver("com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver", "org.mariadb.jdbc.Driver");
            // MariaDB's driver only accepts its own URL scheme since version 3.
            String scheme = driver.startsWith("org.mariadb.") ? "jdbc:mariadb://" : "jdbc:mysql://";
            String url = scheme + settings.host() + ":" + settings.port() + "/" + settings.database();
            String properties = settings.properties().trim();
            if (!properties.isEmpty()) {
                url += (properties.startsWith("?") ? "" : "?") + properties;
            }
            Properties info = new Properties();
            info.setProperty("user", settings.username());
            info.setProperty("password", settings.password());
            DriverManager.setLoginTimeout(10);
            return DriverManager.getConnection(url, info);
        }
        loadDriver("org.sqlite.JDBC");
        File file = new File(settings.sqliteFile());
        if (!file.isAbsolute()) {
            file = new File(dataFolder, settings.sqliteFile());
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new SQLException("Cannot create the folder " + parent);
        }
        return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
    }

    /** Loads the first driver the server has and returns its class name. */
    private static String loadDriver(String... names) throws ClassNotFoundException {
        for (String name : names) {
            try {
                Class.forName(name);
                return name;
            } catch (ClassNotFoundException ignored) {
                // try the next driver
            }
        }
        throw new ClassNotFoundException("No JDBC driver found (" + String.join(", ", names) + ")");
    }

    /** Copies the selections of the old plugin versions, once. */
    private void importLegacyTable() {
        try {
            if ("true".equals(meta("legacy_imported"))) {
                return;
            }
            if (!legacyTableExists()) {
                setMeta("legacy_imported", "true");
                return;
            }
            int imported = 0;
            String insert = mysql
                    ? "INSERT IGNORE INTO " + table + " (uuid, effect, updated) VALUES (?, ?, ?)"
                    : "INSERT OR IGNORE INTO " + table + " (uuid, effect, updated) VALUES (?, ?, ?)";
            // An error here leaves the import unfinished; it is repeated on the next start (rows that were
            // copied already are skipped).
            try (Statement select = connection.createStatement();
                 ResultSet rows = select.executeQuery("SELECT uuid, playerEffect FROM " + LEGACY_TABLE);
                 PreparedStatement statement = connection.prepareStatement(insert)) {
                while (rows.next()) {
                    String uuid = rows.getString(1);
                    String effect = rows.getString(2);
                    if (uuid == null || effect == null || effect.equals(LEGACY_CLEARED)) {
                        continue;
                    }
                    effect = effect.toLowerCase(java.util.Locale.ROOT);
                    try {
                        UUID.fromString(uuid);
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    if (!isKnownEffect.test(effect)) {
                        continue;
                    }
                    statement.setString(1, uuid);
                    statement.setString(2, effect);
                    statement.setLong(3, System.currentTimeMillis());
                    imported += statement.executeUpdate();
                }
            }
            setMeta("legacy_imported", "true");
            if (imported > 0) {
                logger.info("Imported " + imported + " effect selections from the old '" + LEGACY_TABLE + "' table.");
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not import the selections of the old plugin version; trying again on the next start", e);
        }
    }

    private boolean legacyTableExists() {
        try (Statement statement = connection.createStatement()) {
            statement.executeQuery("SELECT uuid, playerEffect FROM " + LEGACY_TABLE + " WHERE 1 = 0").close();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * When a server switches from players.yml to a database, copies players.yml into it once and
     * renames the file to players.yml.imported.
     */
    private void importYamlFile() {
        File file = new File(dataFolder, "players.yml");
        if (!file.isFile()) {
            return;
        }
        YamlStorage yaml = new YamlStorage(file, logger, false);
        // A players.yml that stood in for the database remembers cleared selections; they are cleared here too.
        yaml.rememberClears();
        try {
            yaml.open();
        } catch (Exception e) {
            logger.log(Level.WARNING, "players.yml could not be read, so its selections were not copied to the database", e);
            return;
        }
        int copied = 0;
        try {
            for (java.util.Map.Entry<UUID, String[]> entry : yaml.entries().entrySet()) {
                String effect = entry.getValue()[1];
                save(entry.getKey(), entry.getValue()[0], effect.isEmpty() ? null : effect);
                copied++;
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not copy the selections of players.yml to the database; trying again on the next start", e);
            return;
        }
        File imported = new File(dataFolder, "players.yml.imported");
        for (int i = 2; imported.exists(); i++) {
            imported = new File(dataFolder, "players.yml.imported-" + i);
        }
        if (!file.renameTo(imported)) {
            logger.warning("Could not rename players.yml after copying it to the database");
        }
        logger.info("Copied " + copied + " effect selections from players.yml to the database; the file was renamed to "
                + imported.getName() + ".");
    }

    private String meta(String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT meta_value FROM " + metaTable + " WHERE meta_key = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private void setMeta(String key, String value) throws SQLException {
        String sql = mysql
                ? "INSERT INTO " + metaTable + " (meta_key, meta_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value)"
                : "INSERT OR REPLACE INTO " + metaTable + " (meta_key, meta_value) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    /** The open connection, reconnecting if the database closed it (MySQL does after hours of inactivity). */
    private Connection connection() throws Exception {
        boolean valid;
        try {
            valid = connection != null && !connection.isClosed() && connection.isValid(3);
        } catch (SQLException | AbstractMethodError e) {
            // Very old drivers do not implement isValid.
            valid = connection != null && !connection.isClosed();
        }
        if (!valid) {
            close();
            try {
                connection = connect();
            } catch (Exception e) {
                throw new StorageUnavailableException(describe() + " cannot be reached: " + e.getMessage(), e);
            }
        }
        return connection;
    }

    /** A statement can fail because the connection was lost; then the database counts as unreachable. */
    private Exception unavailableIfDisconnected(SQLException e) {
        boolean valid;
        try {
            valid = connection != null && connection.isValid(2);
        } catch (SQLException | AbstractMethodError ignored) {
            valid = true; // cannot tell, so it counts as an ordinary error
        }
        return valid ? e : new StorageUnavailableException(describe() + " cannot be reached: " + e.getMessage(), e);
    }

    @Override
    public String load(UUID player) throws Exception {
        try (PreparedStatement statement = connection().prepareStatement(
                "SELECT effect FROM " + table + " WHERE uuid = ?")) {
            statement.setString(1, player.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        } catch (SQLException e) {
            throw unavailableIfDisconnected(e);
        }
    }

    @Override
    public void save(UUID player, String name, String effect) throws Exception {
        Connection c = connection();
        try {
            if (effect == null) {
                try (PreparedStatement statement = c.prepareStatement("DELETE FROM " + table + " WHERE uuid = ?")) {
                    statement.setString(1, player.toString());
                    statement.executeUpdate();
                }
                return;
            }
            String sql = mysql
                    ? "INSERT INTO " + table + " (uuid, name, effect, updated) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE name = VALUES(name), effect = VALUES(effect), updated = VALUES(updated)"
                    : "INSERT OR REPLACE INTO " + table + " (uuid, name, effect, updated) VALUES (?, ?, ?, ?)";
            try (PreparedStatement statement = c.prepareStatement(sql)) {
                statement.setString(1, player.toString());
                statement.setString(2, name == null || name.length() <= 64 ? name : name.substring(0, 64));
                statement.setString(3, effect);
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw unavailableIfDisconnected(e);
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // closing anyway
            }
            connection = null;
        }
    }
}

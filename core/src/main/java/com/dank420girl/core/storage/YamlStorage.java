package com.dank420girl.core.storage;

import com.dank420girl.core.config.ConfigFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Selections in a YAML file (players.yml). The file is read once and written shortly after changes.
 */
final class YamlStorage implements Storage {
    private static final String HEADER = "Effect selections of each player, saved by the plugin.\n"
            + "Edit this file only while the server is stopped, or use the select and clear commands.";
    private static final String FALLBACK_HEADER = "Effect selections made while the database could not be reached.\n"
            + "They are copied to the database as soon as it can be reached again; then this file is deleted.";

    private final File file;
    private final Logger logger;
    /** True for players-fallback.yml. */
    private final boolean fallback;
    /**
     * Whether cleared selections are kept (with an empty effect), so they can be cleared in the configured
     * storage later. Always true for the fallback file.
     */
    private boolean rememberClears;
    private final Map<UUID, String[]> players = new LinkedHashMap<UUID, String[]>();
    private boolean dirty;

    YamlStorage(File file, Logger logger, boolean fallback) {
        this.file = file;
        this.logger = logger;
        this.fallback = fallback;
        this.rememberClears = fallback;
    }

    /** From now on cleared selections are kept, because this file stands in for another storage. */
    void rememberClears() {
        rememberClears = true;
    }

    File file() {
        return file;
    }

    @Override
    public String describe() {
        return "YAML (" + file.getName() + ")";
    }

    /**
     * Reads the file.
     *
     * @throws org.bukkit.configuration.InvalidConfigurationException if it is not valid YAML; nothing is
     *         written to the file in that case
     */
    @Override
    public void open() throws Exception {
        players.clear();
        dirty = false;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = ConfigFiles.parse(ConfigFiles.read(file), file.getName());
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                logger.warning(file.getName() + ": '" + key + "' is not a player UUID and is ignored");
                continue;
            }
            ConfigurationSection entry = section.getConfigurationSection(key);
            String effect;
            String name = null;
            if (entry != null) {
                effect = entry.getString("effect");
                name = entry.getString("name");
            } else {
                effect = section.getString(key);
            }
            if (effect != null && (rememberClears || !effect.isEmpty())) {
                players.put(uuid, new String[] {name, effect});
            }
        }
    }

    @Override
    public String load(UUID player) {
        String[] entry = players.get(player);
        return entry == null || entry[1].isEmpty() ? null : entry[1];
    }

    @Override
    public void save(UUID player, String name, String effect) {
        if (effect == null && !rememberClears) {
            if (players.remove(player) != null) {
                dirty = true;
            }
            return;
        }
        players.put(player, new String[] {name, effect == null ? "" : effect});
        dirty = true;
    }

    /** All entries: player to {name, effect}; the effect is empty for a cleared selection. */
    Map<UUID, String[]> entries() {
        return new LinkedHashMap<UUID, String[]>(players);
    }

    /** Writes the file if something changed. */
    void flush() {
        if (!dirty) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header(fallback ? FALLBACK_HEADER : HEADER);
        for (Map.Entry<UUID, String[]> entry : players.entrySet()) {
            String path = "players." + entry.getKey();
            if (entry.getValue()[0] != null) {
                yaml.set(path + ".name", entry.getValue()[0]);
            }
            yaml.set(path + ".effect", entry.getValue()[1]);
        }
        try {
            ConfigFiles.write(file, yaml.saveToString());
            dirty = false;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file.getName(), e);
        }
    }

    @Override
    public void close() {
        flush();
    }
}

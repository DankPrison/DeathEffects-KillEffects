package com.dank420girl.core.storage;

import com.dank420girl.core.config.ConfigFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Selections imported from the old plugin versions, which saved them by player name instead of
 * UUID. Each one is handed to the matching player the first time they join, then removed.
 */
public final class LegacySelections {
    private static final String HEADER = "Effect selections imported from the old version of the plugin, which saved\n"
            + "them by player name. Each entry moves to the player's account when a player with\n"
            + "that name joins. This file is deleted once it is empty.";

    /** Player names of Bedrock players (Floodgate) can contain dots, so paths are split at '/'. */
    private static final char SEPARATOR = '/';

    private final File file;
    private final Logger logger;
    private final Map<String, String[]> byLowerName = new LinkedHashMap<String, String[]>();

    public LegacySelections(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    /** Reads the file if it exists. */
    public synchronized void load() {
        byLowerName.clear();
        if (!file.exists()) {
            return;
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().pathSeparator(SEPARATOR);
            yaml.loadFromString(ConfigFiles.read(file));
            ConfigurationSection section = yaml.getConfigurationSection("players");
            if (section != null) {
                for (String name : section.getKeys(false)) {
                    String effect = section.getString(name);
                    if (effect != null && !effect.isEmpty()) {
                        byLowerName.put(name.toLowerCase(Locale.ROOT), new String[] {name, effect});
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not read " + file.getName(), e);
        }
    }

    /** Adds selections (player name to effect id) and writes the file. */
    public synchronized void addAll(Map<String, String> selections) {
        for (Map.Entry<String, String> entry : selections.entrySet()) {
            byLowerName.put(entry.getKey().toLowerCase(Locale.ROOT), new String[] {entry.getKey(), entry.getValue()});
        }
        save();
    }

    public synchronized boolean isEmpty() {
        return byLowerName.isEmpty();
    }

    /** Removes and returns the selection saved for the player name, or {@code null}. */
    public synchronized String take(String playerName) {
        String[] entry = byLowerName.remove(playerName.toLowerCase(Locale.ROOT));
        if (entry == null) {
            return null;
        }
        save();
        return entry[1];
    }

    private void save() {
        try {
            if (byLowerName.isEmpty()) {
                if (file.exists() && !file.delete()) {
                    logger.warning("Could not delete " + file.getName());
                }
                return;
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().pathSeparator(SEPARATOR);
            yaml.options().header(HEADER);
            for (String[] entry : byLowerName.values()) {
                yaml.set("players" + SEPARATOR + entry[0], entry[1]);
            }
            ConfigFiles.write(file, yaml.saveToString());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not save " + file.getName(), e);
        }
    }
}

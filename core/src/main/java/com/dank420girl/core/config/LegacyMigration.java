package com.dank420girl.core.config;

import com.dank420girl.core.Perm;
import com.dank420girl.core.Variant;
import com.dank420girl.core.names.Names;
import com.dank420girl.core.platform.MinecraftVersion;
import com.dank420girl.core.storage.LegacySelections;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Converts the single config.yml of the old versions (0.x) into config.yml, messages.yml and
 * effects.yml. Only values that differ from the old defaults are carried over, so unchanged
 * settings get the new, improved defaults. The old file is kept as config-v1-backup.yml.
 */
public final class LegacyMigration {
    /** Old icons and sounds use the names of Minecraft 1.13. */
    private static final MinecraftVersion OLD_NAMES = MinecraftVersion.of(1, 13);
    private static final String CLEARED = "%&#@$";

    /** Old effect name keys (EffectNames.* and Holiday.*) to effect ids. */
    private static final Map<String, String> NAME_KEYS = new LinkedHashMap<String, String>();
    /** Old icon keys (EffectIcons.*) to effect ids where they differ from the lower-case key. */
    private static final Map<String, String> ICON_KEYS = new HashMap<String, String>();
    /** Default effect names of the old versions. */
    private static final Map<String, String> OLD_NAMES_DEFAULTS = new HashMap<String, String>();
    /** Default icons of the old versions. */
    private static final Map<String, String> OLD_ICONS_DEFAULTS = new HashMap<String, String>();

    static {
        String[] regular = {"Flame", "Rainbow", "Heart", "Firework", "Cloud", "Lightning", "Coin", "Potion", "Blood",
                "Note", "Flower", "Chess", "Tnt", "Treasure", "Water", "Lava", "Ender", "Warm", "Cold", "Pastel",
                "Fish", "Cookie", "Snow", "Smoke", "Meat", "Angry", "Happy", "Enchanted", "Sun", "Thunder", "Coal",
                "Iron", "Gold", "Diamond", "Emerald", "BloodHelix", "Nuke", "Circle", "Vortex", "Dna", "Disco",
                "Dragon", "Pentagon"};
        for (String key : regular) {
            NAME_KEYS.put("EffectNames." + key, key.toLowerCase(Locale.ROOT));
            ICON_KEYS.put(key, key.toLowerCase(Locale.ROOT));
        }
        String[][] holidays = {
                {"SaintPatricksDay", "stpatrick"}, {"ValentinesDay", "valentinesday"}, {"Easter", "easter"},
                {"Thanksgiving", "thanksgiving"}, {"NewYear", "newyear"}, {"Halloween", "halloween"},
                {"Christmas", "christmas"}, {"MothersDay", "mother"}, {"FathersDay", "father"},
                {"BlackFriday", "blackfriday"}};
        for (String[] holiday : holidays) {
            NAME_KEYS.put("Holiday." + holiday[0], holiday[1]);
        }
        String[][] holidayIcons = {
                {"FathersDay", "father"}, {"MothersDay", "mother"}, {"NewYears", "newyear"}, {"Christmas", "christmas"},
                {"Valentines", "valentinesday"}, {"Easter", "easter"}, {"StPatricks", "stpatrick"},
                {"ThanksGiving", "thanksgiving"}, {"Halloween", "halloween"}, {"BlackFriday", "blackfriday"}};
        for (String[] icon : holidayIcons) {
            ICON_KEYS.put(icon[0], icon[1]);
        }

        String[][] names = {
                {"Holiday.SaintPatricksDay", "&2Saint Patricks Day &aEffect"}, {"Holiday.ValentinesDay", "&dValentine's Day &aEffect"},
                {"Holiday.Easter", "&eE&da&bs&et&de&br &aEffect"}, {"Holiday.Thanksgiving", "&6Thanksgiving &aEffect"},
                {"Holiday.NewYear", "&cN&fe&cw &fY&ce&fa&cr &aEffect"}, {"Holiday.Halloween", "&8H&ea&8l&el&8o&ew&8e&ee&8n &aEffect"},
                {"Holiday.Christmas", "&2C&4h&fr&2i&4s&ft&2m&4a&fs &aEffect"}, {"Holiday.MothersDay", "&dMothers &5Day &aEffect"},
                {"Holiday.FathersDay", "&bFathers &3Day &aEffect"}, {"Holiday.BlackFriday", "&8Black Friday &aEffect"},
                {"EffectNames.Flame", "&eFlame &aEffect"}, {"EffectNames.Rainbow", "&cR&6a&ei&an&bb&3o&5w &aEffect"},
                {"EffectNames.Heart", "&dHeart &aEffect"}, {"EffectNames.Firework", "&cF&fi&cr&fe&cw&fo&cr&fk &aEffect"},
                {"EffectNames.Cloud", "&lCloud &r&aEffect"}, {"EffectNames.Lightning", "&eLightning &aEffect"},
                {"EffectNames.Coin", "&6C&eo&6i&en &aEffect"}, {"EffectNames.Potion", "&3Potion &aEffect"},
                {"EffectNames.Blood", "&4Blood &aEffect"}, {"EffectNames.Note", "&cNote &aEffect"},
                {"EffectNames.Flower", "&aF&el&ao&ew&ae&er &aEffect"}, {"EffectNames.Chess", "&8C&fh&8e&fs&8s &aEffect"},
                {"EffectNames.Tnt", "&cT&fN&cT &aEffect"}, {"EffectNames.Treasure", "&aT&er&7e&aa&es&7u&ar&ee &aEffect"},
                {"EffectNames.Water", "&9Water &aEffect"}, {"EffectNames.Lava", "&6Lava &aEffect"},
                {"EffectNames.Ender", "&5Ender &aEffect"}, {"EffectNames.Warm", "&6Warm &aEffect"},
                {"EffectNames.Cold", "&bC&fo&bl&fd &aEffect"}, {"EffectNames.Pastel", "&cP&fa&ds&ct&fe&dl &aEffect"},
                {"EffectNames.Fish", "&3Fish &aEffects"}, {"EffectNames.Cookie", "&6Cookie &aEffect"},
                {"EffectNames.Snow", "&f&lSnow &r&aEffect"}, {"EffectNames.Smoke", "&8Smoke &aEffect"},
                {"EffectNames.Meat", "&cM&de&ca&dt &aEffect"}, {"EffectNames.Angry", "&cAngry &aEffect"},
                {"EffectNames.Happy", "&2H&aa&2p&ap&2y &aEffect"}, {"EffectNames.Enchanted", "&r&f&k|||&r&fEnchanted&k|||&r &aEffect"},
                {"EffectNames.Sun", "&eSun &aEffect"}, {"EffectNames.Thunder", "&3T&eh&3u&en&3d&ee&3r &aEffect"},
                {"EffectNames.Coal", "&8Coal &aEffect"}, {"EffectNames.Iron", "&7Iron &aEffect"},
                {"EffectNames.Gold", "&eGold &aEffect"}, {"EffectNames.Diamond", "&bDiamond &aEffect"},
                {"EffectNames.Emerald", "&2Emerald &aEffect"}, {"EffectNames.BloodHelix", "&4&k|||&r&cBlood Helix&r&4&k|||&r &r&aEffect"},
                {"EffectNames.Nuke", "&eNuke &aEffect"}, {"EffectNames.Circle", "&eO &6Circle &eO &aEffect"},
                {"EffectNames.Vortex", "&d&nVortex&r&a Effect"}, {"EffectNames.Dna", "&b&nDna&r&a Effect"},
                {"EffectNames.Disco", "&a&nDisco&r&a Effect"}, {"EffectNames.Dragon", "&c&nDragon&r&a Effect"},
                {"EffectNames.Pentagon", "&4&nPentagon&r&a Effect"}, {"EffectNames.Clear", "&cClear Current Effect"}};
        for (String[] name : names) {
            OLD_NAMES_DEFAULTS.put(name[0], name[1]);
        }
        String[][] icons = {
                {"Flame", "BLAZE_POWDER"}, {"Rainbow", "MAGMA_CREAM"}, {"Heart", "ROSE_RED"}, {"Firework", "FIREWORK_ROCKET"},
                {"Cloud", "BLUE_WOOL"}, {"Lightning", "BLAZE_ROD"}, {"Coin", "LEGACY_DOUBLE_PLANT"}, {"Potion", "POTION"},
                {"Blood", "REDSTONE"}, {"Note", "JUKEBOX"}, {"Flower", "DANDELION"}, {"Chess", "QUARTZ_BLOCK"},
                {"Tnt", "GUNPOWDER"}, {"Treasure", "CHEST"}, {"Water", "WATER_BUCKET"}, {"Lava", "LAVA_BUCKET"},
                {"Ender", "ENDER_EYE"}, {"Warm", "RED_WOOL"}, {"Cold", "LIGHT_BLUE_WOOL"}, {"Pastel", "POTION"},
                {"Fish", "PUFFERFISH"}, {"Cookie", "COOKIE"}, {"Snow", "SNOWBALL"}, {"Smoke", "INK_SAC"},
                {"Meat", "BEEF"}, {"Angry", "NETHER_WART"}, {"Happy", "APPLE"}, {"Enchanted", "ENCHANTING_TABLE"},
                {"Sun", "FIREWORK_STAR"}, {"Thunder", "POTION"}, {"Coal", "COAL"}, {"Iron", "IRON_INGOT"},
                {"Gold", "GOLD_INGOT"}, {"Diamond", "DIAMOND"}, {"Emerald", "EMERALD"}, {"BloodHelix", "REDSTONE_BLOCK"},
                {"Nuke", "TNT"}, {"Circle", "FIRE_CHARGE"}, {"Vortex", "HOPPER"}, {"Dna", "REDSTONE_TORCH"},
                {"Disco", "MUSIC_DISC_MALL"}, {"Dragon", "DRAGON_EGG"}, {"Pentagon", "OAK_TRAPDOOR"},
                {"FathersDay", "CLOCK"}, {"MothersDay", "SUNFLOWER"}, {"NewYears", "FIREWORK_ROCKET"},
                {"Christmas", "NETHER_STAR"}, {"Valentines", "ROSE_RED"}, {"Easter", "EGG"}, {"StPatricks", "LILY_PAD"},
                {"ThanksGiving", "COOKED_CHICKEN"}, {"Halloween", "PUMPKIN"}, {"BlackFriday", "BLACK_WOOL"},
                {"Clear", "BARRIER"}};
        for (String[] icon : icons) {
            OLD_ICONS_DEFAULTS.put(icon[0], icon[1]);
        }
    }

    private final Variant variant;
    private final Logger logger;
    private final ConfigFiles files;
    private final Names names;
    private final LegacySelections legacySelections;

    public LegacyMigration(Variant variant, Logger logger, ConfigFiles files, Names names,
                           LegacySelections legacySelections) {
        this.variant = variant;
        this.logger = logger;
        this.files = files;
        this.names = names;
        this.legacySelections = legacySelections;
    }

    /** True if the configuration looks like one of the old versions. */
    static boolean isLegacy(YamlConfiguration config) {
        if (config.contains("config-version")) {
            return false;
        }
        return config.contains("EffectNames") || config.contains("EffectIcons") || config.contains("GUI")
                || config.contains("Holiday");
    }

    public void migrateIfNeeded() throws Exception {
        File folder = files.folder();
        File configFile = new File(folder, "config.yml");
        if (!configFile.exists()) {
            return;
        }
        YamlConfiguration old;
        try {
            old = ConfigFiles.parse(ConfigFiles.read(configFile), "config.yml");
        } catch (InvalidConfigurationException e) {
            return; // reported when the file is loaded
        }
        if (!isLegacy(old)) {
            return;
        }
        String pluginName = variant.name();
        logger.info("Found the configuration of an old " + pluginName + " version; converting it.");

        YamlText config = new YamlText(files.render(ConfigFiles.CONFIG_TEMPLATE, true));
        YamlText messages = new YamlText(files.render(ConfigFiles.MESSAGES_TEMPLATE, false));
        YamlText effects = new YamlText(files.render(ConfigFiles.EFFECTS_TEMPLATE, true));
        int carried = 0;

        // Settings
        String prefix = changed(old, "Settings.Prefix", "&b(" + pluginName + ")&r ");
        if (prefix != null) {
            carried += set(messages, "prefix", prefix);
        }
        String selected = changed(old, "Settings.Selected", "&r &6Selected.");
        if (selected != null) {
            carried += set(messages, "effect-selected", "{prefix}{effect}" + selected);
        }
        boolean restoreMessage = old.getBoolean("Settings.LoginMessageEnabled", true);
        if (!restoreMessage) {
            carried += set(messages, "effect-restored", "");
        }

        // Menu
        carried += setIfChanged(old, "GUI.Name", "&0&n" + pluginName, config, "menu.title");
        carried += setIfChanged(old, "GUI.Filler", " ", config, "menu.filler.name");
        carried += setItemIfChanged(old, "GUI.FillerItem", "GRAY_STAINED_GLASS_PANE", config, "menu.filler.item");
        carried += setIfChanged(old, "GUI.Next", "&a&lClick here for (Page 2)", config, "menu.buttons.next-page.name");
        carried += setItemIfChanged(old, "GUI.NextItem", "LIME_STAINED_GLASS", config, "menu.buttons.next-page.item");
        carried += setIfChanged(old, "GUI.Back", "&a&lClick here for (Page 1)", config, "menu.buttons.previous-page.name");
        carried += setItemIfChanged(old, "GUI.BackItem", "LIME_STAINED_GLASS", config, "menu.buttons.previous-page.item");
        carried += migrateSound(old, "GUI.SelectSound", "BLOCK_NOTE_BLOCK_PLING", 1, 2, config, "menu.sounds.select");
        carried += migrateSound(old, "GUI.NoIconSound", "ENTITY_ITEM_BREAK", 1, 1.1, config, "menu.sounds.locked");
        carried += setIfChanged(old, "EffectNames.Clear", "&cClear Current Effect", config, "menu.buttons.clear.name");
        carried += setItemIfChanged(old, "EffectIcons.Clear", "BARRIER", config, "menu.buttons.clear.item");

        // Storage
        if (old.getBoolean("MySQL.enabled", false)) {
            set(config, "storage.type", "MYSQL");
            set(config, "storage.mysql.host", old.getString("MySQL.host", "localhost"));
            config.set("storage.mysql.port", parsePort(old.getString("MySQL.port", "3306")));
            set(config, "storage.mysql.database", old.getString("MySQL.database", "minecraft"));
            set(config, "storage.mysql.username", old.getString("MySQL.username", "root"));
            set(config, "storage.mysql.password", old.getString("MySQL.password", ""));
            carried++;
        }

        // Messages
        carried += setIfChanged(old, "Messages.NoPermission", "&cYou dont have permission for this effect.", messages, "no-permission-effect");
        // The new versions have one message for both; a changed menu message wins over a changed reload message.
        int noPermission = setIfChanged(old, "Messages.NoPermissionGUI", "&cYou dont have permission to open the gui.", messages, "no-permission");
        if (noPermission == 0) {
            noPermission = setIfChanged(old, "Messages.NoPermissionReload", "&cYou dont have permission to reload this plugin.",
                    messages, "no-permission");
        }
        carried += noPermission;
        if (restoreMessage) {
            carried += setIfChanged(old, "Messages.EffectLoaded", "&6Your previous selected &aEffect &6has been restored.", messages, "effect-restored");
        }
        carried += setIfChanged(old, "Messages.ClearEffect", "&aCleared all effects.", messages, "effect-cleared");
        carried += setIfChanged(old, "Messages.Reloaded", "&a" + pluginName + " has been reloaded.", messages, "reloaded");

        // Effect names and icons
        for (Map.Entry<String, String> entry : NAME_KEYS.entrySet()) {
            String value = changed(old, entry.getKey(), OLD_NAMES_DEFAULTS.get(entry.getKey()));
            if (value != null) {
                carried += set(effects, "effects." + entry.getValue() + ".name", value);
            }
        }
        for (Map.Entry<String, String> entry : ICON_KEYS.entrySet()) {
            carried += setItemIfChanged(old, "EffectIcons." + entry.getKey(), OLD_ICONS_DEFAULTS.get(entry.getKey()),
                    effects, "effects." + entry.getValue() + ".icon");
        }

        // Back up the old file, then write the new ones.
        File backup = new File(folder, "config-v1-backup.yml");
        for (int i = 2; backup.exists(); i++) {
            backup = new File(folder, "config-v1-backup-" + i + ".yml");
        }
        Files.move(configFile.toPath(), backup.toPath());
        ConfigFiles.write(configFile, config.toString());
        writeIfMissing(new File(folder, "messages.yml"), messages.toString());
        writeIfMissing(new File(folder, "effects.yml"), effects.toString());

        // Selections were saved by player name in the old config.
        Map<String, String> selections = new LinkedHashMap<String, String>();
        ConfigurationSection data = old.getConfigurationSection("Data");
        if (data != null) {
            for (String name : data.getKeys(false)) {
                String effect = data.getString(name);
                if (effect == null || effect.equals(CLEARED) || name.equals("examplePlayer")) {
                    continue;
                }
                selections.put(name, effect.toLowerCase(Locale.ROOT));
            }
        }
        if (!selections.isEmpty()) {
            legacySelections.addAll(selections);
        }
        logger.info("Converted the old configuration (" + carried + " customised settings carried over, "
                + selections.size() + " saved selections imported). The old file was kept as " + backup.getName() + ".");
        // In the old versions the wildcard only granted the effects; players who got it for that reason
        // would now get the admin commands as well.
        logger.warning("Please check your permissions: " + variant.permission(Perm.ALL) + " now grants everything, "
                + "including the admin commands. Give players " + variant.permission(Perm.ALL_EFFECTS)
                + " instead to let them use every effect.");
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 3306;
        }
    }

    /** The old value if it was changed from the default, otherwise {@code null}. */
    private static String changed(YamlConfiguration old, String path, String oldDefault) {
        if (!old.contains(path) || old.isConfigurationSection(path)) {
            return null;
        }
        String value = old.getString(path);
        if (value == null || value.equals(oldDefault)) {
            return null;
        }
        return value;
    }

    private int setIfChanged(YamlConfiguration old, String path, String oldDefault, YamlText target, String targetPath) {
        String value = changed(old, path, oldDefault);
        return value == null ? 0 : set(target, targetPath, value);
    }

    private int setItemIfChanged(YamlConfiguration old, String path, String oldDefault, YamlText target, String targetPath) {
        String value = changed(old, path, oldDefault);
        if (value == null || value.equalsIgnoreCase(oldDefault)) {
            return 0;
        }
        String translated = names.translateItem(value, OLD_NAMES);
        return setName(target, targetPath, translated == null ? value : translated);
    }

    private int migrateSound(YamlConfiguration old, String path, String oldDefault, double oldVolume, double oldPitch,
                             YamlText target, String targetPath) {
        int carried = 0;
        if (!old.getBoolean(path + "Enabled", true)) {
            return set(target, targetPath + ".sound", "");
        }
        String sound = changed(old, path, oldDefault);
        if (sound != null) {
            String translated = names.translateSound(sound);
            carried += setName(target, targetPath + ".sound", translated == null ? sound : translated);
        }
        if (old.contains(path + "Volume") && old.getDouble(path + "Volume") != oldVolume) {
            target.set(targetPath + ".volume", old.getDouble(path + "Volume"));
            carried++;
        }
        if (old.contains(path + "Pitch") && old.getDouble(path + "Pitch") != oldPitch) {
            target.set(targetPath + ".pitch", old.getDouble(path + "Pitch"));
            carried++;
        }
        return carried;
    }

    private int set(YamlText target, String path, String value) {
        if (target.set(path, value)) {
            return 1;
        }
        logger.warning("Could not carry over the old value of " + path);
        return 0;
    }

    private int setName(YamlText target, String path, String value) {
        if (target.setName(path, value)) {
            return 1;
        }
        logger.warning("Could not carry over the old value of " + path);
        return 0;
    }

    private static void writeIfMissing(File file, String text) throws IOException {
        if (!file.exists()) {
            ConfigFiles.write(file, text);
        }
    }
}

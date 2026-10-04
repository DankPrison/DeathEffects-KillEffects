package com.dank420girl.core.config;

import com.dank420girl.core.effect.LayerParser;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.permissions.PermissionDefault;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The general settings of config.yml shared by both plugins. Plugin specific sections (triggers,
 * compatibility) are read by the plugins themselves from {@link #root()}.
 */
public final class Settings {
    /** The top-level keys of config.yml; anything else is reported as a typo. */
    private static final Set<String> SECTIONS = new HashSet<String>(Arrays.asList("config-version",
            ConfigFiles.NAMES_VERSION_KEY, "settings", "triggers", "compatibility", "previews", "menu", "performance",
            "storage"));

    private final ConfigurationSection root;
    private final Set<String> enabledWorlds;
    private final Set<String> disabledWorlds;
    private final boolean requirePermission;
    private final String defaultEffect;
    private final PermissionDefault effectPermissionDefault;
    private final boolean debug;

    private final boolean previewsEnabled;
    private final int previewCooldown;
    private final double previewDistance;
    private final boolean previewPrivate;
    private final boolean previewLocked;

    private final double viewDistance;
    private final int maxAnimations;
    private final double particleMultiplier;

    private final MenuSettings menu;
    private final StorageSettings storage;

    public Settings(ConfigurationSection root, LayerParser parser, String defaultTable, Problems problems) {
        this.root = root;
        for (String key : root.getKeys(false)) {
            if (!SECTIONS.contains(key)) {
                problems.warn("", "unknown option '" + key + "' (ignored)");
            }
        }
        OptionReader settings = OptionReader.of(OptionReader.section(root, "settings", "settings", problems), "settings", problems);
        enabledWorlds = lowerCase(settings.strings("enabled-worlds"));
        disabledWorlds = lowerCase(settings.strings("disabled-worlds"));
        requirePermission = settings.bool("require-permission", true);
        defaultEffect = settings.string("default-effect", "").trim().toLowerCase(Locale.ROOT);
        String permissionDefault = settings.string("effect-permission-default", "op");
        PermissionDefault parsed = PermissionDefault.getByName(permissionDefault);
        if (parsed == null) {
            settings.warn("'effect-permission-default' should be op, true, false or not op; using op");
            parsed = PermissionDefault.OP;
        }
        effectPermissionDefault = parsed;
        debug = settings.bool("debug", false);
        settings.reportUnused();

        OptionReader previews = OptionReader.of(OptionReader.section(root, "previews", "previews", problems), "previews", problems);
        previewsEnabled = previews.bool("enabled", true);
        previewCooldown = previews.integer("cooldown", 5, 0, 3600);
        previewDistance = previews.number("distance", 3, 0, 20);
        previewPrivate = previews.bool("private", false);
        previewLocked = previews.bool("locked-effects", true);
        previews.reportUnused();

        OptionReader performance = OptionReader.of(OptionReader.section(root, "performance", "performance", problems), "performance", problems);
        viewDistance = performance.number("view-distance", 32, 1, 256);
        maxAnimations = performance.integer("max-running-animations", 40, 0, 10000);
        particleMultiplier = performance.number("particle-multiplier", 1, 0, 10);
        performance.reportUnused();

        menu = new MenuSettings(OptionReader.section(root, "menu", "menu", problems), parser, problems);
        storage = StorageSettings.read(OptionReader.section(root, "storage", "storage", problems), defaultTable, problems);
    }

    private static Set<String> lowerCase(List<String> values) {
        if (values.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> set = new HashSet<String>();
        for (String value : values) {
            set.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return set;
    }

    /** The whole config.yml, for the plugin specific sections. */
    public ConfigurationSection root() {
        return root;
    }

    /** True if effects may play in the world. */
    public boolean isWorldEnabled(World world) {
        String name = world.getName().toLowerCase(Locale.ROOT);
        if (disabledWorlds.contains(name)) {
            return false;
        }
        return enabledWorlds.isEmpty() || enabledWorlds.contains(name);
    }

    public boolean requirePermission() {
        return requirePermission;
    }

    /** Id of the effect for players without a selection, or an empty string. */
    public String defaultEffect() {
        return defaultEffect;
    }

    public PermissionDefault effectPermissionDefault() {
        return effectPermissionDefault;
    }

    public boolean debug() {
        return debug;
    }

    public boolean previewsEnabled() {
        return previewsEnabled;
    }

    /** Seconds between two previews. */
    public int previewCooldown() {
        return previewCooldown;
    }

    public double previewDistance() {
        return previewDistance;
    }

    public boolean previewPrivate() {
        return previewPrivate;
    }

    /** Whether effects the player cannot use may be previewed. */
    public boolean previewLocked() {
        return previewLocked;
    }

    public double viewDistance() {
        return viewDistance;
    }

    public int maxAnimations() {
        return maxAnimations;
    }

    public double particleMultiplier() {
        return particleMultiplier;
    }

    public MenuSettings menu() {
        return menu;
    }

    public StorageSettings storage() {
        return storage;
    }
}

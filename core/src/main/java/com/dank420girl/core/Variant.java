package com.dank420girl.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The plugins built from this core. Everything that differs between DeathEffects and KillEffects is
 * decided here and in their {@link Trigger}; the plugin modules only add the main class and the API events.
 * <p>
 * The names are repeated in the root build.gradle.kts, which fills in plugin.yml; a test checks that both agree.
 */
public final class Variant {
    public static final Variant DEATH_EFFECTS = new Variant("DeathEffects", "de", "death",
            "Players pick an effect that plays where they die.");
    public static final Variant KILL_EFFECTS = new Variant("KillEffects", "ke", "kill",
            "Players pick an effect that plays where their victims die.");

    private static final List<Variant> ALL = Collections.unmodifiableList(Arrays.asList(DEATH_EFFECTS, KILL_EFFECTS));

    private final String name;
    private final String id;
    private final String alias;
    private final String kind;
    private final String description;

    /**
     * @param name        display name, e.g. DeathEffects
     * @param alias       short command alias, e.g. de
     * @param kind        the word used in messages, e.g. death
     * @param description what the plugin does, for plugin.yml and config.yml
     */
    private Variant(String name, String alias, String kind, String description) {
        this.name = name;
        this.id = name.toLowerCase(Locale.ROOT);
        this.alias = alias;
        this.kind = kind;
        this.description = description;
    }

    /** Both plugins. */
    public static List<Variant> all() {
        return ALL;
    }

    /** E.g. DeathEffects. */
    public String name() {
        return name;
    }

    /** E.g. deatheffects: the main command, the permission prefix and the table prefix. */
    public String id() {
        return id;
    }

    /** E.g. de. */
    public String alias() {
        return alias;
    }

    /** E.g. death or kill. */
    public String kind() {
        return kind;
    }

    /** E.g. "Players pick an effect that plays where they die." */
    public String description() {
        return description;
    }

    /** The other plugin of the pair, which may run on the same server. */
    public Variant other() {
        return this == DEATH_EFFECTS ? KILL_EFFECTS : DEATH_EFFECTS;
    }

    /** A permission of this plugin, e.g. {@code deatheffects.gui}. */
    public String permission(Perm permission) {
        return id + "." + permission.node();
    }

    /** The default permission of an effect, e.g. {@code deatheffects.vortex}. */
    public String effectPermission(String effectId) {
        return id + "." + effectId;
    }

    public String defaultTable() {
        return id + "_players";
    }

    /** What makes the plugin's effects play. */
    Trigger createTrigger(EffectsPlugin plugin) {
        return this == DEATH_EFFECTS ? new DeathTrigger(plugin) : new KillTrigger(plugin);
    }

    /**
     * The tokens of the configuration templates, such as {@code @PLUGIN@}. {@code @TRIGGERS@} is the part of
     * config.yml that only this plugin has.
     */
    public Map<String, String> tokens() {
        Map<String, String> tokens = new LinkedHashMap<String, String>();
        tokens.put("PLUGIN", name);
        tokens.put("PLUGIN_ID", id);
        tokens.put("ALIAS", alias);
        tokens.put("KIND", kind);
        tokens.put("DESCRIPTION", description);
        tokens.put("OTHER_PLUGIN", other().name);
        tokens.put("TRIGGERS", "@INCLUDE templates/triggers-" + kind + ".yml@");
        return tokens;
    }

    @Override
    public String toString() {
        return name;
    }
}

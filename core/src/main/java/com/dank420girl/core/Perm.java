package com.dank420girl.core;

import org.bukkit.permissions.PermissionDefault;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The permissions of both plugins, apart from the one every effect has ({@code deatheffects.<effect>}).
 * Each node is prefixed with the plugin's id, see {@link Variant#permission(Perm)}.
 * <p>
 * plugin.yml declares the same permissions with their descriptions; a test checks that both agree.
 */
public enum Perm {
    // ---- Players: everybody has these unless a permission plugin takes them away.

    /** Open the menu: /de, /de open, /de &lt;page&gt;. */
    GUI("gui", PermissionDefault.TRUE),
    /** Pick an effect by command: /de select &lt;effect&gt; and /de &lt;effect&gt;. The effect's own permission is needed too. */
    SELECT("select", PermissionDefault.TRUE),
    /** Clear one's own effect, by command or with the button in the menu. */
    CLEAR("clear", PermissionDefault.TRUE),
    /** Preview effects: right click in the menu, /de preview &lt;effect&gt;. */
    PREVIEW("preview", PermissionDefault.TRUE),
    /** /de list. */
    LIST("list", PermissionDefault.TRUE),
    /** /de info for one's own effect. */
    INFO("info", PermissionDefault.TRUE),
    /** /de help. */
    HELP("help", PermissionDefault.TRUE),

    // ---- Extras

    /** Use every effect; the permission of each effect is added as a child when the plugin starts. */
    ALL_EFFECTS("alleffects", PermissionDefault.OP),
    /** Preview without waiting for the cooldown. */
    PREVIEW_BYPASS("preview.bypass", PermissionDefault.OP),

    // ---- Admin commands

    /** /de reload. */
    RELOAD("reload", PermissionDefault.OP),
    /** /de version. */
    VERSION("version", PermissionDefault.OP),
    /** /de open &lt;player&gt;. */
    GUI_OTHERS("gui.others", PermissionDefault.OP),
    /** /de select &lt;effect&gt; &lt;player&gt; and /de &lt;effect&gt; &lt;player&gt;. */
    SELECT_OTHERS("select.others", PermissionDefault.OP),
    /** /de clear &lt;player&gt;. */
    CLEAR_OTHERS("clear.others", PermissionDefault.OP),
    /** /de info &lt;player&gt;. */
    INFO_OTHERS("info.others", PermissionDefault.OP),
    /** The name the old versions used for {@link #SELECT_OTHERS}, kept so existing setups keep working. */
    EFFECTS_OTHERS("effects.others", PermissionDefault.OP, SELECT_OTHERS),
    /** Every admin command. */
    ADMIN("admin", PermissionDefault.OP, RELOAD, VERSION, GUI_OTHERS, SELECT_OTHERS, CLEAR_OTHERS, INFO_OTHERS),
    /** Everything. */
    ALL("*", PermissionDefault.OP, GUI, SELECT, CLEAR, PREVIEW, LIST, INFO, HELP, ALL_EFFECTS, PREVIEW_BYPASS, ADMIN);

    private final String node;
    private final PermissionDefault defaultValue;
    private final List<Perm> children;

    Perm(String node, PermissionDefault defaultValue, Perm... children) {
        this.node = node;
        this.defaultValue = defaultValue;
        this.children = Collections.unmodifiableList(Arrays.asList(children));
    }

    /** The node without the plugin's id, for example {@code gui.others}. */
    public String node() {
        return node;
    }

    /** Who has the permission when no permission plugin says otherwise. */
    public PermissionDefault defaultValue() {
        return defaultValue;
    }

    /** The permissions this one grants as well. */
    public List<Perm> children() {
        return children;
    }
}

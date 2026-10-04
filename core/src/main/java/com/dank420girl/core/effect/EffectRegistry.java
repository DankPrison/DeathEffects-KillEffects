package com.dank420girl.core.effect;

import com.dank420girl.core.Variant;
import com.dank420girl.core.config.ConfigFiles;
import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.config.Problems;
import com.dank420girl.core.effect.layer.Layer;
import com.dank420girl.core.names.Names.ItemName;
import com.dank420girl.core.text.Colors;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The effects defined in effects.yml, in menu order.
 */
public final class EffectRegistry {
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,64}");
    /** Sub-commands and permission nodes of the plugins, which effect ids would clash with. */
    private static final Set<String> RESERVED = new HashSet<String>(Arrays.asList(
            "help", "list", "select", "preview", "clear", "info", "open", "reload", "version",
            "gui", "admin", "alleffects", "effects"));

    private final Map<String, EffectDefinition> effects;

    private EffectRegistry(Map<String, EffectDefinition> effects) {
        this.effects = effects;
    }

    public static EffectRegistry empty() {
        return new EffectRegistry(Collections.<String, EffectDefinition>emptyMap());
    }

    /** Reads every effect under {@code effects:}; problems are collected instead of failing. */
    public static EffectRegistry load(ConfigurationSection root, LayerParser parser, Variant variant, Problems problems) {
        for (String key : root.getKeys(false)) {
            if (!key.equals("effects") && !key.equals(ConfigFiles.NAMES_VERSION_KEY)) {
                problems.warn("", "unknown option '" + key + "' (ignored; effects belong under 'effects:')");
            }
        }
        ConfigurationSection section = root.getConfigurationSection("effects");
        List<EffectDefinition> list = new ArrayList<EffectDefinition>();
        if (section == null) {
            problems.warn("effects", "there is no 'effects' section, so there are no effects");
            return new EffectRegistry(Collections.<String, EffectDefinition>emptyMap());
        }
        int position = 0;
        for (String key : section.getKeys(false)) {
            position++;
            String path = "effects." + key;
            String id = key.toLowerCase(Locale.ROOT);
            if (!ID.matcher(id).matches()) {
                problems.warn(path, "effect ids may only contain letters, digits, '-' and '_'; effect skipped");
                continue;
            }
            if (RESERVED.contains(id)) {
                problems.warn(path, "'" + id + "' is the name of a command or permission and cannot be an effect id; effect skipped");
                continue;
            }
            ConfigurationSection effect = section.getConfigurationSection(key);
            if (effect == null) {
                problems.warn(path, "should be a section with name, icon and layers; effect skipped");
                continue;
            }
            EffectDefinition definition = loadEffect(id, effect, path, position, parser, variant, problems);
            if (definition != null) {
                list.add(definition);
            }
        }
        Collections.sort(list, new Comparator<EffectDefinition>() {
            @Override
            public int compare(EffectDefinition a, EffectDefinition b) {
                return Integer.compare(a.order(), b.order());
            }
        });
        Map<String, EffectDefinition> map = new LinkedHashMap<String, EffectDefinition>();
        for (EffectDefinition definition : list) {
            if (map.containsKey(definition.id())) {
                problems.warn("effects." + definition.id(), "defined twice (ids are not case sensitive); the second one is skipped");
                continue;
            }
            map.put(definition.id(), definition);
        }
        return new EffectRegistry(map);
    }

    private static EffectDefinition loadEffect(String id, ConfigurationSection section, String path, int position,
                                               LayerParser parser, Variant variant, Problems problems) {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (String key : section.getKeys(false)) {
            values.put(key, section.get(key));
        }
        OptionReader o = new OptionReader(values, path, problems);
        if (!o.bool("enabled", true)) {
            return null;
        }
        String name = Colors.colorize(o.string("name", id));
        ItemName icon = parser.firstItem(o, "icon", "PAPER");
        if (icon == null) {
            icon = parser.names().item("PAPER", null);
        }
        List<String> lore = Colors.colorize(o.strings("lore"));
        String permission;
        if (o.has("permission")) {
            String value = o.string("permission", "").trim();
            permission = value.isEmpty() || value.equalsIgnoreCase("none") ? null : value;
        } else {
            permission = variant.effectPermission(id);
        }
        int order = o.integer("order", position, Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2);

        List<Layer> layers = new ArrayList<Layer>();
        Object raw = o.raw("layers");
        if (raw instanceof List) {
            List<?> entries = (List<?>) raw;
            for (int i = 0; i < entries.size(); i++) {
                Object entry = entries.get(i);
                String layerPath = path + ".layers[" + i + "]";
                if (!(entry instanceof Map)) {
                    problems.warn(layerPath, "should be a section with a 'type'; layer skipped");
                    continue;
                }
                Layer layer = parser.parse((Map<?, ?>) entry, layerPath, problems);
                if (layer != null) {
                    layers.add(layer);
                }
            }
        } else if (raw != null) {
            problems.warn(path + ".layers", "should be a list of layers");
        }
        if (layers.isEmpty() && parser.particlesAvailable()) {
            problems.warn(path, "has no layers that can play on this server, so it shows nothing");
        }
        o.reportUnused();
        return new EffectDefinition(id, name, icon, lore, permission, order, layers);
    }

    /** Finds an effect by id, ignoring case; {@code null} if there is none. */
    public EffectDefinition get(String id) {
        return id == null ? null : effects.get(id.toLowerCase(Locale.ROOT));
    }

    /** All effects in menu order. */
    public Collection<EffectDefinition> all() {
        return Collections.unmodifiableCollection(effects.values());
    }

    public List<String> ids() {
        return new ArrayList<String>(effects.keySet());
    }

    public int size() {
        return effects.size();
    }
}

package com.dank420girl.core.config;

import com.dank420girl.core.effect.ColorSpec;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Typed access to a map of options (an effect layer, a menu item, ...). Invalid values are reported
 * and replaced by the default instead of failing, and options that were never read are reported as
 * possible typos.
 */
public final class OptionReader {
    private final Map<String, Object> values = new LinkedHashMap<String, Object>();
    private final String path;
    private final Problems problems;
    private final Set<String> used = new LinkedHashSet<String>();

    public OptionReader(Map<?, ?> map, String path, Problems problems) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            values.put(String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT), entry.getValue());
        }
        this.path = path;
        this.problems = problems;
    }

    public static OptionReader of(ConfigurationSection section, String path, Problems problems) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Object value = section.get(key);
                if (value instanceof ConfigurationSection) {
                    value = ((ConfigurationSection) value).getValues(true);
                }
                map.put(key, value);
            }
        }
        return new OptionReader(map, path, problems);
    }

    /**
     * The sub-section {@code key} of {@code parent}, or {@code null} if there is none. A plain value where a
     * section belongs is reported, and the section of the default file is used instead.
     */
    public static ConfigurationSection section(ConfigurationSection parent, String key, String path, Problems problems) {
        if (parent == null) {
            return null;
        }
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            Object value = parent.get(key);
            if (value != null) {
                problems.warn(path, "should be a section of options, not '" + value + "'; using the defaults");
                ConfigurationSection defaults = parent.getDefaultSection();
                return defaults == null ? null : defaults.getConfigurationSection(key);
            }
        }
        return section;
    }

    public String path() {
        return path;
    }

    public Problems problems() {
        return problems;
    }

    public void warn(String message) {
        problems.warn(path, message);
    }

    public boolean has(String key) {
        used.add(key);
        return values.containsKey(key) && values.get(key) != null;
    }

    public Object raw(String key) {
        used.add(key);
        return values.get(key);
    }

    public String string(String key, String def) {
        Object value = raw(key);
        if (value == null) {
            return def;
        }
        if (value instanceof Collection || value instanceof Map) {
            warn("'" + key + "' should be a single value");
            return def;
        }
        return String.valueOf(value);
    }

    public double number(String key, double def, double min, double max) {
        Object value = raw(key);
        if (value == null) {
            return def;
        }
        double number;
        if (value instanceof Number) {
            number = ((Number) value).doubleValue();
        } else {
            try {
                number = Double.parseDouble(String.valueOf(value).trim());
            } catch (NumberFormatException e) {
                warn("'" + key + "' should be a number, not '" + value + "'");
                return def;
            }
        }
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            warn("'" + key + "' should be a number, not '" + value + "'");
            return def;
        }
        if (number < min || number > max) {
            double clamped = Math.max(min, Math.min(max, number));
            warn("'" + key + "' must be between " + format(min) + " and " + format(max) + ", using " + format(clamped));
            return clamped;
        }
        return number;
    }

    public int integer(String key, int def, int min, int max) {
        return (int) Math.round(number(key, def, min, max));
    }

    public Float optionalNumber(String key) {
        if (!has(key)) {
            return null;
        }
        return (float) number(key, 0, -Double.MAX_VALUE, Double.MAX_VALUE);
    }

    public boolean bool(String key, boolean def) {
        Object value = raw(key);
        if (value == null) {
            return def;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (text.equals("true") || text.equals("yes") || text.equals("on")) {
            return true;
        }
        if (text.equals("false") || text.equals("no") || text.equals("off")) {
            return false;
        }
        warn("'" + key + "' should be true or false, not '" + value + "'");
        return def;
    }

    /** A list of strings; a single value counts as a list of one. */
    public List<String> strings(String key) {
        Object value = raw(key);
        if (value == null) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<String>();
        if (value instanceof Collection) {
            for (Object o : (Collection<?>) value) {
                if (o != null) {
                    list.add(String.valueOf(o));
                }
            }
        } else {
            list.add(String.valueOf(value));
        }
        return list;
    }

    /** One number for all three axes, or a list of three numbers {@code [x, y, z]}. */
    public double[] vector(String key, double def) {
        Object value = raw(key);
        if (value == null) {
            return new double[] {def, def, def};
        }
        if (value instanceof Collection) {
            List<?> list = new ArrayList<Object>((Collection<?>) value);
            if (list.size() == 3) {
                double[] result = new double[3];
                for (int i = 0; i < 3; i++) {
                    try {
                        result[i] = Double.parseDouble(String.valueOf(list.get(i)).trim());
                    } catch (NumberFormatException e) {
                        warn("'" + key + "' should be a number or a list of three numbers [x, y, z]");
                        return new double[] {def, def, def};
                    }
                }
                return result;
            }
            warn("'" + key + "' should be a number or a list of three numbers [x, y, z]");
            return new double[] {def, def, def};
        }
        double number = number(key, def, -1000, 1000);
        return new double[] {number, number, number};
    }

    /** A colour or a list of colours, or {@code null} if not set or invalid. */
    public ColorSpec color(String key) {
        List<String> values = strings(key);
        if (values.isEmpty()) {
            return null;
        }
        try {
            return ColorSpec.parseList(values);
        } catch (IllegalArgumentException e) {
            warn("'" + key + "': " + e.getMessage());
            return null;
        }
    }

    /** An option that must be one of the constants of the enum. */
    public <E extends Enum<E>> E choice(String key, Class<E> type, E def) {
        String value = string(key, null);
        if (value == null) {
            return def;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(normalized)) {
                return constant;
            }
        }
        StringBuilder allowed = new StringBuilder();
        for (E constant : type.getEnumConstants()) {
            if (allowed.length() > 0) {
                allowed.append(", ");
            }
            allowed.append(constant.name());
        }
        warn("'" + key + "' should be one of " + allowed + ", not '" + value + "'");
        return def;
    }

    /** Marks options that are read elsewhere (such as sub-sections), so they are not reported as unknown. */
    public void markRead(String... keys) {
        for (String key : keys) {
            used.add(key);
        }
    }

    /** Reports options that were never read: usually typos. */
    public void reportUnused() {
        for (String key : values.keySet()) {
            if (!used.contains(key)) {
                warn("unknown option '" + key + "' (ignored)");
            }
        }
    }

    private static String format(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}

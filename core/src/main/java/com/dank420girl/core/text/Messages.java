package com.dank420girl.core.text;

import com.dank420girl.core.config.Problems;
import com.dank420girl.core.platform.scheduler.Scheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The texts of messages.yml. A message can be a single line or a list of lines; an empty message
 * is not sent at all. {@code {prefix}} is replaced everywhere, other placeholders are passed in
 * as key/value pairs.
 */
public final class Messages {
    private final Map<String, List<String>> messages = new HashMap<String, List<String>>();
    private final Scheduler scheduler;
    private final String prefix;

    public Messages(ConfigurationSection config, ConfigurationSection defaults, Scheduler scheduler) {
        this.scheduler = scheduler;
        load(defaults);
        load(config);
        List<String> prefixLines = messages.get("prefix");
        this.prefix = prefixLines == null || prefixLines.isEmpty() ? "" : prefixLines.get(0);
    }

    /** Reports keys of messages.yml that are not messages of the plugin: usually typos. */
    public static void reportUnknown(ConfigurationSection config, ConfigurationSection defaults, Problems problems) {
        if (config == null || defaults == null) {
            return;
        }
        for (String key : config.getKeys(true)) {
            if (defaults.contains(key)) {
                continue;
            }
            int dot = key.lastIndexOf('.');
            if (dot > 0 && !defaults.contains(key.substring(0, dot))) {
                continue; // the section it is in is reported already
            }
            problems.warn("", "unknown message '" + key + "' (ignored)");
        }
    }

    private void load(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            if (section.isList(key)) {
                List<String> lines = new ArrayList<String>();
                for (Object o : section.getList(key)) {
                    lines.add(String.valueOf(o));
                }
                messages.put(key, lines);
            } else if (section.isConfigurationSection(key)) {
                ConfigurationSection child = section.getConfigurationSection(key);
                for (String childKey : child.getKeys(true)) {
                    if (child.isList(childKey)) {
                        List<String> lines = new ArrayList<String>();
                        for (Object o : child.getList(childKey)) {
                            lines.add(String.valueOf(o));
                        }
                        messages.put(key + "." + childKey, lines);
                    } else if (!child.isConfigurationSection(childKey)) {
                        Object value = child.get(childKey);
                        messages.put(key + "." + childKey, Collections.singletonList(value == null ? "" : String.valueOf(value)));
                    }
                }
            } else {
                Object value = section.get(key);
                messages.put(key, Collections.singletonList(value == null ? "" : String.valueOf(value)));
            }
        }
    }

    /** True if the message exists and is not empty. */
    public boolean isEnabled(String key) {
        List<String> lines = messages.get(key);
        if (lines == null) {
            return false;
        }
        for (String line : lines) {
            if (!line.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** The coloured lines of a message with placeholders filled in; empty if the message is disabled. */
    public List<String> lines(String key, Object... placeholders) {
        List<String> raw = messages.get(key);
        if (raw == null) {
            return Collections.singletonList(key);
        }
        List<String> result = new ArrayList<String>(raw.size());
        for (String line : raw) {
            if (line.isEmpty()) {
                continue;
            }
            result.add(Colors.colorize(replace(line, placeholders)));
        }
        return result;
    }

    /** A single-line message (lines joined with a line break); empty if the message is disabled. */
    public String get(String key, Object... placeholders) {
        List<String> lines = lines(key, placeholders);
        if (lines.isEmpty()) {
            return "";
        }
        if (lines.size() == 1) {
            return lines.get(0);
        }
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }

    /** The raw, uncoloured text of a message, without placeholders replaced. */
    public String raw(String key) {
        List<String> raw = messages.get(key);
        return raw == null || raw.isEmpty() ? "" : raw.get(0);
    }

    public void send(CommandSender to, String key, Object... placeholders) {
        sendLines(to, lines(key, placeholders));
    }

    /** Sends already formatted lines, on the player's own thread on Folia. */
    public void sendLines(final CommandSender to, final List<String> lines) {
        if (to == null || lines.isEmpty()) {
            return;
        }
        Runnable task = new Runnable() {
            @Override
            public void run() {
                for (String line : lines) {
                    to.sendMessage(line);
                }
            }
        };
        if (to instanceof Player) {
            scheduler.executeFor((Player) to, task);
        } else {
            task.run();
        }
    }

    public String replace(String text, Object... placeholders) {
        String result = text.replace("{prefix}", prefix);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return result;
    }
}

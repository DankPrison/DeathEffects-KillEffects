package com.dank420girl.core.config;

import com.dank420girl.core.effect.LayerParser;
import com.dank420girl.core.effect.SoundSpec;
import com.dank420girl.core.names.Names.ItemName;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The menu section of config.yml.
 */
public final class MenuSettings {
    private static final Set<String> BUTTONS = new HashSet<String>(Arrays.asList("clear", "current", "previous-page", "next-page"));
    private static final Set<String> SOUNDS = new HashSet<String>(Arrays.asList("select", "locked", "page", "clear", "preview"));
    private static final Set<String> LORE = new HashSet<String>(Arrays.asList("selected", "available", "locked"));

    /** A fixed item of the menu such as the page buttons. */
    public static final class Button {
        private boolean enabled;
        private final int slot;
        private final ItemName item;
        private final String name;
        private final List<String> lore;

        Button(boolean enabled, int slot, ItemName item, String name, List<String> lore) {
            this.enabled = enabled;
            this.slot = slot;
            this.item = item;
            this.name = name;
            this.lore = lore;
        }

        public boolean enabled() {
            return enabled;
        }

        void disable() {
            enabled = false;
        }

        public int slot() {
            return slot;
        }

        public ItemName item() {
            return item;
        }

        /** The name, still with colour codes and placeholders. */
        public String name() {
            return name;
        }

        public List<String> lore() {
            return lore;
        }
    }

    private final String title;
    private final int rows;
    private final int[] effectSlots;
    private final boolean closeOnSelect;
    private final boolean hideLocked;
    private final boolean glowSelected;
    private final ItemName lockedIcon;
    private final Button filler;
    private final Button clear;
    private final Button current;
    private final Button previousPage;
    private final Button nextPage;
    private final List<String> loreSelected;
    private final List<String> loreAvailable;
    private final List<String> loreLocked;
    private final SoundSpec soundSelect;
    private final SoundSpec soundLocked;
    private final SoundSpec soundPage;
    private final SoundSpec soundClear;
    private final SoundSpec soundPreview;

    MenuSettings(ConfigurationSection section, LayerParser parser, Problems problems) {
        OptionReader o = OptionReader.of(section, "menu", problems);
        title = o.string("title", "&0&nEffects");
        rows = o.integer("rows", 6, 1, 6);
        closeOnSelect = o.bool("close-on-select", true);
        hideLocked = o.bool("hide-locked-effects", false);
        glowSelected = o.bool("glow-selected", true);
        List<String> locked = o.strings("locked-icon");
        lockedIcon = locked.isEmpty() || locked.get(0).trim().isEmpty() ? null : parser.firstItem(o, "locked-icon", null);

        ConfigurationSection buttons = OptionReader.section(section, "buttons", "menu.buttons", problems);
        int lastRow = (rows - 1) * 9;
        filler = button(OptionReader.section(section, "filler", "menu.filler", problems), "menu.filler", -1,
                "GRAY_STAINED_GLASS_PANE", " ", parser, problems);
        clear = button(child(buttons, "clear", problems), "menu.buttons.clear", 4, "BARRIER", "&cClear Current Effect", parser, problems);
        current = button(child(buttons, "current", problems), "menu.buttons.current", lastRow + 4, "BOOK", "&eYour effect: {effect}", parser, problems);
        previousPage = button(child(buttons, "previous-page", problems), "menu.buttons.previous-page", lastRow, "ARROW", "&aPrevious page", parser, problems);
        nextPage = button(child(buttons, "next-page", problems), "menu.buttons.next-page", lastRow + 8, "ARROW", "&aNext page", parser, problems);

        Set<Integer> reserved = new LinkedHashSet<Integer>();
        String[] buttonNames = {"clear", "current", "previous-page", "next-page"};
        Button[] all = {clear, current, previousPage, nextPage};
        for (int i = 0; i < all.length; i++) {
            if (!all[i].enabled()) {
                continue;
            }
            if (!reserved.add(all[i].slot())) {
                problems.warn("menu.buttons." + buttonNames[i] + ".slot", "slot " + all[i].slot()
                        + " is already used by another button; this button is left out");
                all[i].disable();
            }
        }
        effectSlots = slots(o.raw("effect-slots"), rows * 9, reserved, problems);

        ConfigurationSection lore = OptionReader.section(section, "effect-lore", "menu.effect-lore", problems);
        loreSelected = lore == null ? Collections.<String>emptyList() : lore.getStringList("selected");
        loreAvailable = lore == null ? Collections.<String>emptyList() : lore.getStringList("available");
        loreLocked = lore == null ? Collections.<String>emptyList() : lore.getStringList("locked");

        ConfigurationSection sounds = OptionReader.section(section, "sounds", "menu.sounds", problems);
        soundSelect = sound(sounds, "select", parser, problems);
        soundLocked = sound(sounds, "locked", parser, problems);
        soundPage = sound(sounds, "page", parser, problems);
        soundClear = sound(sounds, "clear", parser, problems);
        soundPreview = sound(sounds, "preview", parser, problems);

        o.markRead("buttons", "filler", "effect-lore", "sounds");
        o.reportUnused();
        reportUnknown(buttons, "menu.buttons", BUTTONS, "button", problems);
        reportUnknown(sounds, "menu.sounds", SOUNDS, "sound", problems);
        reportUnknown(lore, "menu.effect-lore", LORE, "option", problems);
    }

    /** Reports entries of a section that the plugin does not know, usually typos. */
    private static void reportUnknown(ConfigurationSection section, String path, Set<String> known, String what,
                                      Problems problems) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            if (!known.contains(key)) {
                problems.warn(path, "unknown " + what + " '" + key + "' (ignored)");
            }
        }
    }

    private static ConfigurationSection child(ConfigurationSection buttons, String key, Problems problems) {
        return OptionReader.section(buttons, key, "menu.buttons." + key, problems);
    }

    private Button button(ConfigurationSection section, String path, int defaultSlot, String defaultItem,
                          String defaultName, LayerParser parser, Problems problems) {
        OptionReader o = OptionReader.of(section, path, problems);
        boolean enabled = o.bool("enabled", true);
        int slot = -1;
        if (defaultSlot >= 0) {
            slot = o.integer("slot", defaultSlot, 0, 1000);
            if (slot >= rows * 9) {
                o.warn("slot " + slot + " is outside a menu of " + rows + " rows; using " + defaultSlot);
                slot = defaultSlot;
            }
        }
        ItemName item = parser.firstItem(o, "item", defaultItem);
        if (item == null) {
            item = parser.names().item(defaultItem, null);
        }
        String name = o.string("name", defaultName);
        List<String> lore = o.strings("lore");
        o.reportUnused();
        return new Button(enabled, slot, item, name, lore);
    }

    private static SoundSpec sound(ConfigurationSection sounds, String key, LayerParser parser, Problems problems) {
        ConfigurationSection section = OptionReader.section(sounds, key, "menu.sounds." + key, problems);
        if (section == null) {
            return null;
        }
        OptionReader o = OptionReader.of(section, "menu.sounds." + key, problems);
        List<String> values = o.strings("sound");
        if (values.isEmpty() || values.get(0).trim().isEmpty() || values.get(0).trim().equalsIgnoreCase("none")) {
            o.markRead("volume", "pitch");
            o.reportUnused();
            return null;
        }
        SoundSpec sound = parser.sound(o, "sound", null);
        o.reportUnused();
        return sound;
    }

    /** Parses '9-44' or a list such as [10, 11, '19-25'] into slot numbers. */
    static int[] slots(Object value, int size, Set<Integer> reserved, Problems problems) {
        List<Object> parts = new ArrayList<Object>();
        if (value == null) {
            parts.add("9-44");
        } else if (value instanceof List) {
            parts.addAll((List<?>) value);
        } else {
            parts.addAll(Arrays.asList(String.valueOf(value).split(",")));
        }
        Set<Integer> slots = new LinkedHashSet<Integer>();
        for (Object part : parts) {
            String text = String.valueOf(part).trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                int dash = text.indexOf('-');
                int from = dash < 0 ? Integer.parseInt(text) : Integer.parseInt(text.substring(0, dash).trim());
                int to = dash < 0 ? from : Integer.parseInt(text.substring(dash + 1).trim());
                for (int slot = Math.min(from, to); slot <= Math.max(from, to); slot++) {
                    if (slot >= 0 && slot < size && !reserved.contains(slot)) {
                        slots.add(slot);
                    }
                }
            } catch (NumberFormatException e) {
                problems.warn("menu.effect-slots", "'" + text + "' is not a slot or a range like 9-44");
            }
        }
        if (slots.isEmpty()) {
            problems.warn("menu.effect-slots", "no usable slots; using every free slot");
            for (int slot = 0; slot < size; slot++) {
                if (!reserved.contains(slot)) {
                    slots.add(slot);
                }
            }
        }
        int[] result = new int[slots.size()];
        int i = 0;
        for (int slot : slots) {
            result[i++] = slot;
        }
        return result;
    }

    public String title() {
        return title;
    }

    public int rows() {
        return rows;
    }

    public int[] effectSlots() {
        return effectSlots;
    }

    public boolean closeOnSelect() {
        return closeOnSelect;
    }

    public boolean hideLocked() {
        return hideLocked;
    }

    public boolean glowSelected() {
        return glowSelected;
    }

    /** Icon shown instead of the effect's own icon when it is locked, or {@code null}. */
    public ItemName lockedIcon() {
        return lockedIcon;
    }

    public Button filler() {
        return filler;
    }

    public Button clear() {
        return clear;
    }

    public Button current() {
        return current;
    }

    public Button previousPage() {
        return previousPage;
    }

    public Button nextPage() {
        return nextPage;
    }

    public List<String> loreSelected() {
        return loreSelected;
    }

    public List<String> loreAvailable() {
        return loreAvailable;
    }

    public List<String> loreLocked() {
        return loreLocked;
    }

    public SoundSpec soundSelect() {
        return soundSelect;
    }

    public SoundSpec soundLocked() {
        return soundLocked;
    }

    public SoundSpec soundPage() {
        return soundPage;
    }

    public SoundSpec soundClear() {
        return soundClear;
    }

    public SoundSpec soundPreview() {
        return soundPreview;
    }
}

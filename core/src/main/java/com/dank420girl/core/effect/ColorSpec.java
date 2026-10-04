package com.dank420girl.core.effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * A colour setting: one colour, a list that is cycled through, a rainbow or a random colour.
 * Colours are plain RGB integers so that this class does not depend on the Bukkit API.
 */
public final class ColorSpec {
    private static final Map<String, Integer> NAMED = new HashMap<String, Integer>();

    static {
        // Bukkit's Color constants
        NAMED.put("WHITE", 0xFFFFFF);
        NAMED.put("SILVER", 0xC0C0C0);
        NAMED.put("GRAY", 0x808080);
        NAMED.put("BLACK", 0x000000);
        NAMED.put("RED", 0xFF0000);
        NAMED.put("MAROON", 0x800000);
        NAMED.put("YELLOW", 0xFFFF00);
        NAMED.put("OLIVE", 0x808000);
        NAMED.put("LIME", 0x00FF00);
        NAMED.put("GREEN", 0x008000);
        NAMED.put("AQUA", 0x00FFFF);
        NAMED.put("TEAL", 0x008080);
        NAMED.put("BLUE", 0x0000FF);
        NAMED.put("NAVY", 0x000080);
        NAMED.put("FUCHSIA", 0xFF00FF);
        NAMED.put("PURPLE", 0x800080);
        NAMED.put("ORANGE", 0xFFA500);
        // a few more that people tend to try
        NAMED.put("PINK", 0xFFC0CB);
        NAMED.put("MAGENTA", 0xFF00FF);
        NAMED.put("CYAN", 0x00FFFF);
        NAMED.put("LIGHT_BLUE", 0xADD8E6);
        NAMED.put("BROWN", 0x8B4513);
        NAMED.put("GOLD", 0xFFD700);
        NAMED.put("DARK_RED", 0x8B0000);
        NAMED.put("DARK_GREEN", 0x006400);
        NAMED.put("LIGHT_GRAY", 0xD3D3D3);
        NAMED.put("DARK_GRAY", 0x404040);
    }

    private enum Mode {
        FIXED, CYCLE, RAINBOW, RANDOM
    }

    private final Mode mode;
    private final int[] colors;

    private ColorSpec(Mode mode, int[] colors) {
        this.mode = mode;
        this.colors = colors;
    }

    public static ColorSpec of(int rgb) {
        return new ColorSpec(Mode.FIXED, new int[] {rgb & 0xFFFFFF});
    }

    public static ColorSpec random() {
        return new ColorSpec(Mode.RANDOM, new int[0]);
    }

    /**
     * Parses a colour value: {@code '#FF0000'}, {@code 'FF0000'}, {@code '255,0,0'}, a name such as
     * {@code RED}, {@code RAINBOW} or {@code RANDOM}.
     *
     * @throws IllegalArgumentException if the value is not a colour
     */
    public static ColorSpec parse(String text) {
        String value = text.trim();
        String upper = value.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (upper.equals("RAINBOW")) {
            return new ColorSpec(Mode.RAINBOW, new int[0]);
        }
        if (upper.equals("RANDOM")) {
            return random();
        }
        return of(parseSingle(value));
    }

    /** Parses a list of colours that is cycled through; a single entry behaves like {@link #parse}. */
    public static ColorSpec parseList(List<String> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("no colours given");
        }
        if (values.size() == 1) {
            return parse(values.get(0));
        }
        int[] colors = new int[values.size()];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = parseSingle(values.get(i));
        }
        return new ColorSpec(Mode.CYCLE, colors);
    }

    private static int parseSingle(String text) {
        String value = text.trim();
        Integer named = NAMED.get(value.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        if (named != null) {
            return named;
        }
        if (value.indexOf(',') >= 0) {
            String[] parts = value.split(",");
            if (parts.length != 3) {
                throw new IllegalArgumentException("'" + text + "' is not a colour (use '#RRGGBB' or 'R,G,B')");
            }
            int rgb = 0;
            for (String part : parts) {
                int component;
                try {
                    component = Integer.parseInt(part.trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("'" + text + "' is not a colour (use '#RRGGBB' or 'R,G,B')");
                }
                if (component < 0 || component > 255) {
                    throw new IllegalArgumentException("'" + text + "' is not a colour (values go from 0 to 255)");
                }
                rgb = (rgb << 8) | component;
            }
            return rgb;
        }
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (hex.length() == 6) {
            try {
                return Integer.parseInt(hex, 16);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new IllegalArgumentException("'" + text + "' is not a colour (use '#RRGGBB', 'R,G,B', a name, RAINBOW or RANDOM)");
    }

    /** True if every call returns the same colour, so derived objects can be cached. */
    public boolean isFixed() {
        return mode == Mode.FIXED;
    }

    /**
     * The colour to use.
     *
     * @param step   a counter such as the animation tick, used by cycling colours and the rainbow
     * @param random used by random colours
     */
    public int rgb(long step, Random random) {
        switch (mode) {
            case FIXED:
                return colors[0];
            case CYCLE:
                return colors[(int) Math.floorMod(step, (long) colors.length)];
            case RAINBOW:
                return hsbToRgb((step % 36) / 36f, 1f, 1f);
            default:
                return hsbToRgb(random.nextFloat(), 0.6f + random.nextFloat() * 0.4f, 1f);
        }
    }

    public List<Integer> fixedColors() {
        if (mode == Mode.FIXED || mode == Mode.CYCLE) {
            List<Integer> list = new ArrayList<Integer>(colors.length);
            for (int c : colors) {
                list.add(c);
            }
            return list;
        }
        return Collections.emptyList();
    }

    /** HSB to RGB without java.awt, which is missing from some minimal Java runtimes. */
    static int hsbToRgb(float hue, float saturation, float brightness) {
        float h = (hue - (float) Math.floor(hue)) * 6f;
        int sector = (int) Math.floor(h);
        float f = h - sector;
        float p = brightness * (1f - saturation);
        float q = brightness * (1f - saturation * f);
        float t = brightness * (1f - saturation * (1f - f));
        float r;
        float g;
        float b;
        switch (sector) {
            case 0:
                r = brightness; g = t; b = p;
                break;
            case 1:
                r = q; g = brightness; b = p;
                break;
            case 2:
                r = p; g = brightness; b = t;
                break;
            case 3:
                r = p; g = q; b = brightness;
                break;
            case 4:
                r = t; g = p; b = brightness;
                break;
            default:
                r = brightness; g = p; b = q;
                break;
        }
        return (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
    }

    @Override
    public String toString() {
        switch (mode) {
            case RAINBOW:
                return "RAINBOW";
            case RANDOM:
                return "RANDOM";
            default:
                StringBuilder sb = new StringBuilder();
                for (int c : colors) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(String.format("#%06X", c));
                }
                return sb.toString();
        }
    }
}

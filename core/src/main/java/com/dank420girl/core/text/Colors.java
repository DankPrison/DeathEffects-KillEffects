package com.dank420girl.core.text;

import com.dank420girl.core.platform.MinecraftVersion;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns {@code &} colour codes and {@code &#RRGGBB} hex colours into Minecraft formatting codes.
 * <p>
 * Hex colours need Minecraft 1.16. On older servers they are replaced by the closest of the 16
 * classic colours, so messages written for new servers still look sensible.
 */
public final class Colors {
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final char SECTION = '§';

    /** The 16 classic colours and their RGB values, used to approximate hex colours. */
    private static final char[] LEGACY_CODES = "0123456789abcdef".toCharArray();
    private static final int[] LEGACY_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private static volatile Boolean hexSupported;

    private Colors() {
    }

    public static String colorize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (text.indexOf("&#") >= 0) {
            Matcher m = HEX.matcher(text);
            StringBuffer sb = new StringBuffer();
            boolean hex = supportsHex();
            while (m.find()) {
                String code = m.group(1);
                m.appendReplacement(sb, Matcher.quoteReplacement(hex ? toHexSequence(code) : nearestLegacy(code)));
            }
            m.appendTail(sb);
            text = sb.toString();
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> colorize(List<String> lines) {
        List<String> result = new ArrayList<String>(lines.size());
        for (String line : lines) {
            result.add(colorize(line));
        }
        return result;
    }

    /** Removes all formatting, including hex colours. */
    public static String strip(String text) {
        if (text == null) {
            return null;
        }
        String stripped = ChatColor.stripColor(colorize(text));
        // ChatColor.stripColor does not know the hex 'x' code on old versions.
        return stripped == null ? null : stripped.replaceAll("(?i)" + SECTION + "[0-9A-FK-ORX]", "");
    }

    private static boolean supportsHex() {
        Boolean value = hexSupported;
        if (value == null) {
            try {
                value = MinecraftVersion.current().isAtLeast(1, 16);
            } catch (IllegalStateException e) {
                value = Boolean.TRUE;
            }
            hexSupported = value;
        }
        return value;
    }

    /** Only meant for tests. */
    static void setHexSupportedForTesting(Boolean value) {
        hexSupported = value;
    }

    private static String toHexSequence(String hex) {
        StringBuilder sb = new StringBuilder(14).append(SECTION).append('x');
        for (char c : hex.toLowerCase().toCharArray()) {
            sb.append(SECTION).append(c);
        }
        return sb.toString();
    }

    static String nearestLegacy(String hex) {
        int rgb = Integer.parseInt(hex, 16);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < LEGACY_RGB.length; i++) {
            int lr = (LEGACY_RGB[i] >> 16) & 0xFF;
            int lg = (LEGACY_RGB[i] >> 8) & 0xFF;
            int lb = LEGACY_RGB[i] & 0xFF;
            long distance = (long) (r - lr) * (r - lr) + (long) (g - lg) * (g - lg) + (long) (b - lb) * (b - lb);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return new String(new char[] {SECTION, LEGACY_CODES[best]});
    }
}

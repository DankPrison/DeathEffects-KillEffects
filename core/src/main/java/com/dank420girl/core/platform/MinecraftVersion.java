package com.dank420girl.core.platform;

import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Minecraft version such as 1.8.8, 1.21.4 or 26.3.
 * <p>
 * Versions are compared numerically, so the yearly versions introduced in 2026 (26.1, 26.2, ...)
 * are correctly treated as newer than every 1.x release.
 */
public final class MinecraftVersion implements Comparable<MinecraftVersion> {
    private static final Pattern NUMBERS = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    private static final Pattern MC_TAG = Pattern.compile("\\(MC: ([0-9.]+)\\)");

    /** Oldest version with the flattened (1.13+) item and block names. */
    public static final MinecraftVersion FLATTENING = new MinecraftVersion(1, 13, 0);

    private static volatile MinecraftVersion current;

    private final int major;
    private final int minor;
    private final int patch;

    private MinecraftVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static MinecraftVersion of(int major, int minor, int patch) {
        return new MinecraftVersion(major, minor, patch);
    }

    public static MinecraftVersion of(int major, int minor) {
        return new MinecraftVersion(major, minor, 0);
    }

    /**
     * Parses the first version number found in the text, for example {@code 1.21.4-R0.1-SNAPSHOT},
     * {@code 26.3.build.12-beta} or {@code git-Spigot-db6de12 (MC: 1.8.8)}.
     *
     * @return the version, or {@code null} if the text contains none
     */
    public static MinecraftVersion parse(String text) {
        if (text == null) {
            return null;
        }
        Matcher tag = MC_TAG.matcher(text);
        String source = tag.find() ? tag.group(1) : text;
        Matcher m = NUMBERS.matcher(source);
        if (!m.find()) {
            return null;
        }
        int major = Integer.parseInt(m.group(1));
        int minor = Integer.parseInt(m.group(2));
        int patch = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        return new MinecraftVersion(major, minor, patch);
    }

    /** The version of the running server. */
    public static MinecraftVersion current() {
        MinecraftVersion version = current;
        if (version == null) {
            version = detect(Bukkit.getServer());
            current = version;
        }
        return version;
    }

    /** Overrides the detected version. Only meant for tests. */
    public static void setCurrentForTesting(MinecraftVersion version) {
        current = version;
    }

    private static MinecraftVersion detect(Server server) {
        if (server == null) {
            throw new IllegalStateException("The server is not running");
        }
        // Paper (1.16.5+) knows the exact game version.
        try {
            Method method = server.getClass().getMethod("getMinecraftVersion");
            MinecraftVersion version = parse(String.valueOf(method.invoke(server)));
            if (version != null) {
                return version;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // not available
        }
        MinecraftVersion version = parse(server.getVersion());
        if (version != null && server.getVersion().contains("(MC:")) {
            return version;
        }
        version = parse(server.getBukkitVersion());
        if (version != null) {
            return version;
        }
        throw new IllegalStateException("Cannot determine the Minecraft version from '" + server.getVersion() + "'");
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    public boolean isAtLeast(int major, int minor) {
        return isAtLeast(major, minor, 0);
    }

    public boolean isAtLeast(int major, int minor, int patch) {
        return compareTo(new MinecraftVersion(major, minor, patch)) >= 0;
    }

    public boolean isAtLeast(MinecraftVersion other) {
        return compareTo(other) >= 0;
    }

    public boolean isBefore(int major, int minor) {
        return !isAtLeast(major, minor, 0);
    }

    public boolean isBefore(int major, int minor, int patch) {
        return !isAtLeast(major, minor, patch);
    }

    /** True before 1.13, when items and blocks still had numeric data values. */
    public boolean isLegacy() {
        return compareTo(FLATTENING) < 0;
    }

    @Override
    public int compareTo(MinecraftVersion o) {
        if (major != o.major) {
            return Integer.compare(major, o.major);
        }
        if (minor != o.minor) {
            return Integer.compare(minor, o.minor);
        }
        return Integer.compare(patch, o.patch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MinecraftVersion)) {
            return false;
        }
        MinecraftVersion that = (MinecraftVersion) o;
        return major == that.major && minor == that.minor && patch == that.patch;
    }

    @Override
    public int hashCode() {
        return (major * 1000 + minor) * 1000 + patch;
    }

    @Override
    public String toString() {
        return patch == 0 ? major + "." + minor : major + "." + minor + "." + patch;
    }
}

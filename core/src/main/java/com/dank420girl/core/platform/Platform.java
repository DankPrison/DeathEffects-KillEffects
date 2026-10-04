package com.dank420girl.core.platform;

import org.bukkit.Bukkit;

/**
 * Facts about the server software the plugin runs on.
 */
public final class Platform {
    private static final boolean FOLIA = Reflect.classExists("io.papermc.paper.threadedregions.RegionizedServer");
    private static final boolean PAPER = Reflect.classExists("com.destroystokyo.paper.PaperConfig")
            || Reflect.classExists("io.papermc.paper.configuration.Configuration");
    private static final boolean PURPUR = Reflect.classExists("org.purpurmc.purpur.PurpurConfig");
    private static final boolean SPIGOT = Reflect.classExists("org.spigotmc.SpigotConfig");

    private Platform() {
    }

    /** True on Folia and its forks, where the world is split into independently ticking regions. */
    public static boolean isFolia() {
        return FOLIA;
    }

    public static boolean isPaper() {
        return PAPER;
    }

    public static boolean isSpigot() {
        return SPIGOT;
    }

    /** A short description such as "Paper (Folia)" for the version command and bug reports. */
    public static String describe() {
        StringBuilder sb = new StringBuilder(Bukkit.getName());
        if (FOLIA) {
            sb.append(" (Folia)");
        } else if (PURPUR) {
            sb.append(" (Purpur)");
        } else if (PAPER) {
            sb.append(" (Paper)");
        } else if (SPIGOT) {
            sb.append(" (Spigot)");
        } else {
            sb.append(" (CraftBukkit)");
        }
        return sb.toString();
    }

    /**
     * The CraftBukkit package version such as {@code v1_8_R3}, or {@code null} on servers that no
     * longer use versioned packages. Only needed for the packet based particles of 1.7 and 1.8.
     */
    public static String craftBukkitPackageVersion() {
        String name = Bukkit.getServer().getClass().getPackage().getName();
        String last = name.substring(name.lastIndexOf('.') + 1);
        return last.startsWith("v") && last.contains("_R") ? last : null;
    }
}

package com.dank420girl.core.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Locale;

/**
 * Version independent access to online players.
 */
public final class Players {
    private static volatile Method legacyOnlinePlayers;

    private Players() {
    }

    /**
     * A snapshot of the online players. Early 1.7 builds returned an array instead of a collection,
     * and on Folia the list can change while it is copied, which is retried.
     */
    public static List<Player> online() {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return copy();
            } catch (ConcurrentModificationException retry) {
                // another region thread changed the list
            }
        }
        return Collections.emptyList();
    }

    private static List<Player> copy() {
        Method legacy = legacyOnlinePlayers;
        if (legacy == null) {
            try {
                Collection<? extends Player> players = Bukkit.getOnlinePlayers();
                return new ArrayList<Player>(players);
            } catch (NoSuchMethodError e) {
                legacy = Reflect.findMethod(Bukkit.class, "getOnlinePlayers");
                legacyOnlinePlayers = legacy;
            }
        }
        if (legacy == null) {
            return Collections.emptyList();
        }
        Object result = Reflect.invoke(legacy, null);
        if (result instanceof Player[]) {
            return new ArrayList<Player>(Arrays.asList((Player[]) result));
        }
        if (result instanceof Collection) {
            List<Player> list = new ArrayList<Player>();
            for (Object o : (Collection<?>) result) {
                list.add((Player) o);
            }
            return list;
        }
        return Collections.emptyList();
    }

    /** Finds an online player by exact name, ignoring case, falling back to the closest match. */
    public static Player find(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) {
            return exact;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        Player match = null;
        int bestLength = Integer.MAX_VALUE;
        for (Player player : online()) {
            String playerName = player.getName();
            if (playerName.equalsIgnoreCase(name)) {
                return player;
            }
            if (playerName.toLowerCase(Locale.ROOT).startsWith(lower) && playerName.length() < bestLength) {
                match = player;
                bestLength = playerName.length();
            }
        }
        return match;
    }

    /** Names of online players starting with the prefix, for tab completion. */
    public static List<String> namesStartingWith(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<String>();
        for (Player player : online()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                names.add(player.getName());
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }
}

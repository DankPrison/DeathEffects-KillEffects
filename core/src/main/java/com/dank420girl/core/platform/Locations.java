package com.dank420girl.core.platform;

import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Method;

/**
 * Location helpers that work on every version.
 */
public final class Locations {
    private static final Method GET_MIN_HEIGHT = Reflect.findMethod(World.class, "getMinHeight");

    private Locations() {
    }

    /** The lowest block height of the world: 0 before 1.17, configurable since. */
    public static int minHeight(World world) {
        if (GET_MIN_HEIGHT == null) {
            return 0;
        }
        try {
            return (Integer) GET_MIN_HEIGHT.invoke(world);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    /**
     * A copy of the location that lies inside the world: someone who died in the void gets their effect at
     * the bottom of the world instead of far below it, where nobody could see it.
     */
    public static Location visible(Location location) {
        Location copy = location.clone();
        World world = copy.getWorld();
        if (world != null) {
            int min = minHeight(world);
            if (copy.getY() < min) {
                copy.setY(min + 1);
            }
            if (copy.getY() > world.getMaxHeight() + 16) {
                copy.setY(world.getMaxHeight());
            }
        }
        return copy;
    }
}

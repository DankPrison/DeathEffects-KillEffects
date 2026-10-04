package com.dank420girl.core.compat;

import com.dank420girl.core.platform.Debug;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Talks to the other plugin of the pair (DeathEffects or KillEffects) through its public methods.
 * <p>
 * Each plugin carries its own copy of the shared code, so they cannot share classes; reflection on
 * the other plugin's main class keeps them independent of each other's version. If the other plugin
 * is missing, disabled or too old, every answer is "no".
 */
public final class OtherPlugin {
    private final String name;
    private final Logger logger;

    public OtherPlugin(String name, Logger logger) {
        this.name = name;
        this.logger = logger;
    }

    /** Calls a public method of the other plugin that returns a boolean; {@code false} if that is not possible. */
    public boolean ask(String method, Class<?>[] parameterTypes, Object... args) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        if (plugin == null || !plugin.isEnabled()) {
            return false;
        }
        try {
            Method m = plugin.getClass().getMethod(method, parameterTypes);
            Object result = m.invoke(plugin, args);
            return result instanceof Boolean && (Boolean) result;
        } catch (NoSuchMethodException e) {
            return false; // an old version without this method
        } catch (InvocationTargetException e) {
            Debug.logRepeating(logger, "ask " + method, name + "." + method + " failed", e.getCause());
            return false;
        } catch (IllegalAccessException | RuntimeException e) {
            Debug.logRepeating(logger, "call " + method, "Cannot call " + name + "." + method, e);
            return false;
        }
    }
}

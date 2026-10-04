package com.dank420girl.core.effect;

import com.dank420girl.core.names.Names.SoundName;
import com.dank420girl.core.platform.Debug;
import com.dank420girl.core.platform.Reflect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Plays sounds to players. Sounds are sent to each listener separately, which respects private
 * previews and is safe on Folia.
 * <p>
 * {@code org.bukkit.Sound} was an enum until 1.21.2 and is an interface since; it is only ever looked
 * up by name and passed on, which works with both.
 */
public final class SoundService {
    private final Logger logger;
    private final ConcurrentHashMap<String, Object> constants = new ConcurrentHashMap<String, Object>();
    private static final Object MISSING = new Object();

    public SoundService(Logger logger) {
        this.logger = logger;
    }

    /** True if the server has a Sound constant with this name. */
    public boolean exists(String name) {
        return constant(name) != null;
    }

    /**
     * True if the server knows a sound by its key, such as {@code entity.player.levelup}. Servers
     * before 1.16 cannot tell and always answer {@code false}.
     */
    public boolean keyExists(String key) {
        Set<String> known = keys;
        if (known == null) {
            known = loadKeys();
            keys = known;
        }
        return known.contains(key);
    }

    private volatile Set<String> keys;

    private Set<String> loadKeys() {
        Set<String> result = new HashSet<String>();
        Method getKey = Reflect.findMethod(Sound.class, "getKey");
        if (getKey == null) {
            return result;
        }
        try {
            Method path = null;
            for (Field field : Sound.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !Sound.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                Object key = getKey.invoke(field.get(null));
                if (key == null) {
                    continue;
                }
                if (path == null) {
                    path = key.getClass().getMethod("getKey");
                }
                result.add(String.valueOf(path.invoke(key)));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Debug.log(logger, "Could not list the sounds of the server", e);
        }
        return result;
    }

    private Object constant(String name) {
        Object value = constants.get(name);
        if (value == null) {
            value = Reflect.enumConstant(Sound.class, name);
            constants.put(name, value == null ? MISSING : value);
        }
        return value == MISSING ? null : value;
    }

    public void play(List<Player> listeners, Location location, SoundSpec spec) {
        for (Player player : listeners) {
            play(player, location, spec);
        }
    }

    /** Plays the sound to one player at the given location. */
    public void play(Player player, Location location, SoundSpec spec) {
        if (spec == null) {
            return;
        }
        SoundName sound = spec.sound();
        try {
            if (sound.isConstant()) {
                Object constant = constant(sound.name());
                if (constant != null) {
                    player.playSound(location, (Sound) constant, spec.volume(), spec.pitch());
                }
            } else {
                player.playSound(location, sound.name(), spec.volume(), spec.pitch());
            }
        } catch (RuntimeException | LinkageError e) {
            Debug.logRepeating(logger, "sound", "Could not play " + spec + " to " + player.getName(), e);
        }
    }
}

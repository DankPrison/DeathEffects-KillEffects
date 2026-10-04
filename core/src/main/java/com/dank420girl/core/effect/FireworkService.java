package com.dank420girl.core.effect;

import com.dank420girl.core.platform.Debug;
import com.dank420girl.core.platform.Reflect;
import com.dank420girl.core.platform.scheduler.Scheduler;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Launches firework explosions. Fireworks are tagged so their explosions never hurt anybody, and are
 * detonated right away with {@code Firework#detonate()}. Servers without that method get the
 * explosion status sent to clients, falling back to a very short flight.
 */
public final class FireworkService {
    /** Entity status that makes clients show a firework explosion. */
    private static final byte EXPLODE_STATUS = 17;

    private final Scheduler scheduler;
    private final Logger logger;
    /** Our fireworks, which must not hurt anybody; each is forgotten shortly after it exploded. */
    private final Set<UUID> ours = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Method detonate;
    private volatile boolean legacyExplosionBroken;

    public FireworkService(Scheduler scheduler, Logger logger) {
        this.scheduler = scheduler;
        this.logger = logger;
        this.detonate = Reflect.findMethod(Firework.class, "detonate");
    }

    /** True if the entity is one of our fireworks, whose damage must be cancelled. */
    public boolean isOurs(Entity entity) {
        return entity instanceof Firework && ours.contains(entity.getUniqueId());
    }

    /** Launches a firework at the location. Must be called on the thread owning the location. */
    public void launch(Location location, FireworkEffect effect, boolean instant) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        final Firework firework = world.spawn(location, Firework.class);
        final UUID id = firework.getUniqueId();
        ours.add(id);
        // Long enough for fireworks that fly before exploding.
        scheduler.runAtLater(location, new Runnable() {
            @Override
            public void run() {
                ours.remove(id);
            }
        }, 100L);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.clearEffects();
        meta.addEffect(effect);
        meta.setPower(0);
        firework.setFireworkMeta(meta);
        if (!instant) {
            return;
        }
        if (detonate != null) {
            try {
                Reflect.invoke(detonate, firework);
                return;
            } catch (RuntimeException e) {
                Debug.logRepeating(logger, "detonate", "Firework#detonate failed", e);
            }
        }
        if (legacyExplosionBroken) {
            return;
        }
        // Clients need to know the entity before it can explode, so wait two ticks.
        scheduler.runAtLater(location, new Runnable() {
            @Override
            public void run() {
                explodeLegacy(firework);
            }
        }, 2L);
    }

    private void explodeLegacy(Firework firework) {
        if (!firework.isValid()) {
            return;
        }
        try {
            World world = firework.getWorld();
            Object worldHandle = world.getClass().getMethod("getHandle").invoke(world);
            Object entityHandle = firework.getClass().getMethod("getHandle").invoke(firework);
            Method broadcast = null;
            for (Method method : worldHandle.getClass().getMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (method.getName().equals("broadcastEntityEffect") && types.length == 2 && types[1] == byte.class
                        && types[0].isInstance(entityHandle)) {
                    broadcast = method;
                    break;
                }
            }
            if (broadcast == null) {
                throw new NoSuchMethodException("broadcastEntityEffect");
            }
            broadcast.invoke(worldHandle, entityHandle, EXPLODE_STATUS);
            firework.remove();
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Let the remaining fireworks fly and explode on their own.
            legacyExplosionBroken = true;
            Debug.log(logger, "Cannot detonate fireworks instantly on this server", e);
        }
    }
}

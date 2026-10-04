package com.dank420girl.core.effect.particle;

import com.dank420girl.core.names.NameTables;
import com.dank420girl.core.names.NameTables.ParticleEntry;
import com.dank420girl.core.names.Names.ItemName;
import com.dank420girl.core.platform.Debug;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Fallback for 1.7 and 1.8 servers whose internals differ from CraftBukkit (for example modded
 * hybrids): Spigot's old particle method {@code World.spigot().playEffect(...)}. It shows particles
 * to everybody near the location, so private previews are visible to others on such servers.
 */
final class SpigotEffectOutput implements ParticleOutput {
    private final Logger logger;
    private final Map<String, Effect> effectsByParticleName = new HashMap<String, Effect>();
    private final Method worldSpigot;
    private final Method playEffect;
    private final int radius;

    SpigotEffectOutput(Logger logger, int radius) throws ReflectiveOperationException {
        this.logger = logger;
        this.radius = radius;
        this.worldSpigot = World.class.getMethod("spigot");
        Class<?> spigotClass = worldSpigot.getReturnType();
        this.playEffect = spigotClass.getMethod("playEffect", Location.class, Effect.class, int.class, int.class,
                float.class, float.class, float.class, float.class, int.class, int.class);
        Method getName = Effect.class.getMethod("getName");
        for (Effect effect : Effect.values()) {
            Object name = getName.invoke(effect);
            if (name instanceof String) {
                effectsByParticleName.put((String) name, effect);
            }
        }
        if (effectsByParticleName.isEmpty()) {
            throw new NoSuchMethodException("Spigot particle effects");
        }
        // Effects are named like the 1.7 particles; 1.8 servers know particles by their 1.8 names.
        for (ParticleEntry entry : NameTables.get().particles()) {
            Effect effect = entry.name17 == null ? null : effectsByParticleName.get(entry.name17);
            if (effect != null && entry.nms18 != null && !effectsByParticleName.containsKey(entry.nms18)) {
                effectsByParticleName.put(entry.nms18, effect);
            }
        }
    }

    @Override
    public String name() {
        return "Spigot effect API";
    }

    @Override
    public boolean exists(String particleName) {
        return effectsByParticleName.containsKey(particleName);
    }

    private static final class Prepared {
        final Effect effect;
        final int id;
        final int data;

        Prepared(Effect effect, int id, int data) {
            this.effect = effect;
            this.id = id;
            this.data = data;
        }
    }

    @Override
    public boolean prepare(ParticleSpec spec) {
        if (spec.prepared instanceof Prepared) {
            return true;
        }
        Effect effect = effectsByParticleName.get(spec.particle().name());
        if (effect == null) {
            return false;
        }
        int id = 0;
        int data = 0;
        ParticleEntry.Kind kind = spec.kind();
        if (kind == ParticleEntry.Kind.ITEM || kind == ParticleEntry.Kind.BLOCK) {
            ItemName item = spec.item() != null ? spec.item() : ItemName.of("STONE", 0);
            Material material = Material.getMaterial(item.name());
            if (material == null) {
                return false;
            }
            id = legacyId(material);
            data = item.data();
        }
        spec.prepared = new Prepared(effect, id, data);
        return true;
    }

    @SuppressWarnings("deprecation")
    private static int legacyId(Material material) {
        return material.getId();
    }

    @Override
    public void send(List<Player> viewers, double x, double y, double z, ParticleSpec spec, int count,
                     double offsetX, double offsetY, double offsetZ, double speed, int rgb, int rgb2, boolean force,
                     Random random) {
        Object state = spec.prepared;
        if (!(state instanceof Prepared) || viewers.isEmpty()) {
            return;
        }
        Prepared prepared = (Prepared) state;
        World world = viewers.get(0).getWorld();
        try {
            Object spigot = worldSpigot.invoke(world);
            ParticleEntry.Kind kind = spec.kind();
            if (spec.color() != null && (kind == ParticleEntry.Kind.DUST || kind == ParticleEntry.Kind.SPELL)) {
                float r = Math.max(((rgb >> 16) & 0xFF) / 255f, 0.0001f);
                float g = ((rgb >> 8) & 0xFF) / 255f;
                float b = (rgb & 0xFF) / 255f;
                for (int i = 0; i < Math.max(1, Math.min(count, 64)); i++) {
                    Location location = new Location(world, x + random.nextGaussian() * offsetX,
                            y + random.nextGaussian() * offsetY, z + random.nextGaussian() * offsetZ);
                    playEffect.invoke(spigot, location, prepared.effect, 0, 0, r, g, b, 1f, 0, radius);
                }
                return;
            }
            playEffect.invoke(spigot, new Location(world, x, y, z), prepared.effect, prepared.id, prepared.data,
                    (float) offsetX, (float) offsetY, (float) offsetZ, (float) speed, count, radius);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Debug.logRepeating(logger, "spigot effect", "Could not play a Spigot particle effect", e);
        }
    }
}

package com.dank420girl.core.effect.particle;

import com.dank420girl.core.names.NameTables.ParticleEntry;
import com.dank420girl.core.names.Names.ItemName;
import com.dank420girl.core.platform.Debug;
import com.dank420girl.core.platform.MinecraftVersion;
import com.dank420girl.core.platform.Reflect;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Particles through Bukkit's particle API (Minecraft 1.9 and newer), reached through reflection.
 * <p>
 * The extra data a particle needs is chosen from {@code Particle#getDataType()}, so particles and
 * data types added by future versions keep working as long as their data can be built from an
 * item, a block, colours or a number.
 */
final class ModernParticleOutput implements ParticleOutput {
    private final Logger logger;
    private final Class<?> particleClass;
    private final Method spawn;
    private final Method spawnForced;
    private final Method getDataType;
    /** Before 1.13 dust had no data and was coloured through the offsets; the same for mob spells before 1.20.5. */
    private final boolean dustByOffsets;
    private final boolean spellByOffsets;

    private final Constructor<?> dustOptions;
    private final Constructor<?> dustTransition;
    private final Constructor<?> spell;
    private final Constructor<?> trail;
    private final Constructor<?> materialData;
    private final Method createBlockData;
    private final Method isBlock;

    ModernParticleOutput(Logger logger, MinecraftVersion version) throws ReflectiveOperationException {
        this.logger = logger;
        this.particleClass = Class.forName("org.bukkit.Particle");
        this.spawn = Player.class.getMethod("spawnParticle", particleClass, double.class, double.class, double.class,
                int.class, double.class, double.class, double.class, double.class, Object.class);
        this.spawnForced = Reflect.findMethod(Player.class, "spawnParticle", particleClass, double.class, double.class,
                double.class, int.class, double.class, double.class, double.class, double.class, Object.class, boolean.class);
        this.getDataType = particleClass.getMethod("getDataType");
        this.dustByOffsets = version.isBefore(1, 13);
        this.spellByOffsets = version.isBefore(1, 20, 5);

        Class<?> dustOptionsClass = Reflect.findClass("org.bukkit.Particle$DustOptions");
        this.dustOptions = Reflect.findConstructor(dustOptionsClass, Color.class, float.class);
        this.dustTransition = Reflect.findConstructor(Reflect.findClass("org.bukkit.Particle$DustTransition"),
                Color.class, Color.class, float.class);
        this.spell = Reflect.findConstructor(Reflect.findClass("org.bukkit.Particle$Spell"), Color.class, float.class);
        this.trail = Reflect.findConstructor(Reflect.findClass("org.bukkit.Particle$Trail"), Location.class, Color.class, int.class);
        this.materialData = Reflect.findConstructor(Reflect.findClass("org.bukkit.material.MaterialData"), Material.class, byte.class);
        this.createBlockData = Reflect.findMethod(Material.class, "createBlockData");
        this.isBlock = Reflect.findMethod(Material.class, "isBlock");
    }

    @Override
    public String name() {
        return "Bukkit particle API";
    }

    @Override
    public boolean exists(String particleName) {
        return Reflect.enumConstant(particleClass, particleName) != null;
    }

    /** How the extra data of a particle is produced. */
    private enum Mode {
        PLAIN, COLOR_OFFSETS, NOTE_OFFSETS, STATIC_DATA, DUST, DUST_TRANSITION, COLOR, SPELL, TRAIL
    }

    private static final class Prepared {
        final Object particle;
        final Mode mode;
        final Object data;
        /** The colour data built last; particles with a fixed colour reuse it. */
        volatile ColorData lastColor;

        Prepared(Object particle, Mode mode, Object data) {
            this.particle = particle;
            this.mode = mode;
            this.data = data;
        }
    }

    private static final class ColorData {
        final int rgb;
        final int rgb2;
        final Object data;

        ColorData(int rgb, int rgb2, Object data) {
            this.rgb = rgb;
            this.rgb2 = rgb2;
            this.data = data;
        }
    }

    @Override
    public boolean prepare(ParticleSpec spec) {
        if (spec.prepared instanceof Prepared) {
            return true;
        }
        Object particle = Reflect.enumConstant(particleClass, spec.particle().name());
        if (particle == null) {
            return false;
        }
        Class<?> type = (Class<?>) Reflect.invoke(getDataType, particle);
        Prepared prepared;
        try {
            prepared = prepare(spec, particle, type);
        } catch (RuntimeException e) {
            Debug.log(logger, "Cannot prepare particle " + spec, e);
            prepared = null;
        }
        if (prepared == null) {
            return false;
        }
        spec.prepared = prepared;
        return true;
    }

    private Prepared prepare(ParticleSpec spec, Object particle, Class<?> type) {
        String typeName = type == null ? "Void" : type.getSimpleName();
        if (type == null || type == Void.class || typeName.equals("Void")) {
            ParticleEntry.Kind kind = spec.kind();
            if (spec.color() != null && ((kind == ParticleEntry.Kind.DUST && dustByOffsets)
                    || (kind == ParticleEntry.Kind.SPELL && spellByOffsets))) {
                return new Prepared(particle, Mode.COLOR_OFFSETS, null);
            }
            if (kind == ParticleEntry.Kind.NOTE && spec.number() != null) {
                return new Prepared(particle, Mode.NOTE_OFFSETS, null);
            }
            return new Prepared(particle, Mode.PLAIN, null);
        }
        if (type == ItemStack.class) {
            ItemName item = spec.item() != null ? spec.item() : ItemName.of("STONE", 0);
            Material material = Material.getMaterial(item.name());
            if (material == null) {
                return null;
            }
            return new Prepared(particle, Mode.STATIC_DATA, new ItemStack(material, 1, (short) item.data()));
        }
        if (typeName.equals("MaterialData")) {
            Material material = blockMaterial(spec);
            if (material == null || materialData == null) {
                return null;
            }
            return new Prepared(particle, Mode.STATIC_DATA, Reflect.construct(materialData, material,
                    (byte) (spec.item() == null ? 0 : spec.item().data())));
        }
        if (typeName.equals("BlockData")) {
            Material material = blockMaterial(spec);
            if (material == null || createBlockData == null) {
                return null;
            }
            return new Prepared(particle, Mode.STATIC_DATA, Reflect.invoke(createBlockData, material));
        }
        if (typeName.equals("DustOptions") && dustOptions != null) {
            return new Prepared(particle, Mode.DUST, null);
        }
        if (typeName.equals("DustTransition") && dustTransition != null) {
            return new Prepared(particle, Mode.DUST_TRANSITION, null);
        }
        if (type == Color.class) {
            return new Prepared(particle, Mode.COLOR, null);
        }
        if (typeName.equals("Spell") && spell != null) {
            return new Prepared(particle, Mode.SPELL, null);
        }
        if (typeName.equals("Trail") && trail != null) {
            return new Prepared(particle, Mode.TRAIL, null);
        }
        if (type == Float.class) {
            float value = spec.number() != null ? spec.number() : spec.particle().canonical().equals("SCULK_CHARGE") ? 0f : 1f;
            return new Prepared(particle, Mode.STATIC_DATA, value);
        }
        if (type == Integer.class) {
            int value = spec.number() != null ? Math.round(spec.number()) : 0;
            return new Prepared(particle, Mode.STATIC_DATA, value);
        }
        // Unknown data type of a newer version: try the simplest constructors.
        Object data = constructGeneric(type, spec);
        return data == null ? null : new Prepared(particle, Mode.STATIC_DATA, data);
    }

    private Material blockMaterial(ParticleSpec spec) {
        ItemName item = spec.item() != null ? spec.item() : ItemName.of("STONE", 0);
        Material material = Material.getMaterial(item.name());
        if (material == null) {
            return null;
        }
        if (isBlock != null && !(Boolean) Reflect.invoke(isBlock, material)) {
            return Material.getMaterial("STONE");
        }
        return material;
    }

    private static Object constructGeneric(Class<?> type, ParticleSpec spec) {
        int number = spec.number() == null ? 1 : Math.round(spec.number());
        Constructor<?> c = Reflect.findConstructor(type, int.class, float.class);
        if (c != null) {
            return Reflect.construct(c, number, 1f);
        }
        c = Reflect.findConstructor(type, int.class);
        if (c != null) {
            return Reflect.construct(c, number);
        }
        c = Reflect.findConstructor(type, float.class);
        if (c != null) {
            return Reflect.construct(c, (float) number);
        }
        c = Reflect.findConstructor(type);
        return c == null ? null : Reflect.construct(c);
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
        switch (prepared.mode) {
            case COLOR_OFFSETS:
                sendColoredByOffsets(viewers, prepared.particle, x, y, z, count, offsetX, offsetY, offsetZ, rgb, force, random);
                return;
            case NOTE_OFFSETS:
                double note = Math.max(0, Math.min(24, spec.number())) / 24.0;
                for (int i = 0; i < Math.max(1, count); i++) {
                    double px = x + spread(offsetX, random);
                    double py = y + spread(offsetY, random);
                    double pz = z + spread(offsetZ, random);
                    spawn(viewers, prepared.particle, px, py, pz, 0, note, 0, 0, 1, null, force);
                }
                return;
            default:
                spawn(viewers, prepared.particle, x, y, z, count, offsetX, offsetY, offsetZ, speed,
                        data(prepared, spec, rgb, rgb2, viewers.get(0), x, y, z, random), force);
        }
    }

    private Object data(Prepared prepared, ParticleSpec spec, int rgb, int rgb2, Player viewer, double x, double y, double z,
                        Random random) {
        switch (prepared.mode) {
            case STATIC_DATA:
                return prepared.data;
            case DUST:
            case DUST_TRANSITION:
            case COLOR:
            case SPELL:
                ColorData last = prepared.lastColor;
                if (last != null && last.rgb == rgb && last.rgb2 == rgb2) {
                    return last.data;
                }
                Object data = colorData(prepared.mode, spec, rgb, rgb2);
                prepared.lastColor = new ColorData(rgb, rgb2, data);
                return data;
            case TRAIL:
                // A trail flies from where it spawns to a target; aim it a little upwards.
                return Reflect.construct(trail, new Location(viewer.getWorld(), x + random.nextGaussian() * 0.5, y + 1.5,
                        z + random.nextGaussian() * 0.5), Color.fromRGB(rgb), 20);
            default:
                return null;
        }
    }

    private Object colorData(Mode mode, ParticleSpec spec, int rgb, int rgb2) {
        switch (mode) {
            case DUST:
                return Reflect.construct(dustOptions, Color.fromRGB(rgb), spec.size());
            case DUST_TRANSITION:
                return Reflect.construct(dustTransition, Color.fromRGB(rgb), Color.fromRGB(rgb2), spec.size());
            case SPELL:
                return Reflect.construct(spell, Color.fromRGB(rgb), spec.number() == null ? 1f : spec.number());
            default:
                return Color.fromRGB(rgb);
        }
    }

    /** Dust and mob spells before they had colour data: each particle is sent alone, with the colour as its "velocity". */
    private void sendColoredByOffsets(List<Player> viewers, Object particle, double x, double y, double z, int count,
                                      double offsetX, double offsetY, double offsetZ, int rgb, boolean force, Random random) {
        double r = Math.max(((rgb >> 16) & 0xFF) / 255.0, 0.0001); // 0 would mean "default red"
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        int amount = Math.max(1, Math.min(count, 64));
        for (int i = 0; i < amount; i++) {
            spawn(viewers, particle, x + spread(offsetX, random), y + spread(offsetY, random), z + spread(offsetZ, random),
                    0, r, g, b, 1, null, force);
        }
    }

    private static double spread(double offset, Random random) {
        return offset == 0 ? 0 : random.nextGaussian() * offset;
    }

    private void spawn(List<Player> viewers, Object particle, double x, double y, double z, int count,
                       double offsetX, double offsetY, double offsetZ, double speed, Object data, boolean force) {
        boolean useForce = force && spawnForced != null;
        for (Player player : viewers) {
            try {
                if (useForce) {
                    spawnForced.invoke(player, particle, x, y, z, count, offsetX, offsetY, offsetZ, speed, data, true);
                } else {
                    spawn.invoke(player, particle, x, y, z, count, offsetX, offsetY, offsetZ, speed, data);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                Debug.logRepeating(logger, "particle send", "Could not send a particle to " + player.getName(), e);
            }
        }
    }
}

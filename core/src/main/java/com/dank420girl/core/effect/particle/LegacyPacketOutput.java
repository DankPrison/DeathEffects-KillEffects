package com.dank420girl.core.effect.particle;

import com.dank420girl.core.names.NameTables;
import com.dank420girl.core.names.NameTables.ParticleEntry;
import com.dank420girl.core.names.Names.ItemName;
import com.dank420girl.core.platform.Debug;
import com.dank420girl.core.platform.Reflect;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Particles for Minecraft 1.7 and 1.8, which have no particle API: the particle packet is built and
 * sent through reflection, which works on CraftBukkit as well as Spigot.
 */
final class LegacyPacketOutput implements ParticleOutput {
    private final Logger logger;
    private final boolean is17;
    private final Constructor<?> packetConstructor;
    private final Class<?> enumParticle;
    private final Set<String> names17 = new HashSet<String>();

    // Taken from the base classes, so subclasses (for example the fake players of NPC plugins) work too.
    private final Method getHandle;
    private final Field playerConnection;
    private final Method sendPacket;

    LegacyPacketOutput(Logger logger, String nmsVersion, boolean is17) throws ReflectiveOperationException {
        this.logger = logger;
        this.is17 = is17;
        String nms = "net.minecraft.server." + nmsVersion + ".";
        this.getHandle = Class.forName("org.bukkit.craftbukkit." + nmsVersion + ".entity.CraftPlayer").getMethod("getHandle");
        this.playerConnection = Class.forName(nms + "EntityPlayer").getField("playerConnection");
        this.sendPacket = Class.forName(nms + "PlayerConnection").getMethod("sendPacket", Class.forName(nms + "Packet"));
        Class<?> packetClass = Class.forName(nms + "PacketPlayOutWorldParticles");
        if (is17) {
            this.enumParticle = null;
            this.packetConstructor = packetClass.getConstructor(String.class, float.class, float.class, float.class,
                    float.class, float.class, float.class, float.class, int.class);
            for (ParticleEntry entry : NameTables.get().particles()) {
                if (entry.name17 != null) {
                    names17.add(entry.name17);
                }
            }
        } else {
            this.enumParticle = Class.forName(nms + "EnumParticle");
            this.packetConstructor = packetClass.getConstructor(enumParticle, boolean.class, float.class, float.class,
                    float.class, float.class, float.class, float.class, float.class, int.class, int[].class);
        }
    }

    @Override
    public String name() {
        return is17 ? "1.7 particle packets" : "1.8 particle packets";
    }

    @Override
    public boolean exists(String particleName) {
        return is17 ? names17.contains(particleName) : Reflect.enumConstant(enumParticle, particleName) != null;
    }

    private static final class Prepared {
        final Object particle; // EnumParticle on 1.8, the packet name on 1.7
        final int[] data;      // extra data on 1.8

        Prepared(Object particle, int[] data) {
            this.particle = particle;
            this.data = data;
        }
    }

    @Override
    public boolean prepare(ParticleSpec spec) {
        if (spec.prepared instanceof Prepared) {
            return true;
        }
        ParticleEntry.Kind kind = spec.kind();
        int id = 0;
        int data = 0;
        if (kind == ParticleEntry.Kind.ITEM || kind == ParticleEntry.Kind.BLOCK) {
            ItemName item = spec.item() != null ? spec.item() : ItemName.of("STONE", 0);
            Material material = Material.getMaterial(item.name());
            if (material == null) {
                return false;
            }
            id = legacyId(material);
            data = item.data();
        }
        Prepared prepared;
        if (is17) {
            String name = spec.particle().name();
            if (kind == ParticleEntry.Kind.ITEM || kind == ParticleEntry.Kind.BLOCK) {
                name = name + "_" + id + "_" + data;
            }
            prepared = new Prepared(name, null);
        } else {
            Object particle = Reflect.enumConstant(enumParticle, spec.particle().name());
            if (particle == null) {
                return false;
            }
            int[] extra;
            if (kind == ParticleEntry.Kind.ITEM) {
                extra = new int[] {id, data};
            } else if (kind == ParticleEntry.Kind.BLOCK) {
                extra = new int[] {id | (data << 12)};
            } else {
                extra = new int[0];
            }
            prepared = new Prepared(particle, extra);
        }
        spec.prepared = prepared;
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
        ParticleEntry.Kind kind = spec.kind();
        if (spec.color() != null && (kind == ParticleEntry.Kind.DUST || kind == ParticleEntry.Kind.SPELL)) {
            float r = Math.max(((rgb >> 16) & 0xFF) / 255f, 0.0001f); // 0 would mean "default red"
            float g = ((rgb >> 8) & 0xFF) / 255f;
            float b = (rgb & 0xFF) / 255f;
            int amount = Math.max(1, Math.min(count, 64));
            for (int i = 0; i < amount; i++) {
                sendPacket(viewers, packet(prepared, force, x + spread(offsetX, random), y + spread(offsetY, random),
                        z + spread(offsetZ, random), r, g, b, 1f, 0));
            }
            return;
        }
        if (kind == ParticleEntry.Kind.NOTE && spec.number() != null) {
            float note = (float) (Math.max(0, Math.min(24, spec.number())) / 24.0);
            for (int i = 0; i < Math.max(1, count); i++) {
                sendPacket(viewers, packet(prepared, force, x + spread(offsetX, random), y + spread(offsetY, random),
                        z + spread(offsetZ, random), note, 0, 0, 1f, 0));
            }
            return;
        }
        sendPacket(viewers, packet(prepared, force, x, y, z, (float) offsetX, (float) offsetY, (float) offsetZ, (float) speed, count));
    }

    private static double spread(double offset, Random random) {
        return offset == 0 ? 0 : random.nextGaussian() * offset;
    }

    private Object packet(Prepared prepared, boolean force, double x, double y, double z,
                          float offsetX, float offsetY, float offsetZ, float speed, int count) {
        if (is17) {
            return Reflect.construct(packetConstructor, prepared.particle, (float) x, (float) y, (float) z,
                    offsetX, offsetY, offsetZ, speed, count);
        }
        return Reflect.construct(packetConstructor, prepared.particle, force, (float) x, (float) y, (float) z,
                offsetX, offsetY, offsetZ, speed, count, prepared.data);
    }

    private void sendPacket(List<Player> viewers, Object packet) {
        for (Player player : viewers) {
            try {
                Object connection = playerConnection.get(getHandle.invoke(player));
                if (connection != null) {
                    sendPacket.invoke(connection, packet);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                Debug.logRepeating(logger, "particle packet", "Could not send a particle packet to " + player.getName(), e);
            }
        }
    }
}

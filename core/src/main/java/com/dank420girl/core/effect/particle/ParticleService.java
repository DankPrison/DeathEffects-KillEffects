package com.dank420girl.core.effect.particle;

import com.dank420girl.core.names.NameTables.ParticleEntry;
import com.dank420girl.core.platform.Debug;
import com.dank420girl.core.platform.MinecraftVersion;
import com.dank420girl.core.platform.Platform;
import com.dank420girl.core.platform.Players;
import com.dank420girl.core.platform.scheduler.Scheduler;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shows particles to the players near a location, using the best output the server supports.
 */
public final class ParticleService {
    private static final int RED = 0xFF0000;
    private static final int WHITE = 0xFFFFFF;
    /** Beyond this distance clients only render particles sent with the "long distance" flag. */
    private static final double CLIENT_RANGE = 32;

    private final Logger logger;
    private final Scheduler scheduler;
    private final ParticleOutput output;

    private volatile double viewDistance = 32;
    private volatile double multiplier = 1;

    public ParticleService(Logger logger, Scheduler scheduler, MinecraftVersion version) {
        this(logger, scheduler, createOutput(logger, version));
    }

    /** Uses the given output, for example in tests. */
    public ParticleService(Logger logger, Scheduler scheduler, ParticleOutput output) {
        this.logger = logger;
        this.scheduler = scheduler;
        this.output = output;
    }

    private static ParticleOutput createOutput(Logger logger, MinecraftVersion version) {
        if (version.isAtLeast(1, 9)) {
            try {
                return new ModernParticleOutput(logger, version);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                logger.log(Level.WARNING, "Bukkit's particle API is not available on this server", e);
            }
        } else {
            String nms = Platform.craftBukkitPackageVersion();
            if (nms != null) {
                try {
                    return new LegacyPacketOutput(logger, nms, version.isBefore(1, 8));
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    Debug.log(logger, "Particle packets are not available, trying Spigot's effect API", e);
                }
            }
            try {
                return new SpigotEffectOutput(logger, 32);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                Debug.log(logger, "Spigot's effect API is not available", e);
            }
        }
        logger.warning("This server offers no way to show particles; particle effects are disabled. "
                + "Sounds, fireworks and lightning still work.");
        return new NoOutput();
    }

    /** Used when the server has no way to show particles. */
    private static final class NoOutput implements ParticleOutput {
        @Override
        public String name() {
            return "none";
        }

        @Override
        public boolean exists(String particleName) {
            return false;
        }

        @Override
        public boolean prepare(ParticleSpec spec) {
            return false;
        }

        @Override
        public void send(List<Player> viewers, double x, double y, double z, ParticleSpec spec, int count,
                         double offsetX, double offsetY, double offsetZ, double speed, int rgb, int rgb2,
                         boolean force, Random random) {
        }
    }

    public void configure(double viewDistance, double multiplier) {
        this.viewDistance = Math.max(1, viewDistance);
        this.multiplier = Math.max(0, multiplier);
    }

    public String outputName() {
        return output.name();
    }

    /** False if this server offers no way to show particles at all (this was logged once already). */
    public boolean isAvailable() {
        return !(output instanceof NoOutput);
    }

    /** True if the server knows a particle by this name; used to translate names. */
    public boolean exists(String particleName) {
        return output.exists(particleName);
    }

    public double multiplier() {
        return multiplier;
    }

    /**
     * Prepares the particle for this server.
     *
     * @return {@code false} if it cannot be shown here
     */
    public boolean prepare(ParticleSpec spec) {
        try {
            return output.prepare(spec);
        } catch (RuntimeException | LinkageError e) {
            Debug.log(logger, "Cannot prepare " + spec, e);
            return false;
        }
    }

    /**
     * The players that should see particles at the location: everyone in range, or only the given player.
     * On Folia only players owned by the current region thread can be reached.
     */
    public List<Player> viewers(Location location, Player onlyFor) {
        World world = location.getWorld();
        if (world == null) {
            return Collections.emptyList();
        }
        double maxDistanceSquared = viewDistance * viewDistance;
        if (onlyFor != null) {
            if (onlyFor.isOnline() && onlyFor.getWorld().equals(world) && canReach(onlyFor)
                    && onlyFor.getLocation().distanceSquared(location) <= maxDistanceSquared) {
                return Collections.singletonList(onlyFor);
            }
            return Collections.emptyList();
        }
        List<Player> candidates = Platform.isFolia() ? Players.online() : world.getPlayers();
        List<Player> viewers = new ArrayList<Player>(Math.min(candidates.size(), 16));
        for (Player player : candidates) {
            // Cheap checks first; on Folia the ownership check is the expensive one.
            if (!world.equals(player.getWorld()) || player.getLocation().distanceSquared(location) > maxDistanceSquared) {
                continue;
            }
            // NPC plugins such as Citizens mark their fake players; they cannot see anything.
            if (player.hasMetadata("NPC") || !canReach(player)) {
                continue;
            }
            viewers.add(player);
        }
        return viewers;
    }

    private boolean canReach(Player player) {
        return !Platform.isFolia() || scheduler.isOwnedByCurrentThread(player);
    }

    /**
     * Shows a burst of particles; the count is scaled by the particle multiplier.
     *
     * @param step counter for cycling and rainbow colours
     */
    public void burst(List<Player> viewers, double x, double y, double z, ParticleSpec spec, int count,
                      double offsetX, double offsetY, double offsetZ, double speed, long step, Random random) {
        if (viewers.isEmpty()) {
            return;
        }
        int scaled = scale(count, random);
        if (scaled <= 0 && count > 0) {
            return;
        }
        if (spec.hasChangingColor() && scaled > 1) {
            // One colour per packet: split the burst into a few groups with their own colour.
            int groups = Math.min(scaled, 8);
            int perGroup = Math.max(1, scaled / groups);
            for (int i = 0; i < groups; i++) {
                send(viewers, x, y, z, spec, perGroup, offsetX, offsetY, offsetZ, speed, step + i, random);
            }
            return;
        }
        send(viewers, x, y, z, spec, scaled, offsetX, offsetY, offsetZ, speed, step, random);
    }

    /** Shows a single particle of an animation; skipped randomly when the multiplier is below 1. */
    public void point(List<Player> viewers, double x, double y, double z, ParticleSpec spec, long step, Random random) {
        if (viewers.isEmpty() || skipPoint(random)) {
            return;
        }
        send(viewers, x, y, z, spec, 1, 0, 0, 0, 0, step, random);
    }

    /** Like {@link #point} but with a colour chosen by the caller (for example one random colour per line). */
    public void pointColored(List<Player> viewers, double x, double y, double z, ParticleSpec spec, int rgb, Random random) {
        if (viewers.isEmpty() || skipPoint(random)) {
            return;
        }
        int rgb2 = spec.color2() != null ? spec.color2().rgb(0, random) : rgb;
        output.send(viewers, x, y, z, spec, 1, 0, 0, 0, 0, rgb, rgb2, viewDistance > CLIENT_RANGE, random);
    }

    private boolean skipPoint(Random random) {
        double m = multiplier;
        return m < 1 && random.nextDouble() >= m;
    }

    /** The colour a particle gets for this step, from its colour setting or the default for its kind. */
    public int colorFor(ParticleSpec spec, long step, Random random) {
        return spec.color() != null ? spec.color().rgb(step, random) : defaultColor(spec, random);
    }

    private int scale(int count, Random random) {
        double m = multiplier;
        if (m == 1 || count <= 0) {
            return count;
        }
        double scaled = count * m;
        int whole = (int) scaled;
        return random.nextDouble() < scaled - whole ? whole + 1 : whole;
    }

    private void send(List<Player> viewers, double x, double y, double z, ParticleSpec spec, int count,
                      double offsetX, double offsetY, double offsetZ, double speed, long step, Random random) {
        int rgb = spec.color() != null ? spec.color().rgb(step, random) : defaultColor(spec, random);
        int rgb2 = spec.color2() != null ? spec.color2().rgb(step, random) : rgb;
        boolean force = viewDistance > CLIENT_RANGE;
        output.send(viewers, x, y, z, spec, count, offsetX, offsetY, offsetZ, speed, rgb, rgb2, force, random);
    }

    private static int defaultColor(ParticleSpec spec, Random random) {
        if (spec.kind() == ParticleEntry.Kind.DUST) {
            return RED;
        }
        if (spec.kind() == ParticleEntry.Kind.SPELL) {
            // Potion swirls used to come in random colours when no colour was given.
            return com.dank420girl.core.effect.ColorSpec.random().rgb(0, random);
        }
        return WHITE;
    }
}

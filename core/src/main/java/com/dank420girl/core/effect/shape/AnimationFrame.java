package com.dank420girl.core.effect.shape;

import com.dank420girl.core.effect.particle.ParticleService;
import com.dank420girl.core.effect.particle.ParticleSpec;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;

/**
 * One frame of an animation: the place it is drawn and who sees it. Shapes draw with coordinates
 * relative to the origin.
 */
public final class AnimationFrame {
    private final ParticleService particles;
    private final List<Player> viewers;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final int index;
    private final Random random;

    public AnimationFrame(ParticleService particles, List<Player> viewers, double x, double y, double z, float yaw,
                          int index, Random random) {
        this.particles = particles;
        this.viewers = viewers;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.index = index;
        this.random = random;
    }

    /** 0 for the first frame, then 1, 2, ... */
    public int index() {
        return index;
    }

    /** The direction the effect faces, in degrees (0 = south, like Minecraft's yaw). */
    public float yaw() {
        return yaw;
    }

    public Random random() {
        return random;
    }

    /** True if nobody can see this frame, so drawing it can be skipped. */
    public boolean isUnseen() {
        return viewers.isEmpty();
    }

    /** Draws one particle at the offset from the origin. */
    public void point(ParticleSpec spec, double dx, double dy, double dz) {
        particles.point(viewers, x + dx, y + dy, z + dz, spec, index, random);
    }

    /** Draws one particle with a colour chosen by the shape. */
    public void point(ParticleSpec spec, double dx, double dy, double dz, int rgb) {
        particles.pointColored(viewers, x + dx, y + dy, z + dz, spec, rgb, random);
    }

    /** Draws one particle at the offset given as a vector. */
    public void point(ParticleSpec spec, Vec3 offset) {
        point(spec, offset.x, offset.y, offset.z);
    }

    /** The colour a particle would get in this frame, so a shape can keep it for several points. */
    public int color(ParticleSpec spec) {
        return particles.colorFor(spec, index, random);
    }
}

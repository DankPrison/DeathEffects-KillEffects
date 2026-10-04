package com.dank420girl.core.effect.shape;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.LayerParser;
import com.dank420girl.core.effect.particle.ParticleSpec;

import java.util.Random;

/**
 * Arcs of fire breathed out the way the player faced (EffectLib's DragonEffect).
 */
final class DragonBreathShape extends ShapeDefinition {
    private final ParticleSpec particle;
    private final int arcs;
    private final int particles;
    private final int stepsPerFrame;
    private final double length;
    private final double pitch;

    DragonBreathShape(OptionReader o, LayerParser parser) {
        super(o, 2, 60);
        particle = parser.particle(o, "", "FLAME", null);
        arcs = o.integer("arcs", 20, 1, 100);
        particles = o.integer("particles", 30, 2, 200);
        stepsPerFrame = o.integer("steps-per-frame", 2, 1, 50);
        length = o.number("length", 4, 0.5, 40);
        pitch = o.number("pitch", 0.1, 0, 5);
    }

    @Override
    public boolean hasParticles() {
        return particle != null;
    }

    @Override
    public Animation start(Random random) {
        return new Animation() {
            private final float[] arcPitch = new float[arcs];
            private final double[] arcAngle = new double[arcs];
            private int step;

            @Override
            public void frame(AnimationFrame f) {
                if (particle == null) {
                    return;
                }
                Vec3 v = new Vec3(0, 0, 0);
                double yawRadians = Math.toRadians(-(f.yaw() + 90));
                for (int j = 0; j < stepsPerFrame; j++) {
                    if (step % particles == 0) {
                        for (int i = 0; i < arcs; i++) {
                            arcPitch[i] = (float) (f.random().nextFloat() * 2 * pitch - pitch);
                            arcAngle[i] = f.random().nextDouble() * 2 * Math.PI;
                        }
                    }
                    if (!f.isUnseen()) {
                        double x = (step % particles) * length / particles;
                        for (int i = 0; i < arcs; i++) {
                            v.set(x, arcPitch[i] * x * x, 0);
                            v.rotateX(arcAngle[i]);
                            v.rotateY(yawRadians);
                            f.point(particle, v);
                        }
                    }
                    step++;
                }
            }

            @Override
            public boolean isFinished() {
                return false;
            }
        };
    }
}

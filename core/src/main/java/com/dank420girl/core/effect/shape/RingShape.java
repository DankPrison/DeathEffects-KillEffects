package com.dank420girl.core.effect.shape;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.LayerParser;
import com.dank420girl.core.effect.particle.ParticleSpec;

import java.util.Random;

/**
 * A spinning ring that can be tilted (based on EffectLib's CircleEffect).
 */
final class RingShape extends ShapeDefinition {
    private final ParticleSpec particle;
    private final double radius;
    private final int points;
    private final double spin;
    private final double tiltX;
    private final double tiltZ;
    private final double wobble;

    RingShape(OptionReader o, LayerParser parser) {
        super(o, 1, 40);
        particle = parser.particle(o, "", "HAPPY_VILLAGER", null);
        radius = o.number("radius", 1.5, 0.05, 30);
        points = o.integer("points", 24, 3, 500);
        spin = Math.toRadians(o.number("spin", 10, -360, 360));
        tiltX = Math.toRadians(o.number("tilt-x", 0, -180, 180));
        tiltZ = Math.toRadians(o.number("tilt-z", 0, -180, 180));
        wobble = Math.toRadians(o.number("wobble", 0, 0, 180));
    }

    @Override
    public boolean hasParticles() {
        return particle != null;
    }

    @Override
    public Animation start(Random random) {
        return new Stateless() {
            @Override
            public void frame(AnimationFrame f) {
                if (particle == null || f.isUnseen()) {
                    return;
                }
                double base = spin * f.index();
                double extraTilt = wobble == 0 ? 0 : Math.sin(f.index() * 0.2) * wobble;
                Vec3 v = new Vec3(0, 0, 0);
                for (int k = 0; k < points; k++) {
                    double angle = base + 2 * Math.PI * k / points;
                    v.set(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                    if (tiltX != 0 || extraTilt != 0) {
                        v.rotateX(tiltX + extraTilt);
                    }
                    if (tiltZ != 0) {
                        v.rotateZ(tiltZ);
                    }
                    f.point(particle, v);
                }
            }
        };
    }
}

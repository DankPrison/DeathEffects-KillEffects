package com.dank420girl.core.effect.shape;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.LayerParser;
import com.dank420girl.core.effect.particle.ParticleSpec;

import java.util.Random;

/**
 * A double helix with base pairs between the strands (EffectLib's DnaEffect).
 */
final class DnaShape extends ShapeDefinition {
    private final ParticleSpec helix;
    private final ParticleSpec base;
    private final ParticleSpec base2;
    private final double radius;
    private final double height;
    private final double grow;
    private final double radials;
    private final int particlesPerFrame;
    private final int baseInterval;
    private final int baseParticles;
    private final Direction direction;

    DnaShape(OptionReader o, LayerParser parser) {
        super(o, 1, 100);
        helix = parser.particle(o, "", "FLAME", null);
        base = parser.optionalParticle(o, "base-", "FISHING");
        base2 = parser.optionalParticle(o, "base2-", "DUST");
        radius = o.number("radius", 1.2, 0.1, 20);
        height = o.number("height", 6, 0.5, 60);
        grow = o.number("grow", 0.1, 0.005, 5);
        radials = Math.toRadians(o.number("radials", 6, -360, 360));
        particlesPerFrame = o.integer("particles-per-frame", 3, 1, 50);
        baseInterval = o.integer("base-interval", 10, 1, 1000);
        baseParticles = o.integer("base-particles", 10, 1, 100);
        direction = o.choice("direction", Direction.class, Direction.UP);
    }

    @Override
    public boolean hasParticles() {
        return helix != null || base != null || base2 != null;
    }

    @Override
    public Animation start(Random random) {
        return new Stateless() {
            @Override
            public void frame(AnimationFrame f) {
                if (f.isUnseen()) {
                    return;
                }
                int stepsPerLoop = Math.max(1, (int) Math.round(height / grow));
                Vec3 v = new Vec3(0, 0, 0);
                for (int j = 0; j < particlesPerFrame; j++) {
                    int step = (f.index() * particlesPerFrame + j) % stepsPerLoop;
                    double y = step * grow;
                    for (int i = 0; i < 2; i++) {
                        double angle = step * radials + Math.PI * i;
                        if (helix == null) {
                            continue;
                        }
                        v.set(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                        direction.orient(v, f.yaw());
                        f.point(helix, v);
                    }
                    if (step % baseInterval == 0 && (base != null || base2 != null)) {
                        double angle = step * radials;
                        for (int i = -baseParticles; i <= baseParticles; i++) {
                            ParticleSpec spec = i < 0 ? base2 : base;
                            if (i == 0 || spec == null) {
                                continue;
                            }
                            double factor = radius * i / baseParticles;
                            v.set(Math.cos(angle) * factor, y, Math.sin(angle) * factor);
                            direction.orient(v, f.yaw());
                            f.point(spec, v);
                        }
                    }
                }
            }
        };
    }
}

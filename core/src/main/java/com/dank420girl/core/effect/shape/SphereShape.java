package com.dank420girl.core.effect.shape;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.LayerParser;
import com.dank420girl.core.effect.particle.ParticleSpec;

import java.util.Random;

/**
 * Particles scattered over the surface of a sphere, which can grow or shrink (EffectLib's SphereEffect).
 */
final class SphereShape extends ShapeDefinition {
    private final ParticleSpec particle;
    private final double radius;
    private final double grow;
    private final int particles;

    SphereShape(OptionReader o, LayerParser parser) {
        super(o, 1, 40);
        particle = parser.particle(o, "", "ENTITY_EFFECT", "RANDOM");
        radius = o.number("radius", 1.5, 0.05, 30);
        grow = o.number("grow", 0, -5, 5);
        particles = o.integer("particles", 30, 1, 500);
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
                double r = radius + grow * f.index();
                if (r <= 0) {
                    return;
                }
                for (int i = 0; i < particles; i++) {
                    f.point(particle, Vec3.randomUnit(f.random()).multiply(r));
                }
            }
        };
    }
}

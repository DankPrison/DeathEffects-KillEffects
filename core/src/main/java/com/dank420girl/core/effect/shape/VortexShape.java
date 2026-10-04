package com.dank420girl.core.effect.shape;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.LayerParser;
import com.dank420girl.core.effect.particle.ParticleSpec;

import java.util.Random;

/**
 * Several helices twisting around each other (EffectLib's VortexEffect).
 */
final class VortexShape extends ShapeDefinition {
    private final ParticleSpec particle;
    private final double radius;
    private final double grow;
    private final double radials;
    private final int circles;
    private final int helixes;
    private final double height;
    private final Direction direction;

    VortexShape(OptionReader o, LayerParser parser) {
        super(o, 1, 80);
        particle = parser.particle(o, "", "FLAME", null);
        radius = o.number("radius", 2, 0.1, 30);
        grow = o.number("grow", 0.05, 0.001, 5);
        radials = Math.toRadians(o.number("radials", 11.25, -360, 360));
        circles = o.integer("circles", 3, 1, 50);
        helixes = o.integer("helixes", 4, 1, 50);
        height = o.number("height", 5, 0.1, 60);
        direction = o.choice("direction", Direction.class, Direction.UP);
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
                Vec3 v = new Vec3(0, 0, 0);
                for (int c = 0; c < circles; c++) {
                    int step = f.index() * circles + c;
                    double along = (step * grow) % height;
                    for (int h = 0; h < helixes; h++) {
                        double angle = step * radials + 2 * Math.PI * h / helixes;
                        v.set(Math.cos(angle) * radius, along, Math.sin(angle) * radius);
                        direction.orient(v, f.yaw());
                        f.point(particle, v);
                    }
                }
            }
        };
    }
}

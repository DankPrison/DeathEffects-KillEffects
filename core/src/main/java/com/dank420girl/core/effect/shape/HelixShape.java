package com.dank420girl.core.effect.shape;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.LayerParser;
import com.dank420girl.core.effect.particle.ParticleSpec;

import java.util.Random;

/**
 * Curved arms spiralling out from the centre on the ground, like a galaxy (EffectLib's HelixEffect).
 */
final class HelixShape extends ShapeDefinition {
    private final ParticleSpec particle;
    private final int strands;
    private final int points;
    private final double radius;
    private final double curve;
    private final double rotation;

    HelixShape(OptionReader o, LayerParser parser) {
        super(o, 4, 40);
        particle = parser.particle(o, "", "FLAME", null);
        strands = o.integer("strands", 6, 1, 32);
        points = o.integer("points", 30, 2, 300);
        radius = o.number("radius", 3, 0.2, 30);
        curve = o.number("curve", 3, -50, 50);
        rotation = Math.toRadians(o.number("rotation", 15, -360, 360));
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
                double turn = rotation * f.index();
                for (int i = 1; i <= strands; i++) {
                    for (int j = 1; j <= points; j++) {
                        double ratio = (double) j / points;
                        double angle = curve * ratio * 2 * Math.PI / strands + 2 * Math.PI * i / strands + turn;
                        f.point(particle, Math.cos(angle) * ratio * radius, 0, Math.sin(angle) * ratio * radius);
                    }
                }
            }
        };
    }
}

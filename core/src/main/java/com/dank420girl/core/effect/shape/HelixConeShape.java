package com.dank420girl.core.effect.shape;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.LayerParser;
import com.dank420girl.core.effect.particle.ParticleSpec;

import java.util.Random;

/**
 * Strands that spiral upwards around a narrowing cone (the original "Blood Helix").
 */
final class HelixConeShape extends ShapeDefinition {
    private final ParticleSpec particle;
    private final double radius;
    private final double height;
    private final int strands;
    private final int points;
    private final double rotation;

    HelixConeShape(OptionReader o, LayerParser parser) {
        super(o, 3, 72);
        particle = parser.particle(o, "", "DUST", "#C00000");
        radius = o.number("radius", 1.25, 0.1, 20);
        height = o.number("height", 3.1, 0.1, 30);
        strands = o.integer("strands", 2, 1, 12);
        points = o.integer("points", 33, 2, 200);
        rotation = Math.toRadians(o.number("rotation", 22.5, -360, 360));
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
                double phi = (f.index() + 1) * rotation;
                for (int s = 0; s < strands; s++) {
                    double strandOffset = 2 * Math.PI * s / strands;
                    for (int j = 0; j < points; j++) {
                        double progress = (double) j / (points - 1);
                        double theta = progress * 2 * Math.PI;
                        double r = radius * (1 - progress);
                        double angle = theta + phi + strandOffset;
                        f.point(particle, Math.cos(angle) * r, progress * height, Math.sin(angle) * r);
                    }
                }
            }
        };
    }
}

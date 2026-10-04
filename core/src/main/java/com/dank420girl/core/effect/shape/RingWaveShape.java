package com.dank420girl.core.effect.shape;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.LayerParser;
import com.dank420girl.core.effect.particle.ParticleSpec;

import java.util.Random;

/**
 * Stacked rings that grow outwards one step per frame (the original "Circle").
 */
final class RingWaveShape extends ShapeDefinition {
    private final ParticleSpec particle;
    private final double startRadius;
    private final double growth;
    private final double maxRadius;
    private final double density;
    private final int rings;
    private final double ringSpacing;

    RingWaveShape(OptionReader o, LayerParser parser) {
        super(o, 2, 12);
        particle = parser.particle(o, "", "FLAME", null);
        startRadius = o.number("start-radius", 1, 0, 50);
        growth = o.number("growth", 1, 0.01, 10);
        maxRadius = o.number("max-radius", 4, 0.1, 64);
        density = o.number("density", 10, 0.5, 100);
        rings = o.integer("rings", 3, 1, 20);
        ringSpacing = o.number("ring-spacing", 1, 0, 10);
    }

    @Override
    public boolean hasParticles() {
        return particle != null;
    }

    @Override
    public Animation start(Random random) {
        return new Animation() {
            private boolean finished;

            @Override
            public void frame(AnimationFrame f) {
                double radius = startRadius + growth * f.index();
                if (radius > maxRadius + 1.0E-6) {
                    finished = true;
                    return;
                }
                if (particle == null || f.isUnseen()) {
                    return;
                }
                int points = Math.max(8, (int) Math.round(density * radius));
                for (int ring = 0; ring < rings; ring++) {
                    double y = ring * ringSpacing;
                    for (int k = 0; k < points; k++) {
                        double angle = 2 * Math.PI * k / points;
                        f.point(particle, Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                    }
                }
            }

            @Override
            public boolean isFinished() {
                return finished;
            }
        };
    }
}

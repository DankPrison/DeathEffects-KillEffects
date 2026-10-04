package com.dank420girl.core.effect.shape;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.LayerParser;
import com.dank420girl.core.effect.particle.ParticleSpec;

import java.util.Random;

/**
 * A ring that races outwards while bobbing up and down in a fading wave (the original "Nuke").
 */
final class ShockwaveShape extends ShapeDefinition {
    private final ParticleSpec particle;
    private final ParticleSpec secondParticle;
    private final double startRadius;
    private final double maxRadius;
    private final double growth;
    private final int points;
    private final double height;
    private final double amplitude;
    private final double damping;

    ShockwaveShape(OptionReader o, LayerParser parser) {
        super(o, 1, 40);
        particle = parser.particle(o, "", "FIREWORK", null);
        secondParticle = parser.optionalParticle(o, "second-", "FLAME");
        startRadius = o.number("start-radius", 0.8, 0, 50);
        maxRadius = o.number("max-radius", 10, 0.5, 64);
        growth = o.number("growth", 0.314, 0.01, 10);
        points = o.integer("points", 64, 4, 512);
        height = o.number("height", 1.5, -10, 50);
        amplitude = o.number("amplitude", 2.0, 0, 20);
        damping = o.number("damping", 0.1, 0, 5);
    }

    @Override
    public boolean hasParticles() {
        return particle != null || secondParticle != null;
    }

    @Override
    public Animation start(Random random) {
        return new Animation() {
            private boolean finished;

            @Override
            public void frame(AnimationFrame f) {
                double t = startRadius + growth * (f.index() + 1);
                if (t > maxRadius) {
                    finished = true;
                    return;
                }
                if (f.isUnseen()) {
                    return;
                }
                double y = amplitude * Math.exp(-damping * t) * Math.sin(t) + height;
                double step = 2 * Math.PI / points;
                for (int k = 0; k < points; k++) {
                    double angle = k * step;
                    if (particle != null) {
                        f.point(particle, t * Math.cos(angle), y, t * Math.sin(angle));
                    }
                    if (secondParticle != null) {
                        double between = angle + step / 2;
                        f.point(secondParticle, t * Math.cos(between), y, t * Math.sin(between));
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

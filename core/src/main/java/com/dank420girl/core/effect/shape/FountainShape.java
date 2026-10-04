package com.dank420girl.core.effect.shape;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.LayerParser;
import com.dank420girl.core.effect.particle.ParticleSpec;

import java.util.Random;

/**
 * Arcs that splash outwards around a central spout (EffectLib's FountainEffect).
 */
final class FountainShape extends ShapeDefinition {
    private final ParticleSpec particle;
    private final int strands;
    private final int strandParticles;
    private final int spoutParticles;
    private final double radius;
    private final double spoutRadius;
    private final double height;
    private final double spoutHeight;
    private final double rotation;

    FountainShape(OptionReader o, LayerParser parser) {
        super(o, 2, 60);
        particle = parser.particle(o, "", "SPLASH", null);
        strands = o.integer("strands", 10, 1, 64);
        strandParticles = o.integer("strand-particles", 30, 2, 300);
        spoutParticles = o.integer("spout-particles", 40, 0, 500);
        radius = o.number("radius", 2.5, 0.2, 30);
        spoutRadius = o.number("spout-radius", 0.25, 0, 10);
        height = o.number("height", 2, 0.1, 30);
        spoutHeight = o.number("spout-height", 3.5, 0, 40);
        rotation = Math.toRadians(o.number("rotation", 45, -360, 360));
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
                for (int i = 1; i <= strands; i++) {
                    double angle = 2 * i * Math.PI / strands + rotation;
                    for (int j = 1; j <= strandParticles; j++) {
                        double ratio = (double) j / strandParticles;
                        double y = Math.sin(Math.PI * j / strandParticles) * height;
                        f.point(particle, Math.cos(angle) * radius * ratio, y, Math.sin(angle) * radius * ratio);
                    }
                }
                Random r = f.random();
                for (int i = 0; i < spoutParticles; i++) {
                    double angle = r.nextDouble() * 2 * Math.PI;
                    double distance = r.nextDouble() * spoutRadius;
                    f.point(particle, Math.cos(angle) * distance, r.nextDouble() * spoutHeight, Math.sin(angle) * distance);
                }
            }
        };
    }
}

package com.dank420girl.core.effect.shape;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.LayerParser;
import com.dank420girl.core.effect.particle.ParticleSpec;

import java.util.Random;

/**
 * A funnel that widens towards the top, crowned by a cloud (based on EffectLib's TornadoEffect).
 */
final class TornadoShape extends ShapeDefinition {
    private final ParticleSpec particle;
    private final ParticleSpec cloud;
    private final double height;
    private final double maxRadius;
    private final double spacing;
    private final double density;
    private final double cloudSize;
    private final int cloudParticles;
    private final double spin;

    TornadoShape(OptionReader o, LayerParser parser) {
        super(o, 3, 80);
        particle = parser.particle(o, "", "CLOUD", null);
        cloud = parser.optionalParticle(o, "cloud-", "LARGE_SMOKE");
        height = o.number("height", 5, 0.5, 40);
        maxRadius = o.number("max-radius", 2.5, 0.1, 20);
        spacing = o.number("spacing", 0.4, 0.05, 5);
        density = o.number("density", 8, 1, 100);
        cloudSize = o.number("cloud-size", 2.5, 0, 20);
        cloudParticles = o.integer("cloud-particles", 40, 0, 500);
        spin = Math.toRadians(o.number("spin", 20, -360, 360));
    }

    @Override
    public boolean hasParticles() {
        return particle != null || cloud != null;
    }

    @Override
    public Animation start(Random random) {
        return new Stateless() {
            @Override
            public void frame(AnimationFrame f) {
                if (f.isUnseen()) {
                    return;
                }
                double turn = spin * f.index();
                if (particle != null) {
                    for (double y = 0; y < height; y += spacing) {
                        double r = 0.15 + (maxRadius - 0.15) * (y / height);
                        int points = Math.max(4, (int) Math.round(r * density));
                        for (int k = 0; k < points; k++) {
                            double angle = turn + 2 * Math.PI * k / points + y;
                            f.point(particle, Math.cos(angle) * r, y, Math.sin(angle) * r);
                        }
                    }
                }
                if (cloud != null && cloudSize > 0) {
                    Random r = f.random();
                    for (int i = 0; i < cloudParticles; i++) {
                        double angle = r.nextDouble() * 2 * Math.PI;
                        double distance = Math.sqrt(r.nextDouble()) * (maxRadius + cloudSize);
                        f.point(cloud, Math.cos(angle) * distance, height + r.nextDouble() * 0.5, Math.sin(angle) * distance);
                    }
                }
            }
        };
    }
}

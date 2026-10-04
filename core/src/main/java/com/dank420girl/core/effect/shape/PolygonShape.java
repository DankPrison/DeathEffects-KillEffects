package com.dank420girl.core.effect.shape;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.LayerParser;
import com.dank420girl.core.effect.particle.ParticleSpec;

import java.util.Random;

/**
 * A flat polygon on the ground; with all corners connected a pentagon becomes a pentagram
 * (the original "Pentagon").
 */
final class PolygonShape extends ShapeDefinition {
    private final ParticleSpec particle;
    private final int sides;
    private final double radius;
    private final boolean connectAll;
    private final double density;
    private final double spin;

    PolygonShape(OptionReader o, LayerParser parser) {
        super(o, 2, 100);
        particle = parser.particle(o, "", "DUST", "#FF0000");
        sides = o.integer("sides", 5, 3, 32);
        radius = o.number("radius", 3, 0.2, 30);
        connectAll = o.bool("connect-all", true);
        density = o.number("density", 4, 0.5, 50);
        spin = Math.toRadians(o.number("spin", 0, -360, 360));
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
                double rotation = spin * f.index() + Math.toRadians(-f.yaw()) - Math.PI / 2;
                double[] xs = new double[sides];
                double[] zs = new double[sides];
                for (int k = 0; k < sides; k++) {
                    double angle = rotation + 2 * Math.PI * k / sides;
                    xs[k] = Math.cos(angle) * radius;
                    zs[k] = Math.sin(angle) * radius;
                }
                for (int a = 0; a < sides; a++) {
                    for (int b = a + 1; b < sides; b++) {
                        boolean edge = b == a + 1 || (a == 0 && b == sides - 1);
                        if (!edge && !connectAll) {
                            continue;
                        }
                        line(f, xs[a], zs[a], xs[b], zs[b]);
                    }
                }
            }
        };
    }

    private void line(AnimationFrame f, double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        int points = Math.max(2, (int) Math.round(Math.sqrt(dx * dx + dz * dz) * density));
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            f.point(particle, x1 + dx * t, 0, z1 + dz * t);
        }
    }
}

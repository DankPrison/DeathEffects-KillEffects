package com.dank420girl.core.effect.shape;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.LayerParser;
import com.dank420girl.core.effect.particle.ParticleSpec;

import java.util.Random;

/**
 * A glowing ball that shoots beams of light in random directions (EffectLib's DiscoBallEffect).
 */
final class DiscoBallShape extends ShapeDefinition {
    /** Where the beams point. */
    enum BeamDirection {
        DOWN, UP, BOTH
    }

    private final ParticleSpec sphere;
    private final ParticleSpec line;
    private final double sphereRadius;
    private final int sphereParticles;
    private final int maxLines;
    private final int lineLength;
    private final int lineParticles;
    private final BeamDirection direction;

    DiscoBallShape(OptionReader o, LayerParser parser) {
        super(o, 7, 105);
        sphere = parser.particle(o, "", "FLAME", null);
        line = parser.optionalParticle(o, "line-", "DUST");
        sphereRadius = o.number("radius", 0.6, 0.05, 10);
        sphereParticles = o.integer("sphere-particles", 50, 0, 500);
        maxLines = o.integer("max-lines", 7, 3, 50);
        lineLength = o.integer("line-length", 15, 1, 50);
        lineParticles = o.integer("line-particles", 40, 2, 300);
        direction = o.choice("direction", BeamDirection.class, BeamDirection.DOWN);
    }

    @Override
    public boolean hasParticles() {
        return sphere != null || line != null;
    }

    @Override
    public Animation start(Random random) {
        return new Stateless() {
            @Override
            public void frame(AnimationFrame f) {
                if (f.isUnseen()) {
                    return;
                }
                Random r = f.random();
                if (line != null) {
                    int lines = (r.nextInt(maxLines - 2) + 2) * 2;
                    for (int m = 0; m < lines; m++) {
                        double tx = r.nextInt(lineLength * 2) - lineLength;
                        double tz = r.nextInt(lineLength * 2) - lineLength;
                        double ty;
                        if (direction == BeamDirection.DOWN) {
                            ty = -(r.nextInt(lineLength) + lineLength);
                        } else if (direction == BeamDirection.UP) {
                            ty = r.nextInt(lineLength) + lineLength;
                        } else {
                            ty = r.nextInt(lineLength * 2) - lineLength;
                        }
                        int rgb = f.color(line);
                        for (int i = 1; i <= lineParticles; i++) {
                            double t = (double) i / lineParticles;
                            f.point(line, tx * t, ty * t, tz * t, rgb);
                        }
                    }
                }
                if (sphere != null) {
                    for (int i = 0; i < sphereParticles; i++) {
                        f.point(sphere, Vec3.randomUnit(r).multiply(sphereRadius));
                    }
                }
            }
        };
    }
}

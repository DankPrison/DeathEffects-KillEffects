package com.dank420girl.core.effect.shape;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.LayerParser;

/**
 * The animated shapes an {@code animation} layer can draw.
 */
public enum Shape {
    HELIX_CONE(new ShapeDefinition.Factory() {
        @Override
        public ShapeDefinition create(OptionReader o, LayerParser p) {
            return new HelixConeShape(o, p);
        }
    }),
    SHOCKWAVE(new ShapeDefinition.Factory() {
        @Override
        public ShapeDefinition create(OptionReader o, LayerParser p) {
            return new ShockwaveShape(o, p);
        }
    }),
    RING_WAVE(new ShapeDefinition.Factory() {
        @Override
        public ShapeDefinition create(OptionReader o, LayerParser p) {
            return new RingWaveShape(o, p);
        }
    }),
    VORTEX(new ShapeDefinition.Factory() {
        @Override
        public ShapeDefinition create(OptionReader o, LayerParser p) {
            return new VortexShape(o, p);
        }
    }),
    DNA(new ShapeDefinition.Factory() {
        @Override
        public ShapeDefinition create(OptionReader o, LayerParser p) {
            return new DnaShape(o, p);
        }
    }),
    DISCO_BALL(new ShapeDefinition.Factory() {
        @Override
        public ShapeDefinition create(OptionReader o, LayerParser p) {
            return new DiscoBallShape(o, p);
        }
    }),
    DRAGON_BREATH(new ShapeDefinition.Factory() {
        @Override
        public ShapeDefinition create(OptionReader o, LayerParser p) {
            return new DragonBreathShape(o, p);
        }
    }),
    POLYGON(new ShapeDefinition.Factory() {
        @Override
        public ShapeDefinition create(OptionReader o, LayerParser p) {
            return new PolygonShape(o, p);
        }
    }),
    SPHERE(new ShapeDefinition.Factory() {
        @Override
        public ShapeDefinition create(OptionReader o, LayerParser p) {
            return new SphereShape(o, p);
        }
    }),
    RING(new ShapeDefinition.Factory() {
        @Override
        public ShapeDefinition create(OptionReader o, LayerParser p) {
            return new RingShape(o, p);
        }
    }),
    HELIX(new ShapeDefinition.Factory() {
        @Override
        public ShapeDefinition create(OptionReader o, LayerParser p) {
            return new HelixShape(o, p);
        }
    }),
    TORNADO(new ShapeDefinition.Factory() {
        @Override
        public ShapeDefinition create(OptionReader o, LayerParser p) {
            return new TornadoShape(o, p);
        }
    }),
    FOUNTAIN(new ShapeDefinition.Factory() {
        @Override
        public ShapeDefinition create(OptionReader o, LayerParser p) {
            return new FountainShape(o, p);
        }
    });

    private final ShapeDefinition.Factory factory;

    Shape(ShapeDefinition.Factory factory) {
        this.factory = factory;
    }

    public ShapeDefinition create(OptionReader options, LayerParser parser) {
        return factory.create(options, parser);
    }
}

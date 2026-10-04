package com.dank420girl.core.effect.layer;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.EffectContext;
import com.dank420girl.core.effect.EffectEngine;
import com.dank420girl.core.effect.shape.Shape;
import com.dank420girl.core.effect.shape.ShapeDefinition;

/**
 * An animated shape drawn with particles over several ticks.
 */
public final class AnimationLayer extends Layer {
    private final Shape shape;
    private final ShapeDefinition definition;

    public AnimationLayer(OptionReader options, Shape shape, ShapeDefinition definition) {
        super("animation", options, 0.0);
        this.shape = shape;
        this.definition = definition;
    }

    public Shape shape() {
        return shape;
    }

    public ShapeDefinition definition() {
        return definition;
    }

    @Override
    protected int activeTicks() {
        return definition.duration();
    }

    @Override
    public void play(EffectEngine engine, EffectContext context, int iteration) {
        engine.startAnimation(this, context);
    }
}

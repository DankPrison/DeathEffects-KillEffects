package com.dank420girl.core.effect.layer;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.EffectContext;
import com.dank420girl.core.effect.EffectEngine;

/**
 * One part of an effect: a particle burst, fireworks, lightning, a sound or an animation.
 * Every layer can start later ({@code delay}) and play several times ({@code repeat} every {@code interval} ticks).
 */
public abstract class Layer {
    private final String type;
    private final int delay;
    private final int repeat;
    private final int interval;
    private final double y;

    protected Layer(String type, OptionReader options, double defaultY) {
        this.type = type;
        this.delay = options.integer("delay", 0, 0, 20 * 60);
        this.repeat = options.integer("repeat", 1, 1, 200);
        this.interval = options.integer("interval", 5, 1, 20 * 60);
        this.y = options.number("y", defaultY, -64, 320);
    }

    public String type() {
        return type;
    }

    /** Ticks before the first play. */
    public int delay() {
        return delay;
    }

    /** How many times the layer plays. */
    public int repeat() {
        return repeat;
    }

    /** Ticks between two plays. */
    public int interval() {
        return interval;
    }

    /** Height above the origin. */
    public double y() {
        return y;
    }

    /** Ticks from the start of the effect until this layer has completely finished. */
    public int length() {
        return delay + (repeat - 1) * interval + activeTicks();
    }

    /** How long one play of the layer lasts. */
    protected int activeTicks() {
        return 1;
    }

    /**
     * Plays the layer once. Called on the thread that owns the effect's location.
     *
     * @param iteration 0 for the first play, 1 for the first repeat, ...
     */
    public abstract void play(EffectEngine engine, EffectContext context, int iteration);
}

package com.dank420girl.core.effect.shape;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.LayerParser;

import java.util.Random;

/**
 * The settings of an animated shape, read once from effects.yml. Each playback gets its own
 * {@link Animation} from {@link #start(Random)}.
 */
public abstract class ShapeDefinition {
    private final int period;
    private final int duration;

    protected ShapeDefinition(OptionReader options, int defaultPeriod, int defaultDuration) {
        this.period = options.integer("period", defaultPeriod, 1, 200);
        this.duration = options.integer("duration", defaultDuration, 1, 20 * 60 * 5);
    }

    /** Ticks between two frames. */
    public int period() {
        return period;
    }

    /** How long the animation runs, in ticks (some shapes stop earlier on their own). */
    public int duration() {
        return duration;
    }

    /** Creates the state for one playback. */
    public abstract Animation start(Random random);

    /** False if none of the shape's particles can be shown on this server, so the layer is left out. */
    public abstract boolean hasParticles();

    /** Draws one frame; the state of a playback. */
    public interface Animation {
        void frame(AnimationFrame frame);

        /** True when the shape has finished before its duration, for example a fully expanded shockwave. */
        boolean isFinished();
    }

    /** Animations whose frames only depend on the frame number. */
    protected abstract static class Stateless implements Animation {
        @Override
        public boolean isFinished() {
            return false;
        }
    }

    /** Creates the definition of a shape from its options. */
    public interface Factory {
        ShapeDefinition create(OptionReader options, LayerParser parser);
    }
}

package com.dank420girl.core.effect.layer;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.EffectContext;
import com.dank420girl.core.effect.EffectEngine;
import com.dank420girl.core.effect.SoundSpec;
import org.bukkit.Location;

/**
 * Plays a sound for the players near the effect.
 */
public final class SoundLayer extends Layer {
    private final SoundSpec sound;

    public SoundLayer(OptionReader options, SoundSpec sound) {
        super("sound", options, 0.0);
        this.sound = sound;
    }

    @Override
    public void play(EffectEngine engine, EffectContext context, int iteration) {
        Location location = context.at(y());
        engine.sounds().play(engine.listeners(location, context), location, sound);
    }
}

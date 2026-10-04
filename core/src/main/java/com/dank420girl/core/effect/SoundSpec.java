package com.dank420girl.core.effect;

import com.dank420girl.core.names.Names.SoundName;

/**
 * A sound with its volume and pitch.
 */
public final class SoundSpec {
    private final SoundName sound;
    private final float volume;
    private final float pitch;

    public SoundSpec(SoundName sound, float volume, float pitch) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }

    public SoundName sound() {
        return sound;
    }

    public float volume() {
        return volume;
    }

    public float pitch() {
        return pitch;
    }

    @Override
    public String toString() {
        return sound + " (volume " + volume + ", pitch " + pitch + ")";
    }
}

package com.dank420girl.core;

import org.bukkit.Location;

/** The effect and the place a play event ended up with, after other plugins had their say. */
public final class EffectPlay {
    private final String effectId;
    private final Location location;

    public EffectPlay(String effectId, Location location) {
        this.effectId = effectId;
        this.location = location;
    }

    public String effectId() {
        return effectId;
    }

    public Location location() {
        return location;
    }
}

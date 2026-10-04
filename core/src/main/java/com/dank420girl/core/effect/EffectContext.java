package com.dank420girl.core.effect;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Random;

/**
 * One playback of an effect: where it plays, whose effect it is and who may see it.
 */
public final class EffectContext {
    private final Location origin;
    private final Player owner;
    private final Player audience;
    private final Random random;

    /**
     * @param origin   where the effect plays; its yaw orients directional animations
     * @param owner    the player whose effect it is, or {@code null}
     * @param audience if not {@code null}, only this player sees particles and hears sounds (private previews)
     */
    public EffectContext(Location origin, Player owner, Player audience, Random random) {
        this.origin = origin.clone();
        this.owner = owner;
        this.audience = audience;
        this.random = random;
    }

    /** A copy of the origin; safe to modify. */
    public Location origin() {
        return origin.clone();
    }

    /** The origin moved up by the given height. */
    public Location at(double y) {
        return origin.clone().add(0, y, 0);
    }

    public Player owner() {
        return owner;
    }

    public Player audience() {
        return audience;
    }

    public Random random() {
        return random;
    }
}

package com.dank420girl.core.effect.particle;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;

/**
 * Sends particles to players. There is one implementation per generation of the game:
 * packets for 1.7 and 1.8, Bukkit's particle API from 1.9 on.
 */
public interface ParticleOutput {

    /** Name for the version command. */
    String name();

    /** True if the server knows a particle by this name. */
    boolean exists(String particleName);

    /**
     * Prepares the particle for this output.
     *
     * @return {@code false} if this server cannot show it (the reason is logged by the caller)
     */
    boolean prepare(ParticleSpec spec);

    /**
     * Shows particles to the given players.
     *
     * @param count   number of particles; they are spread randomly within the offsets
     * @param speed   particle speed ("extra" in Bukkit's API)
     * @param rgb     colour for colourable particles
     * @param rgb2    second colour for colour transitions
     * @param force   show beyond the 32 block range clients normally use
     */
    void send(List<Player> viewers, double x, double y, double z, ParticleSpec spec, int count,
              double offsetX, double offsetY, double offsetZ, double speed, int rgb, int rgb2, boolean force,
              Random random);
}

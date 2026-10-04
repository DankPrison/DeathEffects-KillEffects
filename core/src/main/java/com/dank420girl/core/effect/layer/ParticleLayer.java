package com.dank420girl.core.effect.layer;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.EffectContext;
import com.dank420girl.core.effect.EffectEngine;
import com.dank420girl.core.effect.particle.ParticleService;
import com.dank420girl.core.effect.particle.ParticleSpec;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * A burst of particles. With a list of items or blocks, each of them gets its own burst.
 */
public final class ParticleLayer extends Layer {
    private final List<ParticleSpec> particles;
    private final int count;
    private final double[] offset;
    private final double speed;

    public ParticleLayer(OptionReader options, List<ParticleSpec> particles) {
        super("particle", options, 1.0);
        this.particles = particles;
        this.count = options.integer("count", 10, 0, 5000);
        this.offset = options.vector("offset", 0.3);
        this.speed = options.number("speed", 0.05, 0, 100);
    }

    public List<ParticleSpec> particles() {
        return particles;
    }

    @Override
    public void play(EffectEngine engine, EffectContext context, int iteration) {
        ParticleService service = engine.particles();
        Location location = context.at(y());
        List<Player> viewers = service.viewers(location, context.audience());
        if (viewers.isEmpty()) {
            return;
        }
        for (ParticleSpec spec : particles) {
            service.burst(viewers, location.getX(), location.getY(), location.getZ(), spec, count,
                    offset[0], offset[1], offset[2], speed, iteration, context.random());
        }
    }
}

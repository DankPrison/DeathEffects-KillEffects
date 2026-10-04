package com.dank420girl.core.effect.layer;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.EffectContext;
import com.dank420girl.core.effect.EffectEngine;
import org.bukkit.Location;

/**
 * Strikes harmless lightning: it looks and sounds real but never causes fire or damage.
 */
public final class LightningLayer extends Layer {
    private final int count;
    private final double spread;

    public LightningLayer(OptionReader options) {
        super("lightning", options, 0.0);
        this.count = options.integer("count", 1, 1, 10);
        this.spread = options.number("spread", 0, 0, 20);
    }

    @Override
    public void play(EffectEngine engine, EffectContext context, int iteration) {
        for (int i = 0; i < count; i++) {
            Location location = context.at(y());
            if (spread > 0) {
                location.add((context.random().nextDouble() * 2 - 1) * spread, 0,
                        (context.random().nextDouble() * 2 - 1) * spread);
            }
            location.getWorld().strikeLightningEffect(location);
        }
    }
}

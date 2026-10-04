package com.dank420girl.deatheffects;

import com.dank420girl.core.EffectPlay;
import com.dank420girl.core.EffectsPlugin;
import com.dank420girl.core.Variant;
import com.dank420girl.deatheffects.api.DeathEffectPlayEvent;
import com.dank420girl.deatheffects.api.DeathEffectSelectEvent;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * DeathEffects: players choose an effect that plays where they die.
 * <p>
 * Everything else is in the core, which is built into this jar as {@code com.dank420girl.deatheffects.core}.
 * The API: the public methods of {@link EffectsPlugin} (getSelectedEffectId, setSelectedEffect, playEffect,
 * getEffectIds) and the events in {@link com.dank420girl.deatheffects.api}.
 */
public final class DeathEffectsPlugin extends EffectsPlugin {
    public DeathEffectsPlugin() {
        super(Variant.DEATH_EFFECTS);
    }

    @Override
    protected boolean fireSelectEvent(Player player, String previousEffect, String newEffect) {
        DeathEffectSelectEvent event = new DeathEffectSelectEvent(player, previousEffect, newEffect);
        getServer().getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    @Override
    protected EffectPlay firePlayEvent(LivingEntity victim, Player killer, String effectId, Location location) {
        DeathEffectPlayEvent event = new DeathEffectPlayEvent((Player) victim, killer, effectId, location);
        getServer().getPluginManager().callEvent(event);
        return event.isCancelled() ? null : new EffectPlay(event.getEffectId(), event.getLocation());
    }
}

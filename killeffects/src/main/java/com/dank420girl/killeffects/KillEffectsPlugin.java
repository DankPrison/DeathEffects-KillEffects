package com.dank420girl.killeffects;

import com.dank420girl.core.EffectPlay;
import com.dank420girl.core.EffectsPlugin;
import com.dank420girl.core.Variant;
import com.dank420girl.killeffects.api.KillEffectPlayEvent;
import com.dank420girl.killeffects.api.KillEffectSelectEvent;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * KillEffects: players choose an effect that plays where their victims die.
 * <p>
 * Everything else is in the core, which is built into this jar as {@code com.dank420girl.killeffects.core}.
 * The API: the public methods of {@link EffectsPlugin} (getSelectedEffectId, setSelectedEffect, playEffect,
 * getEffectIds) and the events in {@link com.dank420girl.killeffects.api}.
 */
public final class KillEffectsPlugin extends EffectsPlugin {
    public KillEffectsPlugin() {
        super(Variant.KILL_EFFECTS);
    }

    @Override
    protected boolean fireSelectEvent(Player player, String previousEffect, String newEffect) {
        KillEffectSelectEvent event = new KillEffectSelectEvent(player, previousEffect, newEffect);
        getServer().getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    @Override
    protected EffectPlay firePlayEvent(LivingEntity victim, Player killer, String effectId, Location location) {
        KillEffectPlayEvent event = new KillEffectPlayEvent(killer, victim, effectId, location);
        getServer().getPluginManager().callEvent(event);
        return event.isCancelled() ? null : new EffectPlay(event.getEffectId(), event.getLocation());
    }
}

package com.dank420girl.core;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.config.Problems;
import com.dank420girl.core.effect.EffectDefinition;
import com.dank420girl.core.platform.Debug;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * DeathEffects: the effect a player selected plays where they die.
 */
public final class DeathTrigger extends Trigger {
    private volatile boolean killedByPlayer = true;
    private volatile boolean otherDeaths = true;
    private volatile boolean skipWhenKillEffectPlays;

    DeathTrigger(EffectsPlugin plugin) {
        super(plugin);
    }

    @Override
    Runnable readSettings(ConfigurationSection config, Problems problems) {
        OptionReader triggers = OptionReader.of(OptionReader.section(config, "triggers", "triggers", problems), "triggers", problems);
        final boolean newKilledByPlayer = triggers.bool("killed-by-player", true);
        final boolean newOtherDeaths = triggers.bool("other-deaths", true);
        triggers.reportUnused();
        OptionReader compatibility = OptionReader.of(OptionReader.section(config, "compatibility", "compatibility", problems),
                "compatibility", problems);
        final boolean newSkip = compatibility.bool("skip-when-kill-effect-plays", false);
        compatibility.reportUnused();
        return new Runnable() {
            @Override
            public void run() {
                killedByPlayer = newKilledByPlayer;
                otherDeaths = newOtherDeaths;
                skipWhenKillEffectPlays = newSkip;
            }
        };
    }

    /** Runs last, so plugins that cancel deaths (Paper allows that) are respected. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.isStarted()) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        String what = victim.getName() + " died";
        EffectDefinition effect = effectFor(victim, killer);
        if (effect == null) {
            if (Debug.isEnabled()) {
                Debug.log(plugin.getLogger(), what + ", no death effect: " + whyNot(victim, killer));
            }
            return;
        }
        if (skipWhenKillEffectPlays && killer != null && !killer.equals(victim) && otherWouldPlay(victim, killer)) {
            Debug.log(plugin.getLogger(), what + ", no death effect: the kill effect of " + killer.getName()
                    + " plays instead (compatibility.skip-when-kill-effect-plays)");
            return;
        }
        play(effect, victim, killer, victim, what);
    }

    private EffectDefinition effectFor(Player victim, Player killer) {
        boolean byPlayer = killer != null && !killer.equals(victim);
        if (byPlayer ? !killedByPlayer : !otherDeaths) {
            return null;
        }
        if (!plugin.settings().isWorldEnabled(victim.getWorld())) {
            return null;
        }
        return plugin.activeEffect(victim);
    }

    private String whyNot(Player victim, Player killer) {
        boolean byPlayer = killer != null && !killer.equals(victim);
        if (byPlayer ? !killedByPlayer : !otherDeaths) {
            return byPlayer ? "triggers.killed-by-player is false" : "triggers.other-deaths is false";
        }
        return whyNoEffect(victim, victim);
    }

    @Override
    boolean wouldPlay(LivingEntity victim, Player killer) {
        return victim instanceof Player && effectFor((Player) victim, killer) != null;
    }

    @Override
    boolean skipsWhenOtherEffectPlays() {
        return skipWhenKillEffectPlays;
    }
}

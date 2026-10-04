package com.dank420girl.core;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.config.Problems;
import com.dank420girl.core.effect.EffectDefinition;
import com.dank420girl.core.platform.Debug;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * KillEffects: the effect a player selected plays where their victim dies. Player deaths are
 * EntityDeathEvents too, so one handler covers players and mobs.
 */
public final class KillTrigger extends Trigger {
    private volatile boolean players = true;
    private volatile boolean mobs;
    private volatile boolean self;
    private volatile Set<String> ignoredMobs = Collections.emptySet();
    private volatile boolean skipWhenDeathEffectPlays;

    KillTrigger(EffectsPlugin plugin) {
        super(plugin);
    }

    @Override
    Runnable readSettings(ConfigurationSection config, Problems problems) {
        OptionReader triggers = OptionReader.of(OptionReader.section(config, "triggers", "triggers", problems), "triggers", problems);
        final boolean newPlayers = triggers.bool("players", true);
        final boolean newMobs = triggers.bool("mobs", false);
        final boolean newSelf = triggers.bool("self", false);
        final Set<String> ignored = new HashSet<String>();
        for (String type : triggers.strings("ignored-mobs")) {
            ignored.add(type.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        }
        triggers.reportUnused();
        OptionReader compatibility = OptionReader.of(OptionReader.section(config, "compatibility", "compatibility", problems),
                "compatibility", problems);
        final boolean newSkip = compatibility.bool("skip-when-death-effect-plays", false);
        compatibility.reportUnused();
        return new Runnable() {
            @Override
            public void run() {
                players = newPlayers;
                mobs = newMobs;
                self = newSelf;
                ignoredMobs = ignored;
                skipWhenDeathEffectPlays = newSkip;
            }
        };
    }

    /** Runs last, so plugins that cancel deaths (Paper allows that) are respected. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!plugin.isStarted()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }
        String what = killer.getName() + " killed " + describe(victim);
        EffectDefinition effect = effectFor(killer, victim);
        if (effect == null) {
            if (Debug.isEnabled()) {
                Debug.log(plugin.getLogger(), what + ", no kill effect: " + whyNot(killer, victim));
            }
            return;
        }
        // If both plugins want to step aside for each other, the kill effect plays.
        if (skipWhenDeathEffectPlays && victim instanceof Player && !victim.equals(killer) && !otherSkips()
                && otherWouldPlay(victim, killer)) {
            Debug.log(plugin.getLogger(), what + ", no kill effect: the death effect of " + describe(victim)
                    + " plays instead (compatibility.skip-when-death-effect-plays)");
            return;
        }
        play(effect, victim, killer, killer, what);
    }

    private EffectDefinition effectFor(Player killer, Entity victim) {
        if (victim instanceof Player) {
            if (victim.equals(killer) ? !self : !players) {
                return null;
            }
        } else if (!mobs || ignoredMobs.contains(victim.getType().name())) {
            return null;
        }
        if (!plugin.settings().isWorldEnabled(victim.getWorld())) {
            return null;
        }
        return plugin.activeEffect(killer);
    }

    private String whyNot(Player killer, LivingEntity victim) {
        if (victim instanceof Player) {
            if (victim.equals(killer) ? !self : !players) {
                return victim.equals(killer) ? "triggers.self is false" : "triggers.players is false";
            }
        } else if (!mobs) {
            return "triggers.mobs is false";
        } else if (ignoredMobs.contains(victim.getType().name())) {
            return victim.getType().name() + " is in triggers.ignored-mobs";
        }
        return whyNoEffect(victim, killer);
    }

    private static String describe(Entity victim) {
        return victim instanceof Player ? ((Player) victim).getName() : victim.getType().name();
    }

    @Override
    boolean wouldPlay(LivingEntity victim, Player killer) {
        return killer != null && effectFor(killer, victim) != null;
    }

    @Override
    boolean skipsWhenOtherEffectPlays() {
        return skipWhenDeathEffectPlays;
    }
}

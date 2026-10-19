package com.dank420girl.core;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.config.Problems;
import com.dank420girl.core.effect.EffectDefinition;
import com.dank420girl.core.platform.Debug;
import org.bukkit.Location;
import org.bukkit.World;
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
        final boolean newCancelled = triggers.bool("cancelled-deaths", true);
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
                setPlayForCancelledDeaths(newCancelled);
                skipWhenDeathEffectPlays = newSkip;
            }
        };
    }

    /** Notes where the victim died before other plugins handle the death; some teleport the player at once. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeathStart(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (plugin.isStarted() && (victim instanceof Player || mobs) && victim.getKiller() != null) {
            rememberDeathSpot(victim);
        }
    }

    /**
     * Runs after every other plugin has handled the death. Plugins can cancel deaths on Paper: duel and practice
     * plugins do so to end a fight without the death screen, so cancelled deaths count as well, unless
     * {@code triggers.cancelled-deaths} is false.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Location spot = takeDeathSpot(victim);
        if (!plugin.isStarted()) {
            return;
        }
        handleDeath(victim, victim.getKiller(), spot, isCancelled(event), null);
    }

    @Override
    void handleDeath(LivingEntity victim, Player killer, Location spot, boolean cancelled, String source) {
        if (killer == null) {
            // Only players: mobs die all the time.
            if (victim instanceof Player && Debug.isEnabled()) {
                Debug.log(plugin.getLogger(), describe(victim) + " died" + from(source)
                        + ", no kill effect: no player killed them");
            }
            return;
        }
        String refusal = triggerRefusal(killer, victim);
        if (refusal != null) {
            if (Debug.isEnabled()) {
                Debug.log(plugin.getLogger(), killer.getName() + " killed " + describe(victim) + from(source)
                        + ", no kill effect: " + refusal);
            }
            return;
        }
        String what = killer.getName() + " killed " + describe(victim) + from(source);
        if (alreadyHandled(victim, source, cancelled)) {
            Debug.log(plugin.getLogger(), what + ", no kill effect: this death was already handled");
            return;
        }
        if (cancelled && !playsForCancelledDeaths()) {
            Debug.log(plugin.getLogger(), what + ", no kill effect: another plugin cancelled the death "
                    + "(triggers.cancelled-deaths is false)");
            return;
        }
        Location location = placeOfDeath(victim, spot);
        EffectDefinition effect = effectFor(killer, location.getWorld());
        if (effect == null) {
            if (Debug.isEnabled()) {
                Debug.log(plugin.getLogger(), what + ", no kill effect: " + whyNoEffect(location.getWorld(), killer));
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
        play(effect, victim, killer, killer, location, what, source, cancelled);
    }

    /** Why the triggers settings rule out an effect for this kill, or {@code null} if they allow it. */
    private String triggerRefusal(Player killer, Entity victim) {
        if (victim instanceof Player) {
            if (victim.equals(killer) ? !self : !players) {
                return victim.equals(killer) ? "triggers.self is false" : "triggers.players is false";
            }
        } else if (!mobs) {
            return "triggers.mobs is false";
        } else if (ignoredMobs.contains(victim.getType().name())) {
            return victim.getType().name() + " is in triggers.ignored-mobs";
        }
        return null;
    }

    /** The killer's effect, if it plays in the world where the victim died. */
    private EffectDefinition effectFor(Player killer, World world) {
        return worldEnabled(world) ? plugin.activeEffect(killer) : null;
    }

    private static String describe(Entity victim) {
        return victim instanceof Player ? ((Player) victim).getName() : victim.getType().name();
    }

    @Override
    boolean wouldPlay(LivingEntity victim, Player killer) {
        return killer != null && triggerRefusal(killer, victim) == null
                && effectFor(killer, placeOfDeath(victim, peekDeathSpot(victim)).getWorld()) != null;
    }

    @Override
    boolean skipsWhenOtherEffectPlays() {
        return skipWhenDeathEffectPlays;
    }
}

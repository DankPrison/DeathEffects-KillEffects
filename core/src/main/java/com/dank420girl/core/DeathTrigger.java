package com.dank420girl.core;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.config.Problems;
import com.dank420girl.core.effect.EffectDefinition;
import com.dank420girl.core.platform.Debug;
import org.bukkit.Location;
import org.bukkit.World;
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
        final boolean newCancelled = triggers.bool("cancelled-deaths", true);
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
                setPlayForCancelledDeaths(newCancelled);
                skipWhenKillEffectPlays = newSkip;
            }
        };
    }

    /** Notes where the player died before other plugins handle the death; some teleport the player at once. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeathStart(PlayerDeathEvent event) {
        if (plugin.isStarted()) {
            rememberDeathSpot(event.getEntity());
        }
    }

    /**
     * Runs after every other plugin has handled the death. Plugins can cancel deaths on Paper: duel and practice
     * plugins do so to end a fight without the death screen, so cancelled deaths count as well, unless
     * {@code triggers.cancelled-deaths} is false.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Location spot = takeDeathSpot(victim);
        if (!plugin.isStarted()) {
            return;
        }
        handleDeath(victim, victim.getKiller(), spot, isCancelled(event), null);
    }

    @Override
    void handleDeath(LivingEntity victimEntity, Player killer, Location spot, boolean cancelled, String source) {
        if (!(victimEntity instanceof Player)) {
            return;
        }
        Player victim = (Player) victimEntity;
        String what = victim.getName() + " died" + from(source);
        String refusal = triggerRefusal(victim, killer);
        if (refusal != null) {
            if (Debug.isEnabled()) {
                Debug.log(plugin.getLogger(), what + ", no death effect: " + refusal);
            }
            return;
        }
        if (alreadyHandled(victim, source, cancelled)) {
            Debug.log(plugin.getLogger(), what + ", no death effect: this death was already handled");
            return;
        }
        if (cancelled && !playsForCancelledDeaths()) {
            Debug.log(plugin.getLogger(), what + ", no death effect: another plugin cancelled the death "
                    + "(triggers.cancelled-deaths is false)");
            return;
        }
        Location location = placeOfDeath(victim, spot);
        EffectDefinition effect = effectFor(victim, location.getWorld());
        if (effect == null) {
            if (Debug.isEnabled()) {
                Debug.log(plugin.getLogger(), what + ", no death effect: " + whyNoEffect(location.getWorld(), victim));
            }
            return;
        }
        if (skipWhenKillEffectPlays && killer != null && !killer.equals(victim) && otherWouldPlay(victim, killer)) {
            Debug.log(plugin.getLogger(), what + ", no death effect: the kill effect of " + killer.getName()
                    + " plays instead (compatibility.skip-when-kill-effect-plays)");
            return;
        }
        play(effect, victim, killer, victim, location, what, source, cancelled);
    }

    /** Why the triggers settings rule out an effect for this death, or {@code null} if they allow it. */
    private String triggerRefusal(Player victim, Player killer) {
        boolean byPlayer = killer != null && !killer.equals(victim);
        if (byPlayer ? !killedByPlayer : !otherDeaths) {
            return byPlayer ? "triggers.killed-by-player is false" : "triggers.other-deaths is false";
        }
        return null;
    }

    /** The victim's effect, if it plays in the world where they died. */
    private EffectDefinition effectFor(Player victim, World world) {
        return worldEnabled(world) ? plugin.activeEffect(victim) : null;
    }

    @Override
    boolean wouldPlay(LivingEntity victim, Player killer) {
        if (!(victim instanceof Player) || triggerRefusal((Player) victim, killer) != null) {
            return false;
        }
        return effectFor((Player) victim, placeOfDeath(victim, peekDeathSpot(victim)).getWorld()) != null;
    }

    @Override
    boolean skipsWhenOtherEffectPlays() {
        return skipWhenKillEffectPlays;
    }
}

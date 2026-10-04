package com.dank420girl.core;

import com.dank420girl.core.compat.OtherPlugin;
import com.dank420girl.core.config.Problems;
import com.dank420girl.core.effect.EffectDefinition;
import com.dank420girl.core.platform.Debug;
import com.dank420girl.core.platform.Locations;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * When the effects of a plugin play, with the triggers and compatibility sections of config.yml that go
 * with it: {@link DeathTrigger} for DeathEffects, {@link KillTrigger} for KillEffects.
 */
public abstract class Trigger implements Listener {
    protected final EffectsPlugin plugin;
    /** The other plugin of the pair, asked when both effects could play for the same death. */
    private final OtherPlugin other;

    /** @param plugin the plugin, or {@code null} in tests that only read settings */
    Trigger(EffectsPlugin plugin) {
        this.plugin = plugin;
        this.other = plugin == null ? null : new OtherPlugin(plugin.variant().other().name(), plugin.getLogger());
    }

    /**
     * Reads the plugin's own part of config.yml.
     *
     * @return applies the settings; only called once all files have been read without errors
     */
    abstract Runnable readSettings(ConfigurationSection config, Problems problems);

    /** Whether an effect would play for this death, not counting the compatibility settings. */
    abstract boolean wouldPlay(LivingEntity victim, Player killer);

    /** Whether this plugin skips its effect when the other plugin's effect plays for the same death. */
    abstract boolean skipsWhenOtherEffectPlays();

    /** Asks the other plugin whether its effect would play for this death. */
    boolean otherWouldPlay(LivingEntity victim, Player killer) {
        return other.ask("wouldPlayEffect", new Class<?>[] {LivingEntity.class, Player.class}, victim, killer);
    }

    /** Asks the other plugin whether it skips its effect when this plugin's effect plays. */
    boolean otherSkips() {
        return other.ask("skipsWhenOtherEffectPlays", new Class<?>[0]);
    }

    /**
     * Fires the plugin's play event and plays the effect it ends up with.
     *
     * @param owner the player the effect belongs to
     * @param what  describes the death for debug messages, such as "Alice died"
     */
    void play(EffectDefinition effect, LivingEntity victim, Player killer, Player owner, String what) {
        String kind = plugin.variant().kind();
        EffectPlay play = plugin.firePlayEvent(victim, killer, effect.id(), Locations.visible(victim.getLocation()));
        if (play == null) {
            Debug.log(plugin.getLogger(), what + ", no " + kind + " effect: another plugin cancelled the play event");
            return;
        }
        EffectDefinition chosen = plugin.effects().get(play.effectId());
        if (chosen != null && play.location() != null && play.location().getWorld() != null) {
            Debug.log(plugin.getLogger(), what + ", playing the " + kind + " effect '" + chosen.id() + "'");
            plugin.engine().play(chosen, play.location(), owner, null);
        }
    }

    /** Why no effect plays when the world or the player's selection is the reason, for debug messages. */
    String whyNoEffect(LivingEntity victim, Player owner) {
        if (!plugin.settings().isWorldEnabled(victim.getWorld())) {
            return "effects are turned off in the world " + victim.getWorld().getName()
                    + " (settings.enabled-worlds and disabled-worlds)";
        }
        return plugin.whyNoEffect(owner);
    }
}

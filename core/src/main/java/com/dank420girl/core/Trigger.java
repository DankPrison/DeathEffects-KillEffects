package com.dank420girl.core;

import com.dank420girl.core.compat.OtherPlugin;
import com.dank420girl.core.config.Problems;
import com.dank420girl.core.effect.EffectDefinition;
import com.dank420girl.core.platform.Debug;
import com.dank420girl.core.platform.Locations;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * When the effects of a plugin play, with the triggers and compatibility sections of config.yml that go
 * with it: {@link DeathTrigger} for DeathEffects, {@link KillTrigger} for KillEffects.
 * <p>
 * Deaths are reported by the server's death events and, on servers with a fight plugin such as StrikePractice,
 * by that plugin (see {@link com.dank420girl.core.compat.FightPlugins}). Fight plugins often cancel the server's
 * death so the player never sees the death screen, and may report the same death both ways; some plugins also
 * cancel a death and let the player die for real right after. Each death plays one effect, for the report that
 * comes first.
 */
public abstract class Trigger implements Listener {
    /** Reports of one victim's death within this time, from different sources, are the same death. */
    private static final long SAME_DEATH_NANOS = TimeUnit.SECONDS.toNanos(3);
    /** A noted death spot older than this belongs to a death whose handling never finished. */
    private static final long SPOT_NANOS = TimeUnit.SECONDS.toNanos(30);

    protected final EffectsPlugin plugin;
    /** The other plugin of the pair, asked when both effects could play for the same death. */
    private final OtherPlugin other;
    /** Where victims died, noted before other plugins handle the death: some move the player right away. */
    private final Map<UUID, Spot> deathSpots = new ConcurrentHashMap<UUID, Spot>();
    /** When the last death reported by the server was handled, per victim (System.nanoTime). */
    private final Map<UUID, Long> serverDeaths = new ConcurrentHashMap<UUID, Long>();
    /** The same for deaths that another plugin had cancelled. */
    private final Map<UUID, Long> cancelledDeaths = new ConcurrentHashMap<UUID, Long>();
    /** The same for deaths reported by a fight plugin. */
    private final Map<UUID, Long> fightDeaths = new ConcurrentHashMap<UUID, Long>();
    private volatile boolean playForCancelledDeaths = true;

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

    /**
     * Handles a death.
     *
     * @param killer    the player who killed the victim, or {@code null}
     * @param spot      where the victim died, if it was noted before other plugins handled the death
     * @param cancelled true if another plugin cancelled the death, so the victim did not really die
     * @param source    the fight plugin that reported the death, or {@code null} for the server's death event
     */
    abstract void handleDeath(LivingEntity victim, Player killer, Location spot, boolean cancelled, String source);

    /** Whether effects play for deaths that another plugin cancelled ({@code triggers.cancelled-deaths}). */
    boolean playsForCancelledDeaths() {
        return playForCancelledDeaths;
    }

    void setPlayForCancelledDeaths(boolean value) {
        playForCancelledDeaths = value;
    }

    /** A death reported by a fight plugin such as StrikePractice. Call it on the thread that owns the victim. */
    public void fightDeath(LivingEntity victim, Player killer, String source) {
        if (plugin == null || !plugin.isStarted() || victim == null) {
            return;
        }
        // A spot is only noted while the server is handling the death: StrikePractice reported it meanwhile.
        handleDeath(victim, killer, peekDeathSpot(victim), false, source);
    }

    /** Notes where the victim is now, before other plugins handle the death. */
    void rememberDeathSpot(LivingEntity victim) {
        long now = System.nanoTime();
        deathSpots.put(victim.getUniqueId(), new Spot(victim.getLocation(), now));
        if (deathSpots.size() > 64) {
            for (Iterator<Spot> it = deathSpots.values().iterator(); it.hasNext(); ) {
                if (now - it.next().time > SPOT_NANOS) {
                    it.remove();
                }
            }
        }
    }

    /** The spot noted when the server's death event for the victim began, or {@code null}; forgets it. */
    Location takeDeathSpot(LivingEntity victim) {
        if (deathSpots.isEmpty()) {
            return null;
        }
        Spot spot = deathSpots.remove(victim.getUniqueId());
        return spot == null || spot.location.getWorld() == null ? null : spot.location.clone();
    }

    /** The spot noted for the victim's death the server is handling right now, or {@code null}. */
    Location peekDeathSpot(LivingEntity victim) {
        Spot spot = deathSpots.get(victim.getUniqueId());
        if (spot == null || System.nanoTime() - spot.time > SPOT_NANOS || spot.location.getWorld() == null) {
            return null;
        }
        return spot.location.clone();
    }

    /** Where the victim died: the noted spot, or where the victim is now. */
    static Location placeOfDeath(LivingEntity victim, Location spot) {
        return spot != null ? spot : victim.getLocation();
    }

    /** True if another plugin cancelled the death event. Paper allows that; on older servers it cannot happen. */
    static boolean isCancelled(Event event) {
        return event instanceof Cancellable && ((Cancellable) event).isCancelled();
    }

    /**
     * True if this death was handled moments ago through another report: by the other source (the server or a
     * fight plugin), or by the server once more around a cancelled death (a plugin that cancels the death and
     * reports or lets it happen again). Two deaths that both really happened are never the same.
     *
     * @param source    the fight plugin reporting the death, or {@code null} for the server's death event
     * @param cancelled true if another plugin cancelled this report of the death
     */
    boolean alreadyHandled(LivingEntity victim, String source, boolean cancelled) {
        UUID id = victim.getUniqueId();
        if (source != null) {
            return recent(serverDeaths, id);
        }
        return recent(fightDeaths, id) || recent(cancelled ? serverDeaths : cancelledDeaths, id);
    }

    private static boolean recent(Map<UUID, Long> handled, UUID victim) {
        Long time = handled.get(victim);
        // nanoTime differences cannot go backwards, unlike the wall clock.
        return time != null && System.nanoTime() - time <= SAME_DEATH_NANOS;
    }

    /** Remembers that this report of the death was handled: an effect played, or a plugin stopped it. */
    private void markHandled(LivingEntity victim, String source, boolean cancelled) {
        long now = System.nanoTime();
        mark(source == null ? serverDeaths : fightDeaths, victim.getUniqueId(), now);
        if (cancelled) {
            mark(cancelledDeaths, victim.getUniqueId(), now);
        }
    }

    private static void mark(Map<UUID, Long> handled, UUID victim, long now) {
        handled.put(victim, now);
        if (handled.size() > 64) {
            for (Iterator<Long> it = handled.values().iterator(); it.hasNext(); ) {
                if (now - it.next() > SAME_DEATH_NANOS) {
                    it.remove();
                }
            }
        }
    }

    /** " (StrikePractice)" for deaths reported by a fight plugin, for debug messages. */
    static String from(String source) {
        return source == null ? "" : " (" + source + ")";
    }

    /** Asks the other plugin whether its effect would play for this death. */
    boolean otherWouldPlay(LivingEntity victim, Player killer) {
        return other.ask("wouldPlayEffect", new Class<?>[] {LivingEntity.class, Player.class}, victim, killer);
    }

    /** Asks the other plugin whether it skips its effect when this plugin's effect plays. */
    boolean otherSkips() {
        return other.ask("skipsWhenOtherEffectPlays", new Class<?>[0]);
    }

    /**
     * Fires the plugin's play event and plays the effect it ends up with. Either way the death counts as handled,
     * so a second report of it does not try again, for example after a plugin cancelled the play event in an arena.
     *
     * @param owner     the player the effect belongs to
     * @param location  where the victim died
     * @param what      describes the death for debug messages, such as "Alice died"
     * @param source    the fight plugin that reported the death, or {@code null} for the server's death event
     * @param cancelled true if another plugin cancelled the death
     */
    void play(EffectDefinition effect, LivingEntity victim, Player killer, Player owner, Location location, String what,
              String source, boolean cancelled) {
        String kind = plugin.variant().kind();
        EffectPlay play = plugin.firePlayEvent(victim, killer, effect.id(), Locations.visible(location));
        markHandled(victim, source, cancelled);
        if (play == null) {
            Debug.log(plugin.getLogger(), what + ", no " + kind + " effect: another plugin cancelled the play event");
            return;
        }
        EffectDefinition chosen = plugin.effects().get(play.effectId());
        if (chosen == null) {
            Debug.log(plugin.getLogger(), what + ", no " + kind + " effect: another plugin changed the effect to '"
                    + play.effectId() + "', which does not exist");
            return;
        }
        if (play.location() == null || play.location().getWorld() == null) {
            Debug.log(plugin.getLogger(), what + ", no " + kind + " effect: another plugin changed the location to one "
                    + "without a world");
            return;
        }
        Debug.log(plugin.getLogger(), what + ", playing the " + kind + " effect '" + chosen.id() + "'");
        plugin.engine().play(chosen, play.location(), owner, null);
    }

    /** True if effects play in the world. */
    boolean worldEnabled(World world) {
        return plugin.settings().isWorldEnabled(world);
    }

    /** Why no effect plays when the world or the player's selection is the reason, for debug messages. */
    String whyNoEffect(World world, Player owner) {
        if (!worldEnabled(world)) {
            return "effects are turned off in the world " + world.getName() + " (settings.enabled-worlds and disabled-worlds)";
        }
        return plugin.whyNoEffect(owner);
    }

    /** A place and when it was noted (System.nanoTime). */
    private static final class Spot {
        final Location location;
        final long time;

        Spot(Location location, long time) {
            this.location = location;
            this.time = time;
        }
    }
}

package com.dank420girl.core.compat;

import com.dank420girl.core.Trigger;
import com.dank420girl.core.platform.Debug;
import com.dank420girl.core.platform.scheduler.Scheduler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Deaths in the fights of duel and practice plugins. These plugins usually cancel the real death, so the player
 * skips the death screen, or prevent it altogether, and report it through an event of their own. That event is
 * used through reflection, so neither plugin needs the other.
 */
public final class FightPlugins implements Listener {

    /** A fight plugin's death event: its class and the methods that return the victim and the killer. */
    private static final class Hook {
        final String plugin;
        final String eventClass;
        final String victimMethod;
        final String killerMethod;

        Hook(String plugin, String eventClass, String victimMethod, String killerMethod) {
            this.plugin = plugin;
            this.eventClass = eventClass;
            this.victimMethod = victimMethod;
            this.killerMethod = killerMethod;
        }
    }

    private static final Hook[] HOOKS = {
            // FightDeathEvent(Player player, Player killer, Fight fight, boolean finalRound),
            // see https://strikepractice-api.toppe.dev/doc/ga/strikepractice/events/FightDeathEvent.html
            new Hook("StrikePractice", "ga.strikepractice.events.FightDeathEvent", "getPlayer", "getKiller"),
    };

    private final Plugin owner;
    private final Trigger trigger;
    private final Scheduler scheduler;
    private final Logger logger;
    /** The listener registered for each fight plugin's death event, by plugin name. */
    private final Map<String, Listener> hooked = new ConcurrentHashMap<String, Listener>();

    public FightPlugins(Plugin owner, Trigger trigger, Scheduler scheduler) {
        this.owner = owner;
        this.trigger = trigger;
        this.scheduler = scheduler;
        this.logger = owner.getLogger();
    }

    /** Uses the fight plugins that are enabled already, and those enabled later. */
    public void start() {
        owner.getServer().getPluginManager().registerEvents(this, owner);
        for (Hook hook : HOOKS) {
            hook(hook);
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        for (Hook hook : HOOKS) {
            if (hook.plugin.equals(event.getPlugin().getName())) {
                hook(hook);
            }
        }
    }

    /**
     * Stops listening when a fight plugin is disabled: once it is enabled again, possibly with new classes after
     * a reload by a plugin manager, its event is hooked again.
     */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        Listener listener = hooked.remove(event.getPlugin().getName());
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
    }

    private void hook(final Hook hook) {
        PluginManager pm = owner.getServer().getPluginManager();
        Plugin fightPlugin = pm.getPlugin(hook.plugin);
        Listener listener = new Listener() {
        };
        if (fightPlugin == null || !fightPlugin.isEnabled() || hooked.putIfAbsent(hook.plugin, listener) != null) {
            return;
        }
        try {
            final Class<?> type = Class.forName(hook.eventClass, true, fightPlugin.getClass().getClassLoader());
            final Method victim = type.getMethod(hook.victimMethod);
            final Method killer = type.getMethod(hook.killerMethod);
            pm.registerEvent(type.asSubclass(Event.class), listener, EventPriority.MONITOR, new EventExecutor() {
                @Override
                public void execute(Listener ignored, Event event) {
                    // Events of subclasses share the handler list.
                    if (type.isInstance(event)) {
                        fightDeath(hook, event, victim, killer);
                    }
                }
            }, owner, false);
            logger.info("Effects also play for deaths in " + hook.plugin + " fights.");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            hooked.remove(hook.plugin);
            // Not a problem as such: fight deaths that the plugin cancels still count (triggers.cancelled-deaths).
            logger.info(hook.plugin + " " + fightPlugin.getDescription().getVersion() + " has no fight death event that "
                    + "can be used (" + e + "); effects play for the deaths the server reports.");
        }
    }

    private void fightDeath(final Hook hook, Event event, Method victimMethod, Method killerMethod) {
        Object victim;
        Object killer;
        try {
            victim = victimMethod.invoke(event);
            killer = killerMethod.invoke(event);
        } catch (InvocationTargetException | IllegalAccessException | RuntimeException e) {
            Debug.logRepeating(logger, "fight death " + hook.plugin, "Cannot read a death from " + hook.plugin, e);
            return;
        }
        if (!(victim instanceof LivingEntity)) {
            return;
        }
        final LivingEntity dead = (LivingEntity) victim;
        final Player by = killer instanceof Player ? (Player) killer : null;
        if (event.isAsynchronous() || !scheduler.isOwnedByCurrentThread(dead)) {
            // The play event and the effect need the thread that owns the victim.
            scheduler.executeFor(dead, new Runnable() {
                @Override
                public void run() {
                    trigger.fightDeath(dead, by, hook.plugin);
                }
            });
            return;
        }
        trigger.fightDeath(dead, by, hook.plugin);
    }
}

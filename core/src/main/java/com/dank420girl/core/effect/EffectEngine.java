package com.dank420girl.core.effect;

import com.dank420girl.core.effect.layer.AnimationLayer;
import com.dank420girl.core.effect.layer.Layer;
import com.dank420girl.core.effect.particle.ParticleService;
import com.dank420girl.core.effect.shape.AnimationFrame;
import com.dank420girl.core.effect.shape.ShapeDefinition;
import com.dank420girl.core.platform.Debug;
import com.dank420girl.core.platform.scheduler.Scheduler;
import com.dank420girl.core.platform.scheduler.TaskHandle;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plays effects: runs every layer at the right time on the thread that owns the location, and keeps
 * the number of running animations within the configured limit.
 */
public final class EffectEngine {
    private final Plugin plugin;
    private final Scheduler scheduler;
    private final Logger logger;
    private final ParticleService particles;
    private final SoundService sounds;
    private final FireworkService fireworks;

    private final AtomicInteger runningAnimations = new AtomicInteger();
    private final Set<TaskHandle> tasks = Collections.newSetFromMap(new ConcurrentHashMap<TaskHandle, Boolean>());
    private final Set<String> reportedErrors = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private volatile int maxAnimations = 40;
    private volatile boolean stopped;

    public EffectEngine(Plugin plugin, Scheduler scheduler, Logger logger, ParticleService particles,
                        SoundService sounds, FireworkService fireworks) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.logger = logger;
        this.particles = particles;
        this.sounds = sounds;
        this.fireworks = fireworks;
    }

    public void setMaxAnimations(int maxAnimations) {
        this.maxAnimations = Math.max(0, maxAnimations);
    }

    public ParticleService particles() {
        return particles;
    }

    public SoundService sounds() {
        return sounds;
    }

    public FireworkService fireworks() {
        return fireworks;
    }

    public int runningAnimations() {
        return runningAnimations.get();
    }

    /** The players who should hear and see an effect at the location. */
    public List<Player> listeners(Location location, EffectContext context) {
        return particles.viewers(location, context.audience());
    }

    /**
     * Plays an effect. Can be called from any thread; the effect runs on the thread that owns the location.
     *
     * @param owner    the player whose effect it is, or {@code null}
     * @param audience only this player sees particles and hears sounds, or {@code null} for everyone nearby
     */
    public void play(final EffectDefinition effect, final Location location, final Player owner, final Player audience) {
        if (stopped || location.getWorld() == null) {
            return;
        }
        final Location origin = location.clone();
        scheduler.executeAt(origin, new Runnable() {
            @Override
            public void run() {
                playNow(effect, origin, owner, audience);
            }
        });
    }

    private void playNow(EffectDefinition effect, Location origin, Player owner, Player audience) {
        final EffectContext context = new EffectContext(origin, owner, audience, new Random());
        for (final Layer layer : effect.layers()) {
            for (int i = 0; i < layer.repeat(); i++) {
                final int iteration = i;
                long tick = layer.delay() + (long) i * layer.interval();
                if (tick <= 0) {
                    runLayer(effect, layer, context, iteration);
                    continue;
                }
                final TaskHandle[] holder = new TaskHandle[1];
                holder[0] = scheduler.runAtLater(origin, new Runnable() {
                    @Override
                    public void run() {
                        if (holder[0] != null) {
                            tasks.remove(holder[0]);
                        }
                        if (!stopped) {
                            runLayer(effect, layer, context, iteration);
                        }
                    }
                }, tick);
                tasks.add(holder[0]);
            }
        }
    }

    private void runLayer(EffectDefinition effect, Layer layer, EffectContext context, int iteration) {
        try {
            layer.play(this, context, iteration);
        } catch (RuntimeException | LinkageError e) {
            reportError("effect '" + effect.id() + "', " + layer.type() + " layer", e);
        }
    }

    /** Starts an animation, unless too many are running already. Called on the thread owning the location. */
    public void startAnimation(AnimationLayer layer, EffectContext context) {
        if (stopped) {
            return;
        }
        int running = runningAnimations.incrementAndGet();
        if (maxAnimations > 0 && running > maxAnimations) {
            decrementAnimations();
            Debug.logRepeating(logger, "animation limit", "Skipped an animation: performance.max-running-animations ("
                    + maxAnimations + ") are running", null);
            return;
        }
        ShapeDefinition shape = layer.definition();
        AnimationRun run = new AnimationRun(shape, shape.start(context.random()), context, context.at(layer.y()));
        TaskHandle handle = scheduler.runAtTimer(run.location, run, 1L, shape.period());
        run.attach(handle);
    }

    private void decrementAnimations() {
        int current;
        do {
            current = runningAnimations.get();
        } while (current > 0 && !runningAnimations.compareAndSet(current, current - 1));
    }

    /** Stops every running effect, for example when the plugin is disabled or reloaded. */
    public void stopAll() {
        List<TaskHandle> copy = new ArrayList<TaskHandle>(tasks);
        tasks.clear();
        for (TaskHandle task : copy) {
            task.cancel();
        }
        runningAnimations.set(0);
    }

    /** Stops the engine for good. */
    public void shutdown() {
        stopped = true;
        stopAll();
    }

    private void reportError(String where, Throwable e) {
        if (reportedErrors.add(where + e.getClass().getName())) {
            logger.log(Level.WARNING, "Error while playing " + where + " (further errors like this are hidden)", e);
        }
    }

    /** A running animation, ticked by the scheduler every period. */
    private final class AnimationRun implements Runnable {
        private final ShapeDefinition shape;
        private final ShapeDefinition.Animation animation;
        private final EffectContext context;
        private final Location location;
        private volatile TaskHandle handle;
        private volatile boolean finished;
        private int frame;
        private int elapsed;

        AnimationRun(ShapeDefinition shape, ShapeDefinition.Animation animation, EffectContext context, Location location) {
            this.shape = shape;
            this.animation = animation;
            this.context = context;
            this.location = location;
        }

        void attach(TaskHandle taskHandle) {
            handle = taskHandle;
            tasks.add(taskHandle);
            if (finished || taskHandle == TaskHandle.NONE) {
                finish();
            }
        }

        @Override
        public void run() {
            if (finished) {
                return;
            }
            if (stopped || !plugin.isEnabled() || (handle != null && handle.isCancelled())) {
                finish();
                return;
            }
            try {
                List<Player> viewers = particles.viewers(location, context.audience());
                animation.frame(new AnimationFrame(particles, viewers, location.getX(), location.getY(), location.getZ(),
                        location.getYaw(), frame, context.random()));
            } catch (RuntimeException | LinkageError e) {
                reportError("an animation", e);
                finish();
                return;
            }
            frame++;
            elapsed += shape.period();
            if (elapsed >= shape.duration() || animation.isFinished()) {
                finish();
            }
        }

        private void finish() {
            if (finished && handle == null) {
                return;
            }
            boolean first = !finished;
            finished = true;
            TaskHandle current = handle;
            if (current != null) {
                current.cancel();
                tasks.remove(current);
            }
            if (first) {
                decrementAnimations();
            }
        }
    }
}

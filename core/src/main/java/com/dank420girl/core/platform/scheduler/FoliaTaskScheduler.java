package com.dank420girl.core.platform.scheduler;

import com.dank420girl.core.platform.Reflect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Scheduler for Folia, reached through reflection so the plugin can be compiled against old APIs.
 * <p>
 * Folia rejects delays below one tick, so every delay is raised to at least one.
 */
final class FoliaTaskScheduler implements Scheduler {
    private final Plugin plugin;

    private final Object globalScheduler;
    private final Object regionScheduler;
    private final Object asyncScheduler;

    private final Method globalExecute;
    private final Method globalRunDelayed;
    private final Method globalRunAtFixedRate;
    private final Method globalCancelTasks;
    private final Method regionExecute;
    private final Method regionRunDelayed;
    private final Method regionRunAtFixedRate;
    private final Method asyncRunNow;
    private final Method asyncCancelTasks;
    private final Method entityGetScheduler;
    private final Method entityExecute;
    private final Method entityRunDelayed;
    private final Method taskCancel;
    private final Method ownsLocation;
    private final Method ownsEntity;
    private final Method isGlobalTickThread;

    FoliaTaskScheduler(Plugin plugin) {
        this.plugin = plugin;
        try {
            Server server = Bukkit.getServer();
            Class<?> globalClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            Class<?> regionClass = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
            Class<?> asyncClass = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            Class<?> entityClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            Class<?> taskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");

            globalScheduler = Server.class.getMethod("getGlobalRegionScheduler").invoke(server);
            regionScheduler = Server.class.getMethod("getRegionScheduler").invoke(server);
            asyncScheduler = Server.class.getMethod("getAsyncScheduler").invoke(server);

            globalExecute = globalClass.getMethod("execute", Plugin.class, Runnable.class);
            globalRunDelayed = globalClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            globalRunAtFixedRate = globalClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            globalCancelTasks = globalClass.getMethod("cancelTasks", Plugin.class);

            regionExecute = regionClass.getMethod("execute", Plugin.class, World.class, int.class, int.class, Runnable.class);
            regionRunDelayed = regionClass.getMethod("runDelayed", Plugin.class, World.class, int.class, int.class, Consumer.class, long.class);
            regionRunAtFixedRate = regionClass.getMethod("runAtFixedRate", Plugin.class, World.class, int.class, int.class, Consumer.class, long.class, long.class);

            asyncRunNow = asyncClass.getMethod("runNow", Plugin.class, Consumer.class);
            asyncCancelTasks = asyncClass.getMethod("cancelTasks", Plugin.class);

            entityGetScheduler = Entity.class.getMethod("getScheduler");
            entityExecute = entityClass.getMethod("execute", Plugin.class, Runnable.class, Runnable.class, long.class);
            entityRunDelayed = entityClass.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);

            taskCancel = taskClass.getMethod("cancel");
            ownsLocation = Server.class.getMethod("isOwnedByCurrentRegion", Location.class);
            ownsEntity = Server.class.getMethod("isOwnedByCurrentRegion", Entity.class);
            isGlobalTickThread = Server.class.getMethod("isGlobalTickThread");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("This Folia version is not supported", e);
        }
    }

    @Override
    public void execute(Runnable task) {
        if (isGlobalThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            Reflect.invoke(globalExecute, globalScheduler, plugin, task);
        }
    }

    @Override
    public void executeAt(Location location, Runnable task) {
        if (isOwnedByCurrentThread(location)) {
            task.run();
        } else if (plugin.isEnabled()) {
            Reflect.invoke(regionExecute, regionScheduler, plugin, location.getWorld(),
                    location.getBlockX() >> 4, location.getBlockZ() >> 4, task);
        }
    }

    @Override
    public void executeFor(Entity entity, Runnable task) {
        if (isOwnedByCurrentThread(entity)) {
            task.run();
        } else if (plugin.isEnabled()) {
            Object scheduler = Reflect.invoke(entityGetScheduler, entity);
            Reflect.invoke(entityExecute, scheduler, plugin, task, null, 1L);
        }
    }

    @Override
    public TaskHandle runLater(Runnable task, long delayTicks) {
        if (!plugin.isEnabled()) {
            return TaskHandle.NONE;
        }
        return wrap(Reflect.invoke(globalRunDelayed, globalScheduler, plugin, consumer(task), Math.max(1L, delayTicks)));
    }

    @Override
    public TaskHandle runAtLater(Location location, Runnable task, long delayTicks) {
        if (!plugin.isEnabled()) {
            return TaskHandle.NONE;
        }
        return wrap(Reflect.invoke(regionRunDelayed, regionScheduler, plugin, location.getWorld(),
                location.getBlockX() >> 4, location.getBlockZ() >> 4, consumer(task), Math.max(1L, delayTicks)));
    }

    @Override
    public TaskHandle runForLater(Entity entity, Runnable task, long delayTicks) {
        if (!plugin.isEnabled()) {
            return TaskHandle.NONE;
        }
        Object scheduler = Reflect.invoke(entityGetScheduler, entity);
        Object handle = Reflect.invoke(entityRunDelayed, scheduler, plugin, consumer(task), null, Math.max(1L, delayTicks));
        return handle == null ? TaskHandle.NONE : wrap(handle);
    }

    @Override
    public TaskHandle runTimer(Runnable task, long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) {
            return TaskHandle.NONE;
        }
        return wrap(Reflect.invoke(globalRunAtFixedRate, globalScheduler, plugin, consumer(task),
                Math.max(1L, delayTicks), Math.max(1L, periodTicks)));
    }

    @Override
    public TaskHandle runAtTimer(Location location, Runnable task, long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) {
            return TaskHandle.NONE;
        }
        return wrap(Reflect.invoke(regionRunAtFixedRate, regionScheduler, plugin, location.getWorld(),
                location.getBlockX() >> 4, location.getBlockZ() >> 4, consumer(task),
                Math.max(1L, delayTicks), Math.max(1L, periodTicks)));
    }

    @Override
    public void runAsync(Runnable task) {
        if (plugin.isEnabled()) {
            Reflect.invoke(asyncRunNow, asyncScheduler, plugin, consumer(task));
        } else {
            task.run();
        }
    }

    @Override
    public boolean isOwnedByCurrentThread(Location location) {
        return (Boolean) Reflect.invoke(ownsLocation, Bukkit.getServer(), location);
    }

    @Override
    public boolean isOwnedByCurrentThread(Entity entity) {
        return (Boolean) Reflect.invoke(ownsEntity, Bukkit.getServer(), entity);
    }

    @Override
    public void cancelAll() {
        try {
            Reflect.invoke(globalCancelTasks, globalScheduler, plugin);
            Reflect.invoke(asyncCancelTasks, asyncScheduler, plugin);
        } catch (RuntimeException ignored) {
            // the server is shutting down
        }
    }

    private boolean isGlobalThread() {
        return (Boolean) Reflect.invoke(isGlobalTickThread, Bukkit.getServer());
    }

    private static Consumer<Object> consumer(final Runnable task) {
        return new Consumer<Object>() {
            @Override
            public void accept(Object scheduledTask) {
                task.run();
            }
        };
    }

    private TaskHandle wrap(final Object scheduledTask) {
        return new TaskHandle() {
            private volatile boolean cancelled;

            @Override
            public void cancel() {
                cancelled = true;
                try {
                    Reflect.invoke(taskCancel, scheduledTask);
                } catch (RuntimeException ignored) {
                    // already finished
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };
    }
}

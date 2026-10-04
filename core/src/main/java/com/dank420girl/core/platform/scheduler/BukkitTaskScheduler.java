package com.dank420girl.core.platform.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Scheduler for CraftBukkit, Spigot, Paper and their forks: one main thread for everything.
 */
final class BukkitTaskScheduler implements Scheduler {
    private final Plugin plugin;

    BukkitTaskScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public void executeAt(Location location, Runnable task) {
        execute(task);
    }

    @Override
    public void executeFor(Entity entity, Runnable task) {
        execute(task);
    }

    @Override
    public TaskHandle runLater(Runnable task, long delayTicks) {
        if (!plugin.isEnabled()) {
            return TaskHandle.NONE;
        }
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(0L, delayTicks)));
    }

    @Override
    public TaskHandle runAtLater(Location location, Runnable task, long delayTicks) {
        return runLater(task, delayTicks);
    }

    @Override
    public TaskHandle runForLater(Entity entity, Runnable task, long delayTicks) {
        return runLater(task, delayTicks);
    }

    @Override
    public TaskHandle runTimer(Runnable task, long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) {
            return TaskHandle.NONE;
        }
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, task, Math.max(0L, delayTicks), Math.max(1L, periodTicks)));
    }

    @Override
    public TaskHandle runAtTimer(Location location, Runnable task, long delayTicks, long periodTicks) {
        return runTimer(task, delayTicks, periodTicks);
    }

    @Override
    public void runAsync(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            task.run();
        }
    }

    @Override
    public boolean isOwnedByCurrentThread(Location location) {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public boolean isOwnedByCurrentThread(Entity entity) {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    private static TaskHandle wrap(final BukkitTask task) {
        return new TaskHandle() {
            private volatile boolean cancelled;

            @Override
            public void cancel() {
                cancelled = true;
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };
    }
}

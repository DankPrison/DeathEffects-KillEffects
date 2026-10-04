package com.dank420girl.core.platform.scheduler;

import com.dank420girl.core.platform.Platform;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Runs code on the right thread for the server software.
 * <p>
 * On CraftBukkit, Spigot and Paper everything runs on the main thread. On Folia the world is split
 * into regions that tick on their own threads: code touching a location must run on the thread
 * that owns it, and code touching an entity (including sending a player a message) must run on the
 * entity's thread.
 */
public interface Scheduler {

    static Scheduler create(Plugin plugin) {
        return Platform.isFolia() ? new FoliaTaskScheduler(plugin) : new BukkitTaskScheduler(plugin);
    }

    /** Runs the task on the main thread (Folia: the global region), right away if already there. */
    void execute(Runnable task);

    /** Runs the task on the thread that owns the location, right away if already there. */
    void executeAt(Location location, Runnable task);

    /**
     * Runs the task on the thread that owns the entity, right away if already there. On Folia the task
     * is dropped if the entity is removed first (for example a player who logs out).
     */
    void executeFor(Entity entity, Runnable task);

    TaskHandle runLater(Runnable task, long delayTicks);

    TaskHandle runAtLater(Location location, Runnable task, long delayTicks);

    TaskHandle runForLater(Entity entity, Runnable task, long delayTicks);

    TaskHandle runTimer(Runnable task, long delayTicks, long periodTicks);

    TaskHandle runAtTimer(Location location, Runnable task, long delayTicks, long periodTicks);

    /** Runs the task on a background thread. */
    void runAsync(Runnable task);

    /** True if the current thread may access the location. */
    boolean isOwnedByCurrentThread(Location location);

    /** True if the current thread may access the entity. */
    boolean isOwnedByCurrentThread(Entity entity);

    /** Cancels every task of the plugin. */
    void cancelAll();
}

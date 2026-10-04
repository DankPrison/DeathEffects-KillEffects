package com.dank420girl.core.storage;

import com.dank420girl.core.config.ConfigFiles;
import com.dank420girl.core.config.StorageSettings;
import com.dank420girl.core.platform.Debug;
import org.bukkit.configuration.InvalidConfigurationException;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs all storage work on one background thread, so the server never waits for disks or databases.
 * <p>
 * If the configured storage cannot be used (the database cannot be reached, on startup or later), an
 * interim storage stands in for it: players-fallback.yml, or players.yml when that was used until then.
 * Interim storages remember cleared selections as well. The configured storage is tried again every few
 * minutes and on reload; once it works, everything saved in the meantime is copied to it before it is
 * used, and the selections of players who joined meanwhile are loaded again from it. A file that cannot
 * be read is renamed instead of being overwritten.
 */
public final class StorageService {
    /** Selections made while the database could not be reached. */
    public static final String FALLBACK_FILE = "players-fallback.yml";
    private static final long YAML_SAVE_DELAY_SECONDS = 3;
    private static final long RETRY_MINUTES = 5;

    /** Receives a loaded selection on the storage thread. */
    public interface LoadCallback {
        /**
         * @param effect      the stored effect id, or {@code null}
         * @param provisional true if it was loaded from a storage that stands in for the configured one; the
         *                    selection is loaded again (see {@link #setReloadListener}) once that can be used
         */
        void loaded(String effect, boolean provisional);
    }

    private final Logger logger;
    private final File dataFolder;
    private final ScheduledExecutorService executor;
    private final Predicate<String> isKnownEffect;
    private final Function<StorageSettings, Storage> databases;
    /** Online players whose selection was loaded from an interim storage. Storage thread only. */
    private final Set<UUID> loadedMeanwhile = new HashSet<UUID>();
    private volatile BiConsumer<UUID, String> reloadListener;

    /** The storage in use. */
    private volatile Storage storage;
    /** The storage config.yml asks for. */
    private volatile StorageSettings settings;
    /** Set while the storage in use is not the one config.yml asks for; describes the one asked for. */
    private volatile String unreachable;
    /** False while the settings are the defaults because config.yml could not be read. */
    private volatile boolean settingsConfirmed = true;
    private ScheduledFuture<?> pendingFlush;
    private ScheduledFuture<?> retry;
    private long lastErrorLog;
    private long lastRetryWarning;

    public StorageService(String pluginName, Logger logger, File dataFolder, Predicate<String> isKnownEffect) {
        this(pluginName, logger, dataFolder, isKnownEffect, null);
    }

    /**
     * @param databases creates the storage for SQLite and MySQL settings, or {@code null} for the real
     *                  databases; tests pass their own
     */
    public StorageService(final String pluginName, Logger logger, File dataFolder, Predicate<String> isKnownEffect,
                          Function<StorageSettings, Storage> databases) {
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.isKnownEffect = isKnownEffect;
        this.databases = databases;
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, pluginName + " storage");
                thread.setDaemon(true);
                return thread;
            }
        });
        pool.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        pool.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        this.executor = pool;
    }

    /**
     * Uses the storage configured in config.yml (in the background). Called on startup and reload; a
     * storage that could not be used before is tried again.
     */
    public void open(StorageSettings newSettings) {
        open(newSettings, true);
    }

    /**
     * @param confirmed {@code false} if config.yml could not be read and these are the default settings: the
     *                  selections loaded until the real settings are known are loaded again then
     */
    public void open(final StorageSettings newSettings, final boolean confirmed) {
        submit(new Runnable() {
            @Override
            public void run() {
                boolean same = newSettings.sameAs(settings);
                settings = newSettings;
                settingsConfirmed = confirmed;
                if (storage != null && same && unreachable == null) {
                    if (confirmed) {
                        loadedMeanwhile.clear(); // the storage in use was the right one
                    }
                    return;
                }
                connect(true);
            }
        });
    }

    /**
     * Receives the selections of players that were loaded from an interim storage, loaded again once the
     * configured storage can be used. Called on the storage thread.
     */
    public void setReloadListener(BiConsumer<UUID, String> listener) {
        this.reloadListener = listener;
    }

    /** Switches to the configured storage, or keeps working with an interim one. Storage thread only. */
    private void connect(boolean firstAttempt) {
        Storage previous = storage;
        if (previous instanceof YamlStorage) {
            // A database copies players.yml when it is used for the first time, so it must be up to date.
            ((YamlStorage) previous).flush();
        }
        Storage next = create(settings);
        try {
            next.open();
        } catch (Exception e) {
            next.close();
            failed(previous, next, e, firstAttempt);
            return;
        }
        // What was saved in the meantime is copied first. Until that has worked, the configured storage is
        // not used, so these selections can never overwrite newer ones.
        YamlStorage leftover = null;
        int copied = 0;
        try {
            if (!isFallback(previous)) {
                leftover = openLeftoverFallback();
                if (leftover != null) {
                    copied += copy(leftover.entries(), next);
                }
            }
            Map<UUID, String[]> interim = interimEntries(previous);
            if (interim != null) {
                copied += copy(interim, next);
            }
            if (next instanceof YamlStorage) {
                ((YamlStorage) next).flush();
            }
        } catch (Exception e) {
            next.close();
            useInterim(previous, leftover, next.describe());
            logger.log(Level.WARNING, "Could not copy the selections saved in the meantime to " + next.describe() + "; "
                    + storage.describe() + " stays in use, and it is tried again every " + RETRY_MINUTES + " minutes and on reload", e);
            return;
        }
        storage = next;
        unreachable = null;
        cancelRetry();
        if (leftover != null || isFallback(previous)) {
            deleteFallbackFile();
        }
        if (previous != null && previous != next && !isFallback(previous)) {
            // (The fallback is not closed: closing would write the file again.)
            previous.close();
        }
        if (copied > 0) {
            logger.info("Copied " + copied + " selections saved in the meantime to " + next.describe() + ".");
        }
        logger.info("Saving effect selections with " + next.describe());
        reloadLoadedMeanwhile(next);
    }

    private void failed(Storage previous, Storage attempted, Exception e, boolean firstAttempt) {
        if (attempted instanceof YamlStorage && e instanceof InvalidConfigurationException
                && renameBroken(((YamlStorage) attempted).file(), e)) {
            connect(firstAttempt); // the file is out of the way now, so a new one is started
            return;
        }
        String wanted = attempted.describe();
        useInterim(previous, null, wanted);
        Throwable logged = e instanceof ClassNotFoundException ? null : e;
        if (firstAttempt) {
            logger.log(Level.SEVERE, "Could not use " + wanted + ": " + e.getMessage() + ". " + interimDescription()
                    + "; it is tried again every " + RETRY_MINUTES + " minutes and on reload.", logged);
        } else {
            long now = System.currentTimeMillis();
            if (now - lastRetryWarning > 3600000L) {
                lastRetryWarning = now;
                logger.warning("Still cannot use " + wanted + ": " + e.getMessage() + " (using " + storage.describe() + ")");
            }
        }
    }

    /**
     * The configured storage ({@code wanted}) cannot be used yet: decides what stands in for it and retries
     * it later. players.yml stays in use if it was used until now; otherwise players-fallback.yml is used.
     *
     * @param leftover players-fallback.yml read from an earlier run, or {@code null}
     */
    private void useInterim(Storage previous, YamlStorage leftover, String wanted) {
        unreachable = wanted;
        if (previous instanceof YamlStorage && !isFallback(previous)) {
            // Cleared selections are remembered as well, so they can be copied to the database later.
            ((YamlStorage) previous).rememberClears();
            storage = previous;
        } else if (isInterim(previous)) {
            storage = previous;
        } else {
            if (previous != null) {
                previous.close(); // a database that is not the configured one any more
            }
            storage = leftover != null ? leftover : openFallback();
        }
        scheduleRetry();
    }

    private String interimDescription() {
        Storage current = storage;
        if (isFallback(current)) {
            return "Selections are kept in " + FALLBACK_FILE + " and copied to it once it can be used";
        }
        if (current instanceof MemoryStorage) {
            return "Selections are only kept in memory until then";
        }
        return "Still using " + current.describe() + " until then";
    }

    /**
     * Called when the storage in use fails. If the database cannot be reached any more, selections are kept
     * in players-fallback.yml from now on, as when it cannot be reached on startup. Storage thread only.
     *
     * @return {@code true} if the fallback is used now
     */
    private boolean switchToFallback(Storage failed, Exception e) {
        if (!(e instanceof StorageUnavailableException) || failed != storage || failed instanceof YamlStorage
                || failed instanceof MemoryStorage) {
            return false;
        }
        failed.close();
        if (unreachable == null) {
            unreachable = failed.describe();
        }
        storage = openFallback();
        logger.severe(e.getMessage() + ". " + interimDescription() + "; it is tried again every " + RETRY_MINUTES
                + " minutes and on reload.");
        scheduleRetry();
        return true;
    }

    private void reloadLoadedMeanwhile(Storage from) {
        BiConsumer<UUID, String> listener = reloadListener;
        if (loadedMeanwhile.isEmpty() || listener == null) {
            loadedMeanwhile.clear();
            return;
        }
        List<UUID> players = new ArrayList<UUID>(loadedMeanwhile);
        loadedMeanwhile.clear();
        for (int i = 0; i < players.size(); i++) {
            UUID player = players.get(i);
            String effect;
            try {
                effect = from.load(player);
            } catch (Exception e) {
                if (switchToFallback(from, e)) {
                    // Lost the database again: these players are loaded again the next time.
                    loadedMeanwhile.addAll(players.subList(i, players.size()));
                    return;
                }
                logError("load the effect of " + player, e);
                continue;
            }
            listener.accept(player, effect);
        }
        Debug.log(logger, "Loaded the selections of " + players.size() + " players again from " + from.describe());
    }

    private static boolean isFallback(Storage storage) {
        return storage instanceof YamlStorage && ((YamlStorage) storage).file().getName().equals(FALLBACK_FILE);
    }

    /** Storages that only stand in for the configured one: everything in them still has to be copied. */
    private static boolean isInterim(Storage storage) {
        return isFallback(storage) || storage instanceof MemoryStorage;
    }

    /** The selections an interim storage holds, cleared ones with an empty effect; {@code null} for others. */
    private static Map<UUID, String[]> interimEntries(Storage storage) {
        if (isFallback(storage)) {
            return ((YamlStorage) storage).entries();
        }
        if (storage instanceof MemoryStorage) {
            return ((MemoryStorage) storage).entries();
        }
        return null;
    }

    /** Copies selections; an empty effect clears the selection. Returns how many were copied. */
    private static int copy(Map<UUID, String[]> entries, Storage target) throws Exception {
        for (Map.Entry<UUID, String[]> entry : entries.entrySet()) {
            String effect = entry.getValue()[1];
            target.save(entry.getKey(), entry.getValue()[0], effect.isEmpty() ? null : effect);
        }
        return entries.size();
    }

    private Storage openFallback() {
        File file = new File(dataFolder, FALLBACK_FILE);
        YamlStorage fallback = new YamlStorage(file, logger, true);
        try {
            fallback.open();
            return fallback;
        } catch (InvalidConfigurationException e) {
            if (renameBroken(file, e)) {
                return new YamlStorage(file, logger, true); // a new, empty file
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not read " + FALLBACK_FILE, e);
        }
        logger.severe("Selections are only kept in memory until " + FALLBACK_FILE + " can be used.");
        return new MemoryStorage();
    }

    /**
     * players-fallback.yml left from an earlier run (the server stopped while the database could not be
     * reached), or {@code null} if there is none.
     *
     * @throws IOException if it exists but cannot be read right now; it must not be skipped then
     */
    private YamlStorage openLeftoverFallback() throws IOException {
        File file = new File(dataFolder, FALLBACK_FILE);
        if (!file.exists()) {
            return null;
        }
        YamlStorage leftover = new YamlStorage(file, logger, true);
        try {
            leftover.open();
            return leftover;
        } catch (InvalidConfigurationException e) {
            if (renameBroken(file, e)) {
                return null;
            }
            throw new IOException(FALLBACK_FILE + " cannot be read and cannot be renamed", e);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(FALLBACK_FILE + " cannot be read", e);
        }
    }

    private void deleteFallbackFile() {
        File file = new File(dataFolder, FALLBACK_FILE);
        if (!file.exists() || file.delete()) {
            return;
        }
        try {
            ConfigFiles.write(file, ""); // an empty file is never copied again
        } catch (IOException e) {
            logger.severe("Could not delete " + FALLBACK_FILE + " after copying it. Delete it while the server is stopped, "
                    + "or its selections are copied again on the next start.");
        }
    }

    /** Renames a file that is not valid YAML, so it is kept for the owner and a new one can be started. */
    private boolean renameBroken(File file, Exception e) {
        if (!file.exists()) {
            return false;
        }
        File broken = new File(file.getParentFile(), file.getName() + ".broken-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
        boolean renamed = file.renameTo(broken);
        logger.severe(file.getName() + " could not be read: " + firstLine(e.getMessage()));
        logger.severe(renamed
                ? "It was renamed to " + broken.getName() + " and a new " + file.getName() + " is used. Fix the old file "
                + "and rename it back while the server is stopped to get those selections back."
                : "It could not be renamed either.");
        return renamed;
    }

    private void scheduleRetry() {
        if (retry != null && !retry.isDone()) {
            return;
        }
        try {
            retry = executor.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    if (unreachable != null && settings != null) {
                        connect(false);
                    }
                }
            }, RETRY_MINUTES, RETRY_MINUTES, TimeUnit.MINUTES);
        } catch (RejectedExecutionException ignored) {
            // shutting down
        }
    }

    private void cancelRetry() {
        if (retry != null) {
            retry.cancel(false);
            retry = null;
        }
    }

    private Storage create(StorageSettings settings) {
        if (settings.type() == StorageSettings.Type.YAML) {
            YamlStorage yaml = new YamlStorage(new File(dataFolder, "players.yml"), logger, false);
            if (!settingsConfirmed) {
                // A stand-in for a storage that is not known yet: cleared selections must be copied too.
                yaml.rememberClears();
            }
            return yaml;
        }
        if (databases != null) {
            return databases.apply(settings);
        }
        return new SqlStorage(settings, dataFolder, logger, isKnownEffect);
    }

    /** What is used to save selections, for the version command. */
    public String describe() {
        Storage current = storage;
        if (current == null) {
            return "starting";
        }
        String missing = unreachable;
        String description = missing == null ? current.describe() : current.describe() + " - " + missing + " cannot be used";
        return settingsConfirmed ? description : description + " (the default, as config.yml cannot be read)";
    }

    /** Loads a selection; the callback runs on the storage thread. */
    public void load(final UUID player, final LoadCallback callback) {
        submit(new Runnable() {
            @Override
            public void run() {
                String effect = null;
                Storage current = storage;
                if (current != null) {
                    try {
                        effect = current.load(player);
                    } catch (Exception e) {
                        if (switchToFallback(current, e)) {
                            effect = loadFromFallback(player);
                        } else {
                            logError("load the effect of " + player, e);
                        }
                    }
                }
                boolean provisional = unreachable != null || !settingsConfirmed;
                if (provisional) {
                    loadedMeanwhile.add(player);
                }
                callback.loaded(effect, provisional);
            }
        });
    }

    /** Forgets a player who left, so their selection is not loaded again after an outage. */
    public void forget(final UUID player) {
        submit(new Runnable() {
            @Override
            public void run() {
                loadedMeanwhile.remove(player);
            }
        });
    }

    /** Saves a selection; {@code effect} is {@code null} to remove it. */
    public void save(final UUID player, final String name, final String effect) {
        submit(new Runnable() {
            @Override
            public void run() {
                Storage current = storage;
                if (current == null) {
                    return;
                }
                try {
                    current.save(player, name, effect);
                } catch (Exception e) {
                    if (switchToFallback(current, e)) {
                        try {
                            storage.save(player, name, effect);
                        } catch (Exception again) {
                            logError("save the effect of " + name, again);
                        }
                    } else {
                        logError("save the effect of " + name, e);
                    }
                }
                scheduleFlush();
            }
        });
    }

    private String loadFromFallback(UUID player) {
        try {
            return storage.load(player);
        } catch (Exception e) {
            logError("load the effect of " + player, e);
            return null;
        }
    }

    private void scheduleFlush() {
        if (!(storage instanceof YamlStorage) || (pendingFlush != null && !pendingFlush.isDone())) {
            return;
        }
        try {
            pendingFlush = executor.schedule(new Runnable() {
                @Override
                public void run() {
                    Storage current = storage;
                    if (current instanceof YamlStorage) {
                        ((YamlStorage) current).flush();
                    }
                }
            }, YAML_SAVE_DELAY_SECONDS, TimeUnit.SECONDS);
        } catch (RejectedExecutionException ignored) {
            // shutting down: the file is written when the storage is closed
        }
    }

    private void logError(String action, Exception e) {
        long now = System.currentTimeMillis();
        if (now - lastErrorLog > 60000) {
            lastErrorLog = now;
            logger.log(Level.WARNING, "Could not " + action + " (" + describe() + "); similar errors are hidden for a minute", e);
        }
    }

    private void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            // shutting down: do the work right away so nothing is lost
            task.run();
        }
    }

    private void closeNow() {
        cancelRetry();
        Storage current = storage;
        storage = null;
        if (current != null) {
            if (current instanceof MemoryStorage && !((MemoryStorage) current).entries().isEmpty()) {
                logger.severe(((MemoryStorage) current).entries().size() + " effect selections made while no file could be "
                        + "used are lost. Check that the plugin folder can be written.");
            }
            current.close();
        }
    }

    /** Writes everything and stops the storage thread. Blocks for at most ten seconds. */
    public void shutdown() {
        submit(new Runnable() {
            @Override
            public void run() {
                closeNow();
            }
        });
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Saving effect selections took too long and was stopped.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline).trim();
    }

    /** Last resort when no file can be used: selections last until the server stops. Storage thread only. */
    private static final class MemoryStorage implements Storage {
        /** Player to {name, effect}; the effect is empty for a cleared selection. */
        private final Map<UUID, String[]> selections = new LinkedHashMap<UUID, String[]>();

        @Override
        public String describe() {
            return "memory only";
        }

        @Override
        public void open() {
        }

        @Override
        public String load(UUID player) {
            String[] entry = selections.get(player);
            return entry == null || entry[1].isEmpty() ? null : entry[1];
        }

        @Override
        public void save(UUID player, String name, String effect) {
            selections.put(player, new String[] {name, effect == null ? "" : effect});
        }

        Map<UUID, String[]> entries() {
            return new LinkedHashMap<UUID, String[]>(selections);
        }

        @Override
        public void close() {
        }
    }
}

package com.dank420girl.core.platform;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Messages that help to find problems, shown in the console while {@code settings.debug} is true.
 * <p>
 * Servers do not show the FINE log level, so debug messages are logged as INFO with a "[debug]" tag
 * while debugging is on. Each plugin jar has its own copy of this class, so the switch is per plugin.
 */
public final class Debug {
    private static final long REPEAT_MILLIS = 60000;
    private static final ConcurrentHashMap<String, Long> lastLogged = new ConcurrentHashMap<String, Long>();
    private static volatile boolean enabled;

    private Debug() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void log(Logger logger, String message) {
        log(logger, message, null);
    }

    /**
     * Like {@link #log(Logger, String, Throwable)}, for problems that can happen many times a second (for
     * example once per particle frame and player): each kind of message is logged at most once a minute.
     */
    public static void logRepeating(Logger logger, String kind, String message, Throwable error) {
        if (!enabled && !logger.isLoggable(Level.FINE)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastLogged.get(kind);
        if (last != null && now - last < REPEAT_MILLIS) {
            return;
        }
        lastLogged.put(kind, now);
        log(logger, message + " (this message is shown at most once a minute)", error);
    }

    /** Logs the message (and the error, if any) when debugging is on; otherwise only at the FINE level. */
    public static void log(Logger logger, String message, Throwable error) {
        if (enabled) {
            logger.log(Level.INFO, "[debug] " + message, error);
        } else if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, message, error);
        }
    }
}

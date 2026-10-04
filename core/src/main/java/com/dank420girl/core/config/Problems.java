package com.dank420girl.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Collects mistakes found while reading a configuration file, so they can be reported together with
 * the place they were found, for example {@code effects.yml: effects.flame.layers[0]: unknown option 'cuont' (ignored)}.
 */
public final class Problems {
    private final String file;
    private final List<String> messages = new ArrayList<String>();

    public Problems(String file) {
        this.file = file;
    }

    public void warn(String path, String message) {
        messages.add(path == null || path.isEmpty() ? message : path + ": " + message);
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public int size() {
        return messages.size();
    }

    public List<String> messages() {
        return messages;
    }

    public void log(Logger logger) {
        for (String message : messages) {
            logger.warning(file + ": " + message);
        }
    }
}

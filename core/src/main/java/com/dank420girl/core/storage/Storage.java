package com.dank420girl.core.storage;

import java.util.UUID;

/**
 * Where the selected effect of each player is kept. All methods are called from the storage thread.
 */
public interface Storage {

    /** A short description for the version command and log messages. */
    String describe();

    void open() throws Exception;

    /** The stored effect id of the player, or {@code null}. */
    String load(UUID player) throws Exception;

    /**
     * Stores the selection of a player.
     *
     * @param name   the player's current name, kept for people reading the data
     * @param effect the effect id, or {@code null} to remove the selection
     */
    void save(UUID player, String name, String effect) throws Exception;

    /** Writes pending changes and releases resources. */
    void close();
}

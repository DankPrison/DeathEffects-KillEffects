package com.dank420girl.core.selection;

import com.dank420girl.core.effect.EffectDefinition;
import com.dank420girl.core.effect.EffectRegistry;
import com.dank420girl.core.storage.StorageService;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * The effect each online player has selected. Selections are loaded when a player joins and saved
 * as soon as they change.
 */
public final class SelectionService {
    /** A selection; compared by identity, so a selection the player made is never mistaken for a loaded one. */
    private static final class Selection {
        /** The effect id, or {@code null} for no effect. */
        final String id;
        /** True if it was loaded from the storage, false if the player (or a command) chose it. */
        final boolean loaded;

        Selection(String id, boolean loaded) {
            this.id = id;
            this.loaded = loaded;
        }
    }

    private final ConcurrentHashMap<UUID, Selection> selections = new ConcurrentHashMap<UUID, Selection>();
    /** One token per join: a load that finishes after the player left (or rejoined) is ignored. */
    private final ConcurrentHashMap<UUID, Object> sessions = new ConcurrentHashMap<UUID, Object>();
    private final StorageService storage;

    public SelectionService(StorageService storage) {
        this.storage = storage;
        storage.setReloadListener(new BiConsumer<UUID, String>() {
            @Override
            public void accept(UUID player, String effect) {
                reloaded(player, effect);
            }
        });
    }

    /**
     * Loads the player's selection in the background.
     *
     * @param loaded receives the stored effect id (or {@code null}) on the storage thread, unless the player
     *               changed the selection while it was loading
     */
    public void load(final Player player, final StorageService.LoadCallback loaded) {
        final UUID uuid = player.getUniqueId();
        final Object session = new Object();
        sessions.put(uuid, session);
        storage.load(uuid, new StorageService.LoadCallback() {
            @Override
            public void loaded(final String effect, boolean provisional) {
                final boolean[] applied = new boolean[1];
                // Atomic with unload(), so a player who left in the meantime is not added back.
                sessions.computeIfPresent(uuid, new BiFunction<UUID, Object, Object>() {
                    @Override
                    public Object apply(UUID key, Object current) {
                        if (current == session) {
                            applied[0] = selections.putIfAbsent(uuid, new Selection(effect, true)) == null;
                        }
                        return current;
                    }
                });
                if (applied[0]) {
                    loaded.loaded(effect, provisional);
                }
            }
        });
    }

    /**
     * A selection loaded again after the storage could not be used for a while. It replaces the one loaded
     * before, unless the player has chosen an effect since.
     */
    private void reloaded(UUID player, final String effect) {
        selections.computeIfPresent(player, new BiFunction<UUID, Selection, Selection>() {
            @Override
            public Selection apply(UUID key, Selection current) {
                return current.loaded ? new Selection(effect, true) : current;
            }
        });
    }

    /** Forgets the player when they leave; their selection is already saved. */
    public void unload(UUID player) {
        sessions.remove(player);
        selections.remove(player);
        storage.forget(player);
    }

    /** The selected effect id, or {@code null} for none or not loaded yet. */
    public String selectedId(UUID player) {
        Selection selection = selections.get(player);
        return selection == null ? null : selection.id;
    }

    /** True once the selection of the player has been loaded (or changed). */
    public boolean isLoaded(UUID player) {
        return selections.containsKey(player);
    }

    /** Selects an effect, or clears the selection with {@code null}, and saves it. */
    public void select(Player player, EffectDefinition effect) {
        String id = effect == null ? null : effect.id();
        if (player.isOnline()) {
            selections.put(player.getUniqueId(), new Selection(id, false));
        }
        storage.save(player.getUniqueId(), player.getName(), id);
    }

    /**
     * The effect that plays for the player: the selection, or the default effect if nothing (or an effect
     * that was removed since) is selected, provided the player may use it.
     */
    public EffectDefinition activeEffect(Player player, EffectRegistry effects, String defaultEffect, boolean requirePermission) {
        EffectDefinition effect = effects.get(selectedId(player.getUniqueId()));
        if (effect == null && defaultEffect != null && !defaultEffect.isEmpty()) {
            effect = effects.get(defaultEffect);
        }
        if (effect == null) {
            return null;
        }
        if (requirePermission && effect.permission() != null && !player.hasPermission(effect.permission())) {
            return null;
        }
        return effect;
    }
}

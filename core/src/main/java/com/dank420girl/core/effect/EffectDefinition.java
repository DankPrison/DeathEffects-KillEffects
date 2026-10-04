package com.dank420girl.core.effect;

import com.dank420girl.core.effect.layer.Layer;
import com.dank420girl.core.names.Names.ItemName;

import java.util.Collections;
import java.util.List;

/**
 * An effect from effects.yml: how it looks in the menu and the layers it plays.
 */
public final class EffectDefinition {
    private final String id;
    private final String name;
    private final ItemName icon;
    private final List<String> lore;
    private final String permission;
    private final int order;
    private final List<Layer> layers;

    public EffectDefinition(String id, String name, ItemName icon, List<String> lore, String permission, int order,
                            List<Layer> layers) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.lore = Collections.unmodifiableList(lore);
        this.permission = permission;
        this.order = order;
        this.layers = Collections.unmodifiableList(layers);
    }

    /** The id used in commands and storage, always lower case. */
    public String id() {
        return id;
    }

    /** The display name with colour codes already applied. */
    public String name() {
        return name;
    }

    public ItemName icon() {
        return icon;
    }

    /** The menu description with colour codes already applied. */
    public List<String> lore() {
        return lore;
    }

    /** The permission needed to use the effect, or {@code null} if everyone may use it. */
    public String permission() {
        return permission;
    }

    public int order() {
        return order;
    }

    public List<Layer> layers() {
        return layers;
    }

    /** Ticks until every layer has finished. */
    public int length() {
        int length = 0;
        for (Layer layer : layers) {
            length = Math.max(length, layer.length());
        }
        return length;
    }

    @Override
    public String toString() {
        return id;
    }
}

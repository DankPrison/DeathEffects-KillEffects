package com.dank420girl.core.effect.particle;

import com.dank420girl.core.effect.ColorSpec;
import com.dank420girl.core.names.NameTables.ParticleEntry;
import com.dank420girl.core.names.Names.ItemName;
import com.dank420girl.core.names.Names.ParticleName;

/**
 * A particle with everything it needs to be shown: an item for item particles, a block for block
 * particles, colours for dust and the like. Created when effects.yml is loaded, and prepared for
 * the server's particle output the first time it is shown.
 */
public final class ParticleSpec {
    private final ParticleName particle;
    private final ItemName item;
    private final ColorSpec color;
    private final ColorSpec color2;
    private final float size;
    private final Float number;

    /** Filled in by the active {@link ParticleOutput}; only ever one output is active. */
    volatile Object prepared;

    public ParticleSpec(ParticleName particle, ItemName item, ColorSpec color, ColorSpec color2, float size, Float number) {
        this.particle = particle;
        this.item = item;
        this.color = color;
        this.color2 = color2;
        this.size = size;
        this.number = number;
    }

    /** A plain particle without extra data. */
    public static ParticleSpec of(ParticleName particle) {
        return new ParticleSpec(particle, null, null, null, 1f, null);
    }

    public ParticleName particle() {
        return particle;
    }

    /** The item or block shown by item and block particles, or {@code null}. */
    public ItemName item() {
        return item;
    }

    public ColorSpec color() {
        return color;
    }

    public ColorSpec color2() {
        return color2;
    }

    public float size() {
        return size;
    }

    /** A numeric option some particles need (for example the power of dragon breath), or {@code null}. */
    public Float number() {
        return number;
    }

    public ParticleEntry.Kind kind() {
        return particle.kind();
    }

    /** True if each spawn may get a different colour, so the particles must be sent in groups. */
    public boolean hasChangingColor() {
        return color != null && !color.isFixed();
    }

    /** A copy of this particle with another item (used for lists of items). */
    public ParticleSpec withItem(ItemName otherItem) {
        return new ParticleSpec(particle, otherItem, color, color2, size, number);
    }

    @Override
    public String toString() {
        return particle.name() + (item != null ? "[" + item + "]" : "") + (color != null ? "{" + color + "}" : "");
    }
}

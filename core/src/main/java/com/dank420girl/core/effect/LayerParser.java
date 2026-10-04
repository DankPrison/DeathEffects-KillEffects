package com.dank420girl.core.effect;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.config.Problems;
import com.dank420girl.core.effect.layer.AnimationLayer;
import com.dank420girl.core.effect.layer.FireworkLayer;
import com.dank420girl.core.effect.layer.Layer;
import com.dank420girl.core.effect.layer.LightningLayer;
import com.dank420girl.core.effect.layer.ParticleLayer;
import com.dank420girl.core.effect.layer.SoundLayer;
import com.dank420girl.core.effect.particle.ParticleService;
import com.dank420girl.core.effect.particle.ParticleSpec;
import com.dank420girl.core.effect.shape.Shape;
import com.dank420girl.core.effect.shape.ShapeDefinition;
import com.dank420girl.core.names.NameTables.ParticleEntry;
import com.dank420girl.core.names.Names;
import com.dank420girl.core.names.Names.ItemName;
import com.dank420girl.core.names.Names.ParticleName;
import com.dank420girl.core.names.Names.SoundName;
import com.dank420girl.core.platform.MinecraftVersion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the layer lists of effects.yml into {@link Layer}s, checking every name against the server.
 */
public final class LayerParser {
    private final Names names;
    private final ParticleService particles;
    private final MinecraftVersion source;

    /**
     * @param source the Minecraft version the names in the file were written for, or {@code null}
     */
    public LayerParser(Names names, ParticleService particles, MinecraftVersion source) {
        this.names = names;
        this.particles = particles;
        this.source = source;
    }

    /**
     * Reads one layer.
     *
     * @return the layer, or {@code null} if it is unusable (the reason is reported)
     */
    public Layer parse(Map<?, ?> raw, String path, Problems problems) {
        OptionReader o = new OptionReader(raw, path, problems);
        String type = o.string("type", null);
        if (type == null) {
            o.warn("missing 'type' (particle, firework, lightning, sound or animation); layer skipped");
            return null;
        }
        Layer layer;
        switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "particle":
            case "particles": {
                List<ParticleSpec> specs = particleVariants(o, "", null, null);
                layer = specs.isEmpty() ? null : new ParticleLayer(o, specs);
                break;
            }
            case "firework":
            case "fireworks":
                layer = new FireworkLayer(o);
                break;
            case "lightning":
                layer = new LightningLayer(o);
                break;
            case "sound": {
                SoundSpec sound = sound(o, "sound", null);
                layer = sound == null ? null : new SoundLayer(o, sound);
                break;
            }
            case "animation": {
                Shape shape = o.choice("shape", Shape.class, null);
                if (shape == null) {
                    if (!o.has("shape")) {
                        o.warn("animation layers need a 'shape'; layer skipped");
                    }
                    layer = null;
                } else {
                    ShapeDefinition definition = shape.create(o, this);
                    // Without a particle that can be shown the animation would only take up a slot.
                    layer = definition.hasParticles() ? new AnimationLayer(o, shape, definition) : null;
                }
                break;
            }
            default:
                o.warn("unknown layer type '" + type + "' (use particle, firework, lightning, sound or animation)");
                return null;
        }
        if (layer != null) {
            o.reportUnused();
        }
        return layer;
    }

    /** A required particle option; reports a problem and returns {@code null} if it cannot be used. */
    public ParticleSpec particle(OptionReader o, String prefix, String defaultName, String defaultColor) {
        List<ParticleSpec> list = particleVariants(o, prefix, defaultName, defaultColor);
        return list.isEmpty() ? null : list.get(0);
    }

    /** A particle that can be turned off with {@code none}; returns {@code null} when off or unusable. */
    public ParticleSpec optionalParticle(OptionReader o, String prefix, String defaultName) {
        List<String> names = o.strings(prefix + "particle");
        String name = names.isEmpty() ? defaultName : names.get(0);
        if (name == null || name.trim().isEmpty() || name.trim().equalsIgnoreCase("none")) {
            return null;
        }
        return particle(o, prefix, defaultName, null);
    }

    /**
     * Reads a particle with its extra data. A list of items or blocks gives one particle per entry.
     */
    public List<ParticleSpec> particleVariants(OptionReader o, String prefix, String defaultName, String defaultColor) {
        String key = prefix + "particle";
        if (!particles.isAvailable()) {
            // Already reported once; mark the options as read so they are not reported as typos.
            markRead(o, prefix);
            return Collections.emptyList();
        }
        List<String> candidates = o.strings(key);
        if (candidates.isEmpty() && defaultName != null) {
            candidates = Collections.singletonList(defaultName);
        }
        if (candidates.isEmpty() || candidates.get(0).trim().isEmpty()) {
            o.warn("missing '" + key + "'");
            return Collections.emptyList();
        }
        // A list gives alternatives for older versions: the first particle the server knows is used.
        ParticleName particle = null;
        for (String candidate : candidates) {
            particle = names.particle(candidate);
            if (particle != null) {
                break;
            }
        }
        if (particle == null) {
            o.warn("'" + join(candidates) + "' is not a particle on this Minecraft version (" + names.serverVersion() + "); skipped");
            return Collections.emptyList();
        }

        List<ItemName> items = new ArrayList<ItemName>();
        ParticleEntry.Kind kind = particle.kind();
        if (kind == ParticleEntry.Kind.ITEM || kind == ParticleEntry.Kind.BLOCK) {
            String singular = kind == ParticleEntry.Kind.ITEM ? "item" : "block";
            List<String> values = new ArrayList<String>(o.strings(prefix + singular + "s"));
            values.addAll(o.strings(prefix + singular));
            if (kind == ParticleEntry.Kind.BLOCK) {
                // Blocks may also be given as items.
                values.addAll(o.strings(prefix + "items"));
                values.addAll(o.strings(prefix + "item"));
            }
            if (values.isEmpty()) {
                o.warn("'" + particle.canonical() + "' particles need '" + prefix + singular + "'; using STONE");
                values.add("STONE");
            }
            // In a list, items that do not exist on this version are left out quietly.
            for (String value : values) {
                ItemName item = values.size() == 1 ? item(o, prefix + singular, value) : names.item(value, source);
                if (item != null) {
                    items.add(item);
                }
            }
            if (items.isEmpty()) {
                if (values.size() > 1) {
                    o.warn("none of '" + join(values) + "' is an item on this Minecraft version (" + names.serverVersion() + ")");
                }
                return Collections.emptyList();
            }
        }

        ColorSpec color = o.color(prefix + "color");
        if (color == null && defaultColor != null) {
            color = ColorSpec.parse(defaultColor);
        }
        ColorSpec color2 = o.color(prefix + "color2");
        float size = (float) o.number(prefix + "size", 1, 0.01, 10);
        Float value = o.optionalNumber(prefix + "value");

        List<ParticleSpec> result = new ArrayList<ParticleSpec>();
        ParticleSpec base = new ParticleSpec(particle, items.isEmpty() ? null : items.get(0), color, color2, size, value);
        if (items.size() <= 1) {
            result.add(base);
        } else {
            for (ItemName item : items) {
                result.add(base.withItem(item));
            }
        }
        List<ParticleSpec> usable = new ArrayList<ParticleSpec>(result.size());
        for (ParticleSpec spec : result) {
            if (particles.prepare(spec)) {
                usable.add(spec);
            } else {
                o.warn("the particle " + spec + " cannot be shown on this server version; skipped");
            }
        }
        return usable;
    }

    private static void markRead(OptionReader o, String prefix) {
        for (String option : new String[] {"particle", "item", "items", "block", "blocks", "color", "color2", "size", "value"}) {
            o.has(prefix + option);
        }
    }

    /** False if the server cannot show particles at all. */
    public boolean particlesAvailable() {
        return particles.isAvailable();
    }

    /** Translates an item or block name, reporting it if it does not exist on this server. */
    public ItemName item(OptionReader o, String key, String value) {
        ItemName item = names.item(value, source);
        if (item == null) {
            o.warn("'" + key + "': '" + value + "' is not an item on this Minecraft version (" + names.serverVersion() + ")");
        }
        return item;
    }

    /**
     * Reads an item option that may be a list of alternatives: the first item the server knows is used.
     *
     * @return the item, or {@code null} if the option is missing or none of the items exist (reported)
     */
    public ItemName firstItem(OptionReader o, String key, String defaultValue) {
        List<String> values = o.strings(key);
        if (values.isEmpty()) {
            if (defaultValue == null) {
                return null;
            }
            values = Collections.singletonList(defaultValue);
        }
        for (String value : values) {
            ItemName item = names.item(value, source);
            if (item != null) {
                return item;
            }
        }
        o.warn("'" + key + "': '" + join(values) + "' is not an item on this Minecraft version (" + names.serverVersion() + ")");
        return null;
    }

    private static String join(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(value);
        }
        return sb.toString();
    }

    /**
     * Reads a sound with its volume and pitch.
     *
     * @param prefix prefix of the volume and pitch keys, or {@code null} for "volume" and "pitch"
     */
    public SoundSpec sound(OptionReader o, String key, String prefix) {
        List<String> values = o.strings(key);
        String p = prefix == null ? "" : prefix;
        float volume = (float) o.number(p + "volume", 1, 0, 100);
        float pitch = (float) o.number(p + "pitch", 1, 0.5, 2);
        if (values.isEmpty()) {
            o.warn("missing '" + key + "'");
            return null;
        }
        String first = values.get(0).trim();
        if (first.isEmpty() || first.equalsIgnoreCase("none")) {
            return null;
        }
        // A list gives alternatives for older versions: the first sound the server knows is used.
        for (String value : values) {
            SoundName sound = names.sound(value);
            if (sound != null) {
                return new SoundSpec(sound, volume, pitch);
            }
        }
        o.warn("'" + join(values) + "' is not a sound on this Minecraft version (" + names.serverVersion() + ")");
        return null;
    }

    public Names names() {
        return names;
    }

    public MinecraftVersion source() {
        return source;
    }
}

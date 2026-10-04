package com.dank420girl.core.effect.layer;

import com.dank420girl.core.config.OptionReader;
import com.dank420girl.core.effect.ColorSpec;
import com.dank420girl.core.effect.EffectContext;
import com.dank420girl.core.effect.EffectEngine;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Firework explosions. Shapes and colours can be fixed, lists or RANDOM.
 */
public final class FireworkLayer extends Layer {
    private static final FireworkEffect.Type[] TYPES = FireworkEffect.Type.values();

    private final int count;
    private final double spread;
    private final List<FireworkEffect.Type> types;
    private final ColorSpec colors;
    private final int colorCount;
    private final ColorSpec fadeColors;
    private final Boolean flicker;
    private final Boolean trail;
    private final boolean instant;

    public FireworkLayer(OptionReader options) {
        super("firework", options, 1.0);
        this.count = options.integer("count", 1, 1, 20);
        this.spread = options.number("spread", 0.5, 0, 20);
        this.types = readTypes(options);
        ColorSpec parsedColors = options.color("colors");
        this.colors = parsedColors == null ? ColorSpec.random() : parsedColors;
        this.colorCount = options.integer("color-count", 2, 1, 8);
        this.fadeColors = options.color("fade-colors");
        this.flicker = readOptionalBoolean(options, "flicker");
        this.trail = readOptionalBoolean(options, "trail");
        this.instant = options.bool("instant", true);
    }

    private static List<FireworkEffect.Type> readTypes(OptionReader options) {
        List<FireworkEffect.Type> list = new ArrayList<FireworkEffect.Type>();
        for (String value : options.strings("shape")) {
            String name = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
            if (name.equals("RANDOM")) {
                list.clear();
                return list;
            }
            FireworkEffect.Type type = null;
            for (FireworkEffect.Type candidate : TYPES) {
                if (candidate.name().equals(name)) {
                    type = candidate;
                }
            }
            if (type == null) {
                options.warn("unknown firework shape '" + value + "' (use BALL, BALL_LARGE, STAR, BURST, CREEPER or RANDOM)");
            } else {
                list.add(type);
            }
        }
        return list;
    }

    /** true, false or RANDOM (stored as null). */
    private static Boolean readOptionalBoolean(OptionReader options, String key) {
        String value = options.string(key, "random");
        if (value.equalsIgnoreCase("random")) {
            return null;
        }
        return options.bool(key, false);
    }

    @Override
    public void play(EffectEngine engine, EffectContext context, int iteration) {
        Random random = context.random();
        for (int i = 0; i < count; i++) {
            Location location = context.at(y());
            if (spread > 0) {
                location.add((random.nextDouble() * 2 - 1) * spread, random.nextDouble() * spread * 0.5,
                        (random.nextDouble() * 2 - 1) * spread);
            }
            engine.fireworks().launch(location, buildEffect(random, iteration * count + i), instant);
        }
    }

    private FireworkEffect buildEffect(Random random, int index) {
        FireworkEffect.Type type = types.isEmpty() ? TYPES[random.nextInt(TYPES.length)] : types.get(random.nextInt(types.size()));
        FireworkEffect.Builder builder = FireworkEffect.builder().with(type);
        List<Integer> fixed = colors.fixedColors();
        if (colors.isFixed() || fixed.size() > 1) {
            for (int rgb : fixed) {
                builder.withColor(Color.fromRGB(rgb));
            }
        } else {
            for (int c = 0; c < colorCount; c++) {
                builder.withColor(Color.fromRGB(colors.rgb(index + c, random)));
            }
        }
        if (fadeColors != null) {
            List<Integer> fade = fadeColors.fixedColors();
            if (!fade.isEmpty()) {
                for (int rgb : fade) {
                    builder.withFade(Color.fromRGB(rgb));
                }
            } else {
                builder.withFade(Color.fromRGB(fadeColors.rgb(index, random)));
            }
        }
        builder.flicker(flicker == null ? random.nextBoolean() : flicker);
        builder.trail(trail == null ? random.nextBoolean() : trail);
        return builder.build();
    }
}

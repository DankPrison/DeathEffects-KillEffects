package com.dank420girl.core.effect;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ColorSpecTest {
    private final Random random = new Random(1);

    @Test
    public void parsesSingleColours() {
        assertEquals(0xFF0000, ColorSpec.parse("#FF0000").rgb(0, random));
        assertEquals(0x00FF00, ColorSpec.parse("00ff00").rgb(0, random));
        assertEquals(0x0A141E, ColorSpec.parse("10, 20, 30").rgb(0, random));
        assertEquals(0xFFD700, ColorSpec.parse("gold").rgb(0, random));
        assertEquals(0x8B0000, ColorSpec.parse("dark-red").rgb(0, random));
        assertTrue(ColorSpec.parse("RED").isFixed());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidColours() {
        ColorSpec.parse("#12345");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsComponentsOutOfRange() {
        ColorSpec.parse("300,0,0");
    }

    @Test
    public void cyclesThroughLists() {
        ColorSpec spec = ColorSpec.parseList(Arrays.asList("RED", "#00FF00", "0,0,255"));
        assertFalse(spec.isFixed());
        assertEquals(0xFF0000, spec.rgb(0, random));
        assertEquals(0x00FF00, spec.rgb(1, random));
        assertEquals(0x0000FF, spec.rgb(2, random));
        assertEquals(0xFF0000, spec.rgb(3, random));
        assertEquals(Arrays.asList(0xFF0000, 0x00FF00, 0x0000FF), spec.fixedColors());
    }

    @Test
    public void rainbowAndRandomChange() {
        ColorSpec rainbow = ColorSpec.parse("RAINBOW");
        Set<Integer> colours = new HashSet<Integer>();
        for (int step = 0; step < 20; step++) {
            colours.add(rainbow.rgb(step, random));
        }
        assertTrue(colours.size() > 10);
        ColorSpec randomColours = ColorSpec.parse("random");
        Set<Integer> randoms = new HashSet<Integer>();
        for (int i = 0; i < 20; i++) {
            randoms.add(randomColours.rgb(0, random));
        }
        assertTrue(randoms.size() > 10);
    }
}

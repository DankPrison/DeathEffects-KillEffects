package com.dank420girl.core.platform;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MinecraftVersionTest {
    @Test
    public void parsesServerVersionStrings() {
        assertEquals(MinecraftVersion.of(1, 8, 8), MinecraftVersion.parse("git-Spigot-db6de12-18fbb24 (MC: 1.8.8)"));
        assertEquals(MinecraftVersion.of(1, 21, 4), MinecraftVersion.parse("1.21.4-R0.1-SNAPSHOT"));
        assertEquals(MinecraftVersion.of(26, 3), MinecraftVersion.parse("26.3.build.12-beta"));
        assertEquals(MinecraftVersion.of(26, 1, 2), MinecraftVersion.parse("26.1.2"));
        assertEquals(MinecraftVersion.of(1, 7, 10), MinecraftVersion.parse("git-Bukkit-1.7.10-R0.1 (MC: 1.7.10)"));
        assertNull(MinecraftVersion.parse("unknown"));
        assertNull(MinecraftVersion.parse(null));
    }

    @Test
    public void comparesVersions() {
        MinecraftVersion v = MinecraftVersion.of(1, 20, 4);
        assertTrue(v.isAtLeast(1, 20));
        assertTrue(v.isBefore(1, 20, 5));
        assertFalse(v.isLegacy());
        assertTrue(MinecraftVersion.of(1, 12, 2).isLegacy());
        assertTrue(MinecraftVersion.of(26, 1).isAtLeast(1, 21, 11));
        assertEquals("1.21", MinecraftVersion.of(1, 21, 0).toString());
        assertEquals("1.21.4", MinecraftVersion.of(1, 21, 4).toString());
    }
}

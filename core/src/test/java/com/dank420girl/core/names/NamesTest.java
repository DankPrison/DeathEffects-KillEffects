package com.dank420girl.core.names;

import com.dank420girl.core.platform.MinecraftVersion;
import com.dank420girl.core.testing.TestServer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NamesTest {
    private static final MinecraftVersion V1_13 = MinecraftVersion.of(1, 13);

    @Test
    public void itemsAreTranslatedForLegacyServers() {
        Names names = TestServer.of("1.8.8").names();
        assertEquals("STAINED_GLASS_PANE:7", names.translateItem("GRAY_STAINED_GLASS_PANE", null));
        assertEquals("INK_SACK:1", names.translateItem("RED_DYE", null));
        assertEquals("INK_SACK:1", names.translateItem("ROSE_RED", V1_13));
        assertEquals("WOOL:14", names.translateItem("minecraft:red_wool", null));
        assertEquals("WOOL:14", names.translateItem("WOOL:14", MinecraftVersion.of(1, 8)));
        assertEquals("DOUBLE_PLANT", names.translateItem("SUNFLOWER", null));
        assertEquals("RAW_FISH:3", names.translateItem("PUFFERFISH", null));
        assertEquals("BARRIER", names.translateItem("BARRIER", null));
        assertNull("1.9 item on 1.8", names.translateItem("CHORUS_FLOWER", null));
        assertNull("1.17 item on 1.8", names.translateItem("AMETHYST_SHARD", null));
        assertNull(names.translateItem("NOT_AN_ITEM", null));
    }

    @Test
    public void itemsAreTranslatedForModernServers() {
        Names names = TestServer.of("1.21.4").names();
        assertEquals("GRAY_STAINED_GLASS_PANE", names.translateItem("STAINED_GLASS_PANE:7", null));
        assertEquals("RED_DYE", names.translateItem("INK_SACK:1", null));
        assertEquals("RED_DYE", names.translateItem("ROSE_RED", V1_13));
        assertEquals("RED_WOOL", names.translateItem("LEGACY_WOOL:14", null));
        assertEquals("SUNFLOWER", names.translateItem("DOUBLE_PLANT", null));
        assertEquals("GRAY_STAINED_GLASS_PANE", names.translateItem("gray stained glass pane", null));
        // GRASS meant the grass block before 1.13
        assertEquals("GRASS_BLOCK", names.translateItem("GRASS", MinecraftVersion.of(1, 12)));
    }

    @Test
    public void itemsKeepTheirDataValues() {
        Names.ItemName item = TestServer.of("1.12.2").names().item("LIGHT_BLUE_WOOL", null);
        assertNotNull(item);
        assertEquals("WOOL", item.name());
        assertEquals(3, item.data());
    }

    @Test
    public void soundsAreTranslated() {
        Names legacy = TestServer.of("1.8.8").names();
        assertEquals("LEVEL_UP", legacy.translateSound("ENTITY_PLAYER_LEVELUP"));
        assertEquals("LEVEL_UP", legacy.translateSound("entity.player.levelup"));
        assertEquals("NOTE_PLING", legacy.translateSound("BLOCK_NOTE_BLOCK_PLING"));
        assertNull("1.17 sound on 1.8", legacy.sound("BLOCK_AMETHYST_BLOCK_CHIME"));

        Names modern = TestServer.of("1.21.4").names();
        assertEquals("ENTITY_PLAYER_LEVELUP", modern.translateSound("LEVEL_UP"));
        assertEquals("BLOCK_NOTE_BLOCK_PLING", modern.translateSound("NOTE_PLING"));
        assertEquals("BLOCK_NOTE_BLOCK_PLING", modern.translateSound("BLOCK_NOTE_PLING"));

        Names v12 = TestServer.of("1.12.2").names();
        assertEquals("BLOCK_NOTE_PLING", v12.translateSound("BLOCK_NOTE_BLOCK_PLING"));
        assertNull("vanilla sound the version does not have", v12.sound("block.amethyst_block.chime"));
    }

    @Test
    public void customSoundsArePlayedByKey() {
        Names.SoundName custom = TestServer.of("1.21.4").names().sound("mypack:effects.boom");
        assertNotNull(custom);
        assertFalse(custom.isConstant());
        assertEquals("mypack:effects.boom", custom.name());
    }

    @Test
    public void particlesAreTranslated() {
        assertEquals("VILLAGER_HAPPY", TestServer.of("1.8.8").names().translateParticle("HAPPY_VILLAGER"));
        assertEquals("happyVillager", TestServer.of("1.7.10").names().translateParticle("HAPPY_VILLAGER"));
        assertEquals("VILLAGER_HAPPY", TestServer.of("1.16.5").names().translateParticle("happy_villager"));
        assertEquals("HAPPY_VILLAGER", TestServer.of("26.3").names().translateParticle("VILLAGER_HAPPY"));
        assertEquals("DUST", TestServer.of("26.3").names().translateParticle("REDSTONE"));
        assertEquals("REDSTONE", TestServer.of("1.20.1").names().translateParticle("DUST"));
        assertNull("1.9 particle on 1.8", TestServer.of("1.8.8").names().particle("DRAGON_BREATH"));
        assertTrue(TestServer.of("1.21.4").names().particle("ITEM").kind() == NameTables.ParticleEntry.Kind.ITEM);
    }
}

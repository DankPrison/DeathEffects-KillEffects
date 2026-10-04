package com.dank420girl.core.effect;

import com.dank420girl.core.config.Problems;
import com.dank420girl.core.effect.layer.AnimationLayer;
import com.dank420girl.core.effect.layer.Layer;
import com.dank420girl.core.effect.layer.ParticleLayer;
import com.dank420girl.core.testing.TestServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EffectRegistryTest {
    private static EffectRegistry load(TestServer server, String yaml, Problems problems) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return EffectRegistry.load(config, server.parser(server.version()), TestServer.DEATH_EFFECTS, problems);
    }

    @Test
    public void readsEffectsAndOptions() throws Exception {
        TestServer server = TestServer.of("1.21.4");
        Problems problems = new Problems("effects.yml");
        EffectRegistry effects = load(server, ""
                + "effects:\n"
                + "  Stars:\n"
                + "    name: '&eStars'\n"
                + "    icon: [NOT_AN_ITEM, NETHER_STAR]\n"
                + "    permission: ''\n"
                + "    order: 2\n"
                + "    layers:\n"
                + "      - type: particle\n"
                + "        particle: [NOT_A_PARTICLE, FIREWORK]\n"
                + "        count: 40\n"
                + "      - type: animation\n"
                + "        shape: ring\n"
                + "        particle: DUST\n"
                + "        color: '#FFD700'\n"
                + "  first:\n"
                + "    order: 1\n"
                + "    icon: STAINED_GLASS_PANE:3\n"
                + "    layers:\n"
                + "      - type: sound\n"
                + "        sound: entity.player.levelup\n"
                + "  hidden:\n"
                + "    enabled: false\n", problems);
        assertEquals(problems.messages().toString(), 0, problems.size());
        assertEquals(2, effects.size());
        assertEquals("first", effects.ids().get(0));
        EffectDefinition stars = effects.get("STARS");
        assertNotNull(stars);
        assertEquals("§eStars", stars.name());
        assertEquals("NETHER_STAR", stars.icon().name());
        assertNull(stars.permission());
        assertTrue(stars.layers().get(0) instanceof ParticleLayer);
        assertEquals("FIREWORK", ((ParticleLayer) stars.layers().get(0)).particles().get(0).particle().name());
        assertTrue(stars.layers().get(1) instanceof AnimationLayer);
        assertEquals("LIGHT_BLUE_STAINED_GLASS_PANE", effects.get("first").icon().name());
        assertEquals("deatheffects.first", effects.get("first").permission());
        assertNull(effects.get("hidden"));
    }

    @Test
    public void reportsMistakes() throws Exception {
        TestServer server = TestServer.of("1.8.8");
        Problems problems = new Problems("effects.yml");
        EffectRegistry effects = load(server, ""
                + "effects:\n"
                + "  clear:\n"
                + "    layers: []\n"
                + "  'bad id':\n"
                + "    layers: []\n"
                + "  typo:\n"
                + "    icon: DIAMOND\n"
                + "    layers:\n"
                + "      - type: particle\n"
                + "        particle: FLAME\n"
                + "        cuont: 4\n"
                + "      - type: particles\n"
                + "        particle: FLAMES\n"
                + "      - type: sparkle\n"
                + "      - type: sound\n"
                + "        sound: BLOCK_AMETHYST_BLOCK_CHIME\n"
                + "      - type: animation\n"
                + "        shape: STAR\n"
                + "      - type: particle\n"
                + "        particle: DUST\n"
                + "        count: 99999\n"
                + "        color: notacolour\n", problems);
        String all = problems.messages().toString();
        assertTrue(all, all.contains("effects.clear: 'clear' is the name of a command or permission"));
        assertTrue(all, all.contains("effects.bad id: effect ids may only contain"));
        assertTrue(all, all.contains("effects.typo.layers[0]: unknown option 'cuont'"));
        assertTrue(all, all.contains("effects.typo.layers[1]: 'FLAMES' is not a particle"));
        assertTrue(all, all.contains("effects.typo.layers[2]: unknown layer type 'sparkle'"));
        assertTrue(all, all.contains("effects.typo.layers[3]: 'BLOCK_AMETHYST_BLOCK_CHIME' is not a sound on this Minecraft version (1.8.8)"));
        assertTrue(all, all.contains("effects.typo.layers[4]: 'shape' should be one of"));
        assertTrue(all, all.contains("effects.typo.layers[5]: 'count' must be between 0 and 5000, using 5000"));
        assertTrue(all, all.contains("effects.typo.layers[5]: 'color': 'notacolour' is not a colour"));
        assertEquals(1, effects.size());
        int layers = 0;
        for (Layer layer : effects.get("typo").layers()) {
            layers++;
        }
        assertEquals(2, layers);
    }
}

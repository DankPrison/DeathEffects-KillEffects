package com.dank420girl.core.text;

import com.dank420girl.core.platform.MinecraftVersion;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessagesTest {
    private Messages messages;

    @Before
    public void setUp() throws Exception {
        MinecraftVersion.setCurrentForTesting(MinecraftVersion.of(1, 21, 4));
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.loadFromString("prefix: '&b[P]&r '\nhello: '{prefix}&aHi {player}!'\nlist:\n  - one\n  - 'two {n}'\n"
                + "silent: ''\nhelp:\n  a: '&eA {command}'\n  b:\n    - b1\n    - b2\n");
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("hello: '{prefix}&#FF0000Hey {player}'\n");
        messages = new Messages(config, defaults, null);
    }

    @Test
    public void fillsInPlaceholdersAndColours() {
        assertEquals("§b[P]§r §x§f§f§0§0§0§0Hey Steve", messages.get("hello", "player", "Steve"));
        assertEquals(Arrays.asList("one", "two 2"), messages.lines("list", "n", 2));
        assertEquals("§eA de", messages.get("help.a", "command", "de"));
        assertEquals(Arrays.asList("b1", "b2"), messages.lines("help.b"));
    }

    @Test
    public void emptyMessagesAreOff() {
        assertFalse(messages.isEnabled("silent"));
        assertEquals(Collections.emptyList(), messages.lines("silent"));
        assertTrue(messages.isEnabled("hello"));
    }

    @Test
    public void legacyServersGetTheNearestColour() {
        Colors.setHexSupportedForTesting(false);
        try {
            // the nearest of the 16 old colours: dark red for pure red
            assertEquals("§b[P]§r §4Hey Steve", messages.get("hello", "player", "Steve"));
        } finally {
            Colors.setHexSupportedForTesting(null);
        }
    }
}

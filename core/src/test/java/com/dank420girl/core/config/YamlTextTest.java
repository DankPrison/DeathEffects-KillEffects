package com.dank420girl.core.config;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class YamlTextTest {
    private static final String FILE = ""
            + "# A comment at the top\n"
            + "\n"
            + "menu:\n"
            + "  # The title\n"
            + "  title: '&0Effects' # trailing comment\n"
            + "  filler:\n"
            + "    item: 'STAINED_GLASS_PANE:7'\n"
            + "  sounds: [CLICK, 'ORB_PICKUP']\n"
            + "effects:\n"
            + "  flame:\n"
            + "    layers:\n"
            + "      - type: particle\n"
            + "        particle: FLAME\n"
            + "        items:\n"
            + "          - 'WOOL:14'\n"
            + "          - DIAMOND\n"
            + "      - type: sound\n"
            + "        sound: [ITEM_FIRECHARGE_USE, FIRE_IGNITE]\n"
            + "    lore: |\n"
            + "      particle: NOT_A_KEY\n"
            + "    name: \"&eFlame\"\n";

    @Test
    public void readsValuesByPath() {
        YamlText yaml = new YamlText(FILE);
        assertEquals("&0Effects", yaml.get("menu.title"));
        assertEquals("STAINED_GLASS_PANE:7", yaml.get("menu.filler.item"));
        assertEquals("FLAME", yaml.get("effects.flame.layers[0].particle"));
        assertEquals("WOOL:14", yaml.get("effects.flame.layers[0].items[0]"));
        assertEquals("DIAMOND", yaml.get("effects.flame.layers[0].items[1]"));
        assertEquals("&eFlame", yaml.get("effects.flame.name"));
        assertNull(yaml.get("menu.nothing"));
    }

    @Test
    public void setKeepsCommentsAndLayout() {
        YamlText yaml = new YamlText(FILE);
        assertTrue(yaml.set("menu.title", "&cIt's new"));
        String text = yaml.toString();
        assertTrue(text.contains("  title: '&cIt''s new' # trailing comment\n"));
        assertTrue(text.startsWith("# A comment at the top\n\nmenu:\n  # The title\n"));
        assertFalse(yaml.set("menu.missing", "x"));
        assertTrue(yaml.set("effects.flame.layers[1].sound", 5));
        assertTrue(yaml.toString().contains("        sound: 5\n"));
    }

    @Test
    public void setNameQuotesOnlyWhenNeeded() {
        YamlText yaml = new YamlText("icon: HOPPER\nitem: 'WOOL:14'\n");
        yaml.setName("icon", "DIAMOND_BLOCK");
        yaml.setName("item", "RED_WOOL");
        assertEquals("icon: DIAMOND_BLOCK\nitem: RED_WOOL\n", yaml.toString());
        yaml.setName("icon", "STAINED_GLASS:3");
        assertEquals("icon: 'STAINED_GLASS:3'\nitem: RED_WOOL\n", yaml.toString());
    }

    @Test
    public void transformVisitsScalarsAndListItems() {
        YamlText yaml = new YamlText(FILE);
        int changes = yaml.transform(new YamlText.ValueVisitor() {
            @Override
            public String visit(String path, String key, String value) {
                if (value.equals("WOOL:14")) {
                    return "RED_WOOL";
                }
                if (value.equals("FIRE_IGNITE")) {
                    return "ITEM_FLINTANDSTEEL_USE";
                }
                if (value.equals("ORB_PICKUP")) {
                    return "ENTITY_EXPERIENCE_ORB_PICKUP";
                }
                return null;
            }
        });
        assertEquals(3, changes);
        String text = yaml.toString();
        // quotes that only the old value needed are dropped, others are kept
        assertTrue(text, text.contains("          - RED_WOOL\n"));
        assertTrue(text, text.contains("  sounds: [CLICK, 'ENTITY_EXPERIENCE_ORB_PICKUP']\n"));
        assertTrue(text, text.contains("        sound: [ITEM_FIRECHARGE_USE, ITEM_FLINTANDSTEEL_USE]\n"));
        // block scalars are text, not keys
        assertTrue(text.contains("      particle: NOT_A_KEY\n"));
    }

    @Test
    public void listVisitorCanReplaceWholeLists() {
        YamlText yaml = new YamlText(FILE);
        yaml.transform(new YamlText.ListVisitor() {
            @Override
            public String visit(String path, String key, String value) {
                return null;
            }

            @Override
            public String visitList(String path, String key, List<String> values) {
                if (path.equals("effects.flame.layers[1].sound")) {
                    assertEquals(Arrays.asList("ITEM_FIRECHARGE_USE", "FIRE_IGNITE"), values);
                    return values.get(0);
                }
                return null;
            }
        });
        assertTrue(yaml.toString().contains("        sound: ITEM_FIRECHARGE_USE\n"));
        assertTrue(yaml.set("menu.sounds", "CLICK"));
        assertTrue(yaml.toString().contains("  sounds: 'CLICK'\n"));
    }

    @Test
    public void setTopLevelAddsKeyAfterHeader() {
        YamlText yaml = new YamlText("# Header\n# more\n\n# About menu\nmenu:\n  title: x\n");
        yaml.setTopLevel("names-version", "1.8.8", "Written for");
        assertEquals("# Header\n# more\n\n# Written for\nnames-version: '1.8.8'\n\n# About menu\nmenu:\n  title: x\n", yaml.toString());
        yaml.setTopLevel("names-version", "26.3", "Written for");
        assertEquals("26.3", yaml.get("names-version"));
    }

    @Test
    public void setTopLevelRespectsDocumentMarker() {
        YamlText yaml = new YamlText("# Header\n---\nmenu:\n  title: x\n");
        yaml.setTopLevel("names-version", "1.8.8", null);
        assertEquals("# Header\n---\nnames-version: '1.8.8'\n\nmenu:\n  title: x\n", yaml.toString());
    }

    @Test
    public void visitorsCanTurnScalarsIntoLists() {
        YamlText yaml = new YamlText("sound: NEW_SOUND # comment\nitems:\n  - A\n");
        yaml.transform(new YamlText.ValueVisitor() {
            @Override
            public String visit(String path, String key, String value) {
                return YamlText.listValue(Arrays.asList(value, "OLD:1"));
            }
        });
        assertEquals("sound: [NEW_SOUND, 'OLD:1'] # comment\nitems:\n  - A\n", yaml.toString());
    }

    @Test
    public void keepsWindowsLineEndings() {
        YamlText yaml = new YamlText("a: 1\r\nb: 'x'\r\n");
        yaml.set("b", "y");
        assertEquals("a: 1\r\nb: 'y'\r\n", yaml.toString());
    }

    @Test
    public void colonsInsideValuesAreNotKeys() {
        assertEquals(4, YamlText.keyColon("item: 'WOOL:14'"));
        assertEquals(-1, YamlText.keyColon("WOOL:14"));
        assertEquals(6, YamlText.keyColon("'a: b': c"));
    }
}

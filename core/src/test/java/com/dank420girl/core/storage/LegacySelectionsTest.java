package com.dank420girl.core.storage;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LegacySelectionsTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void handsOutEachSelectionOnce() throws Exception {
        File file = new File(temp.getRoot(), "legacy-selections.yml");
        Map<String, String> imported = new LinkedHashMap<String, String>();
        imported.put("Steve", "vortex");
        imported.put(".BedrockAlex", "flame");
        new LegacySelections(file, Logger.getLogger("test")).addAll(imported);
        assertTrue(file.isFile());

        LegacySelections selections = new LegacySelections(file, Logger.getLogger("test"));
        selections.load();
        assertEquals("flame", selections.take(".bedrockalex"));
        assertNull(selections.take(".bedrockalex"));
        assertEquals("vortex", selections.take("STEVE"));
        assertTrue(selections.isEmpty());
        assertFalse("the file is deleted once empty", file.exists());
    }
}

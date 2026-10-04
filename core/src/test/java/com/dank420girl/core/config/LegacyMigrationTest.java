package com.dank420girl.core.config;

import com.dank420girl.core.Variant;
import com.dank420girl.core.storage.LegacySelections;
import com.dank420girl.core.testing.TestServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Converting the config.yml of the old versions, which also held the saved selections. */
public class LegacyMigrationTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    /** The default config.yml of DeathEffects 0.11 (saved with Windows line endings). */
    private String oldConfig() throws Exception {
        return ConfigFiles.readResource("legacy/deatheffects-0.11.yml");
    }

    private File migrate(String version, String config) throws Exception {
        return migrate(version, config, Variant.DEATH_EFFECTS);
    }

    private File migrate(String version, String config, Variant variant) throws Exception {
        TestServer server = TestServer.of(version);
        File folder = temp.newFolder();
        ConfigFiles.write(new File(folder, "config.yml"), config);
        ConfigFiles files = server.files(folder, variant);
        LegacySelections selections = new LegacySelections(new File(folder, "legacy-selections.yml"), Logger.getLogger("test"));
        new LegacyMigration(variant, Logger.getLogger("test"), files, server.names(), selections).migrateIfNeeded();
        return folder;
    }

    @Test
    public void unchangedOldConfigGetsTheNewDefaults() throws Exception {
        File folder = migrate("1.21.4", oldConfig());
        assertTrue(new File(folder, "config-v1-backup.yml").isFile());
        TestServer server = TestServer.of("1.21.4");
        String fresh = server.files(temp.newFolder(), TestServer.DEATH_EFFECTS).render(ConfigFiles.CONFIG_TEMPLATE, true);
        // Nothing was customised, so the new files are the defaults.
        assertEquals(fresh, ConfigFiles.read(new File(folder, "config.yml")));
        assertTrue(new File(folder, "messages.yml").isFile());
        assertTrue(new File(folder, "effects.yml").isFile());
        assertFalse("example entry is not imported", new File(folder, "legacy-selections.yml").isFile());
    }

    @Test
    public void customisedValuesAreCarriedOver() throws Exception {
        String old = oldConfig().replace("\r\n", "\n")
                .replace("Prefix: '&b(DeathEffects)&r '", "Prefix: '&4[Deaths]&r '")
                .replace("Name: '&0&nDeathEffects'", "Name: '&5My Effects'")
                .replace("Flame: \"&eFlame &aEffect\"", "Flame: \"&6Hot &cFlame\"")
                .replace("ValentinesDay: \"&dValentine's Day &aEffect\"", "ValentinesDay: \"&dLove\"")
                .replace("   Vortex: 'HOPPER'", "   Vortex: 'DIAMOND_BLOCK'")
                .replace("FillerItem: 'GRAY_STAINED_GLASS_PANE'", "FillerItem: 'BLACK_STAINED_GLASS_PANE'")
                .replace("SelectSound: 'BLOCK_NOTE_BLOCK_PLING'", "SelectSound: 'ENTITY_PLAYER_LEVELUP'")
                .replace("SelectSoundPitch: 2", "SelectSoundPitch: 1.5")
                .replace("NoIconSoundEnabled: true", "NoIconSoundEnabled: false")
                .replace("LoginMessageEnabled: true", "LoginMessageEnabled: false")
                .replace("NoPermission: \"&cYou dont have permission for this effect.\"", "NoPermission: \"&cNope!\"")
                .replace("   enabled: false\n   username: 'examplename'", "   enabled: true\n   username: 'steve'")
                .replace("   examplePlayer: \"example\"", "   examplePlayer: \"example\"\n   Steve: \"Vortex\"\n   Alex: \"%&#@$\"");
        File folder = migrate("1.21.4", old);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(folder, "config.yml"));
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(new File(folder, "messages.yml"));
        YamlConfiguration effects = YamlConfiguration.loadConfiguration(new File(folder, "effects.yml"));
        assertEquals(2, config.getInt("config-version"));
        assertEquals("&5My Effects", config.getString("menu.title"));
        assertEquals("BLACK_STAINED_GLASS_PANE", config.getString("menu.filler.item"));
        assertEquals("ENTITY_PLAYER_LEVELUP", config.getString("menu.sounds.select.sound"));
        assertEquals(1.5, config.getDouble("menu.sounds.select.pitch"), 0.001);
        assertEquals("", config.getString("menu.sounds.locked.sound"));
        assertEquals("MYSQL", config.getString("storage.type"));
        assertEquals("steve", config.getString("storage.mysql.username"));
        assertEquals("examplehost.net", config.getString("storage.mysql.host"));
        assertEquals(3306, config.getInt("storage.mysql.port"));
        assertEquals("&4[Deaths]&r ", messages.getString("prefix"));
        assertEquals("&cNope!", messages.getString("no-permission-effect"));
        assertEquals("", messages.getString("effect-restored"));
        assertEquals("&6Hot &cFlame", effects.getString("effects.flame.name"));
        assertEquals("&dLove", effects.getString("effects.valentinesday.name"));
        assertEquals("DIAMOND_BLOCK", effects.getString("effects.vortex.icon"));
        assertEquals("BLAZE_POWDER", effects.getString("effects.flame.icon"));
        // the comments of the new files are there
        assertTrue(ConfigFiles.read(new File(folder, "config.yml")).contains("# When death effects play."));

        LegacySelections selections = new LegacySelections(new File(folder, "legacy-selections.yml"), Logger.getLogger("test"));
        selections.load();
        assertEquals("vortex", selections.take("steve"));
        assertNull("cleared selections are not imported", selections.take("Alex"));
        assertTrue(selections.isEmpty());
    }

    @Test
    public void oldNamesAreTranslatedForOldServers() throws Exception {
        String old = oldConfig().replace("   Vortex: 'HOPPER'", "   Vortex: 'LIGHT_BLUE_WOOL'")
                .replace("SelectSound: 'BLOCK_NOTE_BLOCK_PLING'", "SelectSound: 'ENTITY_PLAYER_LEVELUP'");
        File folder = migrate("1.8.8", old);
        YamlConfiguration effects = YamlConfiguration.loadConfiguration(new File(folder, "effects.yml"));
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(folder, "config.yml"));
        assertEquals("WOOL:3", effects.getString("effects.vortex.icon"));
        assertEquals("LEVEL_UP", config.getString("menu.sounds.select.sound"));
    }

    @Test
    public void newConfigurationsAreLeftAlone() throws Exception {
        TestServer server = TestServer.of("1.21.4");
        String fresh = server.files(temp.newFolder(), TestServer.DEATH_EFFECTS).render(ConfigFiles.CONFIG_TEMPLATE, true);
        File folder = migrate("1.21.4", fresh);
        assertFalse(new File(folder, "config-v1-backup.yml").exists());
        assertEquals(fresh, ConfigFiles.read(new File(folder, "config.yml")));
    }

    @Test
    public void unchangedKillEffectsConfigGetsTheNewDefaults() throws Exception {
        // KillEffects 0.12 has the same file, with KillEffects in the defaults instead of DeathEffects.
        File folder = migrate("1.21.4", ConfigFiles.readResource("legacy/killeffects-0.12.yml"), Variant.KILL_EFFECTS);
        assertTrue(new File(folder, "config-v1-backup.yml").isFile());
        ConfigFiles fresh = TestServer.of("1.21.4").files(temp.newFolder(), Variant.KILL_EFFECTS);
        assertEquals(fresh.render(ConfigFiles.CONFIG_TEMPLATE, true), ConfigFiles.read(new File(folder, "config.yml")));
        assertEquals(fresh.render(ConfigFiles.MESSAGES_TEMPLATE, false), ConfigFiles.read(new File(folder, "messages.yml")));
        assertEquals(fresh.render(ConfigFiles.EFFECTS_TEMPLATE, true), ConfigFiles.read(new File(folder, "effects.yml")));
    }
}

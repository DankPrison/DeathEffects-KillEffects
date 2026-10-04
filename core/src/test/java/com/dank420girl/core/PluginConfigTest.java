package com.dank420girl.core;

import com.dank420girl.core.config.ConfigFiles;
import com.dank420girl.core.config.Problems;
import com.dank420girl.core.config.Settings;
import com.dank420girl.core.config.StorageSettings;
import com.dank420girl.core.testing.TestServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The config.yml each plugin gets from the shared template. */
public class PluginConfigTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    /** Options only one of the plugins has. */
    private static List<String> ownKeys(Variant variant) {
        return variant == Variant.DEATH_EFFECTS
                ? Arrays.asList("triggers.killed-by-player", "triggers.other-deaths", "compatibility.skip-when-kill-effect-plays")
                : Arrays.asList("triggers.players", "triggers.mobs", "triggers.self", "triggers.ignored-mobs",
                "compatibility.skip-when-death-effect-plays");
    }

    @Test
    public void configWorksOnEveryVersion() throws Exception {
        for (Variant variant : Variant.all()) {
            for (String version : TestServer.VERSIONS) {
                String what = variant + " on " + version;
                TestServer server = TestServer.of(version);
                File folder = temp.newFolder(variant.id() + "-" + version);
                ConfigFiles.Loaded loaded = server.files(folder, variant).load("config.yml", ConfigFiles.CONFIG_TEMPLATE, true, true);
                Problems problems = new Problems("config.yml");
                Settings settings = new Settings(loaded.config(), server.parser(loaded.namesVersion()), variant.defaultTable(), problems);
                variant.createTrigger(null).readSettings(loaded.config(), problems);
                assertTrue(what + ": " + problems.messages(), problems.isEmpty());
                assertEquals(what, 36, settings.menu().effectSlots().length);
                assertNotNull(what, settings.menu().soundSelect());
                assertNotNull(what, settings.menu().soundLocked());
                assertNotNull(what, settings.menu().soundPage());
                assertNotNull(what, settings.menu().soundClear());
                assertNotNull(what, settings.menu().soundPreview());
                assertTrue(what, server.hasMaterial(settings.menu().clear().item().name()));
                assertTrue(what, server.hasMaterial(settings.menu().filler().item().name()));
                assertEquals(variant.id() + "_players", settings.storage().table());
                YamlConfiguration file = YamlConfiguration.loadConfiguration(new File(folder, "config.yml"));
                for (String key : ownKeys(variant)) {
                    assertTrue(what + " has " + key, file.contains(key));
                }
                for (String key : ownKeys(variant.other())) {
                    assertFalse(what + " does not have " + key, file.contains(key));
                }
                assertEquals(2, file.getInt("config-version"));
            }
        }
    }

    @Test
    public void everyTokenIsFilledIn() throws Exception {
        Pattern leftover = Pattern.compile("@[A-Z_]+@|@INCLUDE");
        for (Variant variant : Variant.all()) {
            ConfigFiles files = TestServer.of("1.21.4").files(temp.newFolder(), variant);
            for (String template : Arrays.asList(ConfigFiles.CONFIG_TEMPLATE, ConfigFiles.MESSAGES_TEMPLATE, ConfigFiles.EFFECTS_TEMPLATE)) {
                String text = files.render(template, !template.equals(ConfigFiles.MESSAGES_TEMPLATE));
                Matcher matcher = leftover.matcher(text);
                assertFalse(variant + " " + template + ": " + (matcher.find() ? matcher.group() : ""), leftover.matcher(text).find());
                assertFalse(variant + " " + template + " mentions only itself", text.contains("/" + variant.other().alias() + " "));
            }
            String config = files.render(ConfigFiles.CONFIG_TEMPLATE, true);
            assertTrue(config.contains("#  " + variant.name() + " - configuration"));
            assertTrue(config.contains("# " + variant.description()));
            assertTrue(config.contains("Only matters when " + variant.other().name() + " runs on the same server."));
            assertTrue(config.contains("table: " + variant.id() + "_players"));
        }
    }

    @Test
    public void misspelledOptionsAreReported() throws Exception {
        for (Variant variant : Variant.all()) {
            TestServer server = TestServer.of("1.21.4");
            File folder = temp.newFolder();
            ConfigFiles files = server.files(folder, variant);
            String text = files.render(ConfigFiles.CONFIG_TEMPLATE, true);
            String ownTrigger = variant == Variant.DEATH_EFFECTS ? "  other-deaths: true\n" : "  self: false\n";
            String[][] typos = {
                    {"  require-permission: true\n", "  require-permision: false\n"},
                    {"  cooldown: 5\n", "  cooldwn: 1\n"},
                    {"  rows: 6\n", "  rowz: 3\n"},
                    {"      slot: 4\n", "      slott: 4\n"},
                    {"  type: YAML\n", "  typ: MYSQL\n"},
                    {"    port: 3306\n", "    prot: 3306\n"},
                    {"    select:\n      sound:", "    selct:\n      sound:"},
                    {"  filler:\n    enabled: true\n    item: GRAY_STAINED_GLASS_PANE\n    name: ' '\n", "  filler: GRAY_STAINED_GLASS_PANE\n"},
                    {ownTrigger, ownTrigger.replace(":", "s:")},
            };
            for (String[] typo : typos) {
                assertTrue(variant + " config.yml contains " + typo[0].trim(), text.contains(typo[0]));
                text = text.replace(typo[0], typo[1]);
            }
            ConfigFiles.write(new File(folder, "config.yml"), text + "setings:\n  debug: true\nmenu-title: x\n");
            ConfigFiles.Loaded loaded = files.load("config.yml", ConfigFiles.CONFIG_TEMPLATE, true, true);
            Problems problems = new Problems("config.yml");
            Settings settings = new Settings(loaded.config(), server.parser(loaded.namesVersion()), variant.defaultTable(), problems);
            variant.createTrigger(null).readSettings(loaded.config(), problems);
            List<String> expected = Arrays.asList(
                    "unknown option 'setings' (ignored)",
                    "unknown option 'menu-title' (ignored)",
                    "settings: unknown option 'require-permision' (ignored)",
                    "previews: unknown option 'cooldwn' (ignored)",
                    "menu.buttons.clear: unknown option 'slott' (ignored)",
                    "menu: unknown option 'rowz' (ignored)",
                    "menu.sounds: unknown sound 'selct' (ignored)",
                    "storage: unknown option 'typ' (ignored)",
                    "storage.mysql: unknown option 'prot' (ignored)",
                    "menu.filler: should be a section of options, not 'GRAY_STAINED_GLASS_PANE'; using the defaults",
                    "triggers: unknown option '" + ownTrigger.trim().replace(":", "s").split(" ")[0] + "' (ignored)");
            for (String message : expected) {
                assertTrue(variant + " reports \"" + message + "\" in " + problems.messages(), problems.messages().contains(message));
            }
            assertEquals(variant + ": " + problems.messages(), expected.size(), problems.size());
            assertTrue(settings.requirePermission());
            assertEquals(5, settings.previewCooldown());
            assertEquals(6, settings.menu().rows());
            assertEquals(StorageSettings.Type.YAML, settings.storage().type());
            assertTrue("a value instead of a section uses the default section", settings.menu().filler().enabled());
            assertEquals("GRAY_STAINED_GLASS_PANE", settings.menu().filler().item().name());
        }
    }
}

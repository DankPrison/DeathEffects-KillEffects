package com.dank420girl.core.config;

import com.dank420girl.core.effect.EffectDefinition;
import com.dank420girl.core.effect.EffectRegistry;
import com.dank420girl.core.testing.TestServer;
import com.dank420girl.core.text.Messages;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The default effects.yml and messages.yml work on every version without a single warning. */
public class DefaultFilesTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void defaultEffectsLoadOnEveryVersion() throws Exception {
        for (String version : TestServer.VERSIONS) {
            TestServer server = TestServer.of(version);
            File folder = temp.newFolder("effects-" + version);
            ConfigFiles files = server.files(folder, TestServer.DEATH_EFFECTS);
            ConfigFiles.Loaded loaded = files.load("effects.yml", ConfigFiles.EFFECTS_TEMPLATE, true, false);
            Problems problems = new Problems("effects.yml");
            EffectRegistry effects = EffectRegistry.load(loaded.config(), server.parser(loaded.namesVersion()),
                    TestServer.DEATH_EFFECTS, problems);
            assertEquals(version + ": " + problems.messages(), 0, problems.size());
            assertEquals(version, 53, effects.size());
            for (EffectDefinition effect : effects.all()) {
                assertFalse(version + " " + effect.id() + " has layers", effect.layers().isEmpty());
                assertTrue(version + " " + effect.id() + " icon " + effect.icon(), server.hasMaterial(effect.icon().name()));
                assertEquals("deatheffects." + effect.id(), effect.permission());
            }
            assertEquals("flame", effects.ids().get(0));
            assertEquals("valentinesday", effects.ids().get(52));
        }
    }

    @Test
    public void defaultMessagesHaveEveryKey() throws Exception {
        TestServer server = TestServer.of("1.21.4");
        ConfigFiles.Loaded loaded = server.files(temp.newFolder(), TestServer.KILL_EFFECTS)
                .load("messages.yml", ConfigFiles.MESSAGES_TEMPLATE, false, true);
        Messages messages = new Messages(loaded.config(), loaded.config().getDefaults(), null);
        for (String key : Arrays.asList("prefix", "effect-selected", "effect-selected-other", "effect-cleared",
                "effect-cleared-other", "effect-restored", "select-cancelled", "target-lacks-permission",
                "preview-cooldown", "preview-locked", "previews-disabled", "info-self", "info-self-none", "info-other",
                "info-other-none", "info-not-playing", "list-header", "list-entry-selected", "list-entry-available",
                "list-entry-locked", "none", "no-permission", "no-permission-effect", "players-only", "player-not-found",
                "unknown-effect", "unknown-command", "usage", "menu-opened-other", "reloaded", "reload-failed", "version",
                "help-header", "help.open", "help.list", "help.select", "help.preview", "help.clear", "help.info",
                "help.select-other", "help.clear-other", "help.open-other", "help.info-other", "help.reload", "help.version")) {
            assertTrue("missing message " + key, messages.isEnabled(key));
        }
        assertEquals("§b(KillEffects)§r §7Your kill effect is now §cFire§7.",
                messages.get("effect-selected", "effect", "§cFire"));
        assertEquals(5, messages.lines("version").size());
        Problems problems = new Problems("messages.yml");
        Messages.reportUnknown(loaded.config(), loaded.config().getDefaults(), problems);
        assertTrue(problems.messages().toString(), problems.isEmpty());
    }

    @Test
    public void misspelledMessagesAndSectionsAreReported() throws Exception {
        TestServer server = TestServer.of("1.21.4");
        File folder = temp.newFolder();
        ConfigFiles files = server.files(folder, TestServer.DEATH_EFFECTS);
        ConfigFiles.write(new File(folder, "messages.yml"), files.render(ConfigFiles.MESSAGES_TEMPLATE, false)
                + "efect-selected: 'typo'\nhelpp:\n  open: 'typo'\n");
        ConfigFiles.Loaded messages = files.load("messages.yml", ConfigFiles.MESSAGES_TEMPLATE, false, true);
        Problems problems = new Problems("messages.yml");
        Messages.reportUnknown(messages.config(), messages.config().getDefaults(), problems);
        assertEquals(Arrays.asList("unknown message 'efect-selected' (ignored)", "unknown message 'helpp' (ignored)"),
                problems.messages());

        ConfigFiles.write(new File(folder, "effects.yml"), "effect:\n  flame:\n    name: Flame\n");
        ConfigFiles.Loaded effectsFile = files.load("effects.yml", ConfigFiles.EFFECTS_TEMPLATE, true, false);
        problems = new Problems("effects.yml");
        EffectRegistry.load(effectsFile.config(), server.parser(effectsFile.namesVersion()), TestServer.DEATH_EFFECTS, problems);
        assertTrue(problems.messages().toString(), problems.messages().contains(
                "unknown option 'effect' (ignored; effects belong under 'effects:')"));
    }

    @Test
    public void namesFollowTheServerWhenItMovesToAnotherVersion() throws Exception {
        File folder = temp.newFolder();
        // created on 1.8.8 ...
        TestServer old = TestServer.of("1.8.8");
        old.files(folder, TestServer.DEATH_EFFECTS).load("effects.yml", ConfigFiles.EFFECTS_TEMPLATE, true, false);
        String legacy = ConfigFiles.read(new File(folder, "effects.yml"));
        assertTrue(legacy.contains("names-version: '1.8.8'"));
        assertTrue(legacy.contains("icon: 'INK_SACK:1'"));
        assertTrue(legacy.contains("particle: VILLAGER_HAPPY"));
        assertTrue(legacy.contains("sound: [ITEM_FIRECHARGE_USE, FIRE_IGNITE]"));

        // ... then the server is updated
        TestServer modern = TestServer.of("26.3");
        ConfigFiles.Loaded loaded = modern.files(folder, TestServer.DEATH_EFFECTS)
                .load("effects.yml", ConfigFiles.EFFECTS_TEMPLATE, true, false);
        String updated = ConfigFiles.read(new File(folder, "effects.yml"));
        assertTrue(updated.contains("names-version: '26.3'"));
        assertTrue(updated.contains("icon: RED_DYE"));
        assertTrue(updated.contains("particle: HAPPY_VILLAGER"));
        assertTrue(updated.contains("items: [RED_WOOL, ORANGE_WOOL"));
        assertTrue(updated.contains("sound: [ITEM_FIRECHARGE_USE, ITEM_FLINTANDSTEEL_USE]"));
        assertTrue("comments are kept", updated.contains("# Every layer type and option is explained in the reference"));
        assertTrue("old file kept", new File(folder, "backups/effects-1.8.8.yml").isFile());
        Problems problems = new Problems("effects.yml");
        EffectRegistry.load(loaded.config(), modern.parser(loaded.namesVersion()), TestServer.DEATH_EFFECTS, problems);
        assertEquals(problems.messages().toString(), 0, problems.size());

        // ... and back again
        TestServer back = TestServer.of("1.8.8");
        ConfigFiles.Loaded again = back.files(folder, TestServer.DEATH_EFFECTS)
                .load("effects.yml", ConfigFiles.EFFECTS_TEMPLATE, true, false);
        String downgraded = ConfigFiles.read(new File(folder, "effects.yml"));
        assertTrue(downgraded.contains("icon: 'INK_SACK:1'"));
        problems = new Problems("effects.yml");
        EffectRegistry.load(again.config(), back.parser(again.namesVersion()), TestServer.DEATH_EFFECTS, problems);
        assertEquals(problems.messages().toString(), 0, problems.size());
    }

    @Test
    public void movingToAnOlderVersionAndBackKeepsTheNewerNames() throws Exception {
        File folder = temp.newFolder();
        TestServer.of("1.21.4").files(folder, TestServer.DEATH_EFFECTS).load("effects.yml", ConfigFiles.EFFECTS_TEMPLATE, true, false);
        String original = ConfigFiles.read(new File(folder, "effects.yml"));
        assertTrue(original.contains("sound: BLOCK_AMETHYST_BLOCK_CHIME\n"));

        TestServer old = TestServer.of("1.16.5");
        ConfigFiles.Loaded loaded = old.files(folder, TestServer.DEATH_EFFECTS).load("effects.yml", ConfigFiles.EFFECTS_TEMPLATE, true, false);
        String downgraded = ConfigFiles.read(new File(folder, "effects.yml"));
        assertTrue(downgraded, downgraded.contains("sound: [BLOCK_AMETHYST_BLOCK_CHIME, BLOCK_NOTE_BLOCK_CHIME]\n"));
        assertTrue(downgraded, downgraded.contains("particle: [SNOWFLAKE, FIREWORKS_SPARK]\n"));
        Problems problems = new Problems("effects.yml");
        EffectRegistry.load(loaded.config(), old.parser(loaded.namesVersion()), TestServer.DEATH_EFFECTS, problems);
        assertEquals(problems.messages().toString(), 0, problems.size());

        TestServer.of("1.21.4").files(folder, TestServer.DEATH_EFFECTS).load("effects.yml", ConfigFiles.EFFECTS_TEMPLATE, true, false);
        String upgraded = ConfigFiles.read(new File(folder, "effects.yml"));
        assertTrue(upgraded.contains("sound: [BLOCK_AMETHYST_BLOCK_CHIME, BLOCK_NOTE_BLOCK_CHIME]\n"));
        assertTrue(upgraded.contains("particle: [SNOWFLAKE, FIREWORK]\n"));
    }

    @Test
    public void standInNamesAreNotWrittenIntoFiles() throws Exception {
        File folder = temp.newFolder();
        ConfigFiles.write(new File(folder, "effects.yml"), "names-version: '26.3'\neffects:\n  a:\n    layers:\n"
                + "      - type: sound\n        sound: ENTITY_SLIME_SQUISH\n      - type: particle\n        particle: FISHING\n");
        TestServer v17 = TestServer.of("1.7.10");
        ConfigFiles.Loaded loaded = v17.files(folder, TestServer.DEATH_EFFECTS).load("effects.yml", ConfigFiles.EFFECTS_TEMPLATE, true, false);
        String text = ConfigFiles.read(new File(folder, "effects.yml"));
        // 1.7 only has similar sounds and particles: the names stay, and still work there
        assertTrue(text.contains("sound: ENTITY_SLIME_SQUISH\n"));
        assertTrue(text.contains("particle: FISHING\n"));
        Problems problems = new Problems("effects.yml");
        EffectRegistry.load(loaded.config(), v17.parser(loaded.namesVersion()), TestServer.DEATH_EFFECTS, problems);
        assertEquals(problems.messages().toString(), 0, problems.size());
    }

    @Test
    public void brokenFilesAreNotRewritten() throws Exception {
        File folder = temp.newFolder();
        String broken = "names-version: '1.8.8'\neffects:\n  flame:\n    icon: 'WOOL:14'\n    layers: [\n";
        ConfigFiles.write(new File(folder, "effects.yml"), broken);
        try {
            TestServer.of("26.3").files(folder, TestServer.DEATH_EFFECTS).load("effects.yml", ConfigFiles.EFFECTS_TEMPLATE, true, false);
            throw new AssertionError("no exception");
        } catch (org.bukkit.configuration.InvalidConfigurationException expected) {
            assertEquals(broken, ConfigFiles.read(new File(folder, "effects.yml")));
            assertFalse(new File(folder, "backups").exists());
        }
    }

    @Test
    public void alternativesCollapseWhenTheFirstNameExists() throws Exception {
        String modern = TestServer.of("26.3").files(temp.newFolder(), TestServer.DEATH_EFFECTS)
                .render(ConfigFiles.EFFECTS_TEMPLATE, true);
        assertTrue(modern.contains("        sound: ITEM_FIRECHARGE_USE\n"));
        assertTrue(modern.contains("        particle: DRAGON_BREATH\n"));
        String old = TestServer.of("1.12.2").files(temp.newFolder(), TestServer.DEATH_EFFECTS)
                .render(ConfigFiles.EFFECTS_TEMPLATE, true);
        assertTrue(old.contains("        particle: [DRAGON_BREATH, PORTAL]\n") || old.contains("        particle: DRAGON_BREATH\n"));
        assertTrue(old.contains("        sound: [ENTITY_WITCH_CELEBRATE, ENTITY_GHAST_SCREAM]\n"));
    }

    @Test
    public void invalidFilesAreReported() throws Exception {
        File folder = temp.newFolder();
        ConfigFiles.write(new File(folder, "effects.yml"), "effects:\n  flame: [broken\n");
        TestServer server = TestServer.of("1.21.4");
        try {
            server.files(folder, TestServer.DEATH_EFFECTS).load("effects.yml", ConfigFiles.EFFECTS_TEMPLATE, true, false);
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            assertTrue(e.getMessage().startsWith("effects.yml is not valid YAML"));
            ConfigFiles.Loaded defaults = server.files(folder, TestServer.DEATH_EFFECTS).loadDefaults(ConfigFiles.EFFECTS_TEMPLATE, true);
            assertNotNull(defaults.config().getConfigurationSection("effects.flame"));
            return;
        }
        throw new AssertionError("no exception");
    }

    @Test
    public void readsFilesSavedByOldVersionsOnWindows() {
        byte[] cp1252 = {'a', ':', ' ', (byte) 0xE9, '\n'};
        assertEquals("a: é\n", ConfigFiles.decode(cp1252));
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a', ':', ' ', '1'};
        assertEquals("a: 1", ConfigFiles.decode(bom));
    }
}

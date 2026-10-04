package com.dank420girl.core.config;

import com.dank420girl.core.effect.EffectDefinition;
import com.dank420girl.core.effect.layer.Layer;
import com.dank420girl.core.selection.SelectionService;
import com.dank420girl.core.storage.Storage;
import com.dank420girl.core.storage.StorageService;
import com.dank420girl.core.storage.StorageUnavailableException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Storage in this test lives in the config package to reach StorageSettings.read. */
public class StorageServiceTest {
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static StorageSettings settings(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        Problems problems = new Problems("config.yml");
        StorageSettings settings = StorageSettings.read(config.getConfigurationSection("storage"), "deatheffects_players", problems);
        assertTrue(problems.messages().toString(), problems.isEmpty());
        return settings;
    }

    private static StorageSettings yaml() throws Exception {
        return settings("storage:\n  type: YAML\n");
    }

    private static StorageSettings mysql() throws Exception {
        return settings("storage:\n  type: MYSQL\n");
    }

    private static StorageService service(File folder) {
        return service(folder, null);
    }

    /** A service whose SQLite and MySQL settings use the fake database. */
    private static StorageService service(final File folder, final FakeDatabase database) {
        return new StorageService("Test", Logger.getLogger("test"), folder, new Predicate<String>() {
            @Override
            public boolean test(String id) {
                return true;
            }
        }, database == null ? null : new Function<StorageSettings, Storage>() {
            @Override
            public Storage apply(StorageSettings settings) {
                return database.connect(folder);
            }
        });
    }

    /**
     * A database that can go down, shared by all its connections like a real one. Like the real database it
     * copies players.yml when it is opened, with cleared selections (an empty effect) as deletions.
     */
    private static final class FakeDatabase {
        final Map<UUID, String> rows = new ConcurrentHashMap<UUID, String>();
        volatile boolean down;
        /** Saves that fail with an ordinary error, such as a lock wait timeout. */
        final AtomicInteger failingSaves = new AtomicInteger();
        /** Loads that fail because the connection is lost again. */
        final AtomicInteger failingLoads = new AtomicInteger();

        Storage connect(final File folder) {
            return new Storage() {
                @Override
                public String describe() {
                    return "FakeDB";
                }

                @Override
                public void open() throws Exception {
                    if (down) {
                        throw new ConnectException("Connection refused");
                    }
                    File players = new File(folder, "players.yml");
                    if (players.isFile()) {
                        ConfigurationSection section = YamlConfiguration.loadConfiguration(players).getConfigurationSection("players");
                        if (section != null) {
                            for (String key : section.getKeys(false)) {
                                save(UUID.fromString(key), section.getString(key + ".name"), section.getString(key + ".effect"));
                            }
                        }
                        assertTrue(players.renameTo(new File(folder, "players.yml.imported")));
                    }
                }

                @Override
                public String load(UUID player) throws Exception {
                    if (down || failingLoads.getAndDecrement() > 0) {
                        throw new StorageUnavailableException("FakeDB cannot be reached", null);
                    }
                    return rows.get(player);
                }

                @Override
                public void save(UUID player, String name, String effect) throws Exception {
                    if (down) {
                        throw new StorageUnavailableException("FakeDB cannot be reached", null);
                    }
                    if (failingSaves.getAndDecrement() > 0) {
                        throw new SQLException("Lock wait timeout exceeded");
                    }
                    if (effect == null || effect.isEmpty()) {
                        rows.remove(player);
                    } else {
                        rows.put(player, effect);
                    }
                }

                @Override
                public void close() {
                }
            };
        }
    }

    private static String load(StorageService service, UUID player) throws Exception {
        final AtomicReference<String> result = new AtomicReference<String>();
        final CountDownLatch done = new CountDownLatch(1);
        service.load(player, new StorageService.LoadCallback() {
            @Override
            public void loaded(String effect, boolean provisional) {
                result.set(effect);
                done.countDown();
            }
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        return result.get();
    }

    private static Map<UUID, String> recordReloads(StorageService service) {
        final Map<UUID, String> reloaded = new ConcurrentHashMap<UUID, String>();
        service.setReloadListener(new BiConsumer<UUID, String>() {
            @Override
            public void accept(UUID player, String effect) {
                reloaded.put(player, effect == null ? "" : effect);
            }
        });
        return reloaded;
    }

    private static Player player(final UUID uuid, final String name) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        switch (method.getName()) {
                            case "getUniqueId":
                                return uuid;
                            case "getName":
                            case "toString":
                                return name;
                            case "isOnline":
                                return true;
                            case "hashCode":
                                return uuid.hashCode();
                            case "equals":
                                return proxy == args[0];
                            default:
                                throw new UnsupportedOperationException(method.getName());
                        }
                    }
                });
    }

    private static boolean loadSelection(SelectionService selections, Player player) throws Exception {
        final AtomicReference<Boolean> provisional = new AtomicReference<Boolean>();
        final CountDownLatch done = new CountDownLatch(1);
        selections.load(player, new StorageService.LoadCallback() {
            @Override
            public void loaded(String effect, boolean fromStandIn) {
                provisional.set(fromStandIn);
                done.countDown();
            }
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        return provisional.get();
    }

    private static EffectDefinition effect(String id) {
        return new EffectDefinition(id, id, null, Collections.<String>emptyList(), null, 0, Collections.<Layer>emptyList());
    }

    private File fallbackFile(File folder) {
        return new File(folder, StorageService.FALLBACK_FILE);
    }

    @Test
    public void savesAndLoadsWithYaml() throws Exception {
        File folder = temp.newFolder();
        StorageService service = service(folder);
        service.open(yaml());
        service.save(STEVE, "Steve", "flame");
        service.save(ALEX, "Alex", "dna");
        service.save(ALEX, "Alex", null);
        assertEquals("flame", load(service, STEVE));
        assertNull(load(service, ALEX));
        service.shutdown();
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(new File(folder, "players.yml"));
        assertEquals("flame", saved.getString("players." + STEVE + ".effect"));
        assertEquals("Steve", saved.getString("players." + STEVE + ".name"));
        assertFalse(saved.contains("players." + ALEX));
    }

    @Test
    public void unreadablePlayersFileIsKeptAside() throws Exception {
        File folder = temp.newFolder();
        String broken = "players:\n  " + STEVE + ":\n    effect: flame\n  oops: [\n";
        ConfigFiles.write(new File(folder, "players.yml"), broken);
        StorageService service = service(folder);
        service.open(yaml());
        service.save(ALEX, "Alex", "dna");
        service.shutdown();
        File[] kept = folder.listFiles((dir, name) -> name.startsWith("players.yml.broken-"));
        assertEquals(1, kept.length);
        assertEquals(broken, ConfigFiles.read(kept[0]));
        assertEquals("dna", YamlConfiguration.loadConfiguration(new File(folder, "players.yml")).getString("players." + ALEX + ".effect"));
    }

    @Test
    public void unreachableDatabaseUsesFallbackAndCopiesItLater() throws Exception {
        File folder = temp.newFolder();
        StorageService service = service(folder);
        // No JDBC driver is available in the tests, so the database cannot be used.
        service.open(mysql());
        service.save(STEVE, "Steve", "flame");
        service.save(ALEX, "Alex", null);
        assertEquals("flame", load(service, STEVE));
        assertTrue(service.describe(), service.describe().startsWith("YAML (players-fallback.yml) - MySQL"));
        Thread.sleep(3500); // the fallback file is written a few seconds after a change
        File fallback = fallbackFile(folder);
        assertTrue(fallback.isFile());
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(fallback);
        assertEquals("flame", saved.getString("players." + STEVE + ".effect"));
        assertEquals("cleared selections are remembered", "", saved.getString("players." + ALEX + ".effect"));

        // The owner switches to a storage that works: what was saved meanwhile moves there.
        service.open(yaml());
        assertEquals("flame", load(service, STEVE));
        assertEquals("YAML (players.yml)", service.describe());
        service.shutdown();
        assertFalse(fallback.exists());
        assertEquals("flame", YamlConfiguration.loadConfiguration(new File(folder, "players.yml")).getString("players." + STEVE + ".effect"));
    }

    @Test
    public void databaseLostWhileRunningUsesFallbackUntilItIsBack() throws Exception {
        File folder = temp.newFolder();
        FakeDatabase database = new FakeDatabase();
        StorageService service = service(folder, database);
        Map<UUID, String> reloaded = recordReloads(service);
        service.open(mysql());
        service.save(STEVE, "Steve", "flame");
        assertEquals("flame", load(service, STEVE));
        assertEquals("FakeDB", service.describe());

        database.down = true;
        service.save(ALEX, "Alex", "dna");
        assertEquals("the change is kept in the fallback file", "dna", load(service, ALEX));
        assertNull(load(service, STEVE));
        assertTrue(service.describe(), service.describe().startsWith("YAML (players-fallback.yml) - FakeDB"));
        assertNull(database.rows.get(ALEX));

        database.down = false;
        service.open(mysql()); // reloads try the database again, like the retry every few minutes
        assertEquals("dna", load(service, ALEX));
        assertEquals("FakeDB", service.describe());
        assertEquals("copied to the database", "dna", database.rows.get(ALEX));
        assertEquals("loaded again from the database", "flame", reloaded.get(STEVE));
        service.shutdown();
        assertFalse(fallbackFile(folder).exists());
    }

    @Test
    public void selectionsLoadedDuringAnOutageAreLoadedAgain() throws Exception {
        File folder = temp.newFolder();
        FakeDatabase database = new FakeDatabase();
        database.rows.put(STEVE, "flame");
        database.rows.put(ALEX, "dna");
        database.down = true;
        StorageService service = service(folder, database);
        service.open(mysql());
        SelectionService selections = new SelectionService(service);
        Player steve = player(STEVE, "Steve");
        Player alex = player(ALEX, "Alex");
        assertTrue("loaded from a stand-in", loadSelection(selections, steve));
        loadSelection(selections, alex);
        assertNull("the database cannot be reached", selections.selectedId(STEVE));
        selections.select(alex, effect("vortex"));

        database.down = false;
        service.open(mysql());
        load(service, STEVE); // waits until the storage thread is done
        assertEquals("flame", selections.selectedId(STEVE));
        assertEquals("a choice made meanwhile is kept", "vortex", selections.selectedId(ALEX));
        assertEquals("vortex", database.rows.get(ALEX));
        assertFalse("loaded from the database", loadSelection(selections, player(UUID.randomUUID(), "Notch")));
        service.shutdown();
    }

    @Test
    public void failedCopyKeepsTheFallbackUntilTheCopyWorks() throws Exception {
        File folder = temp.newFolder();
        FakeDatabase database = new FakeDatabase();
        StorageService service = service(folder, database);
        service.open(mysql());
        database.down = true;
        service.save(STEVE, "Steve", "vortex");
        load(service, STEVE);

        database.down = false;
        database.failingSaves.set(1); // copying the fallback fails once
        service.open(mysql());
        load(service, STEVE);
        assertTrue(service.describe(), service.describe().startsWith("YAML (players-fallback.yml) - FakeDB"));
        service.save(STEVE, "Steve", "flame"); // the newest choice stays in the fallback for now
        assertEquals("flame", load(service, STEVE));

        service.open(mysql()); // the next attempt copies everything
        assertEquals("flame", load(service, STEVE));
        assertEquals("FakeDB", service.describe());
        assertEquals("flame", database.rows.get(STEVE));
        service.shutdown();
        assertFalse(fallbackFile(folder).exists());

        StorageService restarted = service(folder, database);
        restarted.open(mysql());
        assertEquals("the newest choice survives a restart", "flame", load(restarted, STEVE));
        restarted.shutdown();
    }

    @Test
    public void failedCopyDoesNotReplaceNewerSelections() throws Exception {
        File folder = temp.newFolder();
        FakeDatabase database = new FakeDatabase();
        database.rows.put(STEVE, "flame");
        StorageService service = service(folder, database);
        SelectionService selections = new SelectionService(service);
        service.open(mysql());
        database.down = true;
        service.save(STEVE, "Steve", "vortex"); // chosen during the outage, then Steve joins again:
        loadSelection(selections, player(STEVE, "Steve"));
        assertEquals("vortex", selections.selectedId(STEVE));

        database.down = false;
        database.failingSaves.set(1);
        service.open(mysql());
        load(service, ALEX);
        assertEquals("vortex", selections.selectedId(STEVE));
        service.open(mysql());
        load(service, ALEX);
        assertEquals("vortex", selections.selectedId(STEVE));
        assertEquals("vortex", database.rows.get(STEVE));
        service.shutdown();
    }

    @Test
    public void databaseLostAgainWhileReloadingSelections() throws Exception {
        File folder = temp.newFolder();
        FakeDatabase database = new FakeDatabase();
        database.rows.put(STEVE, "flame");
        database.rows.put(ALEX, "dna");
        database.down = true;
        StorageService service = service(folder, database);
        service.open(mysql());
        SelectionService selections = new SelectionService(service);
        loadSelection(selections, player(STEVE, "Steve"));
        loadSelection(selections, player(ALEX, "Alex"));

        database.down = false;
        database.failingLoads.set(1); // the connection drops right after connecting
        service.open(mysql());
        load(service, ALEX);
        assertTrue(service.describe(), service.describe().startsWith("YAML (players-fallback.yml) - FakeDB"));
        service.open(mysql()); // a reload, or the retry a few minutes later
        load(service, ALEX);
        assertEquals("FakeDB", service.describe());
        assertEquals("flame", selections.selectedId(STEVE));
        assertEquals("dna", selections.selectedId(ALEX));
        service.shutdown();
    }

    @Test
    public void defaultStorageIsAStandInWhileConfigCannotBeRead() throws Exception {
        File folder = temp.newFolder();
        FakeDatabase database = new FakeDatabase();
        database.rows.put(STEVE, "flame");
        database.rows.put(ALEX, "dna");
        StorageService service = service(folder, database);
        SelectionService selections = new SelectionService(service);
        // config.yml cannot be read on startup, so the default (players.yml) is used ...
        service.open(yaml(), false);
        Player steve = player(STEVE, "Steve");
        assertTrue(loadSelection(selections, steve));
        assertNull(selections.selectedId(STEVE));
        selections.select(steve, null); // Steve clears his effect
        service.save(ALEX, "Alex", "vortex");
        assertTrue(service.describe(), service.describe().endsWith("(the default, as config.yml cannot be read)"));

        // ... until the file is fixed and reloaded: it asks for the database.
        service.open(mysql(), true);
        load(service, ALEX);
        assertEquals("FakeDB", service.describe());
        assertNull("the clear reaches the database", database.rows.get(STEVE));
        assertEquals("vortex", database.rows.get(ALEX));
        assertNull(selections.selectedId(STEVE));
        service.shutdown();
    }

    @Test
    public void unreadableFallbackIsKeptAsideAndANewOneIsUsed() throws Exception {
        File folder = temp.newFolder();
        ConfigFiles.write(fallbackFile(folder), "players: [\n");
        FakeDatabase database = new FakeDatabase();
        database.rows.put(STEVE, "flame");
        StorageService service = service(folder, database);
        service.open(mysql());
        load(service, STEVE);
        database.down = true;
        service.save(STEVE, "Steve", "vortex");
        assertEquals("vortex", load(service, STEVE));
        database.down = false;
        service.open(mysql());
        load(service, STEVE);
        assertEquals("vortex", database.rows.get(STEVE));
        service.shutdown();
        File[] kept = folder.listFiles((dir, name) -> name.startsWith(StorageService.FALLBACK_FILE + ".broken-"));
        assertEquals(1, kept.length);
        assertEquals("players: [\n", ConfigFiles.read(kept[0]));
    }

    @Test
    public void leftoverFallbackIsCopiedOnStart() throws Exception {
        File folder = temp.newFolder();
        ConfigFiles.write(fallbackFile(folder), "players:\n  " + STEVE + ":\n    name: Steve\n    effect: vortex\n");
        StorageService service = service(folder);
        service.open(yaml());
        assertEquals("vortex", load(service, STEVE));
        service.shutdown();
        assertFalse(fallbackFile(folder).exists());
    }

    @Test
    public void leftoverFallbackStaysInUseIfItCannotBeCopied() throws Exception {
        File folder = temp.newFolder();
        ConfigFiles.write(fallbackFile(folder), "players:\n  " + STEVE + ":\n    name: Steve\n    effect: vortex\n");
        FakeDatabase database = new FakeDatabase();
        database.rows.put(STEVE, "flame");
        database.failingSaves.set(1);
        StorageService service = service(folder, database);
        service.open(mysql());
        assertEquals("the leftover is not lost", "vortex", load(service, STEVE));
        assertTrue(service.describe(), service.describe().startsWith("YAML (players-fallback.yml) - FakeDB"));
        service.open(mysql());
        assertEquals("vortex", load(service, STEVE));
        assertEquals("vortex", database.rows.get(STEVE));
        service.shutdown();
        assertFalse(fallbackFile(folder).exists());
    }
}

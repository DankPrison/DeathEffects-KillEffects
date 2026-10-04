package com.dank420girl.core.testing;

import com.dank420girl.core.Variant;
import com.dank420girl.core.config.ConfigFiles;
import com.dank420girl.core.effect.LayerParser;
import com.dank420girl.core.effect.particle.ParticleOutput;
import com.dank420girl.core.effect.particle.ParticleService;
import com.dank420girl.core.effect.particle.ParticleSpec;
import com.dank420girl.core.names.NameTables;
import com.dank420girl.core.names.Names;
import com.dank420girl.core.platform.MinecraftVersion;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * The names a server of one Minecraft version knows, read from {@code versions/<version>.txt}, so
 * tests can check names, files and effects against many versions without running a server.
 */
public final class TestServer {
    /** The versions there are name lists for, oldest first. */
    public static final String[] VERSIONS = {"1.7.10", "1.8.8", "1.12.2", "1.16.5", "1.20.1", "1.21.4", "26.3"};

    public static final Variant DEATH_EFFECTS = Variant.DEATH_EFFECTS;
    public static final Variant KILL_EFFECTS = Variant.KILL_EFFECTS;

    private final MinecraftVersion version;
    private final Set<String> materials = new HashSet<String>();
    private final Set<String> sounds = new HashSet<String>();
    private final Set<String> soundKeys = new HashSet<String>();
    private final Set<String> particles = new HashSet<String>();
    private final Names names;
    private final ParticleService particleService;

    private TestServer(String version) throws IOException {
        this.version = MinecraftVersion.parse(version);
        readLists(version);
        if (this.version.isBefore(1, 9)) {
            // 1.7 and 1.8 have no particle enum; the packets use the names of the particle table.
            for (NameTables.ParticleEntry entry : NameTables.get().particles()) {
                String name = this.version.isBefore(1, 8) ? entry.name17 : entry.nms18;
                if (name != null) {
                    particles.add(name);
                }
            }
        }
        if (this.version.isAtLeast(1, 16)) {
            // Since 1.16 sounds have keys, which the plugins can look up.
            for (NameTables.SoundEntry entry : NameTables.get().sounds()) {
                boolean known = sounds.contains(entry.name);
                for (String legacy : entry.legacy) {
                    known |= sounds.contains(legacy);
                }
                if (known && entry.key != null) {
                    soundKeys.add(entry.key);
                }
            }
        }
        names = new Names(NameTables.get(), this.version, contains(materials), contains(sounds), contains(soundKeys),
                contains(particles));
        particleService = new ParticleService(Logger.getLogger("test"), null, new FakeOutput());
    }

    /** A server of the given version; also makes it the "current" version for code that asks. */
    public static TestServer of(String version) {
        try {
            TestServer server = new TestServer(version);
            MinecraftVersion.setCurrentForTesting(server.version);
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("No name lists for " + version, e);
        }
    }

    private static Predicate<String> contains(final Set<String> set) {
        return new Predicate<String>() {
            @Override
            public boolean test(String name) {
                return set.contains(name);
            }
        };
    }

    private void readLists(String version) throws IOException {
        InputStream in = TestServer.class.getClassLoader().getResourceAsStream("versions/" + version + ".txt");
        if (in == null) {
            throw new IOException("versions/" + version + ".txt is missing");
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            Set<String> target = null;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.equals("[materials]")) {
                    target = materials;
                } else if (line.equals("[sounds]")) {
                    target = sounds;
                } else if (line.equals("[particles]")) {
                    target = particles;
                } else if (target != null) {
                    target.add(line);
                }
            }
        } finally {
            reader.close();
        }
    }

    public MinecraftVersion version() {
        return version;
    }

    public Names names() {
        return names;
    }

    public ParticleService particles() {
        return particleService;
    }

    public boolean hasMaterial(String name) {
        return materials.contains(name);
    }

    public boolean hasSound(String name) {
        return sounds.contains(name);
    }

    public boolean hasParticle(String name) {
        return particles.contains(name);
    }

    /** A parser for files whose names are written for the given version. */
    public LayerParser parser(MinecraftVersion source) {
        return new LayerParser(names, particleService, source);
    }

    /** The configuration files of a plugin in the folder, as the plugin creates them. */
    public ConfigFiles files(File folder, Variant variant) {
        return new ConfigFiles(folder, Logger.getLogger("test"), names, version).tokens(variant.tokens());
    }

    /** Accepts the particles and items this version has, like the real outputs do. */
    private final class FakeOutput implements ParticleOutput {
        @Override
        public String name() {
            return "test";
        }

        @Override
        public boolean exists(String particleName) {
            return particles.contains(particleName);
        }

        @Override
        public boolean prepare(ParticleSpec spec) {
            if (!particles.contains(spec.particle().name())) {
                return false;
            }
            return spec.item() == null || materials.contains(spec.item().name());
        }

        @Override
        public void send(List<Player> viewers, double x, double y, double z, ParticleSpec spec, int count,
                         double offsetX, double offsetY, double offsetZ, double speed, int rgb, int rgb2,
                         boolean force, Random random) {
        }
    }
}

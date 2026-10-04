package com.dank420girl.core.names;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The bundled tables of item, sound and particle names used by different Minecraft versions.
 * The tables are pure data and know nothing about the running server.
 */
public final class NameTables {
    private static volatile NameTables instance;

    final Map<String, MaterialEntry> materialsByModern = new LinkedHashMap<String, MaterialEntry>();
    final Map<String, List<MaterialEntry>> materialsByLegacy = new HashMap<String, List<MaterialEntry>>();

    final Map<String, SoundEntry> soundsByName = new LinkedHashMap<String, SoundEntry>();
    final Map<String, SoundEntry> soundsByKey = new HashMap<String, SoundEntry>();
    final Map<String, SoundEntry> soundsByLegacy = new HashMap<String, SoundEntry>();

    final Map<String, ParticleEntry> particlesByCanonical = new LinkedHashMap<String, ParticleEntry>();
    final Map<String, ParticleEntry> particlesByAnyName = new HashMap<String, ParticleEntry>();

    /** The tables bundled with the plugin, loaded once. */
    public static NameTables get() {
        NameTables tables = instance;
        if (tables == null) {
            synchronized (NameTables.class) {
                tables = instance;
                if (tables == null) {
                    tables = new NameTables();
                    tables.loadMaterials(open("names/materials.txt"));
                    tables.loadSounds(open("names/sounds.txt"));
                    tables.loadParticles(open("names/particles.txt"));
                    instance = tables;
                }
            }
        }
        return tables;
    }

    private static List<String[]> open(String resource) {
        InputStream in = NameTables.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing resource " + resource);
        }
        List<String[]> rows = new ArrayList<String[]>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                rows.add(line.split(" +"));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + resource, e);
        }
        return rows;
    }

    void loadMaterials(List<String[]> rows) {
        for (String[] row : rows) {
            String modern = row[0];
            String dataField = row.length > 1 ? row[1] : "0";
            int data;
            int dataSinceMinor = 0;
            int at = dataField.indexOf('@');
            if (at >= 0) {
                data = Integer.parseInt(dataField.substring(0, at));
                String since = dataField.substring(at + 1);
                dataSinceMinor = Integer.parseInt(since.substring(since.indexOf('.') + 1));
            } else {
                data = Integer.parseInt(dataField);
            }
            String[] legacy = new String[Math.max(0, row.length - 2)];
            System.arraycopy(row, 2, legacy, 0, legacy.length);
            MaterialEntry entry = new MaterialEntry(modern, data, dataSinceMinor, legacy);
            materialsByModern.put(modern, entry);
            for (String name : legacy) {
                List<MaterialEntry> list = materialsByLegacy.get(name);
                if (list == null) {
                    list = new ArrayList<MaterialEntry>(2);
                    materialsByLegacy.put(name, list);
                }
                list.add(entry);
            }
        }
    }

    void loadSounds(List<String[]> rows) {
        for (String[] row : rows) {
            String name = row[0];
            String key = row.length > 1 && !row[1].equals("-") ? row[1].toLowerCase(Locale.ROOT) : null;
            String[] legacy = new String[Math.max(0, row.length - 2)];
            System.arraycopy(row, 2, legacy, 0, legacy.length);
            SoundEntry entry = new SoundEntry(name, key, legacy);
            soundsByName.put(name, entry);
            if (key != null && !soundsByKey.containsKey(key)) {
                soundsByKey.put(key, entry);
            }
            for (String old : legacy) {
                if (!soundsByLegacy.containsKey(old)) {
                    soundsByLegacy.put(old, entry);
                }
            }
        }
    }

    void loadParticles(List<String[]> rows) {
        for (String[] row : rows) {
            String[] others = new String[Math.max(0, row.length - 6)];
            System.arraycopy(row, 6, others, 0, others.length);
            ParticleEntry entry = new ParticleEntry(row[0], dash(row[1]), dash(row[2]), dash(row[3]), dash(row[4]),
                    ParticleEntry.Kind.valueOf(row[5].toUpperCase(Locale.ROOT)), others);
            particlesByCanonical.put(entry.canonical, entry);
            register(entry.canonical, entry);
            register(entry.modern, entry);
            register(entry.old, entry);
            register(entry.nms18, entry);
            if (entry.name17 != null) {
                register(entry.name17.toUpperCase(Locale.ROOT), entry);
            }
            for (String other : others) {
                register(other, entry);
            }
        }
    }

    private void register(String name, ParticleEntry entry) {
        if (name != null && !particlesByAnyName.containsKey(name)) {
            particlesByAnyName.put(name, entry);
        }
    }

    private static String dash(String value) {
        return value.equals("-") ? null : value;
    }

    public MaterialEntry material(String modernName) {
        return materialsByModern.get(modernName);
    }

    public List<MaterialEntry> materialsWithLegacyName(String legacyName) {
        List<MaterialEntry> list = materialsByLegacy.get(legacyName);
        return list == null ? Collections.<MaterialEntry>emptyList() : list;
    }

    public java.util.Collection<MaterialEntry> materials() {
        return Collections.unmodifiableCollection(materialsByModern.values());
    }

    public java.util.Collection<SoundEntry> sounds() {
        return Collections.unmodifiableCollection(soundsByName.values());
    }

    public java.util.Collection<ParticleEntry> particles() {
        return Collections.unmodifiableCollection(particlesByCanonical.values());
    }

    /** A row of the material table: the 1.13+ name, the pre-1.13 data value and older names. */
    public static final class MaterialEntry {
        public final String modern;
        public final int data;
        /** The data value only applies from 1.x on (beds became coloured in 1.12); 0 = always. */
        public final int dataSinceMinor;
        public final String[] legacy;

        MaterialEntry(String modern, int data, int dataSinceMinor, String[] legacy) {
            this.modern = modern;
            this.data = data;
            this.dataSinceMinor = dataSinceMinor;
            this.legacy = legacy;
        }

        @Override
        public String toString() {
            return modern;
        }
    }

    /** A row of the sound table: the current name, its namespaced key and older names. */
    public static final class SoundEntry {
        public final String name;
        public final String key;
        public final String[] legacy;

        SoundEntry(String name, String key, String[] legacy) {
            this.name = name;
            this.key = key;
            this.legacy = legacy;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** A row of the particle table with the particle's name in each era of the API. */
    public static final class ParticleEntry {
        /** How extra particle data is sent on servers before 1.13. */
        public enum Kind {
            PLAIN, DUST, SPELL, NOTE, ITEM, BLOCK
        }

        public final String canonical;
        /** Name since 1.20.5, or null. */
        public final String modern;
        /** Bukkit name from 1.9 to 1.20.4, or null. */
        public final String old;
        /** EnumParticle name on 1.8, or null. */
        public final String nms18;
        /** Packet name on 1.7, or null. */
        public final String name17;
        public final Kind kind;
        public final String[] otherNames;

        ParticleEntry(String canonical, String modern, String old, String nms18, String name17, Kind kind, String[] otherNames) {
            this.canonical = canonical;
            this.modern = modern;
            this.old = old;
            this.nms18 = nms18;
            this.name17 = name17;
            this.kind = kind;
            this.otherNames = otherNames;
        }

        @Override
        public String toString() {
            return canonical;
        }
    }
}

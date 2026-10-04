package com.dank420girl.core.names;

import com.dank420girl.core.names.NameTables.MaterialEntry;
import com.dank420girl.core.names.NameTables.ParticleEntry;
import com.dank420girl.core.names.NameTables.SoundEntry;
import com.dank420girl.core.platform.MinecraftVersion;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Translates item, sound and particle names between Minecraft versions for one server.
 * <p>
 * Names are understood whichever version they were written for (for example {@code WOOL:14},
 * {@code RED_WOOL} and {@code minecraft:red_wool} are the same item) and are turned into the name
 * the running server uses. Whether a name exists is always checked against the server.
 */
public final class Names {
    /**
     * Names that mean different things before and after 1.13 (for example GRASS was the grass block and
     * became the plant). Before 1.13 they are never resolved through their modern meaning.
     */
    private static final Set<String> DUPLICATED = new HashSet<String>(Arrays.asList(
            "MELON", "CARROT", "POTATO", "GRASS", "BRICK", "NETHER_BRICK", "DARK_OAK_DOOR", "ACACIA_DOOR",
            "BIRCH_DOOR", "JUNGLE_DOOR", "SPRUCE_DOOR", "MAP", "CAULDRON", "BREWING_STAND", "FLOWER_POT"));

    private final NameTables tables;
    private final MinecraftVersion server;
    private final Predicate<String> materialExists;
    private final Predicate<String> soundExists;
    private final Predicate<String> soundKeyExists;
    private final Predicate<String> particleExists;

    private final ConcurrentHashMap<String, String> itemCache = new ConcurrentHashMap<String, String>();

    /**
     * @param materialExists whether the server has a Material constant with the name
     * @param soundExists    whether the server has a Sound constant with the name
     * @param soundKeyExists whether the server knows a sound by its key, such as {@code entity.player.levelup};
     *                       servers that cannot tell (before 1.16) answer {@code false}
     * @param particleExists whether the server has a particle with the name
     */
    public Names(NameTables tables, MinecraftVersion server, Predicate<String> materialExists,
                 Predicate<String> soundExists, Predicate<String> soundKeyExists, Predicate<String> particleExists) {
        this.tables = tables;
        this.server = server;
        this.materialExists = materialExists;
        this.soundExists = soundExists;
        this.soundKeyExists = soundKeyExists;
        this.particleExists = particleExists;
    }

    public MinecraftVersion serverVersion() {
        return server;
    }

    // ------------------------------------------------------------------------------------------ items

    /**
     * Resolves an item name written for any version, e.g. {@code GRAY_STAINED_GLASS_PANE},
     * {@code STAINED_GLASS_PANE:7} or {@code minecraft:gray_stained_glass_pane}.
     *
     * @param source the version the name was written for, or {@code null} if unknown
     * @return the server's name and data value, or {@code null} if the item does not exist on this server
     */
    public ItemName item(String input, MinecraftVersion source) {
        if (input == null) {
            return null;
        }
        String cacheKey = input + '|' + source;
        String cached = itemCache.get(cacheKey);
        if (cached != null) {
            return cached.isEmpty() ? null : ItemName.parse(cached);
        }
        ItemName result = resolveItem(input, source);
        itemCache.put(cacheKey, result == null ? "" : result.toString());
        return result;
    }

    /** The server's spelling of an item name, or {@code null} if it cannot be translated. */
    public String translateItem(String input, MinecraftVersion source) {
        ItemName name = item(input, source);
        return name == null ? null : name.toString();
    }

    /** An item name split into its parts: the plain name, the data value (-1 if none) and its era. */
    private static final class ParsedItem {
        final String name;
        final int data;
        final boolean legacySource;

        ParsedItem(String name, int data, boolean legacySource) {
            this.name = name;
            this.data = data;
            this.legacySource = legacySource;
        }
    }

    private static ParsedItem parseItem(String input, MinecraftVersion source) {
        String text = input.trim();
        if (text.isEmpty()) {
            return null;
        }
        text = text.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (text.startsWith("MINECRAFT:")) {
            text = text.substring("MINECRAFT:".length());
        }
        int data = -1;
        int colon = text.lastIndexOf(':');
        if (colon > 0) {
            try {
                data = Integer.parseInt(text.substring(colon + 1).trim());
                text = text.substring(0, colon).trim();
            } catch (NumberFormatException e) {
                return null;
            }
        }
        boolean legacySyntax = false;
        if (text.startsWith("LEGACY_")) {
            text = text.substring("LEGACY_".length());
            legacySyntax = true;
        }
        boolean legacySource = legacySyntax || data >= 0 || (source != null && source.isLegacy());
        return new ParsedItem(text, data, legacySource);
    }

    private ItemName resolveItem(String input, MinecraftVersion source) {
        ParsedItem parsed = parseItem(input, source);
        if (parsed == null) {
            return null;
        }
        MaterialEntry entry = findMaterial(parsed.name, parsed.data, parsed.legacySource);
        if (entry != null) {
            ItemName name = serverName(entry);
            if (name != null) {
                return name;
            }
        }
        // Unknown to the tables (for example an item newer than them): accept it if the server knows it.
        if (entry == null && materialExists.test(parsed.name)) {
            return new ItemName(parsed.name, server.isLegacy() ? Math.max(0, parsed.data) : 0);
        }
        return null;
    }

    /**
     * The modern name of the item a name stands for, whichever version it was written for; used to
     * check that a translation means the same item. {@code null} if the tables do not know it.
     */
    public String canonicalItem(String input, MinecraftVersion source) {
        ParsedItem parsed = input == null ? null : parseItem(input, source);
        if (parsed == null) {
            return null;
        }
        MaterialEntry entry = findMaterial(parsed.name, parsed.data, parsed.legacySource);
        return entry == null ? null : entry.modern;
    }

    /** Finds the table entry for a name, following how XSeries interprets legacy names. */
    MaterialEntry findMaterial(String name, int data, boolean legacySource) {
        MaterialEntry modern = tables.material(name);
        if (!legacySource) {
            if (modern != null) {
                return modern;
            }
            return bestLegacy(name, data);
        }
        if (modern != null && data <= 0 && !DUPLICATED.contains(name)) {
            return modern;
        }
        MaterialEntry legacy = bestLegacy(name, data);
        if (legacy != null) {
            return legacy;
        }
        return data <= 0 ? modern : null;
    }

    private MaterialEntry bestLegacy(String name, int data) {
        List<MaterialEntry> candidates = tables.materialsWithLegacyName(name);
        MaterialEntry best = null;
        int wantedData = Math.max(0, data);
        for (MaterialEntry candidate : candidates) {
            if (candidate.data != wantedData) {
                continue;
            }
            if (best == null || candidate.legacy.length < best.legacy.length) {
                best = candidate;
            }
        }
        if (best == null && data < 0 && !candidates.isEmpty()) {
            best = candidates.get(0);
        }
        return best;
    }

    /** How the server spells the item of a table entry, or {@code null} if it has no such item. */
    ItemName serverName(MaterialEntry entry) {
        if (!server.isLegacy()) {
            if (materialExists.test(entry.modern)) {
                return new ItemName(entry.modern, 0);
            }
            for (int i = entry.legacy.length - 1; i >= 0; i--) {
                if (materialExists.test(entry.legacy[i])) {
                    return new ItemName(entry.legacy[i], 0);
                }
            }
            return null;
        }
        int data = entry.dataSinceMinor > 0 && server.isBefore(1, entry.dataSinceMinor) ? 0 : entry.data;
        if (!DUPLICATED.contains(entry.modern) && materialExists.test(entry.modern)) {
            return new ItemName(entry.modern, data);
        }
        for (int i = entry.legacy.length - 1; i >= 0; i--) {
            String legacy = entry.legacy[i];
            // Names introduced by 1.13 (like ROSE_RED) never exist before it, so only true legacy names match.
            if (materialExists.test(legacy)) {
                return new ItemName(legacy, data);
            }
        }
        return null;
    }

    // ----------------------------------------------------------------------------------------- sounds

    /**
     * Resolves a sound name written for any version, a namespaced key like
     * {@code entity.player.levelup}, or a custom resource pack sound key.
     *
     * @return the resolved sound, or {@code null} if it cannot be played on this server
     */
    public SoundName sound(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String text = input.trim();
        if (text.indexOf('.') >= 0 || text.indexOf(':') >= 0) {
            String key = text.toLowerCase(Locale.ROOT);
            if (key.startsWith("minecraft:")) {
                key = key.substring("minecraft:".length());
            }
            SoundEntry entry = tables.soundsByKey.get(key);
            if (entry != null) {
                // A vanilla sound: null if this version does not have it.
                return serverSound(entry);
            }
            // Not a vanilla sound we know: custom (resource pack) sounds and sounds of newer versions
            // are played by their key since 1.9, and with their plain name before that.
            return SoundName.key(text.contains(":") ? text.toLowerCase(Locale.ROOT) : key);
        }
        String name = text.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        SoundEntry entry = tables.soundsByName.get(name);
        if (entry == null) {
            entry = tables.soundsByLegacy.get(name);
        }
        if (entry != null) {
            SoundName resolved = serverSound(entry);
            if (resolved != null) {
                return resolved;
            }
        }
        if (soundExists.test(name)) {
            return SoundName.constant(name);
        }
        return null;
    }

    /**
     * The modern name of the sound a name or key stands for; used to check that a translation means
     * the same sound. {@code null} if the tables do not know it.
     */
    public String canonicalSound(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String text = input.trim();
        SoundEntry entry;
        if (text.indexOf('.') >= 0 || text.indexOf(':') >= 0) {
            String key = text.toLowerCase(Locale.ROOT);
            if (key.startsWith("minecraft:")) {
                key = key.substring("minecraft:".length());
            }
            entry = tables.soundsByKey.get(key);
        } else {
            String name = text.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
            entry = tables.soundsByName.get(name);
            if (entry == null) {
                entry = tables.soundsByLegacy.get(name);
            }
        }
        return entry == null ? null : entry.name;
    }

    /** The server's spelling of a sound name, or {@code null} if it cannot be translated. */
    public String translateSound(String input) {
        SoundName sound = sound(input);
        if (sound == null || !sound.isConstant()) {
            return null;
        }
        return sound.name();
    }

    private SoundName serverSound(SoundEntry entry) {
        if (soundExists.test(entry.name)) {
            return SoundName.constant(entry.name);
        }
        for (String legacy : entry.legacy) {
            if (soundExists.test(legacy)) {
                return SoundName.constant(legacy);
            }
        }
        // The constant may have a name we do not know (a later rename); the key still works.
        if (entry.key != null && server.isAtLeast(1, 9) && soundKeyExists.test(entry.key)) {
            return SoundName.key(entry.key);
        }
        return null;
    }

    // -------------------------------------------------------------------------------------- particles

    /**
     * Resolves a particle name written for any version.
     *
     * @return the particle, or {@code null} if it does not exist on this server
     */
    public ParticleName particle(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String name = input.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (name.startsWith("MINECRAFT:")) {
            name = name.substring("MINECRAFT:".length());
        }
        ParticleEntry entry = tables.particlesByAnyName.get(name);
        if (entry != null) {
            String serverName = serverParticle(entry);
            if (serverName != null) {
                return new ParticleName(serverName, entry, entry.kind);
            }
        }
        if (particleExists.test(name)) {
            return new ParticleName(name, entry, entry == null ? ParticleEntry.Kind.PLAIN : entry.kind);
        }
        return null;
    }

    /**
     * The canonical name of the particle a name stands for; used to check that a translation means the
     * same particle. {@code null} if the tables do not know it.
     */
    public String canonicalParticle(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String name = input.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (name.startsWith("MINECRAFT:")) {
            name = name.substring("MINECRAFT:".length());
        }
        ParticleEntry entry = tables.particlesByAnyName.get(name);
        return entry == null ? null : entry.canonical;
    }

    /** The server's spelling of a particle name, or {@code null} if it cannot be translated. */
    public String translateParticle(String input) {
        ParticleName particle = particle(input);
        return particle == null ? null : particle.name();
    }

    private String serverParticle(ParticleEntry entry) {
        if (server.isBefore(1, 8)) {
            return entry.name17;
        }
        if (server.isBefore(1, 9)) {
            return entry.nms18 != null && particleExists.test(entry.nms18) ? entry.nms18 : null;
        }
        if (entry.modern != null && particleExists.test(entry.modern)) {
            return entry.modern;
        }
        if (entry.old != null && particleExists.test(entry.old)) {
            return entry.old;
        }
        for (String other : entry.otherNames) {
            if (particleExists.test(other)) {
                return other;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------------- results

    /** An item as the server names it: a material name and, before 1.13, a data value. */
    public static final class ItemName {
        private final String name;
        private final int data;

        ItemName(String name, int data) {
            this.name = name;
            this.data = data;
        }

        /** An item name exactly as given, without any translation. */
        public static ItemName of(String name, int data) {
            return new ItemName(name, data);
        }

        static ItemName parse(String text) {
            int colon = text.indexOf(':');
            return colon < 0 ? new ItemName(text, 0)
                    : new ItemName(text.substring(0, colon), Integer.parseInt(text.substring(colon + 1)));
        }

        public String name() {
            return name;
        }

        public int data() {
            return data;
        }

        @Override
        public String toString() {
            return data == 0 ? name : name + ":" + data;
        }
    }

    /** A sound as the server knows it: a Bukkit constant name or a namespaced key. */
    public static final class SoundName {
        private final String name;
        private final boolean constant;

        private SoundName(String name, boolean constant) {
            this.name = name;
            this.constant = constant;
        }

        static SoundName constant(String name) {
            return new SoundName(name, true);
        }

        static SoundName key(String key) {
            return new SoundName(key, false);
        }

        public String name() {
            return name;
        }

        /** True for a Bukkit Sound constant, false for a key played by name. */
        public boolean isConstant() {
            return constant;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** A particle as the server names it, with the table row it came from (if any). */
    public static final class ParticleName {
        private final String name;
        private final ParticleEntry entry;
        private final ParticleEntry.Kind kind;

        ParticleName(String name, ParticleEntry entry, ParticleEntry.Kind kind) {
            this.name = name;
            this.entry = entry;
            this.kind = kind;
        }

        public String name() {
            return name;
        }

        /** The canonical (1.20.5+) name, used to recognise particles independent of the version. */
        public String canonical() {
            return entry == null ? name : entry.canonical;
        }

        public ParticleEntry.Kind kind() {
            return kind;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}

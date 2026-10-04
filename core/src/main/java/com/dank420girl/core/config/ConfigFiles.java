package com.dank420girl.core.config;

import com.dank420girl.core.names.Names;
import com.dank420girl.core.platform.MinecraftVersion;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates, translates and loads the YAML files in the plugin folder.
 * <p>
 * Default files are generated from templates with the item, sound and particle names of the
 * running server. Each file remembers the Minecraft version its names were written for
 * ({@code names-version}); after a server update or downgrade the names are translated in place,
 * keeping every comment, and the previous file is kept in the {@code backups} folder.
 */
public final class ConfigFiles {
    /** The templates of the three files each plugin has, shared by both plugins. */
    public static final String CONFIG_TEMPLATE = "templates/config.yml";
    public static final String MESSAGES_TEMPLATE = "templates/messages.yml";
    public static final String EFFECTS_TEMPLATE = "templates/effects.yml";
    public static final String NAMES_VERSION_KEY = "names-version";
    private static final String NAMES_VERSION_COMMENT =
            "The Minecraft version the item, sound and particle names in this file are written for. Updated automatically.";

    private final File folder;
    private final Logger logger;
    private final Names names;
    private final MinecraftVersion server;
    private static final Pattern INCLUDE = Pattern.compile("@INCLUDE ([^@\\s]+)@");

    private final Map<String, String> tokens = new LinkedHashMap<String, String>();

    public ConfigFiles(File folder, Logger logger, Names names, MinecraftVersion server) {
        this.folder = folder;
        this.logger = logger;
        this.names = names;
        this.server = server;
    }

    /** Replaces {@code @NAME@} in templates with the value. */
    public ConfigFiles token(String name, String value) {
        tokens.put("@" + name + "@", value);
        return this;
    }

    /** Adds several tokens, see {@link #token}. */
    public ConfigFiles tokens(Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            token(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * Reads a template and replaces its tokens. {@code @INCLUDE path@} (usually the value of a token) is
     * replaced by the template at that path, so plugins can add parts of their own to a shared template.
     */
    public String readTemplate(String name) throws IOException {
        return expand(readResource(name), 0);
    }

    private String expand(String text, int depth) throws IOException {
        for (Map.Entry<String, String> token : tokens.entrySet()) {
            text = text.replace(token.getKey(), token.getValue());
        }
        Matcher include = INCLUDE.matcher(text);
        if (!include.find()) {
            return text;
        }
        if (depth > 3) {
            throw new IOException("Templates include each other too deeply: " + include.group(1));
        }
        StringBuffer result = new StringBuffer();
        do {
            String included = expand(readResource(include.group(1)), depth + 1);
            if (included.endsWith("\n")) {
                included = included.substring(0, included.length() - 1); // the line with the include has its own
            }
            include.appendReplacement(result, Matcher.quoteReplacement(included));
        } while (include.find());
        include.appendTail(result);
        return result.toString();
    }

    public File folder() {
        return folder;
    }

    /** The result of loading a file. */
    public static final class Loaded {
        private final YamlConfiguration config;
        private final MinecraftVersion namesVersion;
        private final boolean defaults;

        Loaded(YamlConfiguration config, MinecraftVersion namesVersion, boolean defaults) {
            this.config = config;
            this.namesVersion = namesVersion;
            this.defaults = defaults;
        }

        public YamlConfiguration config() {
            return config;
        }

        /** The version the names in the file were written for (the server version after translation). */
        public MinecraftVersion namesVersion() {
            return namesVersion;
        }

        /** True if these are the template's settings because the file could not be read. */
        public boolean isDefaults() {
            return defaults;
        }
    }

    /**
     * Loads a file, creating it from the template first if it does not exist.
     *
     * @param template      resource path of the template
     * @param translate     whether the file contains item, sound or particle names to keep up to date
     * @param withDefaults  whether missing keys fall back to the template's values
     */
    public Loaded load(String fileName, String template, boolean translate, boolean withDefaults)
            throws IOException, InvalidConfigurationException {
        File file = new File(folder, fileName);
        if (!file.exists()) {
            write(file, render(template, translate));
            logger.info("Created " + fileName);
        }
        String text = read(file);
        // A file with mistakes is reported as it is and never rewritten.
        YamlConfiguration config = parse(text, fileName);
        MinecraftVersion namesVersion = server;
        if (translate) {
            YamlText yaml = new YamlText(text);
            MinecraftVersion written = MinecraftVersion.parse(yaml.get(NAMES_VERSION_KEY));
            if (written == null || !written.equals(server)) {
                int changes = translateNames(yaml, written, templateAlternatives(template));
                yaml.setTopLevel(NAMES_VERSION_KEY, server.toString(), NAMES_VERSION_COMMENT);
                String translated = yaml.toString();
                YamlConfiguration translatedConfig = null;
                try {
                    translatedConfig = parse(translated, fileName);
                } catch (InvalidConfigurationException e) {
                    logger.warning("Could not write the names of Minecraft " + server + " into " + fileName
                            + ", so the file was left as it is: " + firstLine(e.getMessage()));
                    namesVersion = written == null ? server : written;
                }
                if (translatedConfig != null && !translated.equals(text)) {
                    if (changes > 0) {
                        File backup = backup(file, written);
                        logger.info("Translated " + changes + " item, sound and particle names in " + fileName + " from "
                                + (written == null ? "an unknown version" : written.toString()) + " to " + server
                                + " (the old file was saved as " + relative(backup) + ")");
                    }
                    write(file, translated);
                    config = translatedConfig;
                }
            }
        }
        if (withDefaults) {
            config.setDefaults(parse(render(template, translate), template));
            // Makes getKeys() include keys that only exist in the defaults; the file itself is never saved this way.
            config.options().copyDefaults(true);
        }
        return new Loaded(config, namesVersion, false);
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline).trim();
    }

    /** The template's settings, for when a file cannot be read. */
    public Loaded loadDefaults(String template, boolean translate) throws IOException, InvalidConfigurationException {
        YamlConfiguration config = parse(render(template, translate), template);
        return new Loaded(config, server, true);
    }

    /** Renders a template: replaces the tokens and translates the names for this server. */
    public String render(String template, boolean translate) throws IOException {
        String text = readTemplate(template);
        if (!translate) {
            return text;
        }
        YamlText yaml = new YamlText(text);
        collapseAlternatives(yaml);
        translateNames(yaml, null);
        yaml.setTopLevel(NAMES_VERSION_KEY, server.toString(), NAMES_VERSION_COMMENT);
        return yaml.toString();
    }

    /**
     * Templates offer alternatives for older versions, such as {@code sound: [NEW_SOUND, OLD_SOUND]}.
     * When the server knows the first one, the list is written as that single name, so new files only
     * contain lists where the server actually needs an alternative.
     */
    private int collapseAlternatives(YamlText yaml) {
        return yaml.transform(new YamlText.ListVisitor() {
            @Override
            public String visit(String path, String key, String value) {
                return null;
            }

            @Override
            public String visitList(String path, String key, List<String> values) {
                if (values.isEmpty() || !isAlternatives(path, key)) {
                    return null;
                }
                String first = values.get(0);
                switch (category(key)) {
                    case MATERIAL:
                        return names.translateItem(first, null);
                    case SOUND:
                        Names.SoundName sound = names.sound(first);
                        return sound == null ? null : sound.isConstant() ? sound.name() : first;
                    case PARTICLE:
                        return names.translateParticle(first);
                    default:
                        return null;
                }
            }
        });
    }

    /**
     * True if a list under this key is a list of alternatives (the first that exists is used). Item
     * lists in effect layers are different: there every item is shown.
     */
    static boolean isAlternatives(String path, String key) {
        String k = key.toLowerCase(Locale.ROOT);
        if (k.equals("sound") || k.endsWith("-sound") || k.equals("particle") || k.endsWith("-particle")
                || k.equals("icon") || k.endsWith("-icon")) {
            return true;
        }
        return k.equals("item") && path.startsWith("menu.");
    }

    /** Rewrites every item, sound and particle name to the server's spelling. Returns the number of changes. */
    public int translateNames(YamlText yaml, MinecraftVersion source) {
        return translateNames(yaml, source, Collections.<String, List<String>>emptyMap());
    }

    /**
     * Rewrites every item, sound and particle name to the server's spelling.
     *
     * @param alternatives lists of alternatives of the template by path: a name the server does not
     *                     know is replaced by the first alternative it knows, if the name is still the
     *                     template's first choice (for example after moving to an older version)
     * @return the number of changes
     */
    int translateNames(YamlText yaml, final MinecraftVersion source, final Map<String, List<String>> alternatives) {
        return yaml.transform(new YamlText.ValueVisitor() {
            @Override
            public String visit(String path, String key, String value) {
                if (value.isEmpty() || path.equals(NAMES_VERSION_KEY)) {
                    return null;
                }
                Category category = category(key);
                if (category == Category.NONE) {
                    return null;
                }
                String translated = translate(category, value, source);
                if (translated != null && !sameMeaning(category, value, source, translated)) {
                    // Only a stand-in on this version (for example a similar older sound): the name is kept,
                    // so it is used again after moving to a version that has it. It still works here,
                    // because names are resolved the same way when the file is loaded.
                    return null;
                }
                if (translated == null) {
                    // Not on this version at all. When the template offers alternatives, both names are
                    // written as a list: this version uses the alternative, newer ones the original.
                    String alternative = alternative(category, alternatives.get(path), value);
                    if (alternative != null && !path.endsWith("]") && isAlternatives(path, key)) {
                        return YamlText.listValue(Arrays.asList(value, alternative));
                    }
                    return null;
                }
                if (translated.equalsIgnoreCase(normalize(value))) {
                    return null;
                }
                return translated;
            }
        });
    }

    /** True if a translated name stands for the same item, sound or particle as the original. */
    private boolean sameMeaning(Category category, String original, MinecraftVersion source, String translated) {
        String before;
        String after;
        switch (category) {
            case MATERIAL:
                before = names.canonicalItem(original, source);
                after = names.canonicalItem(translated, server);
                break;
            case SOUND:
                before = names.canonicalSound(original);
                after = names.canonicalSound(translated);
                break;
            case PARTICLE:
                before = names.canonicalParticle(original);
                after = names.canonicalParticle(translated);
                break;
            default:
                return true;
        }
        // Names the tables do not know are taken as they are.
        return before == null || after == null || before.equals(after);
    }

    private String translate(Category category, String value, MinecraftVersion source) {
        switch (category) {
            case MATERIAL:
                return names.translateItem(value, source);
            case SOUND:
                return names.translateSound(value);
            case PARTICLE:
                return names.translateParticle(value);
            default:
                return null;
        }
    }

    /** The first alternative of the template the server knows, if the value is the template's first choice. */
    private String alternative(Category category, List<String> choices, String value) {
        if (choices == null || choices.size() < 2 || !normalize(choices.get(0)).equalsIgnoreCase(normalize(value))) {
            return null;
        }
        for (String choice : choices.subList(1, choices.size())) {
            String translated = translate(category, choice, null);
            if (translated != null) {
                return translated;
            }
        }
        return null;
    }

    /** The lists of alternatives in a template, by path. */
    private Map<String, List<String>> templateAlternatives(String template) {
        final Map<String, List<String>> result = new HashMap<String, List<String>>();
        String text;
        try {
            text = readTemplate(template);
        } catch (IOException e) {
            return result;
        }
        new YamlText(text).transform(new YamlText.ListVisitor() {
            @Override
            public String visit(String path, String key, String value) {
                return null;
            }

            @Override
            public String visitList(String path, String key, List<String> values) {
                if (isAlternatives(path, key)) {
                    result.put(path, values);
                }
                return null;
            }
        });
        return result;
    }

    private enum Category {
        MATERIAL, SOUND, PARTICLE, NONE
    }

    static Category category(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        if (k.equals("sound") || k.endsWith("-sound")) {
            return Category.SOUND;
        }
        if (k.equals("particle") || k.equals("particles") || k.endsWith("-particle")) {
            return Category.PARTICLE;
        }
        if (k.equals("icon") || k.equals("item") || k.equals("items") || k.equals("block") || k.equals("blocks")
                || k.equals("material") || k.endsWith("-icon") || k.endsWith("-item") || k.endsWith("-items")
                || k.endsWith("-block") || k.endsWith("-blocks")) {
            return Category.MATERIAL;
        }
        return Category.NONE;
    }

    private static String normalize(String value) {
        String v = value.trim();
        if (v.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            v = v.substring("minecraft:".length());
        }
        return v.replace(' ', '_').replace('-', '_');
    }

    private File backup(File file, MinecraftVersion written) throws IOException {
        File dir = new File(folder, "backups");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        String base = file.getName().replaceAll("\\.yml$", "");
        String suffix = written == null ? "unknown" : written.toString();
        File target = new File(dir, base + "-" + suffix + ".yml");
        for (int i = 2; target.exists(); i++) {
            target = new File(dir, base + "-" + suffix + "-" + i + ".yml");
        }
        Files.copy(file.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        return target;
    }

    private String relative(File file) {
        return folder.getName() + "/" + folder.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
    }

    public static YamlConfiguration parse(String text, String name) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new InvalidConfigurationException(name + " is not valid YAML: " + e.getMessage(), e);
        }
        return config;
    }

    /** Reads a file as UTF-8, falling back to Windows-1252 for files saved by old versions on Windows. */
    public static String read(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return decode(bytes);
    }

    static String decode(byte[] bytes) {
        int start = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, start, bytes.length - start)).toString();
        } catch (CharacterCodingException e) {
            Charset fallback = Charset.isSupported("windows-1252") ? Charset.forName("windows-1252") : StandardCharsets.ISO_8859_1;
            return new String(bytes, start, bytes.length - start, fallback);
        }
    }

    /** Writes the text as UTF-8 through a temporary file, so a crash never leaves a half-written file. */
    public static void write(File file, String text) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        File temp = new File(parent, file.getName() + ".tmp");
        Files.write(temp.toPath(), text.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String readResource(String name) throws IOException {
        InputStream in = ConfigFiles.class.getClassLoader().getResourceAsStream(name);
        if (in == null) {
            throw new IOException("Missing resource " + name);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}

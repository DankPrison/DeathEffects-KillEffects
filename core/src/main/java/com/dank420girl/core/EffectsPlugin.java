package com.dank420girl.core;

import com.dank420girl.core.command.EffectsCommand;
import com.dank420girl.core.compat.FightPlugins;
import com.dank420girl.core.config.ConfigFiles;
import com.dank420girl.core.config.LegacyMigration;
import com.dank420girl.core.config.Problems;
import com.dank420girl.core.config.Settings;
import com.dank420girl.core.effect.EffectDefinition;
import com.dank420girl.core.effect.EffectEngine;
import com.dank420girl.core.effect.EffectRegistry;
import com.dank420girl.core.effect.FireworkService;
import com.dank420girl.core.effect.LayerParser;
import com.dank420girl.core.effect.SoundService;
import com.dank420girl.core.effect.SoundSpec;
import com.dank420girl.core.effect.particle.ParticleService;
import com.dank420girl.core.listener.FireworkListener;
import com.dank420girl.core.listener.PlayerListener;
import com.dank420girl.core.menu.EffectMenu;
import com.dank420girl.core.menu.MenuListener;
import com.dank420girl.core.names.NameTables;
import com.dank420girl.core.names.Names;
import com.dank420girl.core.platform.Debug;
import com.dank420girl.core.platform.MinecraftVersion;
import com.dank420girl.core.platform.Platform;
import com.dank420girl.core.platform.scheduler.Scheduler;
import com.dank420girl.core.selection.SelectionService;
import com.dank420girl.core.storage.LegacySelections;
import com.dank420girl.core.storage.StorageService;
import com.dank420girl.core.text.Colors;
import com.dank420girl.core.text.Messages;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * The main class of both plugins: configuration, effects, menu, commands, storage, permissions and
 * the developer API. What differs between DeathEffects and KillEffects is described by their
 * {@link Variant}; their own main classes only fire their API events.
 * <p>
 * The public methods in the "API" section below are the plugins' API. Call them from the main thread,
 * or on Folia from the thread that owns the player or location.
 */
public abstract class EffectsPlugin extends JavaPlugin {
    private final Variant variant;
    private Trigger trigger;
    private MinecraftVersion version;
    private Scheduler scheduler;
    private Names names;
    private ParticleService particles;
    private SoundService sounds;
    private EffectEngine engine;
    private FireworkService fireworks;
    private ConfigFiles files;
    private StorageService storage;
    private SelectionService selections;
    private LegacySelections legacySelections;
    private EffectMenu menu;

    private volatile Settings settings;
    /** True while config.yml could not be read and its defaults are used. */
    private volatile boolean configFromDefaults;
    private volatile Messages messages;
    private volatile EffectRegistry effects = EffectRegistry.empty();

    private volatile int lastWarnings;
    /** Effect permissions this plugin added, by node. */
    private final Map<String, Permission> registeredPermissions = new HashMap<String, Permission>();
    /** The nodes this plugin added as children of {@code <plugin>.alleffects}. */
    private Set<String> allEffectsChildren = new HashSet<String>();
    private final ConcurrentHashMap<UUID, Long> lastPreview = new ConcurrentHashMap<UUID, Long>();
    private boolean started;

    protected EffectsPlugin(Variant variant) {
        this.variant = variant;
    }

    // --------------------------------------------------------------------------------- for the plugins

    /**
     * Fires the plugin's select event.
     *
     * @return {@code false} if a listener cancelled the change
     */
    protected abstract boolean fireSelectEvent(Player player, String previousEffect, String newEffect);

    /**
     * Fires the plugin's play event before an effect plays for a death.
     *
     * @param victim   who died
     * @param killer   the player who killed them, or {@code null}
     * @param effectId the effect that is about to play
     * @return the effect and place to use, or {@code null} if a listener cancelled the event
     */
    protected abstract EffectPlay firePlayEvent(LivingEntity victim, Player killer, String effectId, Location location);

    // ------------------------------------------------------------------------------------- lifecycle

    @Override
    public final void onEnable() {
        try {
            version = MinecraftVersion.current();
        } catch (IllegalStateException e) {
            getLogger().severe(e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        scheduler = Scheduler.create(this);
        particles = new ParticleService(getLogger(), scheduler, version);
        sounds = new SoundService(getLogger());
        fireworks = new FireworkService(scheduler, getLogger());
        engine = new EffectEngine(this, scheduler, getLogger(), particles, sounds, fireworks);
        names = new Names(NameTables.get(), version, new Predicate<String>() {
            @Override
            public boolean test(String name) {
                return Material.getMaterial(name) != null;
            }
        }, new Predicate<String>() {
            @Override
            public boolean test(String name) {
                return sounds.exists(name);
            }
        }, new Predicate<String>() {
            @Override
            public boolean test(String key) {
                return sounds.keyExists(key);
            }
        }, new Predicate<String>() {
            @Override
            public boolean test(String name) {
                return particles.exists(name);
            }
        });
        files = new ConfigFiles(getDataFolder(), getLogger(), names, version).tokens(variant.tokens());
        trigger = variant.createTrigger(this);
        legacySelections = new LegacySelections(new File(getDataFolder(), "legacy-selections.yml"), getLogger());

        try {
            new LegacyMigration(variant, getLogger(), files, names, legacySelections).migrateIfNeeded();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not convert the configuration of the old version: " + e.getMessage(), e);
        }
        try {
            loadConfiguration(true);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not load the configuration, the plugin is disabled: " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        storage = new StorageService(variant.name(), getLogger(), getDataFolder(), new Predicate<String>() {
            @Override
            public boolean test(String id) {
                return effects.get(id) != null;
            }
        });
        // With a config.yml that cannot be read, the default storage is only a stand-in: selections loaded
        // from it are loaded again once the file is fixed and reloaded.
        storage.open(settings.storage(), !configFromDefaults);
        selections = new SelectionService(storage);
        legacySelections.load();
        menu = new EffectMenu(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new MenuListener(this, menu), this);
        pm.registerEvents(new FireworkListener(fireworks), this);
        pm.registerEvents(trigger, this);
        new FightPlugins(this, trigger, scheduler).start();

        PluginCommand command = getCommand(variant.id());
        if (command != null) {
            EffectsCommand executor = new EffectsCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        registerPermissions();

        for (Player player : com.dank420girl.core.platform.Players.online()) {
            loadSelection(player);
        }
        started = true;
        getLogger().info("Enabled on " + Platform.describe() + ", Minecraft " + version + " with " + effects.size()
                + " effects (particles: " + particles.outputName() + ").");
    }

    @Override
    public final void onDisable() {
        if (!started) {
            return;
        }
        if (menu != null) {
            menu.closeAll();
        }
        if (engine != null) {
            engine.shutdown();
        }
        if (storage != null) {
            storage.shutdown();
        }
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        unregisterPermissions();
        started = false;
    }

    /**
     * Reads config.yml, messages.yml and effects.yml.
     *
     * @param startup on startup a file that is not valid YAML is replaced by the defaults (the file
     *                itself is left alone); on reloads it makes the reload fail instead
     */
    private void loadConfiguration(boolean startup) throws Exception {
        // Every file is read before anything is applied, so a file with mistakes leaves all of the
        // previous settings active.
        ConfigFiles.Loaded config = load("config.yml", ConfigFiles.CONFIG_TEMPLATE, true, true, startup);
        boolean defaultConfig = config.isDefaults();
        ConfigFiles.Loaded messagesFile = load("messages.yml", ConfigFiles.MESSAGES_TEMPLATE, false, true, startup);
        ConfigFiles.Loaded effectsFile = load("effects.yml", ConfigFiles.EFFECTS_TEMPLATE, true, false, startup);

        Problems configProblems = new Problems("config.yml");
        LayerParser configParser = new LayerParser(names, particles, config.namesVersion());
        Settings newSettings = new Settings(config.config(), configParser, variant.defaultTable(), configProblems);
        Runnable applyTriggerSettings = trigger.readSettings(config.config(), configProblems);
        Messages newMessages = new Messages(messagesFile.config(), messagesFile.config().getDefaults(), scheduler);
        Problems messageProblems = new Problems("messages.yml");
        Messages.reportUnknown(messagesFile.config(), messagesFile.config().getDefaults(), messageProblems);

        Problems effectProblems = new Problems("effects.yml");
        LayerParser parser = new LayerParser(names, particles, effectsFile.namesVersion());
        EffectRegistry newEffects = EffectRegistry.load(effectsFile.config(), parser, variant, effectProblems);
        if (!newSettings.defaultEffect().isEmpty() && newEffects.get(newSettings.defaultEffect()) == null) {
            configProblems.warn("settings.default-effect", "there is no effect '" + newSettings.defaultEffect() + "'");
        }
        configProblems.log(getLogger());
        messageProblems.log(getLogger());
        effectProblems.log(getLogger());

        particles.configure(newSettings.viewDistance(), newSettings.particleMultiplier());
        engine.setMaxAnimations(newSettings.maxAnimations());
        applyTriggerSettings.run();
        Debug.setEnabled(newSettings.debug());
        // So server owners can tell that the setting took effect before the first death.
        Debug.log(getLogger(), "Debug messages are on: deaths show here, with the reason why an effect plays or not. "
                + "Turn them off with settings.debug: false.");
        configFromDefaults = defaultConfig;
        settings = newSettings;
        messages = newMessages;
        effects = newEffects;
        lastWarnings = configProblems.size() + messageProblems.size() + effectProblems.size();
    }

    private ConfigFiles.Loaded load(String fileName, String template, boolean translate, boolean withDefaults,
                                    boolean fallBackToDefaults) throws Exception {
        try {
            return files.load(fileName, template, translate, withDefaults);
        } catch (InvalidConfigurationException e) {
            if (!fallBackToDefaults) {
                throw e;
            }
            getLogger().severe(e.getMessage());
            getLogger().severe("Using the default " + fileName + " until the file is fixed; then run /" + variant.alias()
                    + " reload. The file itself was not changed.");
            return files.loadDefaults(template, translate);
        }
    }

    /**
     * Reloads all files. If a file cannot be read, the previous settings stay active.
     *
     * @return {@code true} on success
     */
    public boolean reloadAll(CommandSender sender) {
        try {
            loadConfiguration(false);
        } catch (InvalidConfigurationException e) {
            getLogger().severe("Reload failed, the previous settings stay active. " + e.getMessage());
            messages.send(sender, "reload-failed", "error", firstLine(e.getMessage()));
            return false;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Reload failed, the previous settings stay active: " + e.getMessage(), e);
            messages.send(sender, "reload-failed", "error", firstLine(String.valueOf(e.getMessage())));
            return false;
        }
        storage.open(settings.storage());
        menu.closeAll();
        scheduler.execute(new Runnable() {
            @Override
            public void run() {
                registerPermissions();
            }
        });
        messages.send(sender, "reloaded", "count", effects.size(), "warnings", lastWarnings);
        return true;
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline).trim();
    }

    /** Adds a permission for every effect and makes {@code <plugin>.alleffects} grant all of them. */
    private void registerPermissions() {
        PluginManager pm = getServer().getPluginManager();
        PermissionDefault newDefault = settings.effectPermissionDefault();
        Permission all = allEffectsPermission(pm);
        Map<String, String> wanted = new LinkedHashMap<String, String>();
        for (EffectDefinition effect : effects.all()) {
            if (effect.permission() != null) {
                wanted.put(effect.permission(), "Use the " + Colors.strip(effect.name()) + " effect");
            }
        }
        Iterator<Map.Entry<String, Permission>> registered = registeredPermissions.entrySet().iterator();
        while (registered.hasNext()) {
            Map.Entry<String, Permission> entry = registered.next();
            if (!wanted.containsKey(entry.getKey())) {
                unregister(pm, entry.getValue());
                registered.remove();
            }
        }
        for (Map.Entry<String, String> entry : wanted.entrySet()) {
            Permission existing = registeredPermissions.get(entry.getKey());
            if (existing != null) {
                if (existing.getDefault() != newDefault) {
                    // Updates Bukkit's lists of default permissions, unlike removing and adding it again.
                    existing.setDefault(newDefault);
                }
            } else if (pm.getPermission(entry.getKey()) == null) {
                Permission permission = new Permission(entry.getKey(), entry.getValue(), newDefault);
                pm.addPermission(permission);
                registeredPermissions.put(entry.getKey(), permission);
            }
            // Permissions defined somewhere else (another plugin's plugin.yml) are left as they are.
        }
        for (String child : allEffectsChildren) {
            if (!wanted.containsKey(child)) {
                all.getChildren().remove(child);
            }
        }
        for (String node : wanted.keySet()) {
            all.getChildren().put(node, Boolean.TRUE);
        }
        allEffectsChildren = new HashSet<String>(wanted.keySet());
        all.recalculatePermissibles();
    }

    private Permission allEffectsPermission(PluginManager pm) {
        String node = variant.permission(Perm.ALL_EFFECTS);
        Permission all = pm.getPermission(node);
        if (all == null) {
            all = new Permission(node, "Use every effect", PermissionDefault.OP);
            pm.addPermission(all);
        }
        return all;
    }

    /** Removes the effect permissions this plugin added. */
    private void unregisterPermissions() {
        PluginManager pm = getServer().getPluginManager();
        for (Permission permission : registeredPermissions.values()) {
            unregister(pm, permission);
        }
        registeredPermissions.clear();
        Permission all = pm.getPermission(variant.permission(Perm.ALL_EFFECTS));
        if (all != null) {
            for (String child : allEffectsChildren) {
                all.getChildren().remove(child);
            }
            all.recalculatePermissibles();
        }
        allEffectsChildren = new HashSet<String>();
    }

    private static void unregister(PluginManager pm, Permission permission) {
        // Bukkit keeps removed permissions in its lists of default permissions, so players would keep
        // them; taking the default away first removes them from those lists.
        if (permission.getDefault() != PermissionDefault.FALSE) {
            permission.setDefault(PermissionDefault.FALSE);
        }
        pm.removePermission(permission);
    }

    // ------------------------------------------------------------------------------------- selections

    /** Loads the selection of a player who joined, and hands over a selection saved by name by the old version. */
    public void loadSelection(final Player player) {
        selections.load(player, new StorageService.LoadCallback() {
            @Override
            public void loaded(String effectId, boolean provisional) {
                EffectDefinition effect = effects.get(effectId);
                // A selection saved by name in the old versions is only handed over when the configured storage
                // says the player has none; a stand-in storage cannot tell.
                if (effectId == null && !provisional) {
                    String legacy = legacySelections.isEmpty() ? null : legacySelections.take(player.getName());
                    effect = effects.get(legacy);
                    if (effect != null) {
                        selections.select(player, effect);
                    }
                }
                if (effect != null) {
                    messages.send(player, "effect-restored", "effect", effect.name());
                }
            }
        });
    }

    public void unloadSelection(UUID player) {
        selections.unload(player);
        lastPreview.remove(player);
    }

    /** The effect that would play for the player right now, or {@code null}. */
    public EffectDefinition activeEffect(Player player) {
        Settings s = settings;
        return selections.activeEffect(player, effects, s.defaultEffect(), s.requirePermission());
    }

    /** Why no effect plays for the player ({@link #activeEffect} is {@code null}), for debug messages. */
    protected String whyNoEffect(Player player) {
        UUID uuid = player.getUniqueId();
        if (!selections.isLoaded(uuid)) {
            return "the selection of " + player.getName() + " is still loading";
        }
        String selected = selections.selectedId(uuid);
        String defaultId = settings.defaultEffect();
        EffectDefinition effect = effects.get(selected);
        if (effect == null && !defaultId.isEmpty()) {
            effect = effects.get(defaultId);
        }
        if (effect == null) {
            String reason = selected == null ? "no effect is selected" : "the selected effect '" + selected + "' does not exist";
            return defaultId.isEmpty() ? reason : reason + " and neither does the default effect '" + defaultId + "'";
        }
        return player.getName() + " does not have the permission " + effect.permission() + " for '" + effect.id()
                + "' (settings.require-permission)";
    }

    /** True if the player (or console) may use the effect. */
    public boolean canUse(Permissible who, EffectDefinition effect) {
        return effect.permission() == null || who.hasPermission(effect.permission());
    }

    /**
     * Changes the selection of a player, firing the select event.
     *
     * @param effect the new effect, or {@code null} to clear
     * @param actor  who made the change (receives the confirmation), or {@code null} for API calls
     * @return {@code false} if the event was cancelled
     */
    public boolean select(Player target, EffectDefinition effect, CommandSender actor) {
        String previous = selections.selectedId(target.getUniqueId());
        String next = effect == null ? null : effect.id();
        if (!fireSelectEvent(target, previous, next)) {
            if (actor != null) {
                messages.send(actor, "select-cancelled");
            }
            return false;
        }
        selections.select(target, effect);
        if (actor == null) {
            return true;
        }
        if (effect == null) {
            messages.send(target, "effect-cleared");
            if (actor != target) {
                messages.send(actor, "effect-cleared-other", "player", target.getName());
            }
        } else {
            messages.send(target, "effect-selected", "effect", effect.name());
            if (actor != target) {
                messages.send(actor, "effect-selected-other", "player", target.getName(), "effect", effect.name());
            }
        }
        return true;
    }

    /** The stored selection of an online player, or {@code null}. */
    public String selectedId(UUID player) {
        return selections.selectedId(player);
    }

    /**
     * Plays a preview of the effect in front of the player, respecting the cooldown.
     *
     * @return {@code false} if the preview was refused (the player is told why)
     */
    public boolean preview(final Player player, final EffectDefinition effect) {
        Settings s = settings;
        if (!s.previewsEnabled()) {
            messages.send(player, "previews-disabled");
            return false;
        }
        if (!player.hasPermission(variant.permission(Perm.PREVIEW))) {
            messages.send(player, "no-permission", "permission", variant.permission(Perm.PREVIEW));
            return false;
        }
        if (!s.previewLocked() && !canUse(player, effect)) {
            messages.send(player, "preview-locked");
            return false;
        }
        long now = System.currentTimeMillis();
        if (!player.hasPermission(variant.permission(Perm.PREVIEW_BYPASS)) && s.previewCooldown() > 0) {
            Long last = lastPreview.get(player.getUniqueId());
            long wait = last == null ? 0 : last + s.previewCooldown() * 1000L - now;
            if (wait > 0) {
                messages.send(player, "preview-cooldown", "seconds", (wait + 999) / 1000);
                return false;
            }
        }
        lastPreview.put(player.getUniqueId(), now);
        Location location = player.getLocation();
        Vector direction = location.getDirection().setY(0);
        if (direction.lengthSquared() > 1.0E-6) {
            location.add(direction.normalize().multiply(s.previewDistance()));
        }
        location.setPitch(0);
        SoundSpec sound = s.menu().soundPreview();
        if (sound != null) {
            sounds.play(player, player.getLocation(), sound);
        }
        engine.play(effect, location, player, s.previewPrivate() ? player : null);
        return true;
    }

    /** Opens the effect menu for the player on the given page (1 based). */
    public void openMenu(Player player, int page) {
        menu.open(player, page);
    }

    // ------------------------------------------------------------------------------------------- API

    /**
     * The effect a player selected.
     *
     * @return the effect id, or {@code null} if none is selected, the player is offline or their selection
     *         is still loading after they joined
     */
    public String getSelectedEffectId(UUID player) {
        return selectedId(player);
    }

    /**
     * Selects an effect for a player. Fires the plugin's select event; the effect's permission is not checked.
     *
     * @param effectId the effect id, or {@code null} to clear the selection
     * @return {@code false} if the effect does not exist or the event was cancelled
     */
    public boolean setSelectedEffect(Player player, String effectId) {
        EffectDefinition effect = effectId == null ? null : effects.get(effectId);
        if (effectId != null && effect == null) {
            return false;
        }
        return select(player, effect, null);
    }

    /**
     * Plays any effect at a location. The play event is not fired.
     *
     * @param owner the player the effect belongs to, or {@code null}
     * @return {@code false} if there is no such effect
     */
    public boolean playEffect(String effectId, Location location, Player owner) {
        EffectDefinition effect = effects.get(effectId);
        if (effect == null || location == null || location.getWorld() == null) {
            return false;
        }
        engine.play(effect, location, owner, null);
        return true;
    }

    /** The ids of all effects, in menu order. */
    public Set<String> getEffectIds() {
        return new LinkedHashSet<String>(effects.ids());
    }

    /**
     * Asked by the other plugin of the pair: whether an effect of this plugin would play for this death,
     * not counting the compatibility settings.
     */
    public boolean wouldPlayEffect(LivingEntity victim, Player killer) {
        return started && trigger.wouldPlay(victim, killer);
    }

    /**
     * Asked by the other plugin of the pair: whether this plugin skips its effect when the other plugin's
     * effect plays for the same death.
     */
    public boolean skipsWhenOtherEffectPlays() {
        return started && trigger.skipsWhenOtherEffectPlays();
    }

    // ------------------------------------------------------------------------------------- accessors

    /** Which of the two plugins this is. */
    public Variant variant() {
        return variant;
    }

    public MinecraftVersion minecraftVersion() {
        return version;
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public EffectRegistry effects() {
        return effects;
    }

    public EffectEngine engine() {
        return engine;
    }

    public SoundService sounds() {
        return sounds;
    }

    public ParticleService particles() {
        return particles;
    }

    public StorageService storage() {
        return storage;
    }

    public Names names() {
        return names;
    }

    /** True once the plugin finished starting. */
    public boolean isStarted() {
        return started;
    }
}

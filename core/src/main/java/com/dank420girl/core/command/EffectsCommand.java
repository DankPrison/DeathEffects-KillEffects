package com.dank420girl.core.command;

import com.dank420girl.core.EffectsPlugin;
import com.dank420girl.core.Perm;
import com.dank420girl.core.Variant;
import com.dank420girl.core.effect.EffectDefinition;
import com.dank420girl.core.platform.Platform;
import com.dank420girl.core.platform.Players;
import com.dank420girl.core.text.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The /deatheffects and /killeffects command. Every sub-command has its own permission (see {@link Perm});
 * commands about other players need the matching {@code .others} permission.
 */
public final class EffectsCommand implements org.bukkit.command.CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "help", "list", "select", "preview", "clear", "info", "open", "reload", "version");

    private final EffectsPlugin plugin;

    public EffectsCommand(EffectsPlugin plugin) {
        this.plugin = plugin;
    }

    private Variant id() {
        return plugin.variant();
    }

    private Messages messages() {
        return plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.isStarted()) {
            sender.sendMessage(id().name() + " is still starting, try again in a moment.");
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player) {
                openOwnMenu((Player) sender, 1);
            } else {
                help(sender, label);
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "help":
                help(sender, label);
                return true;
            case "list":
                if (require(sender, Perm.LIST)) {
                    list(sender);
                }
                return true;
            case "select":
                if (rest.length == 0) {
                    usage(sender, label, "select <effect> [player]");
                } else {
                    select(sender, rest[0], rest.length > 1 ? rest[1] : null);
                }
                return true;
            case "preview":
                if (rest.length == 0) {
                    usage(sender, label, "preview <effect>");
                } else {
                    preview(sender, rest[0]);
                }
                return true;
            case "clear":
                clear(sender, rest.length > 0 ? rest[0] : null);
                return true;
            case "info":
                info(sender, rest.length > 0 ? rest[0] : null);
                return true;
            case "open":
                open(sender, label, rest);
                return true;
            case "reload":
                if (require(sender, Perm.RELOAD)) {
                    plugin.reloadAll(sender);
                }
                return true;
            case "version":
                if (require(sender, Perm.VERSION)) {
                    version(sender);
                }
                return true;
            default:
                break;
        }
        // Shortcuts kept from the old versions: "/de <effect> [player]" and "/de <page>".
        if (plugin.effects().get(sub) != null) {
            select(sender, sub, rest.length > 0 ? rest[0] : null);
            return true;
        }
        if (sub.matches("\\d{1,3}") && sender instanceof Player) {
            openOwnMenu((Player) sender, Integer.parseInt(sub));
            return true;
        }
        messages().send(sender, "unknown-command", "command", label);
        return true;
    }

    private boolean has(CommandSender sender, Perm permission) {
        return sender.hasPermission(id().permission(permission));
    }

    /** True if the sender has the permission; otherwise tells them which one is missing. */
    private boolean require(CommandSender sender, Perm permission) {
        if (has(sender, permission)) {
            return true;
        }
        messages().send(sender, "no-permission", "permission", id().permission(permission));
        return false;
    }

    private void openOwnMenu(Player player, int page) {
        if (require(player, Perm.GUI)) {
            plugin.openMenu(player, page);
        }
    }

    private void usage(CommandSender sender, String label, String usage) {
        messages().send(sender, "usage", "usage", "/" + label + " " + usage);
    }

    /**
     * The player a command is about: the named player, or the sender if no name is given. Checks the
     * permission for doing it to oneself or to others, and tells the sender if something is wrong.
     *
     * @return the player, or {@code null} if the command cannot go on
     */
    private Player target(CommandSender sender, String name, Perm own, Perm others) {
        if (name == null) {
            if (!(sender instanceof Player)) {
                messages().send(sender, "players-only");
                return null;
            }
            return require(sender, own) ? (Player) sender : null;
        }
        Player target = Players.find(name);
        if (target != null && target == sender) {
            return require(sender, own) ? target : null;
        }
        if (!require(sender, others)) {
            return null;
        }
        if (target == null) {
            messages().send(sender, "player-not-found", "input", name);
        }
        return target;
    }

    private EffectDefinition effect(CommandSender sender, String name, String label) {
        EffectDefinition effect = plugin.effects().get(name);
        if (effect == null) {
            messages().send(sender, "unknown-effect", "input", name, "command", label);
        }
        return effect;
    }

    private void select(CommandSender sender, String effectName, String playerName) {
        Player target = target(sender, playerName, Perm.SELECT, Perm.SELECT_OTHERS);
        if (target == null) {
            return;
        }
        EffectDefinition effect = effect(sender, effectName, id().alias());
        if (effect == null) {
            return;
        }
        boolean forOther = target != sender;
        if (!forOther && !plugin.canUse(target, effect)) {
            messages().send(sender, "no-permission-effect", "effect", effect.name(), "permission",
                    effect.permission() == null ? "" : effect.permission());
            return;
        }
        if (plugin.select(target, effect, sender) && forOther && plugin.settings().requirePermission()
                && !plugin.canUse(target, effect)) {
            messages().send(sender, "target-lacks-permission", "player", target.getName(),
                    "permission", effect.permission(), "effect", effect.name());
        }
    }

    private void preview(CommandSender sender, String effectName) {
        if (!(sender instanceof Player)) {
            messages().send(sender, "players-only");
            return;
        }
        if (!require(sender, Perm.PREVIEW)) {
            return;
        }
        EffectDefinition effect = effect(sender, effectName, id().alias());
        if (effect != null) {
            plugin.preview((Player) sender, effect);
        }
    }

    private void clear(CommandSender sender, String playerName) {
        Player target = target(sender, playerName, Perm.CLEAR, Perm.CLEAR_OTHERS);
        if (target != null) {
            plugin.select(target, null, sender);
        }
    }

    private void info(CommandSender sender, String playerName) {
        Player target = target(sender, playerName, Perm.INFO, Perm.INFO_OTHERS);
        if (target == null) {
            return;
        }
        String selected = plugin.selectedId(target.getUniqueId());
        EffectDefinition effect = plugin.effects().get(selected);
        EffectDefinition active = plugin.activeEffect(target);
        boolean self = target == sender;
        if (effect == null && active == null) {
            messages().send(sender, self ? "info-self-none" : "info-other-none", "player", target.getName());
            return;
        }
        EffectDefinition shown = effect != null ? effect : active;
        messages().send(sender, self ? "info-self" : "info-other", "player", target.getName(), "effect", shown.name(),
                "id", shown.id());
        if (effect != null && active == null) {
            messages().send(sender, "info-not-playing", "player", target.getName(), "permission",
                    effect.permission() == null ? "" : effect.permission());
        }
    }

    private void open(CommandSender sender, String label, String[] rest) {
        if (rest.length == 0) {
            if (sender instanceof Player) {
                openOwnMenu((Player) sender, 1);
            } else {
                usage(sender, label, "open <player> [page]");
            }
            return;
        }
        int page = 1;
        if (rest.length > 1) {
            try {
                page = Integer.parseInt(rest[1]);
            } catch (NumberFormatException e) {
                usage(sender, label, "open <player> [page]");
                return;
            }
        }
        Player target = target(sender, rest[0], Perm.GUI, Perm.GUI_OTHERS);
        if (target == null) {
            return;
        }
        plugin.openMenu(target, page);
        if (target != sender) {
            messages().send(sender, "menu-opened-other", "player", target.getName());
        }
    }

    private void list(CommandSender sender) {
        List<String> lines = new ArrayList<String>(messages().lines("list-header", "count", plugin.effects().size()));
        String selected = sender instanceof Player ? plugin.selectedId(((Player) sender).getUniqueId()) : null;
        for (EffectDefinition effect : plugin.effects().all()) {
            String key = effect.id().equals(selected) ? "list-entry-selected"
                    : plugin.canUse(sender, effect) ? "list-entry-available" : "list-entry-locked";
            lines.addAll(messages().lines(key, "effect", effect.name(), "id", effect.id()));
        }
        messages().sendLines(sender, lines);
    }

    /** The commands the sender may use. */
    private void help(CommandSender sender, String label) {
        if (!require(sender, Perm.HELP)) {
            return;
        }
        List<String> lines = new ArrayList<String>(messages().lines("help-header", "plugin", id().name(),
                "version", plugin.getDescription().getVersion()));
        Object[][] entries = {
                {"open", Perm.GUI},
                {"list", Perm.LIST},
                {"select", Perm.SELECT},
                {"preview", Perm.PREVIEW},
                {"clear", Perm.CLEAR},
                {"info", Perm.INFO},
                {"select-other", Perm.SELECT_OTHERS},
                {"clear-other", Perm.CLEAR_OTHERS},
                {"open-other", Perm.GUI_OTHERS},
                {"info-other", Perm.INFO_OTHERS},
                {"reload", Perm.RELOAD},
                {"version", Perm.VERSION},
        };
        for (Object[] entry : entries) {
            if (has(sender, (Perm) entry[1])) {
                lines.addAll(messages().lines("help." + entry[0], "command", label));
            }
        }
        messages().sendLines(sender, lines);
    }

    private void version(CommandSender sender) {
        messages().send(sender, "version",
                "plugin", id().name(),
                "version", plugin.getDescription().getVersion(),
                "server", Platform.describe(),
                "minecraft", plugin.minecraftVersion(),
                "java", System.getProperty("java.version"),
                "particles", plugin.particles().outputName(),
                "storage", plugin.storage().describe(),
                "effects", plugin.effects().size(),
                "animations", plugin.engine().runningAnimations());
    }

    // ---------------------------------------------------------------------------------- tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!plugin.isStarted()) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<String>();
            for (String sub : SUBCOMMANDS) {
                if (mayUse(sender, sub)) {
                    options.add(sub);
                }
            }
            options.addAll(selectableEffects(sender));
            return filter(options, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "select":
                    return filter(selectableEffects(sender), args[1]);
                case "preview":
                    return has(sender, Perm.PREVIEW) ? filter(plugin.effects().ids(), args[1]) : empty();
                case "clear":
                    return playerNames(sender, Perm.CLEAR_OTHERS, args[1]);
                case "info":
                    return playerNames(sender, Perm.INFO_OTHERS, args[1]);
                case "open":
                    return playerNames(sender, Perm.GUI_OTHERS, args[1]);
                default:
                    return plugin.effects().get(sub) != null ? playerNames(sender, Perm.SELECT_OTHERS, args[1]) : empty();
            }
        }
        if (args.length == 3 && sub.equals("select")) {
            return playerNames(sender, Perm.SELECT_OTHERS, args[2]);
        }
        return empty();
    }

    private boolean mayUse(CommandSender sender, String sub) {
        switch (sub) {
            case "help":
                return has(sender, Perm.HELP);
            case "list":
                return has(sender, Perm.LIST);
            case "select":
                return has(sender, Perm.SELECT) || has(sender, Perm.SELECT_OTHERS);
            case "preview":
                return has(sender, Perm.PREVIEW);
            case "clear":
                return has(sender, Perm.CLEAR) || has(sender, Perm.CLEAR_OTHERS);
            case "info":
                return has(sender, Perm.INFO) || has(sender, Perm.INFO_OTHERS);
            case "open":
                return has(sender, Perm.GUI) || has(sender, Perm.GUI_OTHERS);
            case "reload":
                return has(sender, Perm.RELOAD);
            case "version":
                return has(sender, Perm.VERSION);
            default:
                return false;
        }
    }

    /** Effects the sender may pick by command: all of them for admins, otherwise the ones they may use. */
    private List<String> selectableEffects(CommandSender sender) {
        boolean others = has(sender, Perm.SELECT_OTHERS);
        if (!others && !has(sender, Perm.SELECT)) {
            return empty();
        }
        List<String> ids = new ArrayList<String>();
        for (EffectDefinition effect : plugin.effects().all()) {
            if (others || plugin.canUse(sender, effect)) {
                ids.add(effect.id());
            }
        }
        return ids;
    }

    private List<String> playerNames(CommandSender sender, Perm others, String prefix) {
        return has(sender, others) ? Players.namesStartingWith(prefix) : empty();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<String>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    private static List<String> empty() {
        return Collections.emptyList();
    }
}

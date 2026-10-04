package com.dank420girl.core.menu;

import com.dank420girl.core.EffectsPlugin;
import com.dank420girl.core.config.MenuSettings;
import com.dank420girl.core.effect.EffectDefinition;
import com.dank420girl.core.platform.MinecraftVersion;
import com.dank420girl.core.text.Colors;
import com.dank420girl.core.text.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The paginated effect menu. Left click selects an effect, right click previews it.
 */
public final class EffectMenu {
    /** Inventory titles were limited to 32 characters before 1.9. */
    private static final int LEGACY_TITLE_LIMIT = 32;

    private final EffectsPlugin plugin;
    /** The menus that are open, by player. */
    private final Map<UUID, Inventory> open = new ConcurrentHashMap<UUID, Inventory>();

    public EffectMenu(EffectsPlugin plugin) {
        this.plugin = plugin;
    }

    /** The effects the player gets to see in the menu, in menu order. */
    List<EffectDefinition> visibleEffects(Player player) {
        boolean hideLocked = plugin.settings().menu().hideLocked();
        List<EffectDefinition> list = new ArrayList<EffectDefinition>();
        for (EffectDefinition effect : plugin.effects().all()) {
            if (!hideLocked || plugin.canUse(player, effect)) {
                list.add(effect);
            }
        }
        return list;
    }

    /** Opens the menu on the player's own thread (page numbers start at 1). */
    public void open(final Player player, final int page) {
        plugin.scheduler().executeFor(player, new Runnable() {
            @Override
            public void run() {
                openNow(player, page);
            }
        });
    }

    private void openNow(Player player, int requestedPage) {
        MenuSettings settings = plugin.settings().menu();
        Messages messages = plugin.messages();
        List<EffectDefinition> effects = visibleEffects(player);
        int[] slots = settings.effectSlots();
        int perPage = Math.max(1, slots.length);
        int pages = Math.max(1, (effects.size() + perPage - 1) / perPage);
        int page = Math.max(1, Math.min(pages, requestedPage));

        MenuHolder holder = new MenuHolder(player.getUniqueId(), page);
        String title = Colors.colorize(settings.title().replace("{page}", String.valueOf(page))
                .replace("{pages}", String.valueOf(pages)));
        if (MinecraftVersion.current().isBefore(1, 9) && title.length() > LEGACY_TITLE_LIMIT) {
            title = title.substring(0, LEGACY_TITLE_LIMIT);
        }
        Inventory inventory = Bukkit.createInventory(holder, settings.rows() * 9, title);
        holder.setInventory(inventory);

        MenuSettings.Button filler = settings.filler();
        if (filler.enabled()) {
            org.bukkit.inventory.ItemStack fillerItem = Items.create(filler.item(), Colors.colorize(filler.name()),
                    Colors.colorize(filler.lore()), false);
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, fillerItem);
            }
        }
        for (int slot : slots) {
            inventory.setItem(slot, null);
        }

        String selected = plugin.selectedId(player.getUniqueId());
        EffectDefinition current = plugin.effects().get(selected);
        int start = (page - 1) * perPage;
        for (int i = 0; i < perPage && start + i < effects.size(); i++) {
            EffectDefinition effect = effects.get(start + i);
            boolean usable = plugin.canUse(player, effect);
            boolean isSelected = effect.id().equals(selected);
            List<String> lore = new ArrayList<String>(effect.lore());
            List<String> status = isSelected ? settings.loreSelected() : usable ? settings.loreAvailable() : settings.loreLocked();
            for (String line : status) {
                lore.add(Colors.colorize(messages.replace(line, "effect", effect.name(), "id", effect.id())));
            }
            boolean showLockedIcon = !usable && settings.lockedIcon() != null;
            inventory.setItem(slots[i], Items.create(showLockedIcon ? settings.lockedIcon() : effect.icon(), effect.name(),
                    lore, isSelected && settings.glowSelected()));
            holder.putEffect(slots[i], effect.id());
        }

        String currentName = current == null ? messages.get("none") : current.name();
        button(inventory, holder, settings.clear(), MenuHolder.Action.CLEAR, page, pages, currentName);
        button(inventory, holder, settings.current(), MenuHolder.Action.NONE, page, pages, currentName);
        if (page > 1) {
            button(inventory, holder, settings.previousPage(), MenuHolder.Action.PREVIOUS_PAGE, page, pages, currentName);
        }
        if (page < pages) {
            button(inventory, holder, settings.nextPage(), MenuHolder.Action.NEXT_PAGE, page, pages, currentName);
        }

        player.openInventory(inventory);
        open.put(player.getUniqueId(), inventory);
    }

    private void button(Inventory inventory, MenuHolder holder, MenuSettings.Button button, MenuHolder.Action action,
                        int page, int pages, String currentEffect) {
        if (!button.enabled() || button.slot() < 0 || button.slot() >= inventory.getSize()) {
            return;
        }
        Messages messages = plugin.messages();
        Object[] placeholders = {"page", page, "pages", pages, "previous", page - 1, "next", page + 1, "effect", currentEffect};
        String name = Colors.colorize(messages.replace(button.name(), placeholders));
        List<String> lore = new ArrayList<String>();
        for (String line : button.lore()) {
            lore.add(Colors.colorize(messages.replace(line, placeholders)));
        }
        inventory.setItem(button.slot(), Items.create(button.item(), name, lore, false));
        holder.putAction(button.slot(), action);
    }

    void closed(UUID player, Inventory inventory) {
        open.remove(player, inventory);
    }

    /**
     * Closes every open menu, for example before a reload changes the effects or when the plugin is
     * disabled. The menus are emptied right away, so nothing can be taken out of them even where closing
     * has to wait for the player's thread (Folia).
     */
    public void closeAll() {
        for (Map.Entry<UUID, Inventory> entry : new ArrayList<Map.Entry<UUID, Inventory>>(open.entrySet())) {
            if (!open.remove(entry.getKey(), entry.getValue())) {
                continue;
            }
            try {
                entry.getValue().clear();
            } catch (RuntimeException ignored) {
                // closing below still helps
            }
            final Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            plugin.scheduler().executeFor(player, new Runnable() {
                @Override
                public void run() {
                    player.closeInventory();
                }
            });
        }
    }
}

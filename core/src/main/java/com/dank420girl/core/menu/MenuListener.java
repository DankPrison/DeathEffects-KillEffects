package com.dank420girl.core.menu;

import com.dank420girl.core.EffectsPlugin;
import com.dank420girl.core.Perm;
import com.dank420girl.core.config.MenuSettings;
import com.dank420girl.core.effect.EffectDefinition;
import com.dank420girl.core.effect.SoundSpec;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles clicks in the effect menu. Every click is cancelled so items can never be taken out or
 * put in; menu actions run on the next tick, as Bukkit asks for inventory changes.
 */
public final class MenuListener implements Listener {
    /** A click that was not handled within this time (the player left, for example) no longer blocks clicks. */
    private static final long CLICK_TIMEOUT_MILLIS = 2000;

    private final EffectsPlugin plugin;
    private final EffectMenu menu;
    /** Players whose last click is still waiting to be handled, with the time of the click. */
    private final ConcurrentHashMap<UUID, Long> pending = new ConcurrentHashMap<UUID, Long>();

    public MenuListener(EffectsPlugin plugin, EffectMenu menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return; // the player's own inventory
        }
        ClickType click = event.getClick();
        final boolean right = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
        final boolean left = click == ClickType.LEFT || click == ClickType.SHIFT_LEFT;
        if (!left && !right) {
            return;
        }
        final Player player = (Player) event.getWhoClicked();
        final MenuHolder holder = (MenuHolder) top.getHolder();
        final String effectId = holder.effectAt(slot);
        final MenuHolder.Action action = holder.actionAt(slot);
        if (effectId == null && (action == null || action == MenuHolder.Action.NONE)) {
            return;
        }
        // One click at a time: further clicks before it is handled are ignored.
        final UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long since = pending.putIfAbsent(uuid, now);
        if (since != null) {
            if (now - since < CLICK_TIMEOUT_MILLIS) {
                return;
            }
            pending.put(uuid, now);
        }
        plugin.scheduler().runForLater(player, new Runnable() {
            @Override
            public void run() {
                try {
                    if (effectId != null) {
                        clickEffect(player, holder, effectId, right);
                    } else {
                        clickAction(player, holder, action);
                    }
                } finally {
                    pending.remove(uuid);
                }
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof MenuHolder)) {
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            menu.closed(event.getPlayer().getUniqueId(), event.getInventory());
            pending.remove(event.getPlayer().getUniqueId());
        }
    }

    /**
     * Closes the menus while the plugin is still enabled: once it is disabled, tasks for players can
     * no longer be scheduled on Folia, and this listener stops protecting the menus.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            menu.closeAll();
        }
    }

    private void clickEffect(Player player, MenuHolder holder, String effectId, boolean preview) {
        EffectDefinition effect = plugin.effects().get(effectId);
        MenuSettings settings = plugin.settings().menu();
        if (effect == null) {
            menu.open(player, holder.page());
            return;
        }
        if (preview) {
            if (plugin.preview(player, effect)) {
                player.closeInventory();
            } else {
                play(player, settings.soundLocked());
            }
            return;
        }
        if (!plugin.canUse(player, effect)) {
            plugin.messages().send(player, "no-permission-effect", "effect", effect.name());
            play(player, settings.soundLocked());
            return;
        }
        if (plugin.select(player, effect, player)) {
            play(player, settings.soundSelect());
        }
        if (settings.closeOnSelect()) {
            player.closeInventory();
        } else {
            menu.open(player, holder.page());
        }
    }

    private void clickAction(Player player, MenuHolder holder, MenuHolder.Action action) {
        MenuSettings settings = plugin.settings().menu();
        switch (action) {
            case CLEAR:
                if (!player.hasPermission(plugin.variant().permission(Perm.CLEAR))) {
                    plugin.messages().send(player, "no-permission", "permission", plugin.variant().permission(Perm.CLEAR));
                    play(player, settings.soundLocked());
                    return;
                }
                if (plugin.select(player, null, player)) {
                    play(player, settings.soundClear());
                }
                if (settings.closeOnSelect()) {
                    player.closeInventory();
                } else {
                    menu.open(player, holder.page());
                }
                return;
            case PREVIOUS_PAGE:
                play(player, settings.soundPage());
                menu.open(player, holder.page() - 1);
                return;
            case NEXT_PAGE:
                play(player, settings.soundPage());
                menu.open(player, holder.page() + 1);
                return;
            default:
        }
    }

    private void play(Player player, SoundSpec sound) {
        if (sound != null) {
            plugin.sounds().play(player, player.getLocation(), sound);
        }
    }
}

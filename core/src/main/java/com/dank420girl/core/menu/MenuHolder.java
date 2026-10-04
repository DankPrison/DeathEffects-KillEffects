package com.dank420girl.core.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Marks an inventory as an effect menu of this plugin and remembers what each slot does.
 * <p>
 * Because DeathEffects and KillEffects each carry their own copy of this class, an
 * {@code instanceof} check tells their menus apart without comparing titles.
 */
public final class MenuHolder implements InventoryHolder {
    /** What clicking a slot does. */
    enum Action {
        CLEAR, PREVIOUS_PAGE, NEXT_PAGE, NONE
    }

    private final UUID viewer;
    private final int page;
    private final Map<Integer, String> effectSlots = new HashMap<Integer, String>();
    private final Map<Integer, Action> actionSlots = new HashMap<Integer, Action>();
    private Inventory inventory;

    MenuHolder(UUID viewer, int page) {
        this.viewer = viewer;
        this.page = page;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    UUID viewer() {
        return viewer;
    }

    int page() {
        return page;
    }

    void putEffect(int slot, String effectId) {
        effectSlots.put(slot, effectId);
    }

    void putAction(int slot, Action action) {
        actionSlots.put(slot, action);
    }

    /** The effect shown in the slot, or {@code null}. */
    String effectAt(int slot) {
        return effectSlots.get(slot);
    }

    Action actionAt(int slot) {
        Action action = actionSlots.get(slot);
        return action == null ? Action.NONE : action;
    }
}

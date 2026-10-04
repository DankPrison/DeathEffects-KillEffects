package com.dank420girl.core.menu;

import com.dank420girl.core.names.Names.ItemName;
import com.dank420girl.core.platform.MinecraftVersion;
import com.dank420girl.core.platform.Reflect;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates menu items on every version: hides attribute and potion texts where possible and makes
 * items glow without showing an enchantment.
 */
final class Items {
    private static final String[] HIDDEN_FLAGS = {
            "HIDE_ATTRIBUTES", "HIDE_POTION_EFFECTS", "HIDE_ADDITIONAL_TOOLTIP", "HIDE_UNBREAKABLE",
            "HIDE_DYE", "HIDE_ARMOR_TRIM", "HIDE_STORED_ENCHANTS"
    };

    private static final Class<?> ITEM_FLAG = Reflect.findClass("org.bukkit.inventory.ItemFlag");
    private static final Method ADD_ITEM_FLAGS = ITEM_FLAG == null ? null
            : Reflect.findMethod(ItemMeta.class, "addItemFlags", Array.newInstance(ITEM_FLAG, 0).getClass());
    private static final Method GLINT_OVERRIDE = Reflect.findMethod(ItemMeta.class, "setEnchantmentGlintOverride", Boolean.class);
    private static final Method IS_ITEM = Reflect.findMethod(Material.class, "isItem");
    private static final Method ENCHANTMENT_BY_NAME = Reflect.findMethod(Enchantment.class, "getByName", String.class);

    private Items() {
    }

    /** The material of the item, or a fallback if the server has no such item. */
    static Material material(ItemName item) {
        Material material = item == null ? null : Material.getMaterial(item.name());
        if (material == null || (IS_ITEM != null && !(Boolean) Reflect.invoke(IS_ITEM, material))) {
            material = Material.getMaterial("PAPER");
        }
        return material;
    }

    static ItemStack create(ItemName item, String name, List<String> lore, boolean glow) {
        Material material = material(item);
        ItemStack stack = MinecraftVersion.current().isLegacy() && item != null && material.name().equals(item.name())
                ? new ItemStack(material, 1, (short) item.data())
                : new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (name != null) {
            meta.setDisplayName(name);
        }
        meta.setLore(lore == null || lore.isEmpty() ? null : new ArrayList<String>(lore));
        hideDetails(meta);
        if (glow) {
            glow(meta);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private static void hideDetails(ItemMeta meta) {
        if (ADD_ITEM_FLAGS == null) {
            return;
        }
        List<Object> flags = new ArrayList<Object>();
        for (String flag : HIDDEN_FLAGS) {
            Object value = Reflect.enumConstant(ITEM_FLAG, flag);
            if (value != null) {
                flags.add(value);
            }
        }
        addFlags(meta, flags);
    }

    private static void addFlags(ItemMeta meta, List<Object> flags) {
        Object array = Array.newInstance(ITEM_FLAG, flags.size());
        for (int i = 0; i < flags.size(); i++) {
            Array.set(array, i, flags.get(i));
        }
        try {
            ADD_ITEM_FLAGS.invoke(meta, array);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // cosmetic only
        }
    }

    private static void glow(ItemMeta meta) {
        if (GLINT_OVERRIDE != null) {
            try {
                GLINT_OVERRIDE.invoke(meta, Boolean.TRUE);
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // fall back to an enchantment
            }
        }
        // Without item flags (1.7) the enchantment would be listed in the lore.
        if (ADD_ITEM_FLAGS == null || ENCHANTMENT_BY_NAME == null) {
            return;
        }
        Object enchantment = Reflect.invoke(ENCHANTMENT_BY_NAME, null, "DURABILITY");
        if (enchantment == null) {
            enchantment = Reflect.invoke(ENCHANTMENT_BY_NAME, null, "UNBREAKING");
        }
        if (enchantment == null) {
            return;
        }
        meta.addEnchant((Enchantment) enchantment, 1, true);
        List<Object> flags = new ArrayList<Object>();
        Object hideEnchants = Reflect.enumConstant(ITEM_FLAG, "HIDE_ENCHANTS");
        if (hideEnchants != null) {
            flags.add(hideEnchants);
            addFlags(meta, flags);
        }
    }
}

package com.fishrework.util;

import com.fishrework.manager.ItemManager;
import com.fishrework.model.AutoSellMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

public final class AutoSellUtil {

    private static final Set<Material> SAFE_FISH = EnumSet.of(
            Material.COD,
            Material.SALMON,
            Material.TROPICAL_FISH,
            Material.PUFFERFISH
    );

    private AutoSellUtil() {
    }

    public static double getAutoSellPrice(ItemManager itemManager, ItemStack item, AutoSellMode mode) {
        if (itemManager == null || item == null || mode == null || mode == AutoSellMode.OFF) {
            return 0;
        }
        if (mode == AutoSellMode.OTHER && !isSafeAutoSellItem(itemManager, item)) {
            return 0;
        }
        return itemManager.getSellPrice(item);
    }

    public static boolean isSafeAutoSellItem(ItemManager itemManager, ItemStack item) {
        if (item == null) {
            return false;
        }
        if (SAFE_FISH.contains(item.getType())) {
            return true;
        }
        return itemManager != null && itemManager.isOtherVendorSellMaterial(item);
    }
}

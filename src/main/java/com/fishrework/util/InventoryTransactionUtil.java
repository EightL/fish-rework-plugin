package com.fishrework.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Map;

public final class InventoryTransactionUtil {

    private InventoryTransactionUtil() {
    }

    public static void restoreOrDrop(Player player, Collection<ItemStack> items) {
        if (player == null || items == null || items.isEmpty()) {
            return;
        }
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }
}

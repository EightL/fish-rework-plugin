package com.fishrework.model;

import org.bukkit.inventory.ItemStack;

public record CustomShopListing(
        String shopId,
        int slotIndex,
        ItemStack item,
        long price
) {
}

package com.fishrework.manager;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class TreasureHolder implements InventoryHolder {
    @Override
    public @NotNull Inventory getInventory() {
        return null; // The inventory will be created with this holder
    }
}

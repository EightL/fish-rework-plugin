package com.fishrework.util;

import com.fishrework.FishRework;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BagUtils {

    private static final Set<Material> FISH_BAG_BASE_ITEMS = Set.of(
            Material.COD,
            Material.SALMON,
            Material.TROPICAL_FISH,
            Material.PUFFERFISH,
            Material.INK_SAC
    );

    private BagUtils() {
    }

    public static boolean hasLavaBagInInventory(FishRework plugin, Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (plugin.getItemManager().isLavaBag(item)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasFishBagInInventory(FishRework plugin, Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (plugin.getItemManager().isFishBag(item)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAllowedInFishBag(FishRework plugin, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (FISH_BAG_BASE_ITEMS.contains(item.getType())) {
            return true;
        }
        if (!item.hasItemMeta()) {
            return false;
        }

        if (item.getItemMeta().getPersistentDataContainer().has(
                plugin.getItemManager().TREASURE_TYPE_KEY,
                PersistentDataType.STRING
        )) {
            return true;
        }

        return item.getItemMeta().getPersistentDataContainer().has(
                plugin.getItemManager().CUSTOM_ITEM_KEY,
                PersistentDataType.STRING
        );
    }

    public static boolean shouldCancelBagInteraction(InventoryClickEvent event,
                                                     boolean currentIsBag,
                                                     boolean cursorIsBag) {
        if (!currentIsBag && !cursorIsBag) {
            return false;
        }
        if (event.isRightClick() || event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
            return true;
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean cursorHasItem = cursor != null && !cursor.getType().isAir();
        boolean currentHasItem = current != null && !current.getType().isAir();
        return (currentIsBag && cursorHasItem) || (cursorIsBag && currentHasItem);
    }

    public static int recoverVanillaBundleContents(Player player, ItemStack bag) {
        if (bag == null) {
            return 0;
        }
        if (!(bag.getItemMeta() instanceof BundleMeta meta)) {
            return 0;
        }

        List<ItemStack> stored = meta.getItems();
        if (stored == null || stored.isEmpty()) {
            return 0;
        }

        int recoveredCount = 0;
        for (ItemStack stack : stored) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            recoveredCount += stack.getAmount();

            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack.clone());
            for (ItemStack remaining : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
            }
        }

        meta.setItems(new ArrayList<>());
        bag.setItemMeta(meta);
        return recoveredCount;
    }
}
package com.fishrework.listener;

import com.fishrework.FishRework;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

public class BlockPlaceListener implements Listener {

    private final FishRework plugin;

    public BlockPlaceListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || !item.hasItemMeta()) return;

        // 1. Check for Custom Materials (e.g., Dread Soul, Ironclad Plate)
        if (item.getItemMeta().getPersistentDataContainer().has(plugin.getItemManager().CUSTOM_ITEM_KEY, PersistentDataType.STRING)) {
            event.setCancelled(true);
            return;
        }

        // 2. Check for Artifacts (unless it's a special placeable one like Display Case)
        // Note: DisplayCase and Totem have their own keys and listeners that might allow/handle placement.
        // Artifacts generally shouldn't be placeable.
        if (item.getItemMeta().getPersistentDataContainer().has(plugin.getItemManager().ARTIFACT_KEY, PersistentDataType.STRING)) {
            // Check if it's NOT a display case (just in case they share the key, which they don't seem to)
            if (!item.getItemMeta().getPersistentDataContainer().has(plugin.getItemManager().DISPLAY_CASE_KEY, PersistentDataType.BYTE)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawnEggInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();

        // Prevent protected custom items from using vanilla throw/consume behavior.
        if (isProtectedThrowableCustomItem(item)) {
            event.setCancelled(true);
            return;
        }

        if (!isProtectedCustomSpawnEgg(item)) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawnEggInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getHand() == EquipmentSlot.OFF_HAND
                ? event.getPlayer().getInventory().getItemInOffHand()
                : event.getPlayer().getInventory().getItemInMainHand();
        if (isProtectedThrowableCustomItem(item) || isProtectedCustomSpawnEgg(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        if (item.getItemMeta().getPersistentDataContainer().has(
                plugin.getItemManager().CUSTOM_ITEM_KEY, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    private boolean isProtectedCustomSpawnEgg(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (!item.getType().name().endsWith("_SPAWN_EGG")) return false;

        return item.getItemMeta().getPersistentDataContainer().has(
                plugin.getItemManager().CUSTOM_ITEM_KEY,
                PersistentDataType.STRING
        );
    }

    private boolean isProtectedThrowableCustomItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        String typeName = item.getType().name();
        boolean isThrowable = item.getType() == Material.EXPERIENCE_BOTTLE
                || item.getType() == Material.ENDER_PEARL
                || item.getType() == Material.ENDER_EYE
                || item.getType() == Material.SNOWBALL
                || item.getType() == Material.SPLASH_POTION
                || item.getType() == Material.LINGERING_POTION
                || typeName.endsWith("_POTION");
        if (!isThrowable) return false;

        return plugin.getItemManager().isBait(item)
                || plugin.getItemManager().getCustomItemId(item) != null;
    }
}

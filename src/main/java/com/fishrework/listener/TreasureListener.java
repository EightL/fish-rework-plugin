package com.fishrework.listener;

import com.fishrework.FishRework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import com.fishrework.manager.TreasureHolder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.Map;

public class TreasureListener implements Listener {

    private final FishRework plugin;

    public TreasureListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Handle only right clicks
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        ItemStack mainItem = event.getPlayer().getInventory().getItemInMainHand();
        boolean mainIsTreasure = isTreasure(mainItem);

        if (mainIsTreasure) {
            // If main hand is treasure, cancel EVERYTHING to stop off-hand interaction
            event.setCancelled(true);
            
            // Only process opening logic once (when the HAND event fires)
            if (event.getHand() == EquipmentSlot.HAND) {
                plugin.getTreasureManager().openTreasure(event.getPlayer(), mainItem.clone());
                mainItem.subtract(1);
            }
            return;
        }

        // If main hand is not treasure, check if off-hand is
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            ItemStack offItem = event.getItem();
            if (isTreasure(offItem)) {
                event.setCancelled(true);
                plugin.getTreasureManager().openTreasure(event.getPlayer(), offItem.clone());
                offItem.subtract(1);
            }
        }
    }

    private boolean isTreasure(ItemStack item) {
        return item != null && item.hasItemMeta() && 
               item.getItemMeta().getPersistentDataContainer().has(plugin.getItemManager().TREASURE_TYPE_KEY, PersistentDataType.STRING);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(plugin.getItemManager().TREASURE_TYPE_KEY, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TreasureHolder)) return;

        Player player = (Player) event.getPlayer();
        Inventory inv = event.getInventory();

        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            // Give to player
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            
            // Drop remaining on ground if inventory full
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
        
        // Clear inventory to be safe (though it will be destroyed)
        inv.clear();
    }
}

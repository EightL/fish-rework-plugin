package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.gui.DisplayCaseCustomizeGUI;
import com.fishrework.manager.DisplayCaseManager;
import com.fishrework.manager.ItemManager;
import com.fishrework.util.FeatureKeys;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class DisplayCaseListener implements Listener {

    private final FishRework plugin;
    private final DisplayCaseManager displayCaseManager;
    private final ItemManager itemManager;

    public DisplayCaseListener(FishRework plugin) {
        this.plugin = plugin;
        this.displayCaseManager = plugin.getDisplayCaseManager();
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.isFeatureEnabled(FeatureKeys.DISPLAY_CASE_ENABLED)) return;
        ItemStack handItem = event.getItemInHand();
        if (itemManager.isDisplayCase(handItem)) {
            // It's a display case!
            // The block is already placed as GLASS by vanilla logic (since getting the item returns a GLASS stack)
            // We just need to initialize the entity.
            displayCaseManager.createDisplayCase(event.getBlock().getLocation(), event.getPlayer());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.isFeatureEnabled(FeatureKeys.DISPLAY_CASE_ENABLED)) return;
        Block block = event.getBlock();
        // Check if it's a display case
        if (displayCaseManager.isDisplayCaseBlock(block)) {
            // Prevent vanilla drop (the slab)
            event.setDropItems(false);
            displayCaseManager.removeDisplayCase(block);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.isFeatureEnabled(FeatureKeys.DISPLAY_CASE_ENABLED)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Check if this is a valid display case block
        if (displayCaseManager.isDisplayCaseBlock(block)) {
            Player player = event.getPlayer();
            
            // Interaction: Shift + Right Click for Customization
            if (player.isSneaking()) {
                new DisplayCaseCustomizeGUI(plugin, player, block).open(player);
                event.setCancelled(true);
                return;
            }

            // Normal Right Click: Item Swap/Place
            boolean handled = displayCaseManager.interact(block, player, event.getItem());
            if (handled) {
                event.setCancelled(true);
            }
        }
    }
    @EventHandler
    public void onBlockRedstone(org.bukkit.event.block.BlockRedstoneEvent event) {
        if (!plugin.isFeatureEnabled(FeatureKeys.DISPLAY_CASE_ENABLED)) return;
        if (event.getBlock().getType() == Material.IRON_TRAPDOOR) {
            if (displayCaseManager.isDisplayCaseBlock(event.getBlock())) {
                event.setNewCurrent(event.getOldCurrent()); // Prevent change
            }
        }
    }
}

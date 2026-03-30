package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.manager.ItemManager;
import com.fishrework.manager.NetheriteRelicManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public class NetheriteRelicListener implements Listener {

    private final FishRework plugin;
    private final NetheriteRelicManager relicManager;
    private final ItemManager itemManager;

    public NetheriteRelicListener(FishRework plugin) {
        this.plugin = plugin;
        this.relicManager = plugin.getNetheriteRelicManager();
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack handItem = event.getItemInHand();
        if (itemManager.isNetheriteRelic(handItem)) {
            // It's a netherite relic!
            relicManager.createRelic(event.getBlock().getLocation(), event.getPlayer().getLocation().getYaw());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (relicManager.isRelicBlock(block.getLocation())) {
            event.setDropItems(false);
            relicManager.removeRelic(block.getLocation());
            
            // Drop the actual relic item
            block.getWorld().dropItemNaturally(block.getLocation(), itemManager.getRequiredItem("netherite_relic"));
            
            // Remove the barrier
            block.setType(Material.AIR);
        }
    }

    @EventHandler
    public void onBlockRedstone(org.bukkit.event.block.BlockRedstoneEvent event) {
        if (event.getBlock().getType() == Material.IRON_TRAPDOOR) {
            if (relicManager.isRelicBlock(event.getBlock().getLocation())) {
                event.setNewCurrent(event.getOldCurrent());
            }
        }
    }

    @EventHandler
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        for (Block block : new java.util.ArrayList<>(event.blockList())) {
            if (block.getType() == Material.IRON_TRAPDOOR && relicManager.isRelicBlock(block.getLocation())) {
                relicManager.removeRelic(block.getLocation());
                block.getWorld().dropItemNaturally(block.getLocation(), itemManager.getRequiredItem("netherite_relic"));
                block.setType(Material.AIR);
                event.blockList().remove(block);
            }
        }
    }
}

package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.gui.BookMergingGUI;
import com.fishrework.gui.SeaCreatureUpgradeGUI;
import com.fishrework.manager.ItemManager;
import com.fishrework.manager.UpgradeBenchManager;
import com.fishrework.manager.UpgradeBenchManager.StationType;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Skill;
import com.fishrework.util.FeatureKeys;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class UpgradeBenchListener implements Listener {

    private final FishRework plugin;
    private final UpgradeBenchManager upgradeBenchManager;
    private final ItemManager itemManager;

    public UpgradeBenchListener(FishRework plugin) {
        this.plugin = plugin;
        this.upgradeBenchManager = plugin.getUpgradeBenchManager();
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.isFeatureEnabled(FeatureKeys.UPGRADE_GUI_ENABLED)) return;
        if (event.isCancelled()) return;
        ItemStack handItem = event.getItemInHand();
        if (itemManager.isUpgradeBench(handItem)) {
            upgradeBenchManager.createUpgradeBench(event.getBlock().getLocation(), event.getPlayer().getLocation().getYaw());
        } else if (itemManager.isBookMerger(handItem)) {
            upgradeBenchManager.createBookMerger(event.getBlock().getLocation(), event.getPlayer().getLocation().getYaw());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.isFeatureEnabled(FeatureKeys.UPGRADE_GUI_ENABLED)) return;
        Block block = event.getBlock();
        if (upgradeBenchManager.isUpgradeBenchBlock(block)) {
            event.setDropItems(false);
            upgradeBenchManager.removeStation(block);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.isFeatureEnabled(FeatureKeys.UPGRADE_GUI_ENABLED)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        StationType stationType = upgradeBenchManager.getStationType(block);
        if (stationType == null) return;

        event.setCancelled(true);
        PlayerData data = plugin.getPlayerData(event.getPlayer().getUniqueId());
        int level = data == null ? 0 : data.getLevel(Skill.FISHING);

        if (stationType == StationType.BOOK_MERGER) {
            if (level >= 14) {
                new BookMergingGUI(plugin, event.getPlayer()).open(event.getPlayer());
                event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            } else {
                event.getPlayer().sendMessage(plugin.getLanguageManager()
                        .getMessage("upgradebenchlistener.need_fishing_level_14", "You need Fishing Level 14 to use this!")
                        .color(NamedTextColor.RED));
                event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            }
            return;
        }

        if (level >= 20) {
            new SeaCreatureUpgradeGUI(plugin, event.getPlayer()).open(event.getPlayer());
            event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }

        event.getPlayer().sendMessage(plugin.getLanguageManager()
                .getMessage("skilldetailgui.you_need_fishing_level_20", "You need Fishing Level 20 to unlock this!")
                .color(NamedTextColor.RED));
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
    }

    @EventHandler
    public void onBlockRedstone(org.bukkit.event.block.BlockRedstoneEvent event) {
        if (!plugin.isFeatureEnabled(FeatureKeys.UPGRADE_GUI_ENABLED)) return;
        if (event.getBlock().getType() == Material.IRON_TRAPDOOR
                && upgradeBenchManager.isUpgradeBenchBlock(event.getBlock())) {
            event.setNewCurrent(event.getOldCurrent());
        }
    }
}

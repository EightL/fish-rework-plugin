package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.gui.CollectionGui;
import com.fishrework.gui.SkillDetailGUI;
import com.fishrework.model.Skill;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class FishingJournalListener implements Listener {

    private final FishRework plugin;

    public FishingJournalListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && 
            event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        if (plugin.getItemManager().isFishingJournal(item)) {
            new SkillDetailGUI(plugin, event.getPlayer(), Skill.FISHING).open(event.getPlayer());
            event.setCancelled(true); // Prevent placing if it interprets as a block place or something
        }
    }
}

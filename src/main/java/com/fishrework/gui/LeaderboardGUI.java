package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.model.Skill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LeaderboardGUI extends BaseGUI {

    private final Player player;
    private final Skill skill;

    public LeaderboardGUI(FishRework plugin, Player player, Skill skill) {
        super(plugin, 6, localizedTitle(plugin, "leaderboardgui.title_prefix", "Leaderboard: ")
                + skill.getLocalizedDisplayName(plugin.getLanguageManager()));
        this.player = player;
        this.skill = skill;
        initializeItems();
    }

    private void initializeItems() {
        // Fetch top 10 (synchronously for now, ideally async)
        Map<UUID, Integer> topPlayers = plugin.getDatabaseManager().getTopPlayers(skill, 10);
        
        int rank = 1;
        // Slots for top 10: 13, 21, 23 (top 3 in podium), keeping it simple: just list?
        // Let's use standard list layout starting from slot 10.
        // Or specific podium slots.
        // Let's simplify: simple list 0-9 in slots 10-14, 19-23
        
        int[] slots = {13, 22, 31, 12, 14, 21, 23, 30, 32, 40}; // Example pattern, or just sequential
        // Let's do just sequential for clarity
        int slotIndex = 0;
        int[] sequentialSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21}; 
        
        // Let's use a nice layout: Top 3 on top row center? 
        // 4, 12,13,14?
        // Let's just do sequential filling from 0 for now.
        
        for (Map.Entry<UUID, Integer> entry : topPlayers.entrySet()) {
            if (slotIndex >= 45) break; 
            
            UUID uuid = entry.getKey();
            int level = entry.getValue();
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            String name = offlinePlayer.getName();
            if (name == null) name = "Unknown";
            
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(offlinePlayer);
            meta.displayName(Component.text(plugin.getLanguageManager().getString(
                            "leaderboardgui.rank_prefix",
                            "#%rank% %name%",
                            "rank", String.valueOf(rank),
                            "name", name))
                    .color(NamedTextColor.GOLD));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(plugin.getLanguageManager().getString(
                            "leaderboardgui.level_prefix",
                            "Level: %level%",
                            "level", String.valueOf(level)))
                    .color(NamedTextColor.YELLOW));
            meta.lore(lore);
            head.setItemMeta(meta);
            
            inventory.setItem(slotIndex, head);
            
            rank++;
            slotIndex++;
        }
        
        // If empty
        if (topPlayers.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.displayName(plugin.getLanguageManager().getMessage("leaderboardgui.no_data_yet", "No Data Yet").color(NamedTextColor.RED));
            empty.setItemMeta(meta);
            inventory.setItem(22, empty);
        }

        // Back Button
        setBackButton(49);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;
        if (event.getSlot() == 49) {
            new SkillsMenuGUI(plugin, player).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        }
    }
}

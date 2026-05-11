package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.storage.DatabaseManager.LeaderboardCategory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class LeaderboardGUI extends BaseGUI {

    private final Player player;
    private final LeaderboardCategory category;

    public LeaderboardGUI(FishRework plugin, Player player, LeaderboardCategory category) {
        super(plugin, 6, localizedTitle(plugin, "leaderboardgui.title", "⚓ Fishing Leaderboard"));
        this.player = player;
        this.category = category;
        initializeItems();
    }

    private void initializeItems() {
        // Bottom bar (row 6, slots 45-53)
        fillBottomBar();

        // Tab buttons
        setTabButton(46, Material.SUNFLOWER,       "leaderboardgui.balance",         "Balance",         LeaderboardCategory.BALANCE);
        setTabButton(47, Material.EXPERIENCE_BOTTLE,"leaderboardgui.fishing_xp",     "Fishing XP",      LeaderboardCategory.FISHING_XP);
        setTabButton(48, Material.COD,              "leaderboardgui.total_fish",     "Fish Caught",     LeaderboardCategory.TOTAL_FISH);
        setTabButton(49, Material.CHEST,            "leaderboardgui.total_treasures","Treasures Caught",LeaderboardCategory.TOTAL_TREASURES);

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.displayName(plugin.getLanguageManager().getMessage("leaderboardgui.close", "Close").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(cm);
        inventory.setItem(53, close);

        // Leaderboard entries (slots 0-44)
        LinkedHashMap<UUID, Long> top = plugin.getDatabaseManager()
                .getTopPlayersBy(category, 10, plugin.getPlayerDataMap());
        int slot = 0;
        int rank = 1;
        for (Map.Entry<UUID, Long> entry : top.entrySet()) {
            UUID uuid = entry.getKey();
            long score = entry.getValue();
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            String name = offlinePlayer.getName();
            if (name == null) name = "Unknown";

            NamedTextColor rankColor = rank == 1 ? NamedTextColor.GOLD
                    : rank == 2 ? NamedTextColor.GRAY
                    : rank == 3 ? NamedTextColor.GOLD
                    : NamedTextColor.WHITE;
            boolean bold = rank <= 3;

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(offlinePlayer);
            meta.displayName(Component.text("#" + rank + " " + name, rankColor)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, bold));
            meta.lore(java.util.List.of(
                    Component.text(formatScore(category, score), NamedTextColor.AQUA)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            skull.setItemMeta(meta);
            inventory.setItem(slot++, skull);
            rank++;
        }

        // Empty state
        if (top.isEmpty()) {
            for (int i = 0; i < 45; i++) {
                if (inventory.getItem(i) == null) {
                    ItemStack empty = new ItemStack(Material.BARRIER);
                    ItemMeta em = empty.getItemMeta();
                    em.displayName(plugin.getLanguageManager().getMessage("leaderboardgui.no_data_yet", "No Data Yet")
                            .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                    empty.setItemMeta(em);
                    inventory.setItem(22, empty);
                    break;
                }
            }
        }
    }

    private void fillBottomBar() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.empty());
        filler.setItemMeta(fm);
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }
    }

    private void setTabButton(int slot, Material mat, String langKey, String fallback, LeaderboardCategory cat) {
        boolean active = this.category == cat;
        ItemStack item = new ItemStack(active ? Material.LIME_STAINED_GLASS_PANE : mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(plugin.getLanguageManager().getString(langKey, fallback),
                        active ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(
                Component.text(plugin.getLanguageManager().getString(
                        "leaderboardgui.click_to_view", "Click to view")).color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private String formatScore(LeaderboardCategory cat, long score) {
        String formatted = String.format("%,d", score);
        return switch (cat) {
            case BALANCE -> plugin.getLanguageManager().getString(
                    "leaderboardgui.doubloons_format", "%amount% Doubloons", "amount", formatted);
            case FISHING_XP -> plugin.getLanguageManager().getString(
                    "leaderboardgui.xp_format", "%amount% XP", "amount", formatted);
            case TOTAL_FISH -> plugin.getLanguageManager().getString(
                    "leaderboardgui.fish_format", "%amount% fish caught", "amount", formatted);
            case TOTAL_TREASURES -> plugin.getLanguageManager().getString(
                    "leaderboardgui.treasures_format", "%amount% treasures opened", "amount", formatted);
        };
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;
        int slot = event.getSlot();

        // Map tab slots to categories
        if (slot == 46) {
            openTab(player, LeaderboardCategory.BALANCE);
        } else if (slot == 47) {
            openTab(player, LeaderboardCategory.FISHING_XP);
        } else if (slot == 48) {
            openTab(player, LeaderboardCategory.TOTAL_FISH);
        } else if (slot == 49) {
            openTab(player, LeaderboardCategory.TOTAL_TREASURES);
        } else if (slot == 53) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        }
    }

    private void openTab(Player p, LeaderboardCategory cat) {
        if (cat == this.category) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }
        new LeaderboardGUI(plugin, p, cat).open(p);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
    }
}

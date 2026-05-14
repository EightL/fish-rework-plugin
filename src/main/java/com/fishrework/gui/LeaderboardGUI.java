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

    private static final int ENTRIES_PER_PAGE = 45;

    private final Player player;
    private final LeaderboardCategory category;
    private final int page;
    private boolean hasNextPage;

    public LeaderboardGUI(FishRework plugin, Player player, LeaderboardCategory category) {
        this(plugin, player, category, 0);
    }

    public LeaderboardGUI(FishRework plugin, Player player, LeaderboardCategory category, int page) {
        super(plugin, 6, localizedTitle(plugin, "leaderboardgui.title", "⚓ Fishing Leaderboard"));
        this.player = player;
        this.category = category;
        this.page = Math.max(0, page);
        initializeItems();
    }

    private void initializeItems() {
        // Bottom bar (row 6, slots 45-53)
        fillBottomBar();

        // Tab buttons
        setTabButton(47, Material.SUNFLOWER,        "leaderboardgui.balance",          "Balance",          LeaderboardCategory.BALANCE);
        setTabButton(48, Material.EXPERIENCE_BOTTLE,"leaderboardgui.fishing_xp",       "Fishing XP",       LeaderboardCategory.FISHING_XP);
        setTabButton(50, Material.COD,              "leaderboardgui.total_fish",       "Fish Caught",      LeaderboardCategory.TOTAL_FISH);
        setTabButton(51, Material.CHEST,            "leaderboardgui.total_treasures",  "Treasures Caught", LeaderboardCategory.TOTAL_TREASURES);

        // Back control
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta cm = back.getItemMeta();
        cm.displayName(plugin.getLanguageManager().getMessage("basegui.back", "Back").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(cm);
        inventory.setItem(49, back);

        // Leaderboard entries (slots 0-44)
        int offset = page * ENTRIES_PER_PAGE;
        LinkedHashMap<UUID, Long> ranked = plugin.getDatabaseManager()
                .getTopPlayersBy(category, offset + ENTRIES_PER_PAGE + 1, plugin.getPlayerDataMap());
        hasNextPage = ranked.size() > offset + ENTRIES_PER_PAGE;
        LinkedHashMap<UUID, Long> top = ranked.entrySet().stream()
                .skip(offset)
                .limit(ENTRIES_PER_PAGE)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
        int slot = 0;
        int rank = offset + 1;
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

        if (page > 0) {
            setPaginationControls(45, 53, page, page + 1);
        }
        if (hasNextPage) {
            setPaginationControls(45, 53, page, page + 2);
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
                    "leaderboardgui.doubloons_format", "%amount% %currency%",
                    "amount", formatted,
                    "currency", plugin.getLanguageManager().getCurrencyName());
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
        if (slot == 45 && page > 0) {
            new LeaderboardGUI(plugin, player, category, page - 1).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        } else if (slot == 53 && hasNextPage) {
            new LeaderboardGUI(plugin, player, category, page + 1).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        } else if (slot == 47) {
            openTab(player, LeaderboardCategory.BALANCE);
        } else if (slot == 48) {
            openTab(player, LeaderboardCategory.FISHING_XP);
        } else if (slot == 50) {
            openTab(player, LeaderboardCategory.TOTAL_FISH);
        } else if (slot == 51) {
            openTab(player, LeaderboardCategory.TOTAL_TREASURES);
        } else if (slot == 49) {
            new SkillDetailGUI(plugin, player, com.fishrework.model.Skill.FISHING).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        }
    }

    private void openTab(Player p, LeaderboardCategory cat) {
        if (cat == this.category) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }
        new LeaderboardGUI(plugin, p, cat, 0).open(p);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
    }
}

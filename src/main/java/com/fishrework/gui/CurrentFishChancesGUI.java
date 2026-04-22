package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.model.CustomMob;
import com.fishrework.model.Skill;
import com.fishrework.util.FishingChanceSnapshotHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shows the full current fishing chance breakdown in a player-friendly format.
 */
public class CurrentFishChancesGUI extends BaseGUI {

    private static final int[] CHANCE_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };

    private final Player player;
    private final int returnSkillDetailPage;
    private int page;

    public CurrentFishChancesGUI(FishRework plugin, Player player, int returnSkillDetailPage) {
        this(plugin, player, returnSkillDetailPage, 0);
    }

    private CurrentFishChancesGUI(FishRework plugin, Player player, int returnSkillDetailPage, int page) {
        super(plugin, 6, localizedTitle(plugin, "currentfishchancesgui.title", "Current Fish Chances"));
        this.player = player;
        this.returnSkillDetailPage = Math.max(0, returnSkillDetailPage);
        this.page = Math.max(0, page);
        initializeItems();
    }

    private void initializeItems() {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, null);
        }

        FishingChanceSnapshotHelper.ChanceSnapshot snapshot =
                FishingChanceSnapshotHelper.capture(plugin, player, Skill.FISHING);
        List<Map.Entry<String, Double>> sorted =
                FishingChanceSnapshotHelper.sortByChanceDescending(snapshot.chances());

        // Header row
        ItemStack headerFill = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta hfMeta = headerFill.getItemMeta();
        hfMeta.displayName(Component.text(" "));
        headerFill.setItemMeta(hfMeta);
        for (int slot = 0; slot <= 8; slot++) {
            inventory.setItem(slot, headerFill);
        }

        ItemStack summary = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta summaryMeta = summary.getItemMeta();
        summaryMeta.displayName(plugin.getLanguageManager().getMessage("currentfishchancesgui.current_context", "Current Context").color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> summaryLore = new ArrayList<>();
        summaryLore.add(Component.empty());
        summaryLore.add(plugin.getLanguageManager().getMessage("currentfishchancesgui.biome", "Biome: ").color(NamedTextColor.GRAY)
                .append(Component.text(snapshot.biomeGroup().getLocalizedName(plugin.getLanguageManager())).color(NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        summaryLore.add(plugin.getLanguageManager().getMessage("currentfishchancesgui.rare_bonus", "Rare Bonus: ").color(NamedTextColor.GRAY)
                .append(Component.text(com.fishrework.util.FormatUtil.format("+%.1f%%", snapshot.totalRareCreatureBonus())).color(NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));
        summaryLore.add(plugin.getLanguageManager().getMessage("currentfishchancesgui.treasure_bonus", "Treasure Bonus: ").color(NamedTextColor.GRAY)
                .append(Component.text(com.fishrework.util.FormatUtil.format("+%.1f%%", snapshot.totalTreasureBonus())).color(NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));
        if (snapshot.activeBaitId() != null && snapshot.activeBaitDisplayName() != null) {
            NamedTextColor baitColor = snapshot.baitAppliesToContext() ? NamedTextColor.AQUA : NamedTextColor.RED;
            String suffix = snapshot.baitAppliesToContext()
                    ? ""
                    : plugin.getLanguageManager().getString("currentfishchancesgui.bait_inactive_suffix", " (inactive here)");
            summaryLore.add(plugin.getLanguageManager().getMessage("currentfishchancesgui.bait", "Bait: ").color(NamedTextColor.GRAY)
                    .append(Component.text(snapshot.activeBaitDisplayName() + suffix).color(baitColor))
                    .decoration(TextDecoration.ITALIC, false));
        }
        summaryMeta.lore(summaryLore);
        summary.setItemMeta(summaryMeta);
        inventory.setItem(4, summary);

        // Chance entries
        int totalPages = Math.max(1, (int) Math.ceil((double) sorted.size() / CHANCE_SLOTS.length));
        if (page >= totalPages) {
            page = totalPages - 1;
        }

        int start = page * CHANCE_SLOTS.length;
        int end = Math.min(start + CHANCE_SLOTS.length, sorted.size());
        for (int i = start; i < end; i++) {
            Map.Entry<String, Double> entry = sorted.get(i);
            int slot = CHANCE_SLOTS[i - start];

            String id = entry.getKey();
            double chance = entry.getValue();
            double weight = snapshot.rawWeights().getOrDefault(id, 0.0);

            CustomMob mob = plugin.getMobRegistry().get(id);
            Material icon = Material.PAPER;
            if ("land_mob_bonus".equals(id)) {
                icon = Material.GRASS_BLOCK;
            } else if (mob != null && mob.getCollectionIcon() != null && !mob.getCollectionIcon().isAir()) {
                icon = mob.getCollectionIcon();
            }

            NamedTextColor titleColor = NamedTextColor.AQUA;
            if ("land_mob_bonus".equals(id)) {
                titleColor = NamedTextColor.GREEN;
            } else if (mob != null && mob.isHostile()) {
                titleColor = NamedTextColor.RED;
            }

            ItemStack item = new ItemStack(icon);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(FishingChanceSnapshotHelper.displayNameForEntry(plugin, id))
                    .color(titleColor)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(plugin.getLanguageManager().getMessage("currentfishchancesgui.chance", "Chance: ").color(NamedTextColor.GRAY)
                    .append(Component.text(com.fishrework.util.FormatUtil.format("%.2f%%", chance)).color(NamedTextColor.YELLOW))
                    .decoration(TextDecoration.ITALIC, false));

            if (!"land_mob_bonus".equals(id)) {
                lore.add(plugin.getLanguageManager().getMessage("currentfishchancesgui.weight", "Weight: ").color(NamedTextColor.GRAY)
                        .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f", weight)).color(NamedTextColor.AQUA))
                        .decoration(TextDecoration.ITALIC, false));
                if (mob != null) {
                    lore.add(plugin.getLanguageManager().getMessage("currentfishchancesgui.required_level", "Required Level: ").color(NamedTextColor.GRAY)
                            .append(Component.text(String.valueOf(mob.getRequiredLevel())).color(NamedTextColor.GREEN))
                            .decoration(TextDecoration.ITALIC, false));
                }
            } else {
                lore.add(plugin.getLanguageManager().getMessage("currentfishchancesgui.extra_chance_for_biome_land", "Extra chance for biome land mobs").color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }

        // Footer
        ItemStack navFill = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta nfMeta = navFill.getItemMeta();
        nfMeta.displayName(Component.text(" "));
        navFill.setItemMeta(nfMeta);
        for (int slot = 45; slot <= 53; slot++) {
            inventory.setItem(slot, navFill);
        }

        setPaginationControls(45, 53, page, totalPages);
        setBackButton(49);
        inventory.setItem(50, createPageInfo(page, totalPages,
                plugin.getLanguageManager().getString("currentfishchancesgui.page_sort", "Sorted by highest chance")));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 49) {
            SkillDetailGUI detailGUI = new SkillDetailGUI(plugin, player, Skill.FISHING).setPage(returnSkillDetailPage);
            detailGUI.open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }

        if (slot == 45 && page > 0) {
            new CurrentFishChancesGUI(plugin, player, returnSkillDetailPage, page - 1).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }

        if (slot == 53) {
            FishingChanceSnapshotHelper.ChanceSnapshot snapshot =
                    FishingChanceSnapshotHelper.capture(plugin, player, Skill.FISHING);
            int maxPage = Math.max(0,
                    (int) Math.ceil((double) snapshot.chances().size() / CHANCE_SLOTS.length) - 1);
            if (page < maxPage) {
                new CurrentFishChancesGUI(plugin, player, returnSkillDetailPage, page + 1).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            }
        }
    }
}

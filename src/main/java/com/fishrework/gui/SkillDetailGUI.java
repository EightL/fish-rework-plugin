package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.leveling.LevelManager;
import com.fishrework.model.CustomMob;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Skill;
import com.fishrework.registry.RecipeDefinition;
import com.fishrework.util.FeatureKeys;
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

public class SkillDetailGUI extends BaseGUI {

    private final Player player;
    private final Skill skill;
    private final org.bukkit.boss.BossBar bossBar;
    private int page = 0;

    // Zig-zag roadmap path: snake pattern through rows 1-4
    // Row 1: left-to-right (slots 9-17)
    // Row 2: right-to-left (slots 26-18)
    // Row 3: left-to-right (slots 27-35)
    // Row 4: right-to-left (slots 44-36)
    private static final int[] ROADMAP_PATH = {
            36, 27, 18, 9,  // Up Col 0
            10, 11,         // Right Row 1
            20, 29, 38,     // Down Col 2
            39, 40,         // Right Row 4
            31, 22, 13,     // Up Col 4
            14, 15,         // Right Row 1
            24, 33, 42,     // Down Col 6
            43, 44,         // Right Row 4
            35, 26, 17      // Up Col 8
    };
    private static final int LEVELS_PER_PAGE = ROADMAP_PATH.length; // 36

    public SkillDetailGUI(FishRework plugin, Player player, Skill skill) {
        super(plugin, 6, localizedTitle(plugin, "skilldetailgui.title_prefix", "Skill Details: ")
                + skill.getLocalizedDisplayName(plugin.getLanguageManager()));
        this.player = player;
        this.skill = skill;

        // Create BossBar
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        double currentXp = data.getXp(skill);
        double nextXp = data.getNextLevelXp(skill, plugin.getLevelManager());

        double progress = 1.0;
        if (nextXp > 0) {
            progress = currentXp / nextXp;
        }
        if (progress > 1.0) progress = 1.0;
        if (progress < 0.0) progress = 0.0;

        this.bossBar = org.bukkit.Bukkit.createBossBar(
                org.bukkit.ChatColor.GREEN + plugin.getLanguageManager().getString(
                        "skilldetailgui.level_progress",
                        "%skill% Level Progress: %current% / %next%",
                        "skill", skill.getLocalizedDisplayName(plugin.getLanguageManager()),
                        "current", com.fishrework.util.FormatUtil.format("%.1f", currentXp),
                        "next", com.fishrework.util.FormatUtil.format("%.0f", nextXp)),
                org.bukkit.boss.BarColor.GREEN,
                org.bukkit.boss.BarStyle.SOLID
        );
        this.bossBar.setProgress(progress);
        this.bossBar.addPlayer(player);

        initializeItems();
    }

    public SkillDetailGUI setPage(int page) {
        this.page = Math.max(0, page);
        initializeItems();
        return this;
    }

    private void initializeItems() {
        // Clear all slots first
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        int currentLevel = data.getLevel(skill);

        // ── Row 0: Header ──

        // Slot 0: Artifact Collection
        if (skill == Skill.FISHING) {
            ItemStack artifactBtn = new ItemStack(Material.NETHER_STAR);
            ItemMeta artMeta = artifactBtn.getItemMeta();
            
            // Count collected artifacts
            PlayerData artData = plugin.getPlayerData(player.getUniqueId());
            int artCollected = artData != null ? artData.getCollectedArtifacts().size() : 0;
            int artTotal = plugin.getArtifactRegistry().size();
            
            artMeta.displayName(plugin.getLanguageManager().getMessage("skilldetailgui.u2b50_artifact_collection", "\u2B50 Artifact Collection").color(NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            artMeta.lore(List.of(
                    Component.text(""),
                    plugin.getLanguageManager().getMessage("skilldetailgui.collected", "Collected: %count%/%total%",
                            "count", String.valueOf(artCollected), "total", String.valueOf(artTotal))
                            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    plugin.getLanguageManager().getMessage("skilldetailgui.view_rare_artifact_finds", "View rare artifact finds!").color(NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            artifactBtn.setItemMeta(artMeta);
            inventory.setItem(0, artifactBtn);
        } else {
            // Slot 0: Decorative Filler
            ItemStack xpHeaderFill = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
            ItemMeta xpHfMeta = xpHeaderFill.getItemMeta();
            xpHfMeta.displayName(Component.text(" "));
            xpHeaderFill.setItemMeta(xpHfMeta);
            inventory.setItem(0, xpHeaderFill);
        }

        // Slot 4: Collection
        if (skill == Skill.FISHING) {
            ItemStack collection = new ItemStack(Material.COD);
            ItemMeta colMeta = collection.getItemMeta();
            colMeta.displayName(plugin.getLanguageManager().getMessage("skilldetailgui.ud83cudfa3_fishing_encyclopedia", "\uD83C\uDFA3 Fishing Encyclopedia").color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            colMeta.lore(List.of(
                    Component.text(""),
                    plugin.getLanguageManager().getMessage("skilldetailgui.view_your_caught_fish", "View your caught fish!").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
            ));
            collection.setItemMeta(colMeta);
            inventory.setItem(4, collection);
        }

        // Slot 8: Current Bonuses
        ItemStack bonus = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta bonusMeta = bonus.getItemMeta();
        bonusMeta.displayName(plugin.getLanguageManager().getMessage("skilldetailgui.u2b50_current_bonuses", "\u2B50 Current Bonuses").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        List<Component> bonusLore = new ArrayList<>();
        bonusLore.add(Component.text(""));

        double doubleCatch = plugin.getLevelManager().getDoubleCatchChance(currentLevel);
        double equipDoubleCatch = plugin.getMobManager().getEquipmentDoubleCatchBonus(player);
        double treasure = plugin.getLevelManager().getTreasureIncrease(currentLevel);
        double totemBonus = plugin.getTotemManager() != null ? plugin.getTotemManager().getTreasureBonus(player) : 0.0;
        double multi = plugin.getLevelManager().getXpMultiplier(currentLevel);
        double rareCreature = plugin.getLevelManager().getRareCreatureChance(currentLevel);
        double equipBonus = plugin.getMobManager().getEquipmentRareCreatureBonus(player);
        double scd = plugin.getMobManager().getEquipmentSeaCreatureDefense(player);
        double sca = plugin.getMobManager().getEquipmentSeaCreatureAttack(player);
        double fishingXpBonus = plugin.getMobManager().getEquipmentFishingXpBonus(player);
        double equipmentHeatResistance = plugin.getMobManager().getEquipmentBonus(player, plugin.getItemManager().HEAT_RESISTANCE_KEY);
        double magmaFilterHeatResistance = plugin.getHeatManager().getTemporaryHeatResistance(player);
        long magmaFilterRemainingSeconds = plugin.getHeatManager().getMagmaFilterRemainingSeconds(player);
        double totalHeatResistance = equipmentHeatResistance + magmaFilterHeatResistance;
        double flatAtk = StatHelper.getEquipmentFlatSCBonus(player, plugin.getItemManager().SC_FLAT_ATTACK_KEY);
        double flatDef = StatHelper.getEquipmentFlatSCBonus(player, plugin.getItemManager().SC_FLAT_DEFENSE_KEY);

        bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.u25b6_double_catch", "\u25B6 Double Catch: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f%%", doubleCatch + equipDoubleCatch)).color(NamedTextColor.GREEN)));
        if (equipDoubleCatch > 0) {
            bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.from_equipment_bonus", "  (+%amount%% from equipment)",
                    "amount", com.fishrework.util.FormatUtil.format("%.1f", equipDoubleCatch)).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.u25b6_treasure_chance", "\u25B6 Treasure Chance: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(com.fishrework.util.FormatUtil.format("+%.1f%%", treasure + totemBonus)).color(NamedTextColor.GREEN)));
        if (totemBonus > 0) {
            bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.from_totem_bonus", "  (+%amount%% from Treasure Totem)",
                    "amount", com.fishrework.util.FormatUtil.format("%.1f", totemBonus)).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        double combinedMulti = multi * (1.0 + fishingXpBonus / 100.0);
        Component xpLine = plugin.getLanguageManager().getMessage("skilldetailgui.u25b6_xp_multiplier", "\u25B6 XP Multiplier ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
        if (fishingXpBonus > 0) {
            xpLine = xpLine.append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.0f%%", fishingXpBonus) + " ").color(NamedTextColor.GREEN));
        }
        bonusLore.add(xpLine.append(Component.text(com.fishrework.util.FormatUtil.format("x%.2f", combinedMulti)).color(NamedTextColor.GREEN)));
        if (fishingXpBonus > 0) {
            bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.from_equipment_bonus_pct", "  (+%amount%% from equipment)",
                    "amount", com.fishrework.util.FormatUtil.format("%.0f", fishingXpBonus)).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.u25b6_rare_creature", "\u25B6 Rare Creature: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(com.fishrework.util.FormatUtil.format("+%.1f%%", rareCreature + equipBonus)).color(NamedTextColor.GREEN)));
        if (equipBonus > 0) {
            bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.from_equipment_bonus", "  (+%amount%% from equipment)",
                    "amount", com.fishrework.util.FormatUtil.format("%.1f", equipBonus)).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }

        int fishingSpeed = plugin.getMobManager().getEquipmentFishingSpeed(player);
        bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.u25b6_fishing_speed", "\u25B6 Fishing Speed: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("+" + fishingSpeed).color(NamedTextColor.GREEN)));
        if (scd > 0 || flatDef > 0) {
            double effectiveFlat = flatDef > 0 ? flatDef : 1.0;
            double value = effectiveFlat * (1.0 + scd / 100.0);
            bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.u25b6_sea_creature_defense", "\u25B6 Sea Creature Defense: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f", value)).color(NamedTextColor.AQUA)));
            bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.flat_multiplier_breakdown", "  (+%flat% flat * %pct%%)",
                    "flat", com.fishrework.util.FormatUtil.format("%.1f", effectiveFlat),
                    "pct", com.fishrework.util.FormatUtil.format("%.1f", scd)).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        if (sca > 0 || flatAtk > 0) {
            double effectiveFlat = flatAtk > 0 ? flatAtk : 1.0;
            double value = effectiveFlat * (1.0 + sca / 100.0);
            bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.u25b6_sea_creature_attack", "\u25B6 Sea Creature Attack: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f", value)).color(NamedTextColor.AQUA)));
            bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.flat_multiplier_breakdown", "  (+%flat% flat * %pct%%)",
                    "flat", com.fishrework.util.FormatUtil.format("%.1f", effectiveFlat),
                    "pct", com.fishrework.util.FormatUtil.format("%.1f", sca)).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        if (currentLevel >= 27 || totalHeatResistance > 0.0) {
            bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.u25b6_heat_resistance", "\u25B6 Heat Resistance: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(com.fishrework.util.FormatUtil.format("+%.1f%%", totalHeatResistance)).color(NamedTextColor.GOLD)));
            if (magmaFilterHeatResistance > 0.0 && magmaFilterRemainingSeconds > 0) {
                bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.from_magma_filter_bonus", "  (+%amount%% from Magma Filter, %seconds%s left)",
                        "amount", com.fishrework.util.FormatUtil.format("%.1f", magmaFilterHeatResistance),
                        "seconds", String.valueOf(magmaFilterRemainingSeconds))
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        // Add Active Bait Bonuses to lore
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (plugin.getItemManager().isBait(offhand)) {
            String baitId = plugin.getItemManager().getBaitId(offhand);
            if (baitId != null) {
                com.fishrework.model.Bait activeBait = plugin.getBaitRegistry().get(baitId);
                if (activeBait != null) {
                    bonusLore.add(Component.empty());
                    bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.ud83dudd17_active_bait", "\uD83D\uDD17 Active Bait: ").color(NamedTextColor.YELLOW)
                            .append(Component.text(activeBait.getLocalizedDisplayName(plugin.getLanguageManager())).color(NamedTextColor.WHITE))
                            .decoration(TextDecoration.ITALIC, false));

                    double baitDoubleCatch = activeBait.getBonus(com.fishrework.model.Bait.DOUBLE_CATCH_CHANCE);
                    double baitTreasure = activeBait.getBonus(com.fishrework.model.Bait.TREASURE_CHANCE);
                    double baitXpMulti = activeBait.getBonus(com.fishrework.model.Bait.XP_MULTIPLIER);
                    double baitRareChance = activeBait.getBonus(com.fishrework.model.Bait.RARE_CREATURE_CHANCE);

                    if (baitDoubleCatch > 0) bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.bait_double_catch_bonus", "  + %amount%% Double Catch", "amount", com.fishrework.util.FormatUtil.format("%.1f", baitDoubleCatch)).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                    if (baitTreasure > 0) bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.bait_treasure_bonus", "  + %amount%% Treasure Chance", "amount", com.fishrework.util.FormatUtil.format("%.1f", baitTreasure)).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                    if (baitXpMulti > 0) bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.bait_xp_bonus", "  + %amount%% XP Multiplier", "amount", com.fishrework.util.FormatUtil.format("%.0f", baitXpMulti)).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                    if (baitRareChance > 0) bonusLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.bait_rare_bonus", "  + %amount%% Rare Creature Chance", "amount", com.fishrework.util.FormatUtil.format("%.1f", baitRareChance)).color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                }
            }
        }

        bonusMeta.lore(bonusLore);
        bonus.setItemMeta(bonusMeta);
        inventory.setItem(8, bonus);

        // Slot 7: Current Fish Chances (summary card + open full breakdown)
        if (skill == Skill.FISHING) {
            FishingChanceSnapshotHelper.ChanceSnapshot chanceSnapshot =
                FishingChanceSnapshotHelper.capture(plugin, player, Skill.FISHING);
            List<Map.Entry<String, Double>> sortedChances =
                FishingChanceSnapshotHelper.sortByChanceDescending(chanceSnapshot.chances());

            ItemStack chancesButton = new ItemStack(Material.HEART_OF_THE_SEA);
            ItemMeta chancesMeta = chancesButton.getItemMeta();
            chancesMeta.displayName(plugin.getLanguageManager().getMessage("skilldetailgui.current_fish_chances", "🐟 Current Fish Chances").color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

            List<Component> chancesLore = new ArrayList<>();
            chancesLore.add(Component.empty());
            chancesLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.biome", "Biome: ").color(NamedTextColor.GRAY)
                .append(Component.text(chanceSnapshot.biomeGroup().getLocalizedName(plugin.getLanguageManager())).color(NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
            chancesLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.rare_bonus", "Rare Bonus: ").color(NamedTextColor.GRAY)
                .append(Component.text(com.fishrework.util.FormatUtil.format("+%.1f%%", chanceSnapshot.totalRareCreatureBonus())).color(NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));
            chancesLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.treasure_bonus", "Treasure Bonus: ").color(NamedTextColor.GRAY)
                .append(Component.text(com.fishrework.util.FormatUtil.format("+%.1f%%", chanceSnapshot.totalTreasureBonus())).color(NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));

            if (chanceSnapshot.activeBaitId() != null && chanceSnapshot.activeBaitDisplayName() != null) {
            NamedTextColor baitColor = chanceSnapshot.baitAppliesToContext() ? NamedTextColor.AQUA : NamedTextColor.RED;
            String baitSuffix = chanceSnapshot.baitAppliesToContext()
                    ? ""
                    : plugin.getLanguageManager().getString("skilldetailgui.bait_inactive_suffix", " (inactive here)");
            chancesLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.bait", "Bait: ").color(NamedTextColor.GRAY)
                .append(Component.text(chanceSnapshot.activeBaitDisplayName() + baitSuffix).color(baitColor))
                .decoration(TextDecoration.ITALIC, false));
            }

            chancesLore.add(Component.empty());
            chancesLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.top_chances_right_now", "Top chances right now:").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

            int topCount = Math.min(5, sortedChances.size());
            if (topCount == 0) {
            chancesLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.no_eligible_catches_in_this", "• No eligible catches in this context").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
            } else {
            for (int i = 0; i < topCount; i++) {
                Map.Entry<String, Double> entry = sortedChances.get(i);
                String id = entry.getKey();
                String label = FishingChanceSnapshotHelper.displayNameForEntry(plugin, id);

                NamedTextColor lineColor = NamedTextColor.AQUA;
                if ("land_mob_bonus".equals(id)) {
                lineColor = NamedTextColor.GREEN;
                } else {
                CustomMob mob = plugin.getMobRegistry().get(id);
                if (mob != null && mob.isHostile()) {
                    lineColor = NamedTextColor.RED;
                }
                }

                chancesLore.add(Component.text(plugin.getLanguageManager().getString(
                                "skilldetailgui.top_chance_entry",
                                "• %label%: ",
                                "label", label))
                                .color(NamedTextColor.GRAY)
                    .append(Component.text(com.fishrework.util.FormatUtil.format("%.2f%%", entry.getValue())).color(lineColor))
                    .decoration(TextDecoration.ITALIC, false));
            }
            }

            chancesLore.add(Component.empty());
            chancesLore.add(plugin.getLanguageManager().getMessage("skilldetailgui.click_for_full_breakdown", "Click for full breakdown").color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));

            chancesMeta.lore(chancesLore);
            chancesButton.setItemMeta(chancesMeta);
            inventory.setItem(7, chancesButton);
        }

        // ── Decorative border fill for unused header slots ──
        ItemStack headerFill = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta hfMeta = headerFill.getItemMeta();
        hfMeta.displayName(Component.text(" "));
        headerFill.setItemMeta(hfMeta);
        for (int slot : new int[]{1, 3, 5}) {
            inventory.setItem(slot, headerFill);
        }
        if (skill != Skill.FISHING) {
            inventory.setItem(7, headerFill);
        }

        // Slot 6: Shop button
        if (skill == Skill.FISHING) {
            boolean shopEnabled = plugin.isFeatureEnabled(FeatureKeys.SHOP_ENABLED);
            ItemStack shopBtn = new ItemStack(shopEnabled ? Material.EMERALD : Material.BARRIER);
            ItemMeta shopMeta = shopBtn.getItemMeta();
            shopMeta.displayName(plugin.getLanguageManager().getMessage("skilldetailgui.fishing_shop", "⛁ Fishing Shop")
                    .color(shopEnabled ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            if (shopEnabled) {
                shopMeta.lore(List.of(
                        Component.text(""),
                        plugin.getLanguageManager().getMessage("skilldetailgui.buy_baits_sell_fish", "Buy baits, sell fish,").color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        plugin.getLanguageManager().getMessage("skilldetailgui.and_manage_your_fish_bag", "and manage your Fish Bag!").color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                ));
            } else {
                shopMeta.lore(List.of(
                        Component.text(""),
                        plugin.getLanguageManager().getMessage("skilldetailgui.disabled_by_server_admin", "✖ Disabled by server admin").color(NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false)
                ));
            }
            shopBtn.setItemMeta(shopMeta);
            inventory.setItem(6, shopBtn);
        } else {
            inventory.setItem(6, headerFill);
        }

        // Slot 2: Sea Creature Upgrade (locked behind Level 20, also hidden if feature disabled)
        if (skill == Skill.FISHING) {
            boolean upgradeEnabled = plugin.isFeatureEnabled(FeatureKeys.UPGRADE_GUI_ENABLED);
            boolean unlocked = currentLevel >= 20;
            ItemStack upgradeBtn;
            ItemMeta upgMeta;
            if (!upgradeEnabled) {
                upgradeBtn = new ItemStack(Material.BARRIER);
                upgMeta = upgradeBtn.getItemMeta();
                upgMeta.displayName(plugin.getLanguageManager().getMessage("skilldetailgui.u2694_upgrade_gear", "\u2694 Upgrade Gear").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                upgMeta.lore(List.of(
                        Component.text(""),
                        plugin.getLanguageManager().getMessage("skilldetailgui.u2716_disabled_by_server_admin", "\u2716 Disabled by server admin").color(NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false)
                ));
            } else if (unlocked) {
                upgradeBtn = new ItemStack(Material.SMITHING_TABLE);
                upgMeta = upgradeBtn.getItemMeta();
                upgMeta.displayName(plugin.getLanguageManager().getMessage("skilldetailgui.u2694_upgrade_gear", "\u2694 Upgrade Gear").color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                upgMeta.lore(List.of(
                        Component.text(""),
                        plugin.getLanguageManager().getMessage("skilldetailgui.upgrade_weapons__armor_with", "Upgrade weapons & armor with").color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        plugin.getLanguageManager().getMessage("skilldetailgui.sea_creature_attack__defense", "sea creature attack & defense!").color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                ));
            } else {
                upgradeBtn = new ItemStack(Material.BARRIER);
                upgMeta = upgradeBtn.getItemMeta();
                upgMeta.displayName(plugin.getLanguageManager().getMessage("skilldetailgui.u2694_upgrade_gear", "\u2694 Upgrade Gear").color(NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
                upgMeta.lore(List.of(
                        Component.text(""),
                        plugin.getLanguageManager().getMessage("skilldetailgui.u2716_requires_fishing_level_20", "\u2716 Requires Fishing Level 20").color(NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false)
                ));
            }
            upgradeBtn.setItemMeta(upgMeta);
            inventory.setItem(2, upgradeBtn);
        } else {
            inventory.setItem(2, headerFill);
        }

        // ── Roadmap: Zig-Zag Path ──
        // Lime glass = completed levels, nether star = current progress level,
        // blue glass = locked levels ahead

        int maxLevel = plugin.getLevelManager().getMaxLevel();
        int totalPages = (int) Math.ceil((double) maxLevel / LEVELS_PER_PAGE);
        int startLevel = (page * LEVELS_PER_PAGE) + 1;
        int endLevel = Math.min(startLevel + LEVELS_PER_PAGE - 1, maxLevel);

        for (int i = 0; i < ROADMAP_PATH.length; i++) {
            int level = startLevel + i;
            if (level > endLevel) {
                continue;
            }

            boolean unlocked = level <= currentLevel;
            boolean isNextLevel = level == currentLevel + 1; // The level we're progressing towards
            boolean isMilestone = level % 5 == 0;

            // Choose item material based on state
            Material mat;
            if (isNextLevel) {
                // Current progress target — always a nether star
                mat = Material.NETHER_STAR;
            } else if (unlocked) {
                // Completed levels — lime stained glass pane
                mat = Material.LIME_STAINED_GLASS_PANE;
            } else {
                // Locked levels ahead — blue stained glass pane
                mat = Material.BLUE_STAINED_GLASS_PANE;
            }

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();

            // Title
            NamedTextColor titleColor = unlocked
                    ? NamedTextColor.GREEN
                    : (isNextLevel ? NamedTextColor.AQUA : NamedTextColor.GRAY);
            String prefix = isNextLevel ? "\u25B8 " : (isMilestone ? "\u2605 " : "  ");
            String suffix = isNextLevel ? " \u25C0 NEXT" : "";
            meta.displayName(plugin.getLanguageManager().getMessage("skilldetailgui.level_title", "%prefix%Level %level%%suffix%",
                    "prefix", prefix, "level", String.valueOf(level), "suffix", suffix)
                    .color(titleColor)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, isMilestone || isNextLevel));

            // Build lore
            List<Component> lore = new ArrayList<>();

            // Progress bar for the next level target
            if (isNextLevel) {
                double currentXp = data.getXp(skill);
                double nextXp = data.getNextLevelXp(skill, plugin.getLevelManager());
                double progressPct = nextXp > 0
                        ? Math.min(1.0, currentXp / nextXp) : 1.0;
                int bars = 20;
                int filled = (int) (progressPct * bars);
                StringBuilder bar = new StringBuilder();
                for (int b = 0; b < bars; b++) bar.append(b < filled ? "\u2588" : "\u2591");
                lore.add(Component.text(bar.toString()).color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(com.fishrework.util.FormatUtil.format(" %.1f%%", progressPct * 100)).color(NamedTextColor.YELLOW)));
                lore.add(plugin.getLanguageManager().getMessage("skilldetailgui.xp_progress", "XP: %current% / %next%",
                        "current", com.fishrework.util.FormatUtil.format("%.0f", currentXp),
                        "next", com.fishrework.util.FormatUtil.format("%.0f", nextXp))
                        .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            } else if (!unlocked) {
                double xpRequired = plugin.getLevelManager().getXpForLevel(level);
                lore.add(plugin.getLanguageManager().getMessage("skilldetailgui.requires_xp", "Requires: %xp% XP",
                        "xp", com.fishrework.util.FormatUtil.format("%.0f", xpRequired))
                        .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }

            lore.add(Component.text(""));

            // Stats for this level
            double lvlDoubleCatch = plugin.getLevelManager().getDoubleCatchChance(level);
            double lvlTreasure = plugin.getLevelManager().getTreasureIncrease(level);
            double lvlMulti = plugin.getLevelManager().getXpMultiplier(level);

            lore.add(plugin.getLanguageManager().getMessage("skilldetailgui.double_catch", "  Double Catch: ").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f%%", lvlDoubleCatch))
                            .color(unlocked ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
            lore.add(plugin.getLanguageManager().getMessage("skilldetailgui.treasure", "  Treasure: ").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(com.fishrework.util.FormatUtil.format("+%.1f%%", lvlTreasure))
                            .color(unlocked ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
            lore.add(plugin.getLanguageManager().getMessage("skilldetailgui.xp_multi", "  XP Multi: ").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(com.fishrework.util.FormatUtil.format("x%.2f", lvlMulti))
                            .color(unlocked ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));

            // Show unlock info for any level with unlocks
            List<LevelManager.UnlockInfo> unlocks = getDetailUnlocks(level);
            boolean hasLevelRecipes = !plugin.getRecipeRegistry().getRecipesForLevel(skill, level).isEmpty();
            if (!unlocks.isEmpty()) {
                lore.add(Component.text(""));
                lore.add(plugin.getLanguageManager().getMessage("skilldetailgui.u2192_unlocks", "\u2192 Unlocks:")
                        .color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));
                for (LevelManager.UnlockInfo unlock : unlocks) {
                    Component icon = plugin.getLanguageManager().getMessage("skilldetailgui.u2022", "  \u2022 ").color(NamedTextColor.YELLOW);
                    if (unlock.mob() != null && unlock.mob().isHostile()) {
                        icon = plugin.getLanguageManager().getMessage("skilldetailgui.u2694", "  \u2694 ").color(plugin.getAdvancementManager().getGroupColor(unlock.mob()));
                    }

                    lore.add(icon.append(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(unlock.text()))
                            .decoration(TextDecoration.ITALIC, false));
                }
            }

            lore.add(Component.text(""));
            if (unlocked) {
                lore.add(plugin.getLanguageManager().getMessage("skilldetailgui.u2714_unlocked", "\u2714 UNLOCKED").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                if (hasLevelRecipes) {
                    lore.add(plugin.getLanguageManager().getMessage("skilldetailgui.click_to_view_recipes", "Click to view recipes!")
                            .color(NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)
                            .decoration(TextDecoration.BOLD, true));
                }
            } else {
                lore.add(plugin.getLanguageManager().getMessage("skilldetailgui.u2716_locked", "\u2716 LOCKED").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                if (hasLevelRecipes) {
                    lore.add(plugin.getLanguageManager().getMessage("skilldetailgui.unlock_this_level_to_view", "Unlock this level to view recipes")
                            .color(NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                }
            }

            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(ROADMAP_PATH[i], item);
        }

        // ── Row 5: Navigation ──

        // Decorative fill
        ItemStack navFill = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta nfMeta = navFill.getItemMeta();
        nfMeta.displayName(Component.text(" "));
        navFill.setItemMeta(nfMeta);
        for (int i = 45; i <= 53; i++) inventory.setItem(i, navFill);

        // Pagination
        setPaginationControls(45, 53, page, totalPages);

        // Slot 47: Settings button (fishing only)
        if (skill == Skill.FISHING) {
            ItemStack settingsBtn = new ItemStack(Material.COMPARATOR);
            ItemMeta settingsMeta = settingsBtn.getItemMeta();
            settingsMeta.displayName(plugin.getLanguageManager().getMessage("skilldetailgui.settings", "Settings").color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            settingsMeta.lore(List.of(
                Component.empty(),
                plugin.getLanguageManager().getMessage("skilldetailgui.manage_your_fishing_qol_toggles", "Manage your fishing QoL toggles").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                plugin.getLanguageManager().getMessage("skilldetailgui.for_autosell_notifications", "for autosell, notifications,").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                plugin.getLanguageManager().getMessage("skilldetailgui.damage_indicators_and_particles", "damage indicators, and particles.").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                plugin.getLanguageManager().getMessage("skilldetailgui.click_to_open", "Click to open").color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false)
            ));
            settingsBtn.setItemMeta(settingsMeta);
            inventory.setItem(47, settingsBtn);

            ItemStack recipeBrowserBtn = new ItemStack(Material.KNOWLEDGE_BOOK);
            ItemMeta recipeMeta = recipeBrowserBtn.getItemMeta();
            recipeMeta.displayName(plugin.getLanguageManager().getMessage("skilldetailgui.recipe_browser", "Recipe Browser").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
            recipeMeta.lore(List.of(
                Component.empty(),
                plugin.getLanguageManager().getMessage("skilldetailgui.browse_every_crafting_recipe", "Browse every crafting recipe,").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                plugin.getLanguageManager().getMessage("skilldetailgui.including_level_and_advancement_unlocks", "including level and advancement unlocks.").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                plugin.getLanguageManager().getMessage("skilldetailgui.click_to_open", "Click to open").color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false)
            ));
            recipeBrowserBtn.setItemMeta(recipeMeta);
            inventory.setItem(49, recipeBrowserBtn);

                ItemStack specialCraftingBtn = new ItemStack(Material.CRAFTING_TABLE);
                ItemMeta specialCraftingMeta = specialCraftingBtn.getItemMeta();
                specialCraftingMeta.displayName(plugin.getLanguageManager().getMessage("skilldetailgui.special_crafting", "Special Crafting").color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
                specialCraftingMeta.lore(List.of(
                    Component.empty(),
                    plugin.getLanguageManager().getMessage("skilldetailgui.open_a_manual_3x3_crafting", "Open a manual 3x3 crafting grid").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                    plugin.getLanguageManager().getMessage("skilldetailgui.for_fish_rework_recipes", "for Fish Rework recipes.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    plugin.getLanguageManager().getMessage("skilldetailgui.click_to_open", "Click to open").color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
                ));
                specialCraftingBtn.setItemMeta(specialCraftingMeta);
                inventory.setItem(51, specialCraftingBtn);
        }

        // Back button removed as this is now the main view
    
    }



    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;

        if (event.getSlot() == 0 && skill == Skill.FISHING) {
            new ArtifactCollectionGUI(plugin, player).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        } else if (event.getSlot() == 2 && skill == Skill.FISHING) {
            if (!plugin.isFeatureEnabled(FeatureKeys.UPGRADE_GUI_ENABLED)) {
                player.sendMessage(plugin.getLanguageManager().getMessage("skilldetailgui.the_upgrade_gear_menu_is", "The Upgrade Gear menu is disabled on this server.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return;
            }
            PlayerData data = plugin.getPlayerData(player.getUniqueId());
            if (data != null && data.getLevel(skill) >= 20) {
                new SeaCreatureUpgradeGUI(plugin, player).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            } else {
                player.sendMessage(plugin.getLanguageManager().getMessage("skilldetailgui.you_need_fishing_level_20", "You need Fishing Level 20 to unlock this!")
                        .color(net.kyori.adventure.text.format.NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            }
        } else if (event.getSlot() == 4 && skill == Skill.FISHING) {
            if (!plugin.isFeatureEnabled(FeatureKeys.ENCYCLOPEDIA_ENABLED)) {
                player.sendMessage(plugin.getLanguageManager().getMessage("skilldetailgui.the_fishing_encyclopedia_is_disabled", "The Fishing Encyclopedia is disabled on this server.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return;
            }
            new CollectionGui(plugin, player).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        } else if (event.getSlot() == 6 && skill == Skill.FISHING) {
            if (!plugin.isFeatureEnabled(FeatureKeys.SHOP_ENABLED)) {
                player.sendMessage(plugin.getLanguageManager().getMessage("skilldetailgui.the_shop_is_disabled_on", "The shop is disabled on this server.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return;
            }
            new ShopMenuGUI(plugin, player).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        } else if (event.getSlot() == 7 && skill == Skill.FISHING) {
            new CurrentFishChancesGUI(plugin, player, page).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        } else if (event.getSlot() == 47 && skill == Skill.FISHING) {
            new FishingSettingsGUI(plugin, player, reopenedPlayer -> {
                SkillDetailGUI gui = new SkillDetailGUI(plugin, reopenedPlayer, skill);
                gui.page = this.page;
                gui.initializeItems();
                gui.open(reopenedPlayer);
            }).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        } else if (event.getSlot() == 49 && skill == Skill.FISHING) {
            new RecipeBrowserGUI(plugin, player, 0,
                    RecipeBrowserGUI.TypeFilter.ALL,
                    RecipeBrowserGUI.UnlockFilter.ALL,
                    null,
                    reopenedPlayer -> {
                        SkillDetailGUI gui = new SkillDetailGUI(plugin, reopenedPlayer, skill);
                        gui.page = this.page;
                        gui.initializeItems();
                        gui.open(reopenedPlayer);
                    }).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        } else if (event.getSlot() == 51 && skill == Skill.FISHING) {
            new SpecialCraftingGUI(plugin, player, reopenedPlayer -> {
                SkillDetailGUI gui = new SkillDetailGUI(plugin, reopenedPlayer, skill);
                gui.page = this.page;
                gui.initializeItems();
                gui.open(reopenedPlayer);
            }).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        // Back button handler removed
        } else if (event.getSlot() == 45 && page > 0) {
            page--;
            initializeItems();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        } else if (event.getSlot() == 53) {
            int maxLevel = plugin.getLevelManager().getMaxLevel();
            int totalPages = (int) Math.ceil((double) maxLevel / LEVELS_PER_PAGE);
            if (page < totalPages - 1) {
                page++;
                initializeItems();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            }
        } else {
            int roadmapIndex = getRoadmapIndex(event.getSlot());
            if (roadmapIndex >= 0) {
                int level = (page * LEVELS_PER_PAGE) + 1 + roadmapIndex;
                openRecipeGuideForLevel(level);
            }
        }
    }

    @Override
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    private List<LevelManager.UnlockInfo> getDetailUnlocks(int level) {
        List<LevelManager.UnlockInfo> unlocks = new ArrayList<>();
        for (LevelManager.UnlockInfo unlock : plugin.getLevelManager().getUnlocksForLevel(skill, level)) {
            if (unlock.mob() != null && !unlock.mob().isHostile()) continue;
            unlocks.add(unlock);
        }
        return unlocks;
    }

    private int getRoadmapIndex(int slot) {
        for (int i = 0; i < ROADMAP_PATH.length; i++) {
            if (ROADMAP_PATH[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private void openRecipeGuideForLevel(int level) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) {
            return;
        }

        if (level > data.getLevel(skill)) {
            player.sendMessage(plugin.getLanguageManager().getMessage("skilldetailgui.reach_level_to_view",
                    "Reach %skill% Level %level% to view these recipes.",
                    "skill", skill.getLocalizedDisplayName(plugin.getLanguageManager()), "level", String.valueOf(level))
                    .color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return;
        }

        List<RecipeDefinition> recipes = plugin.getRecipeRegistry().getRecipesForLevel(skill, level);
        if (recipes.isEmpty()) {
            return;
        }

        new RecipeGuideGUI(plugin, player, recipes, 0, reopenedPlayer -> {
            SkillDetailGUI gui = new SkillDetailGUI(plugin, reopenedPlayer, skill);
            gui.page = this.page;
            gui.initializeItems();
            gui.open(reopenedPlayer);
        }).open(player);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
    }
}

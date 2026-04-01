package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.leveling.LevelManager;
import com.fishrework.model.CustomMob;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Skill;
import com.fishrework.registry.RecipeDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
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
import java.util.StringJoiner;

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
        super(plugin, 6, "Skill Details: " + skill.getDisplayName());
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
                org.bukkit.ChatColor.GREEN + "Level Progress: " + String.format("%.1f", currentXp) + " / " + String.format("%.0f", nextXp),
                org.bukkit.boss.BarColor.GREEN,
                org.bukkit.boss.BarStyle.SOLID
        );
        this.bossBar.setProgress(progress);
        this.bossBar.addPlayer(player);

        initializeItems();
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
            
            artMeta.displayName(Component.text("\u2B50 Artifact Collection").color(NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            artMeta.lore(List.of(
                    Component.text(""),
                    Component.text("Collected: " + artCollected + "/" + artTotal).color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("View rare artifact finds!").color(NamedTextColor.YELLOW)
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
            colMeta.displayName(Component.text("\uD83C\uDFA3 Fishing Encyclopedia").color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            colMeta.lore(List.of(
                    Component.text(""),
                    Component.text("View your caught fish!").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
            ));
            collection.setItemMeta(colMeta);
            inventory.setItem(4, collection);
        }

        // Slot 8: Current Bonuses
        ItemStack bonus = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta bonusMeta = bonus.getItemMeta();
        bonusMeta.displayName(Component.text("\u2B50 Current Bonuses").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
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

        bonusLore.add(Component.text("\u25B6 Double Catch: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format("%.1f%%", doubleCatch + equipDoubleCatch)).color(NamedTextColor.GREEN)));
        if (equipDoubleCatch > 0) {
            bonusLore.add(Component.text("  (" + String.format("+%.1f%%", equipDoubleCatch) + " from equipment)").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        bonusLore.add(Component.text("\u25B6 Treasure Chance: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format("+%.1f%%", treasure + totemBonus)).color(NamedTextColor.GREEN)));
        if (totemBonus > 0) {
            bonusLore.add(Component.text("  (" + String.format("+%.1f%%", totemBonus) + " from Treasure Totem)").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        double combinedMulti = multi * (1.0 + fishingXpBonus / 100.0);
        Component xpLine = Component.text("\u25B6 XP Multiplier ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
        if (fishingXpBonus > 0) {
            xpLine = xpLine.append(Component.text("+" + String.format("%.0f%%", fishingXpBonus) + " ").color(NamedTextColor.GREEN));
        }
        bonusLore.add(xpLine.append(Component.text(String.format("x%.2f", combinedMulti)).color(NamedTextColor.GREEN)));
        if (fishingXpBonus > 0) {
            bonusLore.add(Component.text("  (" + String.format("+%.0f%%", fishingXpBonus) + " from equipment)").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        bonusLore.add(Component.text("\u25B6 Rare Creature: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format("+%.1f%%", rareCreature + equipBonus)).color(NamedTextColor.GREEN)));
        if (equipBonus > 0) {
            bonusLore.add(Component.text("  (" + String.format("+%.1f%%", equipBonus) + " from equipment)").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }

        int fishingSpeed = plugin.getMobManager().getEquipmentFishingSpeed(player);
        bonusLore.add(Component.text("\u25B6 Fishing Speed: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("+" + fishingSpeed).color(NamedTextColor.GREEN)));
        if (scd > 0 || flatDef > 0) {
            double effectiveFlat = flatDef > 0 ? flatDef : 1.0;
            double value = effectiveFlat * (1.0 + scd / 100.0);
            bonusLore.add(Component.text("\u25B6 Sea Creature Defense: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(String.format("%.1f", value)).color(NamedTextColor.AQUA)));
            bonusLore.add(Component.text("  (+" + String.format("%.1f", effectiveFlat) + " flat * " + String.format("%.1f", scd) + "%)").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        if (sca > 0 || flatAtk > 0) {
            double effectiveFlat = flatAtk > 0 ? flatAtk : 1.0;
            double value = effectiveFlat * (1.0 + sca / 100.0);
            bonusLore.add(Component.text("\u25B6 Sea Creature Attack: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(String.format("%.1f", value)).color(NamedTextColor.AQUA)));
            bonusLore.add(Component.text("  (+" + String.format("%.1f", effectiveFlat) + " flat * " + String.format("%.1f", sca) + "%)").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        if (currentLevel >= 27 || totalHeatResistance > 0.0) {
            bonusLore.add(Component.text("\u25B6 Heat Resistance: ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format("+%.1f%%", totalHeatResistance)).color(NamedTextColor.GOLD)));
            if (magmaFilterHeatResistance > 0.0 && magmaFilterRemainingSeconds > 0) {
                bonusLore.add(Component.text("  (+" + String.format("%.1f", magmaFilterHeatResistance) + "% from Magma Filter, " + magmaFilterRemainingSeconds + "s left)")
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
                    bonusLore.add(Component.text("\uD83D\uDD17 Active Bait: ").color(NamedTextColor.YELLOW)
                            .append(Component.text(activeBait.getDisplayName()).color(NamedTextColor.WHITE))
                            .decoration(TextDecoration.ITALIC, false));

                    double baitDoubleCatch = activeBait.getBonus(com.fishrework.model.Bait.DOUBLE_CATCH_CHANCE);
                    double baitTreasure = activeBait.getBonus(com.fishrework.model.Bait.TREASURE_CHANCE);
                    double baitXpMulti = activeBait.getBonus(com.fishrework.model.Bait.XP_MULTIPLIER);
                    double baitRareChance = activeBait.getBonus(com.fishrework.model.Bait.RARE_CREATURE_CHANCE);

                    if (baitDoubleCatch > 0) bonusLore.add(Component.text("  + " + String.format("%.1f%%", baitDoubleCatch) + " Double Catch").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                    if (baitTreasure > 0) bonusLore.add(Component.text("  + " + String.format("%.1f%%", baitTreasure) + " Treasure Chance").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                    if (baitXpMulti > 0) bonusLore.add(Component.text("  + " + String.format("%.0f%%", baitXpMulti) + " XP Multiplier").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                    if (baitRareChance > 0) bonusLore.add(Component.text("  + " + String.format("%.1f%%", baitRareChance) + " Rare Creature Chance").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                }
            }
        }

        bonusMeta.lore(bonusLore);
        bonus.setItemMeta(bonusMeta);
        inventory.setItem(8, bonus);

        // ── Decorative border fill for unused header slots ──
        ItemStack headerFill = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta hfMeta = headerFill.getItemMeta();
        hfMeta.displayName(Component.text(" "));
        headerFill.setItemMeta(hfMeta);
        for (int slot : new int[]{1, 3, 5, 7}) {
            inventory.setItem(slot, headerFill);
        }

        // Slot 6: Shop button
        if (skill == Skill.FISHING) {
            boolean shopEnabled = plugin.isFeatureEnabled("shop_enabled");
            ItemStack shopBtn = new ItemStack(shopEnabled ? Material.EMERALD : Material.BARRIER);
            ItemMeta shopMeta = shopBtn.getItemMeta();
            shopMeta.displayName(Component.text("⛁ Fishing Shop")
                    .color(shopEnabled ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            if (shopEnabled) {
                shopMeta.lore(List.of(
                        Component.text(""),
                        Component.text("Buy baits, sell fish,").color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("and manage your Fish Bag!").color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                ));
            } else {
                shopMeta.lore(List.of(
                        Component.text(""),
                        Component.text("✖ Disabled by server admin").color(NamedTextColor.RED)
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
            boolean upgradeEnabled = plugin.isFeatureEnabled("upgrade_gui_enabled");
            boolean unlocked = currentLevel >= 20;
            ItemStack upgradeBtn;
            ItemMeta upgMeta;
            if (!upgradeEnabled) {
                upgradeBtn = new ItemStack(Material.BARRIER);
                upgMeta = upgradeBtn.getItemMeta();
                upgMeta.displayName(Component.text("\u2694 Upgrade Gear").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                upgMeta.lore(List.of(
                        Component.text(""),
                        Component.text("\u2716 Disabled by server admin").color(NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false)
                ));
            } else if (unlocked) {
                upgradeBtn = new ItemStack(Material.SMITHING_TABLE);
                upgMeta = upgradeBtn.getItemMeta();
                upgMeta.displayName(Component.text("\u2694 Upgrade Gear").color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                upgMeta.lore(List.of(
                        Component.text(""),
                        Component.text("Upgrade weapons & armor with").color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("sea creature attack & defense!").color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                ));
            } else {
                upgradeBtn = new ItemStack(Material.BARRIER);
                upgMeta = upgradeBtn.getItemMeta();
                upgMeta.displayName(Component.text("\u2694 Upgrade Gear").color(NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
                upgMeta.lore(List.of(
                        Component.text(""),
                        Component.text("\u2716 Requires Fishing Level 20").color(NamedTextColor.RED)
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
            meta.displayName(Component.text(prefix + "Level " + level + suffix)
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
                        .append(Component.text(String.format(" %.1f%%", progressPct * 100)).color(NamedTextColor.YELLOW)));
                lore.add(Component.text(String.format("XP: %.0f / %.0f", currentXp, nextXp))
                        .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            } else if (!unlocked) {
                double xpRequired = plugin.getLevelManager().getXpForLevel(level);
                lore.add(Component.text(String.format("Requires: %.0f XP", xpRequired))
                        .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }

            lore.add(Component.text(""));

            // Stats for this level
            double lvlDoubleCatch = plugin.getLevelManager().getDoubleCatchChance(level);
            double lvlTreasure = plugin.getLevelManager().getTreasureIncrease(level);
            double lvlMulti = plugin.getLevelManager().getXpMultiplier(level);

            lore.add(Component.text("  Double Catch: ").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(String.format("%.1f%%", lvlDoubleCatch))
                            .color(unlocked ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
            lore.add(Component.text("  Treasure: ").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(String.format("+%.1f%%", lvlTreasure))
                            .color(unlocked ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
            lore.add(Component.text("  XP Multi: ").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(String.format("x%.2f", lvlMulti))
                            .color(unlocked ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));

            // Show unlock info for any level with unlocks
            List<LevelManager.UnlockInfo> unlocks = getDetailUnlocks(level);
            boolean hasLevelRecipes = !plugin.getRecipeRegistry().getRecipesForLevel(skill, level).isEmpty();
            if (!unlocks.isEmpty()) {
                lore.add(Component.text(""));
                lore.add(Component.text("\u2192 Unlocks:")
                        .color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));
                for (LevelManager.UnlockInfo unlock : unlocks) {
                    Component icon = Component.text("  \u2022 ").color(NamedTextColor.YELLOW);
                    if (unlock.mob() != null && unlock.mob().isHostile()) {
                        icon = Component.text("  \u2694 ").color(plugin.getAdvancementManager().getGroupColor(unlock.mob()));
                    }

                    lore.add(icon.append(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(unlock.text()))
                            .decoration(TextDecoration.ITALIC, false));
                }
            }

            lore.add(Component.text(""));
            if (unlocked) {
                lore.add(Component.text("\u2714 UNLOCKED").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                if (hasLevelRecipes) {
                    lore.add(Component.text("Click to view recipes!")
                            .color(NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)
                            .decoration(TextDecoration.BOLD, true));
                }
            } else {
                lore.add(Component.text("\u2716 LOCKED").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                if (hasLevelRecipes) {
                    lore.add(Component.text("Unlock this level to view recipes")
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

        // Slot 46: Wiki button (fishing only)
        if (skill == Skill.FISHING) {
            ItemStack wikiBtn = new ItemStack(Material.PAPER);
            ItemMeta wikiMeta = wikiBtn.getItemMeta();
            wikiMeta.displayName(Component.text("Wiki").color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            wikiMeta.lore(List.of(
                Component.empty(),
                Component.text("Open the fishing wiki link").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("(currently placeholder)").color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Click to get link in chat").color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false)
            ));
            wikiBtn.setItemMeta(wikiMeta);
            inventory.setItem(46, wikiBtn);

            ItemStack recipeBrowserBtn = new ItemStack(Material.KNOWLEDGE_BOOK);
            ItemMeta recipeMeta = recipeBrowserBtn.getItemMeta();
            recipeMeta.displayName(Component.text("Recipe Browser").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
            recipeMeta.lore(List.of(
                Component.empty(),
                Component.text("Browse every crafting recipe,").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("including level and advancement unlocks.").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Click to open").color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false)
            ));
            recipeBrowserBtn.setItemMeta(recipeMeta);
            inventory.setItem(49, recipeBrowserBtn);
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
            if (!plugin.isFeatureEnabled("upgrade_gui_enabled")) {
                player.sendMessage(net.kyori.adventure.text.Component.text("The Upgrade Gear menu is disabled on this server.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return;
            }
            PlayerData data = plugin.getPlayerData(player.getUniqueId());
            if (data != null && data.getLevel(skill) >= 20) {
                new SeaCreatureUpgradeGUI(plugin, player).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            } else {
                player.sendMessage(net.kyori.adventure.text.Component.text("You need Fishing Level 20 to unlock this!")
                        .color(net.kyori.adventure.text.format.NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            }
        } else if (event.getSlot() == 4 && skill == Skill.FISHING) {
            if (!plugin.isFeatureEnabled("encyclopedia_enabled")) {
                player.sendMessage(net.kyori.adventure.text.Component.text("The Fishing Encyclopedia is disabled on this server.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return;
            }
            new CollectionGui(plugin, player).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        } else if (event.getSlot() == 6 && skill == Skill.FISHING) {
            if (!plugin.isFeatureEnabled("shop_enabled")) {
                player.sendMessage(net.kyori.adventure.text.Component.text("The shop is disabled on this server.")
                        .color(net.kyori.adventure.text.format.NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                return;
            }
            new ShopMenuGUI(plugin, player).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        } else if (event.getSlot() == 46 && skill == Skill.FISHING) {
            String wikiUrl = plugin.getConfig().getString("tips.wiki_url", "https://fish-rework.vercel.app");
            player.sendMessage(Component.text("Open Fishing Wiki: ", NamedTextColor.YELLOW)
                .append(Component.text(wikiUrl, NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.openUrl(wikiUrl))
                    .decoration(TextDecoration.UNDERLINED, true)
                    .hoverEvent(Component.text("Click to open", NamedTextColor.GREEN)))
                .decoration(TextDecoration.ITALIC, false));
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
            player.sendMessage(Component.text("Reach " + skill.getDisplayName() + " Level " + level + " to view these recipes.")
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

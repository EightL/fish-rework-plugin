package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Skill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class SkillsMenuGUI extends BaseGUI {

    private final Player player;

    public SkillsMenuGUI(FishRework plugin, Player player) {
        super(plugin, 3, "Skills Menu"); // 3 rows
        this.player = player;
        initializeItems();
    }

    private void initializeItems() {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());

        // Fill background (optional, let's keep it clean for now or add stained glass)
        // ...

        // Fishing Skill Item (Center)
        Skill fishing = Skill.FISHING;
        ItemStack fishingItem = new ItemStack(fishing.getIcon());
        ItemMeta meta = fishingItem.getItemMeta();
        meta.displayName(Component.text(fishing.getDisplayName()).color(NamedTextColor.AQUA));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(fishing.getDescription()).color(NamedTextColor.GRAY));
        lore.add(Component.text(""));
        
        int level = data.getLevel(fishing);
        double xp = data.getXp(fishing);
        double nextXp = data.getNextLevelXp(fishing, plugin.getLevelManager());
        
        lore.add(Component.text("Level: ").color(NamedTextColor.GRAY)
                .append(Component.text(level).color(NamedTextColor.YELLOW)));
        
        if (level < plugin.getLevelManager().getMaxLevel()) {
             lore.add(Component.text("Progress: ").color(NamedTextColor.GRAY)
                .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f", xp)).color(NamedTextColor.GREEN)) // Show total XP? Or relative?
                .append(Component.text(" / " + com.fishrework.util.FormatUtil.format("%.0f", nextXp)).color(NamedTextColor.GREEN)));
             
             // Simple progress bar
             // [======....]
             // ... maybe later
        } else {
            lore.add(Component.text("MAX LEVEL").color(NamedTextColor.GOLD));
        }



        lore.add(Component.text(""));
        
        // Stats
        double rareChance = plugin.getMobManager().getEquipmentRareCreatureBonus(player);
        double scd = plugin.getMobManager().getEquipmentSeaCreatureDefense(player);
        double sca = plugin.getMobManager().getEquipmentSeaCreatureAttack(player);
        double fishingXpBonus = plugin.getMobManager().getEquipmentFishingXpBonus(player);
        double equipDoubleCatch = plugin.getMobManager().getEquipmentDoubleCatchBonus(player);
        double levelDoubleCatch = plugin.getLevelManager().getDoubleCatchChance(level);
        
        int fishingSpeed = plugin.getMobManager().getEquipmentFishingSpeed(player);
        int luckLevel = StatHelper.getEnchantLevel(player, org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA);
        double treasureChance = luckLevel * 2.0;
        double totemBonus = plugin.getTotemManager() != null ? plugin.getTotemManager().getTreasureBonus(player) : 0.0;

        lore.add(Component.text("Stats:").color(NamedTextColor.GOLD));
        lore.add(Component.text(" Fishing Speed: ").color(NamedTextColor.GRAY)
                .append(Component.text("+" + fishingSpeed).color(NamedTextColor.GREEN)));
        lore.add(Component.text(" Treasure Chance: ").color(NamedTextColor.GRAY)
                .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", treasureChance + totemBonus) + "%").color(NamedTextColor.GREEN)));
        lore.add(Component.text(" Sea Creature Chance: ").color(NamedTextColor.GRAY)
                .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", rareChance) + "%").color(NamedTextColor.GREEN)));
        lore.add(Component.text(" Double Catch: ").color(NamedTextColor.GRAY)
                .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f%%", levelDoubleCatch + equipDoubleCatch)).color(NamedTextColor.GREEN)));
        double flatAtk = StatHelper.getEquipmentFlatSCBonus(player, plugin.getItemManager().SC_FLAT_ATTACK_KEY);
        double flatDef = StatHelper.getEquipmentFlatSCBonus(player, plugin.getItemManager().SC_FLAT_DEFENSE_KEY);

        if (scd > 0 || flatDef > 0) {
            double effectiveFlat = flatDef > 0 ? flatDef : 1.0;
            double value = effectiveFlat * (1.0 + scd / 100.0);
            lore.add(Component.text(" Sea Creature Defense: ").color(NamedTextColor.GRAY)
                    .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f", value)).color(NamedTextColor.AQUA)));
            lore.add(Component.text("  (+" + com.fishrework.util.FormatUtil.format("%.1f", effectiveFlat) + " flat * " + com.fishrework.util.FormatUtil.format("%.1f", scd) + "%)").color(NamedTextColor.DARK_GRAY));
        }
        if (sca > 0 || flatAtk > 0) {
            double effectiveFlat = flatAtk > 0 ? flatAtk : 1.0;
            double value = effectiveFlat * (1.0 + sca / 100.0);
            lore.add(Component.text(" Sea Creature Attack: ").color(NamedTextColor.GRAY)
                    .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f", value)).color(NamedTextColor.AQUA)));
            lore.add(Component.text("  (+" + com.fishrework.util.FormatUtil.format("%.1f", effectiveFlat) + " flat * " + com.fishrework.util.FormatUtil.format("%.1f", sca) + "%)").color(NamedTextColor.DARK_GRAY));
        }
        if (fishingXpBonus > 0) {
            lore.add(Component.text(" Fishing XP Bonus: ").color(NamedTextColor.GRAY)
                    .append(Component.text(com.fishrework.util.FormatUtil.format("+%.0f%%", fishingXpBonus)).color(NamedTextColor.GREEN)));
        }

        lore.add(Component.text(""));
        lore.add(Component.text("Left-Click to view Details").color(NamedTextColor.YELLOW));
        lore.add(Component.text("Right-Click for Leaderboard").color(NamedTextColor.YELLOW));
        
        meta.lore(lore);
        fishingItem.setItemMeta(meta);

        inventory.setItem(13, fishingItem); // Center slot (Row 2, Col 5)
        
        // Close Button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(Component.text("Close").color(NamedTextColor.RED));
        close.setItemMeta(closeMeta);
        inventory.setItem(22, close);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;
        
        Material mat = event.getCurrentItem().getType();
        
        if (mat == Material.FISHING_ROD) {
            if (event.isLeftClick()) {
                new SkillDetailGUI(plugin, player, Skill.FISHING).open(player);
            } else if (event.isRightClick()) {
                new LeaderboardGUI(plugin, player, Skill.FISHING).open(player);
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        } else if (mat == Material.BARRIER) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        }
    }
}

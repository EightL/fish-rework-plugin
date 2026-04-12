package com.fishrework.manager;

import com.fishrework.FishRework;
import com.fishrework.model.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class LoreManager {

    private static final NamedTextColor STAT_VALUE_COLOR = NamedTextColor.GREEN;

    private final FishRework plugin;

    // Cached keys for nether armor detection
    private final NamespacedKey MAGMA_SCALE_ARMOR_KEY;
    private final NamespacedKey INFERNAL_PLATE_ARMOR_KEY;
    private final NamespacedKey VOLCANIC_DREADPLATE_ARMOR_KEY;

    public LoreManager(FishRework plugin) {
        this.plugin = plugin;
        this.MAGMA_SCALE_ARMOR_KEY = new NamespacedKey(plugin, "magma_scale_armor");
        this.INFERNAL_PLATE_ARMOR_KEY = new NamespacedKey(plugin, "infernal_plate_armor");
        this.VOLCANIC_DREADPLATE_ARMOR_KEY = new NamespacedKey(plugin, "volcanic_dreadplate_armor");
    }

    /**
     * Updates the lore of an item based on its stats/enchantments.
     * Preserves original "flavor text" (top lines) if possible.
     */
    public void updateLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();

        // 1. Extract existing flavor text
        List<Component> newLore = new ArrayList<>();
        List<Component> oldLore = meta.lore();

        if (oldLore != null) {
            for (Component line : oldLore) {
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
                // Stop at stats section or empty line that precedes stats
                if (plain.trim().isEmpty()) {
                   // Check if next lines look like stats, if so break.
                   // Simple heuristic: if we hit an empty line, assume it's the separator before stats/rarity.
                   // But wait, flavor text might have empty lines.
                   // Better: Check if line contains known Stat Keywords
                   continue; // Skip empty lines for now, we add them back later
                }

                if (plain.contains("Rare Creature Chance:") ||
                    plain.contains("Fishing Speed:") ||
                    plain.contains("Treasure Chance:") ||
                    plain.contains("Fishing XP Bonus:") ||
                    plain.contains("Treasure Find:") ||
                    plain.contains("Stats double in Nether.") ||
                    plain.contains("Stats are halved outside of Nether.") ||
                    plain.contains("Heat Resistance:") ||
                    plain.contains("Sea Creature Defense:") ||
                    plain.contains("Sea Creature Defense Modifier:") ||
                    plain.contains("Sea Creature Attack:") ||
                    plain.contains("Sea Creature Attack Modifier:") ||
                    plain.contains("SC Flat Attack:") ||
                    plain.contains("SC Flat Defense:") ||
                    plain.contains("Double Catch:") ||
                    plain.contains("Bobber Damage:") ||
                    plain.contains("Full Set bonus") ||
                    isRarityLine(plain)) {
                    break;
                }
                newLore.add(line);
            }
        }

        // 2. Attributes / Enchants
        double rareChance = getStat(meta, plugin.getItemManager().RARE_CREATURE_CHANCE_KEY);
        double fishingXpBonus = getStat(meta, plugin.getItemManager().FISHING_XP_BONUS_KEY);
        double treasureChanceBonus = getStat(meta, plugin.getItemManager().TREASURE_CHANCE_BONUS_KEY);
        double seaCreatureDefense = getStat(meta, plugin.getItemManager().SEA_CREATURE_DEFENSE_KEY);
        double seaCreatureAttack = getStat(meta, plugin.getItemManager().SEA_CREATURE_ATTACK_KEY);
        double doubleCatchBonus = getStat(meta, plugin.getItemManager().DOUBLE_CATCH_BONUS_KEY);
        double bobberDamage = getStat(meta, plugin.getItemManager().BOBBER_DAMAGE_KEY);
        double heatResistance = getStat(meta, plugin.getItemManager().HEAT_RESISTANCE_KEY);
        double scFlatAttack = getStat(meta, plugin.getItemManager().SC_FLAT_ATTACK_KEY);
        double scFlatDefense = getStat(meta, plugin.getItemManager().SC_FLAT_DEFENSE_KEY);
        boolean isNetherArmorPiece = isNetherArmorPiece(meta);
        NamedTextColor heatResistanceColor = isNetherArmorPiece ? NamedTextColor.GREEN : NamedTextColor.GOLD;

        // Add Sea Creature Chance from Enchantment
        org.bukkit.enchantments.Enchantment seaCreatureEnchant = org.bukkit.Registry.ENCHANTMENT.get(new NamespacedKey("fishrework", "sea_creature_chance"));
        if (seaCreatureEnchant != null && meta.hasEnchant(seaCreatureEnchant)) {
            rareChance += meta.getEnchantLevel(seaCreatureEnchant) * 5.0;
        }

        double baseSpeed = getStat(meta, plugin.getItemManager().FISHING_SPEED_KEY);
        int lureLevel = meta.getEnchantLevel(Enchantment.LURE);
        int speedBonus = (int) (baseSpeed + lureLevel * 5);

        // Add Luck of the Sea (Treasure Chance)
        int luckLevel = meta.getEnchantLevel(Enchantment.LUCK_OF_THE_SEA);
        double treasureBonus = luckLevel * 2.0;

        // 3. Rebuild Lore
        // Add a separator if there is flavor text
        if (!newLore.isEmpty()) {
            newLore.add(Component.empty());
        }

        if (speedBonus > 0) {
            newLore.add(Component.text("Fishing Speed: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + speedBonus).color(STAT_VALUE_COLOR)));
        }
        if (treasureBonus > 0) {
            newLore.add(Component.text("Treasure Chance: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", treasureBonus) + "%").color(STAT_VALUE_COLOR)));
        }
        if (rareChance > 0) {
            newLore.add(Component.text("Rare Creature Chance: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", rareChance) + "%").color(STAT_VALUE_COLOR)));
        }
        if (fishingXpBonus > 0) {
            newLore.add(Component.text("Fishing XP Bonus: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.0f", fishingXpBonus) + "%").color(STAT_VALUE_COLOR)));
        }
        if (treasureChanceBonus > 0) {
            newLore.add(Component.text("Treasure Chance: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", treasureChanceBonus) + "%").color(STAT_VALUE_COLOR)));
        }
        if (seaCreatureDefense > 0) {
            newLore.add(Component.text("Sea Creature Defense: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", seaCreatureDefense) + "%").color(STAT_VALUE_COLOR)));
        }
        if (scFlatDefense > 0) {
            newLore.add(Component.text("Sea Creature Defense Modifier: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", scFlatDefense)).color(NamedTextColor.AQUA)));
        }
        if (seaCreatureAttack > 0) {
            newLore.add(Component.text("Sea Creature Attack: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", seaCreatureAttack) + "%").color(STAT_VALUE_COLOR)));
        }
        if (scFlatAttack > 0) {
            newLore.add(Component.text("Sea Creature Attack Modifier: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", scFlatAttack)).color(NamedTextColor.AQUA)));
        }
        if (doubleCatchBonus > 0) {
            newLore.add(Component.text("Double Catch: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", doubleCatchBonus) + "%").color(STAT_VALUE_COLOR)));
        }
        if (bobberDamage > 0) {
            newLore.add(Component.text("Bobber Damage: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f", bobberDamage)).color(NamedTextColor.RED)));
        }
        if (heatResistance > 0) {
            newLore.add(Component.text("Heat Resistance: ").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", heatResistance) + "%").color(heatResistanceColor)));
        }

        if (isNetherArmorPiece) {
            newLore.add(Component.text("Stats are halved outside of Nether.")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        List<Component> fullSetLines = fullSetBonusLines(meta);
        if (!fullSetLines.isEmpty()) {
            newLore.add(Component.empty());
            newLore.addAll(fullSetLines);
        }

        meta.lore(newLore);
        item.setItemMeta(meta);
    }

    private boolean isRarityLine(String plain) {
        for (Rarity r : Rarity.values()) {
            if (plain.contains(r.name())) return true;
        }
        return false;
    }

    private double getStat(ItemMeta meta, NamespacedKey key) {
        if (meta.getPersistentDataContainer().has(key, PersistentDataType.DOUBLE)) {
            return meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
        }
        return 0.0;
    }

    private static Component statLine(String label, String value) {
        return Component.text(label).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(value).color(NamedTextColor.GREEN));
    }

    private boolean isNetherArmorPiece(ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(MAGMA_SCALE_ARMOR_KEY, PersistentDataType.BYTE)
                || pdc.has(INFERNAL_PLATE_ARMOR_KEY, PersistentDataType.BYTE)
                || pdc.has(VOLCANIC_DREADPLATE_ARMOR_KEY, PersistentDataType.BYTE);
    }

    private List<Component> fullSetBonusLines(ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        com.fishrework.manager.ItemManager im = plugin.getItemManager();
        List<Component> lines = new ArrayList<>();

        if (pdc.has(im.SCALE_ARMOR_KEY, PersistentDataType.BYTE)) {
            lines.add(Component.text("Full Set bonus:").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            lines.add(statLine("Water Movement: ", "+0.15"));
        } else if (pdc.has(im.IRONCLAD_ARMOR_KEY, PersistentDataType.BYTE)) {
            lines.add(Component.text("Full Set bonus:").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            lines.add(statLine("Water Movement: ", "+0.5"));
        } else if (pdc.has(im.DREADPLATE_ARMOR_KEY, PersistentDataType.BYTE)) {
            lines.add(Component.text("Full Set bonus:").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            lines.add(statLine("Water Movement: ", "+0.7"));
        } else if (pdc.has(im.DEADMAN_ARMOR_KEY, PersistentDataType.BYTE)) {
            lines.add(Component.text("Full Set bonus:").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            lines.add(statLine("Night multiplier: ", "2×"));
        }
        return lines;
    }
}

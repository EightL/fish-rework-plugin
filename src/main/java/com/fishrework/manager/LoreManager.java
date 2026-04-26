package com.fishrework.manager;

import com.fishrework.FishRework;
import com.fishrework.model.Artifact;
import com.fishrework.model.ArtifactPassiveEffect;
import com.fishrework.model.ArtifactPassiveStat;
import com.fishrework.model.ArtifactPassiveType;
import com.fishrework.model.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        String artifactId = meta.getPersistentDataContainer().get(plugin.getItemManager().ARTIFACT_KEY, PersistentDataType.STRING);
        boolean isArtifactItem = artifactId != null;

        // 1. Extract existing flavor text
        List<Component> newLore = new ArrayList<>();
        List<Component> oldLore = meta.lore();

        if (oldLore != null) {
            for (Component line : oldLore) {
                String plain = PlainTextComponentSerializer.plainText().serialize(line);
                // Stop at stats section or empty line that precedes stats
                if (plain.trim().isEmpty()) {
                   // Check if next lines look like stats, if so break.
                   // Simple heuristic: if we hit an empty line, assume it's the separator before stats/rarity.
                   // But wait, flavor text might have empty lines.
                   // Better: Check if line contains known Stat Keywords
                   continue; // Skip empty lines for now, we add them back later
                }

                if (isGeneratedLoreLine(plain, isArtifactItem)) {
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
        double baseSpeed = getStat(meta, plugin.getItemManager().FISHING_SPEED_KEY);
        Double weaponAttackDamage = getOptionalStat(meta, new NamespacedKey(plugin, "weapon_attack_damage"));
        Double weaponAttackSpeed = getOptionalStat(meta, new NamespacedKey(plugin, "weapon_attack_speed"));
        List<Component> artifactPotionLore = new ArrayList<>();
        boolean isNetherArmorPiece = isNetherArmorPiece(meta);
        NamedTextColor heatResistanceColor = isNetherArmorPiece ? NamedTextColor.GREEN : NamedTextColor.GOLD;

        if (artifactId != null) {
            Artifact artifact = plugin.getArtifactRegistry().get(artifactId);
            if (artifact != null) {
                for (ArtifactPassiveEffect effect : artifact.getPassiveEffects()) {
                    if (effect.getType() == ArtifactPassiveType.STAT_BONUS && effect.getStat() != null) {
                        switch (effect.getStat()) {
                            case RARE_CREATURE_CHANCE -> rareChance += effect.getValue();
                            case TREASURE_CHANCE -> treasureChanceBonus += effect.getValue();
                            case FISHING_XP_BONUS -> fishingXpBonus += effect.getValue();
                            case DOUBLE_CATCH_CHANCE -> doubleCatchBonus += effect.getValue();
                            case FISHING_SPEED -> baseSpeed += effect.getValue();
                            case SEA_CREATURE_ATTACK -> seaCreatureAttack += effect.getValue();
                            case SEA_CREATURE_DEFENSE -> seaCreatureDefense += effect.getValue();
                            case HEAT_RESISTANCE -> heatResistance += effect.getValue();
                        }
                    } else if (effect.getType() == ArtifactPassiveType.POTION && effect.getPotionEffectType() != null) {
                        String potionName = formatPotionName(effect.getPotionEffectType().getKey().getKey());
                        int level = effect.getPotionAmplifier() + 1;
                        artifactPotionLore.add(plugin.getLanguageManager().getMessage("loremanager.passive_effect", "Passive Effect: ").color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                                .append(Component.text(potionName + " " + toRoman(level)).color(NamedTextColor.AQUA)));
                    }
                }
            }
        }

        // Add Sea Creature Chance from Enchantment
        org.bukkit.enchantments.Enchantment seaCreatureEnchant = org.bukkit.Registry.ENCHANTMENT.get(new NamespacedKey("fishrework", "sea_creature_chance"));
        if (seaCreatureEnchant != null && meta.hasEnchant(seaCreatureEnchant)) {
            rareChance += meta.getEnchantLevel(seaCreatureEnchant) * 5.0;
        }

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
            newLore.add(plugin.getLanguageManager().getMessage("loremanager.fishing_speed", "Fishing Speed: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + speedBonus).color(STAT_VALUE_COLOR)));
        }
        if (treasureBonus > 0) {
            newLore.add(plugin.getLanguageManager().getMessage("loremanager.treasure_chance", "Treasure Chance: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", treasureBonus) + "%").color(STAT_VALUE_COLOR)));
        }
        if (rareChance > 0) {
            newLore.add(plugin.getLanguageManager().getMessage("loremanager.rare_creature_chance", "Rare Creature Chance: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", rareChance) + "%").color(STAT_VALUE_COLOR)));
        }
        if (fishingXpBonus > 0) {
            newLore.add(plugin.getLanguageManager().getMessage("loremanager.fishing_xp_bonus", "Fishing XP Bonus: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.0f", fishingXpBonus) + "%").color(STAT_VALUE_COLOR)));
        }
        if (treasureChanceBonus > 0) {
            newLore.add(plugin.getLanguageManager().getMessage("loremanager.treasure_chance", "Treasure Chance: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", treasureChanceBonus) + "%").color(STAT_VALUE_COLOR)));
        }
        if (seaCreatureDefense > 0) {
            newLore.add(plugin.getLanguageManager().getMessage("loremanager.sea_creature_defense", "Sea Creature Defense: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", seaCreatureDefense) + "%").color(STAT_VALUE_COLOR)));
        }
        if (scFlatDefense > 0) {
            newLore.add(plugin.getLanguageManager().getMessage("loremanager.sea_creature_defense_modifier", "Sea Creature Defense Modifier: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", scFlatDefense)).color(NamedTextColor.AQUA)));
        }
        if (seaCreatureAttack > 0) {
            newLore.add(plugin.getLanguageManager().getMessage("loremanager.sea_creature_attack", "Sea Creature Attack: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", seaCreatureAttack) + "%").color(STAT_VALUE_COLOR)));
        }
        if (scFlatAttack > 0) {
            newLore.add(plugin.getLanguageManager().getMessage("loremanager.sea_creature_attack_modifier", "Sea Creature Attack Modifier: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", scFlatAttack)).color(NamedTextColor.AQUA)));
        }
        if (doubleCatchBonus > 0) {
            newLore.add(plugin.getLanguageManager().getMessage("loremanager.double_catch", "Double Catch: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", doubleCatchBonus) + "%").color(STAT_VALUE_COLOR)));
        }
        if (bobberDamage > 0) {
            newLore.add(plugin.getLanguageManager().getMessage("loremanager.bobber_damage", "Bobber Damage: ").color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f", bobberDamage)).color(NamedTextColor.RED)));
        }
        if (heatResistance > 0) {
            newLore.add(plugin.getLanguageManager().getMessage("loremanager.heat_resistance", "Heat Resistance: ").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("+" + com.fishrework.util.FormatUtil.format("%.1f", heatResistance) + "%").color(heatResistanceColor)));
        }

        if (weaponAttackDamage != null || weaponAttackSpeed != null) {
            if (!newLore.isEmpty() && !isBlankComponent(newLore.get(newLore.size() - 1))) {
                newLore.add(Component.empty());
            }
            newLore.add(Component.text("When in Main Hand:").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            if (weaponAttackDamage != null) {
                newLore.add(Component.text(com.fishrework.util.FormatUtil.format(" %.0f", weaponAttackDamage) + " Attack Damage")
           
                        .color(NamedTextColor.DARK_GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            }
            if (weaponAttackSpeed != null) {
                newLore.add(Component.text(com.fishrework.util.FormatUtil.format(" %.1f", weaponAttackSpeed) + " Attack Speed")
                        .color(NamedTextColor.DARK_GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        newLore.addAll(artifactPotionLore);

        if (isNetherArmorPiece) {
            newLore.add(plugin.getLanguageManager().getMessage("loremanager.stats_are_halved_outside_of", "Stats are halved outside of Nether.")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        List<Component> fullSetLines = fullSetBonusLines(meta);
        if (!fullSetLines.isEmpty()) {
            newLore.add(Component.empty());
            newLore.addAll(fullSetLines);
        }

        if (isArtifactItem) {
            if (!newLore.isEmpty() && !isBlankComponent(newLore.get(newLore.size() - 1))) {
                newLore.add(Component.empty());
            }
            newLore.add(plugin.getLanguageManager().getMessage("loremanager.u2b50_artifact", "\u2B50 Artifact").color(NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
        }

        meta.lore(newLore);
        item.setItemMeta(meta);
    }

    private boolean isRarityLine(String plain) {
        for (Rarity r : Rarity.values()) {
            if (matchesLocalizedLine(plain, "rarity." + r.name().toLowerCase(Locale.ROOT) + ".name", r.name())) {
                return true;
            }
        }
        return false;
    }

    private boolean isArtifactMarkerLine(String plain) {
        return matchesLocalizedLine(plain, "loremanager.u2b50_artifact", "\u2B50 Artifact");
    }

    private boolean isBlankComponent(Component component) {
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        return plain.trim().isEmpty();
    }

    private double getStat(ItemMeta meta, NamespacedKey key) {
        if (meta.getPersistentDataContainer().has(key, PersistentDataType.DOUBLE)) {
            return meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
        }
        return 0.0;
    }

    private Double getOptionalStat(ItemMeta meta, NamespacedKey key) {
        if (meta.getPersistentDataContainer().has(key, PersistentDataType.DOUBLE)) {
            return meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
        }
        return null;
    }

    private static Component statLine(String label, String value) {
        return Component.text(label).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(value).color(NamedTextColor.GREEN));
    }

    private boolean isGeneratedLoreLine(String plain, boolean isArtifactItem) {
        if (startsWithLocalizedPrefix(plain, "loremanager.rare_creature_chance", "Rare Creature Chance: ")
                || startsWithLocalizedPrefix(plain, "loremanager.fishing_speed", "Fishing Speed: ")
                || startsWithLocalizedPrefix(plain, "loremanager.treasure_chance", "Treasure Chance: ")
                || startsWithLocalizedPrefix(plain, "loremanager.fishing_xp_bonus", "Fishing XP Bonus: ")
                || startsWithLocalizedPrefix(plain, "loremanager.heat_resistance", "Heat Resistance: ")
                || startsWithLocalizedPrefix(plain, "loremanager.sea_creature_defense", "Sea Creature Defense: ")
                || startsWithLocalizedPrefix(plain, "loremanager.sea_creature_defense_modifier", "Sea Creature Defense Modifier: ")
                || startsWithLocalizedPrefix(plain, "loremanager.sea_creature_attack", "Sea Creature Attack: ")
                || startsWithLocalizedPrefix(plain, "loremanager.sea_creature_attack_modifier", "Sea Creature Attack Modifier: ")
                || startsWithLocalizedPrefix(plain, "loremanager.double_catch", "Double Catch: ")
                || startsWithLocalizedPrefix(plain, "loremanager.bobber_damage", "Bobber Damage: ")
                || startsWithLocalizedPrefix(plain, "loremanager.passive_effect", "Passive Effect: ")
                || startsWithLocalizedPrefix(plain, "loremanager.full_set_bonus", "Full Set bonus:")
                || startsWithLocalizedPrefix(plain, "loremanager.water_movement", "Water Movement: ")
                || startsWithLocalizedPrefix(plain, "loremanager.night_multiplier", "Night multiplier: ")
                || plain.trim().equalsIgnoreCase("When in Main Hand:")
                || plain.trim().endsWith("Attack Damage")
                || plain.trim().endsWith("Attack Speed")
                || matchesLocalizedLine(plain, "loremanager.stats_are_halved_outside_of", "Stats are halved outside of Nether.")
                || matchesLocalizedLine(plain, "loremanager.stats_double_in_nether", "Stats double in Nether.")
                || isRarityLine(plain)) {
            return true;
        }
        return isArtifactItem && isArtifactMarkerLine(plain);
    }

    private boolean startsWithLocalizedPrefix(String plain, String key, String fallback) {
        String normalized = plain.trim();
        String localized = localizedPlain(key, fallback);
        return normalized.startsWith(localized) || normalized.startsWith(fallback.trim());
    }

    private boolean matchesLocalizedLine(String plain, String key, String fallback) {
        String normalized = plain.trim();
        String localized = localizedPlain(key, fallback);
        return normalized.equalsIgnoreCase(localized)
                || normalized.equalsIgnoreCase(fallback.trim())
                || normalized.equalsIgnoreCase(localized.replace("\u2B50", "").trim())
                || normalized.equalsIgnoreCase(fallback.replace("\u2B50", "").trim());
    }

    private String localizedPlain(String key, String fallback) {
        return PlainTextComponentSerializer.plainText()
                .serialize(plugin.getLanguageManager().getMessage(key, fallback))
                .trim();
    }

    private String formatPotionName(String key) {
        String[] words = key.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) continue;
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            if (i < words.length - 1) out.append(' ');
        }
        return out.toString();
    }

    private String toRoman(int value) {
        if (value <= 1) return "I";
        if (value == 2) return "II";
        if (value == 3) return "III";
        if (value == 4) return "IV";
        if (value == 5) return "V";
        return String.valueOf(value);
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
            lines.add(plugin.getLanguageManager().getMessage("loremanager.full_set_bonus", "Full Set bonus:").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            lines.add(statLine(localizedPlain("loremanager.water_movement", "Water Movement: "), "+0.15"));
        } else if (pdc.has(im.IRONCLAD_ARMOR_KEY, PersistentDataType.BYTE)) {
            lines.add(plugin.getLanguageManager().getMessage("loremanager.full_set_bonus", "Full Set bonus:").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            lines.add(statLine(localizedPlain("loremanager.water_movement", "Water Movement: "), "+0.5"));
        } else if (pdc.has(im.DREADPLATE_ARMOR_KEY, PersistentDataType.BYTE)) {
            lines.add(plugin.getLanguageManager().getMessage("loremanager.full_set_bonus", "Full Set bonus:").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            lines.add(statLine(localizedPlain("loremanager.water_movement", "Water Movement: "), "+0.7"));
        } else if (pdc.has(im.DEADMAN_ARMOR_KEY, PersistentDataType.BYTE)) {
            lines.add(plugin.getLanguageManager().getMessage("loremanager.full_set_bonus", "Full Set bonus:").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            lines.add(statLine(localizedPlain("loremanager.night_multiplier", "Night multiplier: "), "2×"));
        }
        return lines;
    }
}

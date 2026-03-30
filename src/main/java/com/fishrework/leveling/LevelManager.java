package com.fishrework.leveling;

import com.fishrework.FishRework;
import com.fishrework.model.CustomMob;
import com.fishrework.model.Skill;
import com.fishrework.registry.RecipeDefinition;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

public class LevelManager {

    private final FishRework plugin;
    private final double baseXp;
    private final double exponent;
    private final int maxLevel;
    
    // Bonuses
    private final double doubleCatchChancePerLevel;
    private final double treasureIncreasePerLevel;
    private final double xpMultiplierPerLevel;
    private final double rareCreatureChancePerLevel;

    public LevelManager(FishRework plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.baseXp = config.getDouble("leveling.base_xp", 100.0);
        this.exponent = config.getDouble("leveling.exponent", 1.5);
        this.maxLevel = config.getInt("leveling.max_level", 50);
        
        this.doubleCatchChancePerLevel = config.getDouble("bonuses.double_catch_chance_per_level", 0.4);
        this.treasureIncreasePerLevel = config.getDouble("bonuses.treasure_chance_increase_per_level", 0.3);
        this.xpMultiplierPerLevel = config.getDouble("bonuses.xp_multiplier_per_level", 0.02);
        this.rareCreatureChancePerLevel = config.getDouble("bonuses.rare_creature_chance_per_level", 0.0);
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    /**
     * Returns the XP required to go from level (level-1) to level.
     * Each level requires this much XP from scratch (XP resets per level).
     * Formula: XP = base * (level-1)^exponent
     */
    public double getXpForLevel(int level) {
        if (level <= 1) return 0;
        return baseXp * Math.pow(level - 1, exponent);
    }

    // Bonuses
    public double getDoubleCatchChance(int level) {
        return level * doubleCatchChancePerLevel; // %
    }

    public double getTreasureIncrease(int level) {
        return level * treasureIncreasePerLevel; // %
    }

    public double getXpMultiplier(int level) {
        return 1.0 + (level * xpMultiplierPerLevel); // e.g. 1.0 + (50 * 0.02) = 2.0
    }

    public double getRareCreatureChance(int level) {
        return level * rareCreatureChancePerLevel; // Default 0.0 per level, boosted by equipment
    }

    public record UnlockInfo(String text, CustomMob mob, RecipeDefinition recipe) {
        public boolean isRecipe() {
            return recipe != null;
        }
    }

    public List<UnlockInfo> getUnlocksForLevel(Skill skill, int level) {
        List<UnlockInfo> unlocks = new ArrayList<>();

        // 1. Mobs
        for (CustomMob mob : plugin.getMobRegistry().getBySkill(skill)) {
            if (mob.getRequiredLevel() == level) {
                String color = mob.isHostile() ? "§c" : "§b"; // Red for hostile, Aqua for passive
                unlocks.add(new UnlockInfo(color + mob.getDisplayName() + "§r", mob, null));
            }
        }

        // 2. Recipes
        List<RecipeDefinition> recipes = plugin.getRecipeRegistry().getRecipesForLevel(skill, level);
        for (RecipeDefinition def : recipes) {
            org.bukkit.inventory.ItemStack result = def.getRecipe().getResult();
            String name;
            if (result.hasItemMeta() && result.getItemMeta().hasDisplayName()) {
                name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(result.getItemMeta().displayName());
            } else {
                String typeName = result.getType().name().toLowerCase().replace('_', ' ');
                StringBuilder sb = new StringBuilder();
                for (String word : typeName.split(" ")) {
                    if (!word.isEmpty()) {
                        sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
                    }
                }
                name = sb.toString().trim();
            }
            unlocks.add(new UnlockInfo("Recipe: " + name, null, def));
        }

        // 3. Special level 20 bonus
        if (skill == Skill.FISHING && level == 20) {
            unlocks.add(new UnlockInfo("Enchantment: Sea Creature Chance", null, null));
        }

        // 4. Special level 27 unlocks
        if (skill == Skill.FISHING && level == 27) {
            unlocks.add(new UnlockInfo("Feature: Lava Fishing", null, null));
            unlocks.add(new UnlockInfo("Shop: Magma Satchel", null, null));
        }

        // 5. Double-spawn rarity unlock tiers
        if (skill == Skill.FISHING && level == 31) {
            unlocks.add(new UnlockInfo("Double Catch: Sea Creature Double Spawn up to RARE", null, null));
        }
        if (skill == Skill.FISHING && level == 44) {
            unlocks.add(new UnlockInfo("Double Catch: Sea Creature Double Spawn up to EPIC", null, null));
        }

        return unlocks;
    }

    /**
     * Get unlocks for advancements only (excludes mobs, only recipes and special unlocks)
     */
    public List<String> getUnlocksForAdvancement(Skill skill, int level) {
        List<String> unlocks = new ArrayList<>();

        // 1. Mobs
        for (CustomMob mob : plugin.getMobRegistry().getBySkill(skill)) {
            if (mob.getRequiredLevel() == level) {
                unlocks.add(mob.getDisplayName());
            }
        }

        // 2. Recipes
        List<RecipeDefinition> recipes = plugin.getRecipeRegistry().getRecipesForLevel(skill, level);
        for (RecipeDefinition def : recipes) {
            org.bukkit.inventory.ItemStack result = def.getRecipe().getResult();
            String name;
            if (result.hasItemMeta() && result.getItemMeta().hasDisplayName()) {
                name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(result.getItemMeta().displayName());
            } else {
                String typeName = result.getType().name().toLowerCase().replace('_', ' ');
                StringBuilder sb = new StringBuilder();
                for (String word : typeName.split(" ")) {
                    if (!word.isEmpty()) {
                        sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
                    }
                }
                name = sb.toString().trim();
            }
            unlocks.add(name);
        }

        // 3. Special level 20 bonus
        if (skill == Skill.FISHING && level == 20) {
            unlocks.add("Enchantment: Sea Creature Chance");
        }

        // 4. Special level 27 unlocks
        if (skill == Skill.FISHING && level == 27) {
            unlocks.add("Feature: Lava Fishing");
            unlocks.add("Shop: Magma Satchel");
        }

        // 5. Double-spawn rarity unlock tiers
        if (skill == Skill.FISHING && level == 31) {
            unlocks.add("Double Catch: Sea Creature Double Spawn up to RARE");
        }
        if (skill == Skill.FISHING && level == 44) {
            unlocks.add("Double Catch: Sea Creature Double Spawn up to EPIC");
        }

        return unlocks;
    }
}

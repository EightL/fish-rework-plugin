package com.fishrework.model;

import org.bukkit.Material;

import java.util.Map;

/**
 * Represents a bait type that can be used in the offhand while fishing.
 * Baits provide various bonuses to fishing outcomes.
 */
public class Bait {

    private final String id;
    private final String displayName;
    private final Material material;
    private final Rarity rarity;
    private final String description;
    private final Map<String, Double> bonuses;

    public Bait(String id, String displayName, Material material, Rarity rarity, String description, Map<String, Double> bonuses) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.rarity = rarity;
        this.description = description;
        this.bonuses = bonuses;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Double> getBonuses() {
        return bonuses;
    }

    public double getBonus(String key) {
        return bonuses.getOrDefault(key, 0.0);
    }

    // Bonus keys
    public static final String RARE_CREATURE_CHANCE = "rare_creature_chance";
    public static final String TREASURE_CHANCE = "treasure_chance";
    public static final String DOUBLE_CATCH_CHANCE = "double_catch_chance";
    public static final String XP_MULTIPLIER = "xp_multiplier";
}

package com.fishrework.model;

import com.fishrework.manager.LanguageManager;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable definition of a custom mob.
 * All mob data lives here — adding a new mob = adding one registration call.
 *
 * <pre>
 * CustomMob.builder("cod", EntityType.COD, Skill.FISHING)
 *     .displayName("Cod")
 *     .xp(10.0)
 *     .baseWeight(1.0)
 *     .chance(25.0)
 *     .collectionIcon(Material.COD_SPAWN_EGG)
 *     .collectionName("Common Cod")
 *     .build();
 * </pre>
 */
public class CustomMob {

    private final String id;
    private final EntityType entityType;
    private final String displayName;
    private final Skill skill;
    private final double xp;
    private final double baseWeight;
    private final double defaultChance;
    private final MobCategory category;
    private final int requiredLevel;
    private final boolean boostByRareCreature;
    private final List<MobDrop> drops;
    private final Material collectionIcon;
    private final String collectionName;
    private final Rarity rarity;
    private final SpawnConfig spawnConfig;

    private CustomMob(Builder b) {
        this.id = b.id;
        this.entityType = b.entityType;
        this.displayName = b.displayName;
        this.skill = b.skill;
        this.xp = b.xp;
        this.baseWeight = b.baseWeight;
        this.defaultChance = b.defaultChance;
        this.category = b.category;
        this.requiredLevel = b.requiredLevel;
        this.boostByRareCreature = b.boostByRareCreature;
        this.drops = Collections.unmodifiableList(new ArrayList<>(b.drops));
        this.collectionIcon = b.collectionIcon;
        this.collectionName = b.collectionName;
        this.rarity = b.rarity;
        this.spawnConfig = b.spawnConfig;
    }

    // --- Getters ---

    public String getId() { return id; }
    public EntityType getEntityType() { return entityType; }
    public String getDisplayName() { return displayName; }
    public String getLocalizedDisplayName(LanguageManager lm) {
        return lm.getString("mob." + id + ".name", displayName);
    }
    public String getCollectionName() { return collectionName; }
    public String getLocalizedCollectionName(LanguageManager lm) {
        return lm.getString("mob." + id + ".collection_name", collectionName);
    }
    public Skill getSkill() { return skill; }
    public double getXp() { return xp; }
    public double getBaseWeight() { return baseWeight; }
    public double getDefaultChance() { return defaultChance; }
    public boolean isHostile() { return category == MobCategory.HOSTILE; }
    public boolean isTreasure() { return category == MobCategory.TREASURE; }
    public MobCategory getCategory() { return category; }
    public int getRequiredLevel() { return requiredLevel; }
    public boolean isBoostByRareCreature() { return boostByRareCreature; }
    public List<MobDrop> getDrops() { return drops; }
    public Material getCollectionIcon() { return collectionIcon; }
    public Rarity getRarity() { return rarity; }
    public SpawnConfig getSpawnConfig() { return spawnConfig; }

    // --- Builder ---

    public static Builder builder(String id, EntityType entityType, Skill skill) {
        return new Builder(id, entityType, skill);
    }

    public static class Builder {
        private final String id;
        private final EntityType entityType;
        private final Skill skill;
        private String displayName;
        private double xp = 0;
        private double baseWeight = 1.0;
        private double defaultChance = 10.0;
        private MobCategory category = MobCategory.PASSIVE;
        private int requiredLevel = 0;
        private boolean boostByRareCreature = false;
        private final List<MobDrop> drops = new ArrayList<>();
        private Material collectionIcon = Material.BARRIER;
        private String collectionName = "???";
        private Rarity rarity = Rarity.COMMON;
        private SpawnConfig spawnConfig;

        private Builder(String id, EntityType entityType, Skill skill) {
            this.id = id;
            this.entityType = entityType;
            this.skill = skill;
            this.displayName = id.substring(0, 1).toUpperCase() + id.substring(1);
        }

        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder xp(double xp) { this.xp = xp; return this; }
        public Builder baseWeight(double baseWeight) { this.baseWeight = baseWeight; return this; }
        public Builder chance(double defaultChance) { this.defaultChance = defaultChance; return this; }
        public Builder hostile(boolean hostile) { 
            if (hostile) this.category = MobCategory.HOSTILE; 
            return this; 
        }
        public Builder category(MobCategory category) { this.category = category; return this; }
        public Builder requiredLevel(int requiredLevel) { this.requiredLevel = requiredLevel; return this; }
        public Builder boostByRareCreature(boolean boost) { this.boostByRareCreature = boost; return this; }
        public Builder drop(MobDrop drop) { this.drops.add(drop); return this; }
        public Builder collectionIcon(Material icon) { this.collectionIcon = icon; return this; }
        public Builder collectionName(String name) { this.collectionName = name; return this; }
        public Builder rarity(Rarity rarity) { this.rarity = rarity; return this; }
        public Builder spawnConfig(SpawnConfig spawnConfig) { this.spawnConfig = spawnConfig; return this; }

        public CustomMob build() {
            return new CustomMob(this);
        }
    }

    public enum MobCategory {
        PASSIVE,
        HOSTILE,
        TREASURE
    }
}

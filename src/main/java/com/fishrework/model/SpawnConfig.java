package com.fishrework.model;

import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.List;

/**
 * Configuration for how a mob is spawned.
 * Parsed from the {@code spawn} section of {@code mobs.yml}.
 * <p>
 * Mobs without a spawn config get default behaviour:
 * <ul>
 *   <li>HOSTILE — 2× HP, 2× damage, aggro nearest</li>
 *   <li>PASSIVE — standard spawn with weight/scale variance</li>
 * </ul>
 */
public class SpawnConfig {

    /** Spawn pattern used by MobManager. */
    public enum Type {
        /** Default: single entity with weight/scale variance. */
        SIMPLE,
        /** Mount + rider, both tagged with same mob ID. */
        MOUNTED,
        /** Multiple entities (GROUP_KILL_ALL optional). */
        GROUP,
        /** Size-scaled entity (slimes). */
        SCALED
    }

    // ── Common fields ─────────────────────────────────────────

    private final Type type;

    /** HP multiplier (-1 = use category default: 2× hostile, 1× passive). */
    private final double healthMultiplier;
    /** Damage multiplier (-1 = use category default). */
    private final double damageMultiplier;
    /** Absolute HP override (-1 = use multiplier). */
    private final double health;
    /** Absolute damage override (-1 = use multiplier). */
    private final double damage;
    /** Visual entity scale (-1 = normal weight-based). */
    private final double scale;

    /** Passive-aggro AI config (null = none). */
    private final AggroConfig aggro;
    /** Particle type for AggroTask (null = none). */
    private final String particle;
    /** Custom name color (null = RED for hostile, default for passive). */
    private final String nameColor;
    /** Optional glow outline color via scoreboard team (null = none). */
    private final String glowColor;

    /** Equipment applied to the entity (or rider for MOUNTED). */
    private final EquipmentConfig equipment;
    /** Potion effects applied on spawn. */
    private final List<PotionConfig> potionEffects;
    /** Generic ability definitions for runtime scheduler. */
    private final List<AbilityConfig> abilities;

    // ── GROUP / SCALED flags ──────────────────────────────────

    /** All members must die for reward (pillager groups, king slime). */
    private final boolean groupKillAll;
    /** Slime size override (-1 = default). */
    private final int slimeSize;

    // ── MOUNTED ───────────────────────────────────────────────

    /** Mount entity config. */
    private final EntityConfig mount;
    /** Rider entity type override (null = mob's entity_type). */
    private final EntityType riderEntityType;

    // ── GROUP ─────────────────────────────────────────────────

    /** Group member definitions. */
    private final List<GroupMemberConfig> members;
    /** Spread radius for random member offsets. */
    private final double spread;

    // ── Special flags ─────────────────────────────────────────

    /** Iron Golem: setPlayerCreated(false). */
    private final boolean notPlayerCreated;

    /** If false, the mob will not target players (initial aggro + target events). */
    private final boolean aggroPlayers;

    // ── Constructor ───────────────────────────────────────────

    private SpawnConfig(Builder b) {
        this.type = b.type;
        this.healthMultiplier = b.healthMultiplier;
        this.damageMultiplier = b.damageMultiplier;
        this.health = b.health;
        this.damage = b.damage;
        this.scale = b.scale;
        this.aggro = b.aggro;
        this.particle = b.particle;
        this.nameColor = b.nameColor;
        this.glowColor = b.glowColor;
        this.equipment = b.equipment;
        this.potionEffects = b.potionEffects != null
                ? Collections.unmodifiableList(b.potionEffects)
                : Collections.emptyList();
        this.abilities = b.abilities != null
            ? Collections.unmodifiableList(b.abilities)
            : Collections.emptyList();
        this.groupKillAll = b.groupKillAll;
        this.slimeSize = b.slimeSize;
        this.mount = b.mount;
        this.riderEntityType = b.riderEntityType;
        this.members = b.members != null
                ? Collections.unmodifiableList(b.members)
                : Collections.emptyList();
        this.spread = b.spread;
        this.notPlayerCreated = b.notPlayerCreated;
        this.aggroPlayers = b.aggroPlayers;
    }

    // ── Getters ───────────────────────────────────────────────

    public Type getType()              { return type; }
    public double getHealthMultiplier() { return healthMultiplier; }
    public double getDamageMultiplier() { return damageMultiplier; }
    public double getHealth()          { return health; }
    public double getDamage()          { return damage; }
    public double getScale()           { return scale; }
    public boolean hasAggro()          { return aggro != null; }
    public AggroConfig getAggro()      { return aggro; }
    public String getParticle()        { return particle; }
    public String getNameColor()       { return nameColor; }
    public String getGlowColor()       { return glowColor; }
    public boolean hasEquipment()      { return equipment != null; }
    public EquipmentConfig getEquipment() { return equipment; }
    public List<PotionConfig> getPotionEffects() { return potionEffects; }
    public List<AbilityConfig> getAbilities() { return abilities; }
    public boolean isGroupKillAll()    { return groupKillAll; }
    public int getSlimeSize()          { return slimeSize; }
    public boolean hasMount()          { return mount != null; }
    public EntityConfig getMount()     { return mount; }
    public EntityType getRiderEntityType() { return riderEntityType; }
    public List<GroupMemberConfig> getMembers() { return members; }
    public double getSpread()          { return spread; }
    public boolean isNotPlayerCreated() { return notPlayerCreated; }
    public boolean isAggroPlayers()     { return aggroPlayers; }

    // ── Builder ───────────────────────────────────────────────

    public static Builder builder(Type type) {
        return new Builder(type);
    }

    public static class Builder {
        private final Type type;
        private double healthMultiplier = -1;
        private double damageMultiplier = -1;
        private double health = -1;
        private double damage = -1;
        private double scale = -1;
        private AggroConfig aggro;
        private String particle;
        private String nameColor;
        private String glowColor;
        private EquipmentConfig equipment;
        private List<PotionConfig> potionEffects;
        private List<AbilityConfig> abilities;
        private boolean groupKillAll;
        private int slimeSize = -1;
        private EntityConfig mount;
        private EntityType riderEntityType;
        private List<GroupMemberConfig> members;
        private double spread = 2.0;
        private boolean notPlayerCreated;
        private boolean aggroPlayers = true;

        Builder(Type type) { this.type = type; }

        public Builder healthMultiplier(double v) { this.healthMultiplier = v; return this; }
        public Builder damageMultiplier(double v) { this.damageMultiplier = v; return this; }
        public Builder health(double v)           { this.health = v; return this; }
        public Builder damage(double v)           { this.damage = v; return this; }
        public Builder scale(double v)            { this.scale = v; return this; }
        public Builder aggro(AggroConfig v)       { this.aggro = v; return this; }
        public Builder particle(String v)         { this.particle = v; return this; }
        public Builder nameColor(String v)        { this.nameColor = v; return this; }
        public Builder glowColor(String v)        { this.glowColor = v; return this; }
        public Builder equipment(EquipmentConfig v) { this.equipment = v; return this; }
        public Builder potionEffects(List<PotionConfig> v) { this.potionEffects = v; return this; }
        public Builder abilities(List<AbilityConfig> v) { this.abilities = v; return this; }
        public Builder groupKillAll(boolean v)    { this.groupKillAll = v; return this; }
        public Builder slimeSize(int v)           { this.slimeSize = v; return this; }
        public Builder mount(EntityConfig v)      { this.mount = v; return this; }
        public Builder riderEntityType(EntityType v) { this.riderEntityType = v; return this; }
        public Builder members(List<GroupMemberConfig> v) { this.members = v; return this; }
        public Builder spread(double v)           { this.spread = v; return this; }
        public Builder notPlayerCreated(boolean v) { this.notPlayerCreated = v; return this; }
        public Builder aggroPlayers(boolean v)    { this.aggroPlayers = v; return this; }

        public SpawnConfig build() { return new SpawnConfig(this); }
    }

    // ══════════════════════════════════════════════════════════
    //  Inner config classes
    // ══════════════════════════════════════════════════════════

    /** Passive-aggro AI parameters (read by AggroTask). */
    public static class AggroConfig {
        private final double speed;
        private final double damage;
        private final double range;
        private final int hitInterval;

        public AggroConfig(double speed, double damage, double range, int hitInterval) {
            this.speed = speed;
            this.damage = damage;
            this.range = range;
            this.hitInterval = hitInterval;
        }

        public double getSpeed()    { return speed; }
        public double getDamage()   { return damage; }
        public double getRange()    { return range; }
        public int getHitInterval() { return hitInterval; }
    }

    /**
     * Equipment slot configuration.
     * Each slot value is a string resolved by MobManager:
     * <ul>
     *   <li>{@code item_id} — ItemManager.getItem(id)</li>
     *   <li>{@code skull:<base64>} — custom skull head</li>
     *   <li>{@code dyed:<MATERIAL>:<#RRGGBB>} — dyed leather armor</li>
     *   <li>{@code enchanted:<MATERIAL>:<ENCHANT>:<LEVEL>} — enchanted vanilla item</li>
     *   <li>{@code tipped_arrow:<EFFECT>:<DURATION>:<AMPLIFIER>:<AMOUNT>} — tipped arrows</li>
     * </ul>
     */
    public static class EquipmentConfig {
        private final String helmet;
        private final String chestplate;
        private final String leggings;
        private final String boots;
        private final String mainhand;
        private final String offhand;

        public EquipmentConfig(String helmet, String chestplate, String leggings,
                               String boots, String mainhand, String offhand) {
            this.helmet = helmet;
            this.chestplate = chestplate;
            this.leggings = leggings;
            this.boots = boots;
            this.mainhand = mainhand;
            this.offhand = offhand;
        }

        public String getHelmet()     { return helmet; }
        public String getChestplate() { return chestplate; }
        public String getLeggings()   { return leggings; }
        public String getBoots()      { return boots; }
        public String getMainhand()   { return mainhand; }
        public String getOffhand()    { return offhand; }
    }

    /** Mount entity configuration for MOUNTED spawns. */
    public static class EntityConfig {
        private final EntityType entityType;
        private final String displayName;
        private final boolean showName;
        private final double healthMultiplier;
        private final double damageMultiplier;
        private final double health;
        private final double damage;
        private final double scale;
        private final AggroConfig aggro;
        private final String pandaGene;

        public EntityConfig(EntityType entityType, String displayName, boolean showName,
                            double healthMultiplier, double damageMultiplier,
                            double health, double damage, double scale,
                            AggroConfig aggro, String pandaGene) {
            this.entityType = entityType;
            this.displayName = displayName;
            this.showName = showName;
            this.healthMultiplier = healthMultiplier;
            this.damageMultiplier = damageMultiplier;
            this.health = health;
            this.damage = damage;
            this.scale = scale;
            this.aggro = aggro;
            this.pandaGene = pandaGene;
        }

        public EntityType getEntityType()   { return entityType; }
        public String getDisplayName()      { return displayName; }
        public boolean isShowName()         { return showName; }
        public double getHealthMultiplier() { return healthMultiplier; }
        public double getDamageMultiplier() { return damageMultiplier; }
        public double getHealth()           { return health; }
        public double getDamage()           { return damage; }
        public double getScale()            { return scale; }
        public boolean hasAggro()           { return aggro != null; }
        public AggroConfig getAggro()       { return aggro; }
        public String getPandaGene()        { return pandaGene; }
    }

    /** Group member for GROUP spawns. */
    public static class GroupMemberConfig {
        private final EntityType entityType;
        private final int count;
        private final double healthMultiplier;
        private final double damageMultiplier;
        private final double health;
        private final double damage;
        private final String displayName;
        private final boolean showName;
        private final AggroConfig aggro;
        private final List<PotionConfig> potionEffects;
        /** Nested rider on this group member (e.g. pillager on ravager). */
        private final GroupMemberConfig rider;

        public GroupMemberConfig(EntityType entityType, int count,
                                double healthMultiplier, double damageMultiplier,
                                double health, double damage,
                                String displayName, boolean showName,
                                AggroConfig aggro, List<PotionConfig> potionEffects,
                                GroupMemberConfig rider) {
            this.entityType = entityType;
            this.count = count;
            this.healthMultiplier = healthMultiplier;
            this.damageMultiplier = damageMultiplier;
            this.health = health;
            this.damage = damage;
            this.displayName = displayName;
            this.showName = showName;
            this.aggro = aggro;
            this.potionEffects = potionEffects != null
                    ? Collections.unmodifiableList(potionEffects) : Collections.emptyList();
            this.rider = rider;
        }

        public EntityType getEntityType()   { return entityType; }
        public int getCount()               { return count; }
        public double getHealthMultiplier() { return healthMultiplier; }
        public double getDamageMultiplier() { return damageMultiplier; }
        public double getHealth()           { return health; }
        public double getDamage()           { return damage; }
        public String getDisplayName()      { return displayName; }
        public boolean isShowName()         { return showName; }
        public boolean hasAggro()           { return aggro != null; }
        public AggroConfig getAggro()       { return aggro; }
        public List<PotionConfig> getPotionEffects() { return potionEffects; }
        public boolean hasRider()           { return rider != null; }
        public GroupMemberConfig getRider()  { return rider; }
    }

    /** Potion effect applied to a mob on spawn. */
    public static class PotionConfig {
        private final String type;
        private final int duration;
        private final int amplifier;

        public PotionConfig(String type, int duration, int amplifier) {
            this.type = type;
            this.duration = duration;
            this.amplifier = amplifier;
        }

        public String getType()    { return type; }
        public int getDuration()   { return duration; }
        public int getAmplifier()  { return amplifier; }
    }

    /** Generic ability configuration used by BossAbilityTask. */
    public static class AbilityConfig {
        private final String id;
        private final int cooldownTicks;
        private final int weight;
        private final double chance;

        public AbilityConfig(String id, int cooldownTicks, int weight, double chance) {
            this.id = id;
            this.cooldownTicks = cooldownTicks;
            this.weight = weight;
            this.chance = chance;
        }

        public String getId()          { return id; }
        public int getCooldownTicks()  { return cooldownTicks; }
        public int getWeight()         { return weight; }
        public double getChance()      { return chance; }
    }
}

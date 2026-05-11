package com.fishrework.model;

import com.fishrework.leveling.LevelManager;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private final Map<Skill, Double> xpMap = new HashMap<>(); // Total XP
    private final Map<Skill, Integer> levelMap = new HashMap<>(); // Current Level
    private final java.util.Set<String> caughtMobs = new java.util.HashSet<>();
    private final java.util.Set<String> collectedArtifacts = new java.util.HashSet<>();
    private boolean damageIndicatorsEnabled = true;
    private boolean fishingTipsEnabled = true;
    private String languageLocale = null;
    private ParticleDetailMode particleDetailMode = ParticleDetailMode.HIGH;
    private SeaCreatureMessageMode seaCreatureMessageMode = SeaCreatureMessageMode.ALL;
    /** Maximum doubloon balance a player can hold. Configurable in config.yml under economy.max_balance. */
    public static final double DEFAULT_MAX_BALANCE = 10_000_000.0;
    private volatile double balance = 0.0;
    private double heat = 0.0;
    private long lastHeatDecayTime = 0;
    private ItemStack[] fishBagContents = null; // null = empty bag, 45 slots (rows 0-4)
    private ItemStack[] lavaBagContents = null; // null = empty bag, 45 slots (rows 0-4)
    private FishingSession session = new FishingSession();

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        for (Skill skill : Skill.values()) {
            xpMap.put(skill, 0.0);
            levelMap.put(skill, 0); // Start at level 0
        }
    }

    public void setXp(Skill skill, double xp) {
        xpMap.put(skill, xp);
    }

    public void setLevel(Skill skill, int level) {
        levelMap.put(skill, level);
    }

    public double getXp(Skill skill) {
        return xpMap.getOrDefault(skill, 0.0);
    }

    public int getLevel(Skill skill) {
        return levelMap.getOrDefault(skill, 0);
    }

    /**
     * Adds XP and returns true if level up occurred.
     * XP is per-level: resets to 0 on each level-up, with overflow carried forward.
     */
    public boolean addXp(Skill skill, double amount, LevelManager levelManager) {
        double currentXp = getXp(skill);
        double newXp = currentXp + amount;

        int currentLevel = getLevel(skill);
        int maxLevel = levelManager.getMaxLevel();

        if (currentLevel >= maxLevel) {
            xpMap.put(skill, newXp);
            return false;
        }

        boolean leveledUp = false;

        // Handle level-ups (including multi-level-ups from large XP grants)
        while (currentLevel < maxLevel) {
            double required = levelManager.getXpForLevel(currentLevel + 1);
            if (newXp >= required) {
                newXp -= required;
                currentLevel++;
                leveledUp = true;
            } else {
                break;
            }
        }

        xpMap.put(skill, newXp);
        if (leveledUp) {
            levelMap.put(skill, currentLevel);
        }

        return leveledUp;
    }

    public double getNextLevelXp(Skill skill, LevelManager levelManager) {
        int currentLevel = getLevel(skill);
        if (currentLevel >= levelManager.getMaxLevel()) return 0;
        return levelManager.getXpForLevel(currentLevel + 1);
    }


    public void addCaughtMob(String mobId) {
        caughtMobs.add(mobId);
    }

    public boolean hasCaughtMob(String mobId) {
        return caughtMobs.contains(mobId);
    }

    public boolean hasCaughtAll(java.util.Collection<String> allMobIds) {
        return caughtMobs.containsAll(allMobIds);
    }

    public java.util.Set<String> getCaughtMobs() {
        return java.util.Collections.unmodifiableSet(caughtMobs);
    }

    // ── Artifact Collection ──

    public void addArtifact(String artifactId) {
        collectedArtifacts.add(artifactId);
    }

    public boolean hasArtifact(String artifactId) {
        return collectedArtifacts.contains(artifactId);
    }

    public java.util.Set<String> getCollectedArtifacts() {
        return java.util.Collections.unmodifiableSet(collectedArtifacts);
    }

    public UUID getUuid() {
        return uuid;
    }

    // ── Economy ──

    public double getBalance() {
        return balance;
    }

    public void setBalance(double amount) {
        this.balance = Math.max(0.0, Math.min(amount, DEFAULT_MAX_BALANCE));
    }

    /** Adds an amount (positive = credit, negative = NOT allowed — use deductBalance instead). */
    public void addBalance(double amount) {
        if (amount < 0) return; // Use deductBalance for deductions
        this.balance = Math.min(this.balance + amount, DEFAULT_MAX_BALANCE);
    }

    /**
     * Deducts an amount from the balance.
     * @return true if successful, false if insufficient funds.
     */
    public boolean deductBalance(double amount) {
        if (this.balance >= amount) {
            this.balance -= amount;
            return true;
        }
        return false;
    }

    // ── Fish Bag ──

    public ItemStack[] getFishBagContents() {
        return fishBagContents;
    }

    public void setFishBagContents(ItemStack[] contents) {
        this.fishBagContents = contents;
    }

    // ── Lava Bag ──

    public ItemStack[] getLavaBagContents() {
        return lavaBagContents;
    }

    public void setLavaBagContents(ItemStack[] contents) {
        this.lavaBagContents = contents;
    }
    
    public boolean isDamageIndicatorsEnabled() {
        return damageIndicatorsEnabled;
    }
    
    public void setDamageIndicatorsEnabled(boolean enabled) {
        this.damageIndicatorsEnabled = enabled;
    }

    public boolean isFishingTipsEnabled() {
        return fishingTipsEnabled;
    }

    public void setFishingTipsEnabled(boolean enabled) {
        this.fishingTipsEnabled = enabled;
    }

    public String getLanguageLocale() {
        return languageLocale;
    }

    public void setLanguageLocale(String languageLocale) {
        this.languageLocale = languageLocale == null || languageLocale.isBlank() ? null : languageLocale;
    }

    public ParticleDetailMode getParticleDetailMode() {
        return particleDetailMode;
    }

    public void setParticleDetailMode(ParticleDetailMode mode) {
        this.particleDetailMode = mode == null ? ParticleDetailMode.HIGH : mode;
    }

    public SeaCreatureMessageMode getSeaCreatureMessageMode() {
        return seaCreatureMessageMode;
    }

    public void setSeaCreatureMessageMode(SeaCreatureMessageMode mode) {
        this.seaCreatureMessageMode = mode == null ? SeaCreatureMessageMode.ALL : mode;
    }

    // ── Heat System ──

    public double getHeat() {
        return heat;
    }

    public void setHeat(double heat) {
        this.heat = heat;
    }

    public void addHeat(double amount) {
        this.heat += amount;
    }

    public long getLastHeatDecayTime() {
        return lastHeatDecayTime;
    }

    public void setLastHeatDecayTime(long time) {
        this.lastHeatDecayTime = time;
    }

    // ── Session ──

    public FishingSession getSession() {
        return session;
    }

    public void resetSession() {
        this.session = new FishingSession();
    }

    public void reset() {
        for (Skill skill : Skill.values()) {
            xpMap.put(skill, 0.0);
            levelMap.put(skill, 0);
        }
        caughtMobs.clear();
        collectedArtifacts.clear();
        balance = 0.0;
        heat = 0.0;
        lastHeatDecayTime = 0;
        fishBagContents = null;
        lavaBagContents = null;
        fishingTipsEnabled = true;
        particleDetailMode = ParticleDetailMode.HIGH;
        seaCreatureMessageMode = SeaCreatureMessageMode.ALL;
    }
}

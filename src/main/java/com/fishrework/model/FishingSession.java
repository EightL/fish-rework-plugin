package com.fishrework.model;

/**
 * Tracks per-session (login to logout) fishing statistics and catch streaks.
 * Reset on join, summarized on quit.
 */
public class FishingSession {

    private int totalCatches = 0;
    private int mobsKilled = 0;
    private int treasuresFound = 0;
    private double xpEarned = 0;
    private double doubloonsEarned = 0;
    private int levelsGained = 0;
    private int newDiscoveries = 0;

    // Heat System Stats
    private double peakHeat = 0.0;
    private double heatDamageTaken = 0.0;

    // Recently discovered mobs (for "NEW" indicator in encyclopedia)
    private final java.util.Set<String> recentDiscoveries = new java.util.HashSet<>();

    // Auto-sell mode
    private AutoSellMode autoSellMode = AutoSellMode.OFF;

    // ── Catch Tracking ──

    public void recordCatch() {
        totalCatches++;
    }

    public void recordMobKill() {
        mobsKilled++;
    }

    public void recordTreasure() {
        treasuresFound++;
    }

    public void addXpEarned(double xp) {
        xpEarned += xp;
    }

    public void addDoubloonsEarned(double amount) {
        doubloonsEarned += amount;
    }

    public void recordLevelUp() {
        levelsGained++;
    }

    public void recordDiscovery(String mobId) {
        newDiscoveries++;
        recentDiscoveries.add(mobId);
    }

    // ── Auto-sell ──

    public AutoSellMode getAutoSellMode() {
        return autoSellMode;
    }

    public void setAutoSellMode(AutoSellMode mode) {
        this.autoSellMode = mode == null ? AutoSellMode.OFF : mode;
    }

    public boolean isAutoSellEnabled() {
        return autoSellMode != AutoSellMode.OFF;
    }

    // ── Getters ──

    public int getTotalCatches() { return totalCatches; }
    public int getMobsKilled() { return mobsKilled; }
    public int getTreasuresFound() { return treasuresFound; }
    public double getXpEarned() { return xpEarned; }
    public double getDoubloonsEarned() { return doubloonsEarned; }
    public int getLevelsGained() { return levelsGained; }
    public int getNewDiscoveries() { return newDiscoveries; }
    public double getPeakHeat() { return peakHeat; }
    public void recordPeakHeat(double heat) { if (heat > peakHeat) peakHeat = heat; }
    public double getHeatDamageTaken() { return heatDamageTaken; }
    public void addHeatDamageTaken(double damage) { heatDamageTaken += damage; }
    public java.util.Set<String> getRecentDiscoveries() { return recentDiscoveries; }
}

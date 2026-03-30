package com.fishrework.model;

import org.bukkit.entity.FishHook;

/**
 * Tracks the state of a lava bobber for one player.
 * Created when a lava rod is cast in the Nether, destroyed when line is reeled in or cancelled.
 */
public class LavaBobberState {

    public enum Phase {
        FLYING,     // Hook just launched, travelling to lava
        IN_LAVA,    // Resting in lava, waiting for bite timer
        NIBBLE,     // Halfway mark — subtle visual cue
        BITE,       // Catch is ready — player must reel in during window
        EXPIRED     // Bite window expired or session cancelled
    }

    private final FishHook hook;
    private Phase phase = Phase.FLYING;

    // Timing
    private int biteTimer = -1;        // Ticks until bite fires (-1 = not started)
    private int reelWindowTimer = -1;  // Ticks remaining in the reel window (-1 = no window)
    private boolean hasCatch = false;  // True when bite is active and player can reel in

    // Configurable (set by LavaFishingListener from config.yml)
    private int maxBiteTicks;
    private int minBiteTicks;
    private int reelWindowTicks;

    public LavaBobberState(FishHook hook, int minBiteTicks, int maxBiteTicks, int reelWindowTicks) {
        this.hook = hook;
        this.minBiteTicks = minBiteTicks;
        this.maxBiteTicks = maxBiteTicks;
        this.reelWindowTicks = reelWindowTicks;
    }

    // ── Getters / Setters ──────────────────────────────────

    public FishHook getHook() { return hook; }
    public Phase getPhase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }

    public int getBiteTimer() { return biteTimer; }
    public void setBiteTimer(int biteTimer) { this.biteTimer = biteTimer; }
    public void decrementBiteTimer() { if (biteTimer > 0) biteTimer--; }

    public int getReelWindowTimer() { return reelWindowTimer; }
    public void setReelWindowTimer(int reelWindowTimer) { this.reelWindowTimer = reelWindowTimer; }
    public void decrementReelWindowTimer() { if (reelWindowTimer > 0) reelWindowTimer--; }

    public boolean hasCatch() { return hasCatch; }
    public void setHasCatch(boolean hasCatch) { this.hasCatch = hasCatch; }

    public int getMinBiteTicks() { return minBiteTicks; }
    public int getMaxBiteTicks() { return maxBiteTicks; }
    public int getReelWindowTicks() { return reelWindowTicks; }
}

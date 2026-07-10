package com.fishrework.model;

/**
 * Lightweight persisted data used by placeholders for players who are offline.
 * Deliberately excludes inventory, location, bait, and other live-only state.
 */
public record PlayerPlaceholderSnapshot(
        double fishingXp,
        int fishingLevel,
        double balance,
        long lifetimeDoubloons,
        long totalFishCaught,
        long totalTreasuresCaught,
        int encyclopediaCaught,
        int artifactsFound,
        AutoSellMode autoSellMode
) {
    public static final PlayerPlaceholderSnapshot EMPTY =
            new PlayerPlaceholderSnapshot(0.0, 0, 0.0, 0L, 0L, 0L, 0, 0, AutoSellMode.OFF);
}

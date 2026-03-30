package com.fishrework.model;

import java.util.*;

/**
 * Defines fishing behaviour for a specific {@link BiomeGroup}.
 * <p>
 * Four layers:
 * <ol>
 *   <li><b>Passive weight overrides</b> — replace the default chance for any passive mob
 *       (e.g. reduce cod in warm water, boost salmon in rivers).</li>
 *   <li><b>Biome-only mobs</b> — mobs with {@code defaultChance = 0} that only spawn
 *       in certain biomes (tropical fish, axolotl, etc.).</li>
 *   <li><b>Hostile weight overrides</b> — biome-specific hostile mob weights. Allows
 *       lowering default hostile chances and adding biome-only hostile mobs.</li>
 *   <li><b>Land mob bonus</b> — a small independent roll that spawns a random land animal
 *       (e.g. 0.5 % to spawn a pig/sheep/cow in a forest).</li>
 * </ol>
 */
public class BiomeFishingProfile {

    /** mobId → biome-adjusted weight (overrides default for base fish, defines chance for biome-only mobs) */
    private final Map<String, Double> passiveWeights;
    /** mobId → biome-specific hostile weight (overrides global hostile chance) */
    private final Map<String, Double> hostileWeights;
    /** mobIds that only spawn at night (must also be in hostileWeights) */
    private final Set<String> nightOnlyHostiles;
    /** mobId pool for land mob bonus roll */
    private final List<String> landMobs;
    /** total % chance for the land mob bonus (e.g. 0.5 = 0.5 %) */
    private final double landMobChance;

    private BiomeFishingProfile(Builder b) {
        this.passiveWeights = Collections.unmodifiableMap(new LinkedHashMap<>(b.passiveWeights));
        this.hostileWeights = Collections.unmodifiableMap(new LinkedHashMap<>(b.hostileWeights));
        this.nightOnlyHostiles = Collections.unmodifiableSet(new HashSet<>(b.nightOnlyHostiles));
        this.landMobs = Collections.unmodifiableList(new ArrayList<>(b.landMobs));
        this.landMobChance = b.landMobChance;
    }

    // ── Passive Getters ──

    public boolean hasWeight(String mobId) {
        return passiveWeights.containsKey(mobId);
    }

    public double getWeight(String mobId) {
        return passiveWeights.getOrDefault(mobId, 0.0);
    }

    public Map<String, Double> getPassiveWeights() {
        return passiveWeights;
    }

    // ── Hostile Getters ──

    public boolean hasHostileWeight(String mobId) {
        return hostileWeights.containsKey(mobId);
    }

    public double getHostileWeight(String mobId) {
        return hostileWeights.getOrDefault(mobId, 0.0);
    }

    public Map<String, Double> getHostileWeights() {
        return hostileWeights;
    }

    public boolean isNightOnly(String mobId) {
        return nightOnlyHostiles.contains(mobId);
    }

    public Set<String> getNightOnlyHostiles() {
        return nightOnlyHostiles;
    }

    // ── Land Getters ──

    public List<String> getLandMobs() {
        return landMobs;
    }

    public double getLandMobChance() {
        return landMobChance;
    }

    // ── Builder ──

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, Double> passiveWeights = new LinkedHashMap<>();
        private final Map<String, Double> hostileWeights = new LinkedHashMap<>();
        private final Set<String> nightOnlyHostiles = new HashSet<>();
        private final List<String> landMobs = new ArrayList<>();
        private double landMobChance = 0.0;

        /** Set the biome-specific weight for a passive mob (base fish or biome-only mob). */
        public Builder weight(String mobId, double weight) {
            passiveWeights.put(mobId, weight);
            return this;
        }

        /** Set the biome-specific weight for a hostile mob. */
        public Builder hostileWeight(String mobId, double weight) {
            hostileWeights.put(mobId, weight);
            return this;
        }

        /** Mark hostile mobs as night-only (they must also have a hostileWeight). */
        public Builder nightOnly(String... mobIds) {
            Collections.addAll(nightOnlyHostiles, mobIds);
            return this;
        }

        /** Define the land mob bonus pool and total chance (percentage). */
        public Builder landMobs(double chance, String... mobIds) {
            this.landMobChance = chance;
            landMobs.addAll(Arrays.asList(mobIds));
            return this;
        }

        public BiomeFishingProfile build() {
            return new BiomeFishingProfile(this);
        }
    }
}

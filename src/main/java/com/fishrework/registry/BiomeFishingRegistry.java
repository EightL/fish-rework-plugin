package com.fishrework.registry;

import com.fishrework.model.BiomeFishingProfile;
import com.fishrework.model.BiomeGroup;

import java.util.EnumMap;
import java.util.Map;

/**
 * Central registry for biome-specific fishing profiles.
 * Adding a new biome profile = one {@code register()} call.
 */
public class BiomeFishingRegistry {

    private final Map<BiomeGroup, BiomeFishingProfile> profiles = new EnumMap<>(BiomeGroup.class);

    public void register(BiomeGroup group, BiomeFishingProfile profile) {
        profiles.put(group, profile);
    }

    /**
     * Returns the fishing profile for the given biome group, or {@code null}
     * if no profile is registered (standard behaviour applies).
     */
    public BiomeFishingProfile get(BiomeGroup group) {
        return profiles.get(group);
    }

    public boolean has(BiomeGroup group) {
        return profiles.containsKey(group);
    }

    public Map<BiomeGroup, BiomeFishingProfile> getProfiles() {
        return profiles;
    }
}

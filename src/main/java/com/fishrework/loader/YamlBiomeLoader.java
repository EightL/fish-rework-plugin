package com.fishrework.loader;

import com.fishrework.FishRework;
import com.fishrework.model.BiomeFishingProfile;
import com.fishrework.model.BiomeGroup;
import com.fishrework.registry.BiomeFishingRegistry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

/**
 * Loads biome fishing profiles from {@code biomes.yml} and populates the {@link BiomeFishingRegistry}.
 */
public class YamlBiomeLoader {

    private final FishRework plugin;

    public YamlBiomeLoader(FishRework plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all biome profiles from {@code biomes.yml} into the given registry.
     *
     * @param registry the biome fishing registry to populate
     * @return the number of profiles loaded
     */
    public int load(BiomeFishingRegistry registry) {
        File file = new File(plugin.getDataFolder(), "biomes.yml");
        if (!file.exists()) plugin.saveResource("biomes.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection section = yaml.getConfigurationSection("biome_profiles");
        if (section == null) {
            plugin.getLogger().warning("No 'biome_profiles' section found in biomes.yml");
            return 0;
        }

        int count = 0;
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) continue;

            try {
                BiomeGroup group = BiomeGroup.valueOf(key);
                BiomeFishingProfile profile = parseProfile(entry);
                registry.register(group, profile);
                count++;
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown BiomeGroup '" + key + "' in biomes.yml — skipping");
            }
        }

        plugin.getLogger().info("Loaded " + count + " biome profiles from biomes.yml");
        return count;
    }

    private BiomeFishingProfile parseProfile(ConfigurationSection entry) {
        BiomeFishingProfile.Builder builder = BiomeFishingProfile.builder();

        // Passive weights
        ConfigurationSection passive = entry.getConfigurationSection("passive_weights");
        if (passive != null) {
            for (String mobId : passive.getKeys(false)) {
                builder.weight(mobId, passive.getDouble(mobId));
            }
        }

        // Hostile weights
        ConfigurationSection hostile = entry.getConfigurationSection("hostile_weights");
        if (hostile != null) {
            for (String mobId : hostile.getKeys(false)) {
                builder.hostileWeight(mobId, hostile.getDouble(mobId));
            }
        }

        // Night-only hostiles
        List<String> nightOnly = entry.getStringList("night_only");
        if (!nightOnly.isEmpty()) {
            builder.nightOnly(nightOnly.toArray(new String[0]));
        }

        // Land mobs
        ConfigurationSection landSection = entry.getConfigurationSection("land_mobs");
        if (landSection != null) {
            double landChance = landSection.getDouble("chance", 0.0);
            List<String> landMobIds = landSection.getStringList("mobs");
            if (!landMobIds.isEmpty()) {
                builder.landMobs(landChance, landMobIds.toArray(new String[0]));
            }
        }

        return builder.build();
    }
}

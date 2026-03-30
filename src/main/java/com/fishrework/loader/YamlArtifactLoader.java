package com.fishrework.loader;

import com.fishrework.FishRework;
import com.fishrework.model.Artifact;
import com.fishrework.model.Rarity;
import com.fishrework.registry.ArtifactRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Loads artifact definitions from {@code artifacts.yml} and populates the {@link ArtifactRegistry}.
 */
public class YamlArtifactLoader {

    private final FishRework plugin;

    public YamlArtifactLoader(FishRework plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all artifacts from {@code artifacts.yml} into the given registry.
     *
     * @param registry the artifact registry to populate
     * @return the number of artifacts loaded
     */
    public int load(ArtifactRegistry registry) {
        File file = new File(plugin.getDataFolder(), "artifacts.yml");
        if (!file.exists()) plugin.saveResource("artifacts.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection section = yaml.getConfigurationSection("artifacts");
        if (section == null) {
            plugin.getLogger().warning("No 'artifacts' section found in artifacts.yml");
            return 0;
        }

        int count = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) continue;

            String displayName = entry.getString("display_name", id);
            String description = entry.getString("description", "");
            Rarity rarity = Rarity.valueOf(entry.getString("rarity", "COMMON"));

            Artifact artifact;
            if (entry.contains("texture_base64")) {
                artifact = new Artifact(id, displayName, description, rarity,
                        entry.getString("texture_base64"));
            } else {
                Material material = Material.valueOf(entry.getString("material", "BARRIER"));
                artifact = new Artifact(id, displayName, description, rarity, material);
            }

            registry.register(artifact);
            count++;
        }

        plugin.getLogger().info("Loaded " + count + " artifacts from artifacts.yml");
        return count;
    }
}

package com.fishrework.loader;

import com.fishrework.FishRework;
import com.fishrework.model.Artifact;
import com.fishrework.model.Rarity;
import com.fishrework.registry.ArtifactRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

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
        YamlConfiguration yaml = YamlLoaderSupport.loadYaml(plugin, "artifacts.yml");

        ConfigurationSection section = YamlLoaderSupport.requireSection(plugin, yaml, "artifacts", "artifacts.yml");
        if (section == null) {
            return 0;
        }

        int count = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) continue;

            String displayName = entry.getString("display_name", id);
            String description = entry.getString("description", "");
            Rarity rarity = YamlParseSupport.parseEnum(
                plugin,
                Rarity.class,
                entry.getString("rarity"),
                Rarity.COMMON,
                "artifacts." + id + ".rarity"
            );

            Artifact artifact;
            if (entry.contains("texture_base64")) {
                artifact = new Artifact(id, displayName, description, rarity,
                        entry.getString("texture_base64"));
            } else {
            Material material = YamlParseSupport.parseEnum(
                plugin,
                Material.class,
                entry.getString("material"),
                Material.BARRIER,
                "artifacts." + id + ".material"
            );
                artifact = new Artifact(id, displayName, description, rarity, material);
            }

            registry.register(artifact);
            count++;
        }

        plugin.getLogger().info("Loaded " + count + " artifacts from artifacts.yml");
        return count;
    }
}

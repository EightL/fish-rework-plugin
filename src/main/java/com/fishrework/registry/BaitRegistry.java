package com.fishrework.registry;

import com.fishrework.FishRework;
import com.fishrework.model.Bait;
import com.fishrework.model.Rarity;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Registry for bait definitions. Loaded from config.yml.
 */
public class BaitRegistry {

    private final Map<String, Bait> baits = new LinkedHashMap<>();

    /**
     * Loads all bait definitions from the plugin's config.yml under "baits:".
     */
    public void loadFromConfig(FishRework plugin) {
        baits.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("baits");
        if (section == null) {
            plugin.getLogger().warning("No 'baits' section found in config.yml — skipping bait loading.");
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection baitSection = section.getConfigurationSection(id);
            if (baitSection == null) continue;

            String displayName = baitSection.getString("display_name", id);
            String materialStr = baitSection.getString("material", "WHEAT_SEEDS");
            Material material;
            try {
                material = Material.valueOf(materialStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material '" + materialStr + "' for bait '" + id + "' — defaulting to WHEAT_SEEDS.");
                material = Material.WHEAT_SEEDS;
            }

            String rarityStr = baitSection.getString("rarity", "COMMON");
            Rarity rarity;
            try {
                rarity = Rarity.valueOf(rarityStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                rarity = Rarity.COMMON;
            }

            String description = baitSection.getString("description", "A fishing bait.");

            // Load bonuses
            Map<String, Double> bonuses = new HashMap<>();
            ConfigurationSection bonusSection = baitSection.getConfigurationSection("bonuses");
            if (bonusSection != null) {
                for (String key : bonusSection.getKeys(false)) {
                    bonuses.put(key, bonusSection.getDouble(key, 0.0));
                }
            }

            Bait bait = new Bait(id, displayName, material, rarity, description, bonuses);
            baits.put(id, bait);
        }

        plugin.getLogger().info("Loaded " + baits.size() + " bait definitions.");
    }

    public Bait get(String id) {
        return baits.get(id);
    }

    public Collection<Bait> getAll() {
        return baits.values();
    }

    public Set<String> getAllIds() {
        return baits.keySet();
    }

    public int size() {
        return baits.size();
    }
}

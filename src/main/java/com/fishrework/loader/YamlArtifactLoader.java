package com.fishrework.loader;

import com.fishrework.FishRework;
import com.fishrework.model.Artifact;
import com.fishrework.model.ArtifactPassiveEffect;
import com.fishrework.model.ArtifactPassiveStat;
import com.fishrework.model.ArtifactPassiveType;
import com.fishrework.model.Rarity;
import com.fishrework.registry.ArtifactRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
            List<ArtifactPassiveEffect> passiveEffects = parsePassiveEffects(id, entry);

            Artifact artifact;
            if (entry.contains("texture_base64")) {
                artifact = new Artifact(id, displayName, description, rarity,
                        entry.getString("texture_base64"), passiveEffects);
            } else {
                Material material = YamlParseSupport.parseEnum(
                    plugin,
                    Material.class,
                    entry.getString("material"),
                    Material.BARRIER,
                    "artifacts." + id + ".material"
                );
                artifact = new Artifact(id, displayName, description, rarity, material, passiveEffects);
            }

            registry.register(artifact);
            count++;
        }

        plugin.getLogger().info("Loaded " + count + " artifacts from artifacts.yml");
        return count;
    }

    private List<ArtifactPassiveEffect> parsePassiveEffects(String artifactId, ConfigurationSection entry) {
        List<Map<?, ?>> rawEffects = entry.getMapList("passive_effects");
        if (rawEffects.isEmpty()) return List.of();

        List<ArtifactPassiveEffect> parsed = new ArrayList<>();
        for (int i = 0; i < rawEffects.size(); i++) {
            Map<?, ?> raw = rawEffects.get(i);
            String pathPrefix = "artifacts." + artifactId + ".passive_effects[" + i + "]";

            String typeValue = asString(raw.get("type"));
            ArtifactPassiveType type = ArtifactPassiveType.fromConfigValue(typeValue);
            if (type == null) {
                plugin.getLogger().warning("[Fish Rework] Skipping invalid passive type at " + pathPrefix + ".type: " + typeValue);
                continue;
            }

            if (type == ArtifactPassiveType.STAT_BONUS) {
                String statValue = asString(raw.get("stat"));
                ArtifactPassiveStat stat = ArtifactPassiveStat.fromConfigValue(statValue);
                if (stat == null) {
                    plugin.getLogger().warning("[Fish Rework] Skipping invalid passive stat at " + pathPrefix + ".stat: " + statValue);
                    continue;
                }

                Double value = asDouble(raw.get("value"));
                if (value == null) {
                    plugin.getLogger().warning("[Fish Rework] Skipping invalid passive value at " + pathPrefix + ".value");
                    continue;
                }

                parsed.add(ArtifactPassiveEffect.statBonus(stat, value));
                continue;
            }

            String potionValue = asString(raw.get("potion"));
            PotionEffectType potionType = parsePotionType(potionValue);
            if (potionType == null) {
                plugin.getLogger().warning("[Fish Rework] Skipping invalid passive potion at " + pathPrefix + ".potion: " + potionValue);
                continue;
            }

            int amplifier = Math.max(0, asInt(raw.get("amplifier"), 0));
            int durationTicks = Math.max(20, asInt(raw.get("duration_ticks"), 60));
            parsed.add(ArtifactPassiveEffect.potion(potionType, amplifier, durationTicks));
        }

        return List.copyOf(parsed);
    }

    private PotionEffectType parsePotionType(String value) {
        if (value == null || value.isBlank()) return null;
        return PotionEffectType.getByName(value.trim().toUpperCase(Locale.ROOT));
    }

    private String asString(Object value) {
        if (value == null) return null;
        return String.valueOf(value);
    }

    private Double asDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) return null;
            if (!trimmed.matches("-?\\d+(\\.\\d+)?")) return null;
            return Double.parseDouble(trimmed);
        }
        return null;
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) return fallback;
            if (!trimmed.matches("-?\\d+")) return fallback;
            return Integer.parseInt(trimmed);
        }
        return fallback;
    }
}

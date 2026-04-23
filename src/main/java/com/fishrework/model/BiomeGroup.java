package com.fishrework.model;

import com.fishrework.manager.LanguageManager;
import org.bukkit.block.Biome;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Groups Minecraft biomes into logical categories for fishing adjustments.
 * Each group can have its own fishing profile that modifies spawn weights.
 * <p>
 * Supports vanilla biomes via the {@link Biome} enum AND custom datapack biomes
 * (Terralith, Tectonic, Incendium) via string-based namespaced key lookup.
 */
public enum BiomeGroup {
    COLD_OCEAN,
    FROZEN_OCEAN,
    NORMAL_OCEAN,
    LUKEWARM_OCEAN,
    WARM_OCEAN,
    RIVER,
    SWAMP,
    BEACH,
    LUSH_CAVES,
    FOREST,
    TAIGA,
    SNOWY,
    PLAINS,
    JUNGLE,
    SAVANNA,
    DESERT,
    MOUNTAINS,
    BADLANDS,
    MUSHROOM,
    MEADOW,
    DEEP_DARK,
    PALE_GARDEN,
    NETHER_WASTES,
    CRIMSON_FOREST,
    WARPED_FOREST,
    SOUL_SAND_VALLEY,
    BASALT_DELTAS,
    OTHER;

    private static final Map<Biome, BiomeGroup> MAPPING = new HashMap<>();
    /** String-based biome key mapping for datapack biomes (Terralith, Tectonic, etc.) */
    private static final Map<String, BiomeGroup> KEY_MAPPING = new HashMap<>();

    static {
        // ══════════════════════════════════════════════════════════
        //  Vanilla Biome Enum Mappings
        // ══════════════════════════════════════════════════════════

        // ── Oceans ──
        map(Biome.COLD_OCEAN, COLD_OCEAN);
        map(Biome.DEEP_COLD_OCEAN, COLD_OCEAN);

        map(Biome.FROZEN_OCEAN, FROZEN_OCEAN);
        map(Biome.DEEP_FROZEN_OCEAN, FROZEN_OCEAN);

        map(Biome.OCEAN, NORMAL_OCEAN);
        map(Biome.DEEP_OCEAN, NORMAL_OCEAN);

        map(Biome.LUKEWARM_OCEAN, LUKEWARM_OCEAN);
        map(Biome.DEEP_LUKEWARM_OCEAN, LUKEWARM_OCEAN);

        map(Biome.WARM_OCEAN, WARM_OCEAN);

        // ── Rivers ──
        map(Biome.RIVER, RIVER);
        map(Biome.FROZEN_RIVER, RIVER);

        // ── Swamps ──
        map(Biome.SWAMP, SWAMP);
        map(Biome.MANGROVE_SWAMP, SWAMP);

        // ── Beaches ──
        map(Biome.BEACH, BEACH);
        map(Biome.SNOWY_BEACH, BEACH);
        map(Biome.STONY_SHORE, BEACH);

        // ── Lush Caves ──
        map(Biome.LUSH_CAVES, LUSH_CAVES);

        // ── Forests ──
        map(Biome.FOREST, FOREST);
        map(Biome.BIRCH_FOREST, FOREST);
        map(Biome.DARK_FOREST, FOREST);
        map(Biome.OLD_GROWTH_BIRCH_FOREST, FOREST);
        map(Biome.FLOWER_FOREST, FOREST);

        // ── Taiga ──
        map(Biome.TAIGA, TAIGA);
        map(Biome.OLD_GROWTH_PINE_TAIGA, TAIGA);
        map(Biome.OLD_GROWTH_SPRUCE_TAIGA, TAIGA);
        map(Biome.SNOWY_TAIGA, TAIGA);
        map(Biome.GROVE, TAIGA);

        // ── Snowy ──
        map(Biome.SNOWY_PLAINS, SNOWY);
        map(Biome.ICE_SPIKES, SNOWY);
        map(Biome.SNOWY_SLOPES, SNOWY);
        map(Biome.FROZEN_PEAKS, SNOWY);

        // ── Plains ──
        map(Biome.PLAINS, PLAINS);
        map(Biome.SUNFLOWER_PLAINS, PLAINS);

        // ── Jungle ──
        map(Biome.JUNGLE, JUNGLE);
        map(Biome.BAMBOO_JUNGLE, JUNGLE);
        map(Biome.SPARSE_JUNGLE, JUNGLE);

        // ── Savanna ──
        map(Biome.SAVANNA, SAVANNA);
        map(Biome.SAVANNA_PLATEAU, SAVANNA);
        map(Biome.WINDSWEPT_SAVANNA, SAVANNA);

        // ── Desert ──
        map(Biome.DESERT, DESERT);

        // ── Mountains ──
        map(Biome.WINDSWEPT_HILLS, MOUNTAINS);
        map(Biome.WINDSWEPT_GRAVELLY_HILLS, MOUNTAINS);
        map(Biome.WINDSWEPT_FOREST, MOUNTAINS);
        map(Biome.JAGGED_PEAKS, MOUNTAINS);
        map(Biome.STONY_PEAKS, MOUNTAINS);

        // ── Badlands ──
        map(Biome.BADLANDS, BADLANDS);
        map(Biome.ERODED_BADLANDS, BADLANDS);
        map(Biome.WOODED_BADLANDS, BADLANDS);

        // ── Mushroom Fields ──
        map(Biome.MUSHROOM_FIELDS, MUSHROOM);

        // ── Meadow ──
        map(Biome.MEADOW, MEADOW);

        // ── Deep Dark ──
        map(Biome.DEEP_DARK, DEEP_DARK);

        // ── Pale Garden ──
        map(Biome.PALE_GARDEN, PALE_GARDEN);

        // ── Nether ──
        map(Biome.NETHER_WASTES, NETHER_WASTES);
        map(Biome.CRIMSON_FOREST, CRIMSON_FOREST);
        map(Biome.WARPED_FOREST, WARPED_FOREST);
        map(Biome.SOUL_SAND_VALLEY, SOUL_SAND_VALLEY);
        map(Biome.BASALT_DELTAS, BASALT_DELTAS);

        // ── Dripstone Caves (treated as cave biome) ──
        map(Biome.DRIPSTONE_CAVES, LUSH_CAVES);

        // ══════════════════════════════════════════════════════════
        //  Tectonic — Cave/River biomes
        // ══════════════════════════════════════════════════════════
        mapKey("tectonic:lush_river", LUSH_CAVES);
        mapKey("tectonic:dripstone_river", LUSH_CAVES);
        mapKey("tectonic:lantern_river", LUSH_CAVES);
        mapKey("tectonic:icy_river", LUSH_CAVES);
        mapKey("tectonic:coral_river", LUSH_CAVES);
        mapKey("tectonic:ancient_river", LUSH_CAVES);
        mapKey("tectonic:old_growth_river", LUSH_CAVES);
        mapKey("tectonic:white_cliffs", BEACH);

        // ══════════════════════════════════════════════════════════
        //  Terralith — Surface biomes
        // ══════════════════════════════════════════════════════════

        // ── Forest ──
        mapKey("terralith:lavender_forest", FOREST);
        mapKey("terralith:moonlight_grove", FOREST);
        mapKey("terralith:moonlight_valley", FOREST);
        mapKey("terralith:sakura_grove", FOREST);
        mapKey("terralith:sakura_valley", FOREST);
        mapKey("terralith:wintry_forest", FOREST);
        mapKey("terralith:cloud_forest", FOREST);
        mapKey("terralith:forested_highlands", FOREST);

        // ── Taiga ──
        mapKey("terralith:birch_taiga", TAIGA);
        mapKey("terralith:siberian_taiga", TAIGA);
        mapKey("terralith:siberian_grove", TAIGA);

        // ── Jungle ──
        mapKey("terralith:jungle_mountains", JUNGLE);
        mapKey("terralith:rocky_jungle", JUNGLE);
        mapKey("terralith:tropical_jungle", JUNGLE);
        mapKey("terralith:amethyst_rainforest", JUNGLE);

        // ── Savanna ──
        mapKey("terralith:ashen_savanna", SAVANNA);
        mapKey("terralith:fractured_savanna", SAVANNA);
        mapKey("terralith:hot_shrubland", SAVANNA);
        mapKey("terralith:brushland", SAVANNA);
        mapKey("terralith:savanna_badlands", SAVANNA);
        mapKey("terralith:savanna_slopes", SAVANNA);

        // ── Desert ──
        mapKey("terralith:ancient_sands", DESERT);
        mapKey("terralith:desert_canyon", DESERT);
        mapKey("terralith:desert_oasis", DESERT);
        mapKey("terralith:desert_spires", DESERT);
        mapKey("terralith:gravel_desert", DESERT);
        mapKey("terralith:lush_desert", DESERT);
        mapKey("terralith:red_oasis", DESERT);
        mapKey("terralith:sandstone_valley", DESERT);

        // ── Mountains ──
        mapKey("terralith:alpine_grove", MOUNTAINS);
        mapKey("terralith:alpine_highlands", MOUNTAINS);
        mapKey("terralith:arid_highlands", MOUNTAINS);
        mapKey("terralith:basalt_cliffs", MOUNTAINS);
        mapKey("terralith:emerald_peaks", MOUNTAINS);
        mapKey("terralith:frozen_cliffs", MOUNTAINS);
        mapKey("terralith:granite_cliffs", MOUNTAINS);
        mapKey("terralith:haze_mountain", MOUNTAINS);
        mapKey("terralith:painted_mountains", MOUNTAINS);
        mapKey("terralith:rocky_mountains", MOUNTAINS);
        mapKey("terralith:scarlet_mountains", MOUNTAINS);
        mapKey("terralith:stony_spires", MOUNTAINS);
        mapKey("terralith:temperate_highlands", MOUNTAINS);
        mapKey("terralith:volcanic_crater", MOUNTAINS);
        mapKey("terralith:volcanic_peaks", MOUNTAINS);
        mapKey("terralith:windswept_spires", MOUNTAINS);
        mapKey("terralith:yosemite_cliffs", MOUNTAINS);
        mapKey("terralith:yosemite_lowlands", MOUNTAINS);
        mapKey("terralith:caldera", MOUNTAINS);

        // ── Snowy ──
        mapKey("terralith:glacial_chasm", SNOWY);
        mapKey("terralith:ice_marsh", SNOWY);
        mapKey("terralith:wintry_lowlands", SNOWY);
        mapKey("terralith:snowy_badlands", SNOWY);
        mapKey("terralith:snowy_cherry_grove", SNOWY);
        mapKey("terralith:snowy_maple_forest", SNOWY);
        mapKey("terralith:snowy_shield", SNOWY);
        mapKey("terralith:cold_shrubland", SNOWY);

        // ── Badlands ──
        mapKey("terralith:bryce_canyon", BADLANDS);
        mapKey("terralith:warped_mesa", BADLANDS);
        mapKey("terralith:white_mesa", BADLANDS);
        mapKey("terralith:amethyst_canyon", BADLANDS);

        // ── Plains ──
        mapKey("terralith:blooming_plateau", PLAINS);
        mapKey("terralith:blooming_valley", PLAINS);
        mapKey("terralith:highlands", PLAINS);
        mapKey("terralith:lavender_valley", PLAINS);
        mapKey("terralith:lush_valley", PLAINS);
        mapKey("terralith:shrubland", PLAINS);
        mapKey("terralith:rocky_shrubland", PLAINS);
        mapKey("terralith:steppe", PLAINS);
        mapKey("terralith:valley_clearing", PLAINS);
        mapKey("terralith:yellowstone", PLAINS);

        // ── Meadow ──
        mapKey("terralith:shield", MEADOW);
        mapKey("terralith:shield_clearing", MEADOW);
        mapKey("terralith:white_cliffs", MEADOW);

        // ── Swamp ──
        mapKey("terralith:orchid_swamp", SWAMP);

        // ── Beach / Islands ──
        mapKey("terralith:gravel_beach", BEACH);
        mapKey("terralith:alpha_islands", BEACH);
        mapKey("terralith:alpha_islands_winter", BEACH);
        mapKey("terralith:mirage_isles", BEACH);

        // ── River ──
        mapKey("terralith:warm_river", RIVER);

        // ── Ocean ──
        mapKey("terralith:deep_warm_ocean", WARM_OCEAN);

        // ── Skylands (floating — treat as neutral ocean) ──
        mapKey("terralith:skylands_autumn", NORMAL_OCEAN);
        mapKey("terralith:skylands_spring", NORMAL_OCEAN);
        mapKey("terralith:skylands_summer", NORMAL_OCEAN);
        mapKey("terralith:skylands_winter", NORMAL_OCEAN);

        // ══════════════════════════════════════════════════════════
        //  Terralith — Cave biomes
        // ══════════════════════════════════════════════════════════
        mapKey("terralith:cave/andesite_caves", LUSH_CAVES);
        mapKey("terralith:cave/crystal_caves", LUSH_CAVES);
        mapKey("terralith:cave/deep_caves", DEEP_DARK);
        mapKey("terralith:cave/diorite_caves", LUSH_CAVES);
        mapKey("terralith:cave/frostfire_caves", LUSH_CAVES);
        mapKey("terralith:cave/fungal_caves", LUSH_CAVES);
        mapKey("terralith:cave/granite_caves", LUSH_CAVES);
        mapKey("terralith:cave/infested_caves", LUSH_CAVES);
        mapKey("terralith:cave/mantle_caves", DEEP_DARK);
        mapKey("terralith:cave/thermal_caves", LUSH_CAVES);
        mapKey("terralith:cave/tuff_caves", LUSH_CAVES);
        mapKey("terralith:cave/underground_jungle", JUNGLE);

        // ══════════════════════════════════════════════════════════
        //  Incendium — Nether biome fallbacks
        // ══════════════════════════════════════════════════════════
        mapKey("incendium:ash_barrens", NETHER_WASTES);
        mapKey("incendium:infernal_dunes", NETHER_WASTES);
        mapKey("incendium:inverted_forest", WARPED_FOREST);
        mapKey("incendium:quartz_flats", NETHER_WASTES);
        mapKey("incendium:toxic_heap", BASALT_DELTAS);
        mapKey("incendium:volcanic_deltas", BASALT_DELTAS);
        mapKey("incendium:weeping_valley", SOUL_SAND_VALLEY);
        mapKey("incendium:withered_forest", SOUL_SAND_VALLEY);
    }

    private static void map(Biome biome, BiomeGroup group) {
        MAPPING.put(biome, group);
    }

    private static void mapKey(String key, BiomeGroup group) {
        KEY_MAPPING.put(key, group);
    }

    /**
     * Resolves a Minecraft biome to its BiomeGroup.
     * Returns {@link #OTHER} for unmapped biomes.
     */
    public static BiomeGroup fromBiome(Biome biome) {
        return MAPPING.getOrDefault(biome, OTHER);
    }

    /**
     * Resolves a biome by its namespaced key string (e.g. "terralith:lavender_forest").
     * Used for custom datapack biomes that don't appear in the Biome enum.
     * Returns {@link #OTHER} for unmapped keys.
     */
    public static BiomeGroup fromBiomeKey(String key) {
        return KEY_MAPPING.getOrDefault(key, OTHER);
    }

    public String getLocalizedName(LanguageManager languageManager) {
        return languageManager.getString(
                "biomegroup." + name().toLowerCase(Locale.ROOT) + ".name",
                toFriendlyName());
    }

    private String toFriendlyName() {
        String[] words = name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            builder.append(word.substring(1));
        }
        return builder.toString();
    }
}

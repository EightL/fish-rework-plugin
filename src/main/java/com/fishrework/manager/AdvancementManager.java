package com.fishrework.manager;

import com.fishrework.FishRework;
import com.fishrework.model.CustomMob;
import com.fishrework.model.Skill;
import com.fishrework.registry.RecipeDefinition;
import com.fishrework.util.FeatureKeys;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Manages custom advancements.
 * Progression advancement descriptions are auto-generated from MobRegistry
 * and RecipeRegistry data so adding content keeps advancements in sync.
 */
public class AdvancementManager {

    private final FishRework plugin;
    public final NamespacedKey ROOT_KEY;
    public final NamespacedKey FISHH_KEY;
    public final NamespacedKey CATCH_ALL_KEY;
    public final NamespacedKey KILL_DEAD_RIDER_KEY;
    public final NamespacedKey KILL_ELDER_GUARDIAN_KEY;
    public final NamespacedKey KILL_POSEIDON_KEY;
    public final NamespacedKey KILL_SPIDER_JOCKEY_KEY;
    public final NamespacedKey KILL_VULTURE_KING_KEY;
    public final NamespacedKey KILL_KING_SLIME_KEY;
    public final NamespacedKey KILL_WARDEN_KEY;
    public final NamespacedKey BUCKET_CATCHER_KEY;

    public final NamespacedKey ARTIFACT_COMMON_KEY;
    public final NamespacedKey ARTIFACT_UNCOMMON_KEY;
    public final NamespacedKey ARTIFACT_RARE_KEY;
    public final NamespacedKey ARTIFACT_EPIC_KEY;
    public final NamespacedKey ARTIFACT_LEGENDARY_KEY;
    public final NamespacedKey ARTIFACT_ALL_KEY;
    
    public final NamespacedKey FISHING_COMPLETIONIST_KEY;

    public AdvancementManager(FishRework plugin) {
        this.plugin = plugin;
        this.ROOT_KEY = new NamespacedKey(plugin, "fishing/root");
        this.FISHH_KEY = new NamespacedKey(plugin, "fishing/fishh");
        this.CATCH_ALL_KEY = new NamespacedKey(plugin, "fishing/catch_all");
        this.KILL_DEAD_RIDER_KEY = new NamespacedKey(plugin, "fishing/kill_dead_rider");
        this.KILL_ELDER_GUARDIAN_KEY = new NamespacedKey(plugin, "fishing/kill_elder_guardian");
        this.KILL_POSEIDON_KEY = new NamespacedKey(plugin, "fishing/kill_poseidon");
        this.KILL_SPIDER_JOCKEY_KEY = new NamespacedKey(plugin, "fishing/kill_spider_jockey");
        this.KILL_VULTURE_KING_KEY = new NamespacedKey(plugin, "fishing/kill_vulture_king");
        this.KILL_KING_SLIME_KEY = new NamespacedKey(plugin, "fishing/kill_king_slime");
        this.KILL_WARDEN_KEY = new NamespacedKey(plugin, "fishing/kill_warden");
        this.BUCKET_CATCHER_KEY = new NamespacedKey(plugin, "fishing/bucket_catcher");

        this.ARTIFACT_COMMON_KEY = new NamespacedKey(plugin, "fishing/artifact_common");
        this.ARTIFACT_UNCOMMON_KEY = new NamespacedKey(plugin, "fishing/artifact_uncommon");
        this.ARTIFACT_RARE_KEY = new NamespacedKey(plugin, "fishing/artifact_rare");
        this.ARTIFACT_EPIC_KEY = new NamespacedKey(plugin, "fishing/artifact_epic");
        this.ARTIFACT_LEGENDARY_KEY = new NamespacedKey(plugin, "fishing/artifact_legendary");
        this.ARTIFACT_ALL_KEY = new NamespacedKey(plugin, "fishing/artifact_all");
        
        this.FISHING_COMPLETIONIST_KEY = new NamespacedKey(plugin, "fishing/completionist");
    }

    // ── Load / Unload ─────────────────────────────────────────

    public void unloadAdvancements() {
        for (int level = 50; level >= 5; level -= 5) {
            safeRemove(getProgressionKey(level));
        }
        
        // Remove group advancements
        for (BiomeAdvancementGroup group : GROUPS) {
            safeRemove(getGroupAllKey(group));
            safeRemove(getGroupLandKey(group));
        }

        safeRemove(FISHING_COMPLETIONIST_KEY);
        
        safeRemove(ARTIFACT_ALL_KEY);
        safeRemove(ARTIFACT_LEGENDARY_KEY);
        safeRemove(ARTIFACT_EPIC_KEY);
        safeRemove(ARTIFACT_RARE_KEY);
        safeRemove(ARTIFACT_UNCOMMON_KEY);
        safeRemove(ARTIFACT_COMMON_KEY);

        safeRemove(KILL_WARDEN_KEY);
        safeRemove(KILL_KING_SLIME_KEY);
        safeRemove(KILL_VULTURE_KING_KEY);
        safeRemove(KILL_SPIDER_JOCKEY_KEY);
        safeRemove(KILL_POSEIDON_KEY);
        safeRemove(KILL_ELDER_GUARDIAN_KEY);
        safeRemove(KILL_DEAD_RIDER_KEY);
        safeRemove(CATCH_ALL_KEY);
        safeRemove(FISHH_KEY);
        safeRemove(ROOT_KEY);
        plugin.getLogger().info("Unloaded custom advancements.");
    }
    
    // ── Sync ──────────────────────────────────────────────────

    /**
     * Syncs a player's advancements and recipe discoveries with their level.
     */
    public void syncAdvancements(Player player, int fishingLevel) {
        if (!plugin.isFeatureEnabled(FeatureKeys.ADVANCEMENTS_ENABLED)) {
            plugin.getRecipeRegistry().syncRecipes(player);
            return;
        }

        grantAdvancement(player, ROOT_KEY);

        for (int level = 5; level <= 50; level += 5) {
            if (fishingLevel >= level) {
                grantAdvancement(player, getProgressionKey(level));
            }
        }

        // Check catch-all
        checkCatchAll(player);
        
        // Check biomes
        checkBiomeAdvancements(player);

        // Check artifacts
        checkArtifactAdvancements(player);

        // Check completionist (Must be last)
        checkCompletionistAdvancement(player);

        // Discover and sync recipes (handles levels AND advancements)
        plugin.getRecipeRegistry().syncRecipes(player);
    }

    public void checkCatchAll(Player player) {
        if (!plugin.isFeatureEnabled(FeatureKeys.ADVANCEMENTS_ENABLED)) return;
        if (hasAdvancement(player, CATCH_ALL_KEY)) return;

        com.fishrework.model.PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        java.util.List<String> allMobIds = plugin.getMobRegistry().getAllIds();
        if (data.hasCaughtAll(allMobIds)) {
            grantAdvancement(player, CATCH_ALL_KEY);
        }
    }

    public void loadAdvancements() {
        if (!plugin.isFeatureEnabled(FeatureKeys.ADVANCEMENTS_ENABLED)) {
            return;
        }
        unloadAdvancements();

        // Root tab
        safeLoad(ROOT_KEY, buildRootJson());

        // Fishh — catch a custom mob
        safeLoad(FISHH_KEY, buildFishhJson());
        
        // Bucket Catcher
        safeLoad(BUCKET_CATCHER_KEY, buildBucketCatcherJson());

        // Catch All — catch every custom mob
        safeLoad(CATCH_ALL_KEY, buildCatchAllJson());

        // Dead Rider Kill
        safeLoad(KILL_DEAD_RIDER_KEY, buildKillDeadRiderJson());

        // Elder Guardian Kill
        safeLoad(KILL_ELDER_GUARDIAN_KEY, buildKillElderGuardianJson());

        // Poseidon Kill
        safeLoad(KILL_POSEIDON_KEY, buildKillBossJson("God of the Sea", "Defeat Poseidon and his dolphin steed.", "minecraft:trident"));

        // Spider Jockey Kill
        safeLoad(KILL_SPIDER_JOCKEY_KEY, buildKillBossJson("Arachne Slayer", "Defeat the Arachne Knight and its spider mount.", "minecraft:spider_eye"));

        // Vulture King Kill
        safeLoad(KILL_VULTURE_KING_KEY, buildKillBossJson("King of Carrion", "Defeat the Vulture King.", "minecraft:phantom_membrane"));

        // King Slime Kill
        safeLoad(KILL_KING_SLIME_KEY, buildKillBossJson("Slime Sovereign", "Defeat the King Slime and all of its children.", "minecraft:slime_ball"));

        // Warden Kill
        safeLoad(KILL_WARDEN_KEY, buildKillBossJson("Warden Vanquisher", "Defeat the mighty Warden.", "minecraft:sculk_catalyst"));

        // Progression levels 5-50
        NamespacedKey previousKey = ROOT_KEY;
        for (int level = 5; level <= 50; level += 5) {
            NamespacedKey currentKey = getProgressionKey(level);
            safeLoad(currentKey, buildProgressionJson(previousKey, level));
            previousKey = currentKey;
        }

        // Biome Advancements (Consolidated)
        for (BiomeAdvancementGroup group : GROUPS) {
            // 1. Land Mobs (if group has any)
            if (!getGroupLandRequirements(group).isEmpty()) {
                safeLoad(getGroupLandKey(group), buildGroupLandJson(group));
            }

            // 2. All Creatures
            safeLoad(getGroupAllKey(group), buildGroupAllJson(group));
        }

        // Artifacts
        safeLoad(ARTIFACT_COMMON_KEY, buildArtifactRarityJson("Common Collector", "Collect all Common artifacts.", "minecraft:iron_nugget", com.fishrework.model.Rarity.COMMON, ROOT_KEY));
        safeLoad(ARTIFACT_UNCOMMON_KEY, buildArtifactRarityJson("Uncommon Collector", "Collect all Uncommon artifacts.", "minecraft:gold_nugget", com.fishrework.model.Rarity.UNCOMMON, ARTIFACT_COMMON_KEY));
        safeLoad(ARTIFACT_RARE_KEY, buildArtifactRarityJson("Rare Collector", "Collect all Rare artifacts.", "minecraft:diamond", com.fishrework.model.Rarity.RARE, ARTIFACT_UNCOMMON_KEY));
        safeLoad(ARTIFACT_EPIC_KEY, buildArtifactRarityJson("Epic Collector", "Collect all Epic artifacts.", "minecraft:emerald", com.fishrework.model.Rarity.EPIC, ARTIFACT_RARE_KEY));
        safeLoad(ARTIFACT_LEGENDARY_KEY, buildArtifactRarityJson("Legendary Collector", "Collect all Legendary artifacts.", "minecraft:nether_star", com.fishrework.model.Rarity.LEGENDARY, ARTIFACT_EPIC_KEY));
        
        safeLoad(ARTIFACT_ALL_KEY, buildArtifactAllJson());

        // Completionist
        safeLoad(FISHING_COMPLETIONIST_KEY, buildCompletionistJson());

        plugin.getLogger().info("Loaded custom advancements (root + fishh + levels 5-50 + bosses + biome groups + artifacts + completionist).");
    }

    // ── JSON Builders ─────────────────────────────────────────

    private String buildGroupLandJson(BiomeAdvancementGroup group) {
        return "{\n" +
                "  \"parent\": \"" + FISHH_KEY + "\",\n" +
                "  \"display\": {\n" +
                "    \"icon\": { \"id\": \"" + group.icon + "\" },\n" +
                "    \"title\": \"" + group.displayName + " Hunter\",\n" +
                "    \"description\": \"Catch all land mobs from " + group.description + ".\",\n" +
                "    \"frame\": \"task\",\n" +
                "    \"show_toast\": true,\n" +
                "    \"announce_to_chat\": true\n" +
                "  },\n" +
                "  \"criteria\": { \"impossible\": { \"trigger\": \"minecraft:impossible\" } }\n" +
                "}";
    }

    private String buildGroupAllJson(BiomeAdvancementGroup group) {
        // Parent: Land mobs if exists, else Fishh
        NamespacedKey parent = getGroupLandRequirements(group).isEmpty() ? FISHH_KEY : getGroupLandKey(group);
        
        return "{\n" +
                "  \"parent\": \"" + parent + "\",\n" +
                "  \"display\": {\n" +
                "    \"icon\": { \"id\": \"" + group.icon + "\", \"nbt\": \"{Enchantments:[{id:k,lvl:1}]}\" },\n" +
                "    \"title\": \"" + group.displayName + " Completed\",\n" +
                "    \"description\": \"Catch all creatures from " + group.description + ".\",\n" +
                "    \"frame\": \"challenge\",\n" +
                "    \"show_toast\": true,\n" +
                "    \"announce_to_chat\": true\n" +
                "  },\n" +
                "  \"criteria\": { \"impossible\": { \"trigger\": \"minecraft:impossible\" } }\n" +
                "}";
    }

    private String buildKillDeadRiderJson() {
        return "{\n" +
                "  \"parent\": \"" + FISHH_KEY + "\",\n" +
                "  \"display\": {\n" +
                "    \"icon\": { \"id\": \"minecraft:netherite_sword\", \"nbt\": \"{Enchantments:[{id:sharpness,lvl:5}]}\" },\n" +
                "    \"title\": \"Abyssal Rider\",\n" +
                "    \"description\": \"Defeat the legendary Dead Rider.\",\n" +
                "    \"frame\": \"challenge\",\n" +
                "    \"show_toast\": true,\n" +
                "    \"announce_to_chat\": true\n" +
                "  },\n" +
                "  \"criteria\": { \"impossible\": { \"trigger\": \"minecraft:impossible\" } }\n" +
                "}";
    }

    private String buildKillElderGuardianJson() {
        return "{\n" +
                "  \"parent\": \"" + FISHH_KEY + "\",\n" +
                "  \"display\": {\n" +
                "    \"icon\": { \"id\": \"minecraft:elder_guardian_spawn_egg\" },\n" +
                "    \"title\": \"Guardian Slayer\",\n" +
                "    \"description\": \"Defeat the ancient Elder Guardian.\",\n" +
                "    \"frame\": \"challenge\",\n" +
                "    \"show_toast\": true,\n" +
                "    \"announce_to_chat\": true\n" +
                "  },\n" +
                "  \"criteria\": { \"impossible\": { \"trigger\": \"minecraft:impossible\" } }\n" +
                "}";
    }

    /** Generic kill-boss advancement JSON builder. */
    private String buildKillBossJson(String title, String description, String icon) {
        return "{\n" +
                "  \"parent\": \"" + FISHH_KEY + "\",\n" +
                "  \"display\": {\n" +
                "    \"icon\": { \"id\": \"" + icon + "\" },\n" +
                "    \"title\": \"" + title + "\",\n" +
                "    \"description\": \"" + description + "\",\n" +
                "    \"frame\": \"challenge\",\n" +
                "    \"show_toast\": true,\n" +
                "    \"announce_to_chat\": true\n" +
                "  },\n" +
                "  \"criteria\": { \"impossible\": { \"trigger\": \"minecraft:impossible\" } }\n" +
                "}";
    }

    private String buildArtifactRarityJson(String title, String description, String icon, com.fishrework.model.Rarity rarity, NamespacedKey parent) {
        String frame = rarity == com.fishrework.model.Rarity.LEGENDARY ? "challenge" : "task";
        return "{\n" +
                "  \"parent\": \"" + parent + "\",\n" +
                "  \"display\": {\n" +
                "    \"icon\": { \"id\": \"" + icon + "\" },\n" +
                "    \"title\": \"" + title + "\",\n" +
                "    \"description\": \"" + description + "\",\n" +
                "    \"frame\": \"" + frame + "\",\n" +
                "    \"show_toast\": true,\n" +
                "    \"announce_to_chat\": true\n" +
                "  },\n" +
                "  \"criteria\": { \"impossible\": { \"trigger\": \"minecraft:impossible\" } }\n" +
                "}";
    }

    private String buildArtifactAllJson() {
        return "{\n" +
                "  \"parent\": \"" + ARTIFACT_LEGENDARY_KEY + "\",\n" +
                "  \"display\": {\n" +
                "    \"icon\": { \"id\": \"minecraft:beacon\" },\n" +
                "    \"title\": \"Museum Curator\",\n" +
                "    \"description\": \"Collect every single artifact.\",\n" +
                "    \"frame\": \"challenge\",\n" +
                "    \"show_toast\": true,\n" +
                "    \"announce_to_chat\": true\n" +
                "  },\n" +
                "  \"criteria\": { \"impossible\": { \"trigger\": \"minecraft:impossible\" } }\n" +
                "}";
    }

    private String buildCompletionistJson() {
        return "{\n" +
                "  \"parent\": \"" + CATCH_ALL_KEY + "\",\n" +
                "  \"display\": {\n" +
                "    \"icon\": { \"id\": \"minecraft:clock\", \"nbt\": \"{Enchantments:[{id:k,lvl:1}]}\" },\n" +
                "    \"title\": \"Fishing Completionist\",\n" +
                "    \"description\": \"Complete every fishing advancement.\",\n" +
                "    \"frame\": \"challenge\",\n" +
                "    \"show_toast\": true,\n" +
                "    \"announce_to_chat\": true\n" +
                "  },\n" +
                "  \"criteria\": { \"impossible\": { \"trigger\": \"minecraft:impossible\" } }\n" +
                "}";
    }

    // ── Sync ──────────────────────────────────────────────────


    // ── Grant / Check ─────────────────────────────────────────

    public void grantAdvancement(Player player, NamespacedKey key) {
        if (!plugin.isFeatureEnabled(FeatureKeys.ADVANCEMENTS_ENABLED)) return;
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) {
            plugin.getLogger().warning("Advancement not found: " + key);
            return;
        }
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        if (!progress.isDone()) {
            for (String criteria : progress.getRemainingCriteria()) {
                progress.awardCriteria(criteria);
            }
        }
        
        // Sync recipes whenever an advancement is granted
        plugin.getRecipeRegistry().syncRecipes(player);
    }

    public boolean hasAdvancement(Player player, NamespacedKey key) {
        if (!plugin.isFeatureEnabled(FeatureKeys.ADVANCEMENTS_ENABLED)) return false;
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) return false;
        return player.getAdvancementProgress(advancement).isDone();
    }

    public NamespacedKey getProgressionKey(int level) {
        return new NamespacedKey(plugin, "fishing/level_" + level);
    }

    // ── JSON Builders ─────────────────────────────────────────

    private String buildRootJson() {
        return "{\n" +
                "  \"display\": {\n" +
                "    \"icon\": { \"id\": \"minecraft:fishing_rod\" },\n" +
                "    \"title\": \"Fishing Encyclopedia\",\n" +
                "    \"description\": \"Begin your journey to master the waters and document your catches.\",\n" +
                "    \"background\": \"minecraft:block/dark_prismarine\",\n" +
                "    \"frame\": \"task\",\n" +
                "    \"show_toast\": true,\n" +
                "    \"announce_to_chat\": false\n" +
                "  },\n" +
                "  \"criteria\": { \"auto\": { \"trigger\": \"minecraft:location\" } }\n" +
                "}";
    }

    private String buildFishhJson() {
        return buildAdvancementJson(FISHH_KEY, ROOT_KEY, "minecraft:cod_spawn_egg", "Fishh", "Catch a verified Monster Fish.", "task", true, true);
    }

    private String buildBucketCatcherJson() {
        return buildAdvancementJson(BUCKET_CATCHER_KEY, FISHH_KEY, "minecraft:water_bucket", "Bucket Catcher", "Catch a creature using the Fish Bucket.", "task", true, true);
    }

    private String buildCatchAllJson() {
         // Custom icon logic handling for nbt
         String icon = "{ \"id\": \"minecraft:fishing_rod\", \"nbt\": \"{Enchantments:[{id:k,lvl:1}]}\" }";
         return buildAdvancementJson(CATCH_ALL_KEY, FISHH_KEY, icon, "Master Angler", "Catch or eliminate every type of unique fish.", "challenge", true, true);
    }

    private String buildProgressionJson(NamespacedKey parent, int level) {
        String icon = getIconForLevel(level);
        String frame = level == 50 ? "challenge" : "task";
        String description = buildLevelDescription(level);

        return "{\n" +
                "  \"parent\": \"" + parent + "\",\n" +
                "  \"display\": {\n" +
                "    \"icon\": { \"id\": \"" + icon + "\" },\n" +
                "    \"title\": \"Fishing Level " + level + "\",\n" +
                "    \"description\": \"" + description + "\",\n" +
                "    \"frame\": \"" + frame + "\",\n" +
                "    \"show_toast\": true,\n" +
                "    \"announce_to_chat\": true\n" +
                "  },\n" +
                "  \"criteria\": { \"impossible\": { \"trigger\": \"minecraft:impossible\" } }\n" +
                "}";
    }

    /**
     * Auto-generates level descriptions from RecipeRegistry (mobs excluded).
     * For milestone advancements, shows recipe unlocks for the entire milestone range.
     */
    private String buildLevelDescription(int level) {
        if (level == 50) return "Master Angler! Max fishing level reached.";

        // For milestone advancements (every 5 levels), show recipe unlocks from previous milestone + 1 to current level
        int startLevel = level - 4; // e.g., for level 10: 10 - 4 = 6
        if (startLevel < 1) startLevel = 1;
        
        List<String> allUnlocks = new ArrayList<>();
        for (int lvl = startLevel; lvl <= level; lvl++) {
            List<String> unlocks = plugin.getLevelManager().getUnlocksForAdvancement(Skill.FISHING, lvl);
            allUnlocks.addAll(unlocks);
        }
        
        StringJoiner sj = new StringJoiner(", ");
        for (String s : allUnlocks) {
            sj.add(s);
        }

        if (sj.length() > 0) {
            return "Unlocks: " + sj.toString();
        }
        return "Reach Fishing Level " + level + ".";
    }

    private String getUnlocksForAdvancement(NamespacedKey key) {
        List<RecipeDefinition> recipes = plugin.getRecipeRegistry().getRecipesForAdvancement(key);
        if (recipes.isEmpty()) return "";

        StringJoiner sj = new StringJoiner(", ");
        for (RecipeDefinition def : recipes) {
             org.bukkit.inventory.ItemStack result = def.getRecipe().getResult();
            String name;
            if (result.hasItemMeta() && result.getItemMeta().hasDisplayName()) {
                name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(result.getItemMeta().displayName());
            } else {
                String typeName = result.getType().name().toLowerCase().replace('_', ' ');
                StringBuilder sb = new StringBuilder();
                for (String word : typeName.split(" ")) {
                    if (!word.isEmpty()) {
                        sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
                    }
                }
                name = sb.toString().trim();
            }
            sj.add(name);
        }
        return "Unlocks: " + sj.toString();
    }

    private String buildAdvancementJson(NamespacedKey key, NamespacedKey parent, String iconIdOrJson, String title, String description, String frame, boolean toast, boolean chat) {
        String unlocks = getUnlocksForAdvancement(key);
        if (!unlocks.isEmpty()) {
            description += " " + unlocks;
        }

        String iconJson = iconIdOrJson.startsWith("{") ? iconIdOrJson : "{ \"id\": \"" + iconIdOrJson + "\" }";

        return "{\n" +
                "  \"parent\": \"" + parent + "\",\n" +
                "  \"display\": {\n" +
                "    \"icon\": " + iconJson + ",\n" +
                "    \"title\": \"" + title + "\",\n" +
                "    \"description\": \"" + description + "\",\n" +
                "    \"frame\": \"" + frame + "\",\n" +
                "    \"show_toast\": " + toast + ",\n" +
                "    \"announce_to_chat\": " + chat + "\n" +
                "  },\n" +
                "  \"criteria\": { \"impossible\": { \"trigger\": \"minecraft:impossible\" } }\n" +
                "}";
    }

    private String getIconForLevel(int level) {
        if (level == 50) return "minecraft:fishing_rod";
        if (level >= 40) return "minecraft:enchanted_book";
        if (level >= 30) return "minecraft:tropical_fish";
        if (level >= 20) return "minecraft:pufferfish";
        if (level >= 10) return "minecraft:salmon";
        return "minecraft:cod";
    }

    private final java.util.Map<String, java.util.Set<String>> cachedGroupRequirements = new java.util.HashMap<>();

    private void safeLoad(NamespacedKey key, String json) {
        try {
            Bukkit.getUnsafe().loadAdvancement(key, json);
        } catch (Exception e) {
            plugin.getLogger().warning("Error loading advancement " + key + ": " + e.getMessage());
        }
    }

    private void safeRemove(NamespacedKey key) {
        try {
            Bukkit.getUnsafe().removeAdvancement(key);
        } catch (Exception ignored) {}
    }

    // ── Biome Groups ──────────────────────────────────────────

    private record BiomeAdvancementGroup(
            String keyName,
            String displayName,
            String description,
            String icon,
            List<com.fishrework.model.BiomeGroup> biomes
    ) {}

    private final List<BiomeAdvancementGroup> GROUPS = List.of(
            new BiomeAdvancementGroup("seven_seas", "The Seven Seas", "Ocean and Beach Biomes", "minecraft:water_bucket",
                    List.of(com.fishrework.model.BiomeGroup.COLD_OCEAN, com.fishrework.model.BiomeGroup.FROZEN_OCEAN,
                            com.fishrework.model.BiomeGroup.NORMAL_OCEAN, com.fishrework.model.BiomeGroup.LUKEWARM_OCEAN,
                            com.fishrework.model.BiomeGroup.WARM_OCEAN, com.fishrework.model.BiomeGroup.BEACH)),

            new BiomeAdvancementGroup("heartlands", "The Heartlands", "River, Plains, Forest, and Meadow Biomes", "minecraft:grass_block",
                    List.of(com.fishrework.model.BiomeGroup.RIVER, com.fishrework.model.BiomeGroup.PLAINS,
                            com.fishrework.model.BiomeGroup.FOREST, com.fishrework.model.BiomeGroup.MEADOW)),

            new BiomeAdvancementGroup("frozen_peaks", "The Frozen Peaks", "Mountain, Snowy, and Taiga Biomes", "minecraft:snow_block",
                    List.of(com.fishrework.model.BiomeGroup.MOUNTAINS, com.fishrework.model.BiomeGroup.SNOWY,
                            com.fishrework.model.BiomeGroup.TAIGA)),

            new BiomeAdvancementGroup("scorched_earth", "Scorched Earth", "Desert and Badlands Biomes", "minecraft:dead_bush",
                    List.of(com.fishrework.model.BiomeGroup.DESERT, com.fishrework.model.BiomeGroup.BADLANDS)),

            new BiomeAdvancementGroup("wild_tropics", "The Wild Tropics", "Swamp, Jungle, and Savanna Biomes", "minecraft:vine",
                    List.of(com.fishrework.model.BiomeGroup.SWAMP, com.fishrework.model.BiomeGroup.JUNGLE,
                            com.fishrework.model.BiomeGroup.SAVANNA)),

            new BiomeAdvancementGroup("hidden_realms", "Hidden Realms", "Lush Caves, Deep Dark, Pale Garden, and Mushroom Fields", "minecraft:spore_blossom",
                    List.of(com.fishrework.model.BiomeGroup.LUSH_CAVES, com.fishrework.model.BiomeGroup.MUSHROOM,
                            com.fishrework.model.BiomeGroup.DEEP_DARK, com.fishrework.model.BiomeGroup.PALE_GARDEN))
    );

    // ── Biome Logic ──

    public void checkBiomeAdvancements(Player player) {
        com.fishrework.model.PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        for (BiomeAdvancementGroup group : GROUPS) {
            // 1. Check Land Mobs (if group has any)
            java.util.Set<String> landMobs = getGroupLandRequirements(group);
            if (!landMobs.isEmpty()) {
                NamespacedKey landKey = getGroupLandKey(group);
                if (!hasAdvancement(player, landKey)) {
                    if (data.hasCaughtAll(new java.util.ArrayList<>(landMobs))) {
                        grantAdvancement(player, landKey);
                    }
                }
            }

            // 2. Check All Creatures
            NamespacedKey allKey = getGroupAllKey(group);
            if (!hasAdvancement(player, allKey)) {
                java.util.Set<String> required = getGroupAllRequirements(group);
                if (data.hasCaughtAll(new java.util.ArrayList<>(required))) {
                    grantAdvancement(player, allKey);
                }
            }
        }
    }

    private java.util.Set<String> getGroupLandRequirements(BiomeAdvancementGroup group) {
        java.util.Set<String> land = new java.util.HashSet<>();
        for (com.fishrework.model.BiomeGroup bg : group.biomes) {
            com.fishrework.model.BiomeFishingProfile profile = plugin.getBiomeFishingRegistry().get(bg);
            if (profile != null) {
                land.addAll(profile.getLandMobs());
            }
        }
        return land;
    }

    private java.util.Set<String> getGroupAllRequirements(BiomeAdvancementGroup group) {
        if (cachedGroupRequirements.containsKey(group.keyName)) {
            return cachedGroupRequirements.get(group.keyName);
        }

        java.util.Set<String> required = new java.util.HashSet<>();

        // 1. Add all land mobs from this group
        required.addAll(getGroupLandRequirements(group));

        // 2. Add all aquatic mobs that can spawn in any of the biomes in this group
        for (com.fishrework.model.BiomeGroup bg : group.biomes) {
            com.fishrework.model.BiomeFishingProfile profile = plugin.getBiomeFishingRegistry().get(bg);

            for (CustomMob mob : plugin.getMobRegistry().getPassive(Skill.FISHING)) {
                if (mob.isTreasure()) continue;

                if (profile != null) {
                    // Profile exists
                    if (profile.hasWeight(mob.getId())) {
                        if (profile.getWeight(mob.getId()) > 0) required.add(mob.getId());
                    } else {
                        // No override, use default
                        if (mob.getDefaultChance() > 0) required.add(mob.getId());
                    }
                } else {
                    // No profile = default spawns
                    if (mob.getDefaultChance() > 0) required.add(mob.getId());
                }
            }

            // 3. Add biome-specific hostile mobs
            if (profile != null) {
                for (String hostileMobId : profile.getHostileWeights().keySet()) {
                    if (profile.getHostileWeight(hostileMobId) > 0) {
                        required.add(hostileMobId);
                    }
                }
            } else {
                // No profile = default hostiles (drowned, guardian, elder_guardian)
                for (CustomMob mob : plugin.getMobRegistry().getHostile(Skill.FISHING)) {
                    if (mob.getDefaultChance() > 0) required.add(mob.getId());
                }
            }
        }

        cachedGroupRequirements.put(group.keyName, required);
        return required;
    }

    public NamespacedKey getGroupLandKey(BiomeAdvancementGroup group) {
        return new NamespacedKey(plugin, "fishing/group_" + group.keyName + "_land");
    }

    public NamespacedKey getGroupAllKey(BiomeAdvancementGroup group) {
        return new NamespacedKey(plugin, "fishing/group_" + group.keyName + "_all");
    }

    /**
     * Gets the representative color for a mob based on its biome group.
     */
    public net.kyori.adventure.text.format.TextColor getGroupColor(CustomMob mob) {
        if (mob == null) return net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;

        for (BiomeAdvancementGroup group : GROUPS) {
            if (getGroupAllRequirements(group).contains(mob.getId())) {
                return getGroupColor(group.keyName);
            }
        }
        return net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE; // Default
    }

    private net.kyori.adventure.text.format.TextColor getGroupColor(String groupKey) {
        return switch (groupKey) {
            case "seven_seas" -> net.kyori.adventure.text.format.NamedTextColor.AQUA;
            case "heartlands" -> net.kyori.adventure.text.format.NamedTextColor.GREEN;
            case "frozen_peaks" -> net.kyori.adventure.text.format.NamedTextColor.WHITE;
            case "scorched_earth" -> net.kyori.adventure.text.format.NamedTextColor.GOLD;
            case "wild_tropics" -> net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN;
            case "hidden_realms" -> net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE;
            default -> net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;
        };
    }

    // ── Check Logic ──

    public void checkArtifactAdvancements(Player player) {
        if (hasAdvancement(player, ARTIFACT_ALL_KEY)) return;

        com.fishrework.model.PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;
        
        java.util.Set<String> collected = data.getCollectedArtifacts();

        // Check rarities
        checkRarity(player, ARTIFACT_COMMON_KEY, com.fishrework.model.Rarity.COMMON, collected);
        checkRarity(player, ARTIFACT_UNCOMMON_KEY, com.fishrework.model.Rarity.UNCOMMON, collected);
        checkRarity(player, ARTIFACT_RARE_KEY, com.fishrework.model.Rarity.RARE, collected);
        checkRarity(player, ARTIFACT_EPIC_KEY, com.fishrework.model.Rarity.EPIC, collected);
        checkRarity(player, ARTIFACT_LEGENDARY_KEY, com.fishrework.model.Rarity.LEGENDARY, collected);

        // Check all
        if (collected.containsAll(plugin.getArtifactRegistry().getAllIds())) {
            grantAdvancement(player, ARTIFACT_ALL_KEY);
        }
    }

    private void checkRarity(Player player, NamespacedKey key, com.fishrework.model.Rarity rarity, java.util.Set<String> collected) {
        if (hasAdvancement(player, key)) return;
        
        List<String> required = plugin.getArtifactRegistry().getByRarity(rarity).stream()
                .map(com.fishrework.model.Artifact::getId)
                .collect(Collectors.toList());
        
        if (!required.isEmpty() && collected.containsAll(required)) {
            grantAdvancement(player, key);
        }
    }

    public void checkCompletionistAdvancement(Player player) {
        if (hasAdvancement(player, FISHING_COMPLETIONIST_KEY)) return;

        // Iterate over all known custom advancements
        List<NamespacedKey> allKeys = new java.util.ArrayList<>();
        allKeys.add(ROOT_KEY);
        allKeys.add(FISHH_KEY);
        allKeys.add(CATCH_ALL_KEY);
        allKeys.add(BUCKET_CATCHER_KEY);
        allKeys.add(KILL_DEAD_RIDER_KEY);
        allKeys.add(KILL_ELDER_GUARDIAN_KEY);
        allKeys.add(KILL_POSEIDON_KEY);
        allKeys.add(KILL_SPIDER_JOCKEY_KEY);
        allKeys.add(KILL_VULTURE_KING_KEY);
        allKeys.add(KILL_KING_SLIME_KEY);
        allKeys.add(KILL_WARDEN_KEY);
        
        for (int level = 5; level <= 50; level += 5) {
            allKeys.add(getProgressionKey(level));
        }

        for (BiomeAdvancementGroup group : GROUPS) {
            if (!getGroupLandRequirements(group).isEmpty()) {
                allKeys.add(getGroupLandKey(group));
            }
            allKeys.add(getGroupAllKey(group));
        }

        allKeys.add(ARTIFACT_COMMON_KEY);
        allKeys.add(ARTIFACT_UNCOMMON_KEY);
        allKeys.add(ARTIFACT_RARE_KEY);
        allKeys.add(ARTIFACT_EPIC_KEY);
        allKeys.add(ARTIFACT_LEGENDARY_KEY);
        allKeys.add(ARTIFACT_ALL_KEY);

        for (NamespacedKey key : allKeys) {
            if (!hasAdvancement(player, key)) {
                return; // Missing at least one
            }
        }

        grantAdvancement(player, FISHING_COMPLETIONIST_KEY);
    }
}

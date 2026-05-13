package com.fishrework.loader;

import com.fishrework.FishRework;
import com.fishrework.manager.ItemManager;
import com.fishrework.manager.TreasureManager;
import com.fishrework.model.CustomMob;
import com.fishrework.model.MobDrop;
import com.fishrework.model.Rarity;
import com.fishrework.model.Skill;
import com.fishrework.model.SpawnConfig;
import com.fishrework.registry.MobRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.configuration.MemoryConfiguration;

/**
 * Loads mob definitions from {@code mobs.yml} and populates the {@link MobRegistry}.
 */
public class YamlMobLoader {

    private final FishRework plugin;

    public YamlMobLoader(FishRework plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all mobs from {@code mobs.yml} into the given registry.
     *
     * @param registry         the mob registry to populate
     * @param itemManager      used to resolve custom item drops
     * @param treasureManager  used to resolve treasure loot drops
     * @return the number of mobs loaded
     */
    public int load(MobRegistry registry, ItemManager itemManager, TreasureManager treasureManager) {
        YamlConfiguration yaml = YamlLoaderSupport.loadYaml(plugin, "mobs.yml");

        ConfigurationSection section = YamlLoaderSupport.requireSection(plugin, yaml, "mobs", "mobs.yml");
        if (section == null) {
            return 0;
        }

        int count = 0;
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) continue;

            try {
                CustomMob mob = parseMob(id, entry, itemManager, treasureManager);
                registry.register(mob);
                count++;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load mob '" + id + "': " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + count + " mobs from mobs.yml");
        return count;
    }

    private CustomMob parseMob(String id, ConfigurationSection entry,
                               ItemManager itemManager, TreasureManager treasureManager) {

        EntityType entityType = YamlParseSupport.parseEnum(
            plugin,
            EntityType.class,
            entry.getString("entity_type"),
            EntityType.COD,
            "mobs." + id + ".entity_type"
        );
        String displayName = entry.getString("display_name", id);
        double xp = entry.getDouble("xp", 0.0);
        double baseWeight = entry.getDouble("base_weight", 1.0);
        double defaultChance = entry.getDouble("default_chance", 0.0);
        CustomMob.MobCategory category = YamlParseSupport.parseEnum(
            plugin,
            CustomMob.MobCategory.class,
            entry.getString("category"),
            CustomMob.MobCategory.PASSIVE,
            "mobs." + id + ".category"
        );
        int requiredLevel = entry.getInt("required_level", 0);
        boolean boost = entry.getBoolean("boost_by_rare_creature", false);
        Material collectionIcon = YamlParseSupport.parseEnum(
            plugin,
            Material.class,
            entry.getString("collection_icon"),
            Material.BARRIER,
            "mobs." + id + ".collection_icon"
        );
        String collectionName = entry.getString("collection_name", displayName);
        String displayModelId = entry.getString("display_model");
        if (displayModelId == null || displayModelId.isBlank()) {
            displayModelId = entry.getString("model");
        }
        Rarity rarity = YamlParseSupport.parseEnum(
            plugin,
            Rarity.class,
            entry.getString("rarity"),
            Rarity.COMMON,
            "mobs." + id + ".rarity"
        );

        CustomMob.Builder builder = CustomMob.builder(id, entityType, Skill.FISHING)
                .displayName(displayName)
                .xp(xp)
                .baseWeight(baseWeight)
                .chance(defaultChance)
            .category(category)
                .requiredLevel(requiredLevel)
                .boostByRareCreature(boost)
                .collectionIcon(collectionIcon)
                .collectionName(collectionName)
                .rarity(rarity)
                .displayModelId(displayModelId);

        // Parse drops
        if (entry.contains("drops")) {
            List<Map<?, ?>> dropsList = entry.getMapList("drops");
            for (Map<?, ?> dropMap : dropsList) {
                MobDrop drop = parseDrop(toConfig(dropMap), itemManager, treasureManager);
                if (drop != null) {
                    builder.drop(drop);
                }
            }
        }

        // Parse spawn config
        if (entry.contains("spawn")) {
            ConfigurationSection spawnSection = entry.getConfigurationSection("spawn");
            if (spawnSection != null) {
                builder.spawnConfig(parseSpawnConfig(spawnSection));
            }
        }

        return builder.build();
    }

    // ── Drop Parsing ──────────────────────────────────────────

    private MobDrop parseDrop(ConfigurationSection dropEntry,
                              ItemManager itemManager, TreasureManager treasureManager) {
        double chance = dropEntry.getDouble("chance", 1.0);
        int min = dropEntry.getInt("min", 1);
        int max = dropEntry.getInt("max", 1);

        if (dropEntry.contains("item")) {
            String itemId = dropEntry.getString("item");
            return MobDrop.builder(() -> itemManager.getItem(itemId))
                    .chance(chance).amount(min, max).build();
        }

        if (dropEntry.contains("vanilla")) {
            String materialName = dropEntry.getString("vanilla");
            Material material = YamlParseSupport.parseEnum(
                plugin,
                Material.class,
                materialName,
                Material.STONE,
                dropEntry.getCurrentPath() + ".vanilla"
            );
            return MobDrop.builder(() -> new ItemStack(material))
                    .chance(chance).amount(min, max).build();
        }

        if (dropEntry.contains("random_item")) {
            List<String> items = dropEntry.getStringList("random_item");
            return MobDrop.builder(() -> {
                String picked = items.get(ThreadLocalRandom.current().nextInt(items.size()));
                return itemManager.getItem(picked);
            }).chance(chance).amount(min, max).build();
        }

        if (dropEntry.contains("treasure_loot")) {
            String value = dropEntry.getString("treasure_loot");
            if ("RANDOM".equalsIgnoreCase(value)) {
                return MobDrop.builder(() -> treasureManager.getRandomTreasure())
                        .chance(chance).amount(min, max).build();
            } else {
                Rarity treasureRarity = YamlParseSupport.parseEnum(
                    plugin,
                    Rarity.class,
                    value,
                    Rarity.COMMON,
                    dropEntry.getCurrentPath() + ".treasure_loot"
                );
                return MobDrop.builder(() -> itemManager.getTreasure(treasureRarity))
                        .chance(chance).amount(min, max).build();
            }
        }

        if (dropEntry.contains("nether_treasure_loot")) {
            String value = dropEntry.getString("nether_treasure_loot");
                Rarity treasureRarity = YamlParseSupport.parseEnum(
                    plugin,
                    Rarity.class,
                    value,
                    Rarity.COMMON,
                    dropEntry.getCurrentPath() + ".nether_treasure_loot"
                );
            return MobDrop.builder(() -> itemManager.getNetherTreasure(treasureRarity))
                    .chance(chance).amount(min, max).build();
        }

        plugin.getLogger().warning("Unknown drop type in mob definition: " + dropEntry.getCurrentPath());
        return null;
    }

    // ── Spawn Config Parsing ──────────────────────────────────

    private SpawnConfig parseSpawnConfig(ConfigurationSection sec) {
        SpawnConfig.Type type = YamlParseSupport.parseEnum(
            plugin,
            SpawnConfig.Type.class,
            sec.getString("type"),
            SpawnConfig.Type.SIMPLE,
            sec.getCurrentPath() + ".type"
        );

        SpawnConfig.Builder b = SpawnConfig.builder(type)
                .healthMultiplier(sec.getDouble("health_multiplier", -1))
                .damageMultiplier(sec.getDouble("damage_multiplier", -1))
                .health(sec.getDouble("health", -1))
                .damage(sec.getDouble("damage", -1))
                .scale(sec.getDouble("scale", -1))
                .particle(sec.getString("particle"))
                .nameColor(sec.getString("name_color"))
                .glowColor(sec.getString("glow_color", sec.getString("glowColor")))
                .groupKillAll(sec.getBoolean("group_kill_all", false))
                .slimeSize(sec.getInt("slime_size", -1))
                .spread(sec.getDouble("spread", 2.0))
                .notPlayerCreated(sec.getBoolean("not_player_created", false))
                .aggroPlayers(sec.getBoolean("aggro_players", true));

        // Aggro
        if (sec.contains("aggro")) {
            b.aggro(parseAggro(sec.getConfigurationSection("aggro")));
        }

        // Equipment
        if (sec.contains("equipment")) {
            b.equipment(parseEquipment(sec.getConfigurationSection("equipment")));
        }

        // Potion effects
        if (sec.contains("potion_effects")) {
            b.potionEffects(parsePotionEffects(sec));
        }

        // Generic abilities
        if (sec.contains("abilities")) {
            b.abilities(parseAbilities(sec));
        }

        // Mount (MOUNTED type)
        if (sec.contains("mount")) {
            b.mount(parseEntityConfig(sec.getConfigurationSection("mount")));
        }

        // Rider entity type override (MOUNTED type)
        if (sec.contains("rider_entity_type")) {
            EntityType riderType = YamlParseSupport.parseEnumOrNull(
                    plugin,
                    EntityType.class,
                    sec.getString("rider_entity_type"),
                    sec.getCurrentPath() + ".rider_entity_type"
            );
            if (riderType != null) {
                b.riderEntityType(riderType);
            }
        }

        // Group members (GROUP type)
        if (sec.contains("members")) {
            b.members(parseGroupMembers(sec));
        }

        return b.build();
    }

    private SpawnConfig.AggroConfig parseAggro(ConfigurationSection sec) {
        if (sec == null) return null;
        return new SpawnConfig.AggroConfig(
                sec.getDouble("speed", 1.0),
                sec.getDouble("damage", 0),
                sec.getDouble("range", 2.5),
                sec.getInt("hit_interval", 25)
        );
    }

    private SpawnConfig.EquipmentConfig parseEquipment(ConfigurationSection sec) {
        if (sec == null) return null;
        return new SpawnConfig.EquipmentConfig(
                sec.getString("helmet"),
                sec.getString("chestplate"),
                sec.getString("leggings"),
                sec.getString("boots"),
                sec.getString("mainhand"),
                sec.getString("offhand")
        );
    }

    private SpawnConfig.EntityConfig parseEntityConfig(ConfigurationSection sec) {
        if (sec == null) return null;
        SpawnConfig.AggroConfig aggro = sec.contains("aggro")
                ? parseAggro(sec.getConfigurationSection("aggro")) : null;

        return new SpawnConfig.EntityConfig(
            YamlParseSupport.parseEnum(
                plugin,
                EntityType.class,
                sec.getString("entity_type"),
                EntityType.PIG,
                sec.getCurrentPath() + ".entity_type"
            ),
                sec.getString("display_name", "Mount"),
                sec.getBoolean("show_name", false),
                sec.getDouble("health_multiplier", -1),
                sec.getDouble("damage_multiplier", -1),
                sec.getDouble("health", -1),
                sec.getDouble("damage", -1),
                sec.getDouble("scale", -1),
                aggro,
                sec.getString("panda_gene")
        );
    }

    private List<SpawnConfig.GroupMemberConfig> parseGroupMembers(ConfigurationSection parent) {
        List<SpawnConfig.GroupMemberConfig> members = new ArrayList<>();
        List<Map<?, ?>> list = parent.getMapList("members");

        for (Map<?, ?> map : list) {
            members.add(parseGroupMember(toConfig(map)));
        }
        return members;
    }

    private SpawnConfig.GroupMemberConfig parseGroupMember(ConfigurationSection sec) {
        SpawnConfig.AggroConfig aggro = sec.contains("aggro")
                ? parseAggro(sec.getConfigurationSection("aggro")) : null;
        List<SpawnConfig.PotionConfig> potions = sec.contains("potion_effects")
                ? parsePotionEffects(sec) : null;

        SpawnConfig.GroupMemberConfig rider = null;
        if (sec.contains("rider")) {
            rider = parseGroupMember(sec.getConfigurationSection("rider"));
        }

        return new SpawnConfig.GroupMemberConfig(
            YamlParseSupport.parseEnum(
                plugin,
                EntityType.class,
                sec.getString("entity_type"),
                EntityType.ZOMBIE,
                sec.getCurrentPath() + ".entity_type"
            ),
                sec.getInt("count", 1),
                sec.getDouble("health_multiplier", -1),
                sec.getDouble("damage_multiplier", -1),
                sec.getDouble("health", -1),
                sec.getDouble("damage", -1),
                sec.getString("display_name"),
                sec.getBoolean("show_name", true),
                aggro,
                potions,
                rider
        );
    }

    private List<SpawnConfig.PotionConfig> parsePotionEffects(ConfigurationSection parent) {
        List<SpawnConfig.PotionConfig> effects = new ArrayList<>();
        List<Map<?, ?>> list = parent.getMapList("potion_effects");

        for (Map<?, ?> map : list) {
            ConfigurationSection sec = toConfig(map);
            effects.add(new SpawnConfig.PotionConfig(
                    sec.getString("type", "ABSORPTION"),
                    sec.getInt("duration", Integer.MAX_VALUE),
                    sec.getInt("amplifier", 0)
            ));
        }
        return effects;
    }

    private List<SpawnConfig.AbilityConfig> parseAbilities(ConfigurationSection parent) {
        List<SpawnConfig.AbilityConfig> abilities = new ArrayList<>();
        List<Map<?, ?>> list = parent.getMapList("abilities");

        for (Map<?, ?> map : list) {
            ConfigurationSection sec = toConfig(map);
            String id = sec.getString("id", "");
            if (id == null || id.isBlank()) continue;

            int cooldownTicks = sec.getInt("cooldown_ticks", 100);
            int weight = sec.getInt("weight", 1);
            double chance = sec.getDouble("chance", 1.0);
            abilities.add(new SpawnConfig.AbilityConfig(id, cooldownTicks, weight, chance));
        }

        return abilities;
    }

    private ConfigurationSection toConfig(Map<?, ?> map) {
        MemoryConfiguration config = new MemoryConfiguration();
        populateSection(config, map);
        return config;
    }

    private void populateSection(ConfigurationSection section, Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String) {
                String key = (String) entry.getKey();
                if (entry.getValue() instanceof Map) {
                    ConfigurationSection sub = section.createSection(key);
                    populateSection(sub, (Map<?, ?>) entry.getValue());
                } else {
                    section.set(key, entry.getValue());
                }
            }
        }
    }
}

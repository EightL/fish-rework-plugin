package com.fishrework.manager;

import com.fishrework.FishRework;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.fishrework.model.Artifact;
import com.fishrework.model.BiomeGroup;
import com.fishrework.model.CustomMob;
import com.fishrework.model.Rarity;

import java.util.*;
import java.util.function.Supplier;

/**
 * Creates custom ItemStacks. Pure item factory — no recipe registration here.
 * Recipes are registered through RecipeRegistry.
 *
 * Materials, enchanted materials, armor sets, and fishing rods are loaded
 * from items.yml by YamlItemLoader. Only utility items with unique
 * construction logic remain hardcoded here.
 */
public class ItemManager {

    private static final Set<String> LAVA_ONLY_BAIT_IDS = Set.of(
        "magma_bait",
        "soul_bait",
        "blaze_bait",
        "cooled_bait"
    );

    private static final Set<BiomeGroup> NETHER_BIOME_GROUPS = EnumSet.of(
        BiomeGroup.NETHER_WASTES,
        BiomeGroup.CRIMSON_FOREST,
        BiomeGroup.WARPED_FOREST,
        BiomeGroup.SOUL_SAND_VALLEY,
        BiomeGroup.BASALT_DELTAS
    );

    private final FishRework plugin;

    // Item registry: name → supplier (populated from YAML + Java items)
    private final Map<String, Supplier<ItemStack>> itemRegistry = new LinkedHashMap<>();

    // PDC keys
    public final NamespacedKey FISH_BUCKET_KEY;
    public final NamespacedKey FISH_BUCKET_RECIPE_KEY;

    public final NamespacedKey SCALE_ARMOR_KEY;
    public final NamespacedKey NEPTUNES_ROD_KEY;
    public final NamespacedKey SHREDDER_KEY;
    public final NamespacedKey RARE_CREATURE_CHANCE_KEY;
    public final NamespacedKey RARITY_KEY;
    public final NamespacedKey DEADMAN_ARMOR_KEY;
    public final NamespacedKey TREASURE_TYPE_KEY; // Stores the rarity name for treasure chests
    public final NamespacedKey HARMONY_ROD_KEY;
    public final NamespacedKey TREASURE_TOTEM_KEY;
    public final NamespacedKey DISPLAY_CASE_KEY;
    public final NamespacedKey FISHING_JOURNAL_KEY;
    public final NamespacedKey ARTIFACT_KEY;

    // New material & equipment keys
    public final NamespacedKey CUSTOM_ITEM_KEY;
    public final NamespacedKey IRONCLAD_ARMOR_KEY;
    public final NamespacedKey DREADPLATE_ARMOR_KEY;
    public final NamespacedKey LEVIATHAN_KEY;
    public final NamespacedKey FISHING_XP_BONUS_KEY;
    public final NamespacedKey TREASURE_CHANCE_BONUS_KEY;

        private static final java.util.Set<Material> OTHER_VENDOR_SELL_MATERIALS = java.util.EnumSet.of(
            Material.APPLE,
            Material.ARROW,
            Material.BEETROOT,
            Material.BLACKSTONE,
            Material.BLAZE_POWDER,
            Material.BONE,
            Material.BREAD,
            Material.CANDLE,
            Material.CRIMSON_FUNGUS,
            Material.DRIED_KELP,
            Material.FEATHER,
            Material.MAGMA_BLOCK,
            Material.MAGMA_CREAM,
            Material.MELON_SLICE,
            Material.NETHERRACK,
            Material.POISONOUS_POTATO,
            Material.POTATO,
            Material.QUARTZ,
            Material.ROTTEN_FLESH,
            Material.SOUL_SAND,
            Material.STICK,
            Material.SUSPICIOUS_STEW,
            Material.WARPED_FUNGUS,
            Material.WHEAT
        );
    public final NamespacedKey SEA_CREATURE_DEFENSE_KEY;
    public final NamespacedKey SEA_CREATURE_ATTACK_KEY;
    public final NamespacedKey DOUBLE_CATCH_BONUS_KEY;
    public final NamespacedKey FISHING_SPEED_KEY;
    public final NamespacedKey NETHERITE_RELIC_KEY;
    public final NamespacedKey SC_FLAT_ATTACK_KEY;
    public final NamespacedKey SC_FLAT_DEFENSE_KEY;
    public final NamespacedKey SC_UPGRADE_TIER_KEY;
    public final NamespacedKey BAIT_KEY;
    public final NamespacedKey BAIT_TARGET_MOB_KEY;
    public final NamespacedKey BAIT_TARGET_MOBS_KEY;
    public final NamespacedKey BAIT_NATIVE_BIOME_GROUPS_KEY;
    public final NamespacedKey FISH_BAG_KEY;
    public final NamespacedKey LAVA_BAG_KEY;
    public final NamespacedKey BOBBER_DAMAGE_KEY;
    public final NamespacedKey LAVA_ROD_KEY;
    public final NamespacedKey LAVA_BOBBER_KEY;
    public final NamespacedKey HEAT_RESISTANCE_KEY;
    public final NamespacedKey HEPHAESTEANTRIDENTKEY;

    public ItemManager(FishRework plugin) {
        this.plugin = plugin;
        this.FISH_BUCKET_KEY = new NamespacedKey(plugin, "fish_bucket");
        this.FISH_BUCKET_RECIPE_KEY = new NamespacedKey(plugin, "fish_bucket_recipe");

        this.SCALE_ARMOR_KEY = new NamespacedKey(plugin, "scale_armor");
        this.NEPTUNES_ROD_KEY = new NamespacedKey(plugin, "neptunes_rod");
        this.SHREDDER_KEY = new NamespacedKey(plugin, "shredder");
        this.RARE_CREATURE_CHANCE_KEY = new NamespacedKey(plugin, "rare_creature_chance");
        this.RARITY_KEY = new NamespacedKey(plugin, "rarity");
        this.DEADMAN_ARMOR_KEY = new NamespacedKey(plugin, "deadman_armor");
        this.TREASURE_TYPE_KEY = new NamespacedKey(plugin, "treasure_type");
        this.HARMONY_ROD_KEY = new NamespacedKey(plugin, "harmony_rod");
        this.TREASURE_TOTEM_KEY = new NamespacedKey(plugin, "treasure_totem");
        this.DISPLAY_CASE_KEY = new NamespacedKey(plugin, "display_case");
        this.FISHING_JOURNAL_KEY = new NamespacedKey(plugin, "fishing_journal");
        this.ARTIFACT_KEY = new NamespacedKey(plugin, "artifact");

        // New keys
        this.CUSTOM_ITEM_KEY = new NamespacedKey(plugin, "custom_item");
        this.IRONCLAD_ARMOR_KEY = new NamespacedKey(plugin, "ironclad_armor");
        this.DREADPLATE_ARMOR_KEY = new NamespacedKey(plugin, "dreadplate_armor");
        this.LEVIATHAN_KEY = new NamespacedKey(plugin, "leviathan");
        this.FISHING_XP_BONUS_KEY = new NamespacedKey(plugin, "fishing_xp_bonus");
        this.TREASURE_CHANCE_BONUS_KEY = new NamespacedKey(plugin, "treasure_chance_bonus");
        this.SEA_CREATURE_DEFENSE_KEY = new NamespacedKey(plugin, "sea_creature_defense");
        this.SEA_CREATURE_ATTACK_KEY = new NamespacedKey(plugin, "sea_creature_attack");
        this.DOUBLE_CATCH_BONUS_KEY = new NamespacedKey(plugin, "double_catch_bonus");
        this.FISHING_SPEED_KEY = new NamespacedKey(plugin, "fishing_speed");
        this.NETHERITE_RELIC_KEY = new NamespacedKey(plugin, "netherite_relic");
        this.SC_FLAT_ATTACK_KEY = new NamespacedKey(plugin, "sc_flat_attack");
        this.SC_FLAT_DEFENSE_KEY = new NamespacedKey(plugin, "sc_flat_defense");
        this.SC_UPGRADE_TIER_KEY = new NamespacedKey(plugin, "sc_upgrade_tier");
        this.BAIT_KEY = new NamespacedKey(plugin, "bait_id");
        this.BAIT_TARGET_MOB_KEY = new NamespacedKey(plugin, "bait_target_mob");
        this.BAIT_TARGET_MOBS_KEY = new NamespacedKey(plugin, "bait_target_mobs");
        this.BAIT_NATIVE_BIOME_GROUPS_KEY = new NamespacedKey(plugin, "bait_native_biome_groups");
        this.FISH_BAG_KEY = new NamespacedKey(plugin, "fish_bag");
        this.LAVA_BAG_KEY = new NamespacedKey(plugin, "lavabag");
        this.BOBBER_DAMAGE_KEY = new NamespacedKey(plugin, "bobber_damage");
        this.LAVA_ROD_KEY = new NamespacedKey(plugin, "lava_rod");
        this.LAVA_BOBBER_KEY = new NamespacedKey(plugin, "lava_bobber");
        this.HEAT_RESISTANCE_KEY = new NamespacedKey(plugin, "heat_resistance");
        this.HEPHAESTEANTRIDENTKEY = new NamespacedKey(plugin, "hephaesteantrident");
    }

    // ── Item Factories (Materials) ───────────────────────────

    public ItemStack createMaterial(String id, Material material, String name, Rarity rarity, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).color(rarity.getColor()).decoration(TextDecoration.ITALIC, false));
        meta.lore(Collections.singletonList(
                Component.text(description).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(CUSTOM_ITEM_KEY, PersistentDataType.STRING, id);
        meta.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, rarity.name());
        item.setItemMeta(meta);
        plugin.getLoreManager().updateLore(item);
        return item;
    }

    public ItemStack createEnchantedMaterial(String id, Material material, String name, Rarity rarity, String description) {
        ItemStack item = createMaterial(id, material, name, rarity, description);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        return item;
    }




    public boolean isCustomItem(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) return false;
        String stored = getCustomItemId(item);
        return id.equals(stored);
    }

    /**
     * Returns true if this item is ANY custom Fish Rework item.
     * Used by AnvilProtectionListener and other general-purpose checks.
     */
    public boolean isCustomItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(CUSTOM_ITEM_KEY, PersistentDataType.STRING)
                || pdc.has(ARTIFACT_KEY, PersistentDataType.STRING)
                || pdc.has(FISH_BAG_KEY, PersistentDataType.BYTE)
                || pdc.has(LAVA_BAG_KEY, PersistentDataType.BYTE)
                || pdc.has(TREASURE_TYPE_KEY, PersistentDataType.STRING)
                || pdc.has(TREASURE_TOTEM_KEY, PersistentDataType.BYTE)
                || pdc.has(DISPLAY_CASE_KEY, PersistentDataType.BYTE)
                || pdc.has(BAIT_KEY, PersistentDataType.STRING)
                || pdc.has(SCALE_ARMOR_KEY, PersistentDataType.BYTE)
                || pdc.has(DEADMAN_ARMOR_KEY, PersistentDataType.BYTE)
                || pdc.has(NEPTUNES_ROD_KEY, PersistentDataType.BYTE)
                || pdc.has(SHREDDER_KEY, PersistentDataType.BYTE)
                || pdc.has(HARMONY_ROD_KEY, PersistentDataType.BYTE)
                || pdc.has(LEVIATHAN_KEY, PersistentDataType.BYTE)
                || pdc.has(LAVA_ROD_KEY, PersistentDataType.BYTE)
                || pdc.has(NETHERITE_RELIC_KEY, PersistentDataType.BYTE)
                || pdc.has(HEPHAESTEANTRIDENTKEY, PersistentDataType.BYTE);
    }

    public String getCustomItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(CUSTOM_ITEM_KEY, PersistentDataType.STRING);
    }

    // ── Utility: Equipment Slot ──────────────────────────────

    public EquipmentSlotGroup getEquipmentSlotGroup(Material material) {
        if (material.name().endsWith("_HELMET")) return EquipmentSlotGroup.HEAD;
        if (material.name().endsWith("_CHESTPLATE")) return EquipmentSlotGroup.CHEST;
        if (material.name().endsWith("_LEGGINGS")) return EquipmentSlotGroup.LEGS;
        if (material.name().endsWith("_BOOTS")) return EquipmentSlotGroup.FEET;
        return EquipmentSlotGroup.ANY;
    }

    // ── Special Items (unique construction, stay in Java) ────

    public ItemStack getDeadRiderHead() {
        // Texture for "Blackstone Skull" (ID: 125287)
        String base64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWJhZGFkYmZhZWE4NGM5MTQxZTE5MDQ3NDE3MDkzMjI0MGYyOGFiYWU3YjNmMGQ2OTJhNmZkYjE0MDk3OTVjMyJ9fX0=";
        ItemStack skull = getCustomSkull(base64);
        ItemMeta meta = skull.getItemMeta();
        Rarity rarity = Rarity.EPIC;
        meta.displayName(Component.text("Dead Rider's Head").color(rarity.getColor()).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(CUSTOM_ITEM_KEY, PersistentDataType.STRING, "dead_rider_head");
        meta.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, rarity.name());
        skull.setItemMeta(meta);
        return skull;
    }

    public ItemStack getCustomSkull(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (base64 == null || base64.isEmpty()) return head;

        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        PlayerProfile profile = org.bukkit.Bukkit.createProfile(UUID.randomUUID(), null);
        profile.getProperties().add(new ProfileProperty("textures", base64));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    // ── Treasure Totem ──

    public boolean isTreasureTotem(ItemStack item) {
        if (item == null || item.getType() != Material.CONDUIT) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(TREASURE_TOTEM_KEY, PersistentDataType.BYTE);
    }

    // ── Display Case ──

    public boolean isDisplayCase(ItemStack item) {
        if (item == null || item.getType() != Material.GLASS) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(DISPLAY_CASE_KEY, PersistentDataType.BYTE);
    }

    // ── Netherite Relic ──

    public boolean isNetheriteRelic(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_BLOCK) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(NETHERITE_RELIC_KEY, PersistentDataType.BYTE);
    }

    // ── Fishing Journal ──

    public boolean isFishingJournal(ItemStack item) {
        if (item == null || item.getType() != Material.BOOK) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(FISHING_JOURNAL_KEY, PersistentDataType.BYTE);
    }

    // ── Bait Items ──

    public ItemStack createBaitItem(com.fishrework.model.Bait bait) {
        ItemStack item = new ItemStack(bait.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(bait.getDisplayName()).color(bait.getRarity().getColor())
                .decoration(TextDecoration.ITALIC, false));

        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text(bait.getDescription()).color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Hold in offhand while fishing").color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Consumed on successful catch").color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(BAIT_KEY, PersistentDataType.STRING, bait.getId());
        meta.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, bait.getRarity().name());
        item.setItemMeta(meta);
        plugin.getLoreManager().updateLore(item);
        return item;
    }

    public ItemStack createSeaCreatureBaitItem(CustomMob mob) {
        Material icon = mob.getCollectionIcon();
        if (icon == null || icon.isAir()) {
            icon = Material.WHEAT_SEEDS;
        }

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        Rarity rarity = mob.getRarity() != null ? mob.getRarity() : Rarity.COMMON;

        meta.displayName(Component.text(mob.getCollectionName() + " Bait")
            .color(rarity.getColor())
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Targets: " + mob.getDisplayName()).color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Always adds this sea creature to the pool").color(NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("x2 weight outside native biome").color(NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("x4 weight in native biome").color(NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Hold in offhand while fishing").color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Consumed on successful catch").color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(BAIT_KEY, PersistentDataType.STRING, "hostile_bait:" + mob.getId());
        meta.getPersistentDataContainer().set(BAIT_TARGET_MOB_KEY, PersistentDataType.STRING, mob.getId());
        meta.getPersistentDataContainer().set(CUSTOM_ITEM_KEY, PersistentDataType.STRING, "hostile_bait_" + mob.getId());
        meta.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, rarity.name());
        item.setItemMeta(meta);
        plugin.getLoreManager().updateLore(item);
        return item;
        }

        public ItemStack createBiomeBaitItem(String baitId,
                         String displayName,
                         Material icon,
                         Rarity rarity,
                         List<String> targetMobIds,
                         List<BiomeGroup> nativeBiomeGroups) {
        Material resolvedIcon = (icon == null || icon.isAir()) ? Material.WHEAT_SEEDS : icon;
        Rarity resolvedRarity = rarity != null ? rarity : Rarity.COMMON;

        ItemStack item = new ItemStack(resolvedIcon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(displayName)
            .color(resolvedRarity.getColor())
            .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Targets unlocked creatures from these biomes")
            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Outside native biome: default weight")
            .color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Inside native biome: x2 weight")
            .color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Hold in offhand while fishing")
            .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Consumed on successful catch")
            .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(BAIT_KEY, PersistentDataType.STRING, "biome_bait:" + baitId);
        meta.getPersistentDataContainer().set(BAIT_TARGET_MOBS_KEY, PersistentDataType.STRING, encodeCsv(targetMobIds));
        meta.getPersistentDataContainer().set(BAIT_NATIVE_BIOME_GROUPS_KEY, PersistentDataType.STRING, encodeBiomeCsv(nativeBiomeGroups));
        meta.getPersistentDataContainer().set(CUSTOM_ITEM_KEY, PersistentDataType.STRING, "biome_bait_" + baitId);
        meta.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, resolvedRarity.name());
        item.setItemMeta(meta);
        plugin.getLoreManager().updateLore(item);
        return item;
        }

    public boolean isBait(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(BAIT_KEY, PersistentDataType.STRING);
    }

    public String getBaitId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(BAIT_KEY, PersistentDataType.STRING);
    }

    public boolean isBaitApplicableForWater(ItemStack item) {
        return isBaitApplicable(item, false);
    }

    public boolean isBaitApplicableForLava(ItemStack item) {
        return isBaitApplicable(item, true);
    }

    private boolean isBaitApplicable(ItemStack item, boolean lavaFishing) {
        if (!isBait(item)) {
            return false;
        }

        String baitId = getBaitId(item);
        if (baitId == null || baitId.isBlank()) {
            return false;
        }
        baitId = baitId.toLowerCase(Locale.ROOT);

        if (baitId.startsWith("biome_bait:")) {
            Set<BiomeGroup> nativeGroups = getBaitNativeBiomeGroups(item);
            if (nativeGroups.isEmpty()) {
                return !lavaFishing;
            }
            boolean hasNetherGroups = containsNetherBiomeGroup(nativeGroups);
            boolean hasOverworldGroups = nativeGroups.stream().anyMatch(group -> !NETHER_BIOME_GROUPS.contains(group));
            return lavaFishing ? hasNetherGroups : hasOverworldGroups;
        }

        if (LAVA_ONLY_BAIT_IDS.contains(baitId)) {
            return lavaFishing;
        }

        return !lavaFishing;
    }

    private boolean containsNetherBiomeGroup(Set<BiomeGroup> groups) {
        for (BiomeGroup group : groups) {
            if (NETHER_BIOME_GROUPS.contains(group)) {
                return true;
            }
        }
        return false;
    }

    public String getBaitTargetMobId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        List<String> mobIds = getBaitTargetMobIds(item);
        return mobIds.isEmpty() ? null : mobIds.get(0);
    }

    public List<String> getBaitTargetMobIds(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Collections.emptyList();

        ItemMeta meta = item.getItemMeta();
        String multi = meta.getPersistentDataContainer().get(BAIT_TARGET_MOBS_KEY, PersistentDataType.STRING);
        if (multi != null && !multi.isBlank()) {
            return decodeCsv(multi);
        }

        String single = meta.getPersistentDataContainer().get(BAIT_TARGET_MOB_KEY, PersistentDataType.STRING);
        if (single == null || single.isBlank()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(single);
    }

    public Set<BiomeGroup> getBaitNativeBiomeGroups(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Collections.emptySet();

        String encoded = item.getItemMeta().getPersistentDataContainer()
                .get(BAIT_NATIVE_BIOME_GROUPS_KEY, PersistentDataType.STRING);
        if (encoded == null || encoded.isBlank()) {
            return Collections.emptySet();
        }

        Set<BiomeGroup> groups = EnumSet.noneOf(BiomeGroup.class);
        for (String value : decodeCsv(encoded)) {
            try {
                groups.add(BiomeGroup.valueOf(value));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed values so old items never break fishing.
            }
        }
        return groups;
    }

    private String encodeCsv(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        List<String> filtered = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                filtered.add(value.trim());
            }
        }
        return String.join(",", filtered);
    }

    private String encodeBiomeCsv(List<BiomeGroup> groups) {
        if (groups == null || groups.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        for (BiomeGroup group : groups) {
            if (group != null) names.add(group.name());
        }
        return String.join(",", names);
    }

    private List<String> decodeCsv(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Collections.emptyList();
        }

        String[] pieces = encoded.split(",");
        List<String> out = new ArrayList<>(pieces.length);
        for (String piece : pieces) {
            if (piece != null && !piece.isBlank()) {
                out.add(piece.trim());
            }
        }
        return out;
    }

    // ── Fish Bag Item ──

    public ItemStack createFishBagItem() {
        ItemStack item = new ItemStack(Material.BUNDLE);
        ItemMeta meta = item.getItemMeta();
        com.fishrework.model.Rarity rarity = com.fishrework.model.Rarity.RARE;
        meta.displayName(Component.text("Fish Bag").color(rarity.getColor())
                .decoration(TextDecoration.ITALIC, false));

        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("A portable bag for storing fish").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("and custom materials.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Right-click to open!").color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(FISH_BAG_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, rarity.name());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createLavaBagItem() {
        ItemStack item = new ItemStack(Material.BUNDLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Magma Satchel")
                .color(net.kyori.adventure.text.format.TextColor.color(0xFF4500))
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.text("Forged from the depths of the Nether,").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("this satchel hungers for lava drops.").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("► Auto-collects drops from fished").color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("  mobs that die in lava.").color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Right-click").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(" to open.").color(NamedTextColor.GRAY))
        ));
        meta.getPersistentDataContainer().set(LAVA_BAG_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.setFireResistant(true);
        item.setItemMeta(meta);
        return item;
    }

        public ItemStack createLavaRingItem() {
        ItemStack item = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = item.getItemMeta();
        Rarity rarity = Rarity.EPIC;

        meta.displayName(Component.text("Lava Ring").color(rarity.getColor())
            .decoration(TextDecoration.ITALIC, false));

        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("Carry in inventory to gain fire resistance.")
            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Forged to keep flames at bay.")
            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(CUSTOM_ITEM_KEY, PersistentDataType.STRING, "lava_ring");
        meta.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, rarity.name());

        item.setItemMeta(meta);
        plugin.getLoreManager().updateLore(item);
        return item;
        }

        public ItemStack createEruptionRingItem() {
        ItemStack item = new ItemStack(Material.FIRE_CHARGE);
        ItemMeta meta = item.getItemMeta();
        Rarity rarity = Rarity.LEGENDARY;

        meta.displayName(Component.text("Eruption Ring").color(rarity.getColor())
            .decoration(TextDecoration.ITALIC, false));

        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("Carry in inventory to gain fire resistance.")
            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Grants free movement while swimming in lava.")
            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(CUSTOM_ITEM_KEY, PersistentDataType.STRING, "eruption_ring");
        meta.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, rarity.name());

        item.setItemMeta(meta);
        plugin.getLoreManager().updateLore(item);
        return item;
        }

    public boolean isFishBag(ItemStack item) {
        if (item == null || item.getType() != Material.BUNDLE) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(FISH_BAG_KEY, PersistentDataType.BYTE);
    }

    public boolean isLavaBag(ItemStack item) {
        if (item == null || item.getType() != Material.BUNDLE) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(LAVA_BAG_KEY, PersistentDataType.BYTE);
    }

    // ── Sell Price Lookups ──

    /**
     * Gets the sell price for a given ItemStack.
     * Checks vanilla fish types first, then custom item PDC.
     * Returns 0 if no price is configured.
     */
    public double getSellPrice(ItemStack item) {
        if (item == null) return 0;

        // Block selling Tridents entirely
        if (item.getType() == Material.TRIDENT) return 0;

        // Check vanilla fish sell prices
        double vanillaPrice = plugin.getConfig().getDouble(
                "economy.sell_prices." + item.getType().name(), 0);
        if (vanillaPrice <= 0) {
            vanillaPrice = plugin.getConfig().getDouble(
                    "sell_prices." + item.getType().name(), 0);
        }
        if (vanillaPrice > 0) return vanillaPrice;

        // Wildcard support: apply POTTERY_SHERD price to all *_POTTERY_SHERD materials.
        if (item.getType().name().endsWith("_POTTERY_SHERD")) {
            double sherdPrice = plugin.getConfig().getDouble("economy.sell_prices.POTTERY_SHERD", 0);
            if (sherdPrice <= 0) {
                sherdPrice = plugin.getConfig().getDouble("sell_prices.POTTERY_SHERD", 0);
            }
            if (sherdPrice > 0) return sherdPrice;
        }

        // Generic "other" treasure junk bucket sold in the vendor as DRIED_KELP.
        if (isOtherVendorSellMaterial(item)) {
            return plugin.getConfig().getDouble("economy.other_vendor_price", 1.0);
        }

        // Check custom item sell prices
        if (item.hasItemMeta()) {
            String customId = item.getItemMeta().getPersistentDataContainer()
                    .get(CUSTOM_ITEM_KEY, PersistentDataType.STRING);
            if (customId != null) {
                double customPrice = plugin.getConfig().getDouble(
                        "economy.custom_sell_prices." + customId, 0);
                if (customPrice <= 0) {
                    customPrice = plugin.getConfig().getDouble(
                            "custom_sell_prices." + customId, 0);
                }
                return customPrice;
            }
        }

        return 0;
    }

    public boolean isOtherVendorSellMaterial(ItemStack item) {
        if (item == null) return false;
        if (!OTHER_VENDOR_SELL_MATERIALS.contains(item.getType())) return false;

        if (!item.hasItemMeta()) return true;

        org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(CUSTOM_ITEM_KEY, PersistentDataType.STRING)) return false;
        if (pdc.has(ARTIFACT_KEY, PersistentDataType.STRING)) return false;
        return true;
    }

    /**
     * Checks if an item is sellable (has a configured sell price > 0).
     */
    public boolean isSellable(ItemStack item) {
        return getSellPrice(item) > 0;
    }

    // ── Artifact Items ─────────────────────────────────────

    public ItemStack createArtifactItem(Artifact artifact) {
        ItemStack item;
        if (artifact.isPlayerHead()) {
            item = getCustomSkull(artifact.getTextureBase64());
        } else {
            item = new ItemStack(artifact.getMaterial());
        }

        ItemMeta meta = item.getItemMeta();
        Rarity rarity = artifact.getRarity();

        meta.displayName(Component.text(artifact.getDisplayName()).color(rarity.getColor())
                .decoration(TextDecoration.ITALIC, false));

        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text(artifact.getDescription()).color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.empty());
        lore.add(Component.text("\u2B50 Artifact").color(NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        meta.lore(lore);

        meta.getPersistentDataContainer().set(ARTIFACT_KEY, PersistentDataType.STRING, artifact.getId());
        meta.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, rarity.name());
        item.setItemMeta(meta);
        return item;
    }

    // ── Treasure Chests ──────────────────────────────────────

    public ItemStack getTreasure(Rarity rarity) {
        Material mat = Material.CHEST;
        String name = "Treasure Chest";
        switch (rarity) {
            case COMMON:
                mat = Material.CHEST;
                name = "Common Treasure Chest";
                break;
            case UNCOMMON:
                mat = Material.getMaterial("COPPER_CHEST");
                if (mat == null) mat = Material.CHEST;
                name = "Uncommon Treasure Chest";
                break;
            case RARE:
                mat = Material.getMaterial("WEATHERED_COPPER_CHEST");
                if (mat == null) mat = Material.CHEST;
                name = "Rare Treasure Chest";
                break;
            case EPIC:
                mat = Material.getMaterial("OXIDIZED_COPPER_CHEST");
                if (mat == null) mat = Material.CHEST;
                name = "Epic Treasure Chest";
                break;
            case LEGENDARY:
                mat = Material.ENDER_CHEST;
                name = "Legendary Treasure Chest";
                break;
            case MYTHIC:
                mat = Material.REINFORCED_DEEPSLATE;
                name = "Mythic Treasure Vault";
                break;
            case SPECIAL:
                mat = Material.REINFORCED_DEEPSLATE;
                name = "Special Treasure Vault";
                break;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).color(rarity.getColor()).decoration(TextDecoration.ITALIC, false));

        // Add lore to explain usage
        meta.lore(java.util.Collections.singletonList(
                Component.text("Right-click to open!").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));

        meta.getPersistentDataContainer().set(TREASURE_TYPE_KEY, PersistentDataType.STRING, rarity.name());
        meta.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, rarity.name());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack getNetherTreasure(Rarity rarity) {
        Material mat = switch (rarity) {
            case COMMON -> Material.NETHER_BRICKS;
            case UNCOMMON -> Material.RED_NETHER_BRICKS;
            case RARE -> Material.BASALT;
            case EPIC -> Material.POLISHED_BASALT;
            case LEGENDARY -> Material.CRYING_OBSIDIAN;
            case MYTHIC -> Material.CRYING_OBSIDIAN;
            case SPECIAL -> Material.CRYING_OBSIDIAN;
        };

        String name = "Nether " + rarity.name().substring(0, 1) + rarity.name().substring(1).toLowerCase() + " Treasure";

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).color(rarity.getColor()).decoration(TextDecoration.ITALIC, false));

        meta.lore(java.util.Collections.singletonList(
                Component.text("Right-click to open!").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));

        if (rarity == Rarity.MYTHIC || rarity == Rarity.SPECIAL) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.getPersistentDataContainer().set(TREASURE_TYPE_KEY, PersistentDataType.STRING, "NETHER_" + rarity.name());
        meta.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, rarity.name());
        item.setItemMeta(meta);
        return item;
    }

    // ── Item Checks ───────────────────────────────────────────

    public boolean isFishBucket(ItemStack item) {
        if (item == null || item.getType() != Material.WATER_BUCKET) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(FISH_BUCKET_KEY, PersistentDataType.BYTE);
    }

    public boolean isNeptunesRod(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(NEPTUNES_ROD_KEY, PersistentDataType.BYTE);
    }

    public boolean isShredder(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(SHREDDER_KEY, PersistentDataType.BYTE);
    }

    public boolean isHarmonyRod(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(HARMONY_ROD_KEY, PersistentDataType.BYTE);
    }

    public boolean isLeviathan(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(LEVIATHAN_KEY, PersistentDataType.BYTE);
    }

    // ── Item Lookup ───────────────────────────────────────────

    /**
     * Returns the item registry map. Used by YamlItemLoader to register
     * YAML-defined item suppliers.
     */
    public Map<String, Supplier<ItemStack>> getItemRegistry() {
        return itemRegistry;
    }

    /**
     * Registers Java-defined utility items into the item registry.
     * Call this AFTER YamlItemLoader has populated the YAML-based items
     * (materials, enchanted materials, armor sets, fishing rods).
     */
    /**
     * Registers Java-only items that cannot be expressed in YAML.
     * Call this AFTER YamlItemLoader has populated YAML-based items.
     */
    public void initJavaItems() {
        // Skull-based items (requires Base64 texture — not expressible in YAML)
        itemRegistry.put("dead_rider_head", this::getDeadRiderHead);

        // Treasure chests (dynamic material selection based on rarity)
        itemRegistry.put("treasure_common", () -> getTreasure(Rarity.COMMON));
        itemRegistry.put("treasure_chest_common", () -> getTreasure(Rarity.COMMON));
        itemRegistry.put("treasure_uncommon", () -> getTreasure(Rarity.UNCOMMON));
        itemRegistry.put("treasure_chest_uncommon", () -> getTreasure(Rarity.UNCOMMON));
        itemRegistry.put("treasure_rare", () -> getTreasure(Rarity.RARE));
        itemRegistry.put("treasure_chest_rare", () -> getTreasure(Rarity.RARE));
        itemRegistry.put("treasure_epic", () -> getTreasure(Rarity.EPIC));
        itemRegistry.put("treasure_chest_epic", () -> getTreasure(Rarity.EPIC));
        itemRegistry.put("treasure_legendary", () -> getTreasure(Rarity.LEGENDARY));
        itemRegistry.put("treasure_chest_legendary", () -> getTreasure(Rarity.LEGENDARY));
        itemRegistry.put("treasure_mythic", () -> getTreasure(Rarity.MYTHIC));
        itemRegistry.put("treasure_chest_mythic", () -> getTreasure(Rarity.MYTHIC));

        // Fish Bag
        itemRegistry.put("fish_bag", this::createFishBagItem);

        // Lava Bag
        itemRegistry.put("lava_bag", this::createLavaBagItem);
        itemRegistry.put("lavabag", this::createLavaBagItem);

        // Lava Ring
        itemRegistry.put("lava_ring", this::createLavaRingItem);
        itemRegistry.put("eruption_ring", this::createEruptionRingItem);

        // Mythic weapon
        itemRegistry.put("gehenna", this::createGehenna);

        // Special weapon
        itemRegistry.put("megalodon_tooth", this::createMegalodonTooth);
    }

    public ItemStack createGehenna() {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();
        Rarity rarity = Rarity.MYTHIC;

        meta.displayName(Component.text("Gehenna")
            .color(rarity.getColor())
            .decoration(TextDecoration.ITALIC, false));

        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
            new NamespacedKey(plugin, "gehenna_speed"),
            -2.8,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlotGroup.MAINHAND));

        // Any explicit attribute modifier set replaces vanilla weapon modifiers,
        // so we must add back mace damage explicitly (base 1 + 11 = 12 shown).
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
            new NamespacedKey(plugin, "gehenna_damage"),
            11.0,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlotGroup.MAINHAND));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Drawn from the molten pits of Gehenna.")
            .color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.text("Its chains hunger for the damned.")
            .color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.empty());
        meta.lore(lore);

        meta.getPersistentDataContainer().set(SEA_CREATURE_ATTACK_KEY, PersistentDataType.DOUBLE, 25.0);
        meta.getPersistentDataContainer().set(CUSTOM_ITEM_KEY, PersistentDataType.STRING, "gehenna");
        meta.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, rarity.name());

        item.setItemMeta(meta);
        plugin.getLoreManager().updateLore(item);
        return item;
    }

    public ItemStack createMegalodonTooth() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        Rarity rarity = Rarity.SPECIAL;

        meta.displayName(Component.text("Megalodon Tooth")
            .color(rarity.getColor())
            .decoration(TextDecoration.ITALIC, false));

        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
            new NamespacedKey(plugin, "megalodon_tooth_speed"),
            -0.8,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlotGroup.MAINHAND));

        // Netherite sword damage profile on an iron sword base.
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
            new NamespacedKey(plugin, "megalodon_tooth_damage"),
            7.0,
            AttributeModifier.Operation.ADD_NUMBER,
            EquipmentSlotGroup.MAINHAND));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("A jagged relic carved from an apex hunter fang.")
            .color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.text("Cuts deep into sea creatures.")
            .color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, true));
        lore.add(Component.empty());
        meta.lore(lore);

        meta.getPersistentDataContainer().set(SEA_CREATURE_ATTACK_KEY, PersistentDataType.DOUBLE, 10.0);
        meta.getPersistentDataContainer().set(CUSTOM_ITEM_KEY, PersistentDataType.STRING, "megalodon_tooth");
        meta.getPersistentDataContainer().set(RARITY_KEY, PersistentDataType.STRING, rarity.name());
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "megalodon_tooth"), PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        plugin.getLoreManager().updateLore(item);
        return item;
    }

    public ItemStack getItem(String name) {
        if (name == null) return null;
        Supplier<ItemStack> supplier = itemRegistry.get(name.toLowerCase());
        return supplier != null ? supplier.get() : null;
    }

    /**
     * Retrieves an item by name, or throws an exception if not found.
     * Use this for items that MUST exist for the plugin to function (e.g. recipe ingredients).
     */
    public ItemStack getRequiredItem(String name) {
        ItemStack item = getItem(name);
        if (item == null) {
            throw new IllegalStateException("Required item '" + name + "' not found in registry! " +
                "Please check if it is defined in items.yml or if the configuration is outdated.");
        }
        return item;
    }

    public List<String> getItemNames() {
        return new ArrayList<>(itemRegistry.keySet());
    }
}

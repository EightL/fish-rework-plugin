package com.fishrework.loader;

import com.fishrework.FishRework;
import com.fishrework.manager.ItemManager;
import com.fishrework.model.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Registry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Loads item definitions (materials, enchanted materials, armor sets, fishing rods)
 * from {@code items.yml} and registers them into the {@link ItemManager} item registry.
 */
public class YamlItemLoader {

    private final FishRework plugin;
    private final Set<String> warnedUnknownEnchantments = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> warnedCustomEnchantFallbacks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public YamlItemLoader(FishRework plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all item definitions from {@code items.yml} and registers them
     * as suppliers in the item registry.
     *
     * @param itemManager the item manager (provides factory methods and the item registry)
     * @return the number of items loaded
     */
    public int load(ItemManager itemManager) {
        YamlConfiguration yaml = YamlLoaderSupport.loadYaml(plugin, "items.yml");

        Map<String, Supplier<ItemStack>> registry = itemManager.getItemRegistry();
        int count = 0;

        count += loadMaterials(yaml, itemManager, registry);
        count += loadEnchantedMaterials(yaml, itemManager, registry);
        count += loadArmorSets(yaml, itemManager, registry);
        count += loadFishingRods(yaml, itemManager, registry);
        count += loadWeapons(yaml, itemManager, registry);
        count += loadUtilityItems(yaml, itemManager, registry);

        plugin.getLogger().info("Loaded " + count + " items from items.yml");
        return count;
    }

    // ── Materials ────────────────────────────────────────────

    private int loadMaterials(YamlConfiguration yaml, ItemManager itemManager,
            Map<String, Supplier<ItemStack>> registry) {
        ConfigurationSection materials = yaml.getConfigurationSection("materials");
        if (materials == null) return 0;
        int count = 0;

        for (String id : materials.getKeys(false)) {
            ConfigurationSection entry = materials.getConfigurationSection(id);
            if (entry == null) continue;

            Material material = resolveMaterial(entry.getString("material", "STONE"), Material.STONE);
            String displayName = entry.getString("display_name", id);
            Rarity rarity = parseRarity(entry.getString("rarity"), Rarity.COMMON, "materials." + id + ".rarity");
            String description = entry.getString("description", "");

            final String fId = id;
            final Material fMat = material;
            final String fName = displayName;
            final Rarity fRarity = rarity;
            final String fDesc = description;

            registry.put(id, () -> itemManager.createMaterial(fId, fMat,
                    plugin.getLanguageManager().getString("item." + fId + ".name", fName),
                    fRarity,
                    plugin.getLanguageManager().getString("item." + fId + ".desc", fDesc)));
            count++;
        }
        return count;
    }

    // ── Enchanted Materials ─────────────────────────────────

    private int loadEnchantedMaterials(YamlConfiguration yaml, ItemManager itemManager,
            Map<String, Supplier<ItemStack>> registry) {
        ConfigurationSection enchanted = yaml.getConfigurationSection("enchanted_materials");
        if (enchanted == null) return 0;
        int count = 0;

        for (String id : enchanted.getKeys(false)) {
            ConfigurationSection entry = enchanted.getConfigurationSection(id);
            if (entry == null) continue;

            Material material = resolveMaterial(entry.getString("material", "STONE"), Material.STONE);
            String displayName = entry.getString("display_name", id);
            Rarity rarity = parseRarity(entry.getString("rarity"), Rarity.UNCOMMON,
                    "enchanted_materials." + id + ".rarity");
            String description = entry.getString("description", "");

            final String fId = id;
            final Material fMat = material;
            final String fName = displayName;
            final Rarity fRarity = rarity;
            final String fDesc = description;

            registry.put(id, () -> itemManager.createEnchantedMaterial(fId, fMat,
                    plugin.getLanguageManager().getString("item." + fId + ".name", fName),
                    fRarity,
                    plugin.getLanguageManager().getString("item." + fId + ".desc", fDesc)));
            count++;
        }
        return count;
    }

    // ── Armor Sets ──────────────────────────────────────────

    private int loadArmorSets(YamlConfiguration yaml, ItemManager itemManager,
            Map<String, Supplier<ItemStack>> registry) {
        ConfigurationSection armorSets = yaml.getConfigurationSection("armor_sets");
        if (armorSets == null) return 0;
        int count = 0;

        for (String setId : armorSets.getKeys(false)) {
            ConfigurationSection setSection = armorSets.getConfigurationSection(setId);
            if (setSection == null) continue;

            // Common set properties
            String materialType = setSection.getString("material_type", "IRON");
                Rarity rarity = parseRarity(setSection.getString("rarity"), Rarity.COMMON,
                    "armor_sets." + setId + ".rarity");
            String setKeyStr = setSection.getString("set_key", setId + "_armor");
            NamespacedKey armorSetKey = new NamespacedKey(plugin, setKeyStr);
            org.bukkit.Color setLeatherColor = parseLeatherColor(setSection.getIntegerList("leather_color"));

            // Bonus key and value
            String bonusKeyStr = setSection.getString("bonus_key");
            double bonusValue = setSection.getDouble("bonus_value", 0);
            NamespacedKey bonusKey = bonusKeyStr != null ? new NamespacedKey(plugin, bonusKeyStr) : null;

            // Extra PDC entries
            Map<NamespacedKey, Double> extraPdc = new LinkedHashMap<>();
            ConfigurationSection extraSection = setSection.getConfigurationSection("extra_pdc");
            if (extraSection != null) {
                for (String key : extraSection.getKeys(false)) {
                    extraPdc.put(new NamespacedKey(plugin, key), extraSection.getDouble(key));
                }
            }

            // Attribute bonuses (e.g. water_movement_efficiency, movement_speed)
            Map<String, Double> attributeBonuses = new LinkedHashMap<>();
            ConfigurationSection attrSection = setSection.getConfigurationSection("attribute_bonuses");
            if (attrSection != null) {
                for (String key : attrSection.getKeys(false)) {
                    attributeBonuses.put(key, attrSection.getDouble(key));
                }
            }

            // Optional lore
            String loreLine = setSection.getString("lore");
            String loreExtra = setSection.getString("lore_extra");
            String loreExtraColor = setSection.getString("lore_extra_color");
            String defaultTrimMaterial = setSection.getString("trim_material");
            String defaultTrimPattern = setSection.getString("trim_pattern");

            // Pieces
            ConfigurationSection piecesSection = setSection.getConfigurationSection("pieces");
            if (piecesSection == null) continue;

            for (String pieceType : piecesSection.getKeys(false)) {
                ConfigurationSection piece = piecesSection.getConfigurationSection(pieceType);
                if (piece == null) continue;

                String displayName = piece.getString("display_name", setId + " " + pieceType);
                int armor = piece.getInt("armor", 0);
                int toughness = piece.getInt("toughness", 0);
                int durability = piece.getInt("durability", 0);

                // Determine material: per-piece override or derived from material_type
                Material pieceMaterial;
                if (piece.contains("material")) {
                    pieceMaterial = resolveMaterial(piece.getString("material"), deriveMaterial(materialType, pieceType));
                } else {
                    pieceMaterial = deriveMaterial(materialType, pieceType);
                }
                String trimMaterialKey = piece.getString("trim_material", defaultTrimMaterial);
                String trimPatternKey = piece.getString("trim_pattern", defaultTrimPattern);
                String baseItemId = piece.getString("base_item");

                org.bukkit.Color pieceLeatherColor = setLeatherColor;
                if (piece.isList("leather_color")) {
                    pieceLeatherColor = parseLeatherColor(piece.getIntegerList("leather_color"));
                }
                boolean pieceIsLeather = isLeatherMaterial(pieceMaterial);

                // Enchantments
                Map<String, Integer> enchantments = new LinkedHashMap<>();
                ConfigurationSection enchSection = piece.getConfigurationSection("enchantments");
                if (enchSection != null) {
                    for (String enchName : enchSection.getKeys(false)) {
                        enchantments.put(enchName, enchSection.getInt(enchName));
                    }
                }

                // Capture all values for lambda
                final Material fMat = pieceMaterial;
                final String fName = displayName;
                final Rarity fRarity = rarity;
                final NamespacedKey fSetKey = armorSetKey;
                final NamespacedKey fBonusKey = bonusKey;
                final double fBonusVal = bonusValue;
                final Map<NamespacedKey, Double> fExtraPdc = new LinkedHashMap<>(extraPdc);
                final Map<String, Double> fAttrBonuses = new LinkedHashMap<>(attributeBonuses);
                final Map<String, Integer> fEnchants = enchantments;
                final String fBaseItemId = baseItemId;
                final boolean fIsLeather = pieceIsLeather;
                final org.bukkit.Color fLeatherColor = pieceLeatherColor;
                final String fLore = loreLine;
                final String fLoreExtra = loreExtra;
                final String fLoreExtraColor = loreExtraColor;
                final String fTrimMaterialKey = trimMaterialKey;
                final String fTrimPatternKey = trimPatternKey;
                final int fArmor = armor;
                final int fToughness = toughness;
                final int fDurability = durability;

                String registryId = setId + "_" + pieceType;
                registry.put(registryId, () -> buildArmorPiece(
                    itemManager, registryId, fMat, plugin.getLanguageManager().getString("item." + registryId + ".name", fName), fRarity, fSetKey,
                        fBonusKey, fBonusVal, fExtraPdc, fAttrBonuses,
                        fEnchants, fBaseItemId, fIsLeather, fLeatherColor, fArmor, fToughness, fDurability,
                        fLore, fLoreExtra, fLoreExtraColor, fTrimMaterialKey, fTrimPatternKey));
                count++;
            }
        }
        return count;
    }

    /**
     * Builds a single armor piece ItemStack from parsed YAML data.
     * Replaces the old createGenericArmorPiece / createLeatherArmorPiece / createDeadmanPiece methods.
     */
    private ItemStack buildArmorPiece(ItemManager itemManager,
            String registryId, Material material, String name, Rarity rarity, NamespacedKey armorSetKey,
            NamespacedKey bonusKey, double bonusValue,
            Map<NamespacedKey, Double> extraPdc,
            Map<String, Double> attributeBonuses,
            Map<String, Integer> enchantments,
            String baseItemId,
            boolean isLeather, org.bukkit.Color leatherColor,
            int armor, int toughness, int durability,
            String loreLine, String loreExtra, String loreExtraColor,
            String trimMaterialKey, String trimPatternKey) {

        ItemStack item;
        if (baseItemId != null && !baseItemId.isBlank()) {
            if ("dead_rider_head".equalsIgnoreCase(baseItemId)) {
                item = itemManager.getDeadRiderHead();
            } else {
                ItemStack base = itemManager.getItem(baseItemId);
                if (base == null) {
                    plugin.getLogger().warning("Unknown armor piece base_item for " + registryId + ": " + baseItemId);
                    item = new ItemStack(material);
                } else {
                    item = base.clone();
                }
            }
        } else {
            item = new ItemStack(material);
        }

        ItemMeta meta = item.getItemMeta();
        if (isLeather && leatherColor != null && meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(leatherColor);
        }

        // Display name
        meta.displayName(Component.text(name).color(rarity.getColor())
                .decoration(TextDecoration.ITALIC, false));

        // Lore
        String localizedLoreLine = loreLine != null
                ? resolveLocalizedLoreLine(registryId, 0, loreLine)
                : null;
        int loreExtraIndex = loreLine != null ? 1 : 0;
        String localizedLoreExtra = loreExtra != null
                ? resolveLocalizedLoreLine(registryId, loreExtraIndex, loreExtra)
                : null;
        if (localizedLoreLine != null || localizedLoreExtra != null) {
            List<Component> lore = new ArrayList<>();
            if (localizedLoreLine != null) {
                lore.add(Component.text(localizedLoreLine).color(NamedTextColor.GRAY));
            }
            if (localizedLoreExtra != null) {
                NamedTextColor extraColor = NamedTextColor.GRAY;
                if (loreExtraColor != null) {
                    try {
                        extraColor = NamedTextColor.NAMES.value(loreExtraColor.toLowerCase());
                        if (extraColor == null) extraColor = NamedTextColor.GRAY;
                    } catch (Exception ignored) {
                        extraColor = NamedTextColor.GRAY;
                    }
                }
                lore.add(Component.text(localizedLoreExtra).color(extraColor)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }

        // Equipment slot
        EquipmentSlotGroup slotGroup = itemManager.getEquipmentSlotGroup(material);
        String key = registryId.toLowerCase().replace(" ", "_").replace("'", "");

        // Armor attribute
        meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                new NamespacedKey(plugin, key + "_armor"), armor,
                AttributeModifier.Operation.ADD_NUMBER, slotGroup));

        // Toughness attribute
        if (toughness > 0) {
            meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                    new NamespacedKey(plugin, key + "_tough"), toughness,
                    AttributeModifier.Operation.ADD_NUMBER, slotGroup));
        }

        // Additional attribute bonuses (water movement, speed, etc.)
        for (Map.Entry<String, Double> attrEntry : attributeBonuses.entrySet()) {
            Attribute attribute = resolveAttribute(attrEntry.getKey());
            if (attribute != null) {
                meta.addAttributeModifier(attribute, new AttributeModifier(
                        new NamespacedKey(plugin, key + "_" + attrEntry.getKey()),
                        attrEntry.getValue(),
                        AttributeModifier.Operation.ADD_NUMBER, slotGroup));
            }
        }

        // PDC: armor set key
        meta.getPersistentDataContainer().set(armorSetKey, PersistentDataType.BYTE, (byte) 1);

        // PDC: bonus key
        if (bonusKey != null && bonusValue > 0) {
            meta.getPersistentDataContainer().set(bonusKey, PersistentDataType.DOUBLE, bonusValue);
        }

        // PDC: extra entries
        for (Map.Entry<NamespacedKey, Double> entry : extraPdc.entrySet()) {
            meta.getPersistentDataContainer().set(entry.getKey(), PersistentDataType.DOUBLE, entry.getValue());
        }

        // PDC: rarity
        meta.getPersistentDataContainer().set(itemManager.RARITY_KEY, PersistentDataType.STRING, rarity.name());

        // PDC: registry id used for runtime refresh/rebuild lookups
        meta.getPersistentDataContainer().set(itemManager.CUSTOM_ITEM_KEY, PersistentDataType.STRING, registryId);
        itemManager.setVanillaFallbackMaterial(meta, material);

        if (meta instanceof ArmorMeta armorMeta) {
            applyArmorTrim(armorMeta, registryId, trimMaterialKey, trimPatternKey);
        }

        // 2x base durability for all custom armor pieces.
        if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
            int baseDurability = durability > 0 ? durability : material.getMaxDurability();
            if (baseDurability > 0) {
                damageable.setMaxDamage(baseDurability * 2);
            }
        }

        item.setItemMeta(meta);

        // Enchantments (added after setItemMeta since addUnsafeEnchantment works on ItemStack)
        applyEnchantments(item, enchantments);

        // Update lore via LoreManager
        plugin.getLoreManager().updateLore(item);
        return item;
    }

    // ── Fishing Rods ────────────────────────────────────────

    private int loadFishingRods(YamlConfiguration yaml, ItemManager itemManager,
            Map<String, Supplier<ItemStack>> registry) {
        ConfigurationSection rods = yaml.getConfigurationSection("fishing_rods");
        if (rods == null) return 0;
        int count = 0;

        for (String rodId : rods.getKeys(false)) {
            ConfigurationSection entry = rods.getConfigurationSection(rodId);
            if (entry == null) continue;

            String displayName = entry.getString("display_name", rodId);
            Rarity rarity = parseRarity(entry.getString("rarity"), Rarity.COMMON,
                    "fishing_rods." + rodId + ".rarity");
            String pdcKeyStr = entry.getString("pdc_key", rodId);
            double rareCreatureChance = entry.getDouble("rare_creature_chance", 0);
            double fishingSpeed = entry.getDouble("fishing_speed", 0);
            double seaCreatureAttack = entry.getDouble("sea_creature_attack", 0);
            double bobberDamage = entry.getDouble("bobber_damage", 0);
            boolean isLavaRod = entry.getBoolean("lava_rod", false);
            double heatResistance = entry.getDouble("heat_resistance", 0);
            int durability = entry.getInt("durability", 0);
            List<String> loreLines = entry.getStringList("lore");

            final String fName = displayName;
            final Rarity fRarity = rarity;
            final String fPdcKey = pdcKeyStr;
            final double fRcc = rareCreatureChance;
            final double fSpeed = fishingSpeed;
            final double fSca = seaCreatureAttack;
            final double fBobberDmg = bobberDamage;
            final boolean fIsLavaRod = isLavaRod;
            final double fHeatResistance = heatResistance;
            final int fDurability = durability;
            final List<String> fLore = new ArrayList<>(loreLines);

            registry.put(rodId, () -> buildFishingRod(
                    itemManager, rodId, plugin.getLanguageManager().getString("item." + rodId + ".name", fName), fRarity, fPdcKey, fRcc, fSpeed, fSca, fBobberDmg,
                    fIsLavaRod, fHeatResistance, fDurability, fLore));
            count++;
        }
        return count;
    }

    /**
     * Builds a fishing rod ItemStack from parsed YAML data.
     */
    private ItemStack buildFishingRod(ItemManager itemManager,
            String registryId, String name, Rarity rarity, String pdcKey,
            double rareCreatureChance, double fishingSpeed,
            double seaCreatureAttack, double bobberDamage,
            boolean isLavaRod, double heatResistance,
            int durability, List<String> loreLines) {

        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(name).color(rarity.getColor()));

        List<String> localizedLoreLines = resolveLocalizedLoreLines(registryId, loreLines);
        if (!localizedLoreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : localizedLoreLines) {
                lore.add(Component.text(line).color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }

        // PDC: rod identifier key
        NamespacedKey rodKey = new NamespacedKey(plugin, pdcKey);
        meta.getPersistentDataContainer().set(rodKey, PersistentDataType.BYTE, (byte) 1);

        // PDC: stats
        if (rareCreatureChance > 0) {
            meta.getPersistentDataContainer().set(itemManager.RARE_CREATURE_CHANCE_KEY,
                    PersistentDataType.DOUBLE, rareCreatureChance);
        }
        if (fishingSpeed > 0) {
            meta.getPersistentDataContainer().set(itemManager.FISHING_SPEED_KEY,
                    PersistentDataType.DOUBLE, fishingSpeed);
        }
        if (seaCreatureAttack > 0) {
            meta.getPersistentDataContainer().set(itemManager.SEA_CREATURE_ATTACK_KEY,
                    PersistentDataType.DOUBLE, seaCreatureAttack);
        }
        if (bobberDamage > 0) {
            meta.getPersistentDataContainer().set(itemManager.BOBBER_DAMAGE_KEY,
                    PersistentDataType.DOUBLE, bobberDamage);
        }

        // PDC: lava rod tag (used by LavaFishingListener to identify lava rods)
        if (isLavaRod) {
            meta.getPersistentDataContainer().set(itemManager.LAVA_ROD_KEY,
                    PersistentDataType.BYTE, (byte) 1);
        }

        // PDC: heat resistance (volcanic rod reduces heat gain)
        if (heatResistance > 0) {
            meta.getPersistentDataContainer().set(itemManager.HEAT_RESISTANCE_KEY,
                    PersistentDataType.DOUBLE, heatResistance);
        }

        // PDC: rarity
        meta.getPersistentDataContainer().set(itemManager.RARITY_KEY,
                PersistentDataType.STRING, rarity.name());

        // PDC: registry id used for runtime refresh/rebuild lookups
        meta.getPersistentDataContainer().set(itemManager.CUSTOM_ITEM_KEY,
            PersistentDataType.STRING, registryId);
        itemManager.setVanillaFallbackMaterial(meta, item.getType());

        // Custom durability
        if (durability > 0 && meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
            damageable.setMaxDamage(durability);
        }

        item.setItemMeta(meta);
        plugin.getLoreManager().updateLore(item);
        return item;
    }

    // ── Weapons ─────────────────────────────────────────────

    private int loadWeapons(YamlConfiguration yaml, ItemManager itemManager,
            Map<String, Supplier<ItemStack>> registry) {
        ConfigurationSection weapons = yaml.getConfigurationSection("weapons");
        if (weapons == null) return 0;
        int count = 0;

        for (String id : weapons.getKeys(false)) {
            ConfigurationSection entry = weapons.getConfigurationSection(id);
            if (entry == null) continue;

            Material material = resolveMaterial(entry.getString("material", "STONE"), Material.STONE);
            String displayName = entry.getString("display_name", id);
            Rarity rarity = parseRarity(entry.getString("rarity"), Rarity.COMMON,
                    "weapons." + id + ".rarity");
            String nameColorStr = entry.getString("name_color");
            String pdcKeyStr = entry.getString("pdc_key");
            Double attackDamage = entry.contains("attack_damage") ? entry.getDouble("attack_damage") : null;
            Double attackSpeed = entry.contains("attack_speed") ? entry.getDouble("attack_speed") : null;
            int upgradeTier = entry.getInt("upgrade_tier", 0);
            int durability = entry.getInt("durability", 0);

            // Lore lines (list of {text, color} maps)
            List<Map<?, ?>> loreEntries = entry.getMapList("lore");
            String textureBase64 = entry.getString("texture_base64");

            // Enchantments
            Map<String, Integer> enchantments = new LinkedHashMap<>();
            ConfigurationSection enchSection = entry.getConfigurationSection("enchantments");
            if (enchSection != null) {
                for (String enchName : enchSection.getKeys(false)) {
                    enchantments.put(enchName, enchSection.getInt(enchName));
                }
            }

            // PDC doubles
            Map<String, Double> pdcDoubles = new LinkedHashMap<>();
            ConfigurationSection pdcSection = entry.getConfigurationSection("pdc");
            if (pdcSection != null) {
                for (String key : pdcSection.getKeys(false)) {
                    pdcDoubles.put(key, pdcSection.getDouble(key));
                }
            }

            // Capture for lambda
            final String fId = id;
            final Material fMat = material;
            final String fName = displayName;
            final Rarity fRarity = rarity;
            final String fNameColor = nameColorStr;
                final String fPdcKey = pdcKeyStr;
            final Double fAttackDamage = attackDamage;
            final Double fAttackSpeed = attackSpeed;
            final int fUpgradeTier = upgradeTier;
            final int fDurability = durability;
            final List<Map<?, ?>> fLoreEntries = new ArrayList<>(loreEntries);
            final String fTextureBase64 = textureBase64;
            final Map<String, Integer> fEnchants = enchantments;
            final Map<String, Double> fPdc = pdcDoubles;

            registry.put(id, () -> buildWeapon(
                    itemManager, fId, fMat, plugin.getLanguageManager().getString("item." + fId + ".name", fName), fRarity, fNameColor, fPdcKey,
                    fLoreEntries, fEnchants, fPdc, fAttackDamage, fAttackSpeed, fUpgradeTier, fDurability));
            count++;
        }
        return count;
    }

    private ItemStack buildWeapon(ItemManager itemManager,
            String id, Material material, String name, Rarity rarity,
                String nameColorStr, String pdcKeyStr, List<Map<?, ?>> loreEntries,
            Map<String, Integer> enchantments,
            Map<String, Double> pdcDoubles, Double attackDamage, Double attackSpeed, int upgradeTier, int durability) {

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Display name — optional override color
        net.kyori.adventure.text.format.TextColor nameColor = rarity.getColor();
        if (nameColorStr != null) {
            NamedTextColor parsed = NamedTextColor.NAMES.value(nameColorStr.toLowerCase());
            if (parsed != null) nameColor = parsed;
        }
        meta.displayName(Component.text(name).color(nameColor)
                .decoration(TextDecoration.ITALIC, false));

        // Lore
        List<Map<?, ?>> localizedLoreEntries = resolveLocalizedLoreEntries(id, loreEntries);
        if (!localizedLoreEntries.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (Map<?, ?> entry : localizedLoreEntries) {
                String text = entry.containsKey("text") ? String.valueOf(entry.get("text")) : "";
                if (text.isEmpty()) {
                    lore.add(Component.empty());
                    continue;
                }
                String colorName = entry.containsKey("color") ? String.valueOf(entry.get("color")) : "GRAY";
                NamedTextColor loreColor = NamedTextColor.NAMES.value(colorName.toLowerCase());
                if (loreColor == null) loreColor = NamedTextColor.GRAY;
                lore.add(Component.text(text).color(loreColor)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }

        // PDC: custom item id
        meta.getPersistentDataContainer().set(itemManager.CUSTOM_ITEM_KEY,
                PersistentDataType.STRING, id);
        itemManager.setVanillaFallbackMaterial(meta, material);

        // PDC: rarity
        meta.getPersistentDataContainer().set(itemManager.RARITY_KEY,
                PersistentDataType.STRING, rarity.name());

        // Optional weapon identifier byte key
        if (pdcKeyStr != null && !pdcKeyStr.isBlank()) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, pdcKeyStr),
                PersistentDataType.BYTE, (byte) 1);
        }

        // PDC: doubles
        for (Map.Entry<String, Double> pdcEntry : pdcDoubles.entrySet()) {
            NamespacedKey key = new NamespacedKey(plugin, pdcEntry.getKey());
            meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, pdcEntry.getValue());
        }

        if (attackDamage != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "weapon_attack_damage"),
                    PersistentDataType.DOUBLE,
                    attackDamage
            );
        }
        if (attackSpeed != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "weapon_attack_speed"),
                    PersistentDataType.DOUBLE,
                    attackSpeed
            );
        }

        // Vanilla attribute overrides (final values shown in "When in Main Hand")
        if (attackSpeed != null || attackDamage != null) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        if (attackSpeed != null) {
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    new NamespacedKey(plugin, id + "_attack_speed"),
                    attackSpeed - 4.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
            ));
        }
        if (attackDamage != null) {
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    new NamespacedKey(plugin, id + "_attack_damage"),
                    attackDamage - 1.0,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND
            ));
        }

            // PDC: upgrade tier
        if (upgradeTier > 0) {
            // Determine type based on stats
            String type = "attack"; // Default
            if (pdcDoubles.containsKey("sea_creature_defense")) {
                type = "defense";
            } else if (pdcDoubles.containsKey("sea_creature_attack")) {
                type = "attack";
            }
            
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "sc_upgrade_tier"),
                    PersistentDataType.STRING, type + ":" + upgradeTier);
        }

        if (durability > 0 && meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
            damageable.setMaxDamage(durability);
        }

        item.setItemMeta(meta);

        // Enchantments (after setItemMeta)
        applyEnchantments(item, enchantments);

        plugin.getLoreManager().updateLore(item);
        return item;
    }

    // ── Utility Items ───────────────────────────────────────

    private int loadUtilityItems(YamlConfiguration yaml, ItemManager itemManager,
            Map<String, Supplier<ItemStack>> registry) {
        ConfigurationSection utilityItems = yaml.getConfigurationSection("utility_items");
        if (utilityItems == null) return 0;
        int count = 0;

        for (String id : utilityItems.getKeys(false)) {
            ConfigurationSection entry = utilityItems.getConfigurationSection(id);
            if (entry == null) continue;

            Material material = resolveMaterial(entry.getString("material", "STONE"), Material.STONE);
            String displayName = entry.getString("display_name", id);
            Rarity rarity = parseRarity(entry.getString("rarity"), Rarity.COMMON,
                    "utility_items." + id + ".rarity");
            String pdcKeyStr = entry.getString("pdc_key", id);
            List<Map<?, ?>> loreEntries = entry.getMapList("lore");
                String textureBase64 = entry.getString("texture_base64");

            final String fId = id;
            final Material fMat = material;
            final String fName = displayName;
            final Rarity fRarity = rarity;
            final String fPdcKey = pdcKeyStr;
            final List<Map<?, ?>> fLoreEntries = new ArrayList<>(loreEntries);
            final String fTextureBase64 = textureBase64;

            registry.put(id, () -> buildUtilityItem(
                    itemManager, fId, fMat, plugin.getLanguageManager().getString("item." + fId + ".name", fName),
                    fRarity, fPdcKey, fLoreEntries, fTextureBase64));
            count++;
        }
        return count;
    }

    private ItemStack buildUtilityItem(ItemManager itemManager,
            String id, Material material, String name, Rarity rarity,
            String pdcKeyStr, List<Map<?, ?>> loreEntries, String textureBase64) {

        ItemStack item = (textureBase64 != null && !textureBase64.isBlank())
                ? itemManager.getCustomSkull(textureBase64)
                : new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(name).color(rarity.getColor())
                .decoration(TextDecoration.ITALIC, false));

        // Lore
        List<Map<?, ?>> localizedLoreEntries = resolveLocalizedLoreEntries(id, loreEntries);
        if (!localizedLoreEntries.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (Map<?, ?> entry : localizedLoreEntries) {
                String text = entry.containsKey("text") ? String.valueOf(entry.get("text")) : "";
                if (text.isEmpty()) {
                    lore.add(Component.empty());
                    continue;
                }
                String colorName = entry.containsKey("color") ? String.valueOf(entry.get("color")) : "GRAY";
                NamedTextColor loreColor = NamedTextColor.NAMES.value(colorName.toLowerCase());
                if (loreColor == null) loreColor = NamedTextColor.GRAY;
                lore.add(Component.text(text).color(loreColor)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        }

        // PDC: identifier byte key
        NamespacedKey pdcKey = new NamespacedKey(plugin, pdcKeyStr);
        meta.getPersistentDataContainer().set(pdcKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(itemManager.CUSTOM_ITEM_KEY, PersistentDataType.STRING, id);
        itemManager.setVanillaFallbackMaterial(meta, item.getType());

        // PDC: rarity
        meta.getPersistentDataContainer().set(itemManager.RARITY_KEY,
                PersistentDataType.STRING, rarity.name());

        item.setItemMeta(meta);
        plugin.getLoreManager().updateLore(item);
        return item;
    }

    private String resolveLocalizedLoreLine(String itemId, int index, String fallback) {
        return plugin.getLanguageManager().getString(
                "item." + itemId + ".lore." + index,
                fallback
        );
    }

    private List<String> resolveLocalizedLoreLines(String itemId, List<String> fallbackLines) {
        List<String> localized = new ArrayList<>();
        for (int i = 0; i < fallbackLines.size(); i++) {
            localized.add(resolveLocalizedLoreLine(itemId, i, fallbackLines.get(i)));
        }
        return localized;
    }

    private List<Map<?, ?>> resolveLocalizedLoreEntries(String itemId, List<Map<?, ?>> fallbackEntries) {
        List<Map<?, ?>> localized = new ArrayList<>();
        for (int i = 0; i < fallbackEntries.size(); i++) {
            Map<?, ?> fallbackEntry = fallbackEntries.get(i);
            Map<String, Object> localizedEntry = new LinkedHashMap<>();
            if (fallbackEntry != null) {
                for (Map.Entry<?, ?> entry : fallbackEntry.entrySet()) {
                    localizedEntry.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            String fallbackText = localizedEntry.containsKey("text")
                    ? String.valueOf(localizedEntry.get("text"))
                    : "";
            localizedEntry.put("text", resolveLocalizedLoreLine(itemId, i, fallbackText));
            localized.add(localizedEntry);
        }
        return localized;
    }

    // ── Utilities ───────────────────────────────────────────

    /** Derives the material enum value for a standard armor piece. */
    private Material deriveMaterial(String materialType, String pieceType) {
        String prefix = materialType.toUpperCase();
        String suffix = switch (pieceType.toUpperCase()) {
            case "HELMET" -> "_HELMET";
            case "CHESTPLATE" -> "_CHESTPLATE";
            case "LEGGINGS" -> "_LEGGINGS";
            case "BOOTS" -> "_BOOTS";
            default -> "_" + pieceType.toUpperCase();
        };
        try {
            return Material.valueOf(prefix + suffix);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown material: " + prefix + suffix + ", falling back to IRON" + suffix);
            return Material.valueOf("IRON" + suffix);
        }
    }

    private void applyArmorTrim(ArmorMeta meta, String registryId, String trimMaterialKey, String trimPatternKey) {
        if ((trimMaterialKey == null || trimMaterialKey.isBlank())
                && (trimPatternKey == null || trimPatternKey.isBlank())) {
            return;
        }
        if (trimMaterialKey == null || trimMaterialKey.isBlank()
                || trimPatternKey == null || trimPatternKey.isBlank()) {
            plugin.getLogger().warning("Armor trim for " + registryId + " is missing material or pattern.");
            return;
        }

        String normalizedTrimMaterial = normalizeTrimMaterial(trimMaterialKey);
        String normalizedTrimPattern = normalizeTrimPattern(trimPatternKey);
        TrimMaterial trimMaterial = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(normalizedTrimMaterial.toLowerCase(Locale.ROOT)));
        TrimPattern trimPattern = Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(normalizedTrimPattern.toLowerCase(Locale.ROOT)));
        if (trimMaterial == null || trimPattern == null) {
            plugin.getLogger().warning("Unknown armor trim for " + registryId + ": material="
                    + trimMaterialKey + ", pattern=" + trimPatternKey);
            return;
        }

        meta.setTrim(new ArmorTrim(trimMaterial, trimPattern));
    }

    private String normalizeTrimMaterial(String trimMaterialKey) {
        if (trimMaterialKey == null) return "";
        String normalized = trimMaterialKey.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "resin_brick" -> "resin";
            default -> normalized;
        };
    }

    private String normalizeTrimPattern(String trimPatternKey) {
        if (trimPatternKey == null) return "";
        String normalized = trimPatternKey.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "bold" -> "bolt";
            default -> normalized;
        };
    }

    private org.bukkit.Color parseLeatherColor(List<Integer> rgb) {
        if (rgb == null || rgb.size() != 3) return null;
        int r = Math.max(0, Math.min(255, rgb.get(0)));
        int g = Math.max(0, Math.min(255, rgb.get(1)));
        int b = Math.max(0, Math.min(255, rgb.get(2)));
        return org.bukkit.Color.fromRGB(r, g, b);
    }

    private boolean isLeatherMaterial(Material material) {
        return material != null && material.name().startsWith("LEATHER_");
    }

    private Material resolveMaterial(String rawName, Material fallback) {
        if (rawName == null || rawName.isBlank()) {
            return fallback;
        }

        String normalized = switch (rawName.toUpperCase(Locale.ROOT)) {
            case "GOLD_HELMET" -> "GOLDEN_HELMET";
            case "GOLD_CHESTPLATE" -> "GOLDEN_CHESTPLATE";
            case "GOLD_LEGGINGS" -> "GOLDEN_LEGGINGS";
            case "GOLD_BOOTS" -> "GOLDEN_BOOTS";
            default -> rawName.toUpperCase(Locale.ROOT);
        };

        try {
            return Material.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown material: " + rawName + ", falling back to " + fallback);
            return fallback;
        }
    }

    private Rarity parseRarity(String rawValue, Rarity fallback, String context) {
        return YamlParseSupport.parseEnum(plugin, Rarity.class, rawValue, fallback, context);
    }

    /** Resolves an attribute name like "water_movement_efficiency" to its Bukkit Attribute. */
    private Attribute resolveAttribute(String name) {
        return switch (name.toLowerCase()) {
            case "water_movement_efficiency" -> Attribute.WATER_MOVEMENT_EFFICIENCY;
            case "movement_speed" -> Attribute.MOVEMENT_SPEED;
            case "armor" -> Attribute.ARMOR;
            case "armor_toughness" -> Attribute.ARMOR_TOUGHNESS;
            case "max_health" -> Attribute.MAX_HEALTH;
            case "attack_damage" -> Attribute.ATTACK_DAMAGE;
            case "attack_speed" -> Attribute.ATTACK_SPEED;
            case "knockback_resistance" -> Attribute.KNOCKBACK_RESISTANCE;
            default -> {
                plugin.getLogger().warning("Unknown attribute: " + name);
                yield null;
            }
        };
    }

    private void applyEnchantments(ItemStack item, Map<String, Integer> enchantments) {
        for (Map.Entry<String, Integer> enchEntry : enchantments.entrySet()) {
            String enchantName = enchEntry.getKey();
            int level = enchEntry.getValue();
            Enchantment enchantment = resolveEnchantment(enchantName);
            if (enchantment != null) {
                item.addUnsafeEnchantment(enchantment, level);
            } else {
                applyKnownCustomEnchantmentFallback(item, enchantName, level);
            }
        }
    }

    private boolean applyKnownCustomEnchantmentFallback(ItemStack item, String name, int level) {
        NamespacedKey key = toNamespacedKey(name);
        if (key == null || !"fishrework".equals(key.getNamespace()) || !"shotgun_volley".equals(key.getKey())) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return true;
        }
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "shotgun_volley_level"),
                PersistentDataType.INTEGER,
                Math.max(1, level));
        item.setItemMeta(meta);

        if (warnedCustomEnchantFallbacks.add(key.toString())) {
            plugin.getLogger().warning(
                    "Custom enchantment " + key + " is not registered; using FishRework's item fallback. "
                            + "Restart or datapack-reload the server after datapack extraction for the vanilla enchantment entry.");
        }
        return true;
    }

    private boolean isKnownCustomEnchantment(String name) {
        NamespacedKey key = toNamespacedKey(name);
        return key != null && "fishrework".equals(key.getNamespace()) && "shotgun_volley".equals(key.getKey());
    }

    private NamespacedKey toNamespacedKey(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
    }

    /** Resolves an enchantment name like "RESPIRATION" to its Bukkit Enchantment. */
    private Enchantment resolveEnchantment(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        return switch (name.toUpperCase()) {
            case "RESPIRATION" -> Enchantment.RESPIRATION;
            case "AQUA_AFFINITY" -> Enchantment.AQUA_AFFINITY;
            case "DEPTH_STRIDER" -> Enchantment.DEPTH_STRIDER;
            case "PROTECTION" -> Enchantment.PROTECTION;
            case "FIRE_PROTECTION" -> Enchantment.FIRE_PROTECTION;
            case "BLAST_PROTECTION" -> Enchantment.BLAST_PROTECTION;
            case "PROJECTILE_PROTECTION" -> Enchantment.PROJECTILE_PROTECTION;
            case "THORNS" -> Enchantment.THORNS;
            case "UNBREAKING" -> Enchantment.UNBREAKING;
            case "MENDING" -> Enchantment.MENDING;
            case "FROST_WALKER" -> Enchantment.FROST_WALKER;
            case "SOUL_SPEED" -> Enchantment.SOUL_SPEED;
            case "SWIFT_SNEAK" -> Enchantment.SWIFT_SNEAK;
            case "FEATHER_FALLING" -> Enchantment.FEATHER_FALLING;
            case "LOYALTY" -> Enchantment.LOYALTY;
            case "RIPTIDE" -> Enchantment.RIPTIDE;
            case "CHANNELING" -> Enchantment.CHANNELING;
            case "IMPALING" -> Enchantment.IMPALING;
            case "QUICK_CHARGE" -> Enchantment.QUICK_CHARGE;
            case "MULTISHOT" -> Enchantment.MULTISHOT;
            case "PIERCING" -> Enchantment.PIERCING;
            case "POWER" -> Enchantment.POWER;
            case "PUNCH" -> Enchantment.PUNCH;
            case "FLAME" -> Enchantment.FLAME;
            case "INFINITY" -> Enchantment.INFINITY;
            default -> {
                NamespacedKey key = toNamespacedKey(name);
                if (key != null) {
                    Enchantment custom = Registry.ENCHANTMENT.get(key);
                    if (custom != null) {
                        yield custom;
                    }
                }
                if (!isKnownCustomEnchantment(name) && warnedUnknownEnchantments.add(name.toLowerCase(Locale.ROOT))) {
                    plugin.getLogger().warning("Unknown enchantment: " + name);
                }
                yield null;
            }
        };
    }
}

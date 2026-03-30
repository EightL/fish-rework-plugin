package com.fishrework.manager;

import com.fishrework.FishRework;
import com.fishrework.model.Artifact;
import com.fishrework.model.Rarity;
import com.fishrework.model.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.OminousBottleMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TreasureManager {

    private final FishRework plugin;
    private final Random random = new Random();

    // ── Chance tiers — controls how likely an item is to be selected ──
    // These are NOT the same as chest/item Rarity (COMMON/UNCOMMON/RARE/EPIC/LEGENDARY/MYTHIC).
    // They only determine selection weight within a pool.
    private enum ChanceTier {
        COMMON(40), UNCOMMON(30), RARE(20), EPIC(10);
        final int weight;
        ChanceTier(int weight) { this.weight = weight; }
    }

    /** A single entry in a treasure loot pool. */
    private record LootEntry(Supplier<ItemStack> itemSupplier, int maxQuantity, ChanceTier chanceTier) {}

    public TreasureManager(FishRework plugin) {
        this.plugin = plugin;
    }

    public ItemStack getRandomTreasure() {
        double roll = random.nextDouble() * 100.0;
        if (roll < plugin.getConfig().getDouble("treasure_balance.fallback_common_threshold", 50.0)) return plugin.getItemManager().getTreasure(Rarity.COMMON);
        if (roll < plugin.getConfig().getDouble("treasure_balance.fallback_uncommon_threshold", 75.0)) return plugin.getItemManager().getTreasure(Rarity.UNCOMMON);
        if (roll < plugin.getConfig().getDouble("treasure_balance.fallback_rare_threshold", 90.0)) return plugin.getItemManager().getTreasure(Rarity.RARE);
        if (roll < plugin.getConfig().getDouble("treasure_balance.fallback_epic_threshold", 98.0)) return plugin.getItemManager().getTreasure(Rarity.EPIC);
        if (roll < plugin.getConfig().getDouble("treasure_balance.fallback_legendary_threshold", 99.5)) return plugin.getItemManager().getTreasure(Rarity.LEGENDARY);
        return plugin.getItemManager().getTreasure(Rarity.MYTHIC);
    }

    public void openTreasure(Player player, ItemStack chest) {
        if (!chest.hasItemMeta()) return;
        String rarityName = chest.getItemMeta().getPersistentDataContainer()
                .get(plugin.getItemManager().TREASURE_TYPE_KEY, PersistentDataType.STRING);
        if (rarityName == null) return;

        boolean isNether = rarityName.startsWith("NETHER_");
        String baseRarityName = isNether ? rarityName.substring(7) : rarityName;

        Rarity rarity;
        try {
            rarity = Rarity.valueOf(baseRarityName);
        } catch (IllegalArgumentException e) {
            return;
        }

        String displayName = (isNether ? "Nether " : "") + baseRarityName.charAt(0) + baseRarityName.substring(1).toLowerCase() + " Treasure";
        Inventory inv = Bukkit.createInventory(new TreasureHolder(), 27,
                Component.text(displayName).color(rarity.getColor()));

        List<ItemStack> loot = generateLoot(rarity, isNether);

        // ── Artifact discovery — check if any loot item is an artifact ──
        NamespacedKey artifactKey = plugin.getItemManager().ARTIFACT_KEY;
        for (ItemStack item : loot) {
            if (item == null || !item.hasItemMeta()) continue;
            String artifactId = item.getItemMeta().getPersistentDataContainer()
                    .get(artifactKey, PersistentDataType.STRING);
            if (artifactId == null) continue;

            Artifact artifact = plugin.getArtifactRegistry().get(artifactId);
            if (artifact == null) continue;

            PlayerData data = plugin.getPlayerData(player.getUniqueId());
            if (data != null && !data.hasArtifact(artifact.getId())) {
                data.addArtifact(artifact.getId());
                plugin.getDatabaseManager().saveArtifact(player.getUniqueId(), artifact.getId());
                player.sendMessage(Component.text(artifact.getDisplayName() + " added to Collection!")
                        .color(artifact.getRarity().getColor())
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                Component.text("Click to view Artifact Collection!")))
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/fishing artifacts")));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
            }
        }

        // ── Distribute items into chest slots with split-stacking ──
        for (ItemStack item : loot) {
            distributeToSlots(inv, item);
        }

        player.openInventory(inv);
        playTreasureOpenSound(player, rarity);
    }

    /** Plays rarity-specific treasure opening sounds. */
    private void playTreasureOpenSound(Player player, Rarity rarity) {
        switch (rarity) {
            case COMMON    -> player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            case UNCOMMON  -> player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.7f);
            case RARE      -> player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            case EPIC      -> player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            case LEGENDARY -> {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.5f, 1.5f);
            }
            case MYTHIC -> {
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
                player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.9f, 0.7f);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Core loot generation
    // ══════════════════════════════════════════════════════════════

    /**
     * Generates loot for a chest of the given rarity.
     * <p>
     * 1. Rolls 7-12 unique items.<br>
     * 2. Hard rule for the first 7: 4 common, 2 uncommon, 1 rare.<br>
     * 3. Extra items (above 7) are rolled by weighted chance tiers
     *    (40% common, 30% uncommon, 20% rare, 10% epic).
     */
    private List<ItemStack> generateLoot(Rarity rarity, boolean isNether) {
        int uniqueCount = rollUniqueCount();
        List<LootEntry> pool = getLootPool(rarity, isNether);

        // Bucket entries by ChanceTier
        Map<ChanceTier, List<LootEntry>> buckets = new EnumMap<>(ChanceTier.class);
        for (ChanceTier tier : ChanceTier.values()) buckets.put(tier, new ArrayList<>());
        for (LootEntry entry : pool) buckets.get(entry.chanceTier()).add(entry);
        // Shuffle each bucket
        for (List<LootEntry> bucket : buckets.values()) Collections.shuffle(bucket, random);

        List<LootEntry> selected = new ArrayList<>();

        // ── Hard rule: 4 common, 2 uncommon, 1 rare ──
        pickFromBucket(buckets, ChanceTier.COMMON,   4, selected);
        pickFromBucket(buckets, ChanceTier.UNCOMMON,  2, selected);
        pickFromBucket(buckets, ChanceTier.RARE,      1, selected);

        // ── Remaining slots: roll by weighted chance ──
        int remaining = uniqueCount - selected.size();
        for (int i = 0; i < remaining; i++) {
            ChanceTier tier = rollChanceTier();
            // Try the rolled tier first; if exhausted, try others in descending weight order
            if (!pickFromBucket(buckets, tier, 1, selected)) {
                boolean found = false;
                for (ChanceTier fallback : ChanceTier.values()) {
                    if (fallback != tier && pickFromBucket(buckets, fallback, 1, selected)) {
                        found = true;
                        break;
                    }
                }
                if (!found) break; // entire pool exhausted
            }
        }

        // ── Roll quantities and build ItemStacks ──
        List<ItemStack> results = new ArrayList<>();
        for (LootEntry entry : selected) {
            int quantity = 1 + random.nextInt(entry.maxQuantity());
            ItemStack item = entry.itemSupplier().get();
            item.setAmount(quantity);
            results.add(item);
        }
        return results;
    }

    /**
     * Picks up to {@code count} entries from the given tier bucket, removes them,
     * and adds to {@code selected}. Returns true if at least one was picked.
     */
    private boolean pickFromBucket(Map<ChanceTier, List<LootEntry>> buckets,
                                   ChanceTier tier, int count, List<LootEntry> selected) {
        List<LootEntry> bucket = buckets.get(tier);
        boolean pickedAny = false;
        for (int i = 0; i < count && !bucket.isEmpty(); i++) {
            selected.add(bucket.remove(bucket.size() - 1));
            pickedAny = true;
        }
        return pickedAny;
    }

    /** Rolls a ChanceTier: 40% common, 30% uncommon, 20% rare, 10% epic. */
    private ChanceTier rollChanceTier() {
        double r = random.nextDouble() * 100.0;
        if (r < plugin.getConfig().getDouble("treasure_balance.loot_tier_common_threshold", 40.0)) return ChanceTier.COMMON;
        if (r < plugin.getConfig().getDouble("treasure_balance.loot_tier_uncommon_threshold", 70.0)) return ChanceTier.UNCOMMON;
        if (r < plugin.getConfig().getDouble("treasure_balance.loot_tier_rare_threshold", 90.0)) return ChanceTier.RARE;
        return ChanceTier.EPIC;
    }

    /** 30% → 7, 25% → 8, 20% → 9, 12% → 10, 8% → 11, 5% → 12 */
    private int rollUniqueCount() {
        double r = random.nextDouble() * 100.0;
        if (r < plugin.getConfig().getDouble("treasure_balance.item_count_7_threshold", 30.0)) return 7;
        if (r < plugin.getConfig().getDouble("treasure_balance.item_count_8_threshold", 55.0)) return 8;
        if (r < plugin.getConfig().getDouble("treasure_balance.item_count_9_threshold", 75.0)) return 9;
        if (r < plugin.getConfig().getDouble("treasure_balance.item_count_10_threshold", 87.0)) return 10;
        if (r < plugin.getConfig().getDouble("treasure_balance.item_count_11_threshold", 95.0)) return 11;
        return 12;
    }

    // ══════════════════════════════════════════════════════════════
    //  Split-stacking distribution
    // ══════════════════════════════════════════════════════════════

    /**
     * Places an ItemStack into random empty slot(s) in the inventory.
     * 50 % chance to split the stack each time (if amount > 1).
     */
    private void distributeToSlots(Inventory inv, ItemStack item) {
        int amount = item.getAmount();
        if (amount <= 0) return;

        if (amount > 1 && random.nextBoolean()) {
            int half = amount / 2;
            int other = amount - half;
            if (random.nextBoolean()) { int tmp = half; half = other; other = tmp; }

            ItemStack part1 = item.clone();
            part1.setAmount(half);
            placeInEmptySlot(inv, part1);

            ItemStack part2 = item.clone();
            part2.setAmount(other);
            distributeToSlots(inv, part2);
        } else {
            placeInEmptySlot(inv, item);
        }
    }

    private void placeInEmptySlot(Inventory inv, ItemStack item) {
        List<Integer> emptySlots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) emptySlots.add(i);
        }
        if (emptySlots.isEmpty()) return;
        int slot = emptySlots.get(random.nextInt(emptySlots.size()));
        inv.setItem(slot, item);
    }

    // ══════════════════════════════════════════════════════════════
    //  Loot pools per chest rarity (with ChanceTier tags)
    // ══════════════════════════════════════════════════════════════

    private List<LootEntry> getLootPool(Rarity rarity, boolean isNether) {
        if (isNether) {
            return switch (rarity) {
                case COMMON     -> getNetherCommonPool();
                case UNCOMMON   -> getNetherUncommonPool();
                case RARE       -> getNetherRarePool();
                case EPIC       -> getNetherEpicPool();
                case LEGENDARY  -> getNetherLegendaryPool();
                case MYTHIC     -> getNetherMythicPool();
            };
        }
        return switch (rarity) {
            case COMMON     -> getCommonPool();
            case UNCOMMON   -> getUncommonPool();
            case RARE       -> getRarePool();
            case EPIC       -> getEpicPool();
            case LEGENDARY  -> getLegendaryPool();
            case MYTHIC     -> getMythicPool();
        };
    }

    // ── Common Chest Pool ────────────────────────────────────────

    private List<LootEntry> getCommonPool() {
        ItemManager im = plugin.getItemManager();
        List<LootEntry> pool = new ArrayList<>();
        //                                                                          qty   ChanceTier
        pool.add(new LootEntry(() -> new ItemStack(Material.PUFFERFISH),             1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.FEATHER),                3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.COD),                    3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.POTATO),                 6, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.BEETROOT),               4, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.DRIED_KELP),             4, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.APPLE),                  3, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.COPPER_NAUTILUS_ARMOR),  1, ChanceTier.RARE));
        pool.add(new LootEntry(this::getSuspiciousStew,                              1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.CANDLE),                 1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.SALMON),                 2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.TROPICAL_FISH),          1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.WHEAT),                  3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.GUNPOWDER),              2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.LEATHER_HORSE_ARMOR),    1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> new ItemStack(Material.STICK),                  6, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.COPPER_INGOT),           2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.POISONOUS_POTATO),       1, ChanceTier.UNCOMMON));
        // Custom materials
        pool.add(new LootEntry(() -> im.getItem("corrupted_flesh"),                  1, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("residue"),                          1, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("wet_bone"),                         1, ChanceTier.COMMON));
        // Artifact
        pool.add(new LootEntry(() -> getRandomArtifactItem(Rarity.COMMON),           1, ChanceTier.EPIC));
        return pool;
    }

    // ── Uncommon Chest Pool ──────────────────────────────────────

    private List<LootEntry> getUncommonPool() {
        ItemManager im = plugin.getItemManager();
        List<LootEntry> pool = new ArrayList<>();
        pool.add(new LootEntry(() -> new ItemStack(Material.APPLE),                  3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.COPPER_INGOT),           3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.IRON_INGOT),             3, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.BREAD),                  3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.MELON_SLICE),            3, ChanceTier.COMMON));
        pool.add(new LootEntry(this::getRandomPotterySherd,                          1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> new ItemStack(Material.GUNPOWDER),              3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.NAUTILUS_SHELL),         1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.COPPER_HORSE_ARMOR),     1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.IRON_NAUTILUS_ARMOR),    1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.ARROW),                  2, ChanceTier.COMMON));
        pool.add(new LootEntry(this::getSuspiciousStew,                              1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.COD),                    4, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.TROPICAL_FISH),          2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.PUFFERFISH),             3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.SALMON),                 3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.CANDLE),                 2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.IRON_HORSE_ARMOR),       1, ChanceTier.RARE));
        // Custom materials
        pool.add(new LootEntry(() -> im.getItem("corrupted_flesh"),                  2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("residue"),                          2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("wet_bone"),                         2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("guardian_scale"),                    1, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("spider_silk"),                      1, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("hyena_fur"),                        1, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("battle_scrap"),                     1, ChanceTier.COMMON));
        // Artifact
        pool.add(new LootEntry(() -> getRandomArtifactItem(Rarity.UNCOMMON),         1, ChanceTier.EPIC));
        return pool;
    }

    // ── Rare Chest Pool ──────────────────────────────────────────

    private List<LootEntry> getRarePool() {
        ItemManager im = plugin.getItemManager();
        List<LootEntry> pool = new ArrayList<>();
        pool.add(new LootEntry(() -> new ItemStack(Material.TURTLE_SCUTE),           1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.GOLDEN_NAUTILUS_ARMOR),  1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> getEnchantedBook(1, 10),                        1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.GOLDEN_CARROT),          3, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(this::getOminousBottle,                               1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> new ItemStack(Material.REDSTONE),               4, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.LAPIS_LAZULI),           5, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.GOLD_INGOT),             2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.ARROW),                  3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE), 1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> new ItemStack(Material.GUNPOWDER),              4, ChanceTier.COMMON));
        pool.add(new LootEntry(this::getSuspiciousStew,                              1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.NAUTILUS_SHELL),         1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.TROPICAL_FISH),          4, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.PUFFERFISH),             5, ChanceTier.COMMON));
        pool.add(new LootEntry(this::getRandomPotterySherd,                          1, ChanceTier.RARE));
        // Custom materials
        pool.add(new LootEntry(() -> im.getItem("battle_scrap"),                     2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("hyena_fur"),                        2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("spider_silk"),                      2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("guardian_scale"),                    2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("slimy_chunk"),                      1, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("polar_fur"),                        1, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("ravager_scales"),                   1, ChanceTier.COMMON));
        // Artifact
        pool.add(new LootEntry(() -> getRandomArtifactItem(Rarity.RARE),             1, ChanceTier.EPIC));
        return pool;
    }

    // ── Epic Chest Pool ──────────────────────────────────────────

    private List<LootEntry> getEpicPool() {
        ItemManager im = plugin.getItemManager();
        List<LootEntry> pool = new ArrayList<>();
        pool.add(new LootEntry(() -> new ItemStack(Material.DIAMOND_NAUTILUS_ARMOR), 1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> new ItemStack(Material.DIAMOND_HORSE_ARMOR),    1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> new ItemStack(Material.GOLDEN_APPLE),           3, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.SNIFFER_EGG),            1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> new ItemStack(Material.HEART_OF_THE_SEA),       1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.DIAMOND),                2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> getEnchantedBook(11, 20),                       1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(this::getOminousBottle,                               1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> new ItemStack(Material.AMETHYST_SHARD),         3, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.EMERALD),                5, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.EXPERIENCE_BOTTLE),      2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.PUFFERFISH),             8, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.TROPICAL_FISH),          8, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.GOLDEN_CARROT),          4, ChanceTier.COMMON));
        // Custom materials
        pool.add(new LootEntry(() -> im.getItem("battle_scrap"),                     3, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("ravager_scales"),                   2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("polar_fur"),                        2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("slimy_chunk"),                      2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("ice_wing"),                         1, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("shark_tooth"),                      1, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("dread_soul"),                       1, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("ironclad_plate"),                   1, ChanceTier.COMMON));
        // Artifact
        pool.add(new LootEntry(() -> getRandomArtifactItem(Rarity.EPIC),             1, ChanceTier.EPIC));
        return pool;
    }

    // ── Legendary Chest Pool ─────────────────────────────────────

    private List<LootEntry> getLegendaryPool() {
        ItemManager im = plugin.getItemManager();
        List<LootEntry> pool = new ArrayList<>();
        pool.add(new LootEntry(() -> new ItemStack(Material.NETHERITE_NAUTILUS_ARMOR), 1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> new ItemStack(Material.NETHERITE_HORSE_ARMOR),    1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> new ItemStack(Material.ENCHANTED_GOLDEN_APPLE),   1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.EMERALD_BLOCK),            2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.GOLD_BLOCK),               2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.NETHERITE_SCRAP),          2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.EXPERIENCE_BOTTLE),        4, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.PUFFERFISH),               8, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.TROPICAL_FISH),            8, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.DIAMOND),                  5, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> getEnchantedBook(21, 30),                         1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.GOLDEN_APPLE),             4, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.TRIDENT),                  1, ChanceTier.UNCOMMON));
        // Custom materials
        pool.add(new LootEntry(() -> im.getItem("battle_scrap"),                     4, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("ironclad_plate"),                   2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("enchanted_hyena_fur"),              2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("enchanted_wet_bone"),               2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("enchanted_corrupted_flesh"),        2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("dread_soul"),                       2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("ice_wing"),                         2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("shark_tooth"),                      2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("beast_scale"),                      1, ChanceTier.COMMON));
        // Artifact
        pool.add(new LootEntry(() -> getRandomArtifactItem(Rarity.LEGENDARY),          1, ChanceTier.EPIC));
        return pool;
    }

    // ── Mythic Chest Pool ───────────────────────────────────────

    private List<LootEntry> getMythicPool() {
        ItemManager im = plugin.getItemManager();
        List<LootEntry> pool = new ArrayList<>();
        pool.add(new LootEntry(() -> new ItemStack(Material.NETHER_STAR),                 1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> new ItemStack(Material.ENCHANTED_GOLDEN_APPLE),      2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.NETHERITE_INGOT),              2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE), 1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> new ItemStack(Material.DIAMOND_BLOCK),                2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.EXPERIENCE_BOTTLE),            8, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("beast_scale"),                            2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("enchanted_hyena_fur"),                    3, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("enchanted_wet_bone"),                     3, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("enchanted_corrupted_flesh"),              3, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("dread_soul"),                             3, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> getRandomArtifactItem(Rarity.MYTHIC),                  1, ChanceTier.EPIC));
        return pool;
    }

    // ── Nether Common Chest Pool ────────────────────────────────────────
    private List<LootEntry> getNetherCommonPool() {
        ItemManager im = plugin.getItemManager();
        List<LootEntry> pool = new ArrayList<>();
        pool.add(new LootEntry(() -> new ItemStack(Material.ROTTEN_FLESH),           4, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.BONE),                   3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.GOLD_NUGGET),            5, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.QUARTZ),                 3, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.MAGMA_CREAM),            1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> new ItemStack(Material.SOUL_SAND),              4, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.BLACKSTONE),             6, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.NETHERRACK),             10, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("corrupted_flesh"),                  2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("residue"),                          2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> getRandomArtifactItem(Rarity.COMMON),           1, ChanceTier.EPIC));
        return pool;
    }

    // ── Nether Uncommon Chest Pool ─────────────────────────────────
    private List<LootEntry> getNetherUncommonPool() {
        ItemManager im = plugin.getItemManager();
        List<LootEntry> pool = new ArrayList<>();
        pool.add(new LootEntry(() -> new ItemStack(Material.GOLD_INGOT),             2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.CRIMSON_FUNGUS),         3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.WARPED_FUNGUS),          3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.BLAZE_POWDER),           2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.MAGMA_BLOCK),            2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.GHAST_TEAR),             1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> im.getItem("spider_silk"),                      2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("hyena_fur"),                        2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("battle_scrap"),                     1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> getRandomArtifactItem(Rarity.UNCOMMON),         1, ChanceTier.EPIC));
        return pool;
    }

    // ── Nether Rare Chest Pool ─────────────────────────────────────
    private List<LootEntry> getNetherRarePool() {
        ItemManager im = plugin.getItemManager();
        List<LootEntry> pool = new ArrayList<>();
        pool.add(new LootEntry(() -> new ItemStack(Material.BLAZE_ROD),              2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.GHAST_TEAR),             2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.MAGMA_CREAM),            4, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.GOLD_BLOCK),             1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.WITHER_SKELETON_SKULL),  1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> new ItemStack(Material.NETHERITE_SCRAP),        1, ChanceTier.EPIC));
        pool.add(new LootEntry(() -> im.getItem("slimy_chunk"),                      2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("ravager_scales"),                   1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("battle_scrap"),                     2, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> getRandomArtifactItem(Rarity.RARE),             1, ChanceTier.EPIC));
        return pool;
    }

    // ── Nether Epic Chest Pool ─────────────────────────────────────
    private List<LootEntry> getNetherEpicPool() {
        ItemManager im = plugin.getItemManager();
        List<LootEntry> pool = new ArrayList<>();
        pool.add(new LootEntry(() -> new ItemStack(Material.NETHERITE_SCRAP),        1, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.WITHER_SKELETON_SKULL),  1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.ANCIENT_DEBRIS),         1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.BLAZE_ROD),              5, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.GHAST_TEAR),             4, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("ice_wing"),                         1, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("dread_soul"),                       1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("battle_scrap"),                     3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> getRandomArtifactItem(Rarity.EPIC),             1, ChanceTier.EPIC));
        return pool;
    }

    // ── Nether Legendary Chest Pool ────────────────────────────────
    private List<LootEntry> getNetherLegendaryPool() {
        ItemManager im = plugin.getItemManager();
        List<LootEntry> pool = new ArrayList<>();
        pool.add(new LootEntry(() -> new ItemStack(Material.NETHERITE_INGOT),        1, ChanceTier.RARE));
        pool.add(new LootEntry(() -> new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE), 1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.ANCIENT_DEBRIS),         2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.NETHERITE_SCRAP),        3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> new ItemStack(Material.WITHER_SKELETON_SKULL),  3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("beast_scale"),                      1, ChanceTier.UNCOMMON));
        pool.add(new LootEntry(() -> im.getItem("dread_soul"),                       2, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> im.getItem("enchanted_corrupted_flesh"),        3, ChanceTier.COMMON));
        pool.add(new LootEntry(() -> getRandomArtifactItem(Rarity.LEGENDARY),        1, ChanceTier.EPIC));
        return pool;
    }

    // ── Nether Mythic Chest Pool ─────────────────────────────────
    private List<LootEntry> getNetherMythicPool() {
        return getNetherLegendaryPool();
    }

    // ══════════════════════════════════════════════════════════════
    //  Special item helpers
    // ══════════════════════════════════════════════════════════════

    /** Returns a random artifact item matching the given chest rarity. */
    private ItemStack getRandomArtifactItem(Rarity rarity) {
        List<Artifact> pool = plugin.getArtifactRegistry().getByRarity(rarity);
        if (pool.isEmpty()) {
            // Fallback — should never happen if artifacts are registered
            return new ItemStack(Material.PAPER);
        }
        Artifact artifact = pool.get(random.nextInt(pool.size()));
        return plugin.getItemManager().createArtifactItem(artifact);
    }

    /**
     * Enchanted book with random enchant, level scaled by enchanting table power range.
     * <p>
     * Maps enchanting table level ranges to enchantment level tiers:<br>
     * Table 1-10 → bottom third of the enchantment's levels<br>
     * Table 11-20 → middle third<br>
     * Table 21-30 → top third
     *
     * @param minTableLevel minimum enchanting table level (1-30)
     * @param maxTableLevel maximum enchanting table level (1-30)
     */
    private ItemStack getEnchantedBook(int minTableLevel, int maxTableLevel) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        org.bukkit.enchantments.Enchantment[] enchants = org.bukkit.enchantments.Enchantment.values();
        org.bukkit.enchantments.Enchantment enchant = enchants[random.nextInt(enchants.length)];

        int max = enchant.getMaxLevel();

        // Determine enchantment level range based on enchanting table power tier
        int minLevel, maxLevel;
        if (maxTableLevel <= 10) {
            // Low-tier: bottom third of enchant levels
            minLevel = 1;
            maxLevel = (int) Math.ceil(max / 3.0);
        } else if (maxTableLevel <= 20) {
            // Mid-tier: middle third of enchant levels
            minLevel = (int) Math.ceil(max / 3.0) + 1;
            maxLevel = (int) Math.ceil(2.0 * max / 3.0);
        } else {
            // High-tier: top third of enchant levels
            minLevel = (int) Math.ceil(2.0 * max / 3.0) + 1;
            maxLevel = max;
        }

        // Clamp to valid enchantment bounds
        minLevel = Math.max(1, Math.min(minLevel, max));
        maxLevel = Math.max(minLevel, Math.min(maxLevel, max));

        int level = minLevel + random.nextInt(maxLevel - minLevel + 1);
        meta.addStoredEnchant(enchant, level, true);
        book.setItemMeta(meta);
        return book;
    }

    /** Random pottery sherd from all available types. */
    private ItemStack getRandomPotterySherd() {
        Material[] sherds = Arrays.stream(Material.values())
                .filter(m -> m.name().endsWith("_POTTERY_SHERD"))
                .toArray(Material[]::new);
        return new ItemStack(sherds[random.nextInt(sherds.length)]);
    }

    /** Suspicious stew with a random potion effect. */
    private ItemStack getSuspiciousStew() {
        ItemStack stew = new ItemStack(Material.SUSPICIOUS_STEW);
        SuspiciousStewMeta meta = (SuspiciousStewMeta) stew.getItemMeta();
        PotionEffectType[] types = {
                PotionEffectType.NIGHT_VISION, PotionEffectType.JUMP_BOOST,
                PotionEffectType.WEAKNESS, PotionEffectType.BLINDNESS,
                PotionEffectType.POISON, PotionEffectType.SATURATION,
                PotionEffectType.FIRE_RESISTANCE, PotionEffectType.REGENERATION
        };
        PotionEffectType type = types[random.nextInt(types.length)];
        meta.addCustomEffect(new PotionEffect(type, plugin.getConfig().getInt("treasure_balance.suspicious_stew_duration_ticks", 200), 0), true);
        stew.setItemMeta(meta);
        return stew;
    }

    /** Ominous bottle with random amplifier (0-4 → Bad Omen I-V). */
    private ItemStack getOminousBottle() {
        ItemStack bottle = new ItemStack(Material.OMINOUS_BOTTLE);
        OminousBottleMeta meta = (OminousBottleMeta) bottle.getItemMeta();
        meta.setAmplifier(random.nextInt(5));
        bottle.setItemMeta(meta);
        return bottle;
    }

    // ══════════════════════════════════════════════════════════════
    //  Loot table description (for treasure chest tooltips)
    // ══════════════════════════════════════════════════════════════

    public List<Component> getLootTable(Rarity rarity, boolean isNether) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Possible Loot:").color(NamedTextColor.GOLD));

        if (isNether) {
            switch (rarity) {
                case COMMON:
                    lore.add(Component.text("- Rotten Flesh, Bone, Gold Nugget").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Quartz, Magma Cream, Soul Sand").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Blackstone, Netherrack").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Corrupted Flesh, Residue").color(NamedTextColor.GREEN));
                    lore.add(Component.text("- ⭐ Artifact").color(NamedTextColor.LIGHT_PURPLE));
                    break;
                case UNCOMMON:
                    lore.add(Component.text("- Gold Ingot, Crimson Fungus").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Warped Fungus, Blaze Powder").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Magma Block, Ghast Tear").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Spider Silk, Hyena Fur, Battle Scrap").color(NamedTextColor.GREEN));
                    lore.add(Component.text("- ⭐ Artifact").color(NamedTextColor.LIGHT_PURPLE));
                    break;
                case RARE:
                    lore.add(Component.text("- Blaze Rod, Ghast Tear, Magma Cream").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Gold Block, Wither Skeleton Skull").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Netherite Scrap").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Slimy Chunk, Ravager Scales, Battle Scrap").color(NamedTextColor.BLUE));
                    lore.add(Component.text("- ⭐ Artifact").color(NamedTextColor.LIGHT_PURPLE));
                    break;
                case EPIC:
                    lore.add(Component.text("- Netherite Scrap, Ancient Debris").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Wither Skeleton Skull, Blaze Rod").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Ghast Tear").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Ice Wing, Dread Soul, Battle Scrap").color(NamedTextColor.DARK_PURPLE));
                    lore.add(Component.text("- ⭐ Artifact").color(NamedTextColor.LIGHT_PURPLE));
                    break;
                case LEGENDARY:
                    lore.add(Component.text("- Netherite Ingot, Ancient Debris").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Netherite Upgrade Smithing Template").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Netherite Scrap, Wither Skeleton Skull").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Beast Scale, Dread Soul").color(NamedTextColor.GOLD));
                    lore.add(Component.text("- Enchanted Corrupted Flesh").color(NamedTextColor.GOLD));
                    lore.add(Component.text("- ⭐ Artifact").color(NamedTextColor.LIGHT_PURPLE));
                    break;
                case MYTHIC:
                    lore.add(Component.text("- Ancient Debris, Netherite Ingot").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Netherite Upgrade Smithing Template").color(NamedTextColor.GRAY));
                    lore.add(Component.text("- Wither Skeleton Skull, Beast Scale").color(NamedTextColor.GOLD));
                    lore.add(Component.text("- Enchanted Corrupted Flesh, Dread Soul").color(NamedTextColor.GOLD));
                    lore.add(Component.text("- ⭐ Artifact").color(NamedTextColor.LIGHT_PURPLE));
                    break;
            }
            return lore;
        }

        switch (rarity) {
            case COMMON:
                lore.add(Component.text("- Feather, Cod, Potato, Beetroot").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Dried Kelp, Apple, Wheat, Stick").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Salmon, Gunpowder, Copper Ingot").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Copper Nautilus Armor, Candle").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Corrupted Flesh, Residue, Wet Bone").color(NamedTextColor.GREEN));
                lore.add(Component.text("- ⭐ Artifact").color(NamedTextColor.LIGHT_PURPLE));
                break;
            case UNCOMMON:
                lore.add(Component.text("- Apple, Copper/Iron Ingot, Bread").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Melon, Gunpowder, Nautilus Shell").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Cod, Pufferfish, Salmon, Arrow").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Iron Nautilus Armor, Pottery Sherd").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Guardian Scale, Spider Silk, Battle Scrap").color(NamedTextColor.GREEN));
                lore.add(Component.text("- ⭐ Artifact").color(NamedTextColor.LIGHT_PURPLE));
                break;
            case RARE:
                lore.add(Component.text("- Golden Carrot, Gold Ingot, Arrow").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Redstone, Lapis Lazuli, Gunpowder").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Golden Nautilus Armor, Enchanted Book").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Tide Armor Trim, Ominous Bottle").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Slimy Chunk, Polar Fur, Ravager Scales").color(NamedTextColor.GREEN));
                lore.add(Component.text("- ⭐ Artifact").color(NamedTextColor.LIGHT_PURPLE));
                break;
            case EPIC:
                lore.add(Component.text("- Diamond, Emerald, Golden Apple").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Heart of the Sea, Sniffer Egg").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Diamond Nautilus Armor, Enchanted Book").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Ice Wing, Shark Tooth, Dread Soul").color(NamedTextColor.GREEN));
                lore.add(Component.text("- Ironclad Plate, Battle Scrap").color(NamedTextColor.GREEN));
                lore.add(Component.text("- ⭐ Artifact").color(NamedTextColor.LIGHT_PURPLE));
                break;
            case LEGENDARY:
                lore.add(Component.text("- Netherite Nautilus Armor, Trident").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Enchanted Golden Apple, Diamond").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Emerald Block, Gold Block, Netherite Scrap").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Enchanted Book, Golden Apple").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Beast Scale, Ironclad Plate, Ice Wing").color(NamedTextColor.GREEN));
                lore.add(Component.text("- ⭐ Artifact").color(NamedTextColor.LIGHT_PURPLE));
                break;
            case MYTHIC:
                lore.add(Component.text("- Nether Star, Netherite Ingot").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Enchanted Golden Apple, Diamond Block").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Netherite Upgrade Smithing Template").color(NamedTextColor.GRAY));
                lore.add(Component.text("- Enchanted Corrupted Flesh, Enchanted Wet Bone").color(NamedTextColor.GREEN));
                lore.add(Component.text("- Beast Scale, Dread Soul").color(NamedTextColor.GREEN));
                lore.add(Component.text("- ⭐ Artifact").color(NamedTextColor.LIGHT_PURPLE));
                break;
        }
        return lore;
    }
}

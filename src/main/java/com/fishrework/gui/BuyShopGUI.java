package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.model.Bait;
import com.fishrework.model.BiomeFishingProfile;
import com.fishrework.model.BiomeGroup;
import com.fishrework.model.CustomMob;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Rarity;
import com.fishrework.model.Skill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Buy shop GUI (double chest / 6 rows).
 * Rows 0-4: Bait items with buy prices
 * Row 5: Navigation + special utility bags
 */
public class BuyShopGUI extends BaseGUI {

    private static final String SHOP_BOOK_LURE_1_ID = "shop_book_lure_1";
    private static final String SHOP_BOOK_LUCK_1_ID = "shop_book_luck_of_the_sea_1";
    private static final String SHOP_BOOK_SEA_CREATURE_1_ID = "shop_book_sea_creature_chance_1";
    private static final String SHOP_TRIDENT_BASE_ID = "shop_trident";
    private static final String SHOP_TRIDENT_BEGINNER_1_ID = "beginner_trident_1";
    private static final String SHOP_TRIDENT_BEGINNER_2_ID = "beginner_trident_2";
    private static final String SHOP_QUICK_CROSSBOW_T1_ID = "quickcharge_repeater_1";
    private static final String SHOP_MULTI_CROSSBOW_T1_ID = "multishot_volley_1";

    private static final int MAGMA_SATCHEL_REQUIRED_LEVEL = 27;
    private static final int BAIT_SLOT_COUNT = 45;
    private static final int BACK_SLOT = 45;
    private static final int FISH_BAG_SLOT = 47;
    private static final int MAGMA_SATCHEL_SLOT = 49;
    private static final int BALANCE_SLOT = 53;

    private final Player player;
    private final List<BuyEntry> buyEntries = new ArrayList<>();
    private final Map<Integer, BuyEntry> specialEntriesBySlot = new HashMap<>();

    private record BuyEntry(String id, String displayName, ItemStack displayItem, double price,
                            boolean isBait) {}

    private record BiomeBaitDefinition(String key,
                                       String displayName,
                                       Material icon,
                                       List<BiomeGroup> biomeGroups) {}

    public BuyShopGUI(FishRework plugin, Player player) {
        super(plugin, 6, "Fishing Shop");
        this.player = player;
        buildBuyEntries();
        initializeItems();
    }

    private void buildBuyEntries() {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        int fishingLevel = data != null ? data.getLevel(Skill.FISHING) : 0;

        // Bring back only utility baits requested by design.
        addRegularBaitIfConfigured("treasure_bait");
        addRegularBaitIfConfigured("xp_bait");

        // Only biome baits are sold in this shop now.
        double defaultBiomeBaitPrice = plugin.getConfig().getDouble(
                "economy.bait_prices.creature_bait",
                50.0
        );
        for (BiomeBaitDefinition definition : getConfiguredBiomeBaits()) {
            List<String> allGroupHostileMobIds = getGroupHostileMobIds(definition.biomeGroups());
            List<String> unlockedMobIds = getUnlockedHostileMobIds(allGroupHostileMobIds, fishingLevel);
            if (unlockedMobIds.isEmpty()) {
                continue;
            }

            double price = plugin.getConfig().getDouble(
                "economy.biome_bait_prices." + definition.key(),
                defaultBiomeBaitPrice
            );
            if (price <= 0) continue;

            Rarity rarity = Rarity.UNCOMMON;

            ItemStack display = plugin.getItemManager().createBiomeBaitItem(
                definition.key(),
                definition.displayName(),
                definition.icon(),
                rarity,
                allGroupHostileMobIds,
                definition.biomeGroups()
            );

            buyEntries.add(new BuyEntry(
                "biome_bait:" + definition.key(),
                definition.displayName(),
                    display,
                    price,
                    true
            ));
        }

        // Special utility bags
        double fishBagPrice = plugin.getConfig().getDouble("economy.fish_bag_price", 0);
        if (fishBagPrice > 0) {
            ItemStack fishBag = plugin.getItemManager().getItem("fish_bag");
            if (fishBag != null) {
                buyEntries.add(new BuyEntry("fish_bag", "Fish Bag", fishBag, fishBagPrice, false));
            }
        }

        double lavaBagPrice = plugin.getConfig().getDouble("economy.lava_bag_price", 0);
        if (lavaBagPrice > 0 && fishingLevel >= MAGMA_SATCHEL_REQUIRED_LEVEL) {
            ItemStack lavaBag = plugin.getItemManager().getItem("lava_bag");
            if (lavaBag != null) {
                buyEntries.add(new BuyEntry("lava_bag", "Magma Satchel", lavaBag, lavaBagPrice, false));
            }
        }

        addShopEquipmentEntries();
    }

    private void addShopEquipmentEntries() {
        double lureBookPrice = plugin.getConfig().getDouble("economy.fishing_shop_prices.lure_1", 300.0);
        if (lureBookPrice > 0) {
            ItemStack lureBook = createShopEnchantedBook(Enchantment.LURE, 1);
            if (lureBook != null) {
                buyEntries.add(new BuyEntry(SHOP_BOOK_LURE_1_ID, "Lure I Book", lureBook, lureBookPrice, true));
            }
        }

        double luckBookPrice = plugin.getConfig().getDouble("economy.fishing_shop_prices.luck_of_the_sea_1", 400.0);
        if (luckBookPrice > 0) {
            ItemStack luckBook = createShopEnchantedBook(Enchantment.LUCK_OF_THE_SEA, 1);
            if (luckBook != null) {
                buyEntries.add(new BuyEntry(SHOP_BOOK_LUCK_1_ID, "Luck of the Sea I Book", luckBook, luckBookPrice, true));
            }
        }

        double seaCreatureBookPrice = plugin.getConfig().getDouble("economy.fishing_shop_prices.sea_creature_chance_1", 500.0);
        if (seaCreatureBookPrice > 0) {
            Enchantment seaCreature = org.bukkit.Registry.ENCHANTMENT.get(new NamespacedKey("fishrework", "sea_creature_chance"));
            ItemStack seaCreatureBook = createShopEnchantedBook(seaCreature, 1);
            if (seaCreatureBook != null) {
                buyEntries.add(new BuyEntry(SHOP_BOOK_SEA_CREATURE_1_ID, "Sea Creature Chance I Book", seaCreatureBook, seaCreatureBookPrice, true));
            }
        }

        double tridentPrice = plugin.getConfig().getDouble("economy.fishing_shop_prices.trident", 600.0);
        if (tridentPrice > 0) {
            ItemStack trident = new ItemStack(Material.TRIDENT);
            buyEntries.add(new BuyEntry(SHOP_TRIDENT_BASE_ID, "Trident", trident, tridentPrice, true));
        }

        double beginnerTrident1Price = plugin.getConfig().getDouble("economy.fishing_shop_prices.beginner_trident_1", 700.0);
        if (beginnerTrident1Price > 0) {
            addShopCustomItemIfConfigured(SHOP_TRIDENT_BEGINNER_1_ID, "Novice Trident", beginnerTrident1Price);
        }

        double beginnerTrident2Price = plugin.getConfig().getDouble("economy.fishing_shop_prices.beginner_trident_2", 1000.0);
        if (beginnerTrident2Price > 0) {
            addShopCustomItemIfConfigured(SHOP_TRIDENT_BEGINNER_2_ID, "Adept Trident", beginnerTrident2Price);
        }

        double quickCrossbowPrice = plugin.getConfig().getDouble("economy.fishing_shop_prices.quickcharge_repeater_1", 2000.0);
        if (quickCrossbowPrice > 0) {
            addShopCustomItemIfConfigured(SHOP_QUICK_CROSSBOW_T1_ID, "Quickcharge Repeater I", quickCrossbowPrice);
        }

        double multishotCrossbowPrice = plugin.getConfig().getDouble("economy.fishing_shop_prices.multishot_volley_1", 1800.0);
        if (multishotCrossbowPrice > 0) {
            addShopCustomItemIfConfigured(SHOP_MULTI_CROSSBOW_T1_ID, "Multishot Volley I", multishotCrossbowPrice);
        }
    }

    private void addShopCustomItemIfConfigured(String itemId, String fallbackName, double price) {
        ItemStack item = plugin.getItemManager().getItem(itemId);
        if (item == null || price <= 0) return;

        String displayName = fallbackName;
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            displayName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
        }

        buyEntries.add(new BuyEntry(itemId, displayName, item, price, true));
    }

    private ItemStack createShopEnchantedBook(Enchantment enchantment, int level) {
        if (enchantment == null) return null;

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        if (!(book.getItemMeta() instanceof EnchantmentStorageMeta meta)) {
            return null;
        }

        meta.addStoredEnchant(enchantment, level, true);
        book.setItemMeta(meta);
        return book;
    }

    private void addRegularBaitIfConfigured(String baitId) {
        Bait bait = plugin.getBaitRegistry().get(baitId);
        if (bait == null) return;

        double price = plugin.getConfig().getDouble("economy.bait_prices." + baitId, 0.0);
        if (price <= 0.0) return;

        ItemStack display = plugin.getItemManager().createBaitItem(bait);
        buyEntries.add(new BuyEntry(baitId, bait.getDisplayName(), display, price, true));
    }

    private void initializeItems() {
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);
        specialEntriesBySlot.clear();

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        double balance = data != null ? data.getBalance() : 0;
        String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");

        // Place bait items in rows 0-4 (slots 0-44)
        int baitSlot = 0;
        for (BuyEntry entry : buyEntries) {
            if (entry.isBait && baitSlot < BAIT_SLOT_COUNT) {
                ItemStack item = entry.displayItem.clone();
                ItemMeta meta = item.getItemMeta();

                // Add price lore
                List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Component.text("Price: " + com.fishrework.util.FormatUtil.format("%.0f", entry.price) + " " + currencyName)
                    .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(plugin.getLanguageManager().getMessage("buyshopgui.click_to_buy_1", "Click to buy 1").color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
                lore.add(plugin.getLanguageManager().getMessage("buyshopgui.shiftclick_to_buy_4", "Shift-click to buy 4").color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));

                meta.lore(lore);
                item.setItemMeta(meta);
                inventory.setItem(baitSlot, item);
                baitSlot++;
            }
        }

        // Place special items in row 5 with fixed slots to avoid overlap/mismatch.
        for (BuyEntry entry : buyEntries) {
            if (!entry.isBait) {
                int targetSlot = switch (entry.id) {
                    case "fish_bag" -> FISH_BAG_SLOT;
                    case "lava_bag" -> MAGMA_SATCHEL_SLOT;
                    default -> -1;
                };
                if (targetSlot < 0) continue;

                ItemStack item = entry.displayItem.clone();
                ItemMeta meta = item.getItemMeta();

                List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Component.text("Price: " + com.fishrework.util.FormatUtil.format("%.0f", entry.price) + " " + currencyName)
                        .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(plugin.getLanguageManager().getMessage("buyshopgui.click_to_buy", "Click to buy!").color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));

                meta.lore(lore);
                item.setItemMeta(meta);
                inventory.setItem(targetSlot, item);
                specialEntriesBySlot.put(targetSlot, entry);
            }
        }

            // Row 5: Navigation
            setBackButton(BACK_SLOT);

        // Balance display
        ItemStack balanceItem = new ItemStack(Material.SUNFLOWER);
        ItemMeta balMeta = balanceItem.getItemMeta();
        balMeta.displayName(Component.text("Balance: " + com.fishrework.util.FormatUtil.format("%.0f", balance) + " " + currencyName)
                .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        balanceItem.setItemMeta(balMeta);
            inventory.setItem(BALANCE_SLOT, balanceItem);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;

        int slot = event.getSlot();
        String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");

        // Back button
        if (slot == BACK_SLOT) {
            new ShopMenuGUI(plugin, player).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }

        // Find which buy entry was clicked
        BuyEntry clickedEntry = null;
        if (slot >= 0 && slot < BAIT_SLOT_COUNT) {
            // Bait slots
            int baitIndex = 0;
            for (BuyEntry entry : buyEntries) {
                if (entry.isBait) {
                    if (baitIndex == slot) {
                        clickedEntry = entry;
                        break;
                    }
                    baitIndex++;
                }
            }
        } else {
            clickedEntry = specialEntriesBySlot.get(slot);
        }

        if (clickedEntry == null) return;

        int bundles = (event.isShiftClick() && canBulkBuy(clickedEntry.id)) ? 4 : 1;
        double totalCost = clickedEntry.price * bundles;

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        if ("lava_bag".equalsIgnoreCase(clickedEntry.id)
            && data.getLevel(Skill.FISHING) < MAGMA_SATCHEL_REQUIRED_LEVEL) {
            player.sendMessage(Component.text("Magma Satchel unlocks at Fishing level " + MAGMA_SATCHEL_REQUIRED_LEVEL + ".")
                .color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1);
            return;
        }

        int outputQuantity = 1;

        if (data.getBalance() < totalCost) {
            player.sendMessage(Component.text("Not enough " + currencyName + "! Need "
                    + com.fishrework.util.FormatUtil.format("%.0f", totalCost) + " but have " + com.fishrework.util.FormatUtil.format("%.0f", data.getBalance()))
                    .color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1);
            return;
        }

        // Deduct balance
        data.deductBalance(totalCost);
        plugin.getDatabaseManager().saveBalance(player.getUniqueId(), data.getBalance());

        // Give item(s)
        int totalBaits = bundles * outputQuantity;
        ItemStack giveItem;
        if (clickedEntry.isBait) {
            giveItem = clickedEntry.displayItem.clone();
        } else {
            giveItem = plugin.getItemManager().getItem(clickedEntry.id);
            if (giveItem == null) return;
        }

        giveItem.setAmount(totalBaits);
        java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(giveItem);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }

        player.sendMessage(Component.text("Bought " + totalBaits + "x " + clickedEntry.displayName + " for "
                + com.fishrework.util.FormatUtil.format("%.0f", totalCost) + " " + currencyName
                + "!")
                .color(NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        initializeItems(); // Refresh balance display
    }

    private boolean canBulkBuy(String entryId) {
        return entryId != null
                && (entryId.startsWith("biome_bait:")
                || "treasure_bait".equals(entryId)
                || "xp_bait".equals(entryId));
    }

    private List<BiomeBaitDefinition> getConfiguredBiomeBaits() {
        List<BiomeBaitDefinition> out = new ArrayList<>();

        // Overworld groups (matching advancements).
        out.add(new BiomeBaitDefinition("wild_tropics", "Wild Tropics Bait", Material.TORCHFLOWER_SEEDS,
                List.of(BiomeGroup.SWAMP, BiomeGroup.JUNGLE, BiomeGroup.SAVANNA)));
        out.add(new BiomeBaitDefinition("frozen_peaks", "Frozen Peaks Bait", Material.MELON_SEEDS,
                List.of(BiomeGroup.MOUNTAINS, BiomeGroup.SNOWY, BiomeGroup.TAIGA)));
        out.add(new BiomeBaitDefinition("scorched_earth", "Scorched Earth Bait", Material.LEAF_LITTER,
                List.of(BiomeGroup.DESERT, BiomeGroup.BADLANDS)));
        out.add(new BiomeBaitDefinition("heartlands", "Heartlands Bait", Material.WHEAT_SEEDS,
                List.of(BiomeGroup.RIVER, BiomeGroup.PLAINS, BiomeGroup.FOREST, BiomeGroup.MEADOW)));
        out.add(new BiomeBaitDefinition("seven_seas", "Seven Seas Bait", Material.PINK_PETALS,
                List.of(BiomeGroup.COLD_OCEAN, BiomeGroup.FROZEN_OCEAN, BiomeGroup.NORMAL_OCEAN,
                        BiomeGroup.LUKEWARM_OCEAN, BiomeGroup.WARM_OCEAN, BiomeGroup.BEACH)));
        out.add(new BiomeBaitDefinition("hidden_realms", "Hidden Realms Bait", Material.FROGSPAWN,
                List.of(BiomeGroup.LUSH_CAVES, BiomeGroup.MUSHROOM, BiomeGroup.DEEP_DARK, BiomeGroup.PALE_GARDEN)));

        // Nether groups.
        out.add(new BiomeBaitDefinition("crimson_forest", "Crimson Forest Bait", Material.CRIMSON_ROOTS,
                List.of(BiomeGroup.CRIMSON_FOREST)));
        out.add(new BiomeBaitDefinition("warped_forest", "Warped Forest Bait", Material.PITCHER_PLANT,
                List.of(BiomeGroup.WARPED_FOREST)));
        out.add(new BiomeBaitDefinition("soul_sand_valley", "Soul Sand Valley Bait", Material.PITCHER_POD,
                List.of(BiomeGroup.SOUL_SAND_VALLEY)));
        out.add(new BiomeBaitDefinition("basalt_deltas", "Basalt Deltas Bait", Material.OPEN_EYEBLOSSOM,
                List.of(BiomeGroup.BASALT_DELTAS)));
        out.add(new BiomeBaitDefinition("nether_wastes", "Nether Wastes Bait", Material.WITHER_ROSE,
                List.of(BiomeGroup.NETHER_WASTES)));

        return out;
    }

    private List<String> getGroupHostileMobIds(List<BiomeGroup> groups) {
        Set<String> hostileMobIds = new HashSet<>();
        for (BiomeGroup group : groups) {
            BiomeFishingProfile profile = plugin.getBiomeFishingRegistry().get(group);
            if (profile == null) continue;
            hostileMobIds.addAll(profile.getHostileWeights().keySet());
        }

        List<String> all = new ArrayList<>(hostileMobIds);
        all.sort(Comparator.naturalOrder());
        return all;
    }

    private List<String> getUnlockedHostileMobIds(List<String> mobIds, int fishingLevel) {
        List<String> unlocked = new ArrayList<>();
        for (String mobId : mobIds) {
            CustomMob mob = plugin.getMobRegistry().get(mobId);
            if (mob == null || !mob.isHostile() || mob.getSkill() != Skill.FISHING) continue;
            if (fishingLevel >= mob.getRequiredLevel()) {
                unlocked.add(mobId);
            }
        }
        unlocked.sort(Comparator.naturalOrder());
        return unlocked;
    }

    private Rarity getHighestUnlockedRarity(List<String> mobIds) {
        Rarity best = Rarity.COMMON;
        for (String mobId : mobIds) {
            CustomMob mob = plugin.getMobRegistry().get(mobId);
            if (mob == null || mob.getRarity() == null) continue;
            if (mob.getRarity().ordinal() > best.ordinal()) {
                best = mob.getRarity();
            }
        }
        return best;
    }
}

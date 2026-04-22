package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.model.CustomMob;
import com.fishrework.model.Skill;
import com.fishrework.model.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Sell shop GUI (6 rows, paginated).
 * Shows all sellable items with prices. Click to sell all matching items from inventory.
 */
public class SellShopGUI extends BaseGUI {

    private final Player player;
    private int page = 0;

    // Pre-computed list of sellable items with their prices
    private final List<SellEntry> sellEntries = new ArrayList<>();
    private final Map<String, Set<String>> customUnlockMobIdsByDropId = new HashMap<>();
    private VendorFilter activeFilter = VendorFilter.ALL;

    private record SellEntry(String displayName, Material material, String customId, double price, EntryCategory category) {}
    private enum EntryCategory {
        VANILLA,
        SEA_CREATURE_DROPS
    }

    private enum VendorFilter {
        ALL("All", Material.HOPPER, NamedTextColor.WHITE),
        VANILLA("Vanilla", Material.COD, NamedTextColor.AQUA),
        SEA_CREATURE_DROPS("Sea Creature Drops", Material.PRISMARINE_SHARD, NamedTextColor.GOLD);

        private final String displayName;
        private final Material icon;
        private final NamedTextColor color;

        VendorFilter(String displayName, Material icon, NamedTextColor color) {
            this.displayName = displayName;
            this.icon = icon;
            this.color = color;
        }

        public String displayName() {
            return displayName;
        }

        public Material icon() {
            return icon;
        }

        public NamedTextColor color() {
            return color;
        }

        public VendorFilter next() {
            VendorFilter[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }
    private static final String POTTERY_SHERD_WILDCARD_ID = "__pottery_sherd_wildcard__";
    private static final int SELL_OTHER_SLOT = 47;
    private static final int MAX_OTHER_TOOLTIP_LINES = 8;

    private static final int ITEMS_PER_PAGE = 45; // Rows 0-4
    private long lastResultClick = 0;

    public SellShopGUI(FishRework plugin, Player player) {
        super(plugin, 6, localizedTitle(plugin, "sellshopgui.title", "Fish Vendor"));
        this.player = player;
        rebuildCustomUnlockMap();
        buildSellEntries();
        initializeItems();
    }

    private List<Material> getPotterySherdMaterials() {
        List<Material> sherds = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.name().endsWith("_POTTERY_SHERD")) {
                sherds.add(material);
            }
        }
        sherds.sort(Comparator.comparing(Enum::name));
        return sherds;
    }

    private void buildSellEntries() {
        PlayerData playerData = plugin.getPlayerData(player.getUniqueId());

        // Vanilla fish
        org.bukkit.configuration.ConfigurationSection vanillaSection =
                plugin.getConfig().getConfigurationSection("economy.sell_prices");
        if (vanillaSection == null) {
            vanillaSection = plugin.getConfig().getConfigurationSection("sell_prices");
        }
        if (vanillaSection != null) {
            Set<Material> addedVanillaMaterials = new HashSet<>();
            for (String key : vanillaSection.getKeys(false)) {
                double price = vanillaSection.getDouble(key);
                if (price <= 0) continue;

                if ("POTTERY_SHERD".equalsIgnoreCase(key)) {
                    List<Material> sherds = getPotterySherdMaterials();
                    if (!sherds.isEmpty()) {
                        Material representative = sherds.get(0);
                        if (addedVanillaMaterials.add(representative)) {
                            sellEntries.add(new SellEntry("Pottery Sherd", representative, POTTERY_SHERD_WILDCARD_ID, price, EntryCategory.VANILLA));
                        }
                    }
                    continue;
                }

                try {
                    Material mat = Material.valueOf(key.toUpperCase());
                    if (addedVanillaMaterials.add(mat)) {
                        String name = formatName(key);
                        sellEntries.add(new SellEntry(name, mat, null, price, EntryCategory.VANILLA));
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Custom materials
        org.bukkit.configuration.ConfigurationSection customSection =
                plugin.getConfig().getConfigurationSection("economy.custom_sell_prices");
        if (customSection == null) {
            customSection = plugin.getConfig().getConfigurationSection("custom_sell_prices");
        }
        if (customSection != null) {
            for (String key : customSection.getKeys(false)) {
                if (isEnchantedMaterialId(key)) {
                    continue; // Requested: remove enchanted material variants from vendor.
                }

                if (!isCustomItemVendorMaterialAllowed(key)) {
                    continue; // Keep vendor focused on material-like drops, not gear.
                }

                if (!isCustomEntryUnlockedInEncyclopedia(key, playerData)) {
                    continue;
                }

                double price = customSection.getDouble(key);
                if (price > 0) {
                    // Try to get the item from ItemManager for display
                    ItemStack sample = plugin.getItemManager().getItem(key);
                    Material mat = sample != null ? sample.getType() : Material.PAPER;
                    String name = sample != null && sample.hasItemMeta() && sample.getItemMeta().hasDisplayName()
                            ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                .serialize(sample.getItemMeta().displayName())
                            : formatName(key);
                    sellEntries.add(new SellEntry(name, mat, key, price, EntryCategory.SEA_CREATURE_DROPS));
                }
            }
        }

        if (sellEntries.isEmpty()) {
            addFallbackVanillaEntries();
        }

    }

    private void addFallbackVanillaEntries() {
        LinkedHashMap<Material, Double> defaults = new LinkedHashMap<>();
        defaults.put(Material.COD, 5.0);
        defaults.put(Material.SALMON, 7.0);
        defaults.put(Material.PUFFERFISH, 10.0);
        defaults.put(Material.TROPICAL_FISH, 12.0);

        for (Map.Entry<Material, Double> entry : defaults.entrySet()) {
            Material material = entry.getKey();
            double price = plugin.getConfig().getDouble(
                    "economy.sell_prices." + material.name(),
                    plugin.getConfig().getDouble("sell_prices." + material.name(), entry.getValue()));
            if (price <= 0) continue;
            sellEntries.add(new SellEntry(formatName(material.name()), material, null, price, EntryCategory.VANILLA));
        }
    }

    private String formatName(String id) {
        String[] parts = id.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void initializeItems() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        double balance = data != null ? data.getBalance() : 0;
        String currencyName = plugin.getLanguageManager().getCurrencyName();
        List<SellEntry> filteredEntries = getFilteredEntries();

        int totalPages = Math.max(1, (int) Math.ceil((double) filteredEntries.size() / ITEMS_PER_PAGE));
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, filteredEntries.size());

        for (int i = start; i < end; i++) {
            SellEntry entry = filteredEntries.get(i);
            int slot = i - start;

            ItemStack item = new ItemStack(entry.material);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(entry.displayName).color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));

            // Count how many the player has
            int count = countInInventory(player, entry.material, entry.customId);
            double buyPrice = entry.price * 5.0;

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text(plugin.getLanguageManager().getString(
                            "sellshopgui.sell_price_each",
                            "Sell Price: %price% %currency% each",
                            "price", com.fishrework.util.FormatUtil.format("%.0f", entry.price),
                            "currency", currencyName))
                    .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(plugin.getLanguageManager().getString(
                            "sellshopgui.buy_price_each",
                            "Buy Price: %price% %currency% each",
                            "price", com.fishrework.util.FormatUtil.format("%.0f", buyPrice),
                            "currency", currencyName))
                    .color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            if (POTTERY_SHERD_WILDCARD_ID.equals(entry.customId)) {
                lore.add(plugin.getLanguageManager().getMessage("sellshopgui.counts_all_pottery_sherd_variants", "Counts all pottery sherd variants")
                        .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text(plugin.getLanguageManager().getString(
                            "sellshopgui.you_have_count",
                            "You have: %count%",
                            "count", String.valueOf(count))).color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            if (count > 0) {
                lore.add(Component.text(plugin.getLanguageManager().getString(
                                "sellshopgui.total_value",
                                "Total: %value% %currency%",
                                "value", com.fishrework.util.FormatUtil.format("%.0f", entry.price * count),
                                "currency", currencyName))
                        .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(plugin.getLanguageManager().getMessage("sellshopgui.leftclick_sell_all", "Left-Click: Sell all").color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
                lore.add(plugin.getLanguageManager().getMessage("sellshopgui.rightclick_buy_1", "Right-Click: Buy 1").color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.empty());
                lore.add(plugin.getLanguageManager().getMessage("sellshopgui.none_in_inventory", "None in inventory").color(NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(plugin.getLanguageManager().getMessage("sellshopgui.rightclick_buy_1", "Right-Click: Buy 1").color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
        }

        // Row 5: Navigation
        ItemStack navFill = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta nfMeta = navFill.getItemMeta();
        nfMeta.displayName(Component.text(" "));
        navFill.setItemMeta(nfMeta);
        for (int i = 45; i <= 53; i++) inventory.setItem(i, navFill);

        // Back button
        setBackButton(45);

        // Pagination
        setPaginationControls(48, 50, page, totalPages);

        // Page info
        inventory.setItem(49, createPageInfo(page, totalPages));

        // Sell All Fish button
        ItemStack sellAll = new ItemStack(Material.GOLD_INGOT);
        ItemMeta saMeta = sellAll.getItemMeta();
        saMeta.displayName(plugin.getLanguageManager().getMessage("sellshopgui.sell_all_fish", "Sell All Fish").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        double totalValue = calculateTotalSellValue(player);
        saMeta.lore(List.of(
                Component.empty(),
                Component.text(plugin.getLanguageManager().getString(
                        "sellshopgui.total_value_long",
                        "Total value: %value% %currency%",
                        "value", com.fishrework.util.FormatUtil.format("%.0f", totalValue),
                        "currency", currencyName))
                        .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                plugin.getLanguageManager().getMessage("sellshopgui.click_to_sell_everything", "Click to sell everything!").color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        sellAll.setItemMeta(saMeta);
        inventory.setItem(51, sellAll);

        // Dedicated Sell Other button (separate from Sell All to avoid accidental junk sales)
        ItemStack sellOther = new ItemStack(Material.DRIED_KELP);
        ItemMeta soMeta = sellOther.getItemMeta();
        soMeta.displayName(plugin.getLanguageManager().getMessage("sellshopgui.sell_other", "Sell Other").color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));

        List<ItemStack> otherItems = getOtherVendorItems(player);
        int otherCount = 0;
        double otherTotal = 0.0;
        for (ItemStack stack : otherItems) {
            otherCount += stack.getAmount();
            otherTotal += stack.getAmount() * plugin.getConfig().getDouble("economy.other_vendor_price", 1.0);
        }

        List<Component> sellOtherLore = new ArrayList<>();
        sellOtherLore.add(Component.empty());
        sellOtherLore.add(plugin.getLanguageManager().getMessage("sellshopgui.sells_only_other_vendor_items", "Sells only 'other' vendor items").color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        sellOtherLore.add(Component.text(plugin.getLanguageManager().getString(
                        "sellshopgui.other_price_each",
                        "Price: 1 %currency% each",
                        "currency", currencyName)).color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));
        sellOtherLore.add(Component.text(plugin.getLanguageManager().getString(
                        "sellshopgui.other_found_count",
                        "Found: %count%",
                        "count", String.valueOf(otherCount))).color(NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        if (otherCount > 0) {
            sellOtherLore.add(Component.text(plugin.getLanguageManager().getString(
                            "sellshopgui.total_value",
                            "Total: %value% %currency%",
                            "value", com.fishrework.util.FormatUtil.format("%.0f", otherTotal),
                            "currency", currencyName))
                .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        }
        sellOtherLore.add(Component.empty());

        if (otherItems.isEmpty()) {
            sellOtherLore.add(plugin.getLanguageManager().getMessage("sellshopgui.no_other_items_in_inventory", "No other items in inventory").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        } else {
            Map<String, Integer> grouped = new LinkedHashMap<>();
            for (ItemStack stack : otherItems) {
            String name = getDisplayName(stack);
            grouped.put(name, grouped.getOrDefault(name, 0) + stack.getAmount());
            }

            int shown = 0;
            for (Map.Entry<String, Integer> e : grouped.entrySet()) {
            if (shown >= MAX_OTHER_TOOLTIP_LINES) break;
            sellOtherLore.add(Component.text("- " + e.getKey() + " x" + e.getValue())
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            shown++;
            }
            int hidden = grouped.size() - shown;
            if (hidden > 0) {
            sellOtherLore.add(Component.text(plugin.getLanguageManager().getString(
                            "sellshopgui.and_more",
                            "... and %count% more",
                            "count", String.valueOf(hidden))).color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
            }
        }

        sellOtherLore.add(Component.empty());
        sellOtherLore.add(plugin.getLanguageManager().getMessage("sellshopgui.click_to_open_confirmation", "Click to open confirmation").color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        soMeta.lore(sellOtherLore);
        sellOther.setItemMeta(soMeta);
        inventory.setItem(SELL_OTHER_SLOT, sellOther);

        // Filter toggle
        ItemStack filterItem = new ItemStack(activeFilter.icon());
        ItemMeta filterMeta = filterItem.getItemMeta();
        filterMeta.displayName(Component.text(plugin.getLanguageManager().getString("sellshopgui.filter_prefix", "Filter: ") + activeFilter.displayName())
            .color(activeFilter.color())
            .decoration(TextDecoration.ITALIC, false));
        filterMeta.lore(List.of(
            plugin.getLanguageManager().getMessage("sellshopgui.click_to_cycle_filters", "Click to cycle filters").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ));
        filterItem.setItemMeta(filterMeta);
        inventory.setItem(52, filterItem);

        // Balance display
        ItemStack balanceItem = new ItemStack(Material.SUNFLOWER);
        ItemMeta balMeta = balanceItem.getItemMeta();
        balMeta.displayName(Component.text(plugin.getLanguageManager().getString(
                        "sellshopgui.balance_prefix",
                        "Balance: %balance% %currency%",
                        "balance", com.fishrework.util.FormatUtil.format("%.0f", balance),
                        "currency", currencyName))
                .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        balanceItem.setItemMeta(balMeta);
        inventory.setItem(53, balanceItem);
    }

    private int countInInventory(Player player, Material material, String customId) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (POTTERY_SHERD_WILDCARD_ID.equals(customId)) {
                if (item.getType().name().endsWith("_POTTERY_SHERD")) {
                    count += item.getAmount();
                }
            } else if (customId != null) {
                if (plugin.getItemManager().isCustomItem(item, customId)) {
                    count += item.getAmount();
                }
            } else {
                if (item.getType() == material && !item.hasItemMeta()) {
                    count += item.getAmount();
                } else if (item.getType() == material && item.hasItemMeta()) {
                    // Vanilla fish can have meta from enchanted rods, etc.
                    // Only count if it's NOT a custom item
                    String stored = item.getItemMeta().getPersistentDataContainer()
                            .get(plugin.getItemManager().CUSTOM_ITEM_KEY,
                                    org.bukkit.persistence.PersistentDataType.STRING);
                    if (stored == null) {
                        count += item.getAmount();
                    }
                }
            }
        }
        return count;
    }

    private double calculateTotalSellValue(Player player) {
        double total = 0;
        PlayerData playerData = plugin.getPlayerData(player.getUniqueId());

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;

            String customId = getCustomId(item);
            if (customId != null) {
                if (isEnchantedMaterialId(customId)) continue;
                if (!isCustomItemVendorMaterialAllowed(customId)) continue;
                if (!isCustomEntryUnlockedInEncyclopedia(customId, playerData)) continue;
            }

            if (plugin.getItemManager().isOtherVendorSellMaterial(item)) {
                continue;
            }

            double price = plugin.getItemManager().getSellPrice(item);
            if (price > 0) {
                total += price * item.getAmount();
            }
        }
        return total;
    }

    private List<ItemStack> getOtherVendorItems(Player player) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (!plugin.getItemManager().isOtherVendorSellMaterial(item)) continue;
            out.add(item);
        }
        return out;
    }

    private String getDisplayName(ItemStack item) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
        }
        return formatName(item.getType().name());
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        // Only handle clicks in this GUI's top inventory; ignore player inventory slots.
        if (event.getClickedInventory() != inventory) return;
        if (event.getCurrentItem() == null) return;

        int slot = event.getSlot();
        String currencyName = plugin.getLanguageManager().getCurrencyName();

        // Navigation
        if (slot == 45) {
            new ShopMenuGUI(plugin, player).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }
        if (slot == 48 && page > 0) {
            page--;
            initializeItems();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }
        if (slot == SELL_OTHER_SLOT) {
            new SellOtherConfirmGUI(plugin, player).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }
        if (slot == 50) {
            int totalPages = Math.max(1, (int) Math.ceil((double) getFilteredEntries().size() / ITEMS_PER_PAGE));
            if (page < totalPages - 1) {
                page++;
                initializeItems();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            }
            return;
        }

        // Filter toggle
        if (slot == 52) {
            activeFilter = activeFilter.next();
            page = 0;
            initializeItems();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }

        // Sell All button
        if (slot == 51) {
            sellAllFromInventory(player);
            return;
        }

        // Sell specific item
        if (slot < 45) {
            List<SellEntry> filteredEntries = getFilteredEntries();
            int index = page * ITEMS_PER_PAGE + slot;
            if (index < filteredEntries.size()) {
                SellEntry entry = filteredEntries.get(index);

                if (event.isRightClick()) {
                    buyOneItem(player, entry);
                    return;
                }

                int sold = removeFromInventory(player, entry.material, entry.customId);
                if (sold > 0) {
                    double earnings = sold * entry.price;
                    PlayerData data = plugin.getPlayerData(player.getUniqueId());
                    if (data != null) {
                        data.addBalance(earnings);
                        data.getSession().addDoubloonsEarned(earnings);
                        plugin.getDatabaseManager().saveBalance(player.getUniqueId(), data.getBalance());
                    }
                    player.sendMessage(plugin.getLanguageManager().getMessage(
                                    "sellshopgui.sold_entry",
                                    "Sold %count%x %item% for %value% %currency%!",
                                    "count", String.valueOf(sold),
                                    "item", entry.displayName,
                                    "value", com.fishrework.util.FormatUtil.format("%.0f", earnings),
                                    "currency", currencyName)
                            .color(NamedTextColor.GREEN));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                    initializeItems(); // Refresh
                } else {
                    player.sendMessage(plugin.getLanguageManager().getMessage(
                                    "sellshopgui.no_item_to_sell",
                                    "You don't have any %item% to sell!",
                                    "item", entry.displayName)
                            .color(NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1);
                }
            }
        }
    }

    private List<SellEntry> getFilteredEntries() {
        if (activeFilter == VendorFilter.ALL) {
            return sellEntries;
        }

        EntryCategory requiredCategory;
        switch (activeFilter) {
            case VANILLA -> requiredCategory = EntryCategory.VANILLA;
            case SEA_CREATURE_DROPS -> requiredCategory = EntryCategory.SEA_CREATURE_DROPS;
            default -> {
                return sellEntries;
            }
        }

        List<SellEntry> filtered = new ArrayList<>();
        for (SellEntry entry : sellEntries) {
            if (entry.category == requiredCategory) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private void buyOneItem(Player player, SellEntry entry) {
        long now = System.currentTimeMillis();
        if (now - lastResultClick < 150) return;
        lastResultClick = now;

        String currencyName = plugin.getLanguageManager().getCurrencyName();
        double buyPrice = entry.price * 5.0;
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        if (data.getBalance() < buyPrice) {
            player.sendMessage(plugin.getLanguageManager().getMessage(
                            "sellshopgui.need_currency_to_buy",
                            "You need %value% %currency% to buy %item%!",
                            "value", com.fishrework.util.FormatUtil.format("%.0f", buyPrice),
                            "currency", currencyName,
                            "item", entry.displayName).color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1);
            return;
        }

        ItemStack bought;
        if (entry.customId != null) {
            ItemStack custom = plugin.getItemManager().getItem(entry.customId);
            if (custom == null) {
                player.sendMessage(plugin.getLanguageManager().getMessage("sellshopgui.this_item_cannot_be_bought", "This item cannot be bought right now.")
                        .color(NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1);
                return;
            }
            bought = custom.clone();
            bought.setAmount(1);
        } else {
            bought = new ItemStack(entry.material, 1);
        }

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(bought);
        if (!overflow.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("sellshopgui.you_dont_have_enough_inventory", "You don't have enough inventory space to buy this item!")
                    .color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1);
            return;
        }

        // Deduct balance safely — if this returns false the player somehow went broke between the check
        // and here (e.g. rapid double-click), so roll back the item and abort.
        if (!data.deductBalance(buyPrice)) {
            player.getInventory().removeItem(bought);
            player.sendMessage(plugin.getLanguageManager().getMessage(
                            "sellshopgui.not_enough_currency",
                            "You don't have enough %currency% to buy this!",
                            "currency", currencyName)
                    .color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1);
            return;
        }
        plugin.getDatabaseManager().saveBalance(player.getUniqueId(), data.getBalance());

        player.sendMessage(plugin.getLanguageManager().getMessage(
                        "sellshopgui.bought_entry",
                        "Bought 1x %item% for %value% %currency%!",
                        "item", entry.displayName,
                        "value", com.fishrework.util.FormatUtil.format("%.0f", buyPrice),
                        "currency", currencyName)
                .color(NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.15f);
        initializeItems();
    }

    private int removeFromInventory(Player player, Material material, String customId) {
        int removed = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;

            boolean matches;
            if (POTTERY_SHERD_WILDCARD_ID.equals(customId)) {
                matches = item.getType().name().endsWith("_POTTERY_SHERD");
            } else if (customId != null) {
                matches = plugin.getItemManager().isCustomItem(item, customId);
            } else {
                if (item.getType() != material) continue;
                // Only match vanilla items (not custom items)
                String stored = item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer()
                        .get(plugin.getItemManager().CUSTOM_ITEM_KEY,
                                org.bukkit.persistence.PersistentDataType.STRING) : null;
                matches = stored == null;
            }

            if (matches) {
                removed += item.getAmount();
                player.getInventory().setItem(i, null);
            }
        }
        return removed;
    }

    private void sellAllFromInventory(Player player) {
        String currencyName = plugin.getLanguageManager().getCurrencyName();
        double totalEarnings = 0;
        int totalItems = 0;
        PlayerData data = plugin.getPlayerData(player.getUniqueId());

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;

            String customId = getCustomId(item);
            if (customId != null) {
                if (isEnchantedMaterialId(customId)) continue;
                if (!isCustomItemVendorMaterialAllowed(customId)) continue;
                if (!isCustomEntryUnlockedInEncyclopedia(customId, data)) continue;
            }

            if (plugin.getItemManager().isOtherVendorSellMaterial(item)) {
                continue;
            }

            double price = plugin.getItemManager().getSellPrice(item);
            if (price > 0) {
                totalEarnings += price * item.getAmount();
                totalItems += item.getAmount();
                player.getInventory().setItem(i, null);
            }
        }

        if (totalItems > 0) {
            if (data != null) {
                data.addBalance(totalEarnings);
                data.getSession().addDoubloonsEarned(totalEarnings);
                plugin.getDatabaseManager().saveBalance(player.getUniqueId(), data.getBalance());
            }
            player.sendMessage(Component.text(plugin.getLanguageManager().getString(
                            "sellshopgui.sold_items_summary",
                            "Sold %count% items for %value% %currency%!",
                            "count", String.valueOf(totalItems),
                            "value", com.fishrework.util.FormatUtil.format("%.0f", totalEarnings),
                            "currency", currencyName))
                    .color(NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.2f);
            initializeItems();
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("sellshopgui.you_dont_have_anything_to", "You don't have anything to sell!")
                    .color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1);
        }
    }

    private void rebuildCustomUnlockMap() {
        customUnlockMobIdsByDropId.clear();

        for (CustomMob mob : plugin.getMobRegistry().getBySkill(Skill.FISHING)) {
            for (com.fishrework.model.MobDrop drop : mob.getDrops()) {
                ItemStack sample = drop.getSampleItem();
                String customId = getCustomId(sample);
                if (customId == null || customId.isBlank()) continue;
                customUnlockMobIdsByDropId
                        .computeIfAbsent(customId, ignored -> new HashSet<>())
                        .add(mob.getId());
            }
        }
    }

    private boolean isCustomEntryUnlockedInEncyclopedia(String customId, PlayerData playerData) {
        Set<String> requiredMobIds = customUnlockMobIdsByDropId.get(customId);
        if (requiredMobIds == null || requiredMobIds.isEmpty()) {
            return true; // Non-mob custom entries remain available.
        }

        if (playerData == null) return false;
        for (String mobId : requiredMobIds) {
            if (playerData.hasCaughtMob(mobId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEnchantedMaterialId(String customId) {
        return customId != null && customId.startsWith("enchanted_");
    }

    private boolean isCustomItemVendorMaterialAllowed(String customId) {
        if (customId == null || customId.isBlank()) return false;

        // Always allow direct mob drops so every encyclopedia-unlocked drop can be sold.
        if (customUnlockMobIdsByDropId.containsKey(customId)) {
            return true;
        }

        ItemStack sample = plugin.getItemManager().getItem(customId);
        if (sample == null) return true;
        return !isGearLikeMaterial(sample.getType());
    }

    private boolean isGearLikeMaterial(Material material) {
        if (material == null) return false;
        String name = material.name();

        if (name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")) {
            return true;
        }

        return material == Material.TRIDENT
                || material == Material.BOW
                || material == Material.CROSSBOW
                || material == Material.SHIELD
                || material == Material.MACE
                || material == Material.FISHING_ROD;
    }

    private String getCustomId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(
                plugin.getItemManager().CUSTOM_ITEM_KEY,
                org.bukkit.persistence.PersistentDataType.STRING
        );
    }
}

package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.model.PlayerData;
import com.fishrework.util.FormatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConfigVendorGUI extends BaseGUI {

    private final Player player;
    private final String menuId;
    private final int backSlot;
    private final int balanceSlot;
    private final Map<Integer, VendorEntry> entriesBySlot = new HashMap<>();

    private enum RewardType {
        INFO,
        VANILLA,
        FISHREWORK_ITEM,
        COMMAND
    }

    private record VendorEntry(
            String id,
            RewardType type,
            int slot,
            double price,
            int amount,
            String itemId,
            Material material,
            List<String> commands,
            String permission,
            ItemStack displayItem,
            String displayName
    ) {}

    public ConfigVendorGUI(FishRework plugin, Player player, String menuId) {
        super(plugin, getRows(plugin, menuId), plugin.getConfig().getString("vendors.menus." + menuId + ".title", menuId));
        this.player = player;
        this.menuId = menuId;
        this.backSlot = inventory.getSize() - 9;
        this.balanceSlot = inventory.getSize() - 1;
        initializeItems();
    }

    private static int getRows(FishRework plugin, String menuId) {
        int rows = plugin.getConfig().getInt("vendors.menus." + menuId + ".rows", 3);
        return Math.max(1, Math.min(6, rows));
    }

    private void initializeItems() {
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);
        entriesBySlot.clear();

        for (VendorEntry entry : loadEntries()) {
            if (entry.slot() < 0 || entry.slot() >= inventory.getSize()) continue;
            if (entry.slot() == backSlot || entry.slot() == balanceSlot) continue;
            inventory.setItem(entry.slot(), createDisplayItem(entry));
            entriesBySlot.put(entry.slot(), entry);
        }

        setBackButton(backSlot);
        setBalanceDisplay(balanceSlot);
    }

    private List<VendorEntry> loadEntries() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("vendors.menus." + menuId + ".entries");
        if (section == null) return List.of();

        List<VendorEntry> entries = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            String path = "vendors.menus." + menuId + ".entries." + id;
            if (!plugin.getConfig().getBoolean(path + ".enabled", true)) continue;

            RewardType type = parseRewardType(plugin.getConfig().getString(path + ".type", "VANILLA"));
            int amount = Math.max(1, plugin.getConfig().getInt(path + ".amount", 1));
            double price = plugin.getConfig().getDouble(path + ".price", 0.0);
            if (price < 0.0) continue;

            String permission = plugin.getConfig().getString(path + ".permission", "");
            if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) continue;

            ItemStack displayItem = createBaseDisplayItem(path, type, amount);
            if (displayItem == null) continue;

            String displayName = getConfiguredDisplayName(path, displayItem, id);
            entries.add(new VendorEntry(
                    id,
                    type,
                    plugin.getConfig().getInt(path + ".slot", -1),
                    price,
                    amount,
                    plugin.getConfig().getString(path + ".item", ""),
                    parseMaterial(plugin.getConfig().getString(path + ".material", ""), Material.STONE),
                    plugin.getConfig().getStringList(path + ".commands"),
                    permission,
                    displayItem,
                    displayName
            ));
        }
        return entries;
    }

    private ItemStack createBaseDisplayItem(String path, RewardType type, int amount) {
        ItemStack item;
        switch (type) {
            case FISHREWORK_ITEM -> {
                item = plugin.getItemManager().getItem(plugin.getConfig().getString(path + ".item", ""));
                if (item == null) return null;
            }
            case INFO -> item = new ItemStack(parseMaterial(plugin.getConfig().getString(path + ".icon", "BOOK"), Material.BOOK));
            case COMMAND -> item = new ItemStack(parseMaterial(plugin.getConfig().getString(path + ".icon", "COMMAND_BLOCK"), Material.COMMAND_BLOCK));
            case VANILLA -> {
                Material material = parseMaterial(plugin.getConfig().getString(path + ".material", ""), null);
                if (material == null) return null;
                item = new ItemStack(material);
            }
            default -> item = null;
        }
        if (item == null || item.getType() == Material.AIR) return null;
        item.setAmount(Math.min(amount, Math.max(1, item.getMaxStackSize())));
        return item;
    }

    private ItemStack createDisplayItem(VendorEntry entry) {
        ItemStack item = entry.displayItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String configuredName = plugin.getConfig().getString(entryPath(entry) + ".name", "");
        if (configuredName != null && !configuredName.isBlank()) {
            meta.displayName(Component.text(configuredName).color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }

        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        List<String> configuredLore = plugin.getConfig().getStringList(entryPath(entry) + ".lore");
        if (!configuredLore.isEmpty()) {
            lore.add(Component.empty());
            for (String line : configuredLore) {
                lore.add(Component.text(line).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }

        if (entry.type() != RewardType.INFO) {
            String currencyName = plugin.getLanguageManager().getCurrencyName();
            lore.add(Component.empty());
            lore.add(Component.text(plugin.getLanguageManager().getString(
                            "configvendorgui.price",
                            "Price: %price% %currency%",
                            "price", FormatUtil.format("%.0f", entry.price()),
                            "currency", currencyName))
                    .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(plugin.getLanguageManager().getString(
                            "configvendorgui.amount",
                            "Amount: %amount%",
                            "amount", String.valueOf(entry.amount())))
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(plugin.getLanguageManager().getMessage("configvendorgui.click_to_buy", "Click to buy").color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String entryPath(VendorEntry entry) {
        return "vendors.menus." + menuId + ".entries." + entry.id();
    }

    private String getConfiguredDisplayName(String path, ItemStack item, String fallback) {
        String configured = plugin.getConfig().getString(path + ".name", "");
        if (configured != null && !configured.isBlank()) return configured;
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        }
        return fallback;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;

        if (event.getSlot() == backSlot) {
            new VendorListGUI(plugin, player).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }

        VendorEntry entry = entriesBySlot.get(event.getSlot());
        if (entry == null) return;
        if (entry.type() == RewardType.INFO) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            return;
        }
        purchase(entry);
    }

    private void purchase(VendorEntry entry) {
        if (entry.permission() != null && !entry.permission().isBlank() && !player.hasPermission(entry.permission())) {
            player.sendMessage(plugin.getLanguageManager().getMessage("configvendorgui.no_permission", "You do not have permission to buy this.").color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        String currencyName = plugin.getLanguageManager().getCurrencyName();
        if (data.getBalance() < entry.price()) {
            player.sendMessage(plugin.getLanguageManager().getMessage(
                            "configvendorgui.not_enough_currency",
                            "Not enough %currency%! Need %required% but have %current%.",
                            "currency", currencyName,
                            "required", FormatUtil.format("%.0f", entry.price()),
                            "current", FormatUtil.format("%.0f", data.getBalance()))
                    .color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }

        if (entry.type() == RewardType.COMMAND && entry.commands().isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("configvendorgui.no_commands_configured", "This vendor item has no commands configured.").color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }

        ItemStack reward = null;
        if (entry.type() != RewardType.COMMAND) {
            reward = createRewardItem(entry);
            if (reward == null) {
                player.sendMessage(plugin.getLanguageManager().getMessage("configvendorgui.reward_unavailable", "This vendor reward is not available.").color(NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                return;
            }
        }

        data.deductBalance(entry.price());
        plugin.getDatabaseManager().saveBalance(player.getUniqueId(), data.getBalance());

        if (entry.type() == RewardType.COMMAND) {
            dispatchCommands(entry);
        } else {
            giveItem(reward, entry.amount());
        }

        player.sendMessage(plugin.getLanguageManager().getMessage(
                        "configvendorgui.purchase_complete",
                        "Bought %amount%x %item% for %price% %currency%!",
                        "amount", String.valueOf(entry.amount()),
                        "item", entry.displayName(),
                        "price", FormatUtil.format("%.0f", entry.price()),
                        "currency", currencyName)
                .color(NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        initializeItems();
    }

    private ItemStack createRewardItem(VendorEntry entry) {
        if (entry.type() == RewardType.FISHREWORK_ITEM) {
            return plugin.getItemManager().getItem(entry.itemId());
        }
        if (entry.type() == RewardType.VANILLA) {
            return new ItemStack(entry.material());
        }
        return null;
    }

    private void giveItem(ItemStack reward, int amount) {
        int remaining = amount;
        int maxStack = Math.max(1, reward.getMaxStackSize());
        while (remaining > 0) {
            ItemStack stack = reward.clone();
            stack.setAmount(Math.min(maxStack, remaining));
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            remaining -= stack.getAmount();
        }
    }

    private void dispatchCommands(VendorEntry entry) {
        for (String rawCommand : entry.commands()) {
            if (rawCommand == null || rawCommand.isBlank()) continue;
            String command = rawCommand.trim();
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            command = command
                    .replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString())
                    .replace("%amount%", String.valueOf(entry.amount()))
                    .replace("%price%", FormatUtil.format("%.0f", entry.price()));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private RewardType parseRewardType(String raw) {
        if (raw == null || raw.isBlank()) return RewardType.VANILLA;
        try {
            return RewardType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return RewardType.VANILLA;
        }
    }

    private Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private void setBalanceDisplay(int slot) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        double balance = data != null ? data.getBalance() : 0;
        String currencyName = plugin.getLanguageManager().getCurrencyName();

        ItemStack balanceItem = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = balanceItem.getItemMeta();
        meta.displayName(Component.text(plugin.getLanguageManager().getString(
                        "configvendorgui.balance_prefix",
                        "Balance: %balance% %currency%",
                        "balance", FormatUtil.format("%.0f", balance),
                        "currency", currencyName))
                .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        balanceItem.setItemMeta(meta);
        inventory.setItem(slot, balanceItem);
    }
}

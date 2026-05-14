package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.economy.EconomyResult;
import com.fishrework.model.PlayerData;
import com.fishrework.util.InventoryTransactionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SellAllConfirmGUI extends BaseGUI {

    private final Player player;

    private static final int CONFIRM_SLOT = 11;
    private static final int CANCEL_SLOT  = 15;
    private static final int MAX_BREAKDOWN_LINES = 8;

    public SellAllConfirmGUI(FishRework plugin, Player player) {
        super(plugin, 3, localizedTitle(plugin, "sellallconfirmgui.title", "Sell All – Confirm?"));
        this.player = player;
        initializeItems();
    }

    private void initializeItems() {
        for (int i = 0; i < 27; i++) inventory.setItem(i, null);

        String currencyName = plugin.getLanguageManager().getCurrencyName();
        double totalValue   = calculateTotalSellValue();

        // Fill row with glass panes
        ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = fill.getItemMeta();
        fm.displayName(Component.text(" "));
        fill.setItemMeta(fm);
        for (int i = 0; i < 27; i++) inventory.setItem(i, fill);

        // Confirm button – lime dye
        ItemStack confirm = new ItemStack(Material.LIME_DYE);
        ItemMeta cm = confirm.getItemMeta();
        cm.displayName(plugin.getLanguageManager()
                .getMessage("sellallconfirmgui.confirm", "Confirm – Sell All")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        cm.lore(buildConfirmLore(currencyName, totalValue));
        confirm.setItemMeta(cm);
        inventory.setItem(CONFIRM_SLOT, confirm);

        // Cancel button – red dye
        ItemStack cancel = new ItemStack(Material.RED_DYE);
        ItemMeta xm = cancel.getItemMeta();
        xm.displayName(plugin.getLanguageManager()
                .getMessage("sellallconfirmgui.cancel", "Cancel")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        xm.lore(List.of(
                Component.empty(),
                plugin.getLanguageManager()
                        .getMessage("sellallconfirmgui.click_to_cancel", "Click to go back")
                        .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        cancel.setItemMeta(xm);
        inventory.setItem(CANCEL_SLOT, cancel);
    }

    private double calculateTotalSellValue() {
        double total = 0;
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (plugin.getItemManager().isOtherVendorSellMaterial(item)) continue;
            double price = plugin.getItemManager().getSellPrice(item);
            if (price > 0) total += price * item.getAmount();
        }
        return total;
    }

    private Map<String, Integer> getSellableItemsGrouped() {
        Map<String, Integer> grouped = new LinkedHashMap<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (plugin.getItemManager().isOtherVendorSellMaterial(item)) continue;
            double price = plugin.getItemManager().getSellPrice(item);
            if (price <= 0) continue;
            grouped.merge(getItemDisplayName(item), item.getAmount(), Integer::sum);
        }
        return grouped;
    }

    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
        }
        String[] parts = item.getType().name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private List<Component> buildConfirmLore(String currencyName, double totalValue) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text(plugin.getLanguageManager().getString(
                        "sellshopgui.total_value_long",
                        "Total value: %value% %currency%",
                        "value", com.fishrework.util.FormatUtil.format("%.0f", totalValue),
                        "currency", currencyName))
                .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        Map<String, Integer> grouped = getSellableItemsGrouped();
        if (grouped.isEmpty()) {
            lore.add(plugin.getLanguageManager().getMessage("sellshopgui.you_dont_have_anything_to", "You don't have anything to sell!")
                    .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        } else {
            int shown = 0;
            for (Map.Entry<String, Integer> e : grouped.entrySet()) {
                if (shown >= MAX_BREAKDOWN_LINES) break;
                lore.add(Component.text("- " + e.getKey() + " x" + e.getValue())
                        .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                shown++;
            }
            int hidden = grouped.size() - shown;
            if (hidden > 0) {
                lore.add(Component.text(plugin.getLanguageManager().getString(
                                "sellshopgui.and_more",
                                "... and %count% more",
                                "count", String.valueOf(hidden)))
                        .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.add(Component.empty());
        lore.add(plugin.getLanguageManager()
                .getMessage("sellallconfirmgui.click_to_confirm", "Click to sell everything!")
                .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        return lore;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() != inventory) return;
        int slot = event.getSlot();

        if (slot == CONFIRM_SLOT) {
            boolean sold = sellAll();
            if (sold) {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.2f);
            }
            new SellShopGUI(plugin, player).open(player);
            return;
        }

        if (slot == CANCEL_SLOT) {
            new SellShopGUI(plugin, player).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        }
    }

    private boolean sellAll() {
        String currencyName = plugin.getLanguageManager().getCurrencyName();
        double totalEarnings = 0;
        int totalItems = 0;
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        List<ItemStack> removedItems = new ArrayList<>();

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            if (plugin.getItemManager().isOtherVendorSellMaterial(item)) continue;
            double price = plugin.getItemManager().getSellPrice(item);
            if (price > 0) {
                totalEarnings += price * item.getAmount();
                totalItems    += item.getAmount();
                removedItems.add(item.clone());
                player.getInventory().setItem(i, null);
            }
        }

        if (totalItems > 0) {
            EconomyResult result = plugin.getEconomyManager().deposit(player, totalEarnings);
            if (!result.success()) {
                InventoryTransactionUtil.restoreOrDrop(player, removedItems);
                player.sendMessage(Component.text(plugin.getEconomyManager().transactionFailedMessage(result))
                        .color(NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1);
                return false;
            }
            if (data != null) {
                data.getSession().addDoubloonsEarned(totalEarnings);
            }
            player.sendMessage(Component.text(plugin.getLanguageManager().getString(
                            "sellshopgui.sold_items_summary",
                            "Sold %count% items for %value% %currency%!",
                            "count", String.valueOf(totalItems),
                            "value", com.fishrework.util.FormatUtil.format("%.0f", totalEarnings),
                            "currency", currencyName))
                    .color(NamedTextColor.GREEN));
            return true;
        } else {
            player.sendMessage(plugin.getLanguageManager()
                    .getMessage("sellshopgui.you_dont_have_anything_to", "You don't have anything to sell!")
                    .color(NamedTextColor.RED));
            return false;
        }
    }
}

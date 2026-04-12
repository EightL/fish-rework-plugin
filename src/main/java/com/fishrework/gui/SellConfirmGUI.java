package com.fishrework.gui;

import com.fishrework.FishRework;
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

import java.util.List;

/**
 * Sell confirmation GUI (3 rows).
 * Shows total items and value from the fish bag.
 * Slot 11: Confirm (lime wool)
 * Slot 15: Cancel (red wool)
 */
public class SellConfirmGUI extends BaseGUI {

    public enum BagType {
        FISH_BAG,
        LAVA_BAG
    }

    private final Player player;
    private final BagType bagType;
    private final double totalValue;
    private final int totalItems;
    private final ItemStack[] remainingContents;

    public SellConfirmGUI(FishRework plugin, Player player, BagType bagType) {
        super(plugin, 3, "Confirm Sale");
        this.player = player;
        this.bagType = bagType;

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        ItemStack[] contents = null;
        if (data != null) {
            contents = bagType == BagType.LAVA_BAG ? data.getLavaBagContents() : data.getFishBagContents();
        }

        SaleResult saleResult = buildSaleResult(contents);
        this.totalValue = saleResult.totalValue;
        this.totalItems = saleResult.totalItems;
        this.remainingContents = saleResult.remainingContents;

        initializeItems();
    }

    private void initializeItems() {
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);

        String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");

        // Info display (center)
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("Sale Summary").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        infoMeta.lore(List.of(
                Component.empty(),
                Component.text("Items: " + totalItems).color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Total: " + com.fishrework.util.FormatUtil.format("%.0f", totalValue) + " " + currencyName)
                        .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
        ));
        info.setItemMeta(infoMeta);
        inventory.setItem(13, info);

        // Slot 11: Confirm
        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.displayName(Component.text("✔ Confirm Sale").color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        confirmMeta.lore(List.of(
                Component.empty(),
                Component.text("Sell " + totalItems + " items for").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text(com.fishrework.util.FormatUtil.format("%.0f", totalValue) + " " + currencyName)
                        .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
        ));
        confirm.setItemMeta(confirmMeta);
        inventory.setItem(11, confirm);

        // Slot 15: Cancel
        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(Component.text("✖ Cancel").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        cancelMeta.lore(List.of(
                Component.empty(),
            Component.text(bagType == BagType.LAVA_BAG ? "Return to Magma Satchel" : "Return to Fish Bag")
                .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        cancel.setItemMeta(cancelMeta);
        inventory.setItem(15, cancel);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;

        int slot = event.getSlot();
        String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");

        if (slot == 11) {
            // Confirm — process sale
            if (totalItems > 0 && totalValue > 0) {
                PlayerData data = plugin.getPlayerData(player.getUniqueId());
                if (data != null) {
                    data.addBalance(totalValue);
                    data.getSession().addDoubloonsEarned(totalValue);
                    if (bagType == BagType.LAVA_BAG) {
                        data.setLavaBagContents(remainingContents);
                        plugin.getDatabaseManager().saveLavaBag(player.getUniqueId(), remainingContents);
                    } else {
                        data.setFishBagContents(remainingContents);
                        plugin.getDatabaseManager().saveFishBag(player.getUniqueId(), remainingContents);
                    }
                    plugin.getDatabaseManager().saveBalance(player.getUniqueId(), data.getBalance());
                }

                player.sendMessage(Component.text("Sold " + totalItems + " items for "
                        + com.fishrework.util.FormatUtil.format("%.0f", totalValue) + " " + currencyName + "!")
                        .color(NamedTextColor.GREEN));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.2f);
            } else {
                player.sendMessage(Component.text("Nothing to sell!").color(NamedTextColor.RED));
            }

            if (bagType == BagType.LAVA_BAG) {
                new LavaBagGUI(plugin, player).open(player);
            } else {
                new FishBagGUI(plugin, player).open(player);
            }

        } else if (slot == 15) {
            if (bagType == BagType.LAVA_BAG) {
                new LavaBagGUI(plugin, player).open(player);
            } else {
                new FishBagGUI(plugin, player).open(player);
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        }
    }

    private SaleResult buildSaleResult(ItemStack[] contents) {
        if (contents == null || contents.length == 0) {
            return new SaleResult(0, 0, null);
        }

        ItemStack[] keptItems = new ItemStack[contents.length];
        double value = 0;
        int items = 0;
        boolean hasKeptItems = false;

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) continue;

            double price = plugin.getItemManager().getSellPrice(item);
            if (price > 0) {
                value += price * item.getAmount();
                items += item.getAmount();
                continue;
            }

            keptItems[i] = item.clone();
            hasKeptItems = true;
        }

        return new SaleResult(value, items, hasKeptItems ? keptItems : null);
    }

    private static final class SaleResult {
        private final double totalValue;
        private final int totalItems;
        private final ItemStack[] remainingContents;

        private SaleResult(double totalValue, int totalItems, ItemStack[] remainingContents) {
            this.totalValue = totalValue;
            this.totalItems = totalItems;
            this.remainingContents = remainingContents;
        }
    }
}

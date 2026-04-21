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

public class SellOtherConfirmGUI extends BaseGUI {

    private final Player player;
    private final int totalItems;
    private final double totalValue;

    public SellOtherConfirmGUI(FishRework plugin, Player player) {
        super(plugin, 3, "Confirm Sell Other");
        this.player = player;

        double unitPrice = plugin.getConfig().getDouble("economy.other_vendor_price", 1.0);
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (!plugin.getItemManager().isOtherVendorSellMaterial(item)) continue;
            count += item.getAmount();
        }

        this.totalItems = count;
        this.totalValue = count * unitPrice;
        initializeItems();
    }

    private void initializeItems() {
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);

        String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");

        ItemStack info = new ItemStack(Material.DRIED_KELP);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(plugin.getLanguageManager().getMessage("sellotherconfirmgui.sell_other_summary", "Sell Other Summary").color(NamedTextColor.GOLD)
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

        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.displayName(plugin.getLanguageManager().getMessage("sellotherconfirmgui.confirm", "Confirm").color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        confirmMeta.lore(List.of(
                Component.empty(),
                plugin.getLanguageManager().getMessage("sellotherconfirmgui.sell_all_other_items", "Sell all Other items").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        confirm.setItemMeta(confirmMeta);
        inventory.setItem(11, confirm);

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(plugin.getLanguageManager().getMessage("sellotherconfirmgui.cancel", "Cancel").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        cancelMeta.lore(List.of(
                Component.empty(),
                plugin.getLanguageManager().getMessage("sellotherconfirmgui.return_to_fish_vendor", "Return to Fish Vendor").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        cancel.setItemMeta(cancelMeta);
        inventory.setItem(15, cancel);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;

        if (event.getSlot() == 11) {
            sellOtherItems();
            new SellShopGUI(plugin, player).open(player);
            return;
        }

        if (event.getSlot() == 15) {
            new SellShopGUI(plugin, player).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        }
    }

    private void sellOtherItems() {
        String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");
        double unitPrice = plugin.getConfig().getDouble("economy.other_vendor_price", 1.0);

        int soldItems = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            if (!plugin.getItemManager().isOtherVendorSellMaterial(item)) continue;
            soldItems += item.getAmount();
            player.getInventory().setItem(i, null);
        }

        if (soldItems <= 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage("sellotherconfirmgui.you_dont_have_any_other", "You don't have any Other items to sell.")
                    .color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1);
            return;
        }

        double earnings = soldItems * unitPrice;
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null) {
            data.addBalance(earnings);
            data.getSession().addDoubloonsEarned(earnings);
            plugin.getDatabaseManager().saveBalance(player.getUniqueId(), data.getBalance());
        }

        player.sendMessage(Component.text("Sold " + soldItems + " Other items for "
                + com.fishrework.util.FormatUtil.format("%.0f", earnings) + " " + currencyName + "!")
                .color(NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.2f);
    }
}

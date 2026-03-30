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
 * Main shop hub GUI (3 rows).
 * Slot 11: Fish Vendor (sell fish & materials)
 * Slot 13: Fishing Shop (buy baits & fish bag)
 * Slot 15: Fish Bag (open fish bag directly)
 * Slot 22: Back button
 * Bottom row: balance display (bottom-right)
 */
public class ShopMenuGUI extends BaseGUI {

    private final Player player;

    public ShopMenuGUI(FishRework plugin, Player player) {
        super(plugin, 3, "⛁ Fishing Shop");
        this.player = player;
        initializeItems();
    }

    private void initializeItems() {
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        double balance = data != null ? data.getBalance() : 0;
        String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");

        // Slot 11: Fish Vendor
        ItemStack vendor = new ItemStack(Material.COD);
        ItemMeta vendorMeta = vendor.getItemMeta();
        vendorMeta.displayName(Component.text("Fish Vendor").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        vendorMeta.lore(List.of(
                Component.empty(),
                Component.text("Sell fish & materials for").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text(currencyName + "!").color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Click to browse!").color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        vendor.setItemMeta(vendorMeta);
        inventory.setItem(11, vendor);

        // Slot 13: Fishing Shop
        ItemStack shop = new ItemStack(Material.EMERALD);
        ItemMeta shopMeta = shop.getItemMeta();
        shopMeta.displayName(Component.text("Fishing Shop").color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        shopMeta.lore(List.of(
                Component.empty(),
                Component.text("Buy baits & special items").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("to improve your fishing!").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Click to browse!").color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        shop.setItemMeta(shopMeta);
        inventory.setItem(13, shop);

        // Slot 22: Back
        setBackButton(22);

        // Balance display
        ItemStack balanceItem = new ItemStack(Material.SUNFLOWER);
        ItemMeta balMeta = balanceItem.getItemMeta();
        balMeta.displayName(Component.text("Balance: " + String.format("%.0f", balance) + " " + currencyName)
                .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        balanceItem.setItemMeta(balMeta);
                inventory.setItem(26, balanceItem);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;

        switch (event.getSlot()) {
            case 11 -> {
                if (!plugin.isFeatureEnabled("shop_enabled")) {
                    player.sendMessage(Component.text("The shop is currently disabled.").color(NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                    return;
                }
                new SellShopGUI(plugin, player).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            }
            case 13 -> {
                if (!plugin.isFeatureEnabled("shop_enabled")) {
                    player.sendMessage(Component.text("The shop is currently disabled.").color(NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                    return;
                }
                new BuyShopGUI(plugin, player).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            }
            case 22 -> {
                new com.fishrework.gui.SkillDetailGUI(plugin, player, com.fishrework.model.Skill.FISHING).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            }
        }
    }
}

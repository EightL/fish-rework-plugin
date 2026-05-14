package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.model.PlayerData;
import com.fishrework.util.FeatureKeys;
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
 * Slot 15: Configured currency vendors (if enabled)
 * Slot 22: Back button
 * Bottom row: balance display (bottom-right)
 */
public class ShopMenuGUI extends BaseGUI {

    private final Player player;

    public ShopMenuGUI(FishRework plugin, Player player) {
        super(plugin, 3, localizedTitle(plugin, "shopmenugui.title", "⛁ Fishing Shop"));
        this.player = player;
        initializeItems();
    }

    private void initializeItems() {
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);

        double balance = plugin.getEconomyManager().getBalance(player);
        String currencyName = plugin.getLanguageManager().getCurrencyName();

        // Slot 11: Fish Vendor
        ItemStack vendor = new ItemStack(Material.COD);
        ItemMeta vendorMeta = vendor.getItemMeta();
        vendorMeta.displayName(plugin.getLanguageManager().getMessage("shopmenugui.fish_vendor", "Fish Vendor").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        vendorMeta.lore(List.of(
                Component.empty(),
                plugin.getLanguageManager().getMessage("shopmenugui.sell_fish__materials_for", "Sell fish & materials for").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text(currencyName + "!").color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                plugin.getLanguageManager().getMessage("shopmenugui.click_to_browse", "Click to browse!").color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        vendor.setItemMeta(vendorMeta);
        inventory.setItem(11, vendor);

        // Slot 13: Fishing Shop
        ItemStack shop = new ItemStack(Material.EMERALD);
        ItemMeta shopMeta = shop.getItemMeta();
        shopMeta.displayName(plugin.getLanguageManager().getMessage("shopmenugui.fishing_shop", "Fishing Shop").color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        shopMeta.lore(List.of(
                Component.empty(),
                plugin.getLanguageManager().getMessage("shopmenugui.buy_baits__special_items", "Buy baits & special items").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                plugin.getLanguageManager().getMessage("shopmenugui.to_improve_your_fishing", "to improve your fishing!").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                plugin.getLanguageManager().getMessage("shopmenugui.click_to_browse", "Click to browse!").color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        shop.setItemMeta(shopMeta);
        inventory.setItem(13, shop);

        if (plugin.getConfig().getBoolean("vendors.enabled", false)) {
            ItemStack vendors = new ItemStack(Material.VILLAGER_SPAWN_EGG);
            ItemMeta vendorsMeta = vendors.getItemMeta();
            vendorsMeta.displayName(plugin.getLanguageManager().getMessage("shopmenugui.doubloon_vendors", "%currency% Vendors").color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            vendorsMeta.lore(List.of(
                    Component.empty(),
                    plugin.getLanguageManager().getMessage("shopmenugui.admin_configured_shops", "Admin-configured shops").color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    plugin.getLanguageManager().getMessage("shopmenugui.click_to_browse", "Click to browse!").color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            vendors.setItemMeta(vendorsMeta);
            inventory.setItem(15, vendors);
        }

        // Slot 22: Back
        setBackButton(22);

        // Balance display
        ItemStack balanceItem = new ItemStack(Material.SUNFLOWER);
        ItemMeta balMeta = balanceItem.getItemMeta();
        balMeta.displayName(Component.text(plugin.getLanguageManager().getString(
                        "shopmenugui.balance_prefix",
                        "Balance: %balance% %currency%",
                        "balance", com.fishrework.util.FormatUtil.format("%.0f", balance),
                        "currency", currencyName))
                .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        balanceItem.setItemMeta(balMeta);
                inventory.setItem(26, balanceItem);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;

        switch (event.getSlot()) {
            case 11 -> {
                if (!requireShopEnabled()) {
                    return;
                }
                new SellShopGUI(plugin, player).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            }
            case 13 -> {
                if (!requireShopEnabled()) {
                    return;
                }
                new BuyShopGUI(plugin, player).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            }
            case 15 -> {
                if (!plugin.getConfig().getBoolean("vendors.enabled", false)) {
                    return;
                }
                new VendorListGUI(plugin, player).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            }
            case 22 -> {
                new com.fishrework.gui.SkillDetailGUI(plugin, player, com.fishrework.model.Skill.FISHING).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            }
        }
    }

    private boolean requireShopEnabled() {
        if (plugin.isFeatureEnabled(FeatureKeys.SHOP_ENABLED)) {
            return true;
        }
        player.sendMessage(plugin.getLanguageManager().getMessage("shopmenugui.the_shop_is_currently_disabled", "The shop is currently disabled.").color(NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
        return false;
    }
}

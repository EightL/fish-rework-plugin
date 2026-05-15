package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.economy.EconomyResult;
import com.fishrework.manager.CustomShopManager;
import com.fishrework.model.CustomShop;
import com.fishrework.model.CustomShopListing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CustomShopGUI extends BaseGUI {

    private static final int[] SHOP_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int DELETE_SLOT = 49;
    private static final int BALANCE_SLOT = 53;

    private final Player viewer;
    private final CustomShop shop;
    private final boolean ownerView;
    private final Map<Integer, CustomShopListing> listingsByIndex;

    public CustomShopGUI(FishRework plugin, Player viewer, CustomShop shop) {
        super(plugin, 6, shop.ownerName() + "'s Fish Stall");
        this.viewer = viewer;
        this.shop = shop;
        this.ownerView = viewer.getUniqueId().equals(shop.ownerUuid());
        this.listingsByIndex = plugin.getCustomShopManager().getListingsBySlot(shop.id());
        initializeItems();
    }

    private void initializeItems() {
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);
        ItemStack bottom = named(Material.BLACK_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, bottom);
        }

        for (int i = 0; i < SHOP_SLOTS.length; i++) {
            CustomShopListing listing = listingsByIndex.get(i);
            inventory.setItem(SHOP_SLOTS[i], listing == null ? emptySlot() : listingItem(listing));
        }

        if (ownerView) {
            inventory.setItem(DELETE_SLOT, named(Material.TNT,
                    plugin.getLanguageManager().getMessage("customshop.delete_shop", "Delete Shop")
                            .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                    List.of(Component.text("Removes this stall and clears its listings.")
                            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));
        }

        inventory.setItem(BALANCE_SLOT, named(Material.SUNFLOWER,
                Component.text(plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(viewer)))
                        .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                List.of()));
    }

    private ItemStack emptySlot() {
        if (!ownerView) {
            return named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        }
        return named(Material.BLUE_STAINED_GLASS_PANE,
                plugin.getLanguageManager().getMessage("customshop.add_item", "Add item")
                        .color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("Click to add a buy-only listing.")
                        .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
    }

    private ItemStack listingItem(CustomShopListing listing) {
        ItemStack item = listing.item().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(Component.text("Price: " + plugin.getCustomShopManager().formatPrice(listing.price()))
                .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        if (ownerView) {
            lore.add(Component.text("Listed in your shop")
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Right-click to reclaim this listing")
                    .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Click to buy")
                    .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack named(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;
        int slot = event.getSlot();

        if (ownerView && slot == DELETE_SLOT) {
            new CustomShopDeleteConfirmGUI(plugin, viewer, shop).open(viewer);
            viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }

        int shopIndex = indexForSlot(slot);
        if (shopIndex < 0) return;

        CustomShopListing listing = listingsByIndex.get(shopIndex);
        if (listing == null) {
            if (ownerView) {
                CustomShopManager.PendingPrice pending = plugin.getCustomShopManager()
                        .getPendingPriceForShop(viewer.getUniqueId(), shop.id());
                if (pending != null) {
                    new CustomShopAddItemGUI(plugin, viewer, shop, pending.slotIndex(), pending.item()).open(viewer);
                    viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
                    return;
                }
                new CustomShopAddItemGUI(plugin, viewer, shop, shopIndex).open(viewer);
                viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            }
            return;
        }

        if (ownerView) {
            if (event.isRightClick()) {
                ItemStack reclaimed = plugin.getCustomShopManager().reclaimListing(viewer, shop, shopIndex);
                if (reclaimed == null || reclaimed.getType().isAir()) {
                    viewer.sendMessage(Component.text("That listing is no longer available.")
                            .color(NamedTextColor.RED));
                    viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                    new CustomShopGUI(plugin, viewer, shop).open(viewer);
                    return;
                }
                Map<Integer, ItemStack> leftover = viewer.getInventory().addItem(reclaimed.clone());
                for (ItemStack drop : leftover.values()) {
                    viewer.getWorld().dropItemNaturally(viewer.getLocation(), drop);
                }
                viewer.sendMessage(Component.text("Listing removed and item returned to you.")
                        .color(NamedTextColor.GREEN));
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                new CustomShopGUI(plugin, viewer, shop).open(viewer);
            }
            return;
        }

        EconomyResult result = plugin.getCustomShopManager().purchase(viewer, shop, listing);
        if (!result.success()) {
            viewer.sendMessage(Component.text(plugin.getEconomyManager().transactionFailedMessage(result)).color(NamedTextColor.RED));
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            return;
        }
        viewer.sendMessage(Component.text("Bought item for " + plugin.getCustomShopManager().formatPrice(Math.round(result.amount())) + ".")
                .color(NamedTextColor.GREEN));
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        new CustomShopGUI(plugin, viewer, shop).open(viewer);
    }

    private int indexForSlot(int slot) {
        for (int i = 0; i < SHOP_SLOTS.length; i++) {
            if (SHOP_SLOTS[i] == slot) return i;
        }
        return -1;
    }
}

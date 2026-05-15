package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.model.CustomShop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class CustomShopAddItemGUI extends BaseGUI {

    private static final int ITEM_SLOT = 13;
    private static final int BACK_SLOT = 18;
    private static final int CONFIRM_SLOT = 22;

    private final Player player;
    private final CustomShop shop;
    private final int shopSlotIndex;
    private ItemStack selected;
    private boolean selectedIsPending;
    private boolean confirmed;

    public CustomShopAddItemGUI(FishRework plugin, Player player, CustomShop shop, int shopSlotIndex) {
        this(plugin, player, shop, shopSlotIndex, null);
    }

    public CustomShopAddItemGUI(FishRework plugin, Player player, CustomShop shop, int shopSlotIndex, ItemStack pendingItem) {
        super(plugin, 3, "Add Shop Item");
        this.player = player;
        this.shop = shop;
        this.shopSlotIndex = shopSlotIndex;
        this.selected = pendingItem == null || pendingItem.getType().isAir() ? null : pendingItem.clone();
        if (this.selected != null) {
            this.selected.setAmount(1);
        }
        this.selectedIsPending = this.selected != null;
        initializeItems();
    }

    private void initializeItems() {
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);
        inventory.setItem(ITEM_SLOT, selected == null ? null : selected.clone());
        ItemStack bottom = named(Material.BLACK_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int slot = 18; slot < 27; slot++) {
            inventory.setItem(slot, bottom);
        }
        inventory.setItem(BACK_SLOT, named(Material.BARRIER,
                plugin.getLanguageManager().getMessage("basegui.back", "Back").color(NamedTextColor.RED),
                List.of()));
        inventory.setItem(CONFIRM_SLOT, named(Material.LIME_WOOL,
                Component.text("Confirm").color(NamedTextColor.GREEN),
                List.of(Component.text("Set the price next.").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));
    }

    private ItemStack named(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public boolean handlesPlayerInventoryClicks() {
        return true;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        boolean top = event.getClickedInventory().equals(event.getView().getTopInventory());

        if (top && event.getSlot() == BACK_SLOT) {
            new CustomShopGUI(plugin, player, shop).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }
        if (top && event.getSlot() == CONFIRM_SLOT) {
            if (selected == null || selected.getType().isAir()) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                return;
            }
            confirmed = true;
            player.closeInventory();
            if (selectedIsPending) {
                plugin.getCustomShopManager().remindPriceInput(player);
            } else {
                plugin.getCustomShopManager().startPriceInput(player, shop.id(), shopSlotIndex, selected);
            }
            return;
        }
        if (top && event.getSlot() == ITEM_SLOT) {
            if (selected != null) {
                if (selectedIsPending) {
                    plugin.getCustomShopManager().clearPendingPrice(player.getUniqueId());
                    selectedIsPending = false;
                }
                giveBack(selected);
                selected = null;
                inventory.setItem(ITEM_SLOT, null);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            } else {
                takeOneFromCursor(event);
            }
            return;
        }
        if (!top) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;
            setSelected(clicked.clone());
            clicked.setAmount(clicked.getAmount() - 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        }
    }

    private void takeOneFromCursor(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType().isAir()) return;
        setSelected(cursor.clone());
        cursor.setAmount(cursor.getAmount() - 1);
        event.setCursor(cursor.getAmount() <= 0 ? null : cursor);
    }

    private void setSelected(ItemStack item) {
        if (selected != null) {
            if (selectedIsPending) {
                plugin.getCustomShopManager().clearPendingPrice(player.getUniqueId());
                selectedIsPending = false;
            }
            giveBack(selected);
        }
        selected = item.clone();
        selected.setAmount(1);
        selectedIsPending = false;
        inventory.setItem(ITEM_SLOT, selected.clone());
    }

    @Override
    public void onDrag(InventoryDragEvent event) {
        if (event.getRawSlots().contains(ITEM_SLOT)) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        if (!confirmed && selected != null && !selectedIsPending) {
            giveBack(selected);
        }
    }

    private void giveBack(ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }
}

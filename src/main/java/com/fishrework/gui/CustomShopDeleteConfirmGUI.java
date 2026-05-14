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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class CustomShopDeleteConfirmGUI extends BaseGUI {

    private static final int CANCEL_SLOT = 11;
    private static final int CONFIRM_SLOT = 15;

    private final Player player;
    private final CustomShop shop;

    public CustomShopDeleteConfirmGUI(FishRework plugin, Player player, CustomShop shop) {
        super(plugin, 3, "Delete Fish Stall");
        this.player = player;
        this.shop = shop;
        initializeItems();
    }

    private void initializeItems() {
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);
        inventory.setItem(CANCEL_SLOT, named(Material.RED_WOOL, Component.text("Cancel").color(NamedTextColor.RED), List.of()));
        inventory.setItem(CONFIRM_SLOT, named(Material.LIME_WOOL, Component.text("Delete Shop").color(NamedTextColor.GREEN),
                List.of(Component.text("Listings are permanently removed.").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));
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
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;
        if (event.getSlot() == CANCEL_SLOT) {
            new CustomShopGUI(plugin, player, shop).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }
        if (event.getSlot() != CONFIRM_SLOT) return;

        plugin.getCustomShopManager().deleteShop(shop);
        ItemStack stallItem = plugin.getItemManager().getItem("fish_stall");
        if (stallItem != null) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stallItem);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 1, 1);
        player.sendMessage(Component.text("Fish stall deleted.").color(NamedTextColor.GREEN));
    }
}

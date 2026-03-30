package com.fishrework.gui;

import com.fishrework.FishRework;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class BaseGUI implements InventoryHolder {

    protected final FishRework plugin;
    protected final Inventory inventory;

    public BaseGUI(FishRework plugin, int rows, String title) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, rows * 9, Component.text(title));
    }

    public void open(org.bukkit.entity.Player player) {
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public abstract void onClick(InventoryClickEvent event);

    /**
     * Whether this GUI intentionally handles clicks from the player's own inventory
     * while the GUI is open. Default is false for safety.
     */
    public boolean handlesPlayerInventoryClicks() {
        return false;
    }

    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        // Default implementation does nothing
    }

    // ══════════════════════════════════════════════════════════
    //  Shared GUI Helpers
    // ══════════════════════════════════════════════════════════

    /**
     * Fills every slot in the inventory with a named-empty glass pane.
     */
    protected void fillBackground(Material material) {
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.empty());
        filler.setItemMeta(meta);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    /**
     * Places a red Barrier "Back" button at the given slot.
     */
    protected void setBackButton(int slot) {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta meta = back.getItemMeta();
        meta.displayName(Component.text("Back").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(meta);
        inventory.setItem(slot, back);
    }

    /**
     * Places Previous / Next page arrows at the given slots.
     * Only places an arrow when there is actually a page to go to.
     */
    protected void setPaginationControls(int prevSlot, int nextSlot, int page, int totalPages) {
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta meta = prev.getItemMeta();
            meta.displayName(Component.text("Previous Page").color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            prev.setItemMeta(meta);
            inventory.setItem(prevSlot, prev);
        }
        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            meta.displayName(Component.text("Next Page").color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            next.setItemMeta(meta);
            inventory.setItem(nextSlot, next);
        }
    }

    /**
     * Creates a Book item showing "Page X/Y" with an optional subtitle lore line.
     */
    protected ItemStack createPageInfo(int page, int totalPages) {
        return createPageInfo(page, totalPages, null);
    }

    protected ItemStack createPageInfo(int page, int totalPages, String subtitle) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Page " + (page + 1) + "/" + totalPages).color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        if (subtitle != null) {
            meta.lore(List.of(Component.text(subtitle).color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
        }
        item.setItemMeta(meta);
        return item;
    }
}

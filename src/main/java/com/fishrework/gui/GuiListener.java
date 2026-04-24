package com.fishrework.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.entity.Player;

public class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof BaseGUI) {
            BaseGUI gui = (BaseGUI) event.getInventory().getHolder();

            if (event.getClickedInventory() == null) {
                event.setCancelled(true);
                return;
            }

            boolean clickedTopInventory = event.getClickedInventory().equals(event.getView().getTopInventory());
            if (!clickedTopInventory && !gui.handlesPlayerInventoryClicks()) {
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true); // Default to cancelled
            if (event.getWhoClicked() instanceof Player player) {
                gui.plugin.getLanguageManager().withPlayer(player, () -> gui.onClick(event));
            } else {
                gui.onClick(event);
            }
        }
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BaseGUI) {
            BaseGUI gui = (BaseGUI) event.getInventory().getHolder();
            if (event.getPlayer() instanceof Player player) {
                gui.plugin.getLanguageManager().withPlayer(player, () -> gui.onClose(event));
            } else {
                gui.onClose(event);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BaseGUI gui) {
            if (!gui.handlesPlayerInventoryClicks()) {
                event.setCancelled(true);
                return;
            }
            if (event.getWhoClicked() instanceof Player player) {
                gui.plugin.getLanguageManager().withPlayer(player, () -> gui.onDrag(event));
            } else {
                gui.onDrag(event);
            }
        }
    }
}

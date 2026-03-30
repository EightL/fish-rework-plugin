package com.fishrework.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

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
            gui.onClick(event);
        }
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BaseGUI) {
            BaseGUI gui = (BaseGUI) event.getInventory().getHolder();
            gui.onClose(event);
        }
    }
}

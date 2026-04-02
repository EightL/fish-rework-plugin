package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.gui.FishBagGUI;
import com.fishrework.util.BagUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles right-click interaction with the Fish Bag item.
 * Opens the FishBagGUI when a player right-clicks while holding a Fish Bag.
 */
public class FishBagListener implements Listener {

    private final FishRework plugin;

    public FishBagListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (plugin.getItemManager().isFishBag(item)) {
            event.setCancelled(true);

            int recovered = BagUtils.recoverVanillaBundleContents(player, item);
            if (recovered > 0) {
                player.sendMessage(Component.text("[Fish Bag] ").color(NamedTextColor.DARK_GRAY)
                        .append(Component.text("Recovered ").color(NamedTextColor.GRAY))
                        .append(Component.text(recovered).color(NamedTextColor.GOLD))
                        .append(Component.text(" item(s) from vanilla bundle storage.").color(NamedTextColor.GRAY)));
            }

            new FishBagGUI(plugin, player).open(player);
        }
    }

    /**
     * Prevents players from using the vanilla bundle mechanics (adding items to the bundle)
     * by clicking on the custom Fish Bag in their inventory.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean currentIsFishBag = plugin.getItemManager().isFishBag(current);
        boolean cursorIsFishBag = plugin.getItemManager().isFishBag(cursor);

        if (BagUtils.shouldCancelBagInteraction(event, currentIsFishBag, cursorIsFishBag)) {
            event.setCancelled(true);
        }
    }
}

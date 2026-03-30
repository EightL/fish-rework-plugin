package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.gui.FishBagGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayList;
import java.util.List;

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

            int recovered = recoverVanillaBundleContents(player, item);
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

        if (!currentIsFishBag && !cursorIsFishBag) return;

        // Hard-disable vanilla bundle storage/extract interactions on custom fish bag.
        if (event.isRightClick() || event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
            event.setCancelled(true);
            return;
        }

        // Also block insertion attempts that are not right-click based.
        boolean cursorHasItem = cursor != null && !cursor.getType().isAir();
        boolean currentHasItem = current != null && !current.getType().isAir();
        if ((currentIsFishBag && cursorHasItem) || (cursorIsFishBag && currentHasItem)) {
            event.setCancelled(true);
        }
    }

    private int recoverVanillaBundleContents(Player player, ItemStack bag) {
        if (bag == null || !plugin.getItemManager().isFishBag(bag)) return 0;
        if (!(bag.getItemMeta() instanceof BundleMeta meta)) return 0;

        List<ItemStack> stored = meta.getItems();
        if (stored == null || stored.isEmpty()) return 0;

        int recoveredCount = 0;
        for (ItemStack stack : stored) {
            if (stack == null || stack.getType().isAir()) continue;
            recoveredCount += stack.getAmount();

            java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack.clone());
            for (ItemStack remaining : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
            }
        }

        meta.setItems(new ArrayList<>());
        bag.setItemMeta(meta);
        return recoveredCount;
    }
}

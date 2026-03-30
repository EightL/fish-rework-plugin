package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.gui.LavaBagGUI;
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
 * Handles right-click GUI access and bundle interaction prevention for the lava bag.
 */
public class LavaBagListener implements Listener {

    private final FishRework plugin;

    public LavaBagListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (plugin.getItemManager().isLavaBag(item)) {
            event.setCancelled(true);

            int recovered = recoverVanillaBundleContents(player, item);
            if (recovered > 0) {
                player.sendMessage(Component.text("[Magma Satchel] ").color(NamedTextColor.DARK_GRAY)
                        .append(Component.text("Recovered ").color(NamedTextColor.GRAY))
                        .append(Component.text(recovered).color(NamedTextColor.GOLD))
                        .append(Component.text(" item(s) from vanilla bundle storage.").color(NamedTextColor.GRAY)));
            }

            new LavaBagGUI(plugin, player).open(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean currentIsLavaBag = plugin.getItemManager().isLavaBag(current);
        boolean cursorIsLavaBag = plugin.getItemManager().isLavaBag(cursor);

        if (!currentIsLavaBag && !cursorIsLavaBag) return;

        // Hard-disable vanilla bundle storage/extract interactions on custom lava bag.
        if (event.isRightClick() || event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
            event.setCancelled(true);
            return;
        }

        // Also block item insertion attempts involving the bag, even on non-right-click actions.
        boolean cursorHasItem = cursor != null && !cursor.getType().isAir();
        boolean currentHasItem = current != null && !current.getType().isAir();
        if ((currentIsLavaBag && cursorHasItem) || (cursorIsLavaBag && currentHasItem)) {
            event.setCancelled(true);
        }
    }

    private int recoverVanillaBundleContents(Player player, ItemStack bag) {
        if (bag == null || !plugin.getItemManager().isLavaBag(bag)) return 0;
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

package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.gui.LavaBagGUI;
import com.fishrework.util.BagUtils;
import com.fishrework.util.FeatureKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

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
        plugin.getLanguageManager().withPlayer(event.getPlayer(), () -> handlePlayerInteract(event));
    }

    private void handlePlayerInteract(PlayerInteractEvent event) {
        if (!plugin.isFeatureEnabled(FeatureKeys.LAVA_BAG)) return;
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (plugin.getItemManager().isLavaBag(item)) {
            event.setCancelled(true);

            int recovered = BagUtils.recoverVanillaBundleContents(player, item);
            if (recovered > 0) {
                player.sendMessage(plugin.getLanguageManager().getMessage("lavabaglistener.magma_satchel", "[Magma Satchel] ").color(NamedTextColor.DARK_GRAY)
                        .append(plugin.getLanguageManager().getMessage("lavabaglistener.recovered", "Recovered ").color(NamedTextColor.GRAY))
                        .append(Component.text(recovered).color(NamedTextColor.GOLD))
                        .append(plugin.getLanguageManager().getMessage("lavabaglistener.items_from_vanilla_bundle_storage", " item(s) from vanilla bundle storage.").color(NamedTextColor.GRAY)));
            }

            new LavaBagGUI(plugin, player).open(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!plugin.isFeatureEnabled(FeatureKeys.LAVA_BAG)) return;
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean currentIsLavaBag = plugin.getItemManager().isLavaBag(current);
        boolean cursorIsLavaBag = plugin.getItemManager().isLavaBag(cursor);

        if (BagUtils.shouldCancelBagInteraction(event, currentIsLavaBag, cursorIsLavaBag)) {
            event.setCancelled(true);
        }
    }
}

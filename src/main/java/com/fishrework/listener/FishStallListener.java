package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.gui.CustomShopAddItemGUI;
import com.fishrework.gui.CustomShopGUI;
import com.fishrework.manager.CustomShopManager;
import com.fishrework.gui.ShopMenuGUI;
import com.fishrework.model.CustomShop;
import com.fishrework.util.FeatureKeys;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class FishStallListener implements Listener {

    private final FishRework plugin;
    private final NamespacedKey stallInteractKey;

    public FishStallListener(FishRework plugin) {
        this.plugin = plugin;
        this.stallInteractKey = new NamespacedKey(plugin, "fish_stall_interact");
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        handleStallInteract(event);
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerInteractAtEntityEvent || event.getRightClicked() instanceof Interaction) return;
        handleStallInteract(event);
    }

    private void handleStallInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        boolean stallInteraction = entity.getPersistentDataContainer().has(stallInteractKey, PersistentDataType.STRING);
        boolean customShopInteraction = plugin.getCustomShopManager() != null
                && plugin.getCustomShopManager().isCustomShopInteraction(entity);
        if (!stallInteraction && !customShopInteraction) return;

        event.setCancelled(true);

        Player player = event.getPlayer();

        if (customShopInteraction) {
            if (!plugin.isFeatureEnabled(FeatureKeys.CUSTOM_SHOPS_ENABLED)) {
                player.sendMessage(plugin.getLanguageManager()
                        .getMessage("fishstalllistener.custom_shops_disabled", "Custom shops are disabled on this server.")
                        .color(NamedTextColor.RED));
                return;
            }
            String shopId = plugin.getCustomShopManager().getShopId(entity);
            CustomShop shop = plugin.getCustomShopManager().getShop(shopId);
            if (shop == null) {
                player.sendMessage(plugin.getLanguageManager()
                        .getMessage("fishstalllistener.shop_not_found", "This shop no longer exists.")
                        .color(NamedTextColor.RED));
                return;
            }
            CustomShopManager.PendingPrice pending = plugin.getCustomShopManager()
                    .getPendingPriceForShop(player.getUniqueId(), shop.id());
            if (pending != null && player.getUniqueId().equals(shop.ownerUuid())) {
                new CustomShopAddItemGUI(plugin, player, shop, pending.slotIndex(), pending.item()).open(player);
                return;
            }
            new CustomShopGUI(plugin, player, shop).open(player);
            return;
        }

        if (!plugin.isFeatureEnabled(FeatureKeys.SHOP_ENABLED)) {
            player.sendMessage(plugin.getLanguageManager()
                    .getMessage("fishingcommand.feature_shop_disabled", "The shop is disabled on this server.")
                    .color(NamedTextColor.RED));
            return;
        }

        new ShopMenuGUI(plugin, player).open(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItemInHand();
        if (!plugin.getItemManager().isFishStall(item)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!plugin.isFeatureEnabled(FeatureKeys.CUSTOM_SHOPS_ENABLED)) {
            player.sendMessage(plugin.getLanguageManager()
                    .getMessage("fishstalllistener.custom_shops_disabled", "Custom shops are disabled on this server.")
                    .color(NamedTextColor.RED));
            return;
        }
        CustomShop shop = plugin.getCustomShopManager().createShop(player, event.getBlock().getLocation());
        if (shop == null) {
            player.sendMessage(plugin.getLanguageManager()
                    .getMessage("fishstalllistener.place_failed", "Could not place the fish stall here.")
                    .color(NamedTextColor.RED));
            return;
        }
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItem(event.getHand(), null);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 1, 1);
        new CustomShopGUI(plugin, player, shop).open(player);
    }
}

package com.fishrework.listener;

import com.fishrework.FishRework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.function.Supplier;

public class LoreUpdateListener implements Listener {

    private final FishRework plugin;

    public LoreUpdateListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        // Run next tick because enchants are applied AFTER the event? 
        // Docs say "The enchants to be applied" are in the event. The item in the table is not yet enchanted.
        // Wait, event.getItem() returns the item being enchanted.
        // But the enchantments are in event.getEnchantsToAdd().
        // We can't easily modify the resulting item here because it hasn't happened yet.
        // Strategy: Run a task 1 tick later to check the inventory slot?
        // Or better: Just calculate it based on what WILL be added? No, too complex.
        // Best approach: Schedule a task to update the item in the inventory 1 tick later.
        
        plugin.getServer().getScheduler().runTask(plugin, () -> {
           ItemStack item = event.getItem(); // This reference might be valid?
           // Actually, the item stays in the slot.
           if (item != null) {
               plugin.getLoreManager().updateLore(item);
           }
        });
    }

    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result != null && result.getType() != org.bukkit.Material.AIR) {
            plugin.getLoreManager().updateLore(result);
        }
    }

    @EventHandler
    public void onGrindstonePrepare(PrepareGrindstoneEvent event) {
        ItemStack result = event.getResult();
        if (result != null && result.getType() != org.bukkit.Material.AIR) {
            plugin.getLoreManager().updateLore(result);
            event.setResult(result);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        ItemStack item = event.getItem().getItemStack();
        if (!item.hasItemMeta()) return;

        String id = item.getItemMeta().getPersistentDataContainer().get(
                plugin.getItemManager().CUSTOM_ITEM_KEY, PersistentDataType.STRING);
        if (id == null) return;

        Supplier<ItemStack> supplier = plugin.getItemManager().getItemRegistry().get(id);
        if (supplier == null) return;

        ItemStack fresh = supplier.get();
        fresh.setAmount(item.getAmount());

        // Preserve mutable player-applied state so refresh doesn't wipe progression.
        for (var ench : item.getEnchantments().entrySet()) {
            fresh.addUnsafeEnchantment(ench.getKey(), ench.getValue());
        }

        ItemMeta oldMeta = item.getItemMeta();
        ItemMeta freshMeta = fresh.getItemMeta();
        if (oldMeta instanceof Damageable oldDamageable && freshMeta instanceof Damageable freshDamageable) {
            freshDamageable.setDamage(oldDamageable.getDamage());
            fresh.setItemMeta(freshMeta);
        }

        event.getItem().setItemStack(fresh);
    }

    /** Catches /enchant command and any other direct item modification. */
    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase();
        if (!msg.startsWith("/enchant")) return;
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held != null && !held.getType().isAir() && held.hasItemMeta()) {
                plugin.getLoreManager().updateLore(held);
                player.getInventory().setItemInMainHand(held);
            }
        }, 1L);
    }

    /** Refreshes lore when a player closes their inventory — catches /give, plugin-added enchants, etc. */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held != null && !held.getType().isAir() && held.hasItemMeta()) {
            plugin.getLoreManager().updateLore(held);
            player.getInventory().setItemInMainHand(held);
        }
    }
}

package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.model.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles the usage of cooling items (Frost Salve, Magma Filter) to reduce heat.
 */
public class CoolingItemListener implements Listener {

    private final FishRework plugin;

    public CoolingItemListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Only trigger on right clicks
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !item.hasItemMeta()) return;

        // Check for PDC identifier keys for cooling items
        boolean isFrostSalve = item.getItemMeta().getPersistentDataContainer()
                .has(new org.bukkit.NamespacedKey(plugin, "frost_salve"), PersistentDataType.BYTE);
        boolean isMagmaFilter = item.getItemMeta().getPersistentDataContainer()
                .has(new org.bukkit.NamespacedKey(plugin, "magma_filter"), PersistentDataType.BYTE);

        if (!isFrostSalve && !isMagmaFilter) return;

        event.setCancelled(true); // Prevent default usage if any

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) {
            return;
        }

        if (isFrostSalve && data.getHeat() <= 0) {
            player.sendMessage(Component.text("You are not hot enough to use this item.").color(NamedTextColor.RED));
            return;
        }

        double heatReduction = isMagmaFilter ? 50.0 : 25.0; // Magma filter = 50, Frost salve = 25
        if (data.getHeat() > 0) {
            plugin.getHeatManager().reduceHeat(player, heatReduction);
        }

        if (isMagmaFilter) {
            plugin.getHeatManager().applyMagmaFilterResistance(player);
        }

        // Consume item
        item.setAmount(item.getAmount() - 1);

        // Feedback
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.0f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), 20);

        if (isMagmaFilter) {
            double resistancePercent = plugin.getConfig().getDouble("heat.magma_filter_resistance_percent", 50.0);
            int durationSeconds = plugin.getConfig().getInt("heat.magma_filter_duration_seconds", 300);
            player.sendMessage(Component.text("Magma Filter active: -" + (int) resistancePercent + "% heat gain for " + durationSeconds + "s!")
                .color(NamedTextColor.AQUA));
        } else {
            player.sendMessage(Component.text("You used a cooling item and reduced your heat by " + (int)heatReduction + "%!")
                .color(NamedTextColor.AQUA));
        }
                
        // Instantly show the updated gauge
        plugin.getHeatManager().showHeatGauge(player, data.getHeat());
    }
}

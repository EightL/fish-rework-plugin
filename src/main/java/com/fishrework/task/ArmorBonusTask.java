package com.fishrework.task;

import com.fishrework.FishRework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Periodically heals players wearing Treasure armor sets (natural regen bonus).
 * <p>
 * Runs every 60 ticks (3 seconds). Each Treasure armor piece grants passive healing:
 * - Treasure:         0.25 HP per piece (0.5 hearts full set)
 * - Pure Treasure:    0.5  HP per piece (1.0 hearts full set)
 * - Perfect Treasure: 0.75 HP per piece (1.5 hearts full set)
 * <p>
 * Only heals if the player is not at full health and has food level > 0.
 */
public class ArmorBonusTask implements Runnable {

    private final FishRework plugin;

    public ArmorBonusTask(FishRework plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.isDead()) continue;
            if (player.getHealth() >= player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()) continue;
            if (player.getFoodLevel() <= 0) continue;

            double healAmount = getTreasureHealAmount(player);
            if (healAmount <= 0) continue;

            double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.min(player.getHealth() + healAmount, maxHealth));
        }
    }

    private double getTreasureHealAmount(Player player) {
        double total = 0.0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            PersistentDataContainer pdc = armor.getItemMeta().getPersistentDataContainer();

            if (pdc.has(plugin.getItemManager().PERFECT_TREASURE_ARMOR_KEY, PersistentDataType.BYTE)) {
                total += plugin.getConfig().getDouble("armor_healing.perfect_per_piece", 0.75);
            } else if (pdc.has(plugin.getItemManager().PURE_TREASURE_ARMOR_KEY, PersistentDataType.BYTE)) {
                total += plugin.getConfig().getDouble("armor_healing.pure_per_piece", 0.5);
            } else if (pdc.has(plugin.getItemManager().TREASURE_ARMOR_KEY, PersistentDataType.BYTE)) {
                total += plugin.getConfig().getDouble("armor_healing.basic_per_piece", 0.25);
            }
        }
        return total;
    }
}

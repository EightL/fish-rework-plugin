package com.fishrework.gui;

import com.fishrework.FishRework;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Shared stat-reading helpers used by multiple GUIs and listeners.
 * Eliminates duplication of getEnchantLevel() and getEquipmentFlatSCBonus()
 * that was previously copy-pasted across SkillDetailGUI, SkillsMenuGUI,
 * and CombatBonusListener.
 */
public final class StatHelper {

    private StatHelper() {}

    /**
     * Sums the enchantment level from main hand + off hand.
     */
    public static int getEnchantLevel(Player player, Enchantment enchant) {
        int level = 0;
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && main.hasItemMeta()) level += main.getEnchantmentLevel(enchant);
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && off.hasItemMeta()) level += off.getEnchantmentLevel(enchant);
        return level;
    }

    /**
     * Sums a flat SC bonus (attack or defense) from all armor + main hand + off hand.
     */
    public static double getEquipmentFlatSCBonus(Player player, NamespacedKey key) {
        double total = 0.0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            total += getFlatBonus(armor, key);
        }
        total += getFlatBonus(player.getInventory().getItemInMainHand(), key);
        total += getFlatBonus(player.getInventory().getItemInOffHand(), key);

        FishRework plugin = FishRework.getInstance();
        if (plugin != null
                && plugin.getTotemManager() != null
                && key.equals(plugin.getItemManager().SC_FLAT_ATTACK_KEY)) {
            total += plugin.getTotemManager().getSeaCreatureAttackModifier(player);
        }
        return total;
    }

    private static double getFlatBonus(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return 0.0;
        if (item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.DOUBLE)) {
            return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
        }
        return 0.0;
    }
}

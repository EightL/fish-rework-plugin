package com.fishrework.listener;

import com.fishrework.FishRework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Prevents custom Fish Rework items from being processed in crafting stations
 * (Anvil, Grindstone, Smithing Table) that could strip their PDC metadata,
 * creating ungated duplicates or breaking gameplay balance.
 *
 * Exceptions:
 * - vanilla-compatible custom gear in the anvil base slot follows vanilla behavior
 * - any damaged custom item can be repaired in an anvil using weird_material
 */
public class AnvilProtectionListener implements Listener {

    private final FishRework plugin;

    public AnvilProtectionListener(FishRework plugin) {
        this.plugin = plugin;
    }

    // ── Anvil Protection ──────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack base = event.getInventory().getItem(0);
        ItemStack addition = event.getInventory().getItem(1);

        if (tryPrepareCustomRepair(event, base, addition)) {
            return;
        }

        if (isVanillaCompatibleCustomBase(base)) {
            return;
        }

        if (hasCustomItem(base) || hasCustomItem(addition)) {
            event.setResult(null);
        }
    }

    // ── Grindstone Protection ─────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (hasCustomItem(event.getInventory().getItem(0))
                || hasCustomItem(event.getInventory().getItem(1))) {
            event.setResult(null);
        }
    }

    // ── Smithing Table Protection ─────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        // Check template, base, and addition slots
        for (ItemStack item : event.getInventory().getStorageContents()) {
            if (hasCustomItem(item)) {
                event.setResult(null);
                return;
            }
        }
    }

    // ── Helper ────────────────────────────────────────────────

    /**
     * Returns true if the item is a Fish Rework custom item (has any custom PDC marker).
     */
    private boolean hasCustomItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return plugin.getItemManager().isCustomItem(item);
    }

    private static final String REPAIR_MATERIAL_ID = "weird_material";

    private boolean isVanillaCompatibleCustomBase(ItemStack base) {
        return base != null && plugin.getItemManager().isVanillaCompatibleCustomItem(base);
    }

    private boolean tryPrepareCustomRepair(PrepareAnvilEvent event, ItemStack base, ItemStack addition) {
        if (base == null || base.getType().isAir() || addition == null || addition.getType().isAir()) {
            return false;
        }
        if (!hasCustomItem(base)) {
            return false;
        }

        ItemMeta baseMeta = base.getItemMeta();
        if (!(baseMeta instanceof Damageable damageableBase) || damageableBase.getDamage() <= 0) {
            return false;
        }

        if (!plugin.getItemManager().isCustomItem(addition, REPAIR_MATERIAL_ID)) {
            return false;
        }

        ItemStack repaired = base.clone();
        ItemMeta meta = repaired.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return false;
        }

        int maxDamage = damageable.getMaxDamage();
        if (maxDamage <= 0) maxDamage = repaired.getType().getMaxDurability();
        if (maxDamage <= 0) return false;

        int repairAmount = Math.max(1, maxDamage / 4);
        damageable.setDamage(Math.max(0, damageable.getDamage() - repairAmount));
        repaired.setItemMeta(meta);

        event.setResult(repaired);
        return true;
    }
}

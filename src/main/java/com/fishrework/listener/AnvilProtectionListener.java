package com.fishrework.listener;

import com.fishrework.FishRework;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Prevents custom Fish Rework items from being processed in crafting stations
 * (Anvil, Grindstone, Smithing Table) that could strip their PDC metadata,
 * creating ungated duplicates or breaking gameplay balance.
 */
public class AnvilProtectionListener implements Listener {

    private final FishRework plugin;

    public AnvilProtectionListener(FishRework plugin) {
        this.plugin = plugin;
    }

    // ── Anvil Protection ──────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (hasCustomItem(event.getInventory().getItem(0))
                || hasCustomItem(event.getInventory().getItem(1))) {
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
}

package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.manager.TotemManager;
import com.fishrework.util.FeatureKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles Treasure Totem placement, breaking, and chunk persistence.
 */
public class TotemListener implements Listener {

    private final FishRework plugin;

    public TotemListener(FishRework plugin) {
        this.plugin = plugin;
    }

    // ── Placement ─────────────────────────────────────────────

    /**
     * Detects when a player places a Conduit that is actually a Treasure Totem.
     * Cancels the vanilla block place and spawns the custom totem entities instead.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.isFeatureEnabled(FeatureKeys.TREASURE_TOTEM_ENABLED)) return;
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.CONDUIT) return;
        if (!item.hasItemMeta()) return;

        if (!item.getItemMeta().getPersistentDataContainer()
                .has(plugin.getItemManager().TREASURE_TOTEM_KEY, PersistentDataType.BYTE)) {
            return;
        }

        // Cancel vanilla conduit placement
        event.setCancelled(true);

        Player player = event.getPlayer();

        // Spawn totem at the block location
        plugin.getTotemManager().spawnTotem(event.getBlock().getLocation(), player);

        // Consume the item
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItem(event.getHand(), null);
        }

        player.sendMessage(plugin.getLanguageManager().getMessage("totemlistener.you_placed_a", "You placed a ").color(NamedTextColor.GRAY)
                .append(plugin.getLanguageManager().getMessage("totemlistener.treasure_totem", "Treasure Totem").color(NamedTextColor.GOLD))
            .append(plugin.getLanguageManager().getMessage("totemlistener.exclamation", "!").color(NamedTextColor.GRAY)));
    }

    // ── Breaking (Interaction entity click) ───────────────────

    /**
     * Right-click on the Interaction entity to break the totem.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.isFeatureEnabled(FeatureKeys.TREASURE_TOTEM_ENABLED)) return;
        Entity clicked = event.getRightClicked();
        if (!plugin.getTotemManager().isTotemInteraction(clicked)) return;

        event.setCancelled(true);
        breakTotemForPlayer(event.getPlayer(), clicked);
    }

    /**
     * Left-click (attack) on the Interaction entity to break the totem.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!plugin.isFeatureEnabled(FeatureKeys.TREASURE_TOTEM_ENABLED)) return;
        Entity target = event.getEntity();
        if (!plugin.getTotemManager().isTotemInteraction(target)) return;

        event.setCancelled(true);

        if (event.getDamager() instanceof Player player) {
            breakTotemForPlayer(player, target);
        }
    }

    private void breakTotemForPlayer(Player player, Entity interactionEntity) {
        plugin.getTotemManager().breakTotem(interactionEntity.getUniqueId(), player);
        player.sendMessage(plugin.getLanguageManager().getMessage("totemlistener.you_broke_a", "You broke a ").color(NamedTextColor.GRAY)
                .append(plugin.getLanguageManager().getMessage("totemlistener.treasure_totem", "Treasure Totem").color(NamedTextColor.GOLD))
            .append(plugin.getLanguageManager().getMessage("totemlistener.exclamation", "!").color(NamedTextColor.GRAY)));
    }

    // ── Chunk Persistence ─────────────────────────────────────

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.isFeatureEnabled(FeatureKeys.TREASURE_TOTEM_ENABLED)) return;
        // Delay by 1 tick to ensure entities are fully loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Chunk chunk = event.getChunk();
            if (chunk.isLoaded()) {
                plugin.getTotemManager().scanChunk(chunk);
            }
        }, 1L);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!plugin.isFeatureEnabled(FeatureKeys.TREASURE_TOTEM_ENABLED)) return;
        plugin.getTotemManager().evictChunk(event.getChunk());
    }
}

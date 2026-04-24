package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.model.PlayerData;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
        if (!(event.getEntity() instanceof Player player)) return;

        plugin.getLanguageManager().withPlayer(player, () -> {
            ItemStack item = event.getItem().getItemStack();
            ItemStack refreshed = refreshFromRegistry(item);
            if (refreshed != null) {
                event.getItem().setItemStack(refreshed);
            }
        });
    }

    @EventHandler
    public void onJoinRefreshEquipment(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                plugin.getLanguageManager().withPlayer(player, () -> refreshPlayerCustomItems(player)), 2L);
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
                plugin.getLanguageManager().withPlayer(player, () -> {
                    plugin.getLoreManager().updateLore(held);
                    player.getInventory().setItemInMainHand(held);
                });
            }
        }, 1L);
    }

    /** Refreshes lore when a player closes their inventory — catches /give, plugin-added enchants, etc. */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        plugin.getLanguageManager().withPlayer(player, () -> refreshPlayerCustomItems(player));
    }

    public void refreshPlayerCustomItems(Player player) {
        if (player == null || !player.isOnline()) return;
        plugin.getLanguageManager().withPlayer(player, () -> refreshPlayerCustomItemsLocalized(player));
    }

    private void refreshPlayerCustomItemsLocalized(Player player) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());

        ItemStack[] storage = player.getInventory().getStorageContents();
        boolean storageChanged = false;
        for (int i = 0; i < storage.length; i++) {
            ItemStack refreshed = refreshFromRegistry(storage[i]);
            if (refreshed != null) {
                storage[i] = refreshed;
                storageChanged = true;
            }
        }
        if (storageChanged) {
            player.getInventory().setStorageContents(storage);
        }

        refreshEquippedSlot(player, org.bukkit.inventory.EquipmentSlot.HEAD);
        refreshEquippedSlot(player, org.bukkit.inventory.EquipmentSlot.CHEST);
        refreshEquippedSlot(player, org.bukkit.inventory.EquipmentSlot.LEGS);
        refreshEquippedSlot(player, org.bukkit.inventory.EquipmentSlot.FEET);
        refreshEquippedSlot(player, org.bukkit.inventory.EquipmentSlot.HAND);
        refreshEquippedSlot(player, org.bukkit.inventory.EquipmentSlot.OFF_HAND);

        if (data != null) {
            data.setFishBagContents(refreshBagContents(data.getFishBagContents()));
            data.setLavaBagContents(refreshBagContents(data.getLavaBagContents()));
        }
    }

    private void refreshEquippedSlot(Player player, org.bukkit.inventory.EquipmentSlot slot) {
        if (player.getEquipment() == null) return;
        ItemStack current = player.getEquipment().getItem(slot);
        ItemStack refreshed = refreshFromRegistry(current);
        if (refreshed == null) {
            if (current != null && !current.getType().isAir() && current.hasItemMeta()) {
                plugin.getLoreManager().updateLore(current);
                player.getEquipment().setItem(slot, current);
            }
            return;
        }
        player.getEquipment().setItem(slot, refreshed);
    }

    private ItemStack[] refreshBagContents(ItemStack[] contents) {
        if (contents == null) return null;

        ItemStack[] refreshedContents = contents.clone();
        boolean changed = false;
        for (int i = 0; i < refreshedContents.length; i++) {
            ItemStack refreshed = refreshFromRegistry(refreshedContents[i]);
            if (refreshed != null) {
                refreshedContents[i] = refreshed;
                changed = true;
            }
        }
        return changed ? refreshedContents : contents;
    }

    private ItemStack refreshFromRegistry(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;

        ItemMeta oldMeta = item.getItemMeta();
        var pdc = oldMeta.getPersistentDataContainer();

        String baitId = pdc.get(plugin.getItemManager().BAIT_KEY, PersistentDataType.STRING);
        if (baitId != null && !baitId.isBlank()) {
            ItemStack freshBait = rebuildBaitItem(item, oldMeta, baitId);
            if (freshBait != null) {
                return freshBait;
            }
        }

        String id = pdc.get(
                plugin.getItemManager().CUSTOM_ITEM_KEY, PersistentDataType.STRING);
        if (id != null) {
            Supplier<ItemStack> supplier = plugin.getItemManager().getItemRegistry().get(id);
            if (supplier != null) {
                ItemStack fresh = supplier.get();
                if (fresh != null) {
                    return finalizeRefreshedItem(item, fresh);
                }
            }
        }

        String artifactId = pdc.get(
                plugin.getItemManager().ARTIFACT_KEY, PersistentDataType.STRING);
        if (artifactId != null) {
            com.fishrework.model.Artifact artifact = plugin.getArtifactRegistry().get(artifactId);
            if (artifact == null) return null;

            return finalizeRefreshedItem(item, plugin.getItemManager().createArtifactItem(artifact));
        }

        String treasureType = pdc.get(plugin.getItemManager().TREASURE_TYPE_KEY, PersistentDataType.STRING);
        if (treasureType != null && !treasureType.isBlank()) {
            boolean nether = treasureType.toUpperCase(Locale.ROOT).startsWith("NETHER_");
            String rarityName = nether ? treasureType.substring("NETHER_".length()) : treasureType;
            com.fishrework.model.Rarity rarity = parseRarity(rarityName);
            ItemStack freshTreasure = nether
                    ? plugin.getItemManager().getNetherTreasure(rarity)
                    : plugin.getItemManager().getTreasure(rarity);
            return finalizeRefreshedItem(item, freshTreasure);
        }

        return null;
    }

    private ItemStack rebuildBaitItem(ItemStack original, ItemMeta oldMeta, String baitId) {
        String normalized = baitId.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("biome_bait:")) {
            String biomeBaitId = baitId.substring("biome_bait:".length());
            List<String> targetMobIds = decodeCsv(oldMeta.getPersistentDataContainer().get(
                    plugin.getItemManager().BAIT_TARGET_MOBS_KEY,
                    PersistentDataType.STRING));
            List<com.fishrework.model.BiomeGroup> nativeGroups = decodeBiomeGroups(oldMeta.getPersistentDataContainer().get(
                    plugin.getItemManager().BAIT_NATIVE_BIOME_GROUPS_KEY,
                    PersistentDataType.STRING));
            String fallbackName = plainDisplayName(oldMeta, com.fishrework.registry.RecipeDefinition.toFriendlyName(biomeBaitId));
            ItemStack fresh = plugin.getItemManager().createBiomeBaitItem(
                    biomeBaitId,
                    fallbackName,
                    original.getType(),
                    parseRarity(oldMeta.getPersistentDataContainer().get(plugin.getItemManager().RARITY_KEY, PersistentDataType.STRING)),
                    targetMobIds,
                    nativeGroups);
            return finalizeRefreshedItem(original, fresh);
        }

        if (normalized.startsWith("hostile_bait:")) {
            String mobId = baitId.substring("hostile_bait:".length());
            com.fishrework.model.CustomMob mob = plugin.getMobRegistry().get(mobId);
            if (mob == null) {
                return null;
            }
            return finalizeRefreshedItem(original, plugin.getItemManager().createSeaCreatureBaitItem(mob));
        }

        com.fishrework.model.Bait bait = plugin.getBaitRegistry().get(baitId);
        if (bait == null) {
            return null;
        }
        return finalizeRefreshedItem(original, plugin.getItemManager().createBaitItem(bait));
    }

    private ItemStack finalizeRefreshedItem(ItemStack original, ItemStack fresh) {
        if (fresh == null) return null;

        fresh.setAmount(original.getAmount());

        for (var ench : original.getEnchantments().entrySet()) {
            fresh.addUnsafeEnchantment(ench.getKey(), ench.getValue());
        }

        ItemMeta oldMeta = original.getItemMeta();
        ItemMeta freshMeta = fresh.getItemMeta();
        if (oldMeta != null && freshMeta != null) {
            oldMeta.getPersistentDataContainer().copyTo(freshMeta.getPersistentDataContainer(), false);
        }
        if (oldMeta instanceof Damageable oldDamageable && freshMeta instanceof Damageable freshDamageable) {
            freshDamageable.setDamage(oldDamageable.getDamage());
        }
        if (freshMeta != null) {
            fresh.setItemMeta(freshMeta);
        }

        plugin.getLoreManager().updateLore(fresh);
        return fresh;
    }

    private List<String> decodeCsv(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Collections.emptyList();
        }

        String[] pieces = encoded.split(",");
        List<String> out = new ArrayList<>(pieces.length);
        for (String piece : pieces) {
            if (piece != null && !piece.isBlank()) {
                out.add(piece.trim());
            }
        }
        return out;
    }

    private List<com.fishrework.model.BiomeGroup> decodeBiomeGroups(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Collections.emptyList();
        }

        List<com.fishrework.model.BiomeGroup> groups = new ArrayList<>();
        for (String piece : encoded.split(",")) {
            if (piece == null || piece.isBlank()) continue;
            try {
                groups.add(com.fishrework.model.BiomeGroup.valueOf(piece.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Ignore stale biome group values from older item data.
            }
        }
        return groups;
    }

    private com.fishrework.model.Rarity parseRarity(String raw) {
        if (raw == null || raw.isBlank()) {
            return com.fishrework.model.Rarity.COMMON;
        }
        try {
            return com.fishrework.model.Rarity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return com.fishrework.model.Rarity.COMMON;
        }
    }

    private String plainDisplayName(ItemMeta meta, String fallback) {
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(meta.displayName());
        }
        return fallback;
    }
}

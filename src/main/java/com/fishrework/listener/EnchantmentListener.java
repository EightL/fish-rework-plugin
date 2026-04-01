package com.fishrework.listener;

import com.fishrework.FishRework;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class EnchantmentListener implements Listener {
    private static final List<Enchantment> TRIDENT_DAMAGE_ENCHANTS = List.of(
            Enchantment.SHARPNESS,
            Enchantment.SMITE,
            Enchantment.BANE_OF_ARTHROPODS
    );

    private final FishRework plugin;
    private final NamespacedKey SEA_CREATURE_CHANCE_KEY;
    private final NamespacedKey SHOTGUN_VOLLEY_KEY;
    private final NamespacedKey REQUIRED_ADVANCEMENT_KEY;

    private final int fishingEnchantMaxLevel;
    private final int tableEnchantCap;

    public EnchantmentListener(FishRework plugin) {
        this.plugin = plugin;
        this.SEA_CREATURE_CHANCE_KEY = new NamespacedKey("fishrework", "sea_creature_chance");
        this.SHOTGUN_VOLLEY_KEY = new NamespacedKey("fishrework", "shotgun_volley");
        // Fishing Level 20 Advancement
        this.REQUIRED_ADVANCEMENT_KEY = new NamespacedKey(plugin, "fishing/level_20");
        this.fishingEnchantMaxLevel = plugin.getConfig().getInt("enchantments.sea_creature_chance_max_level", 6);
        this.tableEnchantCap = plugin.getConfig().getInt("enchantments.table_max_level", 3);
    }

    @EventHandler
    public void onEnchantPrepare(PrepareItemEnchantEvent event) {
        EnchantmentOffer[] offers = event.getOffers();
        for (int i = 0; i < offers.length; i++) {
            EnchantmentOffer offer = offers[i];
            if (offer == null) continue;

            if (offer.getEnchantment().getKey().equals(SEA_CREATURE_CHANCE_KEY)) {
                if (!event.getEnchanter().getAdvancementProgress(event.getEnchanter().getServer().getAdvancement(REQUIRED_ADVANCEMENT_KEY)).isDone()) {
                    offers[i] = null;
                } else {
                    offer.setEnchantmentLevel(Math.min(tableEnchantCap, Math.min(fishingEnchantMaxLevel, offer.getEnchantmentLevel())));
                }
                continue;
            }
            if (offer.getEnchantment().equals(Enchantment.LURE) || offer.getEnchantment().equals(Enchantment.LUCK_OF_THE_SEA)) {
                // Higher levels are custom progression and must be fused elsewhere.
                offer.setEnchantmentLevel(Math.min(tableEnchantCap, offer.getEnchantmentLevel()));
            }
        }
    }

    /**
     * Prevents combining Luck of the Sea and Sea Creature Chance on the same item via anvil.
     * Also allows Sharpness/Smite/Bane to be applied to tridents (vanilla doesn't permit this).
     */
    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        org.bukkit.inventory.AnvilInventory inv = event.getInventory();
        Player player = event.getViewers().stream()
            .filter(Player.class::isInstance)
            .map(Player.class::cast)
            .findFirst()
            .orElse(null);
        ItemStack firstSlot = inv.getItem(0);
        ItemStack secondSlot = inv.getItem(1);

        // ── Allow weapon damage enchants on Tridents ──
        if (firstSlot != null && firstSlot.getType() == org.bukkit.Material.TRIDENT
                && secondSlot != null && secondSlot.getType() == org.bukkit.Material.ENCHANTED_BOOK) {
            org.bukkit.inventory.meta.EnchantmentStorageMeta bookMeta =
                    (org.bukkit.inventory.meta.EnchantmentStorageMeta) secondSlot.getItemMeta();
            Enchantment damageEnchant = getStoredTridentDamageEnchant(bookMeta);
            if (damageEnchant != null) {
                if (hasConflictingTridentDamageEnchant(firstSlot, damageEnchant)) {
                    return;
                }

                int enchantLevel = bookMeta.getStoredEnchantLevel(damageEnchant);

                // Build result: clone the trident and add the requested damage enchant.
                ItemStack result = event.getResult();
                if (result == null || result.getType() == org.bukkit.Material.AIR) {
                    result = firstSlot.clone();
                }
                result.addUnsafeEnchantment(damageEnchant, enchantLevel);

                // Transfer any other stored enchantments from the book
                for (java.util.Map.Entry<Enchantment, Integer> entry : bookMeta.getStoredEnchants().entrySet()) {
                    if (TRIDENT_DAMAGE_ENCHANTS.contains(entry.getKey())) continue;
                    if (entry.getKey().canEnchantItem(result) || result.containsEnchantment(entry.getKey())) {
                        result.addUnsafeEnchantment(entry.getKey(), entry.getValue());
                    }
                }

                event.setResult(result);
                inv.setRepairCost(enchantLevel * 2); // Scale cost with level
                return;
            }
        }

        // ── Block Luck of the Sea + Sea Creature Chance ──
        ItemStack result = event.getResult();
        if (result == null || !result.hasItemMeta()) return;

        Enchantment seaCreature = org.bukkit.Registry.ENCHANTMENT.get(SEA_CREATURE_CHANCE_KEY);
        if (seaCreature == null) return;

        // Custom fishing enchant fusion (> table cap) is handled in Upgrade Gear GUI.
        if (isCustomFusionResult(result, seaCreature)) {
            event.setResult(null);
            if (player != null) {
            player.sendActionBar(Component.text("Use Upgrade Gear GUI to fuse custom fishing enchants.")
                        .color(NamedTextColor.RED));
            }
            return;
        }

        boolean hasLuck = result.containsEnchantment(Enchantment.LUCK_OF_THE_SEA);
        boolean hasSeaCreature = result.containsEnchantment(seaCreature);

        if (hasLuck && hasSeaCreature) {
            event.setResult(null); // Block the combination
        }

        Enchantment shotgunVolley = org.bukkit.Registry.ENCHANTMENT.get(SHOTGUN_VOLLEY_KEY);
        if (shotgunVolley == null) return;

        boolean hasMultishot = result.containsEnchantment(Enchantment.MULTISHOT);
        boolean hasShotgunVolley = result.containsEnchantment(shotgunVolley);
        if (hasMultishot && hasShotgunVolley) {
            event.setResult(null);
            if (player != null) {
                player.sendActionBar(Component.text("Shotgun Volley cannot be combined with Multishot.")
                        .color(NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchantCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("minecraft.command.enchant")) return;

        String message = event.getMessage();
        if (message == null || !message.toLowerCase().startsWith("/enchant ")) return;

        String[] parts = message.trim().split("\\s+");
        if (parts.length < 3) return;

        if (!isSelfEnchantTarget(player, parts[1])) return;

        Enchantment enchantment = resolveTridentDamageEnchant(parts[2]);
        if (enchantment == null) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() != org.bukkit.Material.TRIDENT) return;

        if (hasConflictingTridentDamageEnchant(held, enchantment)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("That trident already has a conflicting damage enchant.")
                    .color(NamedTextColor.RED));
            return;
        }

        int level = 1;
        if (parts.length >= 4) {
            try {
                level = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ignored) {
                return;
            }
        }
        if (level <= 0) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Enchant level must be at least 1.")
                    .color(NamedTextColor.RED));
            return;
        }

        held.addUnsafeEnchantment(enchantment, level);
        plugin.getLoreManager().updateLore(held);
        player.getInventory().setItemInMainHand(held);

        event.setCancelled(true);
        player.sendMessage(Component.text("Applied " + formatEnchantName(enchantment) + " " + level + " to your trident.")
                .color(NamedTextColor.GREEN));
    }

    private boolean isCustomFusionResult(ItemStack result, Enchantment seaCreature) {
        return
                result.getEnchantmentLevel(Enchantment.LURE) > tableEnchantCap
                        || result.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA) > tableEnchantCap
                        || result.getEnchantmentLevel(seaCreature) > tableEnchantCap;
    }

    private Enchantment getStoredTridentDamageEnchant(org.bukkit.inventory.meta.EnchantmentStorageMeta bookMeta) {
        if (bookMeta == null) return null;
        for (Enchantment enchantment : TRIDENT_DAMAGE_ENCHANTS) {
            if (bookMeta.hasStoredEnchant(enchantment)) {
                return enchantment;
            }
        }
        return null;
    }

    private boolean hasConflictingTridentDamageEnchant(ItemStack item, Enchantment requested) {
        for (Enchantment enchantment : TRIDENT_DAMAGE_ENCHANTS) {
            if (enchantment.equals(requested)) continue;
            if (item.containsEnchantment(enchantment)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSelfEnchantTarget(Player player, String targetArg) {
        if (targetArg == null) return false;
        return switch (targetArg.toLowerCase()) {
            case "@s", "@p" -> true;
            default -> targetArg.equalsIgnoreCase(player.getName());
        };
    }

    private Enchantment resolveTridentDamageEnchant(String enchantArg) {
        if (enchantArg == null) return null;
        String key = enchantArg.toLowerCase();
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }

        return switch (key) {
            case "sharpness", "damage_all" -> Enchantment.SHARPNESS;
            case "smite", "damage_undead" -> Enchantment.SMITE;
            case "bane_of_arthropods", "baneofarthropods", "damage_arthropods", "bane" ->
                    Enchantment.BANE_OF_ARTHROPODS;
            default -> null;
        };
    }

    private String formatEnchantName(Enchantment enchantment) {
        if (Enchantment.SHARPNESS.equals(enchantment)) return "Sharpness";
        if (Enchantment.SMITE.equals(enchantment)) return "Smite";
        if (Enchantment.BANE_OF_ARTHROPODS.equals(enchantment)) return "Bane of Arthropods";
        return enchantment.getKey().getKey();
    }
}

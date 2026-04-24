package com.fishrework.listener;

import com.fishrework.FishRework;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;

public class EnchantmentListener implements Listener {
    private static final Material SWORD_FALLBACK_MATERIAL = Material.NETHERITE_SWORD;

    private final FishRework plugin;
    private final NamespacedKey SEA_CREATURE_CHANCE_KEY;
    private final NamespacedKey REQUIRED_ADVANCEMENT_KEY;

    private final int fishingEnchantMaxLevel;
    private final int tableEnchantCap;

    public EnchantmentListener(FishRework plugin) {
        this.plugin = plugin;
        this.SEA_CREATURE_CHANCE_KEY = new NamespacedKey("fishrework", "sea_creature_chance");
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

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack base = event.getInventory().getItem(0);
        ItemStack addition = event.getInventory().getItem(1);
        ItemStack result = event.getResult();
        int repairCost = event.getInventory().getRepairCost();
        boolean changed = false;

        AnvilResult customTridentResult = extendCustomTridentResult(base, addition, result, repairCost);
        if (customTridentResult.changed()) {
            result = customTridentResult.result();
            repairCost = customTridentResult.repairCost();
            changed = true;
        }

        ItemStack sanitizedResult = sanitizeRestrictedFishingFusion(base, addition, result);
        if (sanitizedResult != result) {
            result = sanitizedResult;
            changed = true;
        }

        if (!changed) {
            return;
        }

        if (result != null && !result.getType().isAir() && plugin.getItemManager().isCustomItem(result)) {
            plugin.getLoreManager().updateLore(result);
        }
        event.setResult(result);
        if (result == null || result.getType().isAir()) {
            return;
        }
        event.getInventory().setRepairCost(repairCost);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnchantCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        plugin.getLanguageManager().withPlayer(player, () -> handleEnchantCommand(event, player));
    }

    private void handleEnchantCommand(PlayerCommandPreprocessEvent event, Player player) {
        if (!player.hasPermission("minecraft.command.enchant")) return;

        String message = event.getMessage();
        if (message == null || !message.toLowerCase().startsWith("/enchant ")) return;

        String[] parts = message.trim().split("\\s+");
        if (parts.length < 3) return;
        if (!isSelfEnchantTarget(player, parts[1])) return;

        Enchantment enchantment = resolveEnchant(parts[2]);
        if (enchantment == null || !isSwordOnlyEnchant(enchantment)) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isCustomTrident(held)) return;
        if (hasConflictingEnchant(held, enchantment)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLanguageManager().getMessage("enchantmentlistener.that_trident_already_has_a", "That trident already has a conflicting enchantment.")
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
            player.sendMessage(plugin.getLanguageManager().getMessage("enchantmentlistener.enchant_level_must_be_at", "Enchant level must be at least 1.")
                    .color(NamedTextColor.RED));
            return;
        }

        held.addUnsafeEnchantment(enchantment, level);
        plugin.getLoreManager().updateLore(held);
        player.getInventory().setItemInMainHand(held);

        event.setCancelled(true);
        player.sendMessage(Component.text(plugin.getLanguageManager().getString(
                        "enchantmentlistener.applied_to_trident",
                        "Applied %enchant% %level% to your trident.",
                        "enchant", formatEnchantName(enchantment),
                        "level", String.valueOf(level)))
                .color(NamedTextColor.GREEN));
    }

    private boolean isCustomTrident(ItemStack item) {
        return item != null
                && item.getType() == Material.TRIDENT
                && plugin.getItemManager().isCustomItem(item);
    }

    private AnvilResult extendCustomTridentResult(ItemStack base, ItemStack addition, ItemStack result, int repairCost) {
        if (!isCustomTrident(base)) {
            return new AnvilResult(result, repairCost, false);
        }
        if (addition == null || addition.getType() != Material.ENCHANTED_BOOK || !addition.hasItemMeta()) {
            return new AnvilResult(result, repairCost, false);
        }
        if (!(addition.getItemMeta() instanceof EnchantmentStorageMeta bookMeta)) {
            return new AnvilResult(result, repairCost, false);
        }

        ItemStack working = (result == null || result.getType().isAir()) ? base.clone() : result.clone();
        boolean changed = false;
        int addedCost = 0;

        for (var entry : bookMeta.getStoredEnchants().entrySet()) {
            Enchantment enchantment = entry.getKey();
            int level = entry.getValue();

            if (!shouldApplyToCustomTrident(working, enchantment, level)) {
                continue;
            }

            working.addUnsafeEnchantment(enchantment, level);
            changed = true;
            addedCost += Math.max(1, level * 2);
        }

        if (!changed) {
            return new AnvilResult(result, repairCost, false);
        }

        return new AnvilResult(working, Math.max(repairCost, Math.max(1, addedCost)), true);
    }

    private ItemStack sanitizeRestrictedFishingFusion(ItemStack base, ItemStack addition, ItemStack result) {
        if (base == null || base.getType().isAir() || addition == null || addition.getType().isAir()
                || result == null || result.getType().isAir()) {
            return result;
        }

        ItemStack sanitized = null;
        for (Enchantment enchantment : getRestrictedFishingFusionEnchants()) {
            int resultLevel = getEnchantLevel(result, enchantment);
            if (resultLevel <= tableEnchantCap) {
                continue;
            }

            int inputMaxLevel = Math.max(getEnchantLevel(base, enchantment), getEnchantLevel(addition, enchantment));
            if (resultLevel <= inputMaxLevel) {
                continue;
            }

            if (sanitized == null) {
                sanitized = result.clone();
            }
            setEnchantLevel(sanitized, enchantment, inputMaxLevel);
        }

        if (sanitized == null) {
            return result;
        }

        if (isSameItem(base, sanitized)) {
            return null;
        }
        return sanitized;
    }

    private List<Enchantment> getRestrictedFishingFusionEnchants() {
        List<Enchantment> enchantments = new ArrayList<>(3);
        enchantments.add(Enchantment.LURE);
        enchantments.add(Enchantment.LUCK_OF_THE_SEA);

        Enchantment seaCreatureChance = Registry.ENCHANTMENT.get(SEA_CREATURE_CHANCE_KEY);
        if (seaCreatureChance != null) {
            enchantments.add(seaCreatureChance);
        }
        return enchantments;
    }

    private int getEnchantLevel(ItemStack item, Enchantment enchantment) {
        if (item == null || enchantment == null || !item.hasItemMeta()) {
            return 0;
        }
        if (item.getType() == Material.ENCHANTED_BOOK && item.getItemMeta() instanceof EnchantmentStorageMeta bookMeta) {
            return bookMeta.getStoredEnchantLevel(enchantment);
        }
        return item.getEnchantmentLevel(enchantment);
    }

    private void setEnchantLevel(ItemStack item, Enchantment enchantment, int level) {
        if (item == null || enchantment == null || !item.hasItemMeta()) {
            return;
        }

        if (item.getType() == Material.ENCHANTED_BOOK && item.getItemMeta() instanceof EnchantmentStorageMeta bookMeta) {
            bookMeta.removeStoredEnchant(enchantment);
            if (level > 0) {
                bookMeta.addStoredEnchant(enchantment, level, true);
            }
            item.setItemMeta(bookMeta);
            return;
        }

        item.removeEnchantment(enchantment);
        if (level > 0) {
            item.addUnsafeEnchantment(enchantment, level);
        }
    }

    private boolean isSameItem(ItemStack first, ItemStack second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }

        ItemStack firstCopy = first.clone();
        firstCopy.setAmount(1);
        ItemStack secondCopy = second.clone();
        secondCopy.setAmount(1);
        return firstCopy.isSimilar(secondCopy);
    }

    private boolean shouldApplyToCustomTrident(ItemStack item, Enchantment enchantment, int level) {
        if (enchantment == null || item == null || level <= 0) {
            return false;
        }
        if (item.getEnchantmentLevel(enchantment) >= level) {
            return false;
        }
        if (hasConflictingEnchant(item, enchantment)) {
            return false;
        }
        return enchantment.canEnchantItem(item) || isSwordOnlyEnchant(enchantment);
    }

    private boolean isSwordOnlyEnchant(Enchantment enchantment) {
        return enchantment != null
                && enchantment.canEnchantItem(new ItemStack(SWORD_FALLBACK_MATERIAL))
                && !enchantment.canEnchantItem(new ItemStack(Material.TRIDENT));
    }

    private boolean hasConflictingEnchant(ItemStack item, Enchantment requested) {
        if (item == null || requested == null) return false;
        for (Enchantment enchantment : item.getEnchantments().keySet()) {
            if (enchantment.equals(requested)) continue;
            if (enchantment.conflictsWith(requested) || requested.conflictsWith(enchantment)) {
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

    private Enchantment resolveEnchant(String enchantArg) {
        if (enchantArg == null) return null;

        String normalized = enchantArg.trim().toLowerCase();
        NamespacedKey namespaced = normalized.contains(":")
                ? NamespacedKey.fromString(normalized)
                : NamespacedKey.minecraft(normalized);
        if (namespaced != null) {
            Enchantment direct = Registry.ENCHANTMENT.get(namespaced);
            if (direct != null) {
                return direct;
            }
        }

        String key = normalized.startsWith("minecraft:")
                ? normalized.substring("minecraft:".length())
                : normalized;
        return switch (key) {
            case "sharpness", "damage_all" -> Enchantment.SHARPNESS;
            case "smite", "damage_undead" -> Enchantment.SMITE;
            case "bane_of_arthropods", "baneofarthropods", "damage_arthropods", "bane" ->
                    Enchantment.BANE_OF_ARTHROPODS;
            default -> null;
        };
    }

    private String formatEnchantName(Enchantment enchantment) {
        String[] parts = enchantment.getKey().getKey().split("_");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) builder.append(' ');
            if (parts[i].isEmpty()) continue;
            builder.append(Character.toUpperCase(parts[i].charAt(0)));
            if (parts[i].length() > 1) {
                builder.append(parts[i].substring(1));
            }
        }
        return builder.toString();
    }

    private record AnvilResult(ItemStack result, int repairCost, boolean changed) {}
}

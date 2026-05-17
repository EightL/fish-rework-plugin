package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.manager.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Enchant fusion table split out from the upgrade bench.
 */
public class BookMergingGUI extends BaseGUI {

    private static final int LEFT_SLOT = 10;
    private static final int RIGHT_SLOT = 11;
    private static final int CATALYST_SLOT = 12;
    private static final int ARROW_SLOT = 14;
    private static final int RESULT_SLOT = 16;
    private static final int CLOSE_SLOT = 22;

    private final Player player;
    private final NamespacedKey seaCreatureChanceKey = new NamespacedKey("fishrework", "sea_creature_chance");

    private ItemStack placedLeft = null;
    private ItemStack placedRight = null;
    private ItemStack placedCatalyst = null;
    private int levelCost = 0;
    private long lastResultClick = 0;

    public BookMergingGUI(FishRework plugin, Player player) {
        super(plugin, 3, localizedTitle(plugin, "bookmerginggui.title", "Book Merging"));
        this.player = player;
        initializeItems();
    }

    @Override
    public boolean handlesPlayerInventoryClicks() {
        return true;
    }

    @Override
    public void onDrag(InventoryDragEvent event) {
        for (int slot : event.getRawSlots()) {
            if (slot < inventory.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
        event.setCancelled(false);
    }

    private void initializeItems() {
        fillArcaneBackground();

        inventory.setItem(LEFT_SLOT, createPlaceholder(Material.GREEN_STAINED_GLASS_PANE,
                "Fusion Input", "Place an enchanted book", "or item with a III-V enchant."));
        inventory.setItem(RIGHT_SLOT, createPlaceholder(Material.GREEN_STAINED_GLASS_PANE,
                "Matching Input", "Place the same item type", "with matching enchant + level."));
        inventory.setItem(CATALYST_SLOT, createPlaceholder(Material.ORANGE_STAINED_GLASS_PANE,
                "Catalyst Slot", "Place Arcane Formula here.", "Required for III->IV fusion."));

        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta arrowMeta = arrow.getItemMeta();
        arrowMeta.displayName(plugin.getLanguageManager().getMessage("seacreatureupgradegui.arrow_indicator", "->")
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        arrow.setItemMeta(arrowMeta);
        inventory.setItem(ARROW_SLOT, arrow);

        inventory.setItem(RESULT_SLOT, createPlaceholder(Material.BLACK_STAINED_GLASS_PANE,
                "Fusion Result", "III->IV costs 30 levels", "IV->V 40, V->VI 50"));

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(plugin.getLanguageManager().getMessage("bookmerginggui.close", "Close")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(closeMeta);
        inventory.setItem(CLOSE_SLOT, close);
    }

    private void fillArcaneBackground() {
        Material[] pattern = {
                Material.PURPLE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE,
                Material.BLUE_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE,
                Material.BLUE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.MAGENTA_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE
        };
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, decorativePane(pattern[i]));
        }
    }

    private ItemStack decorativePane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPlaceholder(Material mat, String title, String line1, String line2) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(title).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(line1).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(line2).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        int topSize = inventory.getSize();

        if (slot < 0) return;

        if (slot >= topSize) {
            if (event.isShiftClick() && event.getCurrentItem() != null) {
                event.setCancelled(true);
                ItemStack clicked = event.getCurrentItem();
                int targetSlot = findTargetSlot(clicked);
                if (targetSlot >= 0) {
                    placeItem(targetSlot, clicked.clone());
                    event.getCurrentItem().setAmount(0);
                    updateResult();
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                }
                return;
            }
            event.setCancelled(false);
            return;
        }

        event.setCancelled(true);

        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        if (slot == LEFT_SLOT || slot == RIGHT_SLOT || slot == CATALYST_SLOT) {
            handleInputSlotClick(slot, event);
            return;
        }

        if (slot == RESULT_SLOT) {
            handleResultClick();
        }
    }

    private void handleInputSlotClick(int slot, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack existing = getPlacedItem(slot);

        if (existing != null) {
            if (cursor != null && !cursor.getType().isAir()) {
                if (isValidForSlot(slot, cursor)) {
                    returnItem(slot);
                    placeItem(slot, cursor.clone());
                    event.getView().setCursor(existing);
                    updateResult();
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                } else {
                    player.sendMessage(plugin.getLanguageManager().getMessage("seacreatureupgradegui.that_item_doesnt_go_in", "That item doesn't go in this slot!").color(NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                }
            } else {
                returnItem(slot);
                event.getView().setCursor(existing);
                updateResult();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            }
        } else if (cursor != null && !cursor.getType().isAir()) {
            if (isValidForSlot(slot, cursor)) {
                placeItem(slot, cursor.clone());
                event.getView().setCursor(null);
                updateResult();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            } else {
                player.sendMessage(plugin.getLanguageManager().getMessage("seacreatureupgradegui.that_item_doesnt_go_in", "That item doesn't go in this slot!").color(NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            }
        }
    }

    private int findTargetSlot(ItemStack item) {
        if (isValidForSlot(LEFT_SLOT, item) && placedLeft == null) return LEFT_SLOT;
        if (isValidForSlot(RIGHT_SLOT, item) && placedRight == null) return RIGHT_SLOT;
        if (isValidForSlot(CATALYST_SLOT, item) && placedCatalyst == null) return CATALYST_SLOT;
        return -1;
    }

    private boolean isValidForSlot(int slot, ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemManager itemManager = plugin.getItemManager();

        return switch (slot) {
            case LEFT_SLOT, RIGHT_SLOT -> isFusionItem(item);
            case CATALYST_SLOT -> itemManager.isCustomItem(item, "arcane_formula")
                    || itemManager.isCustomItem(item, "enchanted_arcane_formula")
                    || itemManager.isCustomItem(item, "super_enchanted_arcane_formula");
            default -> false;
        };
    }

    private boolean isFusionItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        Enchantment seaCreature = org.bukkit.Registry.ENCHANTMENT.get(seaCreatureChanceKey);
        if (seaCreature == null) return false;
        return hasLevelInRange(item, Enchantment.LURE, 3, 5)
                || hasLevelInRange(item, Enchantment.LUCK_OF_THE_SEA, 3, 5)
                || hasLevelInRange(item, seaCreature, 3, 5);
    }

    private boolean hasLevelInRange(ItemStack item, Enchantment enchantment, int min, int max) {
        int level = getEnchantLevel(item, enchantment);
        return level >= min && level <= max;
    }

    private void placeItem(int slot, ItemStack item) {
        switch (slot) {
            case LEFT_SLOT -> {
                placedLeft = item;
                inventory.setItem(slot, item);
            }
            case RIGHT_SLOT -> {
                placedRight = item;
                inventory.setItem(slot, item);
            }
            case CATALYST_SLOT -> {
                placedCatalyst = item;
                inventory.setItem(slot, item);
            }
            default -> {
            }
        }
    }

    private ItemStack getPlacedItem(int slot) {
        return switch (slot) {
            case LEFT_SLOT -> placedLeft;
            case RIGHT_SLOT -> placedRight;
            case CATALYST_SLOT -> placedCatalyst;
            default -> null;
        };
    }

    private void returnItem(int slot) {
        switch (slot) {
            case LEFT_SLOT -> {
                placedLeft = null;
                inventory.setItem(slot, createPlaceholder(Material.GREEN_STAINED_GLASS_PANE,
                        "Fusion Input", "Place an enchanted book", "or item with a III-V enchant."));
            }
            case RIGHT_SLOT -> {
                placedRight = null;
                inventory.setItem(slot, createPlaceholder(Material.GREEN_STAINED_GLASS_PANE,
                        "Matching Input", "Place the same item type", "with matching enchant + level."));
            }
            case CATALYST_SLOT -> {
                placedCatalyst = null;
                inventory.setItem(slot, createPlaceholder(Material.ORANGE_STAINED_GLASS_PANE,
                        "Catalyst Slot", "Place Arcane Formula here.", "Required for III->IV fusion."));
            }
            default -> {
            }
        }
    }

    private record FusionEntry(Enchantment enchantment, int toLevel) {}

    private void updateResult() {
        levelCost = 0;
        if (placedLeft == null || placedRight == null) {
            inventory.setItem(RESULT_SLOT, createPlaceholder(Material.BLACK_STAINED_GLASS_PANE,
                    "Fusion Result", "III->IV costs 30 levels", "IV->V 40, V->VI 50"));
            return;
        }

        if (placedLeft.getType() != placedRight.getType()) {
            setError("Input mismatch", "Both fusion inputs must be", "the same item type.");
            return;
        }

        Enchantment seaCreature = org.bukkit.Registry.ENCHANTMENT.get(seaCreatureChanceKey);
        if (seaCreature == null) {
            setError("Missing enchant", "Sea Creature Chance enchant", "is not registered.");
            return;
        }

        List<FusionEntry> upgrades = new ArrayList<>();
        collectFusion(upgrades, Enchantment.LURE);
        collectFusion(upgrades, Enchantment.LUCK_OF_THE_SEA);
        collectFusion(upgrades, seaCreature);

        if (upgrades.isEmpty()) {
            setError("No valid fusion", "Need same enchant + same level", "between III and V.");
            return;
        }

        int requiredEnchantLevels = 0;
        int highestTarget = 0;
        for (FusionEntry entry : upgrades) {
            int required = requiredEnchantLevelsForTarget(entry.toLevel());
            if (required > requiredEnchantLevels) requiredEnchantLevels = required;
            if (entry.toLevel() > highestTarget) highestTarget = entry.toLevel();
        }

        if (requiredEnchantLevels > 0 && player.getLevel() < requiredEnchantLevels) {
            setError("Not enough levels",
                    "Need " + requiredEnchantLevels + " levels for " + toRoman(highestTarget) + ".",
                    "Current levels: " + player.getLevel() + ".");
            return;
        }

        String requiredCatalyst = switch (highestTarget) {
            case 4 -> "arcane_formula";
            case 5 -> "enchanted_arcane_formula";
            case 6 -> "super_enchanted_arcane_formula";
            default -> null;
        };
        if (requiredCatalyst != null) {
            if (placedCatalyst == null) {
                setError("Missing Catalyst", "An arcane formula is required", "for this fusion.");
                return;
            }
            if (!plugin.getItemManager().isCustomItem(placedCatalyst, requiredCatalyst)) {
                setError("Wrong Catalyst", "Need " + requiredCatalyst.replace("_", " ") + " for", toRoman(highestTarget) + " fusion.");
                return;
            }
        }

        levelCost = requiredEnchantLevels;

        ItemStack result = placedLeft.clone();
        for (FusionEntry entry : upgrades) {
            setEnchantLevel(result, entry.enchantment(), entry.toLevel());
        }
        plugin.getLoreManager().updateLore(result);
        inventory.setItem(RESULT_SLOT, result);
    }

    private void collectFusion(List<FusionEntry> upgrades, Enchantment enchantment) {
        int leftLevel = getEnchantLevel(placedLeft, enchantment);
        int rightLevel = getEnchantLevel(placedRight, enchantment);
        if (leftLevel == rightLevel && leftLevel >= 3 && leftLevel <= 5) {
            upgrades.add(new FusionEntry(enchantment, leftLevel + 1));
        }
    }

    private int getEnchantLevel(ItemStack item, Enchantment enchantment) {
        if (item == null || enchantment == null || !item.hasItemMeta()) return 0;
        if (item.getType() == Material.ENCHANTED_BOOK && item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            return meta.getStoredEnchantLevel(enchantment);
        }
        return item.getEnchantmentLevel(enchantment);
    }

    private void setEnchantLevel(ItemStack item, Enchantment enchantment, int level) {
        if (item == null || enchantment == null || !item.hasItemMeta()) return;
        if (item.getType() == Material.ENCHANTED_BOOK && item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            meta.removeStoredEnchant(enchantment);
            meta.addStoredEnchant(enchantment, level, true);
            item.setItemMeta(meta);
            return;
        }
        item.removeEnchantment(enchantment);
        item.addUnsafeEnchantment(enchantment, level);
    }

    private int requiredEnchantLevelsForTarget(int targetLevel) {
        return switch (targetLevel) {
            case 4 -> 30;
            case 5 -> 40;
            case 6 -> 50;
            default -> 0;
        };
    }

    private String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> String.valueOf(level);
        };
    }

    private void setError(String title, String line1, String line2) {
        ItemStack errorItem = new ItemStack(Material.BARRIER);
        ItemMeta meta = errorItem.getItemMeta();
        meta.displayName(Component.text(title).color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(line1).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(line2).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        errorItem.setItemMeta(meta);
        inventory.setItem(RESULT_SLOT, errorItem);
    }

    private void handleResultClick() {
        long now = System.currentTimeMillis();
        if (now - lastResultClick < 150) return;
        lastResultClick = now;

        ItemStack resultItem = inventory.getItem(RESULT_SLOT);
        if (resultItem == null || resultItem.getType() == Material.BLACK_STAINED_GLASS_PANE
                || resultItem.getType() == Material.BARRIER) {
            return;
        }

        if (levelCost > 0) {
            if (player.getLevel() < levelCost) {
                player.sendMessage(plugin.getLanguageManager().getMessage("seacreatureupgradegui.not_enough_enchant_levels", "Not enough enchant levels.").color(NamedTextColor.RED));
                updateResult();
                return;
            }
            player.setLevel(player.getLevel() - levelCost);
        }

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(resultItem.clone());
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }

        consumeOne(LEFT_SLOT);
        consumeOne(RIGHT_SLOT);
        consumeOne(CATALYST_SLOT);
        updateResult();

        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.1f);
        player.sendMessage(plugin.getLanguageManager().getMessage("bookmerginggui.enchants_fused", "Enchants fused!").color(NamedTextColor.GREEN));
    }

    private void consumeOne(int slot) {
        ItemStack item = getPlacedItem(slot);
        if (item == null) return;

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            inventory.setItem(slot, item);
        } else {
            returnItem(slot);
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        Player closingPlayer = (Player) event.getPlayer();
        returnToPlayer(closingPlayer, placedLeft);
        returnToPlayer(closingPlayer, placedRight);
        returnToPlayer(closingPlayer, placedCatalyst);
        placedLeft = null;
        placedRight = null;
        placedCatalyst = null;
    }

    private void returnToPlayer(Player p, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(item);
        for (ItemStack drop : leftover.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), drop);
        }
    }
}

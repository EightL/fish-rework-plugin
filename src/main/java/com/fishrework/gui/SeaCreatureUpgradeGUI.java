package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.manager.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom GUI mimicking a smithing table for upgrading gear with sea creature bonuses.
 * <p>
 * Layout (3 rows = 27 slots):
 * <pre>
 *  Row 0:  [x] [x] [x] [x] [x] [x] [x] [x] [x]
 *  Row 1:  [x] [STONE] [GEAR] [TIER] [x] [→] [x] [RESULT] [x]
 *  Row 2:  [x] [x] [x] [x] [x] [x] [x] [x] [x]
 * </pre>
 * <p>
 * - STONE slot (10): Shark Tooth (+SC Attack) or Beast Scale (+SC Defense)
 * - GEAR  slot (11): The weapon/armor to upgrade
 * - TIER  slot (12): Copper/Iron/Gold/Diamond/Netherite → +1/+2/+3/+4/+5
 * - RESULT slot (16): Output preview (click to claim)
 */
public class SeaCreatureUpgradeGUI extends BaseGUI {

    private static final int STONE_SLOT  = 10;
    private static final int GEAR_SLOT   = 11;
    private static final int TIER_SLOT   = 12;
    private static final int ARROW_SLOT  = 14;
    private static final int RESULT_SLOT = 16;
    private static final int COMBINE_LEFT_SLOT = 28;
    private static final int COMBINE_RIGHT_SLOT = 29;
    private static final int COMBINE_MIDDLE_SLOT = 30;
    private static final int COMBINE_ARROW_SLOT = 32;
    private static final int COMBINE_RESULT_SLOT = 34;

    private final Player player;
    private final NamespacedKey seaCreatureChanceKey = new NamespacedKey("fishrework", "sea_creature_chance");

    // Track items placed by the player so we can return them on close
    private ItemStack placedStone = null;
    private ItemStack placedGear  = null;
    private ItemStack placedTier  = null;
    private ItemStack placedCombineLeft = null;
    private ItemStack placedCombineRight = null;
    private int combineLevelCost = 0;
    private long lastResultClick = 0;

    public SeaCreatureUpgradeGUI(FishRework plugin, Player player) {
        super(plugin, 6, "⚒ Upgrade Gear");
        this.player = player;
        initializeItems();
    }

    @Override
    public boolean handlesPlayerInventoryClicks() {
        return true;
    }

    private void initializeItems() {
        // Fill background with gray glass
        ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = fill.getItemMeta();
        fm.displayName(Component.text(" "));
        fill.setItemMeta(fm);
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, fill);
        }

        // Label slots with placeholder items
        inventory.setItem(STONE_SLOT, createPlaceholder(Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                "Upgrade Stone", "Place a Shark Tooth or", "Beast Scale here."));
        inventory.setItem(GEAR_SLOT, createPlaceholder(Material.ORANGE_STAINED_GLASS_PANE,
                "Equipment", "Place weapon or armor", "to upgrade here."));
        inventory.setItem(TIER_SLOT, createPlaceholder(Material.LIME_STAINED_GLASS_PANE,
                "Upgrade Level", "Place Copper, Iron, Gold,", "Diamond, or Netherite Ingot."));

        // Arrow indicator
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta am = arrow.getItemMeta();
        am.displayName(Component.text("→").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        arrow.setItemMeta(am);
        inventory.setItem(ARROW_SLOT, arrow);

        // Result slot (empty initially)
        inventory.setItem(RESULT_SLOT, createPlaceholder(Material.BLACK_STAINED_GLASS_PANE,
                "Result", "Place all 3 items to", "see the upgrade result."));

        // Enchant fusion row
        inventory.setItem(COMBINE_LEFT_SLOT, createBlankSlot(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        inventory.setItem(COMBINE_RIGHT_SLOT, createBlankSlot(Material.ORANGE_STAINED_GLASS_PANE));
        inventory.setItem(COMBINE_MIDDLE_SLOT, createBlankSlot(Material.LIME_STAINED_GLASS_PANE));
        ItemStack combineArrow = new ItemStack(Material.ARROW);
        ItemMeta cam = combineArrow.getItemMeta();
        cam.displayName(Component.text("→").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        combineArrow.setItemMeta(cam);
        inventory.setItem(COMBINE_ARROW_SLOT, combineArrow);
        inventory.setItem(COMBINE_RESULT_SLOT, createPlaceholder(Material.BLACK_STAINED_GLASS_PANE,
            "Fusion Result", "III->IV costs 30 levels", "IV->V 40, V->VI 50"));

        // Back button (slot 48)
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Back").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inventory.setItem(48, back);
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

    private ItemStack createBlankSlot(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    // ══════════════════════════════════════════════════════════
    //  Click handling
    // ══════════════════════════════════════════════════════════

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        // Player inventory interaction
        if (slot < 0 || slot >= 54) {
            if (event.isShiftClick() && event.getCurrentItem() != null) {
                event.setCancelled(true);
                ItemStack clicked = event.getCurrentItem();
                int targetSlot = findTargetSlot(clicked);
                if (targetSlot >= 0) {
                    placeItem(targetSlot, clicked.clone());
                    event.getCurrentItem().setAmount(0);
                    updateAllResults();
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                }
                return;
            }
            // Allow normal inventory interaction (picking up items, moving within player inv)
            event.setCancelled(false);
            return;
        }

        event.setCancelled(true);

        // Back button
        if (slot == 48) {
            new SkillDetailGUI(plugin, player, com.fishrework.model.Skill.FISHING).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        // Input slot clicks
        if (slot == STONE_SLOT || slot == GEAR_SLOT || slot == TIER_SLOT
            || slot == COMBINE_LEFT_SLOT || slot == COMBINE_RIGHT_SLOT) {
            handleInputSlotClick(slot, event);
            return;
        }

        // Result slot click
        if (slot == RESULT_SLOT) {
            handleResultClick();
            return;
        }

        if (slot == COMBINE_RESULT_SLOT) {
            handleCombineResultClick();
        }
    }

    /**
     * When clicking an input slot:
     * - If the slot has a player-placed item, return it/swap it.
     * - If the slot is empty (placeholder), place cursor item if valid.
     */
    private void handleInputSlotClick(int slot, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack existing = getPlacedItem(slot);

        // Case 1: Slot already has an item
        if (existing != null) {
            if (cursor != null && !cursor.getType().isAir()) {
                if (isValidForSlot(slot, cursor)) {
                    // Valid item on cursor - Swap them
                    returnItem(slot);
                    placeItem(slot, cursor.clone());
                    event.getView().setCursor(existing);
                    updateAllResults();
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                } else {
                    // Invalid item on cursor - Don't swap, just message
                    player.sendMessage(Component.text("That item doesn't go in this slot!").color(NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
                }
            } else {
                // Empty cursor - Pick up the existing item
                returnItem(slot);
                event.getView().setCursor(existing);
                updateAllResults();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            }
        } 
        // Case 2: Slot is empty (placeholder)
        else if (cursor != null && !cursor.getType().isAir()) {
            if (isValidForSlot(slot, cursor)) {
                // Valid item on cursor - Place it
                placeItem(slot, cursor.clone());
                event.getView().setCursor(null);
                updateAllResults();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            } else {
                // Invalid item on cursor - Message
                player.sendMessage(Component.text("That item doesn't go in this slot!").color(NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            }
        }
    }

    /**
     * Determines which input slot a shift-clicked item should go to.
     */
    private int findTargetSlot(ItemStack item) {
        if (isValidForSlot(STONE_SLOT, item) && placedStone == null) return STONE_SLOT;
        if (isValidForSlot(GEAR_SLOT, item) && placedGear == null) return GEAR_SLOT;
        if (isValidForSlot(TIER_SLOT, item) && placedTier == null) return TIER_SLOT;
        if (isValidForSlot(COMBINE_LEFT_SLOT, item) && placedCombineLeft == null) return COMBINE_LEFT_SLOT;
        if (isValidForSlot(COMBINE_RIGHT_SLOT, item) && placedCombineRight == null) return COMBINE_RIGHT_SLOT;
        return -1;
    }

    /**
     * Validates whether an item is acceptable for a given slot.
     */
    private boolean isValidForSlot(int slot, ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemManager im = plugin.getItemManager();

        return switch (slot) {
            case STONE_SLOT -> im.isCustomItem(item, "shark_tooth") || im.isCustomItem(item, "beast_scale");
            case GEAR_SLOT  -> isUpgradeableGear(item);
            case TIER_SLOT  -> getTierLevel(item.getType()) > 0;
            case COMBINE_LEFT_SLOT, COMBINE_RIGHT_SLOT -> isFusionItem(item);
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

    /**
     * Checks if an item is weapon, tool or armor that can receive sea creature upgrades.
     */
    private boolean isUpgradeableGear(ItemStack item) {
        return isWeaponOrTool(item) || isArmor(item);
    }

    private boolean isWeaponOrTool(ItemStack item) {
        if (item == null) return false;
        Material mat = item.getType();
        String name = mat.name();
        
        // Check for common weapon/tool suffixes or specific materials
        if (name.contains("SWORD") || 
            name.contains("AXE") || 
            name.contains("TRIDENT") || 
            name.contains("PICKAXE") || 
            name.contains("SHOVEL") || 
            name.contains("HOE") || 
            name.contains("SPEAR") || 
            name.contains("MACE") ||
            name.contains("BOW") ||
            mat == Material.FISHING_ROD ||
            mat == Material.TRIDENT) return true;

        // Check for custom tridents specifically
        return isCustomTrident(item);
    }

    private boolean isCustomTrident(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer().get(plugin.getItemManager().CUSTOM_ITEM_KEY, org.bukkit.persistence.PersistentDataType.STRING);
        return id != null && id.contains("trident");
    }

    private boolean isArmor(ItemStack item) {
        if (item == null) return false;
        Material mat = item.getType();
        String name = mat.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") ||
               name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    /**
     * Returns the upgrade tier (+N) for a given material type, or 0 if invalid.
     */
    private int getTierLevel(Material mat) {
        return switch (mat) {
            case COPPER_INGOT -> 1;
            case IRON_INGOT   -> 2;
            case GOLD_INGOT   -> 3;
            case DIAMOND       -> 4;
            case NETHERITE_INGOT -> 5;
            default -> 0;
        };
    }

    private String getTierName(Material mat) {
        return switch (mat) {
            case COPPER_INGOT    -> "Copper";
            case IRON_INGOT      -> "Iron";
            case GOLD_INGOT      -> "Gold";
            case DIAMOND         -> "Diamond";
            case NETHERITE_INGOT -> "Netherite";
            default -> "Unknown";
        };
    }

    // ══════════════════════════════════════════════════════════
    //  Item placement tracking
    // ══════════════════════════════════════════════════════════

    private void placeItem(int slot, ItemStack item) {
        switch (slot) {
            case STONE_SLOT -> { placedStone = item; inventory.setItem(slot, item); }
            case GEAR_SLOT  -> { placedGear = item; inventory.setItem(slot, item); }
            case TIER_SLOT  -> { placedTier = item; inventory.setItem(slot, item); }
            case COMBINE_LEFT_SLOT -> { placedCombineLeft = item; inventory.setItem(slot, item); }
            case COMBINE_RIGHT_SLOT -> { placedCombineRight = item; inventory.setItem(slot, item); }
        }
    }

    private ItemStack getPlacedItem(int slot) {
        return switch (slot) {
            case STONE_SLOT -> placedStone;
            case GEAR_SLOT  -> placedGear;
            case TIER_SLOT  -> placedTier;
            case COMBINE_LEFT_SLOT -> placedCombineLeft;
            case COMBINE_RIGHT_SLOT -> placedCombineRight;
            default -> null;
        };
    }

    private void returnItem(int slot) {
        switch (slot) {
            case STONE_SLOT -> {
                placedStone = null;
                inventory.setItem(slot, createPlaceholder(Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                        "Upgrade Stone", "Place a Shark Tooth or", "Beast Scale here."));
            }
            case GEAR_SLOT -> {
                placedGear = null;
                inventory.setItem(slot, createPlaceholder(Material.ORANGE_STAINED_GLASS_PANE,
                        "Equipment", "Place weapon or armor", "to upgrade here."));
            }
            case TIER_SLOT -> {
                placedTier = null;
                inventory.setItem(slot, createPlaceholder(Material.LIME_STAINED_GLASS_PANE,
                        "Upgrade Level", "Place Copper, Iron, Gold,", "Diamond, or Netherite Ingot."));
            }
            case COMBINE_LEFT_SLOT -> {
                placedCombineLeft = null;
                inventory.setItem(slot, createBlankSlot(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
            }
            case COMBINE_RIGHT_SLOT -> {
                placedCombineRight = null;
                inventory.setItem(slot, createBlankSlot(Material.ORANGE_STAINED_GLASS_PANE));
            }
        }
    }

    private void updateAllResults() {
        updateResult();
        updateCombineResult();
    }

    // ══════════════════════════════════════════════════════════
    //  Result computation
    // ══════════════════════════════════════════════════════════

    private void updateResult() {
        if (placedStone == null || placedGear == null || placedTier == null) {
            inventory.setItem(RESULT_SLOT, createPlaceholder(Material.BLACK_STAINED_GLASS_PANE,
                    "Result", "Place all 3 items to", "see the upgrade result."));
            return;
        }

        ItemManager im = plugin.getItemManager();
        boolean isAttack = im.isCustomItem(placedStone, "shark_tooth");
        boolean isDefense = im.isCustomItem(placedStone, "beast_scale");

        // Validate compatibility
        if (isAttack && !isWeaponOrTool(placedGear)) {
            showError("Invalid Gear!", "Shark Tooth can only be", "applied to weapons/tools.");
            return;
        }
        if (isDefense && !isArmor(placedGear)) {
            showError("Invalid Gear!", "Beast Scale can only be", "applied to armor pieces.");
            return;
        }

        int tierLevel = getTierLevel(placedTier.getType());
        String tierName = getTierName(placedTier.getType());

        // Check if gear already has an upgrade of this type at this or higher tier
        if (placedGear.hasItemMeta()) {
            PersistentDataContainer pdc = placedGear.getItemMeta().getPersistentDataContainer();
            String existingUpgrade = pdc.getOrDefault(im.SC_UPGRADE_TIER_KEY, PersistentDataType.STRING, "");
            // Format: "attack:3" or "defense:2" or "attack:3,defense:2"
            if (!existingUpgrade.isEmpty()) {
                String type = isAttack ? "attack" : "defense";
                for (String part : existingUpgrade.split(",")) {
                    String[] kv = part.split(":");
                    if (kv.length == 2 && kv[0].equals(type)) {
                        int existingTier = Integer.parseInt(kv[1]);
                        if (tierLevel <= existingTier) {
                            // Show error result
                            ItemStack errorItem = new ItemStack(Material.BARRIER);
                            ItemMeta em = errorItem.getItemMeta();
                            em.displayName(Component.text("Cannot Downgrade!").color(NamedTextColor.RED)
                                    .decoration(TextDecoration.ITALIC, false));
                            em.lore(List.of(
                                    Component.text("This gear already has " + type + " +" + existingTier)
                                            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                                    Component.text("Use a higher tier material to upgrade.")
                                            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                            ));
                            errorItem.setItemMeta(em);
                            inventory.setItem(RESULT_SLOT, errorItem);
                            return;
                        }
                    }
                }
            }
        }

        // Build result preview
        ItemStack result = placedGear.clone();
        ItemMeta meta = result.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Apply flat bonus
        if (isAttack) {
            pdc.set(im.SC_FLAT_ATTACK_KEY, PersistentDataType.DOUBLE, tierLevel * plugin.getConfig().getDouble("item_balance.sc_flat_bonus_per_tier", 0.5));
        } else {
            pdc.set(im.SC_FLAT_DEFENSE_KEY, PersistentDataType.DOUBLE, tierLevel * plugin.getConfig().getDouble("item_balance.sc_flat_bonus_per_tier", 0.5));
        }

        // Update upgrade tier tracking
        String existingUpgrade = pdc.getOrDefault(im.SC_UPGRADE_TIER_KEY, PersistentDataType.STRING, "");
        String type = isAttack ? "attack" : "defense";
        String newUpgrade = updateUpgradeString(existingUpgrade, type, tierLevel);
        pdc.set(im.SC_UPGRADE_TIER_KEY, PersistentDataType.STRING, newUpgrade);

        result.setItemMeta(meta);
        plugin.getLoreManager().updateLore(result);
        inventory.setItem(RESULT_SLOT, result);
    }

    /**
     * Updates the upgrade tier string (e.g. "attack:3,defense:2").
     */
    private String updateUpgradeString(String existing, String type, int tier) {
        if (existing.isEmpty()) return type + ":" + tier;

        StringBuilder sb = new StringBuilder();
        boolean found = false;
        for (String part : existing.split(",")) {
            String[] kv = part.split(":");
            if (kv.length == 2 && kv[0].equals(type)) {
                sb.append(sb.isEmpty() ? "" : ",").append(type).append(":").append(tier);
                found = true;
            } else {
                sb.append(sb.isEmpty() ? "" : ",").append(part);
            }
        }
        if (!found) {
            sb.append(sb.isEmpty() ? "" : ",").append(type).append(":").append(tier);
        }
        return sb.toString();
    }

    private void showError(String title, String... lines) {
        ItemStack errorItem = new ItemStack(Material.BARRIER);
        ItemMeta em = errorItem.getItemMeta();
        em.displayName(Component.text(title).color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(Component.text(line).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        em.lore(lore);
        errorItem.setItemMeta(em);
        inventory.setItem(RESULT_SLOT, errorItem);
    }

    private record FusionEntry(Enchantment enchantment, int fromLevel, int toLevel) {}

    private void updateCombineResult() {
        combineLevelCost = 0;
        if (placedCombineLeft == null || placedCombineRight == null) {
            inventory.setItem(COMBINE_RESULT_SLOT, createPlaceholder(Material.BLACK_STAINED_GLASS_PANE,
                    "Fusion Result", "III->IV costs 30 levels", "IV->V 40, V->VI 50"));
            return;
        }

        if (placedCombineLeft.getType() != placedCombineRight.getType()) {
            setCombineError("Input mismatch", "Both fusion inputs must be", "the same item type.");
            return;
        }

        Enchantment seaCreature = org.bukkit.Registry.ENCHANTMENT.get(seaCreatureChanceKey);
        if (seaCreature == null) {
            setCombineError("Missing enchant", "Sea Creature Chance enchant", "is not registered.");
            return;
        }

        List<FusionEntry> upgrades = new ArrayList<>();
        collectFusion(upgrades, Enchantment.LURE);
        collectFusion(upgrades, Enchantment.LUCK_OF_THE_SEA);
        collectFusion(upgrades, seaCreature);

        if (upgrades.isEmpty()) {
            setCombineError("No valid fusion", "Need same enchant + same level", "between III and V.");
            return;
        }

        int requiredEnchantLevels = 0;
        int highestTarget = 0;
        for (FusionEntry e : upgrades) {
            int req = requiredEnchantLevelsForTarget(e.toLevel());
            if (req > requiredEnchantLevels) requiredEnchantLevels = req;
            if (e.toLevel() > highestTarget) highestTarget = e.toLevel();
        }

        if (requiredEnchantLevels > 0 && player.getLevel() < requiredEnchantLevels) {
            setCombineError("Not enough levels",
                    "Need " + requiredEnchantLevels + " levels for " + toRoman(highestTarget) + ".",
                    "Current levels: " + player.getLevel() + ".");
            return;
        }

        combineLevelCost = requiredEnchantLevels;

        ItemStack result = placedCombineLeft.clone();
        for (FusionEntry e : upgrades) {
            setEnchantLevel(result, e.enchantment(), e.toLevel());
        }
        plugin.getLoreManager().updateLore(result);
        inventory.setItem(COMBINE_RESULT_SLOT, result);
    }

    private void collectFusion(List<FusionEntry> upgrades, Enchantment enchantment) {
        int leftLevel = getEnchantLevel(placedCombineLeft, enchantment);
        int rightLevel = getEnchantLevel(placedCombineRight, enchantment);
        if (leftLevel == rightLevel && leftLevel >= 3 && leftLevel <= 5) {
            upgrades.add(new FusionEntry(enchantment, leftLevel, leftLevel + 1));
        }
    }

    private int getEnchantLevel(ItemStack item, Enchantment enchantment) {
        if (item == null || enchantment == null || !item.hasItemMeta()) return 0;
        if (item.getType() == Material.ENCHANTED_BOOK && item.getItemMeta() instanceof EnchantmentStorageMeta esm) {
            return esm.getStoredEnchantLevel(enchantment);
        }
        return item.getEnchantmentLevel(enchantment);
    }

    private void setEnchantLevel(ItemStack item, Enchantment enchantment, int level) {
        if (item == null || enchantment == null || !item.hasItemMeta()) return;
        if (item.getType() == Material.ENCHANTED_BOOK && item.getItemMeta() instanceof EnchantmentStorageMeta esm) {
            esm.removeStoredEnchant(enchantment);
            esm.addStoredEnchant(enchantment, level, true);
            item.setItemMeta(esm);
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

    private void setCombineError(String title, String line1, String line2) {
        ItemStack errorItem = new ItemStack(Material.BARRIER);
        ItemMeta em = errorItem.getItemMeta();
        em.displayName(Component.text(title).color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        em.lore(List.of(
                Component.text(line1).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(line2).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        errorItem.setItemMeta(em);
        inventory.setItem(COMBINE_RESULT_SLOT, errorItem);
    }

    // ══════════════════════════════════════════════════════════
    //  Claiming the result
    // ══════════════════════════════════════════════════════════

    private void handleResultClick() {
        long now = System.currentTimeMillis();
        if (now - lastResultClick < 150) return;
        lastResultClick = now;

        ItemStack resultItem = inventory.getItem(RESULT_SLOT);
        if (resultItem == null || resultItem.getType() == Material.BLACK_STAINED_GLASS_PANE
                || resultItem.getType() == Material.BARRIER) {
            return;
        }

        // Give result to player
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(resultItem.clone());
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }

        // Consume exactly one from each input
        consumeOne(STONE_SLOT);
        consumeOne(GEAR_SLOT);
        consumeOne(TIER_SLOT);

        // Update result preview (it might still have items to upgrade again)
        updateAllResults();

        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1.0f, 1.0f);
        player.sendMessage(Component.text("Gear upgraded!").color(NamedTextColor.GREEN));
    }

    private void handleCombineResultClick() {
        long now = System.currentTimeMillis();
        if (now - lastResultClick < 150) return;
        lastResultClick = now;

        ItemStack resultItem = inventory.getItem(COMBINE_RESULT_SLOT);
        if (resultItem == null || resultItem.getType() == Material.BLACK_STAINED_GLASS_PANE
                || resultItem.getType() == Material.BARRIER) {
            return;
        }

        if (combineLevelCost > 0) {
            if (player.getLevel() < combineLevelCost) {
                player.sendMessage(Component.text("Not enough enchant levels.").color(NamedTextColor.RED));
                updateCombineResult();
                return;
            }
            player.setLevel(player.getLevel() - combineLevelCost);
        }

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(resultItem.clone());
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }

        consumeOne(COMBINE_LEFT_SLOT);
        consumeOne(COMBINE_RIGHT_SLOT);
        updateAllResults();

        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.1f);
        player.sendMessage(Component.text("Enchants fused!").color(NamedTextColor.GREEN));
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

    // ══════════════════════════════════════════════════════════
    //  Close — return un-claimed items
    // ══════════════════════════════════════════════════════════

    @Override
    public void onClose(InventoryCloseEvent event) {
        Player p = (Player) event.getPlayer();
        returnToPlayer(p, placedStone);
        returnToPlayer(p, placedGear);
        returnToPlayer(p, placedTier);
        returnToPlayer(p, placedCombineLeft);
        returnToPlayer(p, placedCombineRight);
        placedStone = null;
        placedGear = null;
        placedTier = null;
        placedCombineLeft = null;
        placedCombineRight = null;
    }

    private void returnToPlayer(Player p, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(item);
        for (ItemStack drop : leftover.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), drop);
        }
    }
}

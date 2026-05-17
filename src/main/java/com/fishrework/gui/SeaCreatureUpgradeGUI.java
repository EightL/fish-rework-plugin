package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.manager.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gear-only upgrade table for adding sea creature attack and defense bonuses.
 */
public class SeaCreatureUpgradeGUI extends BaseGUI {

    private static final int STONE_SLOT = 10;
    private static final int GEAR_SLOT = 11;
    private static final int TIER_SLOT = 12;
    private static final int ARROW_SLOT = 14;
    private static final int RESULT_SLOT = 16;
    private static final int CLOSE_SLOT = 22;

    private final Player player;

    private ItemStack placedStone = null;
    private ItemStack placedGear = null;
    private ItemStack placedTier = null;
    private long lastResultClick = 0;

    public SeaCreatureUpgradeGUI(FishRework plugin, Player player) {
        super(plugin, 3, localizedTitle(plugin, "seacreatureupgradegui.title", "Upgrade Gear"));
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
        fillUpgradeBackground();

        inventory.setItem(STONE_SLOT, createPlaceholder(Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                "Upgrade Stone", "Place a Shark Tooth or", "Beast Scale here."));
        inventory.setItem(GEAR_SLOT, createPlaceholder(Material.ORANGE_STAINED_GLASS_PANE,
                "Equipment", "Place weapon or armor", "to upgrade here."));
        inventory.setItem(TIER_SLOT, createPlaceholder(Material.LIME_STAINED_GLASS_PANE,
                "Upgrade Level", "Place Copper, Iron, Gold,", "Diamond, or Netherite Ingot."));

        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta arrowMeta = arrow.getItemMeta();
        arrowMeta.displayName(plugin.getLanguageManager().getMessage("seacreatureupgradegui.arrow_indicator", "->")
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        arrow.setItemMeta(arrowMeta);
        inventory.setItem(ARROW_SLOT, arrow);

        inventory.setItem(RESULT_SLOT, createPlaceholder(Material.BLACK_STAINED_GLASS_PANE,
                "Result", "Place all 3 items to", "see the upgrade result."));

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(plugin.getLanguageManager().getMessage("seacreatureupgradegui.close", "Close")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(closeMeta);
        inventory.setItem(CLOSE_SLOT, close);
    }

    private void fillUpgradeBackground() {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, decorativePane(Material.GRAY_STAINED_GLASS_PANE));
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

        if (slot == STONE_SLOT || slot == GEAR_SLOT || slot == TIER_SLOT) {
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
        if (isValidForSlot(STONE_SLOT, item) && placedStone == null) return STONE_SLOT;
        if (isValidForSlot(GEAR_SLOT, item) && placedGear == null) return GEAR_SLOT;
        if (isValidForSlot(TIER_SLOT, item) && placedTier == null) return TIER_SLOT;
        return -1;
    }

    private boolean isValidForSlot(int slot, ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemManager im = plugin.getItemManager();

        return switch (slot) {
            case STONE_SLOT -> im.isCustomItem(item, "shark_tooth") || im.isCustomItem(item, "beast_scale");
            case GEAR_SLOT -> isUpgradeableGear(item);
            case TIER_SLOT -> getTierLevel(item.getType()) > 0;
            default -> false;
        };
    }

    private boolean isUpgradeableGear(ItemStack item) {
        return isWeaponOrTool(item) || isArmor(item);
    }

    private boolean isWeaponOrTool(ItemStack item) {
        if (item == null) return false;
        Material mat = item.getType();
        String name = mat.name();

        if (name.contains("SWORD")
                || name.contains("AXE")
                || name.contains("TRIDENT")
                || name.contains("PICKAXE")
                || name.contains("SHOVEL")
                || name.contains("HOE")
                || name.contains("SPEAR")
                || name.contains("MACE")
                || name.contains("BOW")
                || mat == Material.FISHING_ROD
                || mat == Material.TRIDENT) {
            return true;
        }

        return isCustomTrident(item);
    }

    private boolean isCustomTrident(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer().get(plugin.getItemManager().CUSTOM_ITEM_KEY, PersistentDataType.STRING);
        return id != null && id.contains("trident");
    }

    private boolean isArmor(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    private int getTierLevel(Material mat) {
        return switch (mat) {
            case COPPER_INGOT -> 1;
            case IRON_INGOT -> 2;
            case GOLD_INGOT -> 3;
            case DIAMOND -> 4;
            case NETHERITE_INGOT -> 5;
            default -> 0;
        };
    }

    private void placeItem(int slot, ItemStack item) {
        switch (slot) {
            case STONE_SLOT -> {
                placedStone = item;
                inventory.setItem(slot, item);
            }
            case GEAR_SLOT -> {
                placedGear = item;
                inventory.setItem(slot, item);
            }
            case TIER_SLOT -> {
                placedTier = item;
                inventory.setItem(slot, item);
            }
            default -> {
            }
        }
    }

    private ItemStack getPlacedItem(int slot) {
        return switch (slot) {
            case STONE_SLOT -> placedStone;
            case GEAR_SLOT -> placedGear;
            case TIER_SLOT -> placedTier;
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
            default -> {
            }
        }
    }

    private void updateResult() {
        if (placedStone == null || placedGear == null || placedTier == null) {
            inventory.setItem(RESULT_SLOT, createPlaceholder(Material.BLACK_STAINED_GLASS_PANE,
                    "Result", "Place all 3 items to", "see the upgrade result."));
            return;
        }

        ItemManager im = plugin.getItemManager();
        boolean isAttack = im.isCustomItem(placedStone, "shark_tooth");
        boolean isDefense = im.isCustomItem(placedStone, "beast_scale");

        if (isAttack && !isWeaponOrTool(placedGear)) {
            showError("Invalid Gear!", "Shark Tooth can only be", "applied to weapons/tools.");
            return;
        }
        if (isDefense && !isArmor(placedGear)) {
            showError("Invalid Gear!", "Beast Scale can only be", "applied to armor pieces.");
            return;
        }

        int tierLevel = getTierLevel(placedTier.getType());

        if (placedGear.hasItemMeta()) {
            PersistentDataContainer pdc = placedGear.getItemMeta().getPersistentDataContainer();
            String existingUpgrade = pdc.getOrDefault(im.SC_UPGRADE_TIER_KEY, PersistentDataType.STRING, "");
            if (!existingUpgrade.isEmpty()) {
                String type = isAttack ? "attack" : "defense";
                boolean hasFlatUpgrade = isAttack
                        ? pdc.has(im.SC_FLAT_ATTACK_KEY, PersistentDataType.DOUBLE)
                        : pdc.has(im.SC_FLAT_DEFENSE_KEY, PersistentDataType.DOUBLE);
                if (hasFlatUpgrade) {
                    for (String part : existingUpgrade.split(",")) {
                        String[] kv = part.split(":");
                        if (kv.length == 2 && kv[0].equals(type)) {
                            int existingTier;
                            try {
                                existingTier = Integer.parseInt(kv[1]);
                            } catch (NumberFormatException ignored) {
                                continue;
                            }
                            if (tierLevel <= existingTier) {
                                ItemStack errorItem = new ItemStack(Material.BARRIER);
                                ItemMeta errorMeta = errorItem.getItemMeta();
                                errorMeta.displayName(plugin.getLanguageManager().getMessage("seacreatureupgradegui.cannot_downgrade", "Cannot Downgrade!")
                                        .color(NamedTextColor.RED)
                                        .decoration(TextDecoration.ITALIC, false));
                                errorMeta.lore(List.of(
                                        Component.text(plugin.getLanguageManager().getString(
                                                        "seacreatureupgradegui.existing_upgrade",
                                                        "This gear already has %type% +%tier%",
                                                        "type", type,
                                                        "tier", String.valueOf(existingTier)))
                                                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                                        plugin.getLanguageManager().getMessage("seacreatureupgradegui.use_a_higher_tier_material", "Use a higher tier material to upgrade.")
                                                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                                ));
                                errorItem.setItemMeta(errorMeta);
                                inventory.setItem(RESULT_SLOT, errorItem);
                                return;
                            }
                        }
                    }
                }
            }
        }

        ItemStack result = placedGear.clone();
        ItemMeta meta = result.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (isAttack) {
            pdc.set(im.SC_FLAT_ATTACK_KEY, PersistentDataType.DOUBLE,
                    tierLevel * plugin.getConfig().getDouble("item_balance.sc_flat_bonus_per_tier", 0.5));
        } else {
            pdc.set(im.SC_FLAT_DEFENSE_KEY, PersistentDataType.DOUBLE,
                    tierLevel * plugin.getConfig().getDouble("item_balance.sc_flat_bonus_per_tier", 0.5));
        }

        String existingUpgrade = pdc.getOrDefault(im.SC_UPGRADE_TIER_KEY, PersistentDataType.STRING, "");
        String type = isAttack ? "attack" : "defense";
        pdc.set(im.SC_UPGRADE_TIER_KEY, PersistentDataType.STRING, updateUpgradeString(existingUpgrade, type, tierLevel));

        result.setItemMeta(meta);
        plugin.getLoreManager().updateLore(result);
        inventory.setItem(RESULT_SLOT, result);
    }

    private String updateUpgradeString(String existing, String type, int tier) {
        if (existing.isEmpty()) return type + ":" + tier;

        StringBuilder builder = new StringBuilder();
        boolean found = false;
        for (String part : existing.split(",")) {
            String[] kv = part.split(":");
            if (kv.length == 2 && kv[0].equals(type)) {
                builder.append(builder.isEmpty() ? "" : ",").append(type).append(":").append(tier);
                found = true;
            } else {
                builder.append(builder.isEmpty() ? "" : ",").append(part);
            }
        }
        if (!found) {
            builder.append(builder.isEmpty() ? "" : ",").append(type).append(":").append(tier);
        }
        return builder.toString();
    }

    private void showError(String title, String... lines) {
        ItemStack errorItem = new ItemStack(Material.BARRIER);
        ItemMeta meta = errorItem.getItemMeta();
        meta.displayName(Component.text(title).color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(Component.text(line).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
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

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(resultItem.clone());
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }

        consumeOne(STONE_SLOT);
        consumeOne(GEAR_SLOT);
        consumeOne(TIER_SLOT);
        updateResult();

        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1.0f, 1.0f);
        player.sendMessage(plugin.getLanguageManager().getMessage("seacreatureupgradegui.gear_upgraded", "Gear upgraded!").color(NamedTextColor.GREEN));
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
        returnToPlayer(closingPlayer, placedStone);
        returnToPlayer(closingPlayer, placedGear);
        returnToPlayer(closingPlayer, placedTier);
        placedStone = null;
        placedGear = null;
        placedTier = null;
    }

    private void returnToPlayer(Player p, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(item);
        for (ItemStack drop : leftover.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), drop);
        }
    }
}

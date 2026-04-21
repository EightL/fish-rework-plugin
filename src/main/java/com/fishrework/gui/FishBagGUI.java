package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.model.PlayerData;
import com.fishrework.util.BagUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Fish Bag GUI (6 rows).
 * Slots 0-44: free item storage (fish and custom items only)
 * Row 5 (45-53): control bar (back, sell all, balance)
 * Does NOT cancel clicks in storage area — allows placing/taking items.
 */
public class FishBagGUI extends BaseGUI {

    private final Player player;
    private static final int STORAGE_SLOTS = 18;
    private static final int CONTROL_ROW_START = 18;

    public FishBagGUI(FishRework plugin, Player player) {
        super(plugin, 3, "Fish Bag");
        this.player = player;
        initializeItems();
    }

    @Override
    public boolean handlesPlayerInventoryClicks() {
        return true;
    }

    private void initializeItems() {
        // Clear the inventory first
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, null);

        // Load saved contents into slots 0-17
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null && data.getFishBagContents() != null) {
            ItemStack[] contents = data.getFishBagContents();
            for (int i = 0; i < Math.min(contents.length, STORAGE_SLOTS); i++) {
                inventory.setItem(i, contents[i]);
            }
        }

        // Row 3: Control bar
        ItemStack controlFill = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta cfMeta = controlFill.getItemMeta();
        cfMeta.displayName(Component.text(" "));
        controlFill.setItemMeta(cfMeta);
        for (int i = CONTROL_ROW_START; i < inventory.getSize(); i++) inventory.setItem(i, controlFill);

        // Slot 18: Back button
        setBackButton(18);

        // Slot 22: Sell All button
        String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");
        double totalValue = calculateBagValue();

        ItemStack sellAll = new ItemStack(Material.GOLD_INGOT);
        ItemMeta saMeta = sellAll.getItemMeta();
        saMeta.displayName(plugin.getLanguageManager().getMessage("fishbaggui.sell_all", "Sell All").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        saMeta.lore(List.of(
                Component.empty(),
                Component.text("Total value: " + com.fishrework.util.FormatUtil.format("%.0f", totalValue) + " " + currencyName)
                        .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                plugin.getLanguageManager().getMessage("fishbaggui.click_to_sell_all_bag", "Click to sell all bag contents!").color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        sellAll.setItemMeta(saMeta);
        inventory.setItem(22, sellAll);

        // Slot 26: Balance display
        double balance = data != null ? data.getBalance() : 0;
        ItemStack balanceItem = new ItemStack(Material.SUNFLOWER);
        ItemMeta balMeta = balanceItem.getItemMeta();
        balMeta.displayName(Component.text("Balance: " + com.fishrework.util.FormatUtil.format("%.0f", balance) + " " + currencyName)
                .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        balanceItem.setItemMeta(balMeta);
        inventory.setItem(26, balanceItem);
    }

    private double calculateBagValue() {
        double total = 0;
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null) continue;
            double price = plugin.getItemManager().getSellPrice(item);
            total += price * item.getAmount();
        }
        return total;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null && event.getSlot() >= CONTROL_ROW_START) return;

        int slot = event.getSlot();

        // Control bar row — always cancel clicks
        if (slot >= CONTROL_ROW_START) {
            // event is already cancelled by GuiListener

            if (slot == 18) {
                // Back — save and go to shop menu
                saveBagContents();
                new ShopMenuGUI(plugin, player).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            } else if (slot == 22) {
                // Sell All — open confirmation
                saveBagContents();
                new SellConfirmGUI(plugin, player, SellConfirmGUI.BagType.FISH_BAG).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            }
            return;
        }

        // Storage area (slots 0-17): allow item placement, but restrict item types
        // Un-cancel the event to allow normal inventory interaction
        event.setCancelled(false);

        // Validate the item being placed (cursor item going into bag)
        ItemStack cursorItem = event.getCursor();
        if (cursorItem != null && !cursorItem.getType().isAir()) {
            // Placing item into bag — validate
            if (!BagUtils.isAllowedInFishBag(plugin, cursorItem)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getLanguageManager().getMessage("fishbaggui.only_fish_and_custom_materials", "Only fish and custom materials can go in the Fish Bag!")
                        .color(NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1);
            }
        }

        // Handle shift-click from player inventory into bag
        if (event.isShiftClick() && event.getClickedInventory() != inventory) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && !BagUtils.isAllowedInFishBag(plugin, clickedItem)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getLanguageManager().getMessage("fishbaggui.only_fish_and_custom_materials", "Only fish and custom materials can go in the Fish Bag!")
                        .color(NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1);
            }
        }
    }

    private void saveBagContents() {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        ItemStack[] contents = new ItemStack[STORAGE_SLOTS];
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            contents[i] = inventory.getItem(i);
        }
        data.setFishBagContents(contents);
    }

    /**
     * Clears all items from the bag (used after selling).
     */
    public void clearBag() {
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            inventory.setItem(i, null);
        }
        saveBagContents();
    }

    @Override
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        saveBagContents();
    }

    // Returns the leftover if bag is full, null if fully added.
    public static ItemStack addToBag(PlayerData data, ItemStack drop) {
        if (data == null || drop == null || drop.getType().isAir()) return drop;
        FishRework plugin = FishRework.getInstance();
        if (plugin != null && !BagUtils.isAllowedInFishBag(plugin, drop)) {
            return drop;
        }

        ItemStack[] contents = data.getFishBagContents();
        if (contents == null) {
            contents = new ItemStack[STORAGE_SLOTS];
        } else if (contents.length < STORAGE_SLOTS) {
            ItemStack[] expanded = new ItemStack[STORAGE_SLOTS];
            System.arraycopy(contents, 0, expanded, 0, Math.min(contents.length, STORAGE_SLOTS));
            contents = expanded;
        } else if (contents.length > STORAGE_SLOTS) {
            ItemStack[] truncated = new ItemStack[STORAGE_SLOTS];
            System.arraycopy(contents, 0, truncated, 0, STORAGE_SLOTS);
            contents = truncated;
        }

        ItemStack remaining = drop.clone();

        // Fill existing stacks first.
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack slot = contents[i];
            if (slot == null) continue;
            if (!slot.isSimilar(remaining)) continue;

            int max = slot.getMaxStackSize();
            int canAdd = max - slot.getAmount();
            if (canAdd <= 0) continue;

            int add = Math.min(canAdd, remaining.getAmount());
            slot.setAmount(slot.getAmount() + add);
            remaining.setAmount(remaining.getAmount() - add);

            if (remaining.getAmount() <= 0) {
                data.setFishBagContents(contents);
                return null;
            }
        }

        // Fill empty slots.
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            if (contents[i] != null) continue;

            int max = remaining.getMaxStackSize();
            int add = Math.min(max, remaining.getAmount());
            ItemStack placed = remaining.clone();
            placed.setAmount(add);
            contents[i] = placed;
            remaining.setAmount(remaining.getAmount() - add);

            if (remaining.getAmount() <= 0) {
                data.setFishBagContents(contents);
                return null;
            }
        }

        data.setFishBagContents(contents);
        return remaining;
    }
}

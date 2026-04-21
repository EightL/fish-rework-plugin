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
 * Lava Bag GUI (6 rows).
 * Slots 0-44: free item storage
 * Row 5 (45-53): control bar (back + balance)
 */
public class LavaBagGUI extends BaseGUI {

    private final Player player;

    public LavaBagGUI(FishRework plugin, Player player) {
        super(plugin, 6, "🔥 Magma Satchel");
        this.player = player;
        initializeItems();
    }

    @Override
    public boolean handlesPlayerInventoryClicks() {
        return true;
    }

    private void initializeItems() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null && data.getLavaBagContents() != null) {
            ItemStack[] contents = data.getLavaBagContents();
            for (int i = 0; i < Math.min(contents.length, 45); i++) {
                inventory.setItem(i, contents[i]);
            }
        }

        ItemStack controlFill = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta cfMeta = controlFill.getItemMeta();
        cfMeta.displayName(Component.text(" "));
        controlFill.setItemMeta(cfMeta);
        for (int i = 45; i <= 53; i++) inventory.setItem(i, controlFill);

        setBackButton(45);

        String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");

        double totalValue = calculateBagValue();
        ItemStack sellAll = new ItemStack(Material.GOLD_INGOT);
        ItemMeta saMeta = sellAll.getItemMeta();
        saMeta.displayName(plugin.getLanguageManager().getMessage("lavabaggui.sell_all", "Sell All").color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        saMeta.lore(List.of(
            Component.empty(),
            Component.text("Total value: " + com.fishrework.util.FormatUtil.format("%.0f", totalValue) + " " + currencyName)
                .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            plugin.getLanguageManager().getMessage("lavabaggui.click_to_sell_all_satchel", "Click to sell all satchel contents!").color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        ));
        sellAll.setItemMeta(saMeta);
        inventory.setItem(49, sellAll);

        double balance = data != null ? data.getBalance() : 0;
        ItemStack balanceItem = new ItemStack(Material.SUNFLOWER);
        ItemMeta balMeta = balanceItem.getItemMeta();
        balMeta.displayName(Component.text("Balance: " + com.fishrework.util.FormatUtil.format("%.0f", balance) + " " + currencyName)
                .color(NamedTextColor.GOLD));
        balanceItem.setItemMeta(balMeta);
        inventory.setItem(53, balanceItem);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null && event.getSlot() >= 45) return;

        int slot = event.getSlot();

        if (slot >= 45) {
            if (slot == 45) {
                saveBagContents();
                new ShopMenuGUI(plugin, player).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            } else if (slot == 49) {
                saveBagContents();
                new SellConfirmGUI(plugin, player, SellConfirmGUI.BagType.LAVA_BAG).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            }
            return;
        }

        // Storage area (slots 0-44): allow interaction, but restrict item types.
        event.setCancelled(false);

        ItemStack cursorItem = event.getCursor();
        if (cursorItem != null && !cursorItem.getType().isAir()) {
            if (!isAllowedInBag(plugin, cursorItem)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getLanguageManager().getMessage("lavabaggui.only_registered_custom_creature_drops", "Only registered custom creature drops can go in the Magma Satchel!")
                        .color(NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1);
            }
        }

        if (event.isShiftClick() && event.getClickedInventory() != inventory) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && !isAllowedInBag(plugin, clickedItem)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getLanguageManager().getMessage("lavabaggui.only_registered_custom_creature_drops", "Only registered custom creature drops can go in the Magma Satchel!")
                        .color(NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1);
            }
        }
    }

    private void saveBagContents() {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        ItemStack[] contents = new ItemStack[45];
        for (int i = 0; i < 45; i++) {
            contents[i] = inventory.getItem(i);
        }
        data.setLavaBagContents(contents);
    }

    @Override
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        saveBagContents();
    }

    public static boolean isAllowedInBag(FishRework plugin, ItemStack item) {
        return BagUtils.isAllowedInFishBag(plugin, item);
    }

    private double calculateBagValue() {
        double total = 0;
        for (int i = 0; i < 45; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null) continue;
            double price = plugin.getItemManager().getSellPrice(item);
            total += price * item.getAmount();
        }
        return total;
    }

    // Returns the leftover if bag is full, null if fully added.
    public static ItemStack addToBag(FishRework plugin, PlayerData data, ItemStack drop) {
        if (data == null || drop == null || drop.getType().isAir()) return drop;
        if (!isAllowedInBag(plugin, drop)) return drop;

        ItemStack[] contents = data.getLavaBagContents();
        if (contents == null) {
            contents = new ItemStack[45];
        } else if (contents.length < 45) {
            ItemStack[] expanded = new ItemStack[45];
            System.arraycopy(contents, 0, expanded, 0, contents.length);
            contents = expanded;
        }

        ItemStack remaining = drop.clone();

        // Fill existing stacks first.
        for (int i = 0; i < 45; i++) {
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
                data.setLavaBagContents(contents);
                return null;
            }
        }

        // Fill empty slots.
        for (int i = 0; i < 45; i++) {
            if (contents[i] != null) continue;

            int max = remaining.getMaxStackSize();
            int add = Math.min(max, remaining.getAmount());
            ItemStack placed = remaining.clone();
            placed.setAmount(add);
            contents[i] = placed;
            remaining.setAmount(remaining.getAmount() - add);

            if (remaining.getAmount() <= 0) {
                data.setLavaBagContents(contents);
                return null;
            }
        }

        data.setLavaBagContents(contents);
        return remaining;
    }
}

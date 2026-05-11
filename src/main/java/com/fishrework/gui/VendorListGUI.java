package com.fishrework.gui;

import com.fishrework.FishRework;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VendorListGUI extends BaseGUI {

    private static final int BACK_SLOT = 18;

    private final Player player;
    private final Map<Integer, String> menuIdsBySlot = new HashMap<>();

    private record MenuEntry(String id, String title, Material icon, int slot) {}

    public VendorListGUI(FishRework plugin, Player player) {
        super(plugin, 3, plugin.getConfig().getString("vendors.list_title", "Doubloon Vendors"));
        this.player = player;
        initializeItems();
    }

    private void initializeItems() {
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);
        menuIdsBySlot.clear();

        List<MenuEntry> menus = getVisibleMenus();
        int nextSlot = 10;
        for (MenuEntry menu : menus) {
            int slot = menu.slot();
            if (slot < 0 || slot >= inventory.getSize() || slot == BACK_SLOT || slot == 26 || menuIdsBySlot.containsKey(slot)) {
                slot = nextAvailableSlot(nextSlot);
            }
            if (slot < 0) continue;
            nextSlot = slot + 1;

            ItemStack item = new ItemStack(menu.icon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(menu.title()).color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.empty(),
                    plugin.getLanguageManager().getMessage("vendorlistgui.click_to_open", "Click to open").color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            menuIdsBySlot.put(slot, menu.id());
        }

        setBackButton(BACK_SLOT);
        setBalanceDisplay(26);
    }

    private List<MenuEntry> getVisibleMenus() {
        ConfigurationSection menusSection = plugin.getConfig().getConfigurationSection("vendors.menus");
        if (menusSection == null) return List.of();

        List<MenuEntry> menus = new ArrayList<>();
        for (String id : menusSection.getKeys(false)) {
            String path = "vendors.menus." + id;
            if (!plugin.getConfig().getBoolean(path + ".enabled", true)) continue;
            String permission = plugin.getConfig().getString(path + ".permission", "");
            if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) continue;

            Material icon = parseMaterial(plugin.getConfig().getString(path + ".icon", "EMERALD"), Material.EMERALD);
            String title = plugin.getConfig().getString(path + ".title", id);
            int slot = plugin.getConfig().getInt(path + ".slot", -1);
            menus.add(new MenuEntry(id, title, icon, slot));
        }
        menus.sort(Comparator.comparingInt((MenuEntry menu) -> menu.slot() < 0 ? Integer.MAX_VALUE : menu.slot())
                .thenComparing(MenuEntry::id));
        return menus;
    }

    private int nextAvailableSlot(int start) {
        for (int slot = Math.max(0, start); slot < inventory.getSize(); slot++) {
            if (slot == BACK_SLOT || slot == 26 || menuIdsBySlot.containsKey(slot)) continue;
            return slot;
        }
        return -1;
    }

    private void setBalanceDisplay(int slot) {
        com.fishrework.model.PlayerData data = plugin.getPlayerData(player.getUniqueId());
        double balance = data != null ? data.getBalance() : 0;
        String currencyName = plugin.getLanguageManager().getCurrencyName();

        ItemStack balanceItem = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = balanceItem.getItemMeta();
        meta.displayName(Component.text(plugin.getLanguageManager().getString(
                        "vendorlistgui.balance_prefix",
                        "Balance: %balance% %currency%",
                        "balance", com.fishrework.util.FormatUtil.format("%.0f", balance),
                        "currency", currencyName))
                .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        balanceItem.setItemMeta(meta);
        inventory.setItem(slot, balanceItem);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;

        if (event.getSlot() == BACK_SLOT) {
            new ShopMenuGUI(plugin, player).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            return;
        }

        String menuId = menuIdsBySlot.get(event.getSlot());
        if (menuId == null) return;

        new ConfigVendorGUI(plugin, player, menuId).open(player);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
    }

    private Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Material.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}

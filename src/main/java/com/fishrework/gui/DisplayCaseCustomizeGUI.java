package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.manager.DisplayCaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class DisplayCaseCustomizeGUI extends BaseGUI {

    private final Block displayCaseBlock;
    private final DisplayCaseManager manager;
    private final Player player;

    // --- Options ---
    private static final List<Material> SLABS = Arrays.asList(
            Material.POLISHED_ANDESITE_SLAB, Material.ANDESITE_SLAB,
            Material.POLISHED_DIORITE_SLAB, Material.DIORITE_SLAB,
            Material.POLISHED_GRANITE_SLAB, Material.GRANITE_SLAB,
            Material.STONE_SLAB, Material.POLISHED_BLACKSTONE_SLAB, Material.POLISHED_DEEPSLATE_SLAB
    );

    private static final List<Material> GLASS = Arrays.asList(
            Material.GLASS,
            Material.WHITE_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS,
            Material.LIGHT_BLUE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS, Material.LIME_STAINED_GLASS,
            Material.PINK_STAINED_GLASS, Material.GRAY_STAINED_GLASS, Material.LIGHT_GRAY_STAINED_GLASS,
            Material.CYAN_STAINED_GLASS, Material.PURPLE_STAINED_GLASS, Material.BLUE_STAINED_GLASS,
            Material.BROWN_STAINED_GLASS, Material.GREEN_STAINED_GLASS, Material.RED_STAINED_GLASS,
            Material.BLACK_STAINED_GLASS
    );

    private static final List<Material> CARPETS = Arrays.asList(
            Material.WHITE_CARPET, Material.ORANGE_CARPET, Material.MAGENTA_CARPET,
            Material.LIGHT_BLUE_CARPET, Material.YELLOW_CARPET, Material.LIME_CARPET,
            Material.PINK_CARPET, Material.GRAY_CARPET, Material.LIGHT_GRAY_CARPET,
            Material.CYAN_CARPET, Material.PURPLE_CARPET, Material.BLUE_CARPET,
            Material.BROWN_CARPET, Material.GREEN_CARPET, Material.RED_CARPET,
            Material.BLACK_CARPET
    );

    public DisplayCaseCustomizeGUI(FishRework plugin, Player player, Block block) {
        super(plugin, 6, "Customize Display Case");
        this.player = player;
        this.displayCaseBlock = block;
        this.manager = plugin.getDisplayCaseManager();
        initializeItems();
    }

    private void initializeItems() {
        // Clear first
        inventory.clear();

        // Get current style
        Material currentSlab = displayCaseBlock.getType();
        Material currentGlass = Material.GLASS;
        Material currentCarpet = Material.MAGENTA_CARPET;

        Optional<ItemDisplay> displayOpt = manager.getDisplayEntity(displayCaseBlock);
        if (displayOpt.isPresent()) {
            ItemDisplay display = displayOpt.get();
            // Try to read from PDC first, else defaults
            String gName = display.getPersistentDataContainer().get(manager.CASE_STYLE_GLASS, PersistentDataType.STRING);
            String cName = display.getPersistentDataContainer().get(manager.CASE_STYLE_CARPET, PersistentDataType.STRING);
            
            if (gName != null) currentGlass = Material.getMaterial(gName);
            if (cName != null) currentCarpet = Material.getMaterial(cName);
            
            // Fallback if null (shouldn't happen with new ones, but old ones might)
            if (currentGlass == null) currentGlass = Material.GLASS;
            if (currentCarpet == null) currentCarpet = Material.MAGENTA_CARPET;
        }

        // --- Row 0: Slabs ---
        for (int i = 0; i < SLABS.size(); i++) {
            inventory.setItem(i, createOptionItem(SLABS.get(i), "Base", currentSlab));
        }

        // --- Row 1-2: Glass ---
        for (int i = 0; i < GLASS.size(); i++) {
            inventory.setItem(9 + i, createOptionItem(GLASS.get(i), "Glass", currentGlass));
        }

        // --- Row 3-4: Carpets ---
        for (int i = 0; i < CARPETS.size(); i++) {
            inventory.setItem(27 + i, createOptionItem(CARPETS.get(i), "Cushion", currentCarpet));
        }

        // --- Row 5: Close ---
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(plugin.getLanguageManager().getMessage("displaycasecustomizegui.close", "Close").color(NamedTextColor.RED));
        close.setItemMeta(closeMeta);
        inventory.setItem(49, close);
        
        // Fill empty slots with pane
        ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillMeta = fill.getItemMeta();
        fillMeta.displayName(Component.empty());
        fill.setItemMeta(fillMeta);
        
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, fill);
            }
        }
    }

    private ItemStack createOptionItem(Material mat, String type, Material current) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        boolean isSelected = (mat == current);
        
        if (isSelected) {
            meta.displayName(Component.text("Selected: " + formatName(mat)).color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.lore(List.of(Component.text("Currently applied " + type).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        } else {
            meta.displayName(Component.text(formatName(mat)).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Click to apply " + type).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        }
        
        item.setItemMeta(meta);
        return item;
    }

    private String formatName(Material mat) {
        String name = mat.name().replace('_', ' ').toLowerCase();
        // Capitalize words
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (sb.length() > 0) sb.append(" ");
            if (word.length() > 0) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return sb.toString();
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        
        int slot = event.getSlot();
        
        // Slabs (Row 0)
        if (slot >= 0 && slot < SLABS.size()) {
            applyStyle(SLABS.get(slot), null, null);
        }
        // Glass (Row 1-2)
        else if (slot >= 9 && slot < 9 + GLASS.size()) {
            applyStyle(null, GLASS.get(slot - 9), null);
        }
        // Carpets (Row 3-4)
        else if (slot >= 27 && slot < 27 + CARPETS.size()) {
            applyStyle(null, null, CARPETS.get(slot - 27));
        }
        // Close
        else if (slot == 49) {
            player.closeInventory();
        }
    }

    private void applyStyle(Material slab, Material glass, Material carpet) {
        manager.updateStyle(displayCaseBlock, slab, glass, carpet);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 2f);
        // Refresh GUI to show new selection
        initializeItems();
    }
}

package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.model.BiomeGroup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Allows the player to select a biome filter for the Encyclopedia.
 */
public class BiomeSelectionGui extends BaseGUI {
    private static final List<BiomeGroup> OVERWORLD_GROUP_ORDER = List.of(
            BiomeGroup.COLD_OCEAN,
            BiomeGroup.FROZEN_OCEAN,
            BiomeGroup.NORMAL_OCEAN,
            BiomeGroup.LUKEWARM_OCEAN,
            BiomeGroup.WARM_OCEAN,
            BiomeGroup.RIVER,
            BiomeGroup.SWAMP,
            BiomeGroup.BEACH,
            BiomeGroup.LUSH_CAVES,
            BiomeGroup.FOREST,
            BiomeGroup.TAIGA,
            BiomeGroup.SNOWY,
            BiomeGroup.PLAINS,
            BiomeGroup.JUNGLE,
            BiomeGroup.SAVANNA,
            BiomeGroup.DESERT,
            BiomeGroup.MOUNTAINS,
            BiomeGroup.BADLANDS,
            BiomeGroup.MUSHROOM,
            BiomeGroup.MEADOW,
            BiomeGroup.DEEP_DARK,
            BiomeGroup.PALE_GARDEN
    );

    private static final List<BiomeGroup> NETHER_GROUP_ORDER = List.of(
            BiomeGroup.CRIMSON_FOREST,
            BiomeGroup.WARPED_FOREST,
            BiomeGroup.SOUL_SAND_VALLEY,
            BiomeGroup.BASALT_DELTAS,
            BiomeGroup.NETHER_WASTES
    );


    private final Player player;
    private final BiomeGroup currentFilter;
    private final CollectionGui.SortType sort;
    private final com.fishrework.model.CustomMob.MobCategory typeFilter;
    private final CollectionGui.BiomeDimension biomeDimension;

    public BiomeSelectionGui(FishRework plugin, Player player, BiomeGroup currentFilter) {
        this(plugin, player, currentFilter, CollectionGui.SortType.XP_ASC, null, CollectionGui.BiomeDimension.OVERWORLD);
    }

    public BiomeSelectionGui(
            FishRework plugin,
            Player player,
            BiomeGroup currentFilter,
            CollectionGui.SortType sort,
            com.fishrework.model.CustomMob.MobCategory typeFilter,
            CollectionGui.BiomeDimension biomeDimension
    ) {
        super(plugin, 6, "Select Biome Filter"); // 6 rows to fit all biomes comfortably
        this.player = player;
        this.currentFilter = currentFilter;
        this.sort = sort;
        this.typeFilter = typeFilter;
        this.biomeDimension = biomeDimension;
        initializeItems();
    }

    private void initializeItems() {
        // Fill background
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        // "ALL" Filter Option (Central functionality)
        ItemStack allItem = new ItemStack(Material.COMPASS);
        ItemMeta allMeta = allItem.getItemMeta();
        allMeta.displayName(plugin.getLanguageManager().getMessage("biomeselectiongui.all_biomes", "ALL BIOMES").color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> allLore = new ArrayList<>();
        allLore.add(plugin.getLanguageManager().getMessage("biomeselectiongui.show_all_fish_and_mobs", "Show all fish and mobs").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        if (currentFilter == null) {
            allLore.add(plugin.getLanguageManager().getMessage("biomeselectiongui.currently_selected", "▶ CURRENTLY SELECTED ◀").color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            allMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            allMeta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }
        allMeta.lore(allLore);
        allItem.setItemMeta(allMeta);
        inventory.setItem(4, allItem); // Top center

        ItemStack dimensionItem = new ItemStack(biomeDimension == CollectionGui.BiomeDimension.OVERWORLD ? Material.GRASS_BLOCK : Material.NETHERRACK);
        ItemMeta dimensionMeta = dimensionItem.getItemMeta();
        dimensionMeta.displayName(Component.text("Dimension: " + biomeDimension.name()).color(NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));
        dimensionMeta.lore(List.of(
            plugin.getLanguageManager().getMessage("biomeselectiongui.click_to_switch_overworldnether", "Click to switch Overworld/Nether").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ));
        dimensionItem.setItemMeta(dimensionMeta);
        inventory.setItem(5, dimensionItem);

        // Show only high-level biome filters (same set as the old front-page quick filters).
        List<BiomeGroup> groups = getVisibleGroups();
        
        // Simple grid layout starting from row 2 (index 9)
        int slot = 9;
        for (BiomeGroup group : groups) {
            if (slot >= 54) break; // Safety break

            ItemStack item = new ItemStack(getIconForBiome(group));
            ItemMeta meta = item.getItemMeta();
            String name = formatBiomeName(group);
            
            meta.displayName(Component.text(name).color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            
            List<Component> lore = new ArrayList<>();
            if (currentFilter == group) {
                lore.add(plugin.getLanguageManager().getMessage("biomeselectiongui.currently_selected", "▶ CURRENTLY SELECTED ◀").color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false));
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            } else {
                 lore.add(plugin.getLanguageManager().getMessage("biomeselectiongui.click_to_filter", "Click to filter").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slot++, item);
        }
        
        // Back Button
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(plugin.getLanguageManager().getMessage("biomeselectiongui.back", "Back").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inventory.setItem(49, back);
    }
    
    private Material getIconForBiome(BiomeGroup group) {
        switch (group) {
            case COLD_OCEAN: return Material.BLUE_ICE;
            case FROZEN_OCEAN: return Material.PACKED_ICE;
            case NORMAL_OCEAN: return Material.WATER_BUCKET;
            case LUKEWARM_OCEAN: return Material.TROPICAL_FISH_BUCKET; // Or kelp
            case WARM_OCEAN: return Material.BRAIN_CORAL;
            case RIVER: return Material.CLAY_BALL;
            case SWAMP: return Material.LILY_PAD;
            case BEACH: return Material.SAND;
            case LUSH_CAVES: return Material.MOSS_BLOCK;
            case FOREST: return Material.OAK_SAPLING;
            case TAIGA: return Material.SPRUCE_SAPLING;
            case SNOWY: return Material.SNOWBALL;
            case PLAINS: return Material.GRASS_BLOCK;
            case JUNGLE: return Material.BAMBOO;
            case SAVANNA: return Material.ACACIA_SAPLING;
            case DESERT: return Material.CACTUS;
            case MOUNTAINS: return Material.STONE;
            case BADLANDS: return Material.TERRACOTTA;
            case MUSHROOM: return Material.RED_MUSHROOM;
            case MEADOW: return Material.POPPY;
            case DEEP_DARK: return Material.SCULK;
            case PALE_GARDEN: return Material.WHITE_TULIP;
            case CRIMSON_FOREST: return Material.CRIMSON_NYLIUM;
            case WARPED_FOREST: return Material.WARPED_NYLIUM;
            case SOUL_SAND_VALLEY: return Material.SOUL_SAND;
            case BASALT_DELTAS: return Material.BASALT;
            case NETHER_WASTES: return Material.NETHERRACK;
            default: return Material.PAPER;
        }
    }
    
    private String formatBiomeName(BiomeGroup group) {
        String name = group.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 49) {
            // Back to Encyclopedia with current filter (or just ALL if they cancel? typically back means cancel action, so return to previous state)
            new CollectionGui(plugin, player, 0, currentFilter, sort, typeFilter, biomeDimension).open(player);
        } else if (slot == 4) {
            // ALL filter
            new CollectionGui(plugin, player, 0, null, sort, typeFilter, biomeDimension).open(player);
        } else if (slot == 5) {
            CollectionGui.BiomeDimension next = biomeDimension == CollectionGui.BiomeDimension.OVERWORLD
                    ? CollectionGui.BiomeDimension.NETHER
                    : CollectionGui.BiomeDimension.OVERWORLD;
            new BiomeSelectionGui(plugin, player, currentFilter, sort, typeFilter, next).open(player);
        } else {
            // Check if slot corresponds to a biome
             List<BiomeGroup> groups = getVisibleGroups();
             
             int index = slot - 9;
             if (index >= 0 && index < groups.size()) {
                 BiomeGroup selected = groups.get(index);
                 new CollectionGui(plugin, player, 0, selected, sort, typeFilter, biomeDimension).open(player);
             }
        }
    }

    private List<BiomeGroup> getVisibleGroups() {
        List<BiomeGroup> orderedGroups = biomeDimension == CollectionGui.BiomeDimension.NETHER
                ? NETHER_GROUP_ORDER
                : OVERWORLD_GROUP_ORDER;

        return orderedGroups.stream()
                .filter(group -> plugin.getBiomeFishingRegistry().has(group))
                .collect(Collectors.toList());
    }
}

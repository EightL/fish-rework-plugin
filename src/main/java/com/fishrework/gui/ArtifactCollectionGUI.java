package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.model.Artifact;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Rarity;
import com.fishrework.model.Skill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Artifact Collection GUI — displays all artifacts grouped by rarity.
 * Collected artifacts show their custom head/item; uncollected show mystery panes.
 */
public class ArtifactCollectionGUI extends BaseGUI {

    private final Player player;
    private final Rarity filter;

    /** Slots for artifact display (3 rows × 7 columns). */
    private static final int[] ARTIFACT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    public ArtifactCollectionGUI(FishRework plugin, Player player) {
        this(plugin, player, null);
    }

    public ArtifactCollectionGUI(FishRework plugin, Player player, Rarity filter) {
        super(plugin, 6, "Artifact Collection");
        this.player = player;
        this.filter = filter;
        initializeItems();
    }

    private void initializeItems() {
        // Fill background
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        
        // Filter artifacts
        List<Artifact> allArtifacts = plugin.getArtifactRegistry().getAll();
        List<Artifact> displayArtifacts = new ArrayList<>();
        if (filter == null) {
            displayArtifacts.addAll(allArtifacts);
        } else {
            for (Artifact a : allArtifacts) {
                if (a.getRarity() == filter) {
                    displayArtifacts.add(a);
                }
            }
        }

        // Artifact chance from config
        double artifactChance = plugin.getConfig().getDouble("artifacts.chance", 3.0);

        int collected = 0;
        int total = allArtifacts.size(); // Total is always global total
        int filteredCollected = 0; // Track collected for this view as well if needed, but header shows global usually

        // Calc global collected count
        for (Artifact a : allArtifacts) {
             if (data != null && data.hasArtifact(a.getId())) collected++;
        }

        for (int i = 0; i < displayArtifacts.size() && i < ARTIFACT_SLOTS.length; i++) {
            Artifact artifact = displayArtifacts.get(i);
            boolean hasIt = data != null && data.hasArtifact(artifact.getId());

            if (hasIt) {
                // Show the actual artifact item
                ItemStack item = plugin.getItemManager().createArtifactItem(artifact);
                inventory.setItem(ARTIFACT_SLOTS[i], item);
            } else {
                // Mystery pane
                ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text("???").color(NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Undiscovered Artifact").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.empty());
                lore.add(Component.text("Rarity: ").color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(artifact.getRarity().name()).color(artifact.getRarity().getColor())
                                .decoration(TextDecoration.BOLD, true)));
                lore.add(Component.empty());
                lore.add(Component.text("Found in " + artifact.getRarity().name() + " Treasure Chests")
                        .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                item.setItemMeta(meta);
                inventory.setItem(ARTIFACT_SLOTS[i], item);
            }
        }

        // Header - title info (slot 4)
        ItemStack header = new ItemStack(Material.NETHER_STAR);
        ItemMeta headerMeta = header.getItemMeta();
        headerMeta.displayName(Component.text("\u2B50 Artifact Collection").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        List<Component> headerLore = new ArrayList<>();
        headerLore.add(Component.empty());
        headerLore.add(Component.text("Collected: " + collected + "/" + total).color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        headerLore.add(Component.text("Artifact Chance: " + com.fishrework.util.FormatUtil.format("%.1f%%", artifactChance))
                .color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        headerLore.add(Component.empty());
        headerLore.add(Component.text("Rare collectibles found in").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        headerLore.add(Component.text("treasure chests while fishing.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        headerMeta.lore(headerLore);
        header.setItemMeta(headerMeta);
        inventory.setItem(4, header);



        // Decorative fill for remaining header slots
        ItemStack headerFill = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta hfMeta = headerFill.getItemMeta();
        hfMeta.displayName(Component.text(" "));
        headerFill.setItemMeta(hfMeta);
        for (int slot : new int[]{0, 1, 2, 3, 5, 6, 7, 8}) {
            inventory.setItem(slot, headerFill);
        }

        // Back button (slot 48)
        setBackButton(48);
        
        // Filter Button (Slot 50)
        ItemStack filterItem = new ItemStack(Material.HOPPER);
        ItemMeta filterMeta = filterItem.getItemMeta();
        String filterName = (filter == null) ? "ALL" : filter.name();
        net.kyori.adventure.text.format.TextColor filterColor = (filter == null) ? NamedTextColor.WHITE : filter.getColor();
        
        filterMeta.displayName(Component.text("Filter: ").color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(filterName).color(filterColor).decoration(TextDecoration.BOLD, true)));
        
        List<Component> filterLore = new ArrayList<>();
        filterLore.add(Component.text("Click to cycle rarity").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        filterMeta.lore(filterLore);
        filterItem.setItemMeta(filterMeta);
        inventory.setItem(50, filterItem);
        
        // Page Info (Slot 49) - adapted strictly for display
        ItemStack pageInfo = new ItemStack(Material.BOOK);
        ItemMeta pageMeta = pageInfo.getItemMeta();
        pageMeta.displayName(Component.text("Stats").color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> pageLore = new ArrayList<>();
        pageLore.add(Component.text("Showing: " + filterName).color(NamedTextColor.GRAY)
                 .decoration(TextDecoration.ITALIC, false));
        pageLore.add(Component.text("Count: " + displayArtifacts.size()).color(NamedTextColor.GRAY)
                 .decoration(TextDecoration.ITALIC, false));
        pageMeta.lore(pageLore);
        pageInfo.setItemMeta(pageMeta);
        inventory.setItem(49, pageInfo);
    }



    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 48) {
            // Back to Skill Detail
            new SkillDetailGUI(plugin, player, Skill.FISHING).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        } else if (slot == 50) {
            // Cycle Filter
            // Order: ALL (null) -> COMMON -> UNCOMMON -> RARE -> EPIC -> LEGENDARY -> ALL
            Rarity nextFilter = null;
            if (filter == null) {
                nextFilter = Rarity.COMMON;
            } else {
                int ord = filter.ordinal();
                Rarity[] values = Rarity.values();
                if (ord < values.length - 1) {
                    nextFilter = values[ord + 1];
                } else {
                    nextFilter = null; // Back to ALL
                }
            }
            
            new ArtifactCollectionGUI(plugin, player, nextFilter).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        }
    }
}

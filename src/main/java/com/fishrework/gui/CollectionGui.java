package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.model.CustomMob;
import com.fishrework.model.PlayerData;
import com.fishrework.model.BiomeFishingProfile;
import com.fishrework.model.BiomeGroup;
import com.fishrework.model.Skill;
import com.fishrework.MobManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.fishrework.model.CustomMob.MobCategory;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fish Collection GUI — auto-populated from MobRegistry.
 * Adding a new mob to the registry automatically adds it here.
 * Supports pagination for large collections.
 */
public class CollectionGui extends BaseGUI {

    private final Player player;
    private int page;
    private final Map<String, double[]> collection;
    private BiomeGroup filter;
    private MobCategory typeFilter;
    private SortType sort;
    private BiomeDimension biomeDimension;
    private boolean showCatchLeaders;
    private int totalPages = 1;
    private boolean initialized;

    /** Slots used for mob display (3 rows × 7 columns). */
    private static final int[] MOB_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int[] CONTROL_SLOTS = {45, 47, 48, 49, 50, 51, 53};
    private static final int MOBS_PER_PAGE = MOB_SLOTS.length; // 21

    public CollectionGui(FishRework plugin, Player player) {
        this(plugin, player, 0, null, SortType.XP_ASC, null, BiomeDimension.OVERWORLD);
    }

    public CollectionGui(FishRework plugin, Player player, int page) {
        this(plugin, player, page, null, SortType.XP_ASC, null, BiomeDimension.OVERWORLD);
    }
    
    public CollectionGui(FishRework plugin, Player player, BiomeGroup filter) {
        this(plugin, player, 0, filter, SortType.XP_ASC, null, BiomeDimension.OVERWORLD);
    }

    public CollectionGui(FishRework plugin, Player player, int page, BiomeGroup filter) {
        this(plugin, player, page, filter, SortType.XP_ASC, null, BiomeDimension.OVERWORLD);
    }

    public CollectionGui(FishRework plugin, Player player, int page, BiomeGroup filter, SortType sort) {
        this(plugin, player, page, filter, sort, null, BiomeDimension.OVERWORLD);
    }

    public CollectionGui(FishRework plugin, Player player, int page, BiomeGroup filter, SortType sort, MobCategory typeFilter) {
        this(plugin, player, page, filter, sort, typeFilter, BiomeDimension.OVERWORLD);
    }

    public CollectionGui(FishRework plugin, Player player, int page, BiomeGroup filter, SortType sort, MobCategory typeFilter, BiomeDimension biomeDimension) {
        this(plugin, player, page, filter, sort, typeFilter, biomeDimension, false);
    }

    public CollectionGui(FishRework plugin, Player player, int page, BiomeGroup filter, SortType sort, MobCategory typeFilter,
                         BiomeDimension biomeDimension, boolean showCatchLeaders) {
        super(plugin, 6, localizedTitle(plugin, "collectiongui.title", "Fishing Encyclopedia"));
        this.player = player;
        this.page = page;
        this.filter = filter;
        this.sort = sort;
        this.typeFilter = typeFilter;
        this.biomeDimension = biomeDimension;
        this.showCatchLeaders = showCatchLeaders;
        this.collection = plugin.getDatabaseManager().loadCollection(player.getUniqueId());
        initializeItems();
    }
    
    public enum SortType {
        XP_ASC, XP_DESC,
        CHANCE_ASC, CHANCE_DESC,
        AMOUNT_ASC, AMOUNT_DESC
    }

    public enum BiomeDimension {
        OVERWORLD,
        NETHER
    }

    private void initializeItems() {
        refreshDynamicSlots();

        // Get filtered mobs
        List<CustomMob> filteredMobs = getFilteredMobs();
        
        // Calculate chances (True percentages)
        Map<String, Double> mobChances = calculateMobChances(filteredMobs);
        
        // SORTING LOGIC
        filteredMobs.sort((m1, m2) -> {
            int result = 0;
            switch (sort) {
                case XP_ASC:
                    result = Double.compare(m1.getXp(), m2.getXp());
                    break;
                case XP_DESC:
                    result = Double.compare(m2.getXp(), m1.getXp());
                    break;
                case CHANCE_ASC:
                    result = Double.compare(mobChances.getOrDefault(m1.getId(), 0.0), mobChances.getOrDefault(m2.getId(), 0.0));
                    break;
                case CHANCE_DESC:
                    result = Double.compare(mobChances.getOrDefault(m2.getId(), 0.0), mobChances.getOrDefault(m1.getId(), 0.0));
                    break;
                case AMOUNT_ASC:
                     int c1 = collection.containsKey(m1.getId()) ? (int)collection.get(m1.getId())[0] : 0;
                     int c2 = collection.containsKey(m2.getId()) ? (int)collection.get(m2.getId())[0] : 0;
                     result = Integer.compare(c1, c2);
                     break;
                case AMOUNT_DESC:
                     int c3 = collection.containsKey(m1.getId()) ? (int)collection.get(m1.getId())[0] : 0;
                     int c4 = collection.containsKey(m2.getId()) ? (int)collection.get(m2.getId())[0] : 0;
                     result = Integer.compare(c4, c3);
                     break;
            }
            return result;
        });

        // XP Multiplier for display
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        int level = data != null ? data.getLevel(Skill.FISHING) : 0;
        double xpMulti = plugin.getLevelManager().getXpMultiplier(level);
        boolean calculatePercents = (filter != null);

        totalPages = Math.max(1, (int) Math.ceil((double) filteredMobs.size() / MOBS_PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;

        int startIndex = page * MOBS_PER_PAGE;
        int endIndex = Math.min(startIndex + MOBS_PER_PAGE, filteredMobs.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slotIdx = i - startIndex;
            if (slotIdx >= MOB_SLOTS.length) break;
            CustomMob mob = filteredMobs.get(i);

            if (collection.containsKey(mob.getId())) {
                // Unlocked
                double[] stats = collection.get(mob.getId());
                int count = (int) stats[0];
                double maxWeight = stats[1];

                String countLabel = mob.isHostile() ? "Killed: " : (mob.isTreasure() ? "Found: " : "Caught: ");

                // ── QOL: Check if this is a recent discovery (NEW indicator) ──
                boolean isNew = false;
                PlayerData pData = plugin.getPlayerData(player.getUniqueId());
                if (pData != null && pData.getSession().getRecentDiscoveries().contains(mob.getId())) {
                    isNew = true;
                }

                ItemStack item = new ItemStack(mob.getCollectionIcon());
                ItemMeta meta = item.getItemMeta();
                TextColor nameColor = mob.getRarity() != null ? mob.getRarity().getColor() : TextColor.color(NamedTextColor.WHITE);
                TextColor iconColor = mob.isHostile() ? NamedTextColor.RED : NamedTextColor.WHITE;
                Component displayName = Component.text(mob.getIcon() + " ").color(iconColor)
                        .append(Component.text(mob.getLocalizedCollectionName(plugin.getLanguageManager())).color(nameColor))
                        .decoration(TextDecoration.ITALIC, false);
                if (isNew) {
                    displayName = plugin.getLanguageManager().getMessage("collectiongui.u2b50_new", "\u2B50 NEW! ").color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false)
                            .decoration(TextDecoration.BOLD, true)
                            .append(displayName);
                }
                meta.displayName(displayName);
                        
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(countLabel + count).color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));

                if (showCatchLeaders) {
                    appendCatchLeaderLore(lore, mob);
                }

                // Every registered non-treasure creature now participates in the shared weight profile.
                if (!mob.isTreasure()) {
                    lore.add(buildMaxWeightLine(mob, maxWeight));
                }
                
                lore.add(Component.text(""));
                
                // --- XP & Chance Info ---
                
                // XP
                double baseXp = mob.getXp();
                double finalXp = baseXp * xpMulti;
                Component xpLine = plugin.getLanguageManager().getMessage("collectiongui.xp", "XP: ").color(NamedTextColor.GRAY)
                        .append(Component.text(com.fishrework.util.FormatUtil.format("%.0f", baseXp)).color(NamedTextColor.YELLOW));
                if (xpMulti > 1.0) {
                     xpLine = xpLine.append(Component.text(com.fishrework.util.FormatUtil.format(" (x%.2f = %.0f)", xpMulti, finalXp))
                        .color(NamedTextColor.AQUA));
                }
                lore.add(xpLine.decoration(TextDecoration.ITALIC, false));
                
                // Chance
                Component chanceLine = plugin.getLanguageManager().getMessage("collectiongui.chance", "Chance: ").color(NamedTextColor.GRAY);
                if (calculatePercents) {
                    // Use calculated percentage directly
                    double pct = mobChances.getOrDefault(mob.getId(), 0.0);
                    chanceLine = chanceLine.append(Component.text(plugin.getLanguageManager().getString(
                                    "collectiongui.chance_in_biome",
                                    "%chance% (in %biome%)",
                                    "chance", com.fishrework.util.FormatUtil.format("%.2f%%", pct),
                                    "biome", filter.getLocalizedName(plugin.getLanguageManager())))
                            .color(NamedTextColor.GREEN));
                            
                    // Land Mob Special Case Indicator
                    com.fishrework.model.BiomeFishingProfile pf = plugin.getBiomeFishingRegistry().get(filter);
                    if (pf != null && pf.getLandMobs().contains(mob.getId())) {
                        chanceLine = chanceLine.append(plugin.getLanguageManager().getMessage("collectiongui.land_bonus", " [Land Bonus]").color(NamedTextColor.GOLD));
                    }
                    
                } else {
                    // Global view -> Show Base Weight
                    double base = mob.getDefaultChance();
                    if (base > 0) {
                         chanceLine = chanceLine.append(Component.text(plugin.getLanguageManager().getString(
                                         "collectiongui.base_weight",
                                         "Base Weight %weight%",
                                         "weight", String.valueOf(base)))
                                 .color(NamedTextColor.DARK_GRAY));
                    } else {
                         chanceLine = chanceLine.append(plugin.getLanguageManager().getMessage("collectiongui.biome_specific", "Biome Specific").color(NamedTextColor.DARK_GRAY));
                    }
                }
                lore.add(chanceLine.decoration(TextDecoration.ITALIC, false));
                // ------------------------

                // Treasures: Add loot table to lore
                if (mob.isTreasure()) {
                    lore.add(Component.empty());
                    boolean isNether = mob.getId().startsWith("nether_");
                    lore.addAll(plugin.getTreasureManager().getLootTable(mob.getRarity(), isNether));
                }
                
                // Mob Drops
                if (!mob.isTreasure() && plugin.getMobRegistry().get(mob.getId()) != null && !mob.getDrops().isEmpty()) {
                     List<Component> dropsLore = new ArrayList<>();
                     dropsLore.add(Component.empty());
                     dropsLore.add(plugin.getLanguageManager().getMessage("collectiongui.drops", "Drops:").color(NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false));
                    
                    boolean hasDrops = false;
                    for (com.fishrework.model.MobDrop drop : mob.getDrops()) {
                        ItemStack dropItem = drop.getSampleItem();
                        if (dropItem != null) {
                            hasDrops = true;
                            // Resolve Name
                            String name;
                            if (dropItem.hasItemMeta() && dropItem.getItemMeta().hasDisplayName()) {
                                name = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().serialize(dropItem.getItemMeta().displayName());
                                // Strip color codes if needed, or keep them. Let's keep textual part.
                                name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(dropItem.getItemMeta().displayName());
                            } else {
                                // Friendly name from type
                                String typeName = dropItem.getType().name().toLowerCase().replace('_', ' ');
                                StringBuilder sb = new StringBuilder();
                                for (String word : typeName.split(" ")) {
                                    if (!word.isEmpty()) {
                                        sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
                                    }
                                }
                                name = sb.toString().trim();
                            }

                            // Format: - [Name] (x-y) [Chance]%
                            // Formatting range
                            String rangeStr = (drop.getMinAmount() == drop.getMaxAmount()) ? 
                                    String.valueOf(drop.getMinAmount()) : 
                                    (drop.getMinAmount() + "-" + drop.getMaxAmount());
                            
                            // Runtime chance now depends on the caught creature's sampled weight profile.
                            String chanceStr = formatWeightAdjustedDropChance(drop.getChance());
                            
                            dropsLore.add(Component.text("- " + name + " (" + rangeStr + ") " + chanceStr)
                                    .color(NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false));
                        }
                    }
                    if (hasDrops) {
                        lore.addAll(dropsLore);
                    }
                }

                meta.lore(lore);
                item.setItemMeta(meta);
                inventory.setItem(MOB_SLOTS[slotIdx], item);
            } else {
                ItemStack item = new ItemStack(mob.isTreasure() ? mob.getCollectionIcon() : Material.RED_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                if (mob.isTreasure()) {
                    TextColor nameColor = mob.getRarity() != null ? mob.getRarity().getColor() : TextColor.color(NamedTextColor.GOLD);
                    meta.displayName(Component.text(mob.getIcon() + " ").color(NamedTextColor.GOLD)
                            .append(Component.text(mob.getLocalizedCollectionName(plugin.getLanguageManager())).color(nameColor))
                            .decoration(TextDecoration.ITALIC, false));
                    meta.lore(List.of(
                            plugin.getLanguageManager().getMessage("collectiongui.locked_treasure_hint", "Find this treasure chest to unlock details.")
                                    .color(NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false)
                    ));
                } else {
                    meta.displayName(plugin.getLanguageManager().getMessage("collectiongui.locked_item_name", "???").color(NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false));
                    String hint = mob.isHostile() ? "Kill this creature to unlock!" : "Catch this fish to unlock!";
                    meta.lore(List.of(Component.text(hint).color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
                }
                item.setItemMeta(meta);
                inventory.setItem(MOB_SLOTS[slotIdx], item);
            }
        }

        // Page info
        int caught = (int) filteredMobs.stream().filter(m -> collection.containsKey(m.getId())).count();
        ItemStack pageInfo = new ItemStack(Material.BOOK);
        ItemMeta pageMeta = pageInfo.getItemMeta();
        pageMeta.displayName(Component.text(plugin.getLanguageManager().getString("pagination.page", "Page ") + (page + 1) + "/" + totalPages).color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        pageMeta.lore(List.of(Component.text(plugin.getLanguageManager().getString(
                        "collectiongui.collected_count",
                        "Collected: %count%/%total%",
                        "count", String.valueOf(caught),
                        "total", String.valueOf(filteredMobs.size()))).color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        pageInfo.setItemMeta(pageMeta);
        inventory.setItem(49, pageInfo);

        // Navigation
        setPaginationControls(45, 53, page, totalPages);

        // Other-player catches toggle (top-right)
        ItemStack catchLeaderToggle = new ItemStack(showCatchLeaders ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta catchLeaderToggleMeta = catchLeaderToggle.getItemMeta();
        catchLeaderToggleMeta.displayName(plugin.getLanguageManager().getMessage(
                        "collectiongui.other_player_catches",
                        "Other Player Catches")
                .color(showCatchLeaders ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        catchLeaderToggleMeta.lore(List.of(
                plugin.getLanguageManager().getMessage(
                                showCatchLeaders
                                        ? "collectiongui.hide_other_player_catches"
                                        : "collectiongui.show_other_player_catches",
                                showCatchLeaders
                                        ? "Hide stored player catch leaders"
                                        : "Show stored player catch leaders")
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        catchLeaderToggle.setItemMeta(catchLeaderToggleMeta);
        inventory.setItem(8, catchLeaderToggle);

        // Back button
        setBackButton(48);

        // Type Filter Button (Slot 47)
        ItemStack typeFilterItem = new ItemStack(Material.SPYGLASS);
        ItemMeta typeFilterMeta = typeFilterItem.getItemMeta();
        String typeName = (typeFilter == null)
                ? plugin.getLanguageManager().getString("collectiongui.all", "ALL")
                : localizedTypeName(typeFilter);
        NamedTextColor typeColor = typeFilter == null ? NamedTextColor.WHITE
                : (typeFilter == MobCategory.HOSTILE ? NamedTextColor.RED
                : (typeFilter == MobCategory.TREASURE ? NamedTextColor.GOLD
                : NamedTextColor.AQUA));
        typeFilterMeta.displayName(Component.text(plugin.getLanguageManager().getString("collectiongui.type_prefix", "Type: ") + typeName).color(typeColor)
                .decoration(TextDecoration.ITALIC, false));
        typeFilterMeta.lore(List.of(
            plugin.getLanguageManager().getMessage("collectiongui.click_to_filter_by_type", "Click to filter by type").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ));
        typeFilterItem.setItemMeta(typeFilterMeta);
        inventory.setItem(47, typeFilterItem);
        
        // Filter Button (Slot 50)
        ItemStack filterItem = new ItemStack(Material.HOPPER);
        ItemMeta filterMeta = filterItem.getItemMeta();
        String filterName = (filter == null)
                ? plugin.getLanguageManager().getString("collectiongui.all", "ALL")
                : filter.getLocalizedName(plugin.getLanguageManager());
        filterMeta.displayName(Component.text(plugin.getLanguageManager().getString("collectiongui.filter_prefix", "Filter: ") + filterName).color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        filterMeta.lore(List.of(
            plugin.getLanguageManager().getMessage("collectiongui.click_to_change_biome_filter", "Click to change biome filter").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ));
        filterItem.setItemMeta(filterMeta);
        inventory.setItem(50, filterItem);
        
        // Sort Button (Slot 51)
        ItemStack sortItem = new ItemStack(Material.COMPARATOR);
        ItemMeta sortMeta = sortItem.getItemMeta();
        String sortName = localizedSortName(sort);
        sortMeta.displayName(Component.text(plugin.getLanguageManager().getString("collectiongui.sort_prefix", "Sort: ") + sortName).color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        sortMeta.lore(List.of(
            plugin.getLanguageManager().getMessage("collectiongui.click_to_change_sort_order", "Click to change sort order").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text(plugin.getLanguageManager().getString("collectiongui.current_sort_prefix", "Current: ") + sortName).color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        ));
        sortItem.setItemMeta(sortMeta);
        inventory.setItem(51, sortItem);
        
        // Front page intentionally omits quick biome tiles to reduce clutter.
        initialized = true;
    }

    private void refreshDynamicSlots() {
        if (!initialized) {
            fillBackground(Material.GRAY_STAINED_GLASS_PANE);
            return;
        }

        ItemStack filler = createFiller();
        for (int slot : MOB_SLOTS) {
            inventory.setItem(slot, filler);
        }
        for (int slot : CONTROL_SLOTS) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack createFiller() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.empty());
        filler.setItemMeta(meta);
        return filler;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 48) {
            // Back
            new SkillDetailGUI(plugin, player, Skill.FISHING).open(player);
            playClick();
        } else if (slot == 47) {
            // Type Filter cycle: ALL -> PASSIVE -> HOSTILE -> TREASURE -> ALL
            MobCategory nextType = null;
            if (typeFilter == null) {
                nextType = MobCategory.PASSIVE;
            } else {
                switch (typeFilter) {
                    case PASSIVE: nextType = MobCategory.HOSTILE; break;
                    case HOSTILE: nextType = MobCategory.TREASURE; break;
                    case TREASURE: nextType = null; break;
                }
            }
            typeFilter = nextType;
            page = 0;
            initializeItems();
            playClick();
        } else if (slot == 50) {
             // Open Filter Selection (Reset page to 0, keep sort?) - Usually good to reset sort or keep it? Keeping it.
             new BiomeSelectionGui(plugin, player, filter, sort, typeFilter, biomeDimension, showCatchLeaders).open(player);
             playClick();
        } else if (slot == 8) {
            showCatchLeaders = !showCatchLeaders;
            initializeItems();
            playClick();
        } else if (slot == 45) {
            // Previous page
            if (page > 0) {
                page--;
                initializeItems();
                playClick();
            }
        } else if (slot == 51) {
            // Sort Button
            SortType[] values = SortType.values();
            int nextOrdinal = (sort.ordinal() + 1) % values.length;
            sort = values[nextOrdinal];
            initializeItems();
            playClick();
        } else if (slot == 53) {
            // Next page
            if (page < totalPages - 1) {
                page++;
                initializeItems();
                playClick();
            }
        }
    }

    private void playClick() {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    private void appendCatchLeaderLore(List<Component> lore, CustomMob mob) {
        LinkedHashMap<UUID, Long> leaders = plugin.getDatabaseManager().getTopCollectorsForMob(mob.getId(), 5);
        lore.add(Component.empty());
        lore.add(plugin.getLanguageManager().getMessage(
                        "collectiongui.other_player_catch_leaders",
                        "Other player catches:")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        if (leaders.isEmpty()) {
            lore.add(plugin.getLanguageManager().getMessage(
                            "collectiongui.no_other_player_catches",
                            "No stored catches yet.")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            return;
        }

        int rank = 1;
        for (Map.Entry<UUID, Long> entry : leaders.entrySet()) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
            String name = offlinePlayer.getName();
            if (name == null || name.isBlank()) {
                name = plugin.getLanguageManager().getString("collectiongui.unknown_player", "Unknown");
            }
            String line = plugin.getLanguageManager().getString(
                    "collectiongui.other_player_catch_entry",
                    "#%rank% %name%: %count%",
                    "rank", String.valueOf(rank),
                    "name", name,
                    "count", String.valueOf(entry.getValue()));
            lore.add(Component.text(line).color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            rank++;
        }
    }

    private List<CustomMob> getFilteredMobs() {
        List<CustomMob> allMobs = plugin.getMobRegistry().getBySkill(Skill.FISHING);
        List<CustomMob> filtered = new ArrayList<>();
        
        if (filter == null) {
            filtered.addAll(allMobs);
        } else {
            com.fishrework.model.BiomeFishingProfile profile = plugin.getBiomeFishingRegistry().get(filter);
            for (CustomMob mob : allMobs) {
                boolean keep = false;
                
                if (mob.isHostile()) {
                    if (profile != null) {
                        // If profile has custom hostile weights, only show those
                        if (!profile.getHostileWeights().isEmpty()) {
                            if (profile.hasHostileWeight(mob.getId())) {
                                keep = true;
                            }
                        } else {
                            // Profile uses default hostiles -> show all hostiles
                            keep = true;
                        }
                    } else {
                        // No filter or no profile -> show all hostiles
                        keep = true;
                    }
                } else if (mob.isTreasure()) {
                    keep = true;
                } else {
                    if (profile != null) {
                        if (profile.hasWeight(mob.getId()) && profile.getWeight(mob.getId()) > 0) {
                            keep = true;
                        } else if (profile.getLandMobs().contains(mob.getId())) {
                            keep = true;
                        }
                    } 
                    
                    if (!keep) {
                         if (profile == null || !profile.hasWeight(mob.getId())) {
                             if (mob.getDefaultChance() > 0) {
                                 keep = true;
                             }
                         }
                    }
                }
                
                if (keep) {
                    filtered.add(mob);
                }
            }
        }

        // Apply type filter (now applies to ALL filter as well)
        if (typeFilter != null) {
            filtered.removeIf(mob -> mob.getCategory() != typeFilter);
        }

        return filtered;
    }
    private Map<String, Double> calculateMobChances(List<CustomMob> mobs) {
        Map<String, Double> chances = new HashMap<>();

        // Context
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        int level = data != null ? data.getLevel(Skill.FISHING) : 0;
        
        com.fishrework.model.BiomeFishingProfile profile = (filter != null) ? plugin.getBiomeFishingRegistry().get(filter) : null;
        
        boolean hasHarmony = false;
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (plugin.getItemManager().isHarmonyRod(main) || plugin.getItemManager().isHarmonyRod(off)) {
            hasHarmony = true;
        }

        double rareBonus = plugin.getMobManager().getEquipmentRareCreatureBonus(player);
        double treasureBonus = plugin.getMobManager().getTreasureChance(player);
        
        // --- 1. Land Mob Chance (Pre-Roll) ---
        double landRollChance = 0.0;
        int eligibleLandMobs = 0;
        
        if (profile != null && !profile.getLandMobs().isEmpty()) {
            landRollChance = plugin.getMobManager().getEffectiveLandMobChance(profile);
            if (hasHarmony) {
                landRollChance *= 2.0;
            }
            eligibleLandMobs = profile.getLandMobs().size();
            
            if (eligibleLandMobs > 0) {
                double perMobChance = landRollChance / eligibleLandMobs;
                for (String mobId : profile.getLandMobs()) {
                    chances.put(mobId, perMobChance);
                }
            }
        }
        
        // --- 2. Main Pool Weights from MobManager ---
        // We use player location or filter location. If filter is set, we need a dummy location in that biome?
        // Actually buildWeightMap takes a Location BUT uses it for:
        // 1. Biome (we pass profile directly, so this is used if profile is null - handled)
        // 2. Y-level (Glow Squid check) -> We can just pass null location if we don't know, OR player location.
        // Let's use player location but force the profile.
        
        Map<String, Double> rawWeights = plugin.getMobManager().buildWeightMap(
            Skill.FISHING, 
            level, 
            1.0 + (rareBonus / 100.0), 
            1.0 + (treasureBonus / 100.0), 
            profile, 
            player.getLocation(), // Use player location for day/night check primarily. 
            hasHarmony
        );
        
        // --- 3. Final Probability Calculation ---
        // Probability(MainPool runs) = 1.0 - (landRollChance / 100.0)
        double mainPoolProbability = Math.max(0.0, 1.0 - (landRollChance / 100.0));
        double totalWeight = rawWeights.values().stream().mapToDouble(d -> d).sum();

        if (totalWeight > 0) {
            for (Map.Entry<String, Double> entry : rawWeights.entrySet()) {
                String id = entry.getKey();
                double weight = entry.getValue();
                
                // Chance within the pool
                double poolChance = (weight / totalWeight);
                
                // Global chance = PoolChance * MainPoolProbability
                double globalChance = poolChance * mainPoolProbability * 100.0;
                
                // Add to existing chance (if it was somehow also a land mob, though unlikely)
                chances.merge(id, globalChance, Double::sum);
            }
        }
        
        // Ensure all mobs have an entry to avoid NPEs if logic expects it
        for (CustomMob mob : mobs) {
            chances.putIfAbsent(mob.getId(), 0.0);
        }
        
        return chances;
    }

    private String localizedTypeName(MobCategory category) {
        return plugin.getLanguageManager().getString(
                "collectiongui.type." + category.name().toLowerCase(),
                formatEnumLabel(category.name()));
    }

    private String localizedSortName(SortType sortType) {
        return plugin.getLanguageManager().getString(
                "collectiongui.sort." + sortType.name().toLowerCase(),
                formatEnumLabel(sortType.name()));
    }

    private String formatEnumLabel(String rawName) {
        return Arrays.stream(rawName.toLowerCase().split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .reduce((left, right) -> left + " " + right)
                .orElse(rawName);
    }

    private String formatDropChance(double chance) {
        double percent = Math.max(0.0, chance * 100.0);
        if (percent >= 10.0) return com.fishrework.util.FormatUtil.format("%.0f%%", percent);
        if (percent >= 1.0) return com.fishrework.util.FormatUtil.format("%.1f%%", percent);
        if (percent > 0.0) return com.fishrework.util.FormatUtil.format("%.2f%%", percent);
        return "0%";
    }

    private Component buildMaxWeightLine(CustomMob mob, double maxWeight) {
        String template = plugin.getLanguageManager().getString(
                "collectiongui.max_weight",
                "Max Weight: %weight%kg");
        String token = "%weight%";
        int tokenIndex = template.indexOf(token);
        String formattedWeight = com.fishrework.util.FormatUtil.format("%.2f", maxWeight);
        Component coloredWeight = Component.text(formattedWeight + "kg")
                .color(plugin.getMobManager().getWeightRarity(mob, maxWeight).getColor());

        if (tokenIndex < 0) {
            return Component.text(template + " ")
                    .color(NamedTextColor.YELLOW)
                    .append(coloredWeight)
                    .decoration(TextDecoration.ITALIC, false);
        }

        int suffixStart = tokenIndex + token.length();
        if (template.startsWith("kg", suffixStart)) {
            suffixStart += 2;
        }

        return Component.text(template.substring(0, tokenIndex))
                .color(NamedTextColor.YELLOW)
                .append(coloredWeight)
                .append(Component.text(template.substring(suffixStart)).color(NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false);
    }

    private String formatWeightAdjustedDropChance(double baseChance) {
        com.fishrework.model.SeaCreatureWeightProfile.Tuning tuning = plugin.getMobManager().getWeightProfileTuning();
        double minimum = baseChance * plugin.getMobManager().getDropRollMultiplierAtSize(tuning.minSizeMultiplier());
        double normal = baseChance * plugin.getMobManager().getDropRollMultiplierAtSize(1.0);
        double maximum = baseChance * plugin.getMobManager().getDropRollMultiplierAtSize(tuning.maxSizeMultiplier());
        return plugin.getLanguageManager().getString(
                "collectiongui.weight_adjusted_drop_chance",
                "%normal% at 1.0x, %min%-%max% by weight",
                "normal", formatDropChance(normal),
                "min", formatDropChance(minimum),
                "max", formatDropChance(maximum)
        );
    }
}

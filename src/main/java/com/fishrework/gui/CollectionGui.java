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
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.fishrework.model.CustomMob.MobCategory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fish Collection GUI — auto-populated from MobRegistry.
 * Adding a new mob to the registry automatically adds it here.
 * Supports pagination for large collections.
 */
public class CollectionGui extends BaseGUI {

    private final Player player;
    private int page;
    private final BiomeGroup filter;
    private final MobCategory typeFilter;
    private final SortType sort;
    private final BiomeDimension biomeDimension;

    /** Slots used for mob display (3 rows × 7 columns). */
    private static final int[] MOB_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
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
        super(plugin, 6, "Fishing Encyclopedia");
        this.player = player;
        this.page = page;
        this.filter = filter;
        this.sort = sort;
        this.typeFilter = typeFilter;
        this.biomeDimension = biomeDimension;
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
        // Fill background
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);

        // Load player's collection data
        Map<String, double[]> collection = plugin.getDatabaseManager().loadCollection(player.getUniqueId());

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

        int totalPages = Math.max(1, (int) Math.ceil((double) filteredMobs.size() / MOBS_PER_PAGE));
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
                NamedTextColor nameColor = mob.isHostile() ? NamedTextColor.RED : NamedTextColor.AQUA;
                Component displayName = Component.text(mob.getCollectionName()).color(nameColor)
                        .decoration(TextDecoration.ITALIC, false);
                if (isNew) {
                    displayName = Component.text("\u2B50 NEW! ").color(NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false)
                            .decoration(TextDecoration.BOLD, true)
                            .append(displayName);
                }
                meta.displayName(displayName);
                        
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(countLabel + count).color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));

                // --- Max Weight Attribute Cleanup ---
                // Show max weight ONLY if it's an aquatic mob AND not a treasure
                // Treasures and land mobs (Pigs, etc) don't have "Max Weight" logic that makes sense to users
                boolean isAquatic = plugin.getMobManager().isAquatic(mob.getEntityType());
                if (!mob.isTreasure() && isAquatic) {
                    lore.add(Component.text("Max Weight: " + String.format("%.2f", maxWeight) + "kg")
                            .color(NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false));
                }
                
                lore.add(Component.text(""));
                
                // --- XP & Chance Info ---
                
                // XP
                double baseXp = mob.getXp();
                double finalXp = baseXp * xpMulti;
                Component xpLine = Component.text("XP: ").color(NamedTextColor.GRAY)
                        .append(Component.text(String.format("%.0f", baseXp)).color(NamedTextColor.YELLOW));
                if (xpMulti > 1.0) {
                     xpLine = xpLine.append(Component.text(String.format(" (x%.2f = %.0f)", xpMulti, finalXp))
                        .color(NamedTextColor.AQUA));
                }
                lore.add(xpLine.decoration(TextDecoration.ITALIC, false));
                
                // Chance
                Component chanceLine = Component.text("Chance: ").color(NamedTextColor.GRAY);
                if (calculatePercents) {
                    // Use calculated percentage directly
                    double pct = mobChances.getOrDefault(mob.getId(), 0.0);
                    chanceLine = chanceLine.append(Component.text(String.format("%.2f%% (in %s)", pct, filter.name().replace('_', ' ')))
                            .color(NamedTextColor.GREEN));
                            
                    // Land Mob Special Case Indicator
                    com.fishrework.model.BiomeFishingProfile pf = plugin.getBiomeFishingRegistry().get(filter);
                    if (pf != null && pf.getLandMobs().contains(mob.getId())) {
                        chanceLine = chanceLine.append(Component.text(" [Land Bonus]").color(NamedTextColor.GOLD));
                    }
                    
                } else {
                    // Global view -> Show Base Weight
                    double base = mob.getDefaultChance();
                    if (base > 0) {
                         chanceLine = chanceLine.append(Component.text("Base Weight " + base).color(NamedTextColor.DARK_GRAY));
                    } else {
                         chanceLine = chanceLine.append(Component.text("Biome Specific").color(NamedTextColor.DARK_GRAY));
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
                if (plugin.getMobRegistry().get(mob.getId()) != null && !mob.getDrops().isEmpty()) {
                     List<Component> dropsLore = new ArrayList<>();
                     dropsLore.add(Component.empty());
                     dropsLore.add(Component.text("Drops:").color(NamedTextColor.GOLD)
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
                            
                            // Chance
                            String chanceStr = String.format("%.0f%%", drop.getChance() * 100);
                            
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
            } else { // Locked text logic (remains the same)               // Locked
                ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text("???").color(NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false));
                String hint = mob.isHostile() ? "Kill this creature to unlock!" : "Catch this fish to unlock!";
                meta.lore(List.of(Component.text(hint).color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
                item.setItemMeta(meta);
                inventory.setItem(MOB_SLOTS[slotIdx], item);
            }
        }

        // Page info
        int caught = (int) filteredMobs.stream().filter(m -> collection.containsKey(m.getId())).count();
        ItemStack pageInfo = new ItemStack(Material.BOOK);
        ItemMeta pageMeta = pageInfo.getItemMeta();
        pageMeta.displayName(Component.text("Page " + (page + 1) + "/" + totalPages).color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        pageMeta.lore(List.of(Component.text("Collected: " + caught + "/" + filteredMobs.size()).color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        pageInfo.setItemMeta(pageMeta);
        inventory.setItem(49, pageInfo);

        // Navigation
        setPaginationControls(45, 53, page, totalPages);

        // Back button
        setBackButton(48);

        // Type Filter Button (Slot 47)
        ItemStack typeFilterItem = new ItemStack(Material.SPYGLASS);
        ItemMeta typeFilterMeta = typeFilterItem.getItemMeta();
        String typeName = (typeFilter == null) ? "ALL" : typeFilter.name();
        NamedTextColor typeColor = typeFilter == null ? NamedTextColor.WHITE
                : (typeFilter == MobCategory.HOSTILE ? NamedTextColor.RED
                : (typeFilter == MobCategory.TREASURE ? NamedTextColor.GOLD
                : NamedTextColor.AQUA));
        typeFilterMeta.displayName(Component.text("Type: " + typeName).color(typeColor)
                .decoration(TextDecoration.ITALIC, false));
        typeFilterMeta.lore(List.of(
            Component.text("Click to filter by type").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ));
        typeFilterItem.setItemMeta(typeFilterMeta);
        inventory.setItem(47, typeFilterItem);
        
        // Filter Button (Slot 50)
        ItemStack filterItem = new ItemStack(Material.HOPPER);
        ItemMeta filterMeta = filterItem.getItemMeta();
        String filterName = (filter == null) ? "ALL" : filter.name().replace('_', ' ');
        filterMeta.displayName(Component.text("Filter: " + filterName).color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        filterMeta.lore(List.of(
            Component.text("Click to change biome filter").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ));
        filterItem.setItemMeta(filterMeta);
        inventory.setItem(50, filterItem);
        
        // Sort Button (Slot 51)
        ItemStack sortItem = new ItemStack(Material.COMPARATOR);
        ItemMeta sortMeta = sortItem.getItemMeta();
        sortMeta.displayName(Component.text("Sort: " + sort.name()).color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        sortMeta.lore(List.of(
            Component.text("Click to change sort order").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("Current: " + sort.name().replace('_', ' ')).color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        ));
        sortItem.setItemMeta(sortMeta);
        inventory.setItem(51, sortItem);
        
        // Front page intentionally omits quick biome tiles to reduce clutter.
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 48) {
            // Back
            new SkillDetailGUI(plugin, player, Skill.FISHING).open(player);
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
            new CollectionGui(plugin, player, 0, filter, sort, nextType, biomeDimension).open(player);
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
        } else if (slot == 50) {
             // Open Filter Selection (Reset page to 0, keep sort?) - Usually good to reset sort or keep it? Keeping it.
             new BiomeSelectionGui(plugin, player, filter, sort, typeFilter, biomeDimension).open(player);
        } else if (slot == 45) {
            // Previous page
            if (page > 0) {
                new CollectionGui(plugin, player, page - 1, filter, sort, typeFilter, biomeDimension).open(player);
            }
        } else if (slot == 51) {
            // Sort Button
            SortType[] values = SortType.values();
            int nextOrdinal = (sort.ordinal() + 1) % values.length;
            new CollectionGui(plugin, player, page, filter, values[nextOrdinal], typeFilter, biomeDimension).open(player);
            // Click sound
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1f);
        } else if (slot == 53) {
            // Next page
            List<CustomMob> filteredMobs = getFilteredMobs();
            int maxPage = (int) Math.ceil((double) filteredMobs.size() / MOBS_PER_PAGE) - 1;
            if (page < maxPage) {
                new CollectionGui(plugin, player, page + 1, filter, sort, typeFilter, biomeDimension).open(player);
            }
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
}

package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.registry.RecipeDefinition;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class RecipeBrowserGUI extends BaseGUI {

    private static final int[] RECIPE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final Player player;
    private final Consumer<Player> backAction;
    private int page;
    private TypeFilter typeFilter;
    private UnlockFilter unlockFilter;
    private Integer levelFilter;
    private List<RecipeEntryInfo> filteredEntries = List.of();

    public RecipeBrowserGUI(FishRework plugin, Player player) {
        this(plugin, player, 0, TypeFilter.ALL, UnlockFilter.ALL, null, null);
    }

    public RecipeBrowserGUI(FishRework plugin,
            Player player,
            int page,
            TypeFilter typeFilter,
            UnlockFilter unlockFilter,
            Integer levelFilter,
            Consumer<Player> backAction) {
        super(plugin, 6, localizedTitle(plugin, "recipebrowsergui.title", "Recipe Browser"));
        this.player = player;
        this.page = page;
        this.typeFilter = typeFilter;
        this.unlockFilter = unlockFilter;
        this.levelFilter = levelFilter;
        this.backAction = backAction;
        initializeItems();
    }

    private void initializeItems() {
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);

        filteredEntries = getFilteredEntries();
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredEntries.size() / RECIPE_SLOTS.length));
        if (page >= totalPages) {
            page = totalPages - 1;
        }

        int startIndex = page * RECIPE_SLOTS.length;
        int endIndex = Math.min(startIndex + RECIPE_SLOTS.length, filteredEntries.size());

        for (int i = 0; i < RECIPE_SLOTS.length; i++) {
            inventory.setItem(RECIPE_SLOTS[i], null);
        }

        for (int i = startIndex; i < endIndex; i++) {
            RecipeEntryInfo entry = filteredEntries.get(i);
            inventory.setItem(RECIPE_SLOTS[i - startIndex], createRecipeEntry(entry));
        }

        setPaginationControls(45, 53, page, totalPages);
        setBackButton(48);
        inventory.setItem(47, createTypeFilterItem());
        inventory.setItem(49, createPageInfo(
                page,
                totalPages,
                plugin.getLanguageManager().getString(
                        "recipebrowsergui.recipes_count",
                        "Recipes: %count%",
                        "count", String.valueOf(filteredEntries.size()))));
        inventory.setItem(50, createLevelFilterItem());
        inventory.setItem(51, createUnlockFilterItem());
    }

    private List<RecipeEntryInfo> getFilteredEntries() {
        List<RecipeEntryInfo> entries = new ArrayList<>();
        for (String resultId : plugin.getRecipeRegistry().getRecipeResultIds()) {
            List<RecipeDefinition> recipes = plugin.getRecipeRegistry().getRecipesForResultId(resultId);
            if (recipes.isEmpty()) {
                continue;
            }
            ItemStack item = plugin.getItemManager().getItem(resultId);
            if (item == null) {
                continue;
            }
            boolean unlocked = recipes.stream()
                    .anyMatch(recipe -> plugin.getRecipeRegistry().canCraft(player, recipe.getKey()));
            if (matchesFilters(resultId, item, recipes, unlocked)) {
                String displayName = getDisplayName(item);
                String sortKey = (unlocked ? "0:" : "1:") + displayName.toLowerCase(Locale.ROOT);
                entries.add(new RecipeEntryInfo(resultId, recipes, item, displayName, unlocked, sortKey));
            }
        }

        entries.sort(Comparator.comparing(RecipeEntryInfo::sortKey));
        return entries;
    }

    private boolean matchesFilters(String resultId, ItemStack item, List<RecipeDefinition> recipes, boolean unlocked) {
        if (!typeFilter.matches(resultId, item)) {
            return false;
        }

        if (levelFilter != null) {
            boolean levelMatched = false;
            for (RecipeDefinition recipe : recipes) {
                int requiredLevel = recipe.getRequiredLevel();
                if (levelFilter == 0 && requiredLevel == 0) {
                    levelMatched = true;
                    break;
                }
                if (levelFilter > 0 && requiredLevel == levelFilter) {
                    levelMatched = true;
                    break;
                }
            }
            if (!levelMatched) {
                return false;
            }
        }

        return switch (unlockFilter) {
            case ALL -> true;
            case UNLOCKED -> unlocked;
            case LOCKED -> !unlocked;
        };
    }

    private ItemStack createRecipeEntry(RecipeEntryInfo entry) {
        ItemStack item = entry.item().clone();
        List<RecipeDefinition> recipes = entry.recipes();
        RecipeDefinition primaryRecipe = selectPrimaryRecipe(recipes);
        boolean unlocked = entry.unlocked();
        boolean craftableNow = recipes.stream()
                .anyMatch(recipe -> plugin.getRecipeCraftingManager().getAvailability(player, recipe).canCraftNow());

        if (!unlocked) {
            return createLockedRecipeEntry(primaryRecipe);
        }

        ItemMeta meta = item.getItemMeta();
        String displayName = entry.displayName();
        meta.displayName(Component.text(displayName)
                .color(craftableNow ? NamedTextColor.GREEN : NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (craftableNow) {
            lore.add(plugin.getLanguageManager().getMessage("recipebrowsergui.craftable_now", "Craftable now")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        }

        if (primaryRecipe != null) {
            if (primaryRecipe.getRequiredLevel() > 0) {
                lore.add(Component.text(plugin.getLanguageManager().getString(
                                "recipebrowsergui.fishing_level",
                                "Fishing Level %level%",
                                "level", String.valueOf(primaryRecipe.getRequiredLevel())))
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            } else if (primaryRecipe.hasAdvancementRequirement()) {
                lore.add(plugin.getLanguageManager().getMessage("recipebrowsergui.advancement_unlock", "Advancement unlock")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(plugin.getLanguageManager().getMessage("recipebrowsergui.no_unlock_requirement", "No unlock requirement")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        if (recipes.size() > 1) {
            lore.add(Component.text(plugin.getLanguageManager().getString(
                            "recipebrowsergui.recipes_count",
                            "Recipes: %count%",
                            "count", String.valueOf(recipes.size())))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(plugin.getLanguageManager().getMessage("recipebrowsergui.click_to_inspect_recipe", "Click to inspect recipe")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLockedRecipeEntry(RecipeDefinition primaryRecipe) {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("recipebrowsergui.locked_item_name", "???")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getLanguageManager().getMessage("recipebrowsergui.recipe_not_unlocked_yet", "Recipe not unlocked yet.")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        if (primaryRecipe != null) {
            if (primaryRecipe.getRequiredLevel() > 0) {
                lore.add(Component.text(plugin.getLanguageManager().getString(
                                "recipebrowsergui.requires_fishing_level",
                                "Requires Fishing Level %level%",
                                "level", String.valueOf(primaryRecipe.getRequiredLevel())))
                        .color(NamedTextColor.DARK_RED)
                        .decoration(TextDecoration.ITALIC, false));
            } else if (primaryRecipe.hasAdvancementRequirement()) {
                lore.add(plugin.getLanguageManager().getMessage("recipebrowsergui.requires_a_progression_advancement", "Requires a progression advancement.")
                        .color(NamedTextColor.DARK_RED)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private RecipeDefinition selectPrimaryRecipe(List<RecipeDefinition> recipes) {
        return recipes.stream()
                .sorted(Comparator
                        .comparing((RecipeDefinition recipe) -> !plugin.getRecipeRegistry().canCraft(player, recipe.getKey()))
                        .thenComparingInt(recipe -> recipe.getRequiredLevel() == 0 ? Integer.MAX_VALUE : recipe.getRequiredLevel())
                        .thenComparing(recipe -> recipe.getKey().getKey()))
                .findFirst()
                .orElse(null);
    }

    private String getDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
        }
        return RecipeDefinition.toFriendlyName(item.getType().name());
    }

    private ItemStack createTypeFilterItem() {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(plugin.getLanguageManager().getString(
                        "recipebrowsergui.type_prefix",
                        "Type: %type%",
                        "type", getTypeFilterLabel(typeFilter)))
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                plugin.getLanguageManager().getMessage("recipebrowsergui.filter_by_gear_type", "Filter by gear type")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLevelFilterItem() {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(plugin.getLanguageManager().getString(
                        "recipebrowsergui.level_prefix",
                        "Level: %level%",
                        "level", getLevelFilterLabel(levelFilter)))
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                plugin.getLanguageManager().getMessage("recipebrowsergui.cycle_required_fishing_level", "Cycle required fishing level")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createUnlockFilterItem() {
        ItemStack item = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(plugin.getLanguageManager().getString(
                        "recipebrowsergui.state_prefix",
                        "State: %state%",
                        "state", getUnlockFilterLabel(unlockFilter)))
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                plugin.getLanguageManager().getMessage("recipebrowsergui.show_unlocked_or_locked_recipes", "Show unlocked or locked recipes")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private String getTypeFilterLabel(TypeFilter filter) {
        return switch (filter) {
            case ALL -> plugin.getLanguageManager().getString("recipebrowsergui.type.all", "All");
            case RODS -> plugin.getLanguageManager().getString("recipebrowsergui.type.rods", "Rods");
            case ARMOR -> plugin.getLanguageManager().getString("recipebrowsergui.type.armor", "Armor");
            case WEAPONS -> plugin.getLanguageManager().getString("recipebrowsergui.type.weapons", "Weapons");
            case MATERIALS -> plugin.getLanguageManager().getString("recipebrowsergui.type.materials", "Materials");
            case BAITS -> plugin.getLanguageManager().getString("recipebrowsergui.type.baits", "Baits");
            case UTILITIES -> plugin.getLanguageManager().getString("recipebrowsergui.type.utilities", "Utilities");
        };
    }

    private String getLevelFilterLabel(Integer value) {
        if (value == null) {
            return plugin.getLanguageManager().getString("recipebrowsergui.level.all", "All");
        }
        if (value == 0) {
            return plugin.getLanguageManager().getString("recipebrowsergui.level.none", "No Level");
        }
        return String.valueOf(value);
    }

    private String getUnlockFilterLabel(UnlockFilter filter) {
        return switch (filter) {
            case ALL -> plugin.getLanguageManager().getString("recipebrowsergui.state.all", "All");
            case UNLOCKED -> plugin.getLanguageManager().getString("recipebrowsergui.state.unlocked", "Unlocked");
            case LOCKED -> plugin.getLanguageManager().getString("recipebrowsergui.state.locked", "Locked");
        };
    }

    private List<Integer> getLevelOptions() {
        List<Integer> options = new ArrayList<>();
        options.add(null);
        options.add(0);
        plugin.getRecipeRegistry().getAll().stream()
                .map(RecipeDefinition::getRequiredLevel)
                .filter(level -> level > 0)
                .distinct()
                .sorted()
                .forEach(options::add);
        return options;
    }

    private void openRecipe(String resultId) {
        List<RecipeDefinition> recipes = plugin.getRecipeRegistry().getRecipesForResultId(resultId);
        new RecipeGuideGUI(plugin, player, recipes, 0, reopenedPlayer ->
                new RecipeBrowserGUI(plugin, reopenedPlayer, page, typeFilter, unlockFilter, levelFilter, backAction).open(reopenedPlayer)
        ).open(player);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == 48) {
            if (backAction != null) {
                backAction.accept(player);
            } else {
                player.closeInventory();
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 45 && page > 0) {
            page--;
            initializeItems();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 53) {
            int totalPages = Math.max(1, (int) Math.ceil((double) filteredEntries.size() / RECIPE_SLOTS.length));
            if (page < totalPages - 1) {
                page++;
                initializeItems();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            }
            return;
        }

        if (slot == 47) {
            typeFilter = typeFilter.next();
            page = 0;
            initializeItems();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 50) {
            List<Integer> options = getLevelOptions();
            int index = options.indexOf(levelFilter);
            levelFilter = options.get((index + 1) % options.size());
            page = 0;
            initializeItems();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 51) {
            unlockFilter = unlockFilter.next();
            page = 0;
            initializeItems();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        for (int i = 0; i < RECIPE_SLOTS.length; i++) {
            if (RECIPE_SLOTS[i] != slot) {
                continue;
            }

            int resultIndex = page * RECIPE_SLOTS.length + i;
            if (resultIndex < filteredEntries.size() && filteredEntries.get(resultIndex).unlocked()) {
                openRecipe(filteredEntries.get(resultIndex).resultId());
            }
            return;
        }
    }

    private record RecipeEntryInfo(
            String resultId,
            List<RecipeDefinition> recipes,
            ItemStack item,
            String displayName,
            boolean unlocked,
            String sortKey) {
    }

    public enum UnlockFilter {
        ALL,
        UNLOCKED,
        LOCKED;

        public UnlockFilter next() {
            UnlockFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum TypeFilter {
        ALL,
        RODS,
        ARMOR,
        WEAPONS,
        MATERIALS,
        BAITS,
        UTILITIES;

        public TypeFilter next() {
            TypeFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public boolean matches(String resultId, ItemStack item) {
            Material material = item.getType();
            return switch (this) {
                case ALL -> true;
                case RODS -> material == Material.FISHING_ROD || resultId.contains("_rod") || resultId.endsWith("rod");
                case ARMOR -> material.name().endsWith("_HELMET")
                        || material.name().endsWith("_CHESTPLATE")
                        || material.name().endsWith("_LEGGINGS")
                        || material.name().endsWith("_BOOTS");
                case WEAPONS -> material == Material.TRIDENT
                        || material == Material.MACE
                        || material.name().endsWith("_SWORD")
                        || material.name().endsWith("_AXE")
                        || material.name().endsWith("_BOW");
                case MATERIALS -> !(RODS.matches(resultId, item)
                        || ARMOR.matches(resultId, item)
                        || WEAPONS.matches(resultId, item)
                        || BAITS.matches(resultId, item)
                        || UTILITIES.matches(resultId, item));
                case BAITS -> resultId.contains("bait");
                case UTILITIES -> resultId.contains("bag")
                        || resultId.contains("journal")
                        || resultId.contains("totem")
                        || resultId.contains("bucket")
                        || resultId.contains("relic")
                        || resultId.contains("display_case")
                        || resultId.contains("ring");
            };
        }
    }
}

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

        List<String> resultIds = getFilteredResultIds();
        int totalPages = Math.max(1, (int) Math.ceil((double) resultIds.size() / RECIPE_SLOTS.length));
        if (page >= totalPages) {
            page = totalPages - 1;
        }

        int startIndex = page * RECIPE_SLOTS.length;
        int endIndex = Math.min(startIndex + RECIPE_SLOTS.length, resultIds.size());

        for (int i = 0; i < RECIPE_SLOTS.length; i++) {
            inventory.setItem(RECIPE_SLOTS[i], null);
        }

        for (int i = startIndex; i < endIndex; i++) {
            String resultId = resultIds.get(i);
            inventory.setItem(RECIPE_SLOTS[i - startIndex], createRecipeEntry(resultId));
        }

        setPaginationControls(45, 53, page, totalPages);
        setBackButton(48);
        inventory.setItem(47, createTypeFilterItem());
        inventory.setItem(49, createPageInfo(page, totalPages, "Recipes: " + resultIds.size()));
        inventory.setItem(50, createLevelFilterItem());
        inventory.setItem(51, createUnlockFilterItem());
    }

    private List<String> getFilteredResultIds() {
        List<String> resultIds = new ArrayList<>();
        for (String resultId : plugin.getRecipeRegistry().getRecipeResultIds()) {
            List<RecipeDefinition> recipes = plugin.getRecipeRegistry().getRecipesForResultId(resultId);
            if (recipes.isEmpty()) {
                continue;
            }
            if (matchesFilters(resultId, recipes)) {
                resultIds.add(resultId);
            }
        }

        resultIds.sort(Comparator.comparing(this::getSortKey));
        return resultIds;
    }

    private boolean matchesFilters(String resultId, List<RecipeDefinition> recipes) {
        ItemStack item = plugin.getItemManager().getItem(resultId);
        if (item == null) {
            return false;
        }

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
            case UNLOCKED -> recipes.stream().anyMatch(recipe -> plugin.getRecipeRegistry().canCraft(player, recipe.getKey()));
            case LOCKED -> recipes.stream().noneMatch(recipe -> plugin.getRecipeRegistry().canCraft(player, recipe.getKey()));
        };
    }

    private ItemStack createRecipeEntry(String resultId) {
        ItemStack item = plugin.getItemManager().getRequiredItem(resultId).clone();
        List<RecipeDefinition> recipes = plugin.getRecipeRegistry().getRecipesForResultId(resultId);
        RecipeDefinition primaryRecipe = selectPrimaryRecipe(recipes);
        boolean unlocked = recipes.stream().anyMatch(recipe -> plugin.getRecipeRegistry().canCraft(player, recipe.getKey()));
        boolean craftableNow = recipes.stream()
                .anyMatch(recipe -> plugin.getRecipeCraftingManager().getAvailability(player, recipe).canCraftNow());

        if (!unlocked) {
            return createLockedRecipeEntry(primaryRecipe);
        }

        ItemMeta meta = item.getItemMeta();
        String displayName = getDisplayName(item);
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
                lore.add(Component.text("Fishing Level " + primaryRecipe.getRequiredLevel())
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
            lore.add(Component.text("Recipes: " + recipes.size())
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
        meta.displayName(plugin.getLanguageManager().getMessage("recipebrowsergui.", "???")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getLanguageManager().getMessage("recipebrowsergui.recipe_not_unlocked_yet", "Recipe not unlocked yet.")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        if (primaryRecipe != null) {
            if (primaryRecipe.getRequiredLevel() > 0) {
                lore.add(Component.text("Requires Fishing Level " + primaryRecipe.getRequiredLevel())
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

    private String getSortKey(String resultId) {
        ItemStack item = plugin.getItemManager().getRequiredItem(resultId);
        String name = getDisplayName(item);
        boolean unlocked = plugin.getRecipeRegistry().getRecipesForResultId(resultId).stream()
                .anyMatch(recipe -> plugin.getRecipeRegistry().canCraft(player, recipe.getKey()));
        return (unlocked ? "0:" : "1:") + name.toLowerCase();
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
        meta.displayName(Component.text("Type: " + typeFilter.label)
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
        String label = levelFilter == null ? "All" : (levelFilter == 0 ? "No Level" : "Level " + levelFilter);
        meta.displayName(Component.text("Level: " + label)
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
        meta.displayName(Component.text("State: " + unlockFilter.label)
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
            int totalPages = Math.max(1, (int) Math.ceil((double) getFilteredResultIds().size() / RECIPE_SLOTS.length));
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

            List<String> resultIds = getFilteredResultIds();
            int resultIndex = page * RECIPE_SLOTS.length + i;
            if (resultIndex < resultIds.size()) {
                String resultId = resultIds.get(resultIndex);
                List<RecipeDefinition> recipes = plugin.getRecipeRegistry().getRecipesForResultId(resultId);
                boolean unlocked = recipes.stream().anyMatch(recipe -> plugin.getRecipeRegistry().canCraft(player, recipe.getKey()));
                if (unlocked) {
                    openRecipe(resultId);
                }
            }
            return;
        }
    }

    public enum UnlockFilter {
        ALL("All"),
        UNLOCKED("Unlocked"),
        LOCKED("Locked");

        private final String label;

        UnlockFilter(String label) {
            this.label = label;
        }

        public UnlockFilter next() {
            UnlockFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum TypeFilter {
        ALL("All"),
        RODS("Rods"),
        ARMOR("Armor"),
        WEAPONS("Weapons"),
        MATERIALS("Materials"),
        BAITS("Baits"),
        UTILITIES("Utilities");

        private final String label;

        TypeFilter(String label) {
            this.label = label;
        }

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

package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.registry.RecipeDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class SpecialCraftingGUI extends BaseGUI {

    private static final int[] GRID_SLOTS = {
            10, 11, 12,
            19, 20, 21,
            28, 29, 30
    };

    private static final Set<Integer> GRID_SLOT_SET = Set.of(10, 11, 12, 19, 20, 21, 28, 29, 30);

    private static final int INFO_SLOT = 4;
    private static final int ARROW_SLOT = 23;
    private static final int RESULT_SLOT = 25;
    private static final int BACK_SLOT = 48;
    private static final int CRAFT_SLOT = 49;
    private static final int CLEAR_SLOT = 50;

    private final Player player;
    private final Consumer<Player> backAction;
    private RecipeMatch currentMatch;
    private boolean refreshQueued;

    private record RecipeMatch(RecipeDefinition recipe, int[] consumedSlots) {}

    public SpecialCraftingGUI(FishRework plugin, Player player) {
        this(plugin, player, null);
    }

    public SpecialCraftingGUI(FishRework plugin, Player player, Consumer<Player> backAction) {
        super(plugin, 6, "Special Crafting");
        this.player = player;
        this.backAction = backAction;
        initializeItems();
    }

    @Override
    public boolean handlesPlayerInventoryClicks() {
        return true;
    }

    private void initializeItems() {
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);

        for (int slot : GRID_SLOTS) {
            inventory.setItem(slot, null);
        }

        inventory.setItem(ARROW_SLOT, createArrowItem());
        setBackButton(BACK_SLOT);
        inventory.setItem(CLEAR_SLOT, createClearButton());

        refreshRecipePreview();
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) {
            return;
        }

        boolean clickedTop = rawSlot < inventory.getSize();

        if (clickedTop) {
            if (GRID_SLOT_SET.contains(rawSlot)) {
                event.setCancelled(false);
                queueRefresh();
                return;
            }

            if (rawSlot == BACK_SLOT) {
                if (backAction != null) {
                    backAction.accept(player);
                } else {
                    player.closeInventory();
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                return;
            }

            if (rawSlot == CRAFT_SLOT) {
                craftCurrentRecipe();
                return;
            }

            if (rawSlot == CLEAR_SLOT) {
                clearGridToPlayer();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                return;
            }

            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
            moveShiftClickedStackIntoGrid(event);
            return;
        }

        event.setCancelled(false);
    }

    @Override
    public void onDrag(InventoryDragEvent event) {
        boolean touchesTop = false;

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= inventory.getSize()) {
                continue;
            }
            touchesTop = true;
            if (!GRID_SLOT_SET.contains(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }

        if (touchesTop) {
            event.setCancelled(false);
            queueRefresh();
        }
    }

    @Override
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        returnGridItemsToPlayer();
    }

    private void moveShiftClickedStackIntoGrid(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (isEmpty(clicked)) {
            return;
        }

        ItemStack moving = clicked.clone();
        int remaining = moveToGrid(moving);
        if (remaining == moving.getAmount()) {
            return;
        }

        if (remaining <= 0) {
            event.getClickedInventory().setItem(event.getSlot(), null);
        } else {
            ItemStack updated = clicked.clone();
            updated.setAmount(remaining);
            event.getClickedInventory().setItem(event.getSlot(), updated);
        }

        queueRefresh();
    }

    private int moveToGrid(ItemStack stack) {
        int remaining = stack.getAmount();

        for (int slot : GRID_SLOTS) {
            ItemStack existing = inventory.getItem(slot);
            if (isEmpty(existing) || !existing.isSimilar(stack)) {
                continue;
            }

            int max = existing.getMaxStackSize();
            int canAdd = max - existing.getAmount();
            if (canAdd <= 0) {
                continue;
            }

            int add = Math.min(canAdd, remaining);
            existing.setAmount(existing.getAmount() + add);
            remaining -= add;
            if (remaining <= 0) {
                return 0;
            }
        }

        for (int slot : GRID_SLOTS) {
            if (!isEmpty(inventory.getItem(slot))) {
                continue;
            }

            int add = Math.min(stack.getMaxStackSize(), remaining);
            ItemStack placed = stack.clone();
            placed.setAmount(add);
            inventory.setItem(slot, placed);
            remaining -= add;
            if (remaining <= 0) {
                return 0;
            }
        }

        return remaining;
    }

    private void craftCurrentRecipe() {
        if (currentMatch == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("specialcraftinggui.no_matching_recipe", "No matching recipe.").color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        RecipeDefinition recipe = currentMatch.recipe();
        if (!plugin.getRecipeRegistry().canCraft(player, recipe.getKey())) {
            player.sendMessage(Component.text(getLockMessage(recipe)).color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        ItemStack result = buildCraftResult(recipe, currentMatch.consumedSlots());
        consumeGrid(currentMatch.consumedSlots());

        var leftovers = player.getInventory().addItem(result);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        player.sendMessage(Component.text("Crafted " + getDisplayName(result) + ".").color(NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        refreshRecipePreview();
    }

    private void clearGridToPlayer() {
        for (int slot : GRID_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (isEmpty(item)) {
                continue;
            }

            var leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            inventory.setItem(slot, null);
        }

        refreshRecipePreview();
    }

    private void returnGridItemsToPlayer() {
        for (int slot : GRID_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (isEmpty(item)) {
                continue;
            }

            var leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            inventory.setItem(slot, null);
        }
    }

    private void refreshRecipePreview() {
        currentMatch = findMatchingRecipe();

        if (currentMatch == null) {
            inventory.setItem(INFO_SLOT, createDefaultInfoItem());
            inventory.setItem(RESULT_SLOT, createNoRecipeResult());
            inventory.setItem(CRAFT_SLOT, createCraftButton(null, false));
            return;
        }

        RecipeDefinition recipe = currentMatch.recipe();
        boolean unlocked = plugin.getRecipeRegistry().canCraft(player, recipe.getKey());

        inventory.setItem(INFO_SLOT, createRecipeInfoItem(recipe, unlocked));
        inventory.setItem(RESULT_SLOT, unlocked
                ? buildCraftResult(recipe, currentMatch.consumedSlots())
                : createLockedResult(recipe));
        inventory.setItem(CRAFT_SLOT, createCraftButton(recipe, unlocked));
    }

    private void queueRefresh() {
        if (refreshQueued) {
            return;
        }
        refreshQueued = true;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            refreshQueued = false;
            refreshRecipePreview();
        });
    }

    private RecipeMatch findMatchingRecipe() {
        ItemStack[] matrix = getMatrix();

        for (RecipeDefinition recipe : plugin.getRecipeRegistry().getAll()) {
            if (recipe.getDisplayType() == RecipeDefinition.DisplayType.SHAPELESS) {
                continue;
            }
            int[] consumed = matchRecipe(recipe, matrix);
            if (consumed != null) {
                return new RecipeMatch(recipe, consumed);
            }
        }

        List<RecipeDefinition> shapeless = plugin.getRecipeRegistry().getAll().stream()
                .filter(recipe -> recipe.getDisplayType() == RecipeDefinition.DisplayType.SHAPELESS)
                .sorted(Comparator.comparing(recipe -> recipe.getKey().getKey()))
                .toList();

        for (RecipeDefinition recipe : shapeless) {
            int[] consumed = matchRecipe(recipe, matrix);
            if (consumed != null) {
                return new RecipeMatch(recipe, consumed);
            }
        }

        return null;
    }

    private int[] matchRecipe(RecipeDefinition recipe, ItemStack[] matrix) {
        return switch (recipe.getDisplayType()) {
            case SHAPED, ENCHANTED_MATERIAL -> matchShapedRecipe(recipe, matrix);
            case SHAPELESS -> matchShapelessRecipe(recipe, matrix);
        };
    }

    private int[] matchShapedRecipe(RecipeDefinition recipe, ItemStack[] matrix) {
        List<RecipeDefinition.Ingredient> grid = recipe.getGrid();
        if (grid.size() != 9) {
            return null;
        }

        int minRow = 3;
        int minCol = 3;
        int maxRow = -1;
        int maxCol = -1;

        for (int i = 0; i < 9; i++) {
            if (grid.get(i) == null) {
                continue;
            }

            int row = i / 3;
            int col = i % 3;
            minRow = Math.min(minRow, row);
            minCol = Math.min(minCol, col);
            maxRow = Math.max(maxRow, row);
            maxCol = Math.max(maxCol, col);
        }

        if (maxRow < 0) {
            return null;
        }

        int patternHeight = maxRow - minRow + 1;
        int patternWidth = maxCol - minCol + 1;

        for (int rowOffset = 0; rowOffset <= 3 - patternHeight; rowOffset++) {
            for (int colOffset = 0; colOffset <= 3 - patternWidth; colOffset++) {
                int[] consumed = new int[9];
                boolean matched = true;

                for (int row = 0; row < 3 && matched; row++) {
                    for (int col = 0; col < 3; col++) {
                        int matrixIndex = row * 3 + col;
                        ItemStack stack = matrix[matrixIndex];

                        RecipeDefinition.Ingredient expected = null;
                        if (row >= rowOffset && row < rowOffset + patternHeight
                                && col >= colOffset && col < colOffset + patternWidth) {
                            int recipeRow = minRow + (row - rowOffset);
                            int recipeCol = minCol + (col - colOffset);
                            expected = grid.get(recipeRow * 3 + recipeCol);
                        }

                        if (expected == null) {
                            if (!isEmpty(stack)) {
                                matched = false;
                                break;
                            }
                        } else {
                            if (!expected.matches(stack, plugin.getItemManager())) {
                                matched = false;
                                break;
                            }
                            consumed[matrixIndex] = 1;
                        }
                    }
                }

                if (matched) {
                    return consumed;
                }
            }
        }

        return null;
    }

    private int[] matchShapelessRecipe(RecipeDefinition recipe, ItemStack[] matrix) {
        List<RecipeDefinition.Ingredient> required = new ArrayList<>(recipe.getIngredientUnits());
        if (required.isEmpty()) {
            return null;
        }

        List<Integer> occupiedSlots = new ArrayList<>();
        for (int i = 0; i < matrix.length; i++) {
            if (!isEmpty(matrix[i])) {
                occupiedSlots.add(i);
            }
        }

        if (occupiedSlots.size() != required.size()) {
            return null;
        }

        required.sort(Comparator.comparingInt(RecipeDefinition.Ingredient::specificityScore).reversed());

        int[] consumed = new int[9];
        boolean[] used = new boolean[occupiedSlots.size()];
        if (assignShapelessIngredients(0, required, occupiedSlots, used, matrix, consumed)) {
            return consumed;
        }

        return null;
    }

    private boolean assignShapelessIngredients(int ingredientIndex,
            List<RecipeDefinition.Ingredient> required,
            List<Integer> occupiedSlots,
            boolean[] used,
            ItemStack[] matrix,
            int[] consumed) {
        if (ingredientIndex >= required.size()) {
            return true;
        }

        RecipeDefinition.Ingredient ingredient = required.get(ingredientIndex);
        for (int i = 0; i < occupiedSlots.size(); i++) {
            if (used[i]) {
                continue;
            }

            int matrixSlot = occupiedSlots.get(i);
            if (!ingredient.matches(matrix[matrixSlot], plugin.getItemManager())) {
                continue;
            }

            used[i] = true;
            consumed[matrixSlot] = 1;
            if (assignShapelessIngredients(ingredientIndex + 1, required, occupiedSlots, used, matrix, consumed)) {
                return true;
            }

            used[i] = false;
            consumed[matrixSlot] = 0;
        }

        return false;
    }

    private ItemStack[] getMatrix() {
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            matrix[i] = inventory.getItem(GRID_SLOTS[i]);
        }
        return matrix;
    }

    private ItemStack buildCraftResult(RecipeDefinition recipe, int[] consumedSlots) {
        ItemStack baseResult = recipe.createResultItem(plugin.getItemManager()).clone();
        List<ItemStack> sourceIngredients = collectSourceIngredients(consumedSlots);
        return plugin.getRecipeCraftingManager().applySpecialCraftingResult(
                recipe.getKey().getKey(),
                baseResult,
                sourceIngredients);
    }

    private List<ItemStack> collectSourceIngredients(int[] consumedSlots) {
        List<ItemStack> source = new ArrayList<>();
        for (int i = 0; i < consumedSlots.length; i++) {
            if (consumedSlots[i] <= 0) {
                continue;
            }

            ItemStack stack = inventory.getItem(GRID_SLOTS[i]);
            if (!isEmpty(stack)) {
                source.add(stack.clone());
            }
        }
        return source;
    }

    private void consumeGrid(int[] consumedSlots) {
        for (int i = 0; i < consumedSlots.length; i++) {
            int consume = consumedSlots[i];
            if (consume <= 0) {
                continue;
            }

            int slot = GRID_SLOTS[i];
            ItemStack stack = inventory.getItem(slot);
            if (isEmpty(stack)) {
                continue;
            }

            int remaining = stack.getAmount() - consume;
            if (remaining <= 0) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(remaining);
                inventory.setItem(slot, stack);
            }
        }
    }

    private ItemStack createArrowItem() {
        ItemStack arrow = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = arrow.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("specialcraftinggui.crafting_output", "Crafting Output")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        arrow.setItemMeta(meta);
        return arrow;
    }

    private ItemStack createDefaultInfoItem() {
        ItemStack info = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("specialcraftinggui.special_crafting", "Special Crafting")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                plugin.getLanguageManager().getMessage("specialcraftinggui.place_items_in_the_3x3", "Place items in the 3x3 grid.")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                plugin.getLanguageManager().getMessage("specialcraftinggui.matching_custom_recipes_will_appear", "Matching custom recipes will appear.")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        info.setItemMeta(meta);
        return info;
    }

    private ItemStack createRecipeInfoItem(RecipeDefinition recipe, boolean unlocked) {
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(Component.text(getResultName(recipe))
                .color(unlocked ? NamedTextColor.GOLD : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Type: " + formatDisplayType(recipe.getDisplayType()))
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        if (unlocked) {
            lore.add(plugin.getLanguageManager().getMessage("specialcraftinggui.recipe_unlocked", "Recipe unlocked")
                    .color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text(getLockMessage(recipe))
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        info.setItemMeta(meta);
        return info;
    }

    private ItemStack createNoRecipeResult() {
        ItemStack result = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("specialcraftinggui.no_matching_recipe", "No Matching Recipe")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        result.setItemMeta(meta);
        return result;
    }

    private ItemStack createLockedResult(RecipeDefinition recipe) {
        ItemStack result = new ItemStack(Material.BARRIER);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("specialcraftinggui.recipe_locked", "Recipe Locked")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(getLockMessage(recipe))
                .color(NamedTextColor.DARK_RED)
                .decoration(TextDecoration.ITALIC, false)));
        result.setItemMeta(meta);
        return result;
    }

    private ItemStack createCraftButton(RecipeDefinition recipe, boolean unlocked) {
        boolean canCraft = recipe != null && unlocked;
        ItemStack button = new ItemStack(canCraft ? Material.LIME_WOOL : Material.GRAY_WOOL);
        ItemMeta meta = button.getItemMeta();
        meta.displayName(Component.text(canCraft ? "Craft" : "Cannot Craft")
                .color(canCraft ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (recipe == null) {
            lore.add(plugin.getLanguageManager().getMessage("specialcraftinggui.arrange_items_to_match_a", "Arrange items to match a recipe.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else if (unlocked) {
            lore.add(plugin.getLanguageManager().getMessage("specialcraftinggui.click_to_craft_from_this", "Click to craft from this grid.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text(getLockMessage(recipe))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        button.setItemMeta(meta);
        return button;
    }

    private ItemStack createClearButton() {
        ItemStack clear = new ItemStack(Material.PAPER);
        ItemMeta meta = clear.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("specialcraftinggui.clear_grid", "Clear Grid")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                plugin.getLanguageManager().getMessage("specialcraftinggui.returns_all_grid_items_to", "Returns all grid items to your inventory.")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        clear.setItemMeta(meta);
        return clear;
    }

    private String getLockMessage(RecipeDefinition recipe) {
        if (recipe.hasLevelRequirement()) {
            return "Requires Fishing Level " + recipe.getRequiredLevel();
        }
        if (recipe.hasAdvancementRequirement()) {
            return "Requires advancement: " + RecipeDefinition.toFriendlyName(recipe.getRequiredAdvancement().getKey());
        }
        return "Recipe locked";
    }

    private String getResultName(RecipeDefinition recipe) {
        ItemStack result = recipe.createResultItem(plugin.getItemManager());
        if (result.hasItemMeta() && result.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(result.getItemMeta().displayName());
        }
        return RecipeDefinition.toFriendlyName(result.getType().name());
    }

    private String getDisplayName(ItemStack item) {
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        }
        if (item == null) {
            return "item";
        }
        return RecipeDefinition.toFriendlyName(item.getType().name());
    }

    private String formatDisplayType(RecipeDefinition.DisplayType displayType) {
        return switch (displayType) {
            case SHAPED -> "Shaped";
            case SHAPELESS -> "Shapeless";
            case ENCHANTED_MATERIAL -> "Enchanted Material";
        };
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
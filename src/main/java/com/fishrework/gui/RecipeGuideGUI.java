package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.manager.RecipeCraftingManager;
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
import java.util.List;
import java.util.function.Consumer;

public class RecipeGuideGUI extends BaseGUI {

    private static final int[] GRID_SLOTS = {
            10, 11, 12,
            19, 20, 21,
            28, 29, 30
    };

    private final Player player;
    private final List<RecipeDefinition> recipes;
    private final Consumer<Player> backAction;
    private int recipeIndex;

    public RecipeGuideGUI(FishRework plugin, Player player, List<RecipeDefinition> recipes) {
        this(plugin, player, recipes, 0, null);
    }

    public RecipeGuideGUI(FishRework plugin,
            Player player,
            List<RecipeDefinition> recipes,
            int recipeIndex,
            Consumer<Player> backAction) {
        super(plugin, 6, localizedTitle(plugin, "recipeguidegui.title", "Recipe Guide"));
        this.player = player;
        this.recipes = List.copyOf(recipes);
        this.recipeIndex = Math.max(0, Math.min(recipeIndex, Math.max(0, recipes.size() - 1)));
        this.backAction = backAction;
        initializeItems();
    }

    private void initializeItems() {
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);

        RecipeDefinition recipe = currentRecipe();
        RecipeCraftingManager.RecipeAvailability availability = plugin.getRecipeCraftingManager().getAvailability(player, recipe);

        for (int i = 0; i < GRID_SLOTS.length; i++) {
            inventory.setItem(GRID_SLOTS[i], null);
            RecipeDefinition.Ingredient ingredient = i < recipe.getGrid().size() ? recipe.getGrid().get(i) : null;
            if (ingredient != null) {
                inventory.setItem(GRID_SLOTS[i], ingredient.createDisplayItem(plugin.getItemManager()));
            }
        }

        inventory.setItem(25, createResultItem(recipe));
        inventory.setItem(4, createInfoItem(recipe));
        inventory.setItem(23, createArrowItem());
        inventory.setItem(49, createCraftButton(recipe, availability));
        inventory.setItem(50, createHintItem());
        setBackButton(48);
        setPaginationControls(45, 53, recipeIndex, recipes.size());
    }

    private RecipeDefinition currentRecipe() {
        return recipes.get(recipeIndex);
    }

    private ItemStack createInfoItem(RecipeDefinition recipe) {
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(Component.text(getResultName(recipe))
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(plugin.getLanguageManager().getString(
                        "recipeguidegui.recipe_index",
                        "Recipe %current%/%total%",
                        "current", String.valueOf(recipeIndex + 1),
                        "total", String.valueOf(recipes.size())))
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(plugin.getLanguageManager().getString("recipeguidegui.type_prefix", "Type: ") + formatDisplayType(recipe.getDisplayType()))
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        if (recipe.hasLevelRequirement()) {
            lore.add(Component.text(plugin.getLanguageManager().getString(
                            "recipeguidegui.requires_fishing_level",
                            "Requires Fishing Level %level%",
                            "level", String.valueOf(recipe.getRequiredLevel())))
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        } else if (recipe.hasAdvancementRequirement()) {
            lore.add(Component.text(plugin.getLanguageManager().getString(
                            "recipeguidegui.requires_advancement",
                            "Requires %advancement%",
                            "advancement", RecipeDefinition.toFriendlyName(recipe.getRequiredAdvancement().getKey())))
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        info.setItemMeta(meta);
        return info;
    }

    private ItemStack createResultItem(RecipeDefinition recipe) {
        return recipe.createResultItem(plugin.getItemManager());
    }

    private ItemStack createCraftButton(RecipeDefinition recipe, RecipeCraftingManager.RecipeAvailability availability) {
        Material material = availability.canCraftNow() ? Material.LIME_WOOL : Material.GRAY_WOOL;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(availability.canCraftNow()
                        ? plugin.getLanguageManager().getString("recipeguidegui.craft", "Craft")
                        : availability.message())
                .color(availability.canCraftNow() ? NamedTextColor.GREEN : NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (availability.canCraftNow()) {
            lore.add(plugin.getLanguageManager().getMessage("recipeguidegui.click_to_craft_using_your", "Click to craft using your inventory, Fish Bag, and Magma Satchel.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else if (availability.unlocked()) {
            lore.add(plugin.getLanguageManager().getMessage("recipeguidegui.missing_ingredients_in_your_inventory", "Missing ingredients in your inventory or bags.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else if (recipe.hasLevelRequirement()) {
            lore.add(Component.text(plugin.getLanguageManager().getString(
                            "recipeguidegui.reach_level_to_unlock",
                            "Reach Fishing Level %level% to unlock it.",
                            "level", String.valueOf(recipe.getRequiredLevel())))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else if (recipe.hasAdvancementRequirement()) {
            lore.add(plugin.getLanguageManager().getMessage("recipeguidegui.unlock_the_required_advancement_first", "Unlock the required advancement first.")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHintItem() {
        ItemStack hint = new ItemStack(Material.PAPER);
        ItemMeta meta = hint.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("recipeguidegui.recipe_browser", "Recipe Browser")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                plugin.getLanguageManager().getMessage("recipeguidegui.use_fishing_recipe_while_holding", "Use /fishing recipe while holding a custom item.")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                plugin.getLanguageManager().getMessage("recipeguidegui.use_the_recipe_browser_in", "Use the Recipe Browser in the Fishing menu to browse all recipes.")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        hint.setItemMeta(meta);
        return hint;
    }

    private ItemStack createArrowItem() {
        ItemStack arrow = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = arrow.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage("recipeguidegui.crafting_output", "Crafting Output")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        arrow.setItemMeta(meta);
        return arrow;
    }

    private String getResultName(RecipeDefinition recipe) {
        ItemStack result = recipe.createResultItem(plugin.getItemManager());
        if (result.hasItemMeta() && result.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(result.getItemMeta().displayName());
        }
        return RecipeDefinition.toFriendlyName(result.getType().name());
    }

    private String formatDisplayType(RecipeDefinition.DisplayType displayType) {
        return switch (displayType) {
            case SHAPED -> plugin.getLanguageManager().getString("recipeguidegui.type_shaped", "Shaped");
            case SHAPELESS -> plugin.getLanguageManager().getString("recipeguidegui.type_shapeless", "Shapeless");
            case ENCHANTED_MATERIAL -> plugin.getLanguageManager().getString("recipeguidegui.type_enchanted_material", "Enchanted Material");
        };
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

        if (slot == 45 && recipeIndex > 0) {
            recipeIndex--;
            initializeItems();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 53 && recipeIndex < recipes.size() - 1) {
            recipeIndex++;
            initializeItems();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 49) {
            RecipeCraftingManager.CraftAttempt attempt = plugin.getRecipeCraftingManager().craft(player, currentRecipe());
            player.sendMessage(Component.text(attempt.message())
                    .color(attempt.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            player.playSound(player.getLocation(),
                    attempt.success() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                    1f,
                    attempt.success() ? 1.4f : 1f);
            initializeItems();
        }
    }
}

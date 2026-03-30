package com.fishrework.registry;

import com.fishrework.manager.ItemManager;
import com.fishrework.model.Skill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Wraps a Bukkit Recipe with level/advancement gating metadata.
 * Adding a level-gated recipe = one register() call in RecipeRegistry.
 */
public class RecipeDefinition {

    public enum DisplayType {
        SHAPED,
        SHAPELESS,
        ENCHANTED_MATERIAL
    }

    public record IngredientOption(String key, Material material, boolean customItem) {

        public ItemStack createDisplayItem(ItemManager itemManager) {
            if (customItem) {
                return itemManager.getRequiredItem(key).clone();
            }
            return new ItemStack(material);
        }

        public boolean matches(ItemStack stack, ItemManager itemManager) {
            if (stack == null || stack.getType().isAir()) {
                return false;
            }
            if (customItem) {
                return itemManager.isCustomItem(stack, key);
            }
            return stack.getType() == material;
        }

        public String displayName(ItemManager itemManager) {
            if (customItem) {
                ItemStack item = itemManager.getRequiredItem(key);
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                            .serialize(item.getItemMeta().displayName());
                }
            }
            return RecipeDefinition.toFriendlyName(key);
        }

        public String signature() {
            return (customItem ? "custom:" : "vanilla:") + key;
        }
    }

    public record Ingredient(List<IngredientOption> options) {

        public Ingredient {
            options = List.copyOf(options);
        }

        public boolean matches(ItemStack stack, ItemManager itemManager) {
            for (IngredientOption option : options) {
                if (option.matches(stack, itemManager)) {
                    return true;
                }
            }
            return false;
        }

        public ItemStack createDisplayItem(ItemManager itemManager) {
            ItemStack display = options.getFirst().createDisplayItem(itemManager);
            if (options.size() == 1) {
                return display;
            }

            ItemMeta meta = display.getItemMeta();
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            if (!lore.isEmpty()) {
                lore.add(Component.empty());
            }
            lore.add(Component.text("Any of:")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            for (IngredientOption option : options) {
                lore.add(Component.text("- " + option.displayName(itemManager))
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            display.setItemMeta(meta);
            return display;
        }

        public boolean isCustomOnly() {
            return options.stream().allMatch(IngredientOption::customItem);
        }

        public int specificityScore() {
            int score = isCustomOnly() ? 100 : 0;
            return score + Math.max(0, 10 - options.size());
        }

        public String signature() {
            StringJoiner joiner = new StringJoiner("|");
            options.stream()
                    .map(IngredientOption::signature)
                    .sorted()
                    .forEach(joiner::add);
            return joiner.toString();
        }
    }

    private final NamespacedKey key;
    private final Recipe recipe;
    private final Skill requiredSkill;
    private final int requiredLevel;
    private final NamespacedKey requiredAdvancement; // null = level-check only

    /**
     * Maps custom item IDs to their base Material.
     * Used for server-side validation since recipes use MaterialChoice
     * (to enable client-side recipe book filtering).
     * Empty if the recipe has no custom item ingredients.
     */
    private final Map<String, Material> customIngredients;
    private final String resultId;
    private final DisplayType displayType;
    private final List<Ingredient> grid;
    private final List<Ingredient> ingredientUnits;

    public RecipeDefinition(NamespacedKey key, Recipe recipe, Skill requiredSkill, int requiredLevel, NamespacedKey requiredAdvancement) {
        this(key, recipe, requiredSkill, requiredLevel, requiredAdvancement, Collections.emptyMap(), null, null, Collections.emptyList(), Collections.emptyList());
    }

    public RecipeDefinition(NamespacedKey key, Recipe recipe, Skill requiredSkill, int requiredLevel, NamespacedKey requiredAdvancement, Map<String, Material> customIngredients) {
        this(key, recipe, requiredSkill, requiredLevel, requiredAdvancement, customIngredients, null, null, Collections.emptyList(), Collections.emptyList());
    }

    public RecipeDefinition(NamespacedKey key,
                            Recipe recipe,
                            Skill requiredSkill,
                            int requiredLevel,
                            NamespacedKey requiredAdvancement,
                            Map<String, Material> customIngredients,
                            String resultId,
                            DisplayType displayType,
                            List<Ingredient> grid,
                            List<Ingredient> ingredientUnits) {
        this.key = key;
        this.recipe = recipe;
        this.requiredSkill = requiredSkill;
        this.requiredLevel = requiredLevel;
        this.requiredAdvancement = requiredAdvancement;
        this.customIngredients = customIngredients;
        this.resultId = resultId;
        this.displayType = displayType == null ? DisplayType.SHAPED : displayType;
        this.grid = grid == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(grid));
        this.ingredientUnits = ingredientUnits == null ? Collections.emptyList() : List.copyOf(ingredientUnits);
    }

    /**
     * Convenience: level-gated recipe (no advancement check).
     */
    public RecipeDefinition(NamespacedKey key, Recipe recipe, Skill requiredSkill, int requiredLevel) {
        this(key, recipe, requiredSkill, requiredLevel, null);
    }

    /**
     * Convenience: advancement-gated recipe (no level check).
     */
    public RecipeDefinition(NamespacedKey key, Recipe recipe, NamespacedKey requiredAdvancement) {
        this(key, recipe, null, 0, requiredAdvancement);
    }

    public NamespacedKey getKey() { return key; }
    public Recipe getRecipe() { return recipe; }
    public Skill getRequiredSkill() { return requiredSkill; }
    public int getRequiredLevel() { return requiredLevel; }
    public NamespacedKey getRequiredAdvancement() { return requiredAdvancement; }
    public Map<String, Material> getCustomIngredients() { return customIngredients; }
    public String getResultId() { return resultId; }
    public DisplayType getDisplayType() { return displayType; }
    public List<Ingredient> getGrid() { return grid; }
    public List<Ingredient> getIngredientUnits() { return ingredientUnits; }

    public boolean hasLevelRequirement() { return requiredSkill != null && requiredLevel > 0; }
    public boolean hasAdvancementRequirement() { return requiredAdvancement != null; }
    public boolean hasCustomIngredients() { return !customIngredients.isEmpty(); }
    public boolean hasRecipeLayout() { return !grid.isEmpty(); }

    public ItemStack createResultItem(ItemManager itemManager) {
        if (resultId != null && !resultId.isBlank()) {
            return itemManager.getRequiredItem(resultId);
        }
        return recipe.getResult().clone();
    }

    public static String toFriendlyName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }

        String value = raw.contains("/") ? raw.substring(raw.lastIndexOf('/') + 1) : raw;
        StringBuilder sb = new StringBuilder();
        for (String word : value.toLowerCase().replace('-', '_').split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
        }
        return sb.toString().trim();
    }
}

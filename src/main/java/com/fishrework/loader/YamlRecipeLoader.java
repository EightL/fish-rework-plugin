package com.fishrework.loader;

import com.fishrework.FishRework;
import com.fishrework.manager.ItemManager;
import com.fishrework.model.Skill;
import com.fishrework.registry.RecipeDefinition;
import com.fishrework.registry.RecipeRegistry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.*;

/**
 * Loads recipe definitions from {@code recipes.yml} and registers them
 * into the {@link RecipeRegistry}.
 *
 * <p>All custom item ingredients use {@link RecipeChoice.MaterialChoice} so that
 * the Minecraft client-side recipe book "craftable" filter works correctly.
 * Server-side validation of actual custom item identity is handled by
 * {@link com.fishrework.listener.CraftingListener}.</p>
 */
public class YamlRecipeLoader {

    private final FishRework plugin;

    /**
     * Collects custom ingredient IDs encountered while building a single recipe.
     * Reset before each recipe, then passed to {@link #createDefinition}.
     */
    private final Map<String, Material> currentCustomIngredients = new HashMap<>();

    public YamlRecipeLoader(FishRework plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all recipe definitions from {@code recipes.yml}.
     *
     * @param itemManager the item manager for resolving custom item ingredients
     * @return the number of recipes loaded
     */
    public int load(ItemManager itemManager) {
        YamlConfiguration yaml = YamlLoaderSupport.loadYaml(plugin, "recipes.yml");

        RecipeRegistry registry = plugin.getRecipeRegistry();
        int count = 0;

        count += loadShaped(yaml, itemManager, registry);
        count += loadShapeless(yaml, itemManager, registry);
        count += loadEnchantedMaterials(yaml, itemManager, registry);
        count += loadUpgrades(yaml, itemManager, registry);

        plugin.getLogger().info("Loaded " + count + " recipes from recipes.yml");
        return count;
    }

    // ── Shaped Recipes ──────────────────────────────────────

    private int loadShaped(YamlConfiguration yaml, ItemManager itemManager, RecipeRegistry registry) {
        ConfigurationSection section = yaml.getConfigurationSection("shaped");
        if (section == null) return 0;
        int count = 0;

        for (String recipeId : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(recipeId);
            if (entry == null) continue;
            if (registerShaped(recipeId, entry, itemManager, registry)) {
                count++;
            }
        }
        return count;
    }

    private boolean registerShaped(String recipeId, ConfigurationSection entry, ItemManager itemManager, RecipeRegistry registry) {
        try {
            currentCustomIngredients.clear();

            String resultId = entry.getString("result");
            ItemStack result = itemManager.getRequiredItem(resultId);

            NamespacedKey key = new NamespacedKey(plugin, recipeId);
            ShapedRecipe recipe = new ShapedRecipe(key, result);

            List<String> pattern = entry.getStringList("pattern");
            recipe.shape(pattern.toArray(new String[0]));
            Set<Character> patternSymbols = collectPatternSymbols(pattern);

            ConfigurationSection ingredients = entry.getConfigurationSection("ingredients");
            if (ingredients != null) {
                for (String charKey : ingredients.getKeys(false)) {
                    char c = charKey.charAt(0);
                    if (!patternSymbols.contains(c)) {
                        plugin.getLogger().warning("Ignoring unused ingredient symbol '" + c + "' in shaped recipe '" + recipeId + "'.");
                        continue;
                    }
                    if (ingredients.isList(charKey)) {
                        // Material choice (list of vanilla materials or custom items)
                        List<String> choices = ingredients.getStringList(charKey);
                        RecipeChoice choice = resolveChoice(choices, itemManager);
                        recipe.setIngredient(c, choice);
                    } else {
                        String ingredientName = ingredients.getString(charKey);
                        setIngredient(recipe, c, ingredientName, itemManager);
                    }
                }
            }

            for (char c : patternSymbols) {
                if (ingredients == null || !ingredients.contains(String.valueOf(c))) {
                    throw new IllegalArgumentException("Missing ingredient for symbol '" + c + "'");
                }
            }

            RecipeDefinition def = createDefinition(
                    key,
                    recipe,
                    entry,
                    resultId,
                    RecipeDefinition.DisplayType.SHAPED,
                    buildShapedGrid(pattern, ingredients, itemManager),
                    buildShapedIngredientUnits(pattern, ingredients, itemManager));
            registry.register(def);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load shaped recipe '" + recipeId + "': " + e.getMessage());
            return false;
        }
    }

    private Set<Character> collectPatternSymbols(List<String> pattern) {
        Set<Character> symbols = new LinkedHashSet<>();
        for (String row : pattern) {
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c != ' ') {
                    symbols.add(c);
                }
            }
        }
        return symbols;
    }

    // ── Shapeless Recipes ───────────────────────────────────

    private int loadShapeless(YamlConfiguration yaml, ItemManager itemManager, RecipeRegistry registry) {
        ConfigurationSection section = yaml.getConfigurationSection("shapeless");
        if (section == null) return 0;
        int count = 0;

        for (String recipeId : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(recipeId);
            if (entry == null) continue;

            // Automatically detect shaped vs shapeless based on keys, handling user misconfiguration
            if (entry.contains("pattern")) {
                if (registerShaped(recipeId, entry, itemManager, registry)) {
                    count++;
                }
            } else {
                if (registerShapeless(recipeId, entry, itemManager, registry)) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean registerShapeless(String recipeId, ConfigurationSection entry, ItemManager itemManager, RecipeRegistry registry) {
        try {
            currentCustomIngredients.clear();

            String resultId = entry.getString("result");
            ItemStack result = itemManager.getRequiredItem(resultId);

            NamespacedKey key = new NamespacedKey(plugin, recipeId);
            ShapelessRecipe recipe = new ShapelessRecipe(key, result);

            List<?> ingredientList = entry.getList("ingredients");
            if (ingredientList != null) {
                for (Object ingredient : ingredientList) {
                    if (ingredient instanceof String name) {
                        addShapelessIngredient(recipe, name, itemManager);
                    } else if (ingredient instanceof Map<?, ?> map) {
                        // Material choice: { fish_choice: [COD, SALMON, ...] }
                        for (Map.Entry<?, ?> e : map.entrySet()) {
                            if (e.getValue() instanceof List<?> choices) {
                                List<String> choiceNames = new ArrayList<>();
                                for (Object c : choices) choiceNames.add(String.valueOf(c));
                                recipe.addIngredient(resolveChoice(choiceNames, itemManager));
                            }
                        }
                    }
                }
            }

            List<RecipeDefinition.Ingredient> ingredientUnits = buildShapelessIngredientUnits(ingredientList, itemManager);
            RecipeDefinition def = createDefinition(
                    key,
                    recipe,
                    entry,
                    resultId,
                    RecipeDefinition.DisplayType.SHAPELESS,
                    buildShapelessGrid(ingredientUnits),
                    ingredientUnits);
            registry.register(def);
            return true;
        } catch (Exception e) {
             plugin.getLogger().warning("Failed to load shapeless recipe '" + recipeId + "': " + e.getMessage());
             return false;
        }
    }

    // ── Enchanted Material Recipes ──────────────────────────

    private int loadEnchantedMaterials(YamlConfiguration yaml, ItemManager itemManager, RecipeRegistry registry) {
        ConfigurationSection section = yaml.getConfigurationSection("enchanted_material");
        if (section == null) return 0;
        int count = 0;

        for (String recipeId : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(recipeId);
            if (entry == null) continue;

            try {
                currentCustomIngredients.clear();

                String resultId = entry.getString("result");
                String baseId = entry.getString("base");
                ItemStack result = itemManager.getRequiredItem(resultId);
                ItemStack baseMaterial = itemManager.getRequiredItem(baseId);

                NamespacedKey key = new NamespacedKey(plugin, recipeId);
                ShapedRecipe recipe = new ShapedRecipe(key, result);
                recipe.shape("MMM", "MMM", "MMM");
                // Use MaterialChoice so client-side recipe book "craftable" filter works
                recipe.setIngredient('M', new RecipeChoice.MaterialChoice(baseMaterial.getType()));

                // Track this custom ingredient for server-side validation
                trackCustomIngredient(baseId, baseMaterial.getType(), itemManager);

                RecipeDefinition.Ingredient ingredient = resolveIngredient(baseId, itemManager);
                List<RecipeDefinition.Ingredient> grid = new ArrayList<>();
                List<RecipeDefinition.Ingredient> ingredientUnits = new ArrayList<>();
                for (int i = 0; i < 9; i++) {
                    grid.add(ingredient);
                    ingredientUnits.add(ingredient);
                }

                RecipeDefinition def = createDefinition(
                        key,
                        recipe,
                        entry,
                        resultId,
                        RecipeDefinition.DisplayType.ENCHANTED_MATERIAL,
                        grid,
                        ingredientUnits);
                registry.register(def);
                count++;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load enchanted material recipe '" + recipeId + "': " + e.getMessage());
            }
        }
        return count;
    }

    // ── Upgrade Recipes ─────────────────────────────────────

    private int loadUpgrades(YamlConfiguration yaml, ItemManager itemManager, RecipeRegistry registry) {
        ConfigurationSection section = yaml.getConfigurationSection("upgrade");
        if (section == null) return 0;
        int count = 0;

        for (String recipeId : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(recipeId);
            if (entry == null) continue;

            // Automatically detect shaped vs shapeless based on keys
            if (entry.contains("pattern")) {
                if (registerShaped(recipeId, entry, itemManager, registry)) {
                    count++;
                }
            } else {
                // If it's a shapeless upgrade, we support the standard list format
                // or the simplified list format from the original code
                // The original code expected "ingredients" list of strings.
                // registerShapeless expects "ingredients" list of strings or maps.
                // This is compatible.
                if (registerShapeless(recipeId, entry, itemManager, registry)) {
                    count++;
                }
            }
        }
        return count;
    }

    // ── Utilities ───────────────────────────────────────────

    /**
     * Creates a RecipeDefinition with level or advancement gating from YAML config.
     * Also attaches the collected custom ingredient map for server-side validation.
     */
    private RecipeDefinition createDefinition(NamespacedKey key,
            org.bukkit.inventory.Recipe recipe,
            ConfigurationSection entry,
            String resultId,
            RecipeDefinition.DisplayType displayType,
            List<RecipeDefinition.Ingredient> grid,
            List<RecipeDefinition.Ingredient> ingredientUnits) {

        String advancementStr = entry.getString("advancement");
        int level = entry.getInt("level", 0);

        // Snapshot the custom ingredients collected during recipe building
        Map<String, Material> customIngs = currentCustomIngredients.isEmpty()
                ? Collections.emptyMap()
                : new HashMap<>(currentCustomIngredients);

        if (advancementStr != null && !advancementStr.isEmpty()) {
            // Advancement-gated
            NamespacedKey advKey = new NamespacedKey(plugin, advancementStr);
            return new RecipeDefinition(key, recipe, null, 0, advKey, customIngs, resultId, displayType, grid, ingredientUnits);
        } else if (level > 0) {
            // Level-gated
            int progLvl = Math.max(5, (level / 5) * 5);
            NamespacedKey advKey = plugin.getAdvancementManager().getProgressionKey(progLvl);
            return new RecipeDefinition(key, recipe, Skill.FISHING, level, advKey, customIngs, resultId, displayType, grid, ingredientUnits);
        } else {
            // No gating
            return new RecipeDefinition(key, recipe, Skill.FISHING, 0, null, customIngs, resultId, displayType, grid, ingredientUnits);
        }
    }

    private List<RecipeDefinition.Ingredient> buildShapedGrid(List<String> pattern,
            ConfigurationSection ingredients,
            ItemManager itemManager) {
        List<RecipeDefinition.Ingredient> grid = new ArrayList<>(Collections.nCopies(9, null));
        for (int row = 0; row < 3; row++) {
            String patternRow = row < pattern.size() ? pattern.get(row) : "";
            for (int col = 0; col < 3; col++) {
                char symbol = col < patternRow.length() ? patternRow.charAt(col) : ' ';
                if (symbol == ' ') {
                    continue;
                }

                RecipeDefinition.Ingredient ingredient = resolveIngredient(ingredients, String.valueOf(symbol), itemManager);
                grid.set((row * 3) + col, ingredient);
            }
        }
        return grid;
    }

    private List<RecipeDefinition.Ingredient> buildShapedIngredientUnits(List<String> pattern,
            ConfigurationSection ingredients,
            ItemManager itemManager) {
        List<RecipeDefinition.Ingredient> ingredientUnits = new ArrayList<>();
        for (String patternRow : pattern) {
            for (int col = 0; col < patternRow.length(); col++) {
                char symbol = patternRow.charAt(col);
                if (symbol == ' ') {
                    continue;
                }

                RecipeDefinition.Ingredient ingredient = resolveIngredient(ingredients, String.valueOf(symbol), itemManager);
                if (ingredient != null) {
                    ingredientUnits.add(ingredient);
                }
            }
        }
        return ingredientUnits;
    }

    private List<RecipeDefinition.Ingredient> buildShapelessIngredientUnits(List<?> ingredientList, ItemManager itemManager) {
        List<RecipeDefinition.Ingredient> ingredientUnits = new ArrayList<>();
        if (ingredientList == null) {
            return ingredientUnits;
        }

        for (Object ingredient : ingredientList) {
            if (ingredient instanceof String name) {
                ingredientUnits.add(resolveIngredient(name, itemManager));
            } else if (ingredient instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getValue() instanceof List<?> choices) {
                        List<String> names = new ArrayList<>();
                        for (Object choice : choices) {
                            names.add(String.valueOf(choice));
                        }
                        ingredientUnits.add(resolveIngredient(names, itemManager));
                    }
                }
            }
        }

        return ingredientUnits;
    }

    private List<RecipeDefinition.Ingredient> buildShapelessGrid(List<RecipeDefinition.Ingredient> ingredientUnits) {
        List<RecipeDefinition.Ingredient> grid = new ArrayList<>(Collections.nCopies(9, null));
        for (int i = 0; i < ingredientUnits.size() && i < 9; i++) {
            grid.set(i, ingredientUnits.get(i));
        }
        return grid;
    }

    private RecipeDefinition.Ingredient resolveIngredient(ConfigurationSection ingredients,
            String ingredientKey,
            ItemManager itemManager) {
        if (ingredients == null || !ingredients.contains(ingredientKey)) {
            return null;
        }

        if (ingredients.isList(ingredientKey)) {
            return resolveIngredient(ingredients.getStringList(ingredientKey), itemManager);
        }
        return resolveIngredient(ingredients.getString(ingredientKey), itemManager);
    }

    private RecipeDefinition.Ingredient resolveIngredient(String name, ItemManager itemManager) {
        return new RecipeDefinition.Ingredient(List.of(resolveIngredientOption(name, itemManager)));
    }

    private RecipeDefinition.Ingredient resolveIngredient(List<String> names, ItemManager itemManager) {
        List<RecipeDefinition.IngredientOption> options = new ArrayList<>();
        for (String name : names) {
            options.add(resolveIngredientOption(name, itemManager));
        }
        return new RecipeDefinition.Ingredient(options);
    }

    private RecipeDefinition.IngredientOption resolveIngredientOption(String name, ItemManager itemManager) {
        if (isVanillaMaterial(name)) {
            return new RecipeDefinition.IngredientOption(name, Material.valueOf(name), false);
        }

        ItemStack customItem = itemManager.getRequiredItem(name);
        return new RecipeDefinition.IngredientOption(name, customItem.getType(), true);
    }

    /**
     * Sets a shaped recipe ingredient. Uses MaterialChoice for all items so the client
     * recipe book filter works. Tracks custom ingredients for server-side validation.
     */
    private void setIngredient(ShapedRecipe recipe, char c, String name, ItemManager itemManager) {
        if (isVanillaMaterial(name)) {
            recipe.setIngredient(c, Material.valueOf(name));
        } else {
            // Use MaterialChoice so client-side recipe book "craftable" filter works.
            // Server-side CraftingListener validates the actual custom item identity.
            ItemStack customItem = itemManager.getRequiredItem(name);
            recipe.setIngredient(c, new RecipeChoice.MaterialChoice(customItem.getType()));
            trackCustomIngredient(name, customItem.getType(), itemManager);
        }
    }

    /**
     * Adds a shapeless recipe ingredient. Same MaterialChoice strategy as setIngredient.
     */
    private void addShapelessIngredient(ShapelessRecipe recipe, String name, ItemManager itemManager) {
        if (isVanillaMaterial(name)) {
            recipe.addIngredient(Material.valueOf(name));
        } else {
            // Use MaterialChoice so client-side recipe book "craftable" filter works.
            ItemStack customItem = itemManager.getRequiredItem(name);
            recipe.addIngredient(new RecipeChoice.MaterialChoice(customItem.getType()));
            trackCustomIngredient(name, customItem.getType(), itemManager);
        }
    }

    /**
     * Resolves a list of ingredient names to a MaterialChoice.
     */
    private RecipeChoice resolveChoice(List<String> names, ItemManager itemManager) {
        // Always use MaterialChoice so client-side recipe book "craftable" filter works.
        List<Material> materials = new ArrayList<>();
        for (String name : names) {
            if (isVanillaMaterial(name)) {
                materials.add(Material.valueOf(name));
            } else {
                ItemStack customItem = itemManager.getRequiredItem(name);
                materials.add(customItem.getType());
                trackCustomIngredient(name, customItem.getType(), itemManager);
            }
        }
        return new RecipeChoice.MaterialChoice(materials);
    }

    /**
     * Tracks a custom item ingredient for server-side validation.
     * Only tracks items that share a Material type with vanilla items or other custom items.
     */
    private void trackCustomIngredient(String name, Material material, ItemManager itemManager) {
        if (!isVanillaMaterial(name)) {
            currentCustomIngredients.put(name, material);
        }
    }

    /**
     * Checks whether a name refers to a vanilla Material (fully uppercase).
     */
    private boolean isVanillaMaterial(String name) {
        try {
            Material.valueOf(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

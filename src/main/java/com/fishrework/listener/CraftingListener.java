package com.fishrework.listener;

import com.fishrework.FishRework;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ItemStack;

/**
 * Blocks crafting of level/advancement-gated recipes.
 * Fully data-driven — no hardcoded recipe checks.
 * Adding a new gated recipe requires zero changes here.
 */
public class CraftingListener implements Listener {

    private final FishRework plugin;

    public CraftingListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null || !(recipe instanceof Keyed)) return;

        NamespacedKey recipeKey = ((Keyed) recipe).getKey();
        Player player = (Player) event.getView().getPlayer();

        // Single check against the recipe registry (level/advancement gating)
        if (!plugin.getRecipeRegistry().canCraft(player, recipeKey)) {
            event.getInventory().setResult(null);
            return;
        }

        // ── Custom Ingredient Validation ──
        // Since recipes use MaterialChoice (for client recipe book compatibility),
        // we must validate that custom items in the grid are actually the correct ones.
        com.fishrework.registry.RecipeDefinition def = plugin.getRecipeRegistry().getDefinition(recipeKey);
        if (def != null && def.hasCustomIngredients()) {
            if (!validateCustomIngredients(event, def)) {
                event.getInventory().setResult(null);
                return;
            }
        }

        // ── Trident Upgrade Logic ──
        // Check if we are crafting one of the specific trident recipes
        String key = recipeKey.getKey();
        if (key.equals("trident_1_recipe") || key.equals("trident_2_recipe")
            || key.equals("trident_3_recipe") || key.equals("hephaestean_recipe")) {
            handleSpecialCraftResult(event, key);
        }
    }

    /**
     * Validates that custom item ingredients in the crafting grid match expected IDs.
     * For each custom ingredient required by the recipe, checks that items in the grid
     * with the matching material type have the correct PDC custom_item ID.
     */
    private boolean validateCustomIngredients(PrepareItemCraftEvent event,
            com.fishrework.registry.RecipeDefinition def) {
        
        java.util.Map<String, org.bukkit.Material> customIngredients = def.getCustomIngredients();
        // Build a reverse map: Material -> expected custom item IDs
        java.util.Map<org.bukkit.Material, java.util.Set<String>> materialToIds = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, org.bukkit.Material> entry : customIngredients.entrySet()) {
            materialToIds.computeIfAbsent(entry.getValue(), k -> new java.util.HashSet<>()).add(entry.getKey());
        }

        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item == null || item.getType().isAir()) continue;

            java.util.Set<String> expectedIds = materialToIds.get(item.getType());
            if (expectedIds != null) {
                // This slot's material matches a custom ingredient — verify it's actually custom
                boolean matchesAny = false;
                for (String expectedId : expectedIds) {
                    if (plugin.getItemManager().isCustomItem(item, expectedId)) {
                        matchesAny = true;
                        break;
                    }
                }
                if (!matchesAny) {
                    return false; // Vanilla item used instead of required custom item
                }
            }
        }
        return true;
    }

    private void handleSpecialCraftResult(PrepareItemCraftEvent event, String recipeKey) {
        ItemStack result = event.getInventory().getResult();
        if (result == null) return;
        java.util.List<ItemStack> matrixItems = new java.util.ArrayList<>();
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item != null && !item.getType().isAir()) {
                matrixItems.add(item);
            }
        }
        event.getInventory().setResult(plugin.getRecipeCraftingManager()
                .applySpecialCraftingResult(recipeKey, result.clone(), matrixItems));
    }
}

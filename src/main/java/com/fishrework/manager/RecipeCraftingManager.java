package com.fishrework.manager;

import com.fishrework.FishRework;
import com.fishrework.model.PlayerData;
import com.fishrework.registry.RecipeDefinition;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipeCraftingManager {

    private final FishRework plugin;

    public RecipeCraftingManager(FishRework plugin) {
        this.plugin = plugin;
    }

    public record RecipeAvailability(boolean unlocked, boolean hasIngredients, String message) {
        public boolean canCraftNow() {
            return unlocked && hasIngredients;
        }
    }

    public record CraftAttempt(boolean success, String message, ItemStack craftedItem) {}

    private enum StorageKind {
        INVENTORY,
        FISH_BAG,
        LAVA_BAG
    }

    private record StorageEntry(StorageKind kind, int slot, ItemStack stack) {}

    public RecipeAvailability getAvailability(Player player, RecipeDefinition recipe) {
        if (!plugin.getRecipeRegistry().canCraft(player, recipe.getKey())) {
            return new RecipeAvailability(false, false, getLockMessage(recipe));
        }

        return findRemovalPlan(player, recipe) != null
                ? new RecipeAvailability(true, true, plugin.getLanguageManager().getString("recipeguidegui.craft", "Craft"))
                : new RecipeAvailability(true, false, plugin.getLanguageManager().getString(
                        "recipecraftingmanager.cannot_craft_insufficient_ingredients",
                        "Cannot craft, insufficient ingredients"));
    }

    public CraftAttempt craft(Player player, RecipeDefinition recipe) {
        RecipeAvailability availability = getAvailability(player, recipe);
        if (!availability.canCraftNow()) {
            return new CraftAttempt(false, availability.message(), null);
        }

        Map<Integer, Integer> removalPlan = findRemovalPlan(player, recipe);
        if (removalPlan == null) {
            return new CraftAttempt(false, plugin.getLanguageManager().getString(
                    "recipecraftingmanager.cannot_craft_insufficient_ingredients",
                    "Cannot craft, insufficient ingredients"), null);
        }

        ItemStack craftedItem = buildCraftResult(player, recipe, removalPlan);
        consumeIngredients(player, removalPlan);

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(craftedItem);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        return new CraftAttempt(true, plugin.getLanguageManager().getString(
                "recipecraftingmanager.crafted_item",
                "Crafted %item%.",
                "item", getDisplayName(craftedItem)), craftedItem);
    }

    public ItemStack applySpecialCraftingResult(String recipeKey, ItemStack baseResult, List<ItemStack> sourceIngredients) {
        if (baseResult == null) {
            return baseResult;
        }

        ItemStack inheritedSource = findInheritedSourceItem(baseResult, sourceIngredients);
        if (inheritedSource == null || !inheritedSource.hasItemMeta() || inheritedSource.getEnchantments().isEmpty()) {
            return baseResult;
        }

        ItemStack newResult = baseResult.clone();
        ItemMeta resultMeta = newResult.getItemMeta();
        if (resultMeta == null) {
            return baseResult;
        }

        boolean stripLoyalty = isTridentUpgradeRecipe(recipeKey);
        for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : inheritedSource.getEnchantments().entrySet()) {
            if (stripLoyalty && entry.getKey().equals(org.bukkit.enchantments.Enchantment.LOYALTY)) {
                continue;
            }

            int targetLevel = Math.max(newResult.getEnchantmentLevel(entry.getKey()), entry.getValue());
            resultMeta.addEnchant(entry.getKey(), targetLevel, true);
        }

        newResult.setItemMeta(resultMeta);
        plugin.getLoreManager().updateLore(newResult);
        return newResult;
    }

    private ItemStack buildCraftResult(Player player, RecipeDefinition recipe, Map<Integer, Integer> removalPlan) {
        ItemStack result = recipe.createResultItem(plugin.getItemManager());

        List<ItemStack> matchedIngredients = new ArrayList<>();
        for (Integer slot : removalPlan.keySet()) {
            StorageReference reference = decodeReference(slot);
            ItemStack source = getStack(player, reference);
            if (source != null && !source.getType().isAir()) {
                matchedIngredients.add(source.clone());
            }
        }

        return applySpecialCraftingResult(recipe.getKey().getKey(), result, matchedIngredients);
    }

    private void consumeIngredients(Player player, Map<Integer, Integer> removalPlan) {
        PlayerInventory inventory = player.getInventory();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        for (Map.Entry<Integer, Integer> entry : removalPlan.entrySet()) {
            StorageReference reference = decodeReference(entry.getKey());
            int amount = entry.getValue();

            ItemStack stack = getStack(player, reference);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            int remaining = stack.getAmount() - amount;
            ItemStack replacement = remaining <= 0 ? null : stack.clone();
            if (replacement != null) {
                replacement.setAmount(remaining);
            }

            if (reference.kind() == StorageKind.INVENTORY) {
                inventory.setItem(reference.slot(), replacement);
            } else {
                setBagStack(data, reference, replacement);
            }
        }
    }

    private Map<Integer, Integer> findRemovalPlan(Player player, RecipeDefinition recipe) {
        List<RecipeDefinition.Ingredient> requiredUnits = new ArrayList<>(recipe.getIngredientUnits());
        if (requiredUnits.isEmpty()) {
            return new HashMap<>();
        }

        List<StorageEntry> storageEntries = getStorageEntries(player);
        if (storageEntries.isEmpty()) {
            return null;
        }

        requiredUnits.sort(Comparator
                .comparingInt((RecipeDefinition.Ingredient ingredient) -> getCandidateCount(ingredient, storageEntries))
                .thenComparing(Comparator.comparingInt(RecipeDefinition.Ingredient::specificityScore).reversed()));

        Map<Integer, Integer> usage = new HashMap<>();
        if (assignUnits(0, requiredUnits, storageEntries, usage)) {
            return usage;
        }
        return null;
    }

    private boolean assignUnits(int index,
            List<RecipeDefinition.Ingredient> requiredUnits,
            List<StorageEntry> storageEntries,
            Map<Integer, Integer> usage) {
        if (index >= requiredUnits.size()) {
            return true;
        }

        RecipeDefinition.Ingredient ingredient = requiredUnits.get(index);
        for (StorageEntry entry : storageEntries) {
            int encodedSlot = encodeReference(entry.kind(), entry.slot());
            int used = usage.getOrDefault(encodedSlot, 0);
            if (used >= entry.stack().getAmount()) {
                continue;
            }
            if (!ingredient.matches(entry.stack(), plugin.getItemManager())) {
                continue;
            }

            usage.put(encodedSlot, used + 1);
            if (assignUnits(index + 1, requiredUnits, storageEntries, usage)) {
                return true;
            }

            if (used == 0) {
                usage.remove(encodedSlot);
            } else {
                usage.put(encodedSlot, used);
            }
        }

        return false;
    }

    private int getCandidateCount(RecipeDefinition.Ingredient ingredient, List<StorageEntry> storageEntries) {
        int count = 0;
        for (StorageEntry entry : storageEntries) {
            if (ingredient.matches(entry.stack(), plugin.getItemManager())) {
                count += entry.stack().getAmount();
            }
        }
        return count;
    }

    private List<StorageEntry> getStorageEntries(Player player) {
        List<StorageEntry> entries = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack stack = storage[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            entries.add(new StorageEntry(StorageKind.INVENTORY, slot, stack));
        }

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        addBagEntries(entries, StorageKind.FISH_BAG, data != null ? data.getFishBagContents() : null);
        addBagEntries(entries, StorageKind.LAVA_BAG, data != null ? data.getLavaBagContents() : null);
        return entries;
    }

    private void addBagEntries(List<StorageEntry> entries, StorageKind kind, ItemStack[] contents) {
        if (contents == null) {
            return;
        }
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            entries.add(new StorageEntry(kind, slot, stack));
        }
    }

    private String getLockMessage(RecipeDefinition recipe) {
        if (recipe.hasLevelRequirement()) {
            return plugin.getLanguageManager().getString(
                    "recipeguidegui.requires_fishing_level",
                    "Requires Fishing Level %level%",
                    "level", String.valueOf(recipe.getRequiredLevel()));
        }
        if (recipe.hasAdvancementRequirement()) {
            return plugin.getLanguageManager().getString(
                    "recipeguidegui.requires_advancement",
                    "Requires %advancement%",
                    "advancement", RecipeDefinition.toFriendlyName(recipe.getRequiredAdvancement().getKey()));
        }
        return plugin.getLanguageManager().getString("recipecraftingmanager.recipe_locked", "Recipe locked");
    }

    private boolean isTridentUpgradeRecipe(String recipeKey) {
        return "trident_1_recipe".equals(recipeKey)
                || "trident_2_recipe".equals(recipeKey)
                || "trident_3_recipe".equals(recipeKey)
                || "hephaestean_recipe".equals(recipeKey);
    }

    private ItemStack findInheritedSourceItem(ItemStack result, List<ItemStack> sourceIngredients) {
        if (result == null || sourceIngredients == null || sourceIngredients.isEmpty()) {
            return null;
        }

        Material resultMaterial = getLogicalMaterial(result);
        if (resultMaterial == null) {
            return null;
        }

        ItemStack bestMatch = null;
        int bestScore = Integer.MIN_VALUE;
        for (ItemStack item : sourceIngredients) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            if (getLogicalMaterial(item) != resultMaterial) {
                continue;
            }

            int score = 0;
            if (item.getType() == result.getType()) {
                score += 20;
            }
            if (plugin.getItemManager().isCustomItem(item)) {
                score += 10;
            }
            score += item.getEnchantments().size();

            if (score > bestScore) {
                bestScore = score;
                bestMatch = item;
            }
        }
        return bestMatch;
    }

    private Material getLogicalMaterial(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        Material fallback = plugin.getItemManager().getVanillaFallbackMaterial(item);
        return fallback != null ? fallback : item.getType();
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

    private int encodeReference(StorageKind kind, int slot) {
        return switch (kind) {
            case INVENTORY -> slot;
            case FISH_BAG -> 1000 + slot;
            case LAVA_BAG -> 2000 + slot;
        };
    }

    private StorageReference decodeReference(int encoded) {
        if (encoded >= 2000) {
            return new StorageReference(StorageKind.LAVA_BAG, encoded - 2000);
        }
        if (encoded >= 1000) {
            return new StorageReference(StorageKind.FISH_BAG, encoded - 1000);
        }
        return new StorageReference(StorageKind.INVENTORY, encoded);
    }

    private record StorageReference(StorageKind kind, int slot) {}

    private ItemStack getStack(Player player, StorageReference reference) {
        return switch (reference.kind()) {
            case INVENTORY -> player.getInventory().getItem(reference.slot());
            case FISH_BAG -> getBagStack(plugin.getPlayerData(player.getUniqueId()), true, reference.slot());
            case LAVA_BAG -> getBagStack(plugin.getPlayerData(player.getUniqueId()), false, reference.slot());
        };
    }

    private ItemStack getBagStack(PlayerData data, boolean fishBag, int slot) {
        if (data == null) {
            return null;
        }
        ItemStack[] contents = fishBag ? data.getFishBagContents() : data.getLavaBagContents();
        if (contents == null || slot < 0 || slot >= contents.length) {
            return null;
        }
        return contents[slot];
    }

    private void setBagStack(PlayerData data, StorageReference reference, ItemStack stack) {
        if (data == null) {
            return;
        }
        ItemStack[] contents = reference.kind() == StorageKind.FISH_BAG ? data.getFishBagContents() : data.getLavaBagContents();
        if (contents == null || reference.slot() < 0 || reference.slot() >= contents.length) {
            return;
        }
        contents[reference.slot()] = stack;
        if (reference.kind() == StorageKind.FISH_BAG) {
            data.setFishBagContents(contents);
        } else if (reference.kind() == StorageKind.LAVA_BAG) {
            data.setLavaBagContents(contents);
        }
    }
}

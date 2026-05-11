package com.fishrework.registry;

import com.fishrework.FishRework;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Skill;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Central registry for all level/advancement-gated recipes.
 * CraftingListener queries this instead of hardcoding recipe checks.
 * AdvancementManager queries this to discover recipes on level-up.
 */
public class RecipeRegistry {

    private final FishRework plugin;
    private final Map<NamespacedKey, RecipeDefinition> recipes = new LinkedHashMap<>();
    private final Map<String, List<RecipeDefinition>> recipesByResultId = new LinkedHashMap<>();
    private final Map<Skill, Map<Integer, List<RecipeDefinition>>> recipesByLevel = new EnumMap<>(Skill.class);
    private final Map<NamespacedKey, List<RecipeDefinition>> recipesByAdvancement = new HashMap<>();

    public RecipeRegistry(FishRework plugin) {
        this.plugin = plugin;
    }

    public void register(RecipeDefinition def) {
        recipes.put(def.getKey(), def);
        if (def.getResultId() != null && !def.getResultId().isBlank()) {
            recipesByResultId.computeIfAbsent(def.getResultId(), ignored -> new ArrayList<>()).add(def);
        }
        if (def.hasLevelRequirement()) {
            recipesByLevel
                    .computeIfAbsent(def.getRequiredSkill(), ignored -> new HashMap<>())
                    .computeIfAbsent(def.getRequiredLevel(), ignored -> new ArrayList<>())
                    .add(def);
        }
        if (def.hasAdvancementRequirement()) {
            recipesByAdvancement
                    .computeIfAbsent(def.getRequiredAdvancement(), ignored -> new ArrayList<>())
                    .add(def);
        }
        plugin.getServer().addRecipe(def.getRecipe());
    }

    public RecipeDefinition get(NamespacedKey key) {
        return recipes.get(key);
    }

    /** Alias for {@link #get(NamespacedKey)} — used by CraftingListener for clarity. */
    public RecipeDefinition getDefinition(NamespacedKey key) {
        return recipes.get(key);
    }

    public Collection<RecipeDefinition> getAll() {
        return Collections.unmodifiableCollection(recipes.values());
    }

    public List<RecipeDefinition> getRecipesForResultId(String resultId) {
        if (resultId == null || resultId.isBlank()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(recipesByResultId.getOrDefault(resultId, Collections.emptyList()));
    }

    public Set<String> getRecipeResultIds() {
        return Collections.unmodifiableSet(recipesByResultId.keySet());
    }

    public List<RecipeDefinition> getRecipesForResultItem(ItemStack item) {
        String customItemId = plugin.getItemManager().getCustomItemId(item);
        if (customItemId == null) {
            return Collections.emptyList();
        }
        return getRecipesForResultId(customItemId);
    }

    /**
     * Checks if a player meets the requirements to craft a recipe.
     * Returns true if allowed, false if blocked.
     */
    public boolean canCraft(Player player, NamespacedKey recipeKey) {
        RecipeDefinition def = recipes.get(recipeKey);
        if (def == null) return true; // Not a gated recipe

        // 1. Level Requirement
        // Level recipes may also carry a progression advancement key for display/recipe-book
        // sync, but the configured level remains the crafting authority.
        if (def.hasLevelRequirement()) {
            PlayerData data = plugin.getPlayerData(player.getUniqueId());
            if (data == null) return false;
            int level = data.getLevel(def.getRequiredSkill());
            if (level < def.getRequiredLevel()) return false;
        }

        // 2. Advancement Requirement
        if (!def.hasLevelRequirement() && def.hasAdvancementRequirement()) {
            return plugin.getAdvancementManager().hasAdvancement(player, def.getRequiredAdvancement());
        }

        return true;
    }

    /**
     * Discovers all recipes a player qualifies for at a given skill level.
     * Called on level-up and on join.
     */
    public void discoverRecipesForLevel(Player player, Skill skill, int level) {
        for (RecipeDefinition def : recipes.values()) {
            if (def.getRequiredSkill() == skill && level >= def.getRequiredLevel()) {
                if (!player.hasDiscoveredRecipe(def.getKey())) {
                    player.discoverRecipe(def.getKey());
                }
            }
        }
    }

    /**
     * Returns all recipe keys that require a specific skill level.
     */
    public List<RecipeDefinition> getRecipesForLevel(Skill skill, int level) {
        Map<Integer, List<RecipeDefinition>> byLevel = recipesByLevel.get(skill);
        if (byLevel == null) {
            return Collections.emptyList();
        }
        List<RecipeDefinition> list = byLevel.get(level);
        if (list == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Returns all recipe keys that require a specific advancement.
     */
    public List<RecipeDefinition> getRecipesForAdvancement(NamespacedKey advancementKey) {
        List<RecipeDefinition> list = recipesByAdvancement.get(advancementKey);
        if (list == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Fully synchronizes a player's knowledge book with their eligibility.
     * Iterates ALL recipes.
     * - If eligible -> discover.
     * - If NOT eligible -> undiscover (fixes revoked advancements/levels).
     */
    public void syncRecipes(Player player) {
        for (RecipeDefinition def : recipes.values()) {
            boolean allowed = canCraft(player, def.getKey());
            if (allowed) {
                if (!player.hasDiscoveredRecipe(def.getKey())) {
                    player.discoverRecipe(def.getKey());
                }
            } else {
                if (player.hasDiscoveredRecipe(def.getKey())) {
                    player.undiscoverRecipe(def.getKey());
                }
            }
        }
    }
}

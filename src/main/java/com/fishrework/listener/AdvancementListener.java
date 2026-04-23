package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.registry.RecipeDefinition;
import com.fishrework.util.FeatureKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class AdvancementListener implements Listener {

    private final FishRework plugin;

    public AdvancementListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (!plugin.isFeatureEnabled(FeatureKeys.ADVANCEMENTS_ENABLED)) return;

        // Sync recipes whenever ANY advancement is completed
        // This handles vanilla commands, manual grants, and normal progression
        plugin.getRecipeRegistry().syncRecipes(event.getPlayer());

        String advancementPath = event.getAdvancement().getKey().getKey();
        if (advancementPath.startsWith("fishing/level_")) {
            return;
        }

        List<RecipeDefinition> recipes = plugin.getRecipeRegistry().getRecipesForAdvancement(event.getAdvancement().getKey());
        if (recipes.isEmpty()) {
            return;
        }

        event.getPlayer().sendMessage(plugin.getLanguageManager().getMessage("advancementlistener.recipes_unlocked", "   Recipes Unlocked:").color(NamedTextColor.LIGHT_PURPLE));
        for (RecipeDefinition recipe : recipes) {
            ItemStack result = recipe.createResultItem(plugin.getItemManager());
            String name = result.hasItemMeta() && result.getItemMeta().hasDisplayName()
                    ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(result.getItemMeta().displayName())
                    : RecipeDefinition.toFriendlyName(result.getType().name());

            event.getPlayer().sendMessage(Component.text(plugin.getLanguageManager().getString(
                            "advancementlistener.recipe_entry",
                            "    ★ Recipe: %name%",
                            "name", name))
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false)
                    .clickEvent(ClickEvent.runCommand("/fishing recipe " + recipe.getResultId()))
                    .hoverEvent(plugin.getLanguageManager().getMessage("advancementlistener.click_to_open_recipe", "Click to open recipe").color(NamedTextColor.GREEN)));
        }
    }
}

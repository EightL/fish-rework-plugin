package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.registry.RecipeDefinition;
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

        event.getPlayer().sendMessage(Component.text("   Recipes Unlocked:").color(NamedTextColor.LIGHT_PURPLE));
        for (RecipeDefinition recipe : recipes) {
            ItemStack result = recipe.createResultItem(plugin.getItemManager());
            String name = result.hasItemMeta() && result.getItemMeta().hasDisplayName()
                    ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(result.getItemMeta().displayName())
                    : RecipeDefinition.toFriendlyName(result.getType().name());

            event.getPlayer().sendMessage(Component.text("    ★ Recipe: " + name)
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false)
                    .clickEvent(ClickEvent.runCommand("/fishing recipe " + recipe.getResultId()))
                    .hoverEvent(Component.text("Click to open recipe").color(NamedTextColor.GREEN)));
        }
    }
}

package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.gui.ShopMenuGUI;
import com.fishrework.util.FeatureKeys;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public class FishStallListener implements Listener {

    private final FishRework plugin;
    private final NamespacedKey stallInteractKey;

    public FishStallListener(FishRework plugin) {
        this.plugin = plugin;
        this.stallInteractKey = new NamespacedKey(plugin, "fish_stall_interact");
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;

        if (!interaction.getPersistentDataContainer().has(stallInteractKey, PersistentDataType.STRING)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();

        if (!plugin.isFeatureEnabled(FeatureKeys.SHOP_ENABLED)) {
            player.sendMessage(plugin.getLanguageManager()
                    .getMessage("fishingcommand.feature_shop_disabled", "The shop is disabled on this server.")
                    .color(NamedTextColor.RED));
            return;
        }

        new ShopMenuGUI(plugin, player).open(player);
    }
}

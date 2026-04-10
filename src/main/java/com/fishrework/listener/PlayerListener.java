package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Skill;
import com.fishrework.util.FeatureKeys;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final FishRework plugin;

    public PlayerListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Load player data fully async (loadPlayer includes settings, bags, balance, etc.)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerData data = plugin.getDatabaseManager().loadPlayer(event.getPlayer().getUniqueId());

            // Back to main thread to cache it and sync advancements
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getPlayerDataMap().put(event.getPlayer().getUniqueId(), data);

                // Reset transient session state on join (preserves persisted settings already loaded above)
                data.resetSession();

                // Sync advancements with current level
                if (event.getPlayer().isOnline()) {
                    int fishingLevel = data.getLevel(Skill.FISHING);
                    if (plugin.isFeatureEnabled(FeatureKeys.ADVANCEMENTS_ENABLED)) {
                        plugin.getAdvancementManager().syncAdvancements(event.getPlayer(), fishingLevel);
                    } else {
                        plugin.getRecipeRegistry().syncRecipes(event.getPlayer());
                    }

                    // Notify admins of available updates (check runs async at startup)
                    if (event.getPlayer().hasPermission("fishrework.admin")
                            && plugin.getUpdateChecker() != null) {
                        plugin.getUpdateChecker().notifyPlayer(event.getPlayer());
                    }
                }
            });
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getBossBarManager().removeAllBossBars(event.getPlayer());
        plugin.clearFishingTipTracking(event.getPlayer().getUniqueId());
        PlayerData data = plugin.getPlayerDataMap().remove(event.getPlayer().getUniqueId());
        if (data != null) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.getDatabaseManager().savePlayer(data);
            });
        }
    }

}

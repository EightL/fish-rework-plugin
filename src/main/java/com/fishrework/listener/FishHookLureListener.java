package com.fishrework.listener;

import com.fishrework.FishRework;
import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

/**
 * When Lure 4–6 is on the rod, vanilla would break fishing. We disable vanilla Lure on the hook
 * and set wait times via Paper API so levels 4–6 work.
 */
public class FishHookLureListener implements Listener {

    private final int lureCap;
    private final int ticksPerLevel;
    private final int defaultMinWait;
    private final int defaultMaxWait;

    private final FishRework plugin;

    public FishHookLureListener(FishRework plugin) {
        this.plugin = plugin;
        this.lureCap = plugin.getConfig().getInt("enchantments.lure_cap", 3);
        this.ticksPerLevel = plugin.getConfig().getInt("enchantments.ticks_per_lure_level", 100);
        this.defaultMinWait = plugin.getConfig().getInt("enchantments.default_min_wait_ticks", 100);
        this.defaultMaxWait = plugin.getConfig().getInt("enchantments.default_max_wait_ticks", 600);
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.FISHING) return;
        FishHook hook = event.getHook();
        Player player = event.getPlayer();

        // Skip lava bobbers — they have their own timing in LavaBobberTask
        if (plugin.getLavaFishingListener() != null
                && plugin.getLavaFishingListener().isActiveLavaFishing(player)) {
            return;
        }

        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod.getType() != Material.FISHING_ROD || !rod.hasItemMeta()) return;
        int lureLevel = rod.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LURE);
        if (lureLevel <= lureCap) return;

        hook.setApplyLure(false);
        int minWait = Math.max(1, defaultMinWait - lureLevel * ticksPerLevel);
        int maxWait = Math.max(1, defaultMaxWait - lureLevel * ticksPerLevel);
        hook.setWaitTime(minWait, maxWait);
    }
}

package com.fishrework.listener;

import com.fishrework.FishRework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;

public class MobFireListener implements Listener {

    private final FishRework plugin;

    public MobFireListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!plugin.getMobManager().isFishedMob(entity)) return;

        event.setCancelled(true);
        entity.setFireTicks(0);
        entity.setVisualFire(false);
    }
}


package com.fishrework.listener;

import com.fishrework.FishRework;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;

public class PiglinCrownListener implements Listener {

    private static final double PACIFY_RADIUS_XZ = 16.0;
    private static final double PACIFY_RADIUS_Y = 8.0;

    private final FishRework plugin;

    public PiglinCrownListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerHitsZombifiedPiglin(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof PigZombie piglin)) return;
        if (isFishedMob(piglin)) return;

        Player attacker = resolveAttackingPlayer(event.getDamager());
        if (attacker == null || !isWearingPiglinCrown(attacker)) return;

        pacify(piglin);
        for (Entity nearby : piglin.getNearbyEntities(PACIFY_RADIUS_XZ, PACIFY_RADIUS_Y, PACIFY_RADIUS_XZ)) {
            if (nearby instanceof PigZombie nearbyPiglin && !isFishedMob(nearbyPiglin)) {
                pacify(nearbyPiglin);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onZombifiedPiglinTargetsPlayer(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof PigZombie piglin)) return;
        if (isFishedMob(piglin)) return;
        if (!(event.getTarget() instanceof Player player)) return;
        if (!isWearingPiglinCrown(player)) return;

        event.setCancelled(true);
        pacify(piglin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onZombifiedPiglinDamagesCrownedPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDamager() instanceof PigZombie piglin)) return;
        if (isFishedMob(piglin)) return;
        if (!isWearingPiglinCrown(player)) return;

        event.setCancelled(true);
        pacify(piglin);
    }

    private Player resolveAttackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean isWearingPiglinCrown(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        return plugin.getItemManager().isCustomItem(helmet, "piglin_crown_helmet");
    }

    private boolean isFishedMob(LivingEntity entity) {
        return plugin.getMobManager() != null && plugin.getMobManager().isFishedMob(entity);
    }

    private void pacify(PigZombie piglin) {
        piglin.setTarget(null);
        piglin.setAnger(0);
    }
}
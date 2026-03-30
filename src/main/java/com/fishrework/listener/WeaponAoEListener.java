package com.fishrework.listener;

import com.fishrework.FishRework;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Applies weapon-based area damage for configured custom items.
 * Currently used for Gehenna splash damage.
 */
public class WeaponAoEListener implements Listener {

    private final FishRework plugin;
    private final Set<UUID> splashVictims = new HashSet<>();
    private static final Particle.DustOptions AOE_HIT_RED = new Particle.DustOptions(Color.fromRGB(200, 40, 40), 1.2f);

    public WeaponAoEListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("combat.gehenna_aoe.enabled", true)) return;

        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (victim instanceof Player) return;

        // Ignore secondary events caused by our own splash calls.
        if (splashVictims.contains(victim.getUniqueId())) return;

        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || !weapon.hasItemMeta()) return;

        String itemId = weapon.getItemMeta().getPersistentDataContainer()
                .get(plugin.getItemManager().CUSTOM_ITEM_KEY, PersistentDataType.STRING);
        if (!"gehenna".equals(itemId)) return;

        double radius = Math.max(0.0, plugin.getConfig().getDouble("combat.gehenna_aoe.radius", 4.0));
        double multiplier = plugin.getConfig().getDouble("combat.gehenna_aoe.damage_multiplier", 0.25);
        if (radius <= 0.0 || multiplier <= 0.0) return;

        double splashDamage = event.getFinalDamage() * multiplier;
        if (splashDamage <= 0.0) return;

        for (Entity nearby : victim.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity target)) continue;
            if (target instanceof Player) continue;
            if (target.equals(victim)) continue;
            if (target.isDead()) continue;

            UUID targetId = target.getUniqueId();
            splashVictims.add(targetId);
            try {
                target.damage(splashDamage, player);
                target.getWorld().spawnParticle(
                        Particle.DUST,
                        target.getLocation().add(0, target.getHeight() * 0.5, 0),
                        8,
                        0.25,
                        0.35,
                        0.25,
                        AOE_HIT_RED
                );
            } finally {
                splashVictims.remove(targetId);
            }
        }
    }
}

package com.fishrework.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Squid;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles lava creature lifecycle: spawn, floating in lava, fire immunity, and cleanup.
 */
public class LavaCreatureManager implements Listener {

    private static final String LAVA_CREATURE_META = "lava_creature";

    private final Plugin plugin;
    private final Map<UUID, BukkitTask> floatTasks = new HashMap<>();

    public LavaCreatureManager(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public LivingEntity spawnCreature(Location loc, EntityType type, String name, double hp) {
        LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, type);

        entity.setCustomName("§c§l" + name);
        entity.setCustomNameVisible(true);

        if (entity.getAttribute(Attribute.MAX_HEALTH) != null) {
            entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(hp);
            entity.setHealth(hp);
        }

        entity.setRemoveWhenFarAway(false);
        trackCreature(entity);
        return entity;
    }

    public void trackCreature(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.isDead()) return;
        if (entity.hasMetadata(LAVA_CREATURE_META) && floatTasks.containsKey(entity.getUniqueId())) return;

        entity.setMetadata(LAVA_CREATURE_META, new FixedMetadataValue(plugin, true));
        startFloating(entity);
    }

    private void startFloating(LivingEntity entity) {
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (entity.isDead() || !entity.isValid()) {
                    cancel();
                    floatTasks.remove(entity.getUniqueId());
                    return;
                }

                if (entity.isInLava()) {
                    Vector vel = entity.getVelocity();

                    // Squids have stronger downward drift in non-water fluids,
                    // so force a guaranteed upward component while in lava.
                    if (entity instanceof Squid) {
                        double liftedY = Math.max(vel.getY(), 0.0) + 0.18;
                        vel.setY(Math.min(liftedY, 0.30));
                    } else {
                        vel.setY(Math.min(vel.getY() + 0.12, 0.18));
                    }

                    entity.setVelocity(vel);

                    entity.setFireTicks(0);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);

        floatTasks.put(entity.getUniqueId(), task);
    }

    public void removeCreature(LivingEntity entity) {
        BukkitTask task = floatTasks.remove(entity.getUniqueId());
        if (task != null) task.cancel();
        entity.remove();
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity)) return;
        if (!e.getEntity().hasMetadata(LAVA_CREATURE_META)) return;

        switch (e.getCause()) {
            case LAVA, FIRE, FIRE_TICK, HOT_FLOOR -> e.setCancelled(true);
        }
    }

    public void shutdown() {
        floatTasks.values().forEach(BukkitTask::cancel);
        floatTasks.clear();
    }
}
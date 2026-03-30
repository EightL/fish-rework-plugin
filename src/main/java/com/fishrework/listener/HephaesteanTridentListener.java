package com.fishrework.listener;

import com.fishrework.FishRework;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class HephaesteanTridentListener implements Listener {

    private static final String BURST_DAMAGE_PATH = "items.hephaesteantrident.burstdamage";
    private static final String FIRE_TICK_DAMAGE_PATH = "items.hephaesteantrident.firetickdamage";
    private static final String VISUAL_FIRE_TICKS_PATH = "items.hephaesteantrident.visualfireticks";

    private final FishRework plugin;
    private final NamespacedKey hephaesteanProjectileKey;

    public HephaesteanTridentListener(FishRework plugin) {
        this.plugin = plugin;
        this.hephaesteanProjectileKey = new NamespacedKey(plugin, "hephaestean_projectile");
    }

    private boolean isHephaesteanItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (item.getItemMeta().getPersistentDataContainer()
            .has(plugin.getItemManager().HEPHAESTEANTRIDENTKEY, PersistentDataType.BYTE)) {
            return true;
        }

        String customId = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getItemManager().CUSTOM_ITEM_KEY, PersistentDataType.STRING);
        return "hephaestean_trident".equalsIgnoreCase(customId);
    }

    private boolean isHephaestean(Trident trident) {
        if (trident.getPersistentDataContainer().has(hephaesteanProjectileKey, PersistentDataType.BYTE)) {
            return true;
        }
        return isHephaesteanItem(trident.getItem());
    }

    @EventHandler
    public void onTridentLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!isHephaesteanItem(trident.getItem())) return;

        trident.getPersistentDataContainer().set(hephaesteanProjectileKey, PersistentDataType.BYTE, (byte) 1);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!trident.isValid() || trident.isDead()) {
                    cancel();
                    return;
                }

                Location loc = trident.getLocation();
                World world = loc.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }

                world.spawnParticle(Particle.FLAME, loc, 3, 0.05, 0.05, 0.05, 0.01);
                world.spawnParticle(Particle.LAVA, loc, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @EventHandler
    public void onTridentHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!isHephaestean(trident)) return;
        if (!(event.getHitEntity() instanceof LivingEntity target)) return;
        if (!(trident.getShooter() instanceof Player shooter)) return;

        Location loc = target.getLocation().add(0, 1.0, 0);
        World world = loc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.FLAME, loc, 14, 0.35, 0.35, 0.35, 0.04);

        Particle.DustOptions lightBlue = new Particle.DustOptions(Color.fromRGB(100, 210, 255), 1.3f);
        world.spawnParticle(Particle.DUST, loc, 10, 0.3, 0.3, 0.3, 0.0, lightBlue);

        world.spawnParticle(Particle.LAVA, loc, 4, 0.2, 0.2, 0.2, 0.0);

        world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 0.7f, 0.6f);
        world.playSound(loc, Sound.ITEM_FIRECHARGE_USE, 0.5f, 1.2f);

        int visualFireTicks = Math.max(1, plugin.getConfig().getInt(VISUAL_FIRE_TICKS_PATH, 20));
        target.setVisualFire(true);
        target.setFireTicks(Math.max(target.getFireTicks(), visualFireTicks));

        double burstDamage = plugin.getConfig().getDouble(BURST_DAMAGE_PATH, 14.0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!target.isValid() || target.isDead()) return;
            target.damage(burstDamage, shooter);
        }, 1L);

        // Fire-resistant mobs don't take vanilla burn ticks, so apply one guaranteed burn pulse.
        double fireTickDamage = plugin.getConfig().getDouble(FIRE_TICK_DAMAGE_PATH, 2.0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!target.isValid() || target.isDead()) return;
            if (fireTickDamage > 0) {
                target.damage(fireTickDamage, shooter);
            }
            target.setVisualFire(false);
            target.setFireTicks(0);
        }, visualFireTicks);
    }
}

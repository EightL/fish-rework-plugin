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

    private static final Particle.DustOptions PRISMARINE_DUST =
        new Particle.DustOptions(Color.fromRGB(70, 210, 190), 1.1f);
    private static final Particle.DustOptions TIDE_DUST =
        new Particle.DustOptions(Color.fromRGB(90, 170, 255), 1.2f);
    private static final Particle.DustOptions DREAD_DUST =
        new Particle.DustOptions(Color.fromRGB(90, 30, 130), 1.1f);
    private static final Particle.DustOptions POSEIDON_DUST =
        new Particle.DustOptions(Color.fromRGB(120, 210, 255), 1.3f);
    private static final Particle.DustOptions FORGE_DUST =
        new Particle.DustOptions(Color.fromRGB(255, 140, 60), 1.2f);
    private static final int ENCAPSULATE_SHELL_STEPS = 10;
    private static final int ENCAPSULATE_IMPLODE_STEPS = 4;
    private static final double ENCAPSULATE_RADIUS = 1.25;
    private static final double ENCAPSULATE_HEIGHT_PADDING = 0.4;
    private static final double POSEIDON_IMPLODE_DAMAGE = 4.0;
    private static final int DREADSPEAR_VORTEX_STEPS = 18;
    private static final double DREADSPEAR_VORTEX_RADIUS = 1.05;
    private static final double DREADSPEAR_HIT_DAMAGE = 4.0;
    private static final int TIDESPLITTER_SPIRAL_STEPS = 28;
    private static final double TIDESPLITTER_SPIRAL_RADIUS_MIN = 0.25;
    private static final double TIDESPLITTER_SPIRAL_RADIUS_MAX = 1.35;
    private static final double TIDESPLITTER_SPIRAL_HEIGHT = 2.2;
    private static final double TIDESPLITTER_SPIRAL_DAMAGE = 4.0;

    private static final String BURST_DAMAGE_PATH = "items.hephaesteantrident.burstdamage";
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

    private String getCustomItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getItemManager().CUSTOM_ITEM_KEY, PersistentDataType.STRING);
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
        ItemStack item = trident.getItem();
        String customId = getCustomItemId(item);
        if (!isTrackedTrident(customId)) return;

        if (isHephaesteanItem(item)) {
            trident.getPersistentDataContainer().set(hephaesteanProjectileKey, PersistentDataType.BYTE, (byte) 1);
        }

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!trident.isValid() || trident.isDead()) {
                    cancel();
                    return;
                }
                if (trident.isOnGround() && ticks > 4) {
                    cancel();
                    return;
                }

                Location loc = trident.getLocation();
                World world = loc.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }

                ticks++;
                spawnTridentTrail(world, loc, customId, ticks);

            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onTridentHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(event.getHitEntity() instanceof LivingEntity target)) return;
        if (!(trident.getShooter() instanceof Player shooter)) return;

        if (isHephaestean(trident)) {
            handleHephaesteanHit(shooter, target);
            return;
        }

        String customId = getCustomItemId(trident.getItem());
        if (customId == null) return;
        switch (customId) {
            case "poseidons_trident" -> handlePoseidonHit(shooter, target);
            case "trident_3" -> handleDreadspearHit(shooter, target);
            default -> {
            }
        }
    }

    private void handlePoseidonHit(Player shooter, LivingEntity target) {
        startEncapsulateImplode(shooter, target, POSEIDON_DUST, Particle.NAUTILUS,
            Particle.ELECTRIC_SPARK, Particle.NAUTILUS, POSEIDON_IMPLODE_DAMAGE);
    }

    private void handleDreadspearHit(Player shooter, LivingEntity target) {
        startDreadspearVortex(shooter, target);
    }

    private void handleHephaesteanHit(Player shooter, LivingEntity target) {
        startEncapsulateImplode(shooter, target, FORGE_DUST, Particle.FLAME,
                Particle.ELECTRIC_SPARK, Particle.END_ROD,
                plugin.getConfig().getDouble(BURST_DAMAGE_PATH, 14.0));

        Location loc = target.getLocation().add(0, 1.0, 0);
        World world = loc.getWorld();
        if (world == null) return;

        world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 0.7f, 0.6f);
        world.playSound(loc, Sound.ITEM_FIRECHARGE_USE, 0.5f, 1.2f);

        target.setVisualFire(false);
        target.setFireTicks(0);
    }

    @EventHandler
    public void onTidesplitterHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(event.getHitEntity() instanceof LivingEntity target)) return;
        if (!(trident.getShooter() instanceof Player shooter)) return;

        String customId = getCustomItemId(trident.getItem());
        if (!"trident_2".equals(customId)) return;

        startTidesplitterSpiral(shooter, target);
    }

    private boolean isTrackedTrident(String customId) {
        if (customId == null || customId.isBlank()) return false;
        return switch (customId) {
            case "beginner_trident_1",
                    "beginner_trident_2",
                    "trident_1",
                    "trident_2",
                    "trident_3",
                    "poseidons_trident",
                    "hephaestean_trident" -> true;
            default -> false;
        };
    }

    private void spawnTridentTrail(World world, Location loc, String customId, int ticks) {
        if (customId == null) return;
        switch (customId) {
            case "beginner_trident_1" -> {
                if (ticks % 3 == 0) {
                    world.spawnParticle(Particle.BUBBLE, loc, 2, 0.05, 0.05, 0.05, 0.01);
                    world.spawnParticle(Particle.BUBBLE_POP, loc, 1, 0.02, 0.02, 0.02, 0.01);
                }
            }
            case "beginner_trident_2" -> {
                if (ticks % 3 == 0) {
                    world.spawnParticle(Particle.BUBBLE, loc, 3, 0.07, 0.07, 0.07, 0.01);
                    world.spawnParticle(Particle.SPLASH, loc, 1, 0.02, 0.02, 0.02, 0.01);
                }
            }
            case "trident_1" -> {
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.DUST, loc, 2, 0.05, 0.05, 0.05, 0.0, PRISMARINE_DUST);
                    world.spawnParticle(Particle.BUBBLE_POP, loc, 1, 0.02, 0.02, 0.02, 0.01);
                }
            }
            case "trident_2" -> {
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.DUST, loc, 2, 0.05, 0.05, 0.05, 0.0, TIDE_DUST);
                    world.spawnParticle(Particle.NAUTILUS, loc, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }
            case "trident_3" -> {
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.DUST, loc, 2, 0.05, 0.05, 0.05, 0.0, DREAD_DUST);
                    world.spawnParticle(Particle.PORTAL, loc, 2, 0.08, 0.08, 0.08, 0.02);
                    world.spawnParticle(Particle.END_ROD, loc, 1, 0.05, 0.05, 0.05, 0.01);
                }
                if (ticks % 3 == 0) {
                    world.spawnParticle(Particle.NAUTILUS, loc, 1, 0.06, 0.06, 0.06, 0.01);
                }
            }
            case "poseidons_trident" -> {
                world.spawnParticle(Particle.DUST, loc, 2, 0.05, 0.05, 0.05, 0.0, POSEIDON_DUST);
                world.spawnParticle(Particle.END_ROD, loc, 1, 0.05, 0.05, 0.05, 0.01);
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 2, 0.05, 0.05, 0.05, 0.02);
                    world.spawnParticle(Particle.NAUTILUS, loc, 2, 0.05, 0.05, 0.05, 0.01);
                }
            }
            case "hephaestean_trident" -> {
                world.spawnParticle(Particle.DUST, loc, 2, 0.06, 0.06, 0.06, 0.0, FORGE_DUST);
                world.spawnParticle(Particle.FLAME, loc, 2, 0.05, 0.05, 0.05, 0.02);
                world.spawnParticle(Particle.END_ROD, loc, 2, 0.05, 0.05, 0.05, 0.01);
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 3, 0.05, 0.05, 0.05, 0.02);
                    world.spawnParticle(Particle.NAUTILUS, loc, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }
            default -> {
            }
        }
    }

    private void startEncapsulateImplode(Player shooter, LivingEntity target,
            Particle.DustOptions dust, Particle shellParticle,
            Particle accentParticle, Particle coreParticle, double damage) {
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!target.isValid() || target.isDead()) {
                    cancel();
                    return;
                }

                Location base = target.getLocation();
                World world = base.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }

                double height = Math.max(1.4, target.getHeight() + ENCAPSULATE_HEIGHT_PADDING);
                Location center = base.clone().add(0, height * 0.5, 0);

                if (step == 0) {
                    spawnEncapsulateBurst(world, center, height, dust, shellParticle, accentParticle);
                }

                if (step < ENCAPSULATE_SHELL_STEPS) {
                    spawnEncapsulateShell(world, center, height, ENCAPSULATE_RADIUS,
                        5, dust, shellParticle, accentParticle);
                } else if (step < ENCAPSULATE_SHELL_STEPS + ENCAPSULATE_IMPLODE_STEPS) {
                    double progress = (step - ENCAPSULATE_SHELL_STEPS) / (double) ENCAPSULATE_IMPLODE_STEPS;
                    double radius = ENCAPSULATE_RADIUS * (1.0 - progress);
                    spawnEncapsulateShell(world, center, height, radius,
                        6, dust, shellParticle, accentParticle);
                } else {
                    spawnEncapsulateCore(world, center, dust, accentParticle, coreParticle);
                    if (damage > 0.0 && target.isValid() && !target.isDead()) {
                        target.damage(damage, shooter);
                    }
                    cancel();
                    return;
                }

                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnEncapsulateBurst(World world, Location center, double height,
            Particle.DustOptions dust, Particle shellParticle, Particle accentParticle) {
        double radius = ENCAPSULATE_RADIUS;
        world.spawnParticle(shellParticle, center, 12, radius, height * 0.5, radius, 0.02);
        world.spawnParticle(accentParticle, center, 6, radius * 0.8, height * 0.4, radius * 0.8, 0.02);
        world.spawnParticle(Particle.DUST, center, 12, radius, height * 0.5, radius, 0.0, dust);
    }

    private void spawnEncapsulateShell(World world, Location center, double height, double radius,
            int count, Particle.DustOptions dust, Particle shellParticle, Particle accentParticle) {
        for (int i = 0; i < count; i++) {
            double yOffset = (Math.random() - 0.5) * height;
            double ringRadius = radius * Math.cos((yOffset / height) * (Math.PI / 2));
            double angle = Math.random() * (Math.PI * 2);
            double x = center.getX() + Math.cos(angle) * ringRadius;
            double y = center.getY() + yOffset;
            double z = center.getZ() + Math.sin(angle) * ringRadius;

            world.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
            world.spawnParticle(shellParticle, x, y, z, 1, 0, 0, 0, 0.01);
            if (Math.random() < 0.25) {
                world.spawnParticle(accentParticle, x, y, z, 1, 0, 0, 0, 0.02);
            }
        }
    }

    private void spawnEncapsulateCore(World world, Location center, Particle.DustOptions dust,
            Particle accentParticle, Particle coreParticle) {
        world.spawnParticle(Particle.DUST, center, 18, 0.2, 0.2, 0.2, 0.0, dust);
        world.spawnParticle(accentParticle, center, 10, 0.15, 0.15, 0.15, 0.02);
        world.spawnParticle(coreParticle, center, 12, 0.1, 0.1, 0.1, 0.02);
    }

    private void startDreadspearVortex(Player shooter, LivingEntity target) {
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!target.isValid() || target.isDead()) {
                    cancel();
                    return;
                }

                Location base = target.getLocation();
                World world = base.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }

                double height = Math.max(1.2, target.getHeight() + 0.2);
                double angle = step * 0.6;
                double radius = DREADSPEAR_VORTEX_RADIUS + Math.sin(step * 0.35) * 0.2;
                double y = base.getY() + (height * 0.5) + Math.sin(step * 0.4) * 0.35;

                double x = base.getX() + Math.cos(angle) * radius;
                double z = base.getZ() + Math.sin(angle) * radius;

                world.spawnParticle(Particle.DUST, x, y, z, 3, 0.06, 0.06, 0.06, 0.0, DREAD_DUST);
                world.spawnParticle(Particle.PORTAL, x, y, z, 2, 0.08, 0.08, 0.08, 0.02);
                if (step % 3 == 0) {
                    world.spawnParticle(Particle.END_ROD, x, y, z, 2, 0.04, 0.04, 0.04, 0.01);
                }

                step++;
                if (step >= DREADSPEAR_VORTEX_STEPS) {
                    world.spawnParticle(Particle.PORTAL, base, 26, 0.6, 0.8, 0.6, 0.04);
                    world.spawnParticle(Particle.DUST, base, 20, 0.6, 0.8, 0.6, 0.0, DREAD_DUST);
                    if (DREADSPEAR_HIT_DAMAGE > 0.0 && target.isValid() && !target.isDead()) {
                        target.damage(DREADSPEAR_HIT_DAMAGE, shooter);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void startTidesplitterSpiral(Player shooter, LivingEntity target) {
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!target.isValid() || target.isDead()) {
                    cancel();
                    return;
                }

                if (step >= TIDESPLITTER_SPIRAL_STEPS) {
                    target.damage(TIDESPLITTER_SPIRAL_DAMAGE, shooter);
                    cancel();
                    return;
                }

                Location base = target.getLocation();
                World world = base.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }

                double progress = step / (double) TIDESPLITTER_SPIRAL_STEPS;
                double radius = TIDESPLITTER_SPIRAL_RADIUS_MIN
                        + (TIDESPLITTER_SPIRAL_RADIUS_MAX - TIDESPLITTER_SPIRAL_RADIUS_MIN) * progress;
                double angle = step * 0.5;
                double y = base.getY() + progress * TIDESPLITTER_SPIRAL_HEIGHT;

                double px = base.getX() + Math.cos(angle) * radius;
                double pz = base.getZ() + Math.sin(angle) * radius;

                world.spawnParticle(Particle.DUST, px, y, pz, 1, 0, 0, 0, 0, TIDE_DUST);
                if (step % 4 == 0) {
                    world.spawnParticle(Particle.NAUTILUS, px, y, pz, 1, 0, 0, 0, 0.01);
                }

                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}

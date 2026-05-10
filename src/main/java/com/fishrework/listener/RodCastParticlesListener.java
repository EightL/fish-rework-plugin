package com.fishrework.listener;

import com.fishrework.FishRework;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class RodCastParticlesListener implements Listener {

    private static final Particle.DustOptions HARMONY_DUST =
            new Particle.DustOptions(Color.fromRGB(120, 220, 190), 1.0f);
    private static final Particle.DustOptions NEPTUNE_DUST =
            new Particle.DustOptions(Color.fromRGB(90, 160, 255), 1.1f);

    private final FishRework plugin;

    public RodCastParticlesListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onHookLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof FishHook hook)) return;
        if (!(hook.getShooter() instanceof Player player)) return;

        ItemStack rod = getRodInHands(player);
        String customId = getCustomItemId(rod);
        if (!isTrackedRod(customId)) return;

        int maxTicks = getTrailDuration(customId);
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!hook.isValid() || hook.isDead()) {
                    cancel();
                    return;
                }
                ticks++;
                if (ticks > maxTicks) {
                    cancel();
                    return;
                }

                Location loc = hook.getLocation();
                World world = loc.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }

                spawnRodCastParticles(world, loc, customId, ticks);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private ItemStack getRodInHands(Player player) {
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod.getType().name().equals("FISHING_ROD") && rod.hasItemMeta()) {
            return rod;
        }
        rod = player.getInventory().getItemInOffHand();
        if (rod.getType().name().equals("FISHING_ROD") && rod.hasItemMeta()) {
            return rod;
        }
        return null;
    }

    private String getCustomItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getItemManager().CUSTOM_ITEM_KEY, PersistentDataType.STRING);
    }

    private boolean isTrackedRod(String customId) {
        if (customId == null || customId.isBlank()) return false;
        return switch (customId) {
            case "harmony_rod",
                    "neptunes_rod",
                    "shredder",
                    "leviathan",
                    "ember_rod",
                    "inferno_rod",
                    "hellfire_rod",
                    "volcanic_rod" -> true;
            default -> false;
        };
    }

    private int getTrailDuration(String customId) {
        if (customId == null) return 8;
        return switch (customId) {
            case "harmony_rod" -> 8;
            case "neptunes_rod" -> 9;
            case "shredder" -> 10;
            case "leviathan" -> 12;
            case "ember_rod" -> 9;
            case "inferno_rod" -> 10;
            case "hellfire_rod" -> 11;
            case "volcanic_rod" -> 12;
            default -> 8;
        };
    }

    private void spawnRodCastParticles(World world, Location loc, String customId, int ticks) {
        if (customId == null) return;
        switch (customId) {
            case "harmony_rod" -> {
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.DUST, loc, 2, 0.06, 0.06, 0.06, 0.0, HARMONY_DUST);
                    world.spawnParticle(Particle.BUBBLE_POP, loc, 1, 0.02, 0.02, 0.02, 0.01);
                }
            }
            case "neptunes_rod" -> {
                world.spawnParticle(Particle.DUST, loc, 2, 0.06, 0.06, 0.06, 0.0, NEPTUNE_DUST);
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.NAUTILUS, loc, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }
            case "shredder" -> {
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.CRIT, loc, 3, 0.08, 0.08, 0.08, 0.01);
                    world.spawnParticle(Particle.SPLASH, loc, 2, 0.07, 0.07, 0.07, 0.02);
                }
            }
            case "leviathan" -> {
                world.spawnParticle(Particle.END_ROD, loc, 1, 0.05, 0.05, 0.05, 0.01);
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.NAUTILUS, loc, 1, 0.06, 0.06, 0.06, 0.01);
                    world.spawnParticle(Particle.BUBBLE, loc, 3, 0.08, 0.08, 0.08, 0.01);
                }
            }
            case "ember_rod" -> {
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.FLAME, loc, 2, 0.05, 0.05, 0.05, 0.01);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 1, 0.05, 0.05, 0.05, 0.02);
                }
            }
            case "inferno_rod" -> {
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.FLAME, loc, 3, 0.06, 0.06, 0.06, 0.01);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 2, 0.05, 0.05, 0.05, 0.02);
                    world.spawnParticle(Particle.END_ROD, loc, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }
            case "hellfire_rod" -> {
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.FLAME, loc, 2, 0.06, 0.06, 0.06, 0.01);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 2, 0.05, 0.05, 0.05, 0.02);
                    world.spawnParticle(Particle.END_ROD, loc, 1, 0.05, 0.05, 0.05, 0.01);
                }
            }
            case "volcanic_rod" -> {
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.FLAME, loc, 3, 0.06, 0.06, 0.06, 0.01);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 3, 0.05, 0.05, 0.05, 0.02);
                    world.spawnParticle(Particle.END_ROD, loc, 2, 0.05, 0.05, 0.05, 0.01);
                }
            }
            default -> {
            }
        }
    }
}

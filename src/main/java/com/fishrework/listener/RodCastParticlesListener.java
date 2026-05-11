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
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class RodCastParticlesListener implements Listener {

    private static final Particle.DustOptions HARMONY_DUST =
            new Particle.DustOptions(Color.fromRGB(120, 220, 190), 1.0f);
    private static final Particle.DustOptions NEPTUNE_DUST =
            new Particle.DustOptions(Color.fromRGB(90, 160, 255), 1.1f);

    private final FishRework plugin;
    private final Map<UUID, TrailState> activeTrails = new HashMap<>();
    private BukkitTask trailTask;

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
        activeTrails.put(hook.getUniqueId(), new TrailState(hook, customId, maxTicks));
        ensureTrailTask();
    }

    private void ensureTrailTask() {
        if (trailTask != null) {
            return;
        }

        trailTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (activeTrails.isEmpty()) {
                    stopTrailTask();
                    return;
                }

                Iterator<Map.Entry<UUID, TrailState>> iterator = activeTrails.entrySet().iterator();
                while (iterator.hasNext()) {
                    TrailState state = iterator.next().getValue();
                    FishHook hook = state.hook;
                    if (hook == null || hook.isDead() || !hook.isValid()) {
                        iterator.remove();
                        continue;
                    }

                    state.ticks++;
                    if (state.ticks > state.maxTicks) {
                        iterator.remove();
                        continue;
                    }

                    Location loc = hook.getLocation();
                    World world = loc.getWorld();
                    if (world == null) {
                        iterator.remove();
                        continue;
                    }

                    spawnRodCastParticles(world, loc, state.customId, state.ticks);
                }

                if (activeTrails.isEmpty()) {
                    stopTrailTask();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void stopTrailTask() {
        if (trailTask != null) {
            trailTask.cancel();
            trailTask = null;
        }
    }

    private static final class TrailState {
        private final FishHook hook;
        private final String customId;
        private final int maxTicks;
        private int ticks = 0;

        private TrailState(FishHook hook, String customId, int maxTicks) {
            this.hook = hook;
            this.customId = customId;
            this.maxTicks = maxTicks;
        }
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

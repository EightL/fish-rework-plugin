package com.fishrework.task;

import com.fishrework.FishRework;
import com.fishrework.util.ParticleDetailScaler;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives subtle movement trails for selected armor full sets.
 * Runs frequently enough to look smooth, but keeps particle counts very low.
 */
public class ArmorSetTrailTask implements Runnable {

        private static final double PARTICLE_VIEW_DISTANCE = 64.0;
    private static final double PARTICLE_VIEW_DISTANCE_SQ = PARTICLE_VIEW_DISTANCE * PARTICLE_VIEW_DISTANCE;
        private static final double MIN_HORIZONTAL_SPEED_SQ = 0.0036;

    private static final Particle.DustOptions DREAD_DUST_MAIN =
            new Particle.DustOptions(org.bukkit.Color.fromRGB(120, 12, 24), 1.25f);
    private static final Particle.DustOptions DREAD_DUST_ACCENT =
            new Particle.DustOptions(org.bukkit.Color.fromRGB(70, 8, 18), 1.05f);
    private static final Particle.DustOptions VOLCANIC_DUST =
            new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 105, 40), 1.1f);

    private final FishRework plugin;
    private final NamespacedKey customItemKey;
    private final NamespacedKey dreadplateArmorKey;
    private final NamespacedKey volcanicDreadplateArmorKey;

    public ArmorSetTrailTask(FishRework plugin) {
        this.plugin = plugin;
        this.customItemKey = plugin.getItemManager().CUSTOM_ITEM_KEY;
        this.dreadplateArmorKey = plugin.getItemManager().DREADPLATE_ARMOR_KEY;
        this.volcanicDreadplateArmorKey = new NamespacedKey(plugin, "volcanic_dreadplate_armor");
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player == null || !player.isValid() || player.isDead()) continue;

            TrailType trailType = resolveTrailType(player);
            if (trailType == null) continue;

            Vector horizontalVelocity = player.getVelocity().clone().setY(0.0);
            if (horizontalVelocity.lengthSquared() < MIN_HORIZONTAL_SPEED_SQ) continue;

            emitTrail(player, horizontalVelocity, trailType);
        }
    }

    private TrailType resolveTrailType(Player player) {
        if (isWearingFullSet(player, volcanicDreadplateArmorKey, "volcanic_dreadplate_")) {
            return TrailType.VOLCANIC_DREADPLATE;
        }
        if (isWearingFullSet(player, dreadplateArmorKey, "dreadplate_")) {
            return TrailType.DREADPLATE;
        }
        return null;
    }

    private boolean isWearingFullSet(Player player, NamespacedKey setKey, String customIdPrefix) {
        return hasSetIdentity(player.getInventory().getHelmet(), setKey, customIdPrefix)
                && hasSetIdentity(player.getInventory().getChestplate(), setKey, customIdPrefix)
                && hasSetIdentity(player.getInventory().getLeggings(), setKey, customIdPrefix)
                && hasSetIdentity(player.getInventory().getBoots(), setKey, customIdPrefix);
    }

    private boolean hasSetIdentity(ItemStack item, NamespacedKey setKey, String customIdPrefix) {
        if (item == null || !item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(setKey, PersistentDataType.BYTE)) return true;

        String customId = pdc.get(customItemKey, PersistentDataType.STRING);
        return customId != null && customId.startsWith(customIdPrefix);
    }

    private void emitTrail(Player player, Vector horizontalVelocity, TrailType trailType) {
        Location base = player.getLocation().add(0.0, 0.85, 0.0);
        Vector behind = horizontalVelocity.normalize().multiply(-0.52);
        Location trailOrigin = base.add(behind);
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        switch (trailType) {
            case DREADPLATE -> {
                Location puff = jitter(trailOrigin, rng, 0.10, 0.07, 0.10);
                spawnParticle(player.getWorld(), Particle.DUST, puff, 2, 0.04, 0.04, 0.04, 0.0, DREAD_DUST_MAIN);

                if (rng.nextDouble() < 0.35) {
                    spawnParticle(player.getWorld(), Particle.DUST, jitter(trailOrigin, rng, 0.10, 0.07, 0.10),
                            1, 0.03, 0.03, 0.03, 0.0, DREAD_DUST_ACCENT);
                }

                if (rng.nextDouble() < 0.2) {
                    spawnParticle(player.getWorld(), Particle.SMOKE, jitter(trailOrigin, rng, 0.08, 0.06, 0.08),
                            1, 0.01, 0.01, 0.01, 0.0);
                }
            }
            case VOLCANIC_DREADPLATE -> {
                Location ember = jitter(trailOrigin, rng, 0.10, 0.07, 0.10);
                spawnParticle(player.getWorld(), Particle.FLAME, ember, 2, 0.04, 0.04, 0.04, 0.0);

                if (rng.nextDouble() < 0.4) {
                    spawnParticle(player.getWorld(), Particle.DUST, jitter(trailOrigin, rng, 0.10, 0.07, 0.10),
                            1, 0.03, 0.03, 0.03, 0.0, VOLCANIC_DUST);
                }

                if (rng.nextDouble() < 0.18) {
                    spawnParticle(player.getWorld(), Particle.SOUL_FIRE_FLAME,
                            jitter(trailOrigin, rng, 0.08, 0.06, 0.08), 1, 0.01, 0.01, 0.01, 0.0);
                }
                if (rng.nextDouble() < 0.08) {
                    spawnParticle(player.getWorld(), Particle.LAVA,
                            jitter(trailOrigin, rng, 0.07, 0.05, 0.07), 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    private Location jitter(Location origin, ThreadLocalRandom rng, double xzSpread, double ySpread, double zSpread) {
        return origin.clone().add(
                rng.nextDouble(-xzSpread, xzSpread),
                rng.nextDouble(-ySpread, ySpread),
                rng.nextDouble(-zSpread, zSpread)
        );
    }

    private void spawnParticle(World world, Particle particle, Location location, int count, Object... extras) {
        if (world == null || location == null || count <= 0) return;

        for (Player viewer : world.getPlayers()) {
            if (viewer == null || !viewer.isValid() || viewer.isDead()) continue;
            if (viewer.getLocation().distanceSquared(location) > PARTICLE_VIEW_DISTANCE_SQ) continue;

            int scaledCount = ParticleDetailScaler.getScaledCount(plugin, viewer, count);
            if (scaledCount <= 0) continue;

            spawnParticleForViewer(viewer, particle, location, scaledCount, extras);
        }
    }

    private void spawnParticleForViewer(Player viewer, Particle particle, Location location, int count, Object[] extras) {
        int extraLen = extras == null ? 0 : extras.length;
        if (extraLen == 0) {
            viewer.spawnParticle(particle, location, count);
            return;
        }
        if (extraLen == 1) {
            viewer.spawnParticle(particle, location, count, extras[0]);
            return;
        }
        if (extraLen == 4 && extras[0] instanceof Number n0 && extras[1] instanceof Number n1 && extras[2] instanceof Number n2) {
            double ox = n0.doubleValue();
            double oy = n1.doubleValue();
            double oz = n2.doubleValue();
            if (extras[3] instanceof Number n3) {
                viewer.spawnParticle(particle, location, count, ox, oy, oz, n3.doubleValue());
            } else {
                viewer.spawnParticle(particle, location, count, ox, oy, oz, extras[3]);
            }
            return;
        }
        if (extraLen == 5 && extras[0] instanceof Number n0 && extras[1] instanceof Number n1 && extras[2] instanceof Number n2) {
            double ox = n0.doubleValue();
            double oy = n1.doubleValue();
            double oz = n2.doubleValue();
            if (extras[3] instanceof Number n3) {
                viewer.spawnParticle(particle, location, count, ox, oy, oz, n3.doubleValue(), extras[4]);
            } else {
                viewer.spawnParticle(particle, location, count, ox, oy, oz, extras[3]);
            }
            return;
        }

        viewer.spawnParticle(particle, location, count, extras[0]);
    }

    private enum TrailType {
        DREADPLATE,
        VOLCANIC_DREADPLATE
    }
}
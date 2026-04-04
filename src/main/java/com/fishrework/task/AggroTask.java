package com.fishrework.task;

import com.fishrework.FishRework;
import com.fishrework.MobManager;
import com.fishrework.util.ParticleDetailScaler;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs every 5 game ticks (4× per second).
 * Handles two responsibilities:
 *   1. Re-targets natively-hostile fished mobs that lost their target.
 *   2. Drives passive-aggro AI (velocity chase + contact damage) for
 *      normally-passive mobs tagged with AGGRO_SPEED_KEY.
 */
public class AggroTask implements Runnable {

    private static final int TICKS_PER_RUN = 5; // must match scheduler period in FishRework
    private static final double PARTICLE_VIEW_DISTANCE = 64.0;
    private static final double PARTICLE_VIEW_DISTANCE_SQ = PARTICLE_VIEW_DISTANCE * PARTICLE_VIEW_DISTANCE;
    private static final Particle.DustOptions BITE_RED = new Particle.DustOptions(Color.fromRGB(190, 25, 25), 1.1f);
    private static final Particle.DustOptions WAILING_AURA_RED = new Particle.DustOptions(Color.fromRGB(255, 62, 62), 2.2f);
    private static final Particle.DustOptions WAILING_AURA_CRIMSON = new Particle.DustOptions(Color.fromRGB(150, 24, 32), 2.0f);
    private static final Particle.DustOptions WAILING_AURA_GREEN = new Particle.DustOptions(Color.fromRGB(102, 255, 125), 2.0f);
    private static final Particle.DustOptions WAILING_AURA_DEEP = new Particle.DustOptions(Color.fromRGB(40, 155, 70), 1.8f);

    private final FishRework plugin;
    private final NamespacedKey wailingRoleKey;

    /** Tracks per-entity hit cooldowns: entity UUID → game tick of last hit */
    private final Map<UUID, Long> lastHitTick = new HashMap<>();
    private long gameTick = 0;

    public AggroTask(FishRework plugin) {
        this.plugin = plugin;
        this.wailingRoleKey = new NamespacedKey(plugin, "wailing_ghast_role");
    }

    @Override
    public void run() {
        gameTick += TICKS_PER_RUN;
        MobManager mobManager = plugin.getMobManager();
        plugin.getBossBarManager().cleanupMobBossBars();
        
        // Optimized iteration over active fished mobs only
        for (UUID uuid : mobManager.getActiveFishedMobs()) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(uuid);
            // If null (unloaded) or not valid (dead/removed), skip.
            // We rely on MobListener (EntityDeath/Remove) to clean up the set, 
            // so we don't remove here to avoid removing unloaded entities.
            if (entity == null || !entity.isValid()) continue;

            if (!(entity instanceof Mob mob)) continue;

            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            // Double-check tag to be safe (though set membership should guarantee it)
            if (!pdc.has(mobManager.FISHED_MOB_KEY, PersistentDataType.BYTE)) continue;

            String mobId = pdc.get(mobManager.MOB_ID_KEY, PersistentDataType.STRING);
            if (mobId == null) continue;

                plugin.getBossBarManager().updateMobBossBar(mob, mobId);

            boolean hasPassiveAggro = pdc.has(mobManager.AGGRO_SPEED_KEY, PersistentDataType.DOUBLE)
                    && pdc.has(mobManager.AGGRO_DAMAGE_KEY, PersistentDataType.DOUBLE)
                    && pdc.has(mobManager.AGGRO_RANGE_KEY, PersistentDataType.DOUBLE)
                    && pdc.has(mobManager.AGGRO_HIT_INTERVAL_KEY, PersistentDataType.INTEGER);
            if (!mobManager.isHostile(mobId) && !hasPassiveAggro) continue;

            // ── Particle effects ──
            if (pdc.has(mobManager.PARTICLE_TYPE_KEY, PersistentDataType.STRING)) {
                spawnParticles(mob, pdc.get(mobManager.PARTICLE_TYPE_KEY, PersistentDataType.STRING));
            }

            // ── Passive-aggro AI ──
            if (hasPassiveAggro) {
                handlePassiveAggro(mob, pdc, mobManager);
                continue;
            }

            // ── Default hostile re-targeting ──
            if (mob.getTarget() instanceof Player tp 
                    && tp.isValid() 
                    && (tp.getGameMode() == GameMode.SURVIVAL || tp.getGameMode() == GameMode.ADVENTURE)) {
                keepSpecialGhastsClose(mob, mobId, tp);
                continue;
            }
            Player nearest = findNearestPlayer(mob, plugin.getConfig().getDouble("mob_balance.hostile_retarget_range", 24.0));
            if (nearest != null) {
                mob.setTarget(nearest);
                keepSpecialGhastsClose(mob, mobId, nearest);
            }
        }

        // Purge stale cooldown entries periodically
        if (gameTick % 200 == 0) {
            lastHitTick.entrySet().removeIf(e -> Bukkit.getEntity(e.getKey()) == null);
        }
    }

    /**
     * Drives chase + contact damage for a normally-passive mob.
     * Uses direct velocity manipulation so it works for ALL entity types
     * (dolphins, glow squids, mooshrooms, camels, etc.) regardless of
     * their internal pathfinder/navigation type.
     */
    private void handlePassiveAggro(Mob mob, PersistentDataContainer pdc, MobManager mobManager) {
        Double speed = pdc.get(mobManager.AGGRO_SPEED_KEY, PersistentDataType.DOUBLE);
        Double damage = pdc.get(mobManager.AGGRO_DAMAGE_KEY, PersistentDataType.DOUBLE);
        Double range = pdc.get(mobManager.AGGRO_RANGE_KEY, PersistentDataType.DOUBLE);
        Integer hitInterval = pdc.get(mobManager.AGGRO_HIT_INTERVAL_KEY, PersistentDataType.INTEGER);

        if (speed == null || damage == null || range == null || hitInterval == null) return;

        Player nearest = findNearestPlayer(mob, plugin.getConfig().getDouble("mob_balance.passive_aggro_search_range", 32.0));
        if (nearest == null) return;

        Location mobLoc = mob.getLocation();
        Location targetLoc = nearest.getLocation();
        double distSq = mobLoc.distanceSquared(targetLoc);

        // ── Chase: direct velocity toward player ──
        // Try pathfinder first (works for land mobs); always apply velocity
        // as a guaranteed fallback that works for water/passive mobs too.
        try {
            mob.getPathfinder().moveTo(nearest, speed);
        } catch (Exception ignored) {}

        // Velocity-based pursuit — works universally
        if (distSq > range * range) {
            Vector dir = targetLoc.toVector().subtract(mobLoc.toVector());
            if (dir.lengthSquared() > 0) {
                // Scale speed: 0.3 blocks/tick base × speed multiplier, capped
                double velocityMagnitude = Math.min(speed * plugin.getConfig().getDouble("mob_balance.passive_aggro_velocity_mult", 0.15), 
                        plugin.getConfig().getDouble("mob_balance.passive_aggro_max_velocity", 1.5));
                Vector velocity = dir.normalize().multiply(velocityMagnitude);
                // Preserve existing Y velocity if mob is in water/falling
                velocity.setY(mob.getVelocity().getY() + velocity.getY() * plugin.getConfig().getDouble("mob_balance.passive_aggro_y_velocity_factor", 0.3));
                mob.setVelocity(velocity);
            }
        }

        // Also make the mob look at the player
        mob.lookAt(nearest);

        if (shouldSuppressPassiveAggroDamage(mob, pdc, mobManager)) {
            return;
        }

        // ── Contact damage ──
        if (distSq <= range * range) {
            if (nearest.getGameMode() == GameMode.SURVIVAL || nearest.getGameMode() == GameMode.ADVENTURE) {
                UUID entityId = mob.getUniqueId();
                Long lastHit = lastHitTick.get(entityId);
                if (lastHit == null || (gameTick - lastHit) >= hitInterval) {
                    nearest.damage(damage, mob);
                    spawnBiteParticles(nearest);
                    lastHitTick.put(entityId, gameTick);
                }
            }
        }
    }

    private boolean shouldSuppressPassiveAggroDamage(Mob mob, PersistentDataContainer pdc, MobManager mobManager) {
        String mobId = pdc.get(mobManager.MOB_ID_KEY, PersistentDataType.STRING);
        if (!"poseidon".equals(mobId)) return false;
        return isBelowHalfHealth(mob);
    }

    private boolean isBelowHalfHealth(LivingEntity entity) {
        org.bukkit.attribute.AttributeInstance maxHealth = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (maxHealth == null) return false;
        double max = maxHealth.getValue();
        if (max <= 0.0) return false;
        return entity.getHealth() <= (max * 0.5);
    }

    private void spawnBiteParticles(Player player) {
        Location loc = player.getLocation().add(0, player.getHeight() * 0.5, 0);
        spawnParticle(player.getWorld(), Particle.DUST, loc, 10, 0.25, 0.35, 0.25, 0.0, BITE_RED);
        spawnParticle(player.getWorld(), Particle.DAMAGE_INDICATOR, loc, 4, 0.2, 0.3, 0.2, 0.0);
    }

    private Player findNearestPlayer(Mob mob, double range) {
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;
        double rangeSq = range * range;

        for (Player p : mob.getWorld().getPlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
            double dist = p.getLocation().distanceSquared(mob.getLocation());
            if (dist < rangeSq && dist < nearestDist) {
                nearest = p;
                nearestDist = dist;
            }
        }
        return nearest;
    }

    private void keepSpecialGhastsClose(Mob mob, String mobId, Player anchor) {
        if (anchor == null) return;
        if (!mob.getWorld().equals(anchor.getWorld())) return;

        if (!"ghast_broodmother".equals(mobId) && !"wailing_ghast_duo".equals(mobId)) return;

        Location mobLoc = mob.getLocation();
        Location anchorLoc = anchor.getLocation().clone().add(0, anchor.getHeight() * 0.65, 0);

        double softDistance = "wailing_ghast_duo".equals(mobId) ? 16.0 : 20.0;
        double hardDistance = "wailing_ghast_duo".equals(mobId) ? 28.0 : 34.0;
        double distSq = mobLoc.distanceSquared(anchorLoc);
        Vector pull = new Vector(0, 0, 0);

        if (distSq > softDistance * softDistance) {
            Vector toAnchor = anchorLoc.toVector().subtract(mobLoc.toVector());
            if (toAnchor.lengthSquared() > 0.0001) {
                double pullSpeed = distSq > hardDistance * hardDistance ? 1.3 : 0.8;
                pull.add(toAnchor.normalize().multiply(pullSpeed));
            }
        }

        if ("wailing_ghast_duo".equals(mobId)) {
            LivingEntity twin = findNearestWailingTwin(mob);
            if (twin != null && twin.isValid() && !twin.isDead()) {
                double twinDistSq = twin.getLocation().distanceSquared(mobLoc);
                double twinSoft = 10.0;
                if (twinDistSq > twinSoft * twinSoft) {
                    Vector toTwin = twin.getLocation().toVector().subtract(mobLoc.toVector());
                    if (toTwin.lengthSquared() > 0.0001) {
                        pull.add(toTwin.normalize().multiply(0.55));
                    }
                }
            }
        }

        if (pull.lengthSquared() <= 0.0001) return;

        if (pull.length() > 1.4) {
            pull.normalize().multiply(1.4);
        }
        pull.setY(Math.max(-0.24, Math.min(0.24, pull.getY())));
        mob.setVelocity(pull);
        mob.lookAt(anchor);
    }

    private LivingEntity findNearestWailingTwin(Mob mob) {
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (org.bukkit.entity.Entity nearby : mob.getWorld().getNearbyEntities(mob.getLocation(), 22.0, 14.0, 22.0)) {
            if (!(nearby instanceof LivingEntity living)) continue;
            if (living.getUniqueId().equals(mob.getUniqueId())) continue;
            if (!plugin.getMobManager().isFishedMob(living)) continue;
            if (!"wailing_ghast_duo".equals(plugin.getMobManager().getMobId(living))) continue;

            double dist = living.getLocation().distanceSquared(mob.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = living;
            }
        }
        return nearest;
    }

    /**
     * Spawns ambient particle effects around a mob based on its particle type tag.
     * Called every 5 ticks for tagged mobs.
     */
    private void spawnParticles(LivingEntity entity, String particleType) {
        if (particleType == null || entity.isDead()) return;
        Location loc = entity.getLocation().add(0, entity.getHeight() / 2.0, 0);
        World world = entity.getWorld();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        switch (particleType) {
            case "potion" -> {
                // Purple/green swirling potion particles around the witch
                for (int i = 0; i < 3; i++) {
                    double ox = rng.nextDouble(-0.6, 0.6);
                    double oy = rng.nextDouble(-0.3, 0.8);
                    double oz = rng.nextDouble(-0.6, 0.6);
                    spawnParticle(world, Particle.WITCH, loc.clone().add(ox, oy, oz), 1, 0, 0, 0, 0);
                }
            }
            case "gore" -> {
                // Red/dark blood particles for the megalodon
                Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(139, 0, 0), 1.5f);
                for (int i = 0; i < 4; i++) {
                    double ox = rng.nextDouble(-1.5, 1.5);
                    double oy = rng.nextDouble(-0.5, 1.0);
                    double oz = rng.nextDouble(-1.5, 1.5);
                    spawnParticle(world, Particle.DUST, loc.clone().add(ox, oy, oz), 1, redDust);
                }
                // Extra drip particles
                if (rng.nextInt(3) == 0) {
                    spawnParticle(world, Particle.DAMAGE_INDICATOR, loc.clone().add(
                        rng.nextDouble(-1.0, 1.0), rng.nextDouble(-0.5, 0.5), rng.nextDouble(-1.0, 1.0)), 1, 0, 0, 0, 0);
                }
            }
            case "blood_light" -> {
                // Light red particles for angry polar bear
                Particle.DustOptions lightRed = new Particle.DustOptions(Color.fromRGB(200, 50, 50), 1.0f);
                for (int i = 0; i < 2; i++) {
                    double ox = rng.nextDouble(-0.5, 0.5);
                    double oy = rng.nextDouble(-0.2, 0.6);
                    double oz = rng.nextDouble(-0.5, 0.5);
                    spawnParticle(world, Particle.DUST, loc.clone().add(ox, oy, oz), 1, lightRed);
                }
            }
            case "crimson_ghast" -> {
                if ("wailing_ghast_duo".equals(plugin.getMobManager().getMobId(entity))) {
                    String role = entity.getPersistentDataContainer().get(wailingRoleKey, PersistentDataType.STRING);
                    boolean toxic = "TOXIC".equals(role);

                    Particle.DustOptions main = toxic ? WAILING_AURA_GREEN : WAILING_AURA_RED;
                    Particle.DustOptions alt = toxic ? WAILING_AURA_DEEP : WAILING_AURA_CRIMSON;
                    int streams = toxic ? 8 : 7;
                    double t = System.currentTimeMillis() / (toxic ? 160.0 : 180.0);

                    for (int i = 0; i < streams; i++) {
                        double angle = t + (Math.PI * 2.0 * i / streams);
                        double radius = 2.9 + (toxic ? 0.4 : 0.55) * Math.sin(t * 0.9 + i);

                        for (int seg = 0; seg < 8; seg++) {
                            double segAngle = angle + (seg * 0.32);
                            double segRadius = radius + (seg * 0.24);
                            double x = Math.cos(segAngle) * segRadius;
                            double z = Math.sin(segAngle) * segRadius;
                            double y = -1.25 + (seg * 0.36) + (Math.sin(segAngle * 1.7) * 0.12);
                            Location p = loc.clone().add(x, y, z);

                            spawnParticle(world, Particle.DUST, p, 2, main);
                            spawnParticle(world, Particle.DUST, p, 1, alt);
                            if (toxic && seg % 2 == 0) {
                                spawnParticle(world, Particle.SNEEZE, p, 1, 0.02, 0.02, 0.02, 0.0);
                            }
                        }
                    }

                    if (toxic) {
                        spawnParticle(world, Particle.SNEEZE, loc.clone().add(0, 0.25, 0), 8, 0.45, 0.3, 0.45, 0.01);
                    } else {
                        spawnParticle(world, Particle.SQUID_INK, loc.clone().add(0, 0.35, 0), 6, 0.4, 0.28, 0.4, 0.01);
                    }
                    break;
                }

                // Orbiting blood tentacles around the ghast body.
                Particle.DustOptions blood = new Particle.DustOptions(Color.fromRGB(210, 32, 44), 2.7f);
                Particle.DustOptions bloodDark = new Particle.DustOptions(Color.fromRGB(78, 8, 16), 2.35f);
                double t = System.currentTimeMillis() / 170.0;
                int tentacles = 10;

                for (int i = 0; i < tentacles; i++) {
                    double baseAngle = t + (Math.PI * 2.0 * i / tentacles);
                    double orbitRadius = 3.55 + 0.55 * Math.sin(t * 0.8 + i);

                    for (int seg = 0; seg < 11; seg++) {
                        double segPhase = baseAngle + (seg * 0.28);
                        double radius = orbitRadius + (seg * 0.34);
                        double x = Math.cos(segPhase) * radius;
                        double z = Math.sin(segPhase) * radius;
                        double y = -1.45 + (seg * 0.34) + (Math.sin(segPhase * 1.8) * 0.2);
                        Location p = loc.clone().add(x, y, z);

                        spawnParticle(world, Particle.DUST, p, 2, blood);
                        if (seg % 2 == 0) {
                            spawnParticle(world, Particle.DUST, p, 2, bloodDark);
                        } else {
                            spawnParticle(world, Particle.SMOKE, p, 2, 0.03, 0.03, 0.03, 0.0);
                        }
                    }
                }

                spawnParticle(world, Particle.SQUID_INK, loc.clone().add(0, 0.35, 0), 8, 0.45, 0.3, 0.45, 0.01);
            }
            case "void_colossus" -> {
                double t = System.currentTimeMillis() / 150.0;

                Particle.DustOptions deepVoid = new Particle.DustOptions(Color.fromRGB(16, 18, 24), 1.9f);
                Particle.DustOptions violetEdge = new Particle.DustOptions(Color.fromRGB(90, 40, 140), 1.4f);
                Particle.DustOptions abyssBlue = new Particle.DustOptions(Color.fromRGB(34, 70, 120), 1.25f);

                int rings = 4;
                for (int r = 0; r < rings; r++) {
                    double radius = 1.5 + (r * 0.75);
                    double y = -1.1 + (r * 0.72);
                    int points = 18 + (r * 4);

                    for (int i = 0; i < points; i++) {
                        double angle = (Math.PI * 2.0 * i / points) + (t * (0.65 + r * 0.12));
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        Location p = loc.clone().add(x, y + Math.sin(angle * 2.0 + t) * 0.09, z);

                        spawnParticle(world, Particle.DUST, p, 1, 0.01, 0.01, 0.01, 0.0, deepVoid);
                        if (i % 2 == 0) {
                            spawnParticle(world, Particle.DUST, p, 1, 0.01, 0.01, 0.01, 0.0, violetEdge);
                        }
                        if (i % 3 == 0) {
                            spawnParticle(world, Particle.DUST, p, 1, 0.01, 0.01, 0.01, 0.0, abyssBlue);
                        }
                    }
                }

                for (int i = 0; i < 8; i++) {
                    double angle = (Math.PI * 2.0 * i / 8.0) - t * 0.6;
                    double radius = 2.8 + Math.sin(t + i) * 0.3;
                    Location p = loc.clone().add(Math.cos(angle) * radius, 0.15 + Math.sin(t * 1.3 + i) * 0.15, Math.sin(angle) * radius);
                    spawnParticle(world, Particle.REVERSE_PORTAL, p, 1, 0.02, 0.02, 0.02, 0.0);
                    spawnParticle(world, Particle.PORTAL, p, 1, 0.02, 0.02, 0.02, 0.0);
                }

                spawnParticle(world, Particle.SQUID_INK, loc.clone().add(0, 0.2, 0), 5, 0.4, 0.3, 0.4, 0.01);
                spawnParticle(world, Particle.SMOKE, loc.clone().add(0, 0.35, 0), 8, 0.45, 0.35, 0.45, 0.01);
            }
            default -> {}
        }
    }

    private void spawnParticle(World world, Particle particle, Location location, int count, Object... extras) {
        if (world == null || location == null || count <= 0) return;

        for (Player viewer : world.getPlayers()) {
            if (viewer == null || viewer.isDead()) continue;
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
                double extra = n3.doubleValue();
                if (extras[4] instanceof Boolean force) {
                    viewer.spawnParticle(particle, location, count, ox, oy, oz, extra, force);
                } else {
                    viewer.spawnParticle(particle, location, count, ox, oy, oz, extra, extras[4]);
                }
            } else {
                viewer.spawnParticle(particle, location, count, ox, oy, oz, extras[3]);
            }
            return;
        }
        if (extraLen == 6 && extras[0] instanceof Number n0 && extras[1] instanceof Number n1 && extras[2] instanceof Number n2
                && extras[3] instanceof Number n3 && extras[5] instanceof Boolean force) {
            viewer.spawnParticle(particle, location, count,
                    n0.doubleValue(), n1.doubleValue(), n2.doubleValue(), n3.doubleValue(), extras[4], force);
            return;
        }

        viewer.spawnParticle(particle, location, count, extras[0]);
    }
}

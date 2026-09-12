package com.fishrework.manager;

import com.fishrework.FishRework;
import com.fishrework.MobManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Version-neutral runtime support for the 26.2 Sulfur Cube sea creature.
 * This class deliberately avoids linking against SulfurCube so the same jar can
 * still be compiled and loaded for Minecraft versions where that type is absent.
 */
public final class SulfurCubeController implements Listener, Runnable {

    public static final String MOB_ID = "sulfur_cube";
    private static final int TICKS_PER_RUN = 5;
    private static final int ATTACK_COOLDOWN_TICKS = 8;
    private static final double ABILITY_RANGE = 3.6;
    private static final Particle.DustOptions SULFUR_YELLOW =
            new Particle.DustOptions(Color.fromRGB(235, 212, 56), 1.15f);
    private static final Particle.DustOptions CINNABAR_RED =
            new Particle.DustOptions(Color.fromRGB(190, 55, 40), 1.0f);

    private final FishRework plugin;
    private final NamespacedKey variantKey;
    private final NamespacedKey lastAttackerKey;
    private final NamespacedKey lastAttackTickKey;
    private final Map<UUID, Long> playerAttackCooldowns = new ConcurrentHashMap<>();
    private long gameTick;

    public SulfurCubeController(FishRework plugin) {
        this.plugin = plugin;
        this.variantKey = new NamespacedKey(plugin, "sulfur_cube_variant");
        this.lastAttackerKey = new NamespacedKey(plugin, "sulfur_cube_last_attacker");
        this.lastAttackTickKey = new NamespacedKey(plugin, "sulfur_cube_last_attack_tick");
    }

    /** Applies one uniformly-random vanilla archetype by equipping its representative block. */
    public void configure(LivingEntity entity) {
        if (!isSulfurCube(entity)) return;

        Variant[] variants = Variant.values();
        Variant variant = variants[ThreadLocalRandom.current().nextInt(variants.length)];
        entity.getPersistentDataContainer().set(variantKey, PersistentDataType.STRING, variant.name());

        // In 26.2 the absorbed block is the cube mob's BODY equipment. Equipping it
        // activates the matching vanilla archetype and visuals without a hard API link.
        if (entity.getEquipment() != null) {
            entity.getEquipment().setItem(EquipmentSlot.BODY, new ItemStack(variant.block), true);
            entity.getEquipment().setDropChance(EquipmentSlot.BODY, 0.0f);
        }
        invokeIfPresent(entity, "setSize", new Class<?>[]{int.class}, 2);
        // Absorbing a block normally disables the cube's AI. Re-enable both AI and
        // wandering so movement remains identical to a standard unloaded cube.
        entity.setAI(true);
        invokeIfPresent(entity, "setWander", new Class<?>[]{boolean.class}, true);
    }

    @Override
    public void run() {
        gameTick += TICKS_PER_RUN;
        MobManager mobs = plugin.getMobManager();

        for (UUID uuid : mobs.getActiveFishedMobs()) {
            Entity raw = Bukkit.getEntity(uuid);
            if (!(raw instanceof LivingEntity cube) || !cube.isValid() || cube.isDead()) continue;
            if (!MOB_ID.equals(mobs.getMobId(cube))) continue;

            Variant variant = getVariant(cube);
            spawnAura(cube, variant);

            if (gameTick % 20 != 0) continue;
            Player target = nearestPlayer(cube, ABILITY_RANGE);
            if (target != null) {
                useVariantAbility(cube, target, variant);
            }
        }

        if (gameTick % 200 == 0) {
            playerAttackCooldowns.entrySet().removeIf(entry -> Bukkit.getPlayer(entry.getKey()) == null);
        }
    }

    /**
     * Loaded cubes ignore ordinary player damage in vanilla. Convert an actual
     * arm swing aimed at this custom creature into direct, cooldown-aware HP loss.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) return;

        Entity target = player.getTargetEntity(5, false);
        if (!(target instanceof LivingEntity cube) || !isSulfurCube(cube)) return;

        long now = Bukkit.getCurrentTick();
        long last = playerAttackCooldowns.getOrDefault(player.getUniqueId(), Long.MIN_VALUE / 2);
        if (now - last < ATTACK_COOLDOWN_TICKS) return;
        playerAttackCooldowns.put(player.getUniqueId(), now);

        double attackDamage = 1.0;
        if (player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            attackDamage = player.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getValue();
        }
        double cooldown = Math.max(0.0, Math.min(1.0, player.getAttackCooldown()));
        double damage = attackDamage * (0.2 + cooldown * cooldown * 0.8);
        if (getVariant(cube) == Variant.HIGH_RESISTANCE) damage *= 0.65;
        damage = Math.max(1.0, damage);

        applyDirectDamage(cube, player, damage);
        knockCubeFromAttacker(cube, player, getVariant(cube));
    }

    /** Projectiles also need the immunity bypass; ProjectileHitEvent still fires on an immune cube. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getHitEntity() instanceof LivingEntity cube) || !isSulfurCube(cube)) return;
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) return;

        double damage = projectile instanceof AbstractArrow arrow ? arrow.getDamage() : 4.0;
        if (getVariant(cube) == Variant.HIGH_RESISTANCE) damage *= 0.65;
        applyDirectDamage(cube, player, Math.max(1.0, damage));
    }

    /** Prevent a second damage application on implementations that also emit this event. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNativePlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity cube) || !isSulfurCube(cube)) return;
        boolean playerSourced = event.getDamager() instanceof Player;
        if (event.getDamager() instanceof Projectile projectile) {
            playerSourced = projectile.getShooter() instanceof Player;
        }
        if (!playerSourced) return;
        event.setCancelled(true);
    }

    public Player getRecentAttacker(LivingEntity cube) {
        if (!isSulfurCube(cube)) return null;
        Long tick = cube.getPersistentDataContainer().get(lastAttackTickKey, PersistentDataType.LONG);
        String rawUuid = cube.getPersistentDataContainer().get(lastAttackerKey, PersistentDataType.STRING);
        if (tick == null || rawUuid == null || Bukkit.getCurrentTick() - tick > 20L * 30L) return null;
        try {
            return Bukkit.getPlayer(UUID.fromString(rawUuid));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Used by other Fish Rework combat systems (for example bobber damage). */
    public void applyDirectDamage(LivingEntity cube, Player player, double damage) {
        if (!isSulfurCube(cube) || player == null || damage <= 0.0 || cube.isDead()) return;
        long now = Bukkit.getCurrentTick();
        cube.getPersistentDataContainer().set(lastAttackerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        cube.getPersistentDataContainer().set(lastAttackTickKey, PersistentDataType.LONG, now);
        cube.playHurtAnimation(player.getLocation().getYaw());
        cube.getWorld().playSound(cube.getLocation(), "minecraft:entity.sulfur_cube.hurt", 0.8f, 1.0f);
        cube.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                cube.getLocation().add(0, cube.getHeight() * 0.55, 0), 5, 0.25, 0.25, 0.25, 0.02);
        cube.setHealth(Math.max(0.0, cube.getHealth() - damage));
    }

    private boolean isSulfurCube(LivingEntity entity) {
        return entity != null
                && MOB_ID.equals(plugin.getMobManager().getMobId(entity))
                && "SULFUR_CUBE".equals(entity.getType().name());
    }

    private Variant getVariant(LivingEntity cube) {
        String stored = cube.getPersistentDataContainer().get(variantKey, PersistentDataType.STRING);
        if (stored == null) return Variant.REGULAR;
        try {
            return Variant.valueOf(stored.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Variant.REGULAR;
        }
    }

    private void useVariantAbility(LivingEntity cube, Player target, Variant variant) {
        Location center = cube.getLocation().add(0, cube.getHeight() * 0.4, 0);
        switch (variant) {
            case REGULAR -> target.damage(4.0, cube);
            case BOUNCY -> {
                target.damage(3.0, cube);
                target.setVelocity(target.getVelocity().setY(0.85));
            }
            case SLOW_BOUNCY -> {
                target.damage(4.0, cube);
                target.setVelocity(target.getVelocity().setY(0.5));
                effect(target, PotionEffectType.SLOWNESS, 35, 0);
            }
            case SLOW_FLAT -> {
                target.damage(5.0, cube);
                effect(target, PotionEffectType.WEAKNESS, 50, 0);
            }
            case FAST_FLAT -> {
                target.damage(5.0, cube);
                dash(cube, target, 0.75);
            }
            case LIGHT -> {
                target.damage(3.0, cube);
                effect(target, PotionEffectType.LEVITATION, 25, 0);
            }
            case FAST_SLIDING -> {
                target.damage(4.0, cube);
                knockAway(cube, target, 1.15);
            }
            case SLOW_SLIDING -> {
                target.damage(4.0, cube);
                target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + 70));
                effect(target, PotionEffectType.SLOWNESS, 45, 1);
            }
            case HIGH_RESISTANCE -> {
                target.damage(4.0, cube);
                effect(target, PotionEffectType.MINING_FATIGUE, 60, 0);
            }
            case STICKY -> {
                target.damage(3.0, cube);
                effect(target, PotionEffectType.SLOWNESS, 60, 2);
            }
            case HOT -> {
                target.damage(5.0, cube);
                target.setFireTicks(Math.max(target.getFireTicks(), 60));
            }
            case EXPLOSIVE -> {
                target.damage(7.0, cube);
                knockAway(cube, target, 1.35);
                cube.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.35f);
                cube.getWorld().spawnParticle(Particle.EXPLOSION, center, 2, 0.25, 0.25, 0.25, 0.0);
            }
        }
    }

    private void spawnAura(LivingEntity cube, Variant variant) {
        Location center = cube.getLocation().add(0, cube.getHeight() * 0.5, 0);
        double phase = (gameTick * 0.12) + variant.ordinal() * 0.5;
        for (int i = 0; i < 2; i++) {
            double angle = phase + Math.PI * i;
            Location point = center.clone().add(Math.cos(angle) * 0.65, Math.sin(phase * 0.6 + i) * 0.18, Math.sin(angle) * 0.65);
            cube.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, variant.dust);
        }
        Particle goo = particleByName("SULFUR_CUBE_GOO", Particle.SPLASH);
        cube.getWorld().spawnParticle(goo, center, 1, 0.28, 0.2, 0.28, 0.01);
    }

    private Player nearestPlayer(LivingEntity cube, double range) {
        Player nearest = null;
        double best = range * range;
        for (Player player : cube.getWorld().getPlayers()) {
            if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) continue;
            double distance = player.getLocation().distanceSquared(cube.getLocation());
            if (distance <= best) {
                nearest = player;
                best = distance;
            }
        }
        return nearest;
    }

    private static void effect(Player player, PotionEffectType type, int ticks, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true));
    }

    private static void dash(LivingEntity cube, Player target, double speed) {
        Vector direction = target.getLocation().toVector().subtract(cube.getLocation().toVector());
        if (direction.lengthSquared() > 0.001) cube.setVelocity(direction.normalize().multiply(speed));
    }

    private static void knockAway(LivingEntity cube, Player target, double strength) {
        Vector direction = target.getLocation().toVector().subtract(cube.getLocation().toVector());
        if (direction.lengthSquared() <= 0.001) return;
        Vector velocity = direction.normalize().multiply(strength);
        velocity.setY(Math.max(0.28, velocity.getY()));
        target.setVelocity(velocity);
    }

    private static void knockCubeFromAttacker(LivingEntity cube, Player attacker, Variant variant) {
        Vector away = cube.getLocation().toVector().subtract(attacker.getEyeLocation().toVector());
        away.setY(0.0);
        if (away.lengthSquared() <= 0.001) {
            away = attacker.getLocation().getDirection().multiply(-1.0).setY(0.0);
        }
        if (away.lengthSquared() <= 0.001) return;

        Vector knockback = away.normalize().multiply(variant.hitKnockback);
        knockback.setY(variant.hitLift);
        cube.setVelocity(knockback);
        cube.getWorld().playSound(cube.getLocation(), "minecraft:entity.sulfur_cube.bounce", 0.7f, variant.soundPitch);
    }

    private static Particle particleByName(String name, Particle fallback) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static void invokeIfPresent(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method method = target.getClass().getMethod(name, types);
            method.invoke(target, args);
        } catch (ReflectiveOperationException ignored) {
            // Expected on pre-26.2 servers.
        }
    }

    private enum Variant {
        REGULAR(Material.DIRT, SULFUR_YELLOW, 0.80, 0.32, 1.00f),
        BOUNCY(Material.OAK_PLANKS, new Particle.DustOptions(Color.fromRGB(181, 135, 78), 1.0f), 1.35, 0.58, 1.20f),
        SLOW_BOUNCY(Material.STONE, new Particle.DustOptions(Color.fromRGB(125, 125, 125), 1.0f), 0.82, 0.50, 0.85f),
        SLOW_FLAT(Material.IRON_BLOCK, new Particle.DustOptions(Color.fromRGB(215, 218, 220), 1.0f), 0.58, 0.24, 0.75f),
        FAST_FLAT(Material.OAK_LEAVES, new Particle.DustOptions(Color.fromRGB(75, 150, 55), 1.0f), 1.05, 0.28, 1.15f),
        LIGHT(Material.WHITE_WOOL, new Particle.DustOptions(Color.fromRGB(245, 245, 245), 0.9f), 1.50, 0.68, 1.35f),
        FAST_SLIDING(Material.BLUE_ICE, new Particle.DustOptions(Color.fromRGB(85, 145, 235), 1.0f), 1.25, 0.22, 1.25f),
        SLOW_SLIDING(Material.RED_MUSHROOM_BLOCK, new Particle.DustOptions(Color.fromRGB(205, 52, 48), 1.0f), 0.78, 0.22, 0.90f),
        HIGH_RESISTANCE(Material.SOUL_SAND, new Particle.DustOptions(Color.fromRGB(92, 68, 55), 1.1f), 0.38, 0.18, 0.65f),
        STICKY(Material.HONEY_BLOCK, new Particle.DustOptions(Color.fromRGB(242, 170, 45), 1.0f), 0.62, 0.25, 0.80f),
        EXPLOSIVE(Material.TNT, CINNABAR_RED, 1.00, 0.38, 1.05f),
        HOT(Material.MAGMA_BLOCK, new Particle.DustOptions(Color.fromRGB(255, 92, 30), 1.1f), 0.92, 0.35, 0.95f);

        private final Material block;
        private final Particle.DustOptions dust;
        private final double hitKnockback;
        private final double hitLift;
        private final float soundPitch;

        Variant(Material block, Particle.DustOptions dust,
                double hitKnockback, double hitLift, float soundPitch) {
            this.block = block;
            this.dust = dust;
            this.hitKnockback = hitKnockback;
            this.hitLift = hitLift;
            this.soundPitch = soundPitch;
        }
    }
}

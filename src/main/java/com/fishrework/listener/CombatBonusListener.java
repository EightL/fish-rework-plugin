package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.gui.StatHelper;
import com.fishrework.util.FeatureKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies Sea Creature Defense (SCD) and Sea Creature Attack (SCA) bonuses,
 * and shows floating damage indicators on fished mobs.
 * <p>
 * SCD reduces damage taken from fished mobs (percentage-based from armor PDC).
 * SCA increases damage dealt to fished mobs (percentage-based from armor PDC).
 * <p>
 * Also applies flat SC bonuses from the Upgrade GUI.
 * Handles indirect damage sources: projectiles, evoker fangs, and vexes.
 */
public class CombatBonusListener implements Listener {

    private final FishRework plugin;
    private final boolean damageIndicatorsEnabled;
    private final NamespacedKey heatMarkUntilKey;
    private final NamespacedKey emberVolleyProjectileKey;
    private final NamespacedKey wailingToxicProjectileKey;
    private final NamespacedKey wailingToxicHandledKey;
    private final NamespacedKey projectileDamageKey;
    private final NamespacedKey shotgunVolleyEnchantKey;
    private final NamespacedKey shotgunVolleyProjectileKey;
    private final NamespacedKey shotgunVolleyLevelKey;

    private static final double SHOTGUN_SPREAD_STEP_DEGREES = 4.5;
    private static final double SHOTGUN_VERTICAL_SPREAD = 0.08;

    public CombatBonusListener(FishRework plugin) {
        this.plugin = plugin;
        this.damageIndicatorsEnabled = plugin.isFeatureEnabled(FeatureKeys.DAMAGE_INDICATORS_ENABLED)
            && plugin.getConfig().getBoolean("combat.damage_indicator_enabled", true);
        this.heatMarkUntilKey = new NamespacedKey(plugin, "blaze_fisher_heat_mark_until");
        this.emberVolleyProjectileKey = new NamespacedKey(plugin, "ember_volley_projectile");
        this.wailingToxicProjectileKey = new NamespacedKey(plugin, "wailing_toxic_projectile");
        this.wailingToxicHandledKey = new NamespacedKey(plugin, "wailing_toxic_handled");
        this.projectileDamageKey = new NamespacedKey(plugin, "projectile_damage");
        this.shotgunVolleyEnchantKey = new NamespacedKey("fishrework", "shotgun_volley");
        this.shotgunVolleyProjectileKey = new NamespacedKey(plugin, "shotgun_volley_projectile");
        this.shotgunVolleyLevelKey = new NamespacedKey(plugin, "shotgun_volley_level");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onShotgunProjectileHitEntity(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Projectile projectile)) return;
        if (!(event.getHitEntity() instanceof LivingEntity victim)) return;
        if (!(projectile.getShooter() instanceof Player)) return;
        if (!projectile.getPersistentDataContainer().has(shotgunVolleyProjectileKey, PersistentDataType.BYTE)) return;
        if (victim instanceof Player) return;

        // Clear i-frames as early as possible when a tagged pellet collides.
        victim.setNoDamageTicks(0);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onShotgunProjectileDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof org.bukkit.entity.Projectile projectile)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!(projectile.getShooter() instanceof Player)) return;
        if (!projectile.getPersistentDataContainer().has(shotgunVolleyProjectileKey, PersistentDataType.BYTE)) return;
        if (victim instanceof Player) return;

        // Ensure each pellet in the volley can register damage on the same mob.
        victim.setNoDamageTicks(0);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {

        // ── SCD: Fished mob (or its projectile/fangs/vex) attacks player → reduce damage ──
        if (event.getEntity() instanceof Player player) {
            LivingEntity attacker = resolveFishedAttacker(event.getDamager());
            if (attacker != null) {
                double scdPercent = plugin.getMobManager().getEquipmentSeaCreatureDefense(player);
                double flatDef = StatHelper.getEquipmentFlatSCBonus(player, plugin.getItemManager().SC_FLAT_DEFENSE_KEY);

                // Set default flat thing if player doesn't have upgrades
                if (flatDef == 0) flatDef = plugin.getConfig().getDouble("combat.default_flat_defense", 1.0);

                double finalReduction;
                double armor = 0.0;
                org.bukkit.attribute.AttributeInstance armorAttr = player.getAttribute(org.bukkit.attribute.Attribute.ARMOR);
                if (armorAttr != null) armor = armorAttr.getValue();

                // Formula: (Armor + Flat) * SC_Defence%
                // Acts as a reduction from the incoming damage.
                finalReduction = (armor + flatDef) * (scdPercent / 100.0);
                
                event.setDamage(Math.max(0.0, event.getDamage() - finalReduction));

                if ("blaze_fisher".equals(plugin.getMobManager().getMobId(attacker))) {
                    applyBlazeHeatMarkBonus(player, event);
                }
            }
        }

        // ── SCA: Player (or player's projectile) attacks fished mob → increase damage ──
        if (event.getEntity() instanceof LivingEntity victim
                && plugin.getMobManager().isFishedMob(victim)) {
            Player player = resolvePlayerAttacker(event.getDamager());
            if (player != null) {
                double scaPercent = plugin.getMobManager().getEquipmentSeaCreatureAttack(player);

                // Thrown tridents are no longer in the main hand — read SCA from the projectile item
                if (event.getDamager() instanceof org.bukkit.entity.Trident thrownTrident) {
                    org.bukkit.inventory.ItemStack tridentItem = thrownTrident.getItem();
                    if (tridentItem.hasItemMeta()) {
                        org.bukkit.persistence.PersistentDataContainer pdc = tridentItem.getItemMeta().getPersistentDataContainer();
                        org.bukkit.NamespacedKey scaKey = plugin.getItemManager().SEA_CREATURE_ATTACK_KEY;
                        if (pdc.has(scaKey, org.bukkit.persistence.PersistentDataType.DOUBLE)) {
                            scaPercent += pdc.get(scaKey, org.bukkit.persistence.PersistentDataType.DOUBLE);
                        }
                    }
                }

                double flatAtk = StatHelper.getEquipmentFlatSCBonus(player, plugin.getItemManager().SC_FLAT_ATTACK_KEY);

                // Set default flat thing if player doesn't have upgrades
                if (flatAtk == 0) flatAtk = plugin.getConfig().getDouble("combat.default_flat_attack", 1.0);

                // Formula: (Damage + Flat) * (1 + SC_Attack%)
                double projectileBonusDamage = 0.0;
                if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
                    PersistentDataContainer projectilePdc = projectile.getPersistentDataContainer();
                    if (projectilePdc.has(projectileDamageKey, PersistentDataType.DOUBLE)) {
                        Double stored = projectilePdc.get(projectileDamageKey, PersistentDataType.DOUBLE);
                        if (stored != null && stored > 0.0) {
                            projectileBonusDamage = stored;
                        }
                    }
                }

                double originalDamage = event.getDamage() + projectileBonusDamage;
                double newDamage = (originalDamage + flatAtk) * (1.0 + scaPercent / 100.0);
                
                event.setDamage(newDamage);
            }
        }

        // ── Custom Projectile Damage Amplification ──
        if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof LivingEntity shooter
                && plugin.getMobManager().isFishedMob(shooter)) {
            String mobId = plugin.getMobManager().getMobId(shooter);
            if ("ghastling".equals(mobId)) {
                event.setDamage(event.getDamage() + 10.0);
            } else if ("wailing_ghast_duo".equals(mobId)) {
                if (proj.getPersistentDataContainer().has(wailingToxicProjectileKey, PersistentDataType.BYTE)) {
                    if (proj.getPersistentDataContainer().has(wailingToxicHandledKey, PersistentDataType.BYTE)) {
                        event.setDamage(0.0);
                        return;
                    }
                    double projectileDamage = plugin.getConfig().getDouble("combat.abilities.wailing_twin_toxic_spit.projectile_damage", 10.0);
                    int poisonTicks = plugin.getConfig().getInt("combat.abilities.wailing_twin_toxic_spit.poison_ticks", 90);
                    int poisonAmplifier = plugin.getConfig().getInt("combat.abilities.wailing_twin_toxic_spit.poison_amplifier", 1);
                    event.setDamage(projectileDamage);
                    if (event.getEntity() instanceof LivingEntity victim) {
                        victim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.POISON,
                                poisonTicks,
                                Math.max(0, poisonAmplifier)
                        ));
                        victim.getWorld().spawnParticle(
                            Particle.DUST,
                            victim.getLocation().add(0, victim.getHeight() * 0.45, 0),
                            10,
                            0.2,
                            0.2,
                            0.2,
                            0.0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(88, 220, 110), 1.05f)
                        );
                    }
                    return;
                }
                event.setDamage(event.getDamage() + 10.0);
            } else if ("ghast_broodmother".equals(mobId)) {
                event.setDamage(event.getDamage() * 3.0);
            } else if ("blaze_fisher".equals(mobId)
                    && proj.getPersistentDataContainer().has(emberVolleyProjectileKey, PersistentDataType.BYTE)) {
                double projectileDamage = plugin.getConfig().getDouble("combat.abilities.ember_volley.projectile_damage", 6.0);
                int fireTicks = plugin.getConfig().getInt("combat.abilities.ember_volley.fire_ticks", 40);
                event.setDamage(projectileDamage);
                if (event.getEntity() instanceof LivingEntity victim) {
                    victim.setFireTicks(Math.max(victim.getFireTicks(), fireTicks));
                }
            }
        }

        // ── Damage Indicators: Show floating text when player hits fished mob ──
        if (damageIndicatorsEnabled
                && event.getEntity() instanceof LivingEntity entity
                && plugin.getMobManager().isFishedMob(entity)) {
            Player dmgPlayer = resolvePlayerAttacker(event.getDamager());
            if (dmgPlayer != null) {
                com.fishrework.model.PlayerData data = plugin.getPlayerData(dmgPlayer.getUniqueId());
                if (data == null || data.isDamageIndicatorsEnabled()) {
                    double damage = Math.min(event.getFinalDamage(), entity.getHealth());
                    showDamageIndicator(entity, damage);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) return;
        if (!(event.getProjectile() instanceof org.bukkit.entity.Projectile projectile)) return;

        org.bukkit.inventory.ItemStack weapon = event.getBow();
        if (weapon == null || !weapon.hasItemMeta()) return;

        PersistentDataContainer weaponPdc = weapon.getItemMeta().getPersistentDataContainer();
        Double bonus = null;
        if (weaponPdc.has(projectileDamageKey, PersistentDataType.DOUBLE)) {
            bonus = weaponPdc.get(projectileDamageKey, PersistentDataType.DOUBLE);
            if (bonus != null && bonus > 0.0) {
                projectile.getPersistentDataContainer().set(projectileDamageKey, PersistentDataType.DOUBLE, bonus);
            }
        }

        if (weapon.getType() != Material.CROSSBOW) return;
        if (!(projectile instanceof AbstractArrow baseArrow)) return;

        int volleyLevel = 0;
        org.bukkit.enchantments.Enchantment shotgunVolley = org.bukkit.Registry.ENCHANTMENT.get(shotgunVolleyEnchantKey);
        if (shotgunVolley != null) {
            volleyLevel = weapon.getEnchantmentLevel(shotgunVolley);
        }
        Integer fallbackVolleyLevel = weaponPdc.get(shotgunVolleyLevelKey, PersistentDataType.INTEGER);
        if (fallbackVolleyLevel != null) {
            volleyLevel = Math.max(volleyLevel, fallbackVolleyLevel);
        }
        if (volleyLevel <= 0) return;

        markShotgunProjectile(baseArrow, volleyLevel);
        spawnShotgunPellets(shooter, baseArrow, volleyLevel, bonus != null ? bonus : 0.0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWailingToxicImpact(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball)) return;

        PersistentDataContainer pdc = fireball.getPersistentDataContainer();
        if (!pdc.has(wailingToxicProjectileKey, PersistentDataType.BYTE)) return;
        if (pdc.has(wailingToxicHandledKey, PersistentDataType.BYTE)) return;
        if (!(fireball.getShooter() instanceof LivingEntity shooter)) return;
        if (!plugin.getMobManager().isFishedMob(shooter)) return;
        if (!"wailing_ghast_duo".equals(plugin.getMobManager().getMobId(shooter))) return;

        pdc.set(wailingToxicHandledKey, PersistentDataType.BYTE, (byte) 1);

        if (event.getHitEntity() instanceof LivingEntity victim && !victim.isDead()) {
            double projectileDamage = plugin.getConfig().getDouble("combat.abilities.wailing_twin_toxic_spit.projectile_damage", 10.0);
            int poisonTicks = plugin.getConfig().getInt("combat.abilities.wailing_twin_toxic_spit.poison_ticks", 90);
            int poisonAmplifier = plugin.getConfig().getInt("combat.abilities.wailing_twin_toxic_spit.poison_amplifier", 1);

            victim.damage(projectileDamage, shooter);
            victim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.POISON,
                    poisonTicks,
                    Math.max(0, poisonAmplifier)
            ));
            victim.getWorld().spawnParticle(
                    Particle.DUST,
                    victim.getLocation().add(0, victim.getHeight() * 0.45, 0),
                    12,
                    0.2,
                    0.2,
                    0.2,
                    0.0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(88, 220, 110), 1.05f)
            );
        }

        fireball.remove();
    }

    private void applyBlazeHeatMarkBonus(Player player, EntityDamageByEntityEvent event) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Long markedUntil = pdc.get(heatMarkUntilKey, PersistentDataType.LONG);
        if (markedUntil == null) return;

        long now = System.currentTimeMillis();
        if (now > markedUntil) {
            pdc.remove(heatMarkUntilKey);
            return;
        }

        pdc.remove(heatMarkUntilKey);

        double bonusDamage = plugin.getConfig().getDouble("combat.abilities.heat_mark.bonus_damage", 5.0);
        int burnTicks = plugin.getConfig().getInt("combat.abilities.heat_mark.burn_ticks", 60);

        event.setDamage(event.getDamage() + bonusDamage);
        player.setFireTicks(Math.max(player.getFireTicks(), burnTicks));

        Location hit = player.getLocation().add(0, player.getHeight() * 0.5, 0);
        player.getWorld().spawnParticle(Particle.FLAME, hit, 18, 0.22, 0.28, 0.22, 0.02);
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, hit, 8, 0.18, 0.24, 0.18, 0.01);
        player.getWorld().spawnParticle(Particle.SMOKE, hit, 10, 0.2, 0.2, 0.2, 0.01);
        player.getWorld().playSound(hit, Sound.ENTITY_BLAZE_HURT, 0.9f, 0.9f);
    }

    private void showDamageIndicator(LivingEntity entity, double damage) {
        double offsetRange = plugin.getConfig().getDouble("combat.damage_indicator_offset_range", 0.5);
        double yOffset = plugin.getConfig().getDouble("combat.damage_indicator_y_offset", 0.5);
        double offsetX = (Math.random() - 0.5) * offsetRange;
        double offsetZ = (Math.random() - 0.5) * offsetRange;
        Location loc = entity.getEyeLocation().add(offsetX, yOffset, offsetZ);
        TextDisplay display = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);

        display.text(Component.text(com.fishrework.util.FormatUtil.format("-%.1f", damage))
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD));
        display.setBillboard(Display.Billboard.CENTER);
        display.setViewRange((float) plugin.getConfig().getDouble("combat.damage_indicator_view_range", 32));
        display.setSeeThrough(true);

        Bukkit.getScheduler().runTaskLater(plugin, display::remove, plugin.getConfig().getLong("combat.damage_indicator_duration_ticks", 30));
    }

    /**
     * Resolves the actual fished-mob attacker from direct hits, projectiles,
     * evoker fangs, or vexes summoned by fished evokers.
     */
    private LivingEntity resolveFishedAttacker(Entity damager) {
        // Direct melee from a fished mob
        if (damager instanceof LivingEntity le && plugin.getMobManager().isFishedMob(le)) {
            return le;
        }
        // Projectile (arrow, trident, etc.) fired by a fished mob
        if (damager instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof LivingEntity shooter
                && plugin.getMobManager().isFishedMob(shooter)) {
            return shooter;
        }
        // Evoker fangs cast by a fished evoker (temple_guardian, pillager groups)
        if (damager instanceof org.bukkit.entity.EvokerFangs fangs
                && fangs.getOwner() instanceof LivingEntity owner
                && plugin.getMobManager().isFishedMob(owner)) {
            return owner;
        }
        // Vex summoned by a fished evoker
        if (damager instanceof org.bukkit.entity.Vex vex) {
            try {
                org.bukkit.entity.Mob summoner = vex.getSummoner();
                if (summoner != null && plugin.getMobManager().isFishedMob(summoner)) {
                    return summoner;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Resolves the player attacker from direct melee or projectile shots.
     * Allows SCA to work with bows, tridents, and other ranged weapons.
     */
    private Player resolvePlayerAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    private void spawnShotgunPellets(Player shooter, AbstractArrow baseArrow, int volleyLevel, double projectileBonus) {
        int totalArrows = getShotgunArrowCount(volleyLevel);
        if (totalArrows <= 1) return;

        Vector baseVelocity = baseArrow.getVelocity();
        double baseSpeed = baseVelocity.length();
        if (baseSpeed <= 0.0) {
            baseSpeed = 3.0;
            baseVelocity = shooter.getEyeLocation().getDirection().multiply(baseSpeed);
        }

        Location eye = shooter.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-6) {
            right = new Vector(1, 0, 0);
        } else {
            right.normalize();
        }
        Location spawnOrigin = eye.clone().add(forward.clone().multiply(0.55));

        for (int i = 0; i < totalArrows - 1; i++) {
            double spreadStep = ((i / 2) + 1) * SHOTGUN_SPREAD_STEP_DEGREES;
            double yawOffset = (i % 2 == 0) ? -spreadStep : spreadStep;

            Vector pelletVelocity = rotateAroundY(baseVelocity, yawOffset);
            pelletVelocity.setY(pelletVelocity.getY() + ThreadLocalRandom.current().nextDouble(-SHOTGUN_VERTICAL_SPREAD, SHOTGUN_VERTICAL_SPREAD));
            pelletVelocity = pelletVelocity.normalize().multiply(baseSpeed);

                double lateralDirection = (i % 2 == 0) ? -1.0 : 1.0;
                double lateralScale = Math.min(2.0, (i / 2) + 1) * 0.06;
                Location pelletSpawn = spawnOrigin.clone()
                    .add(right.clone().multiply(lateralDirection * lateralScale))
                    .add(0.0, 0.02, 0.0);

                Arrow pellet = shooter.getWorld().spawnArrow(pelletSpawn, pelletVelocity, (float) baseSpeed, 0.0f);
            pellet.setShooter(shooter);
            pellet.setDamage(baseArrow.getDamage());
            pellet.setCritical(baseArrow.isCritical());
            pellet.setFireTicks(baseArrow.getFireTicks());
            pellet.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);

            markShotgunProjectile(pellet, volleyLevel);
            if (projectileBonus > 0.0) {
                pellet.getPersistentDataContainer().set(projectileDamageKey, PersistentDataType.DOUBLE, projectileBonus);
            }
        }
    }

    private void markShotgunProjectile(AbstractArrow arrow, int volleyLevel) {
        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        pdc.set(shotgunVolleyProjectileKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(shotgunVolleyLevelKey, PersistentDataType.INTEGER, volleyLevel);
    }

    private int getShotgunArrowCount(int volleyLevel) {
        return Math.max(2, Math.min(6, volleyLevel + 1));
    }

    private Vector rotateAroundY(Vector vector, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = (vector.getX() * cos) - (vector.getZ() * sin);
        double z = (vector.getX() * sin) + (vector.getZ() * cos);
        return new Vector(x, vector.getY(), z);
    }
}

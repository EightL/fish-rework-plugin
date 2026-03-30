package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.gui.StatHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

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

    public CombatBonusListener(FishRework plugin) {
        this.plugin = plugin;
        this.damageIndicatorsEnabled = plugin.getConfig().getBoolean("combat.damage_indicator_enabled", true);
        this.heatMarkUntilKey = new NamespacedKey(plugin, "blaze_fisher_heat_mark_until");
        this.emberVolleyProjectileKey = new NamespacedKey(plugin, "ember_volley_projectile");
        this.wailingToxicProjectileKey = new NamespacedKey(plugin, "wailing_toxic_projectile");
        this.wailingToxicHandledKey = new NamespacedKey(plugin, "wailing_toxic_handled");
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
                double originalDamage = event.getDamage();
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

        display.text(Component.text(String.format("-%.1f", damage))
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
}

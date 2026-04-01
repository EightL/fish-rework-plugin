package com.fishrework.task;

import com.fishrework.FishRework;
import com.fishrework.MobManager;
import com.fishrework.model.CustomMob;
import com.fishrework.model.SpawnConfig;
import com.fishrework.util.ParticleDetailScaler;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs every second and executes YAML-defined mob abilities.
 */
public class BossAbilityTask implements Runnable {

    private static final int TICKS_PER_RUN = 20;
    private static final double PARTICLE_VIEW_DISTANCE = 64.0;
    private static final double PARTICLE_VIEW_DISTANCE_SQ = PARTICLE_VIEW_DISTANCE * PARTICLE_VIEW_DISTANCE;
    private static final Particle.DustOptions AOE_HIT_RED = new Particle.DustOptions(Color.fromRGB(200, 40, 40), 1.2f);
    private static final Particle.DustOptions FROST_BEAM_BLUE = new Particle.DustOptions(Color.fromRGB(140, 225, 255), 1.25f);
    private static final Particle.DustOptions DUNE_BEAM_SAND = new Particle.DustOptions(Color.fromRGB(218, 185, 124), 1.25f);
    private static final Particle.DustOptions VULTURE_GREEN_LIGHT = new Particle.DustOptions(Color.fromRGB(152, 255, 152), 1.3f);
    private static final Particle.DustOptions VULTURE_GREEN_DARK = new Particle.DustOptions(Color.fromRGB(34, 139, 34), 1.2f);
    private static final Particle.DustOptions TEMPLE_GREEN = new Particle.DustOptions(Color.fromRGB(170, 255, 170), 1.25f);
    private static final Particle.DustOptions TEMPLE_ORANGE = new Particle.DustOptions(Color.fromRGB(255, 165, 60), 1.15f);
    private static final Particle.DustOptions TEMPLE_YELLOW = new Particle.DustOptions(Color.fromRGB(255, 230, 110), 1.15f);
    private static final Particle.DustOptions KING_SLIME_GREEN = new Particle.DustOptions(Color.fromRGB(35, 110, 45), 1.25f);
    private static final Particle.DustOptions KING_SLIME_BROWN = new Particle.DustOptions(Color.fromRGB(110, 75, 40), 1.2f);
    private static final Particle.DustOptions POSEIDON_WAVE_AQUA = new Particle.DustOptions(Color.fromRGB(70, 220, 235), 1.4f);
    private static final Particle.DustOptions POSEIDON_WAVE_DEEP = new Particle.DustOptions(Color.fromRGB(20, 110, 185), 1.3f);
    private static final Particle.DustOptions DARK_RITUAL_TEAL = new Particle.DustOptions(Color.fromRGB(25, 170, 160), 1.2f);
    private static final Particle.DustOptions DARK_RITUAL_BLACK = new Particle.DustOptions(Color.fromRGB(18, 20, 22), 1.15f);
    private static final Particle.DustOptions EMBER_ORANGE = new Particle.DustOptions(Color.fromRGB(255, 146, 46), 1.1f);
    private static final Particle.DustOptions EMBER_RED = new Particle.DustOptions(Color.fromRGB(255, 74, 28), 1.0f);
    private static final Particle.DustOptions MAGMA_GOLD = new Particle.DustOptions(Color.fromRGB(255, 195, 70), 1.2f);
    private static final Particle.DustOptions MAGMA_RED = new Particle.DustOptions(Color.fromRGB(255, 90, 34), 1.1f);
    private static final Particle.DustOptions HOG_FIRE_ORANGE = new Particle.DustOptions(Color.fromRGB(255, 132, 44), 1.2f);
    private static final Particle.DustOptions HOG_FIRE_RED = new Particle.DustOptions(Color.fromRGB(230, 65, 25), 1.1f);
    private static final Particle.DustOptions WARPED_CYAN = new Particle.DustOptions(Color.fromRGB(66, 235, 220), 1.2f);
    private static final Particle.DustOptions WARPED_DEEP = new Particle.DustOptions(Color.fromRGB(24, 58, 78), 1.1f);
    private static final Particle.DustOptions SOUL_ICE = new Particle.DustOptions(Color.fromRGB(153, 216, 255), 1.2f);
    private static final Particle.DustOptions SOUL_GRAY = new Particle.DustOptions(Color.fromRGB(96, 102, 110), 1.1f);
    private static final Particle.DustOptions SOUL_BLACK = new Particle.DustOptions(Color.fromRGB(26, 30, 36), 1.0f);
    private static final Particle.DustOptions WITHER_VIOLET = new Particle.DustOptions(Color.fromRGB(104, 70, 150), 1.2f);
    private static final Particle.DustOptions WITHER_PURPLE = new Particle.DustOptions(Color.fromRGB(68, 40, 110), 1.15f);
    private static final Particle.DustOptions MAGMA_LASER_GOLD = new Particle.DustOptions(Color.fromRGB(255, 176, 62), 1.25f);
    private static final Particle.DustOptions MAGMA_LASER_RED = new Particle.DustOptions(Color.fromRGB(255, 88, 38), 1.15f);
    private static final Particle.DustOptions BLOOD_CRIMSON = new Particle.DustOptions(Color.fromRGB(210, 32, 44), 2.85f);
    private static final Particle.DustOptions BLOOD_DARK = new Particle.DustOptions(Color.fromRGB(78, 8, 16), 2.45f);
    private static final Particle.DustOptions ABOMINATION_ORANGE = new Particle.DustOptions(Color.fromRGB(255, 118, 26), 1.35f);
    private static final Particle.DustOptions ABOMINATION_SCARLET = new Particle.DustOptions(Color.fromRGB(148, 18, 30), 1.25f);
    private static final Particle.DustOptions ABOMINATION_DEEP = new Particle.DustOptions(Color.fromRGB(54, 8, 16), 1.15f);
    private static final Particle.DustOptions WAILING_LASER_RED = new Particle.DustOptions(Color.fromRGB(255, 52, 52), 1.35f);
    private static final Particle.DustOptions WAILING_TOXIC_GREEN = new Particle.DustOptions(Color.fromRGB(102, 255, 125), 1.25f);
    private static final Particle.DustOptions WAILING_TOXIC_DEEP = new Particle.DustOptions(Color.fromRGB(40, 155, 70), 1.15f);

    private final FishRework plugin;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, String> lastUsedAbility = new HashMap<>();
    private final Map<UUID, Boolean> wasOnGround = new HashMap<>();
    private final NamespacedKey heatMarkUntilKey;
    private final NamespacedKey emberVolleyProjectileKey;
    private final NamespacedKey emberVolleyAllowUntilKey;
    private final NamespacedKey wailingRoleKey;
    private final NamespacedKey wailingToxicProjectileKey;
    private final NamespacedKey crimsonPhaseTwoKey;
    private final NamespacedKey crimsonAbilityControllerKey;
    private long gameTick = 0L;

    public BossAbilityTask(FishRework plugin) {
        this.plugin = plugin;
        this.heatMarkUntilKey = new NamespacedKey(plugin, "blaze_fisher_heat_mark_until");
        this.emberVolleyProjectileKey = new NamespacedKey(plugin, "ember_volley_projectile");
        this.emberVolleyAllowUntilKey = new NamespacedKey(plugin, "ember_volley_allow_until");
        this.wailingRoleKey = new NamespacedKey(plugin, "wailing_ghast_role");
        this.wailingToxicProjectileKey = new NamespacedKey(plugin, "wailing_toxic_projectile");
        this.crimsonPhaseTwoKey = new NamespacedKey(plugin, "crimson_abomination_phase_two");
        this.crimsonAbilityControllerKey = new NamespacedKey(plugin, "crimson_abomination_ability_controller");
    }

    @Override
    public void run() {
        gameTick += TICKS_PER_RUN;
        MobManager mobManager = plugin.getMobManager();

        for (UUID uuid : mobManager.getActiveFishedMobs()) {
            Entity raw = Bukkit.getEntity(uuid);
            if (!(raw instanceof LivingEntity mob) || !raw.isValid() || mob.isDead()) continue;

            PersistentDataContainer pdc = mob.getPersistentDataContainer();
            if (!pdc.has(mobManager.FISHED_MOB_KEY, PersistentDataType.BYTE)) continue;

            String mobId = pdc.get(mobManager.MOB_ID_KEY, PersistentDataType.STRING);
            if (mobId == null || mobId.isBlank()) continue;

            CustomMob def = plugin.getMobRegistry().get(mobId);
            if (def == null || def.getSpawnConfig() == null) continue;

            handleCrimsonAbominationPhaseTransition(mob, mobId);

            if (!isAbilityController(mob, mobId)) continue;

            List<SpawnConfig.AbilityConfig> abilities = def.getSpawnConfig().getAbilities();
            if (abilities.isEmpty()) continue;

            handleKingSlimeLandingAbility(mob, uuid, abilities);

            SpawnConfig.AbilityConfig chosen = chooseAbility(mob, uuid, abilities);
            if (chosen == null) continue;

            executeAbility(mob, chosen.getId());
            cooldowns.computeIfAbsent(uuid, ignored -> new HashMap<>())
                    .put(chosen.getId().toUpperCase(), gameTick + Math.max(1, chosen.getCooldownTicks()));
            lastUsedAbility.put(uuid, chosen.getId().toUpperCase());
        }

        if (gameTick % 200 == 0) {
            cooldowns.entrySet().removeIf(e -> Bukkit.getEntity(e.getKey()) == null);
            lastUsedAbility.entrySet().removeIf(e -> Bukkit.getEntity(e.getKey()) == null);
            wasOnGround.entrySet().removeIf(e -> Bukkit.getEntity(e.getKey()) == null);
        }
    }

    private SpawnConfig.AbilityConfig chooseAbility(LivingEntity mob, UUID mobUuid, List<SpawnConfig.AbilityConfig> abilities) {
        Map<String, Long> perMob = cooldowns.computeIfAbsent(mobUuid, ignored -> new HashMap<>());
        List<SpawnConfig.AbilityConfig> ready = new ArrayList<>();

        int totalWeight = 0;
        for (SpawnConfig.AbilityConfig ability : abilities) {
            if ("KING_SLIME_SPLASH".equalsIgnoreCase(ability.getId())) continue;
            if (!isPhaseGateOpen(mob, ability.getId())) continue;
            if (!isRoleAbilityAllowed(mob, ability.getId())) continue;
            if (!isReady(perMob, ability)) continue;
            ready.add(ability);
            totalWeight += Math.max(0, ability.getWeight());
        }
        if (totalWeight <= 0) return null;

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int acc = 0;
        SpawnConfig.AbilityConfig chosen = null;
        for (SpawnConfig.AbilityConfig ability : ready) {
            acc += Math.max(0, ability.getWeight());
            if (roll < acc) {
                chosen = ability;
                break;
            }
        }
        if (chosen == null) return null;

        String mobId = plugin.getMobManager().getMobId(mob);
        if ("ender_angler".equals(mobId) && ready.size() > 1) {
            String last = lastUsedAbility.get(mobUuid);
            if (last != null && chosen.getId().equalsIgnoreCase(last)) {
                int altWeight = 0;
                for (SpawnConfig.AbilityConfig ability : ready) {
                    if (ability.getId().equalsIgnoreCase(last)) continue;
                    altWeight += Math.max(0, ability.getWeight());
                }

                if (altWeight > 0) {
                    int altRoll = ThreadLocalRandom.current().nextInt(altWeight);
                    int altAcc = 0;
                    for (SpawnConfig.AbilityConfig ability : ready) {
                        if (ability.getId().equalsIgnoreCase(last)) continue;
                        altAcc += Math.max(0, ability.getWeight());
                        if (altRoll < altAcc) {
                            chosen = ability;
                            break;
                        }
                    }
                }
            }
        }
        return chosen;
    }

    private boolean isReady(Map<String, Long> perMob, SpawnConfig.AbilityConfig ability) {
        String id = ability.getId().toUpperCase();
        long nextReadyTick = perMob.getOrDefault(id, 0L);
        if (gameTick < nextReadyTick) return false;
        return ThreadLocalRandom.current().nextDouble() <= clampChance(ability.getChance());
    }

    private double clampChance(double chance) {
        if (chance < 0.0) return 0.0;
        if (chance > 1.0) return 1.0;
        return chance;
    }

    private boolean isPhaseGateOpen(LivingEntity mob, String abilityId) {
        String id = abilityId.toUpperCase();
        String mobId = plugin.getMobManager().getMobId(mob);
        if ("crimson_abomination".equals(mobId)) {
            boolean phaseTwo = isCrimsonPhaseTwo(mob);
            if ("BLOOD_EXPLOSION".equals(id)) {
                return phaseTwo;
            }
            if ("BLOOD_YOKE".equals(id)) {
                return !phaseTwo;
            }
            if ("WAR_DRUM".equals(id)) {
                return !phaseTwo;
            }
        }
        if (!"POSEIDON_STORM_WAVE".equals(id) && !"WARDEN_PHASE_TWO".equals(id)) {
            return true;
        }

        if (mob.getAttribute(Attribute.MAX_HEALTH) == null) return false;
        double max = mob.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (max <= 0.0) return false;
        return mob.getHealth() <= (max * 0.5);
    }

    private boolean isRoleAbilityAllowed(LivingEntity mob, String abilityId) {
        String mobId = plugin.getMobManager().getMobId(mob);
        if (!"wailing_ghast_duo".equals(mobId)) return true;

        String role = ensureWailingRole(mob);
        if (role == null) return true;

        String id = abilityId.toUpperCase();
        if ("WAILING_TWIN_LASER".equals(id)) {
            return "LASER".equals(role);
        }
        if ("WAILING_TWIN_TOXIC_SPIT".equals(id)) {
            return "TOXIC".equals(role);
        }
        return true;
    }

    private boolean isAbilityController(LivingEntity mob, String mobId) {
        if (!"crimson_abomination".equals(mobId)) {
            return isMountedBossController(mob);
        }
        if (isCrimsonPhaseTwo(mob)) {
            return mob.getPersistentDataContainer().getOrDefault(crimsonAbilityControllerKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
        }
        return isMountedBossController(mob);
    }

    private boolean isCrimsonPhaseTwo(LivingEntity mob) {
        if (mob == null) return false;
        return mob.getPersistentDataContainer().getOrDefault(crimsonPhaseTwoKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    private void handleCrimsonAbominationPhaseTransition(LivingEntity mob, String mobId) {
        if (!"crimson_abomination".equals(mobId)) return;

        LivingEntity mount = resolveMountedBossMount(mob);
        if (mount == null || !mount.isValid() || mount.isDead()) return;
        if (isCrimsonPhaseTwo(mount)) return;

        org.bukkit.attribute.AttributeInstance maxHealth = mount.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;
        double max = maxHealth.getValue();
        if (max <= 0.0 || mount.getHealth() > (max * 0.5)) return;

        LivingEntity rider = resolveMountedBossRider(mount);
        if (rider == null || !rider.isValid() || rider.isDead()) return;

        mount.removePassenger(rider);

        mount.getPersistentDataContainer().set(crimsonPhaseTwoKey, PersistentDataType.BYTE, (byte) 1);
        rider.getPersistentDataContainer().set(crimsonPhaseTwoKey, PersistentDataType.BYTE, (byte) 1);
        mount.getPersistentDataContainer().set(crimsonAbilityControllerKey, PersistentDataType.BYTE, (byte) 0);
        rider.getPersistentDataContainer().set(crimsonAbilityControllerKey, PersistentDataType.BYTE, (byte) 1);

        Location center = mount.getLocation().clone().add(0, 0.2, 0);
        center.getWorld().playSound(center, Sound.ENTITY_PIGLIN_ANGRY, 1.15f, 0.65f);
        center.getWorld().playSound(center, Sound.ENTITY_HOGLIN_RETREAT, 1.0f, 0.8f);
        center.getWorld().playSound(center, Sound.BLOCK_CHAIN_BREAK, 0.9f, 0.85f);
        spawnParticle(center.getWorld(), Particle.DUST, center, 28, 0.9, 0.35, 0.9, 0.0, BLOOD_CRIMSON);
        spawnParticle(center.getWorld(), Particle.DUST, center, 18, 0.8, 0.28, 0.8, 0.0, ABOMINATION_ORANGE);
        spawnParticle(center.getWorld(), Particle.CRIMSON_SPORE, center, 18, 0.75, 0.25, 0.75, 0.01);

        Player target = findNearestPlayer(mount, 28.0);
        if (target != null) {
            if (mount instanceof org.bukkit.entity.Mob mountMob) {
                mountMob.setTarget(target);
            }
            if (rider instanceof org.bukkit.entity.Mob riderMob) {
                riderMob.setTarget(target);
            }

            Vector away = rider.getLocation().toVector().subtract(mount.getLocation().toVector());
            if (away.lengthSquared() <= 0.0001) {
                away = target.getLocation().toVector().subtract(mount.getLocation().toVector());
            }
            if (away.lengthSquared() > 0.0001) {
                rider.setVelocity(away.normalize().multiply(0.65).setY(0.36));
            }
        }
    }

    private void executeAbility(LivingEntity mob, String abilityId) {
        switch (abilityId.toUpperCase()) {
            case "GROUND_SLAM" -> castGroundSlam(mob);
            case "MINI_BLAST" -> castMiniBlast(mob);
            case "FROST_BEAM" -> castFrostBeam(mob);
            case "DUNE_BEAM" -> castDuneBeam(mob);
            case "VULTURE_MISSILE" -> castVultureMissile(mob);
            case "TEMPLE_RITUAL" -> castTempleRitual(mob);
            case "POSEIDON_WAVE" -> castPoseidonsWave(mob);
            case "POSEIDON_STORM_WAVE" -> castPoseidonStormWave(mob);
            case "DARK_RITUAL" -> castDarkRitual(mob);
            case "WARDEN_PHASE_TWO" -> castWardenPhaseTwo(mob);
            case "KING_SLIME_SPLASH" -> {
            }
            case "TIDAL_PULL" -> castTidalPull(mob);
            case "INK_CLOUD" -> castInkCloud(mob);
            case "EMBER_VOLLEY" -> castEmberVolley(mob);
            case "HEAT_MARK" -> castHeatMark(mob);
            case "MAGMA_BURST" -> castMagmaBurst(mob);
            case "LAVA_SURGE" -> castLavaSurge(mob);
            case "HOGLIN_FLAMETHROWER" -> castHoglinFlamethrower(mob);
            case "FIRE_GEYSERS" -> castFireGeysers(mob);
            case "STRIDER_LAVA_SPEW" -> castStriderLavaSpew(mob);
            case "NETHER_REND" -> castNetherRend(mob);
            case "WITHER_LANCES" -> castWitherLances(mob);
            case "ABYSSAL_NOVA" -> castAbyssalNova(mob);
            case "TORSO_TENTACLES" -> castTorsoTentacles(mob);
            case "BRUTE_TUSK_SWEEP" -> castBruteTuskSweep(mob);
            case "WARPED_ENDERMAN_GAZE" -> castWarpedEndermanGaze(mob);
            case "WARPED_ENDERMAN_SNARE" -> castWarpedEndermanSnare(mob);
            case "RIFTBOUND_GAZE" -> castRiftboundGaze(mob);
            case "RIFTBOUND_SNARE" -> castRiftboundSnare(mob);
            case "SOUL_RUPTURE" -> castSoulRupture(mob);
            case "SOUL_GEYSERS" -> castSoulGeysers(mob);
            case "VALLEY_DIRGE" -> castValleyDirge(mob);
            case "MAGMA_EYE_LASERS" -> castMagmaEyeLasers(mob);
            case "MAGMA_ARM_ROCKETS" -> castMagmaArmRockets(mob);
            case "BLOOD_TONGUE" -> castBloodTongue(mob);
            case "BLOOD_EXPLOSION" -> castBloodExplosion(mob);
            case "BLOOD_YOKE" -> castBloodYoke(mob);
            case "WAR_DRUM" -> castWarDrum(mob);
            case "WAILING_TWIN_LASER" -> castWailingTwinLaser(mob);
            case "WAILING_TWIN_TOXIC_SPIT" -> castWailingTwinToxicSpit(mob);
            default -> {
            }
        }
    }

    private String ensureWailingRole(LivingEntity mob) {
        if (!"wailing_ghast_duo".equals(plugin.getMobManager().getMobId(mob))) return null;

        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        String existing = pdc.get(wailingRoleKey, PersistentDataType.STRING);
        if (existing != null) return existing;

        LivingEntity nearestTwin = null;
        double nearestTwinDist = Double.MAX_VALUE;
        String twinRole = null;

        for (Entity nearby : mob.getWorld().getNearbyEntities(mob.getLocation(), 18.0, 12.0, 18.0)) {
            if (!(nearby instanceof LivingEntity twin)) continue;
            if (twin.getUniqueId().equals(mob.getUniqueId())) continue;
            if (!plugin.getMobManager().isFishedMob(twin)) continue;
            if (!"wailing_ghast_duo".equals(plugin.getMobManager().getMobId(twin))) continue;

            double dist = twin.getLocation().distanceSquared(mob.getLocation());
            if (dist < nearestTwinDist) {
                nearestTwinDist = dist;
                nearestTwin = twin;
                twinRole = twin.getPersistentDataContainer().get(wailingRoleKey, PersistentDataType.STRING);
            }
        }

        String role;
        if (nearestTwin != null && twinRole != null) {
            role = "LASER".equals(twinRole) ? "TOXIC" : "LASER";
        } else {
            role = (mob.getUniqueId().hashCode() & 1) == 0 ? "LASER" : "TOXIC";
            if (nearestTwin != null && twinRole == null) {
                nearestTwin.getPersistentDataContainer().set(wailingRoleKey, PersistentDataType.STRING,
                        "LASER".equals(role) ? "TOXIC" : "LASER");
                applyWailingGlow(nearestTwin, nearestTwin.getPersistentDataContainer().get(wailingRoleKey, PersistentDataType.STRING));
            }
        }

        pdc.set(wailingRoleKey, PersistentDataType.STRING, role);
        applyWailingGlow(mob, role);
        return role;
    }

    private void applyWailingGlow(LivingEntity mob, String role) {
        if (mob == null || role == null) return;
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard scoreboard = manager.getMainScoreboard();
        String teamName = "LASER".equals(role) ? "fr_glow_wr" : "fr_glow_wg";
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        team.color("LASER".equals(role)
                ? net.kyori.adventure.text.format.NamedTextColor.RED
                : net.kyori.adventure.text.format.NamedTextColor.GREEN);

        String entry = mob.getUniqueId().toString();
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
        mob.setGlowing(true);
    }

    private void castWailingTwinLaser(LivingEntity mob) {
        if (!"LASER".equals(ensureWailingRole(mob))) return;

        double range = plugin.getConfig().getDouble("combat.abilities.wailing_twin_laser.range", 42.0);
        int pulses = Math.max(1, plugin.getConfig().getInt("combat.abilities.wailing_twin_laser.pulses", 4));
        long pulseInterval = Math.max(1L, plugin.getConfig().getLong("combat.abilities.wailing_twin_laser.pulse_interval_ticks", 5L));
        double step = Math.max(0.2, plugin.getConfig().getDouble("combat.abilities.wailing_twin_laser.beam_step", 0.7));
        double hitRadius = plugin.getConfig().getDouble("combat.abilities.wailing_twin_laser.beam_radius", 1.3);
        double damage = plugin.getConfig().getDouble("combat.abilities.wailing_twin_laser.pulse_damage", 12.0);
        int fireTicks = plugin.getConfig().getInt("combat.abilities.wailing_twin_laser.fire_ticks", 80);

        Player target = findNearestPlayer(mob, range);
        if (target == null) return;

        mob.getWorld().playSound(mob.getEyeLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.6f);
        for (int i = 0; i < pulses; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!mob.isValid() || mob.isDead() || target.isDead()) return;
                if (target.getLocation().distanceSquared(mob.getLocation()) > (range * range)) return;

                Location origin = mob.getEyeLocation().clone();
                Location targetCenter = target.getLocation().clone().add(0, target.getHeight() * 0.5, 0);
                Vector direction = targetCenter.toVector().subtract(origin.toVector());
                if (direction.lengthSquared() <= 0.0001) return;
                direction.normalize();

                Set<UUID> hitPlayers = new HashSet<>();
                for (double d = 0.7; d <= range; d += step) {
                    Location point = origin.clone().add(direction.clone().multiply(d));
                    spawnParticle(point.getWorld(), Particle.DUST, point, 6, 0.05, 0.05, 0.05, 0.0, WAILING_LASER_RED);
                    spawnParticle(point.getWorld(), Particle.FLAME, point, 2, 0.04, 0.04, 0.04, 0.01);

                    for (Entity nearby : point.getWorld().getNearbyEntities(point, hitRadius, hitRadius, hitRadius)) {
                        if (!(nearby instanceof Player player)) continue;
                        if (player.isDead()) continue;
                        if (!hitPlayers.add(player.getUniqueId())) continue;

                        player.damage(damage, mob);
                        player.setFireTicks(Math.max(player.getFireTicks(), fireTicks));
                        spawnAoEHitParticles(player);
                    }
                }

                origin.getWorld().playSound(origin, Sound.ENTITY_GHAST_SHOOT, 0.95f, 1.45f);
            }, i * pulseInterval);
        }
    }

    private void castBloodYoke(LivingEntity mob) {
        LivingEntity mount = resolveMountedBossMount(mob);
        LivingEntity rider = resolveMountedBossRider(mount);
        if (mount == null) return;

        double range = plugin.getConfig().getDouble("combat.abilities.blood_yoke.range", 18.0);
        long chargeTicks = Math.max(8L, plugin.getConfig().getLong("combat.abilities.blood_yoke.charge_ticks", 18L));
        double chargeSpeed = plugin.getConfig().getDouble("combat.abilities.blood_yoke.charge_speed", 1.45);
        long rushDurationTicks = Math.max(8L, plugin.getConfig().getLong("combat.abilities.blood_yoke.rush_duration_ticks", 16L));
        double hitRadius = plugin.getConfig().getDouble("combat.abilities.blood_yoke.hit_radius", 2.25);
        double impactDamage = plugin.getConfig().getDouble("combat.abilities.blood_yoke.impact_damage", 8.0);
        long tetherDurationTicks = Math.max(20L, plugin.getConfig().getLong("combat.abilities.blood_yoke.tether_duration_ticks", 60L));
        double breakDistance = plugin.getConfig().getDouble("combat.abilities.blood_yoke.break_distance", 5.5);
        double dragStrength = plugin.getConfig().getDouble("combat.abilities.blood_yoke.drag_strength", 0.42);
        double chipDamage = plugin.getConfig().getDouble("combat.abilities.blood_yoke.chip_damage", 4.0);
        long chipIntervalTicks = Math.max(4L, plugin.getConfig().getLong("combat.abilities.blood_yoke.chip_interval_ticks", 10L));

        Player target = findNearestPlayer(mount, range);
        if (target == null) return;

        Location start = mount.getLocation().clone();
        mount.getWorld().playSound(start, Sound.ENTITY_HOGLIN_ANGRY, 1.1f, 0.65f);
        mount.getWorld().playSound(start, Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE, 0.8f, 0.8f);

        new BukkitRunnable() {
            long lived = 0L;
            long tetherElapsed = 0L;
            long lastChipAt = -999L;
            boolean rushing = false;
            Player hooked = null;
            Vector rushDir = null;

            @Override
            public void run() {
                if (!mount.isValid() || mount.isDead()) {
                    cancel();
                    return;
                }

                LivingEntity liveRider = rider != null && rider.isValid() && !rider.isDead() ? rider : resolveMountedBossRider(mount);
                Location mountLoc = mount.getLocation().clone();
                Location riderLoc = liveRider != null ? liveRider.getEyeLocation().clone() : mountLoc.clone().add(0, 1.35, 0);

                if (hooked != null) {
                    if (hooked.isDead() || !hooked.isValid() || !hooked.getWorld().equals(mount.getWorld())) {
                        cancel();
                        return;
                    }

                    drawBloodYokeTether(riderLoc, hooked.getLocation().clone().add(0, hooked.getHeight() * 0.5, 0), tetherElapsed);

                    Location playerFeet = hooked.getLocation().clone();
                    spawnBloodYokeSigil(playerFeet, 1.35, tetherElapsed * 0.12, true);
                    spawnBloodYokeSigil(mountLoc, 1.8, -tetherElapsed * 0.16, false);

                    Vector pull = riderLoc.toVector().subtract(hooked.getLocation().toVector());
                    double distance = pull.length();
                    if (distance > 0.001) {
                        Vector yank = pull.normalize().multiply(dragStrength + Math.max(0.0, distance - 2.0) * 0.04);
                        yank.setY(Math.max(0.08, hooked.getVelocity().getY() * 0.35));
                        hooked.setVelocity(hooked.getVelocity().multiply(0.55).add(yank));
                    }

                    if (distance > breakDistance && (tetherElapsed - lastChipAt) >= chipIntervalTicks) {
                        hooked.damage(chipDamage, mount);
                        spawnAoEHitParticles(hooked);
                        Location hit = hooked.getLocation().clone().add(0, hooked.getHeight() * 0.5, 0);
                        spawnParticle(hit.getWorld(), Particle.DUST, hit, 16, 0.18, 0.22, 0.18, 0.0, BLOOD_CRIMSON);
                        spawnParticle(hit.getWorld(), Particle.DUST, hit, 10, 0.16, 0.18, 0.16, 0.0, ABOMINATION_ORANGE);
                        hit.getWorld().playSound(hit, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 0.7f, 0.85f);
                        lastChipAt = tetherElapsed;
                    }

                    tetherElapsed += 2L;
                    if (tetherElapsed >= tetherDurationTicks) {
                        mount.getWorld().playSound(mountLoc, Sound.BLOCK_CHAIN_BREAK, 0.75f, 0.8f);
                        cancel();
                    }
                    return;
                }

                if (!rushing) {
                    if (target.isDead() || !target.isValid()) {
                        cancel();
                        return;
                    }

                    Location targetLoc = target.getLocation().clone().add(0, target.getHeight() * 0.5, 0);
                    Vector toTarget = targetLoc.toVector().subtract(mountLoc.toVector());
                    toTarget.setY(0);
                    if (toTarget.lengthSquared() <= 0.0001) {
                        cancel();
                        return;
                    }

                    Vector forward = toTarget.normalize();
                    spawnBloodYokeChargeTelegraph(mountLoc, riderLoc, forward, Math.min(range, mountLoc.distance(targetLoc)), lived);

                    if (lived >= chargeTicks) {
                        rushing = true;
                        rushDir = forward;
                        mount.setVelocity(rushDir.clone().multiply(chargeSpeed).setY(0.18));
                        mount.getWorld().playSound(mountLoc, Sound.ENTITY_ZOGLIN_ATTACK, 1.0f, 0.75f);
                        mount.getWorld().playSound(mountLoc, Sound.ITEM_TRIDENT_THROW, 0.8f, 0.6f);
                    }

                    lived += 2L;
                    return;
                }

                if (rushDir == null) {
                    cancel();
                    return;
                }

                mount.setVelocity(mount.getVelocity().multiply(0.35).add(rushDir.clone().multiply(chargeSpeed * 0.7).setY(0.06)));
                spawnBloodYokeRushTrail(mountLoc, riderLoc, rushDir, lived);

                for (Entity nearby : mount.getWorld().getNearbyEntities(mountLoc, hitRadius, 2.2, hitRadius)) {
                    if (!(nearby instanceof Player player)) continue;
                    if (player.isDead()) continue;
                    if (player.getLocation().distanceSquared(mountLoc) > (hitRadius * hitRadius)) continue;

                    hooked = player;
                    hooked.damage(impactDamage, mount);
                    spawnAoEHitParticles(hooked);
                    mount.getWorld().playSound(mountLoc, Sound.BLOCK_CHAIN_PLACE, 0.9f, 0.85f);
                    mount.getWorld().playSound(mountLoc, Sound.ENTITY_HOGLIN_ATTACK, 1.0f, 0.9f);
                    break;
                }

                lived += 2L;
                if (lived >= chargeTicks + rushDurationTicks) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void castWarDrum(LivingEntity mob) {
        LivingEntity mount = resolveMountedBossMount(mob);
        LivingEntity rider = resolveMountedBossRider(mount);
        if (mount == null) return;

        double innerRadius = plugin.getConfig().getDouble("combat.abilities.war_drum.inner_radius", 3.36);
        double middleRadius = plugin.getConfig().getDouble("combat.abilities.war_drum.middle_radius", 6.0);
        double outerRadius = plugin.getConfig().getDouble("combat.abilities.war_drum.outer_radius", 8.64);
        long beatIntervalTicks = Math.max(4L, plugin.getConfig().getLong("combat.abilities.war_drum.beat_interval_ticks", 10L));
        double firstDamage = plugin.getConfig().getDouble("combat.abilities.war_drum.first_damage", 6.0);
        double secondDamage = plugin.getConfig().getDouble("combat.abilities.war_drum.second_damage", 7.5);
        double thirdDamage = plugin.getConfig().getDouble("combat.abilities.war_drum.third_damage", 9.0);
        double finaleDamage = plugin.getConfig().getDouble("combat.abilities.war_drum.finale_damage", 12.0);
        double finaleKnockback = plugin.getConfig().getDouble("combat.abilities.war_drum.finale_knockback", 0.95);

        Location center = mount.getLocation().clone();
        mount.getWorld().playSound(center, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.5f, 0.55f);
        mount.getWorld().playSound(center, Sound.ENTITY_PIGLIN_ANGRY, 0.95f, 0.7f);

        new BukkitRunnable() {
            int beat = 0;

            @Override
            public void run() {
                if (!mount.isValid() || mount.isDead() || beat >= 4) {
                    cancel();
                    return;
                }

                LivingEntity liveRider = rider != null && rider.isValid() && !rider.isDead() ? rider : resolveMountedBossRider(mount);
                Location liveCenter = mount.getLocation().clone().add(0, 0.12, 0);
                double phase = beat * 0.32;

                spawnWarDrumCore(liveCenter, phase, beat == 3);
                if (liveRider != null) {
                    Location helm = liveRider.getLocation().clone().add(0, liveRider.getHeight() * 0.7, 0);
                    spawnParticle(helm.getWorld(), Particle.DUST, helm, 12, 0.18, 0.18, 0.18, 0.0, ABOMINATION_ORANGE);
                    spawnParticle(helm.getWorld(), Particle.CRIT, helm, 8, 0.15, 0.12, 0.15, 0.02);
                }

                switch (beat) {
                    case 0 -> {
                        spawnWarDrumRing(liveCenter, innerRadius, phase, true);
                        liveCenter.getWorld().playSound(liveCenter, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.5f, 0.6f);
                        affectWarDrumPlayers(mount, liveCenter, innerRadius, firstDamage,
                                player -> player.setVelocity(player.getVelocity().add(new Vector(0, 0.34, 0))));
                    }
                    case 1 -> {
                        spawnWarDrumRing(liveCenter, middleRadius, -phase, false);
                        spawnWarDrumSpokes(liveCenter, middleRadius, phase + 0.35);
                        liveCenter.getWorld().playSound(liveCenter, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.5f, 0.72f);
                        affectWarDrumPlayers(mount, liveCenter, middleRadius, secondDamage, player -> {
                        });
                    }
                    case 2 -> {
                        spawnWarDrumRing(liveCenter, outerRadius, phase, true);
                        spawnWarDrumSpokes(liveCenter, outerRadius, phase + 0.55);
                        liveCenter.getWorld().playSound(liveCenter, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.5f, 0.84f);
                        affectWarDrumPlayers(mount, liveCenter, outerRadius, thirdDamage, player -> {
                        });
                    }
                    case 3 -> {
                        spawnWarDrumRing(liveCenter, innerRadius, phase, true);
                        spawnWarDrumRing(liveCenter, middleRadius, -phase * 1.15, false);
                        spawnWarDrumRing(liveCenter, outerRadius, phase * 1.3, true);
                        spawnWarDrumSpokes(liveCenter, outerRadius, phase + 0.9);
                        liveCenter.getWorld().playSound(liveCenter, Sound.ENTITY_ZOGLIN_ATTACK, 1.05f, 0.75f);
                        liveCenter.getWorld().playSound(liveCenter, Sound.ENTITY_GENERIC_EXPLODE, 0.65f, 1.25f);

                        affectWarDrumPlayers(mount, liveCenter, outerRadius, finaleDamage, player -> {
                            Vector push = player.getLocation().toVector().subtract(liveCenter.toVector());
                            if (push.lengthSquared() > 0.0001) {
                                player.setVelocity(push.normalize().multiply(finaleKnockback).setY(0.22));
                            }
                        });
                    }
                    default -> {
                    }
                }

                beat++;
            }
        }.runTaskTimer(plugin, 0L, beatIntervalTicks);
    }

    private void castWailingTwinToxicSpit(LivingEntity mob) {
        if (!"TOXIC".equals(ensureWailingRole(mob))) return;

        double range = plugin.getConfig().getDouble("combat.abilities.wailing_twin_toxic_spit.range", 38.0);
        int shots = Math.max(1, plugin.getConfig().getInt("combat.abilities.wailing_twin_toxic_spit.shots", 3));
        long shotIntervalTicks = Math.max(1L, plugin.getConfig().getLong("combat.abilities.wailing_twin_toxic_spit.shot_interval_ticks", 7L));
        double speed = plugin.getConfig().getDouble("combat.abilities.wailing_twin_toxic_spit.projectile_speed", 1.05);
        double spread = plugin.getConfig().getDouble("combat.abilities.wailing_twin_toxic_spit.spread", 0.075);

        Player target = findNearestPlayer(mob, range);
        if (target == null) return;

        Location mouth = mob.getEyeLocation().clone();
        mob.getWorld().playSound(mouth, Sound.ENTITY_GHAST_WARN, 0.9f, 1.25f);
        spawnParticle(mob.getWorld(), Particle.DUST, mouth, 12, 0.2, 0.2, 0.2, 0.0, WAILING_TOXIC_GREEN);

        for (int i = 0; i < shots; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!mob.isValid() || mob.isDead() || target.isDead()) return;
                if (target.getLocation().distanceSquared(mob.getLocation()) > (range * range)) return;

                Location launch = mob.getEyeLocation().clone().add(mob.getLocation().getDirection().normalize().multiply(0.65));
                Location targetCenter = target.getLocation().clone().add(0, target.getHeight() * 0.45, 0);
                Vector direction = targetCenter.toVector().subtract(launch.toVector());
                direction.add(new Vector(
                        ThreadLocalRandom.current().nextDouble(-spread, spread),
                        ThreadLocalRandom.current().nextDouble(-spread * 0.6, spread * 0.6),
                        ThreadLocalRandom.current().nextDouble(-spread, spread)
                ));
                if (direction.lengthSquared() <= 0.0001) return;

                Fireball spit = mob.getWorld().spawn(launch, Fireball.class);
                spit.setShooter(mob);
                Vector velocity = direction.normalize().multiply(speed);
                spit.setVelocity(velocity);
                spit.setDirection(velocity);
                spit.setYield(0.0f);
                spit.setIsIncendiary(false);
                spit.getPersistentDataContainer().set(wailingToxicProjectileKey, PersistentDataType.BYTE, (byte) 1);
                startWailingToxicTrail(spit);

                spawnParticle(launch.getWorld(), Particle.DUST, launch, 8, 0.08, 0.08, 0.08, 0.0, WAILING_TOXIC_GREEN);
                spawnParticle(launch.getWorld(), Particle.DUST, launch, 6, 0.07, 0.07, 0.07, 0.0, WAILING_TOXIC_DEEP);
                spawnParticle(launch.getWorld(), Particle.SNEEZE, launch, 4, 0.08, 0.08, 0.08, 0.01);
                launch.getWorld().playSound(launch, Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 0.8f);
            }, i * shotIntervalTicks);
        }
    }

    private void startWailingToxicTrail(Fireball spit) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!spit.isValid() || spit.isDead() || ticks > 100) {
                    cancel();
                    return;
                }

                Location p = spit.getLocation();
                spawnParticle(p.getWorld(), Particle.DUST, p, 5, 0.1, 0.1, 0.1, 0.0, WAILING_TOXIC_GREEN);
                spawnParticle(p.getWorld(), Particle.DUST, p, 3, 0.08, 0.08, 0.08, 0.0, WAILING_TOXIC_DEEP);
                spawnParticle(p.getWorld(), Particle.SNEEZE, p, 2, 0.08, 0.08, 0.08, 0.0);
                spawnParticle(p.getWorld(), Particle.SMOKE, p, 1, 0.05, 0.05, 0.05, 0.0);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void castEmberVolley(LivingEntity mob) {
        double range = plugin.getConfig().getDouble("combat.abilities.ember_volley.range", 24.0);
        int shots = Math.max(1, plugin.getConfig().getInt("combat.abilities.ember_volley.shots", 3));
        long shotIntervalTicks = Math.max(1L, plugin.getConfig().getLong("combat.abilities.ember_volley.shot_interval_ticks", 6L));
        double speed = plugin.getConfig().getDouble("combat.abilities.ember_volley.projectile_speed", 0.85);
        double spread = plugin.getConfig().getDouble("combat.abilities.ember_volley.spread", 0.12);

        Player target = findNearestPlayer(mob, range);
        if (target == null) return;

        Location eye = mob.getEyeLocation();
        mob.getWorld().playSound(eye, Sound.ENTITY_BLAZE_AMBIENT, 0.7f, 0.85f);
        spawnParticle(mob.getWorld(), Particle.FLAME, eye.clone().add(0, 0.1, 0), 18, 0.25, 0.25, 0.25, 0.01);
        spawnParticle(mob.getWorld(), Particle.SMOKE, eye.clone().add(0, 0.1, 0), 10, 0.2, 0.2, 0.2, 0.01);

        for (int i = 0; i < shots; i++) {
            final int shotIndex = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!mob.isValid() || mob.isDead() || target.isDead()) return;
                if (target.getLocation().distanceSquared(mob.getLocation()) > (range * range)) return;

                Location launch = mob.getEyeLocation().clone().add(mob.getLocation().getDirection().normalize().multiply(0.45));
                Location targetCenter = target.getLocation().clone().add(0, target.getHeight() * 0.5, 0);
                Vector direction = targetCenter.toVector().subtract(launch.toVector());
                direction.add(new Vector(
                        ThreadLocalRandom.current().nextDouble(-spread, spread),
                        ThreadLocalRandom.current().nextDouble(-spread * 0.5, spread * 0.5),
                        ThreadLocalRandom.current().nextDouble(-spread, spread)
                ));
                if (direction.lengthSquared() <= 0.0001) return;

                long allowUntilMs = System.currentTimeMillis() + 500L;
                mob.getPersistentDataContainer().set(emberVolleyAllowUntilKey, PersistentDataType.LONG, allowUntilMs);

                SmallFireball fireball = mob.launchProjectile(SmallFireball.class);
                Vector velocity = direction.normalize().multiply(speed);
                fireball.setDirection(velocity);
                fireball.setVelocity(velocity);
                fireball.setYield(0.0f);
                fireball.setIsIncendiary(false);
                fireball.getPersistentDataContainer().set(emberVolleyProjectileKey, PersistentDataType.BYTE, (byte) 1);

                Particle accent = switch (shotIndex % 3) {
                    case 1 -> Particle.SOUL_FIRE_FLAME;
                    case 2 -> Particle.LAVA;
                    default -> Particle.FLAME;
                };

                spawnParticle(launch.getWorld(), accent, launch, 8, 0.08, 0.08, 0.08, 0.01);
                spawnParticle(launch.getWorld(), Particle.SMOKE, launch, 7, 0.1, 0.1, 0.1, 0.01);
                spawnParticle(launch.getWorld(), Particle.DUST, launch, 4, 0.05, 0.05, 0.05, 0.0,
                        shotIndex % 2 == 0 ? EMBER_ORANGE : EMBER_RED);
                launch.getWorld().playSound(launch, Sound.ENTITY_BLAZE_SHOOT, 0.7f, 0.9f + (shotIndex * 0.08f));
            }, i * shotIntervalTicks);
        }
    }

    private void castHeatMark(LivingEntity mob) {
        double range = plugin.getConfig().getDouble("combat.abilities.heat_mark.range", 18.0);
        int durationTicks = Math.max(20, plugin.getConfig().getInt("combat.abilities.heat_mark.duration_ticks", 80));

        Player target = findNearestPlayer(mob, range);
        if (target == null) return;

        long expireAtMs = System.currentTimeMillis() + (durationTicks * 50L);
        target.getPersistentDataContainer().set(heatMarkUntilKey, PersistentDataType.LONG, expireAtMs);

        Location center = target.getLocation().clone().add(0, target.getHeight() * 0.55, 0);
        target.getWorld().playSound(center, Sound.BLOCK_FIRE_AMBIENT, 0.9f, 1.35f);
        target.getWorld().playSound(center, Sound.ENTITY_BLAZE_BURN, 0.8f, 1.15f);
        spawnParticle(target.getWorld(), Particle.FLAME, center, 20, 0.35, 0.45, 0.35, 0.015);
        spawnParticle(target.getWorld(), Particle.SOUL_FIRE_FLAME, center, 12, 0.25, 0.35, 0.25, 0.01);
        spawnParticle(target.getWorld(), Particle.SMOKE, center, 16, 0.3, 0.35, 0.3, 0.01);

        new BukkitRunnable() {
            int lived = 0;

            @Override
            public void run() {
                if (target.isDead() || !target.isValid()) {
                    cancel();
                    return;
                }
                if (lived >= durationTicks) {
                    cancel();
                    return;
                }

                Location orbitCenter = target.getLocation().clone().add(0, target.getHeight() * 0.55, 0);
                double phase = lived * 0.28;
                double radius = 0.75;

                for (int i = 0; i < 2; i++) {
                    double angle = phase + (Math.PI * i);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location p = orbitCenter.clone().add(x, 0.08 * Math.sin(phase * 2.0 + i), z);
                    Particle ringParticle = (lived / 8) % 2 == 0 ? Particle.FLAME : Particle.SOUL_FIRE_FLAME;
                    spawnParticle(target.getWorld(), ringParticle, p, 1, 0.0, 0.0, 0.0, 0.0);
                    spawnParticle(target.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0,
                            i == 0 ? EMBER_ORANGE : EMBER_RED);
                }

                spawnParticle(target.getWorld(), Particle.ASH, orbitCenter, 1, 0.22, 0.18, 0.22, 0.005);
                lived += 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    private void castMagmaBurst(LivingEntity mob) {
        double radius = plugin.getConfig().getDouble("combat.abilities.magma_burst.radius", 5.2);
        double damage = plugin.getConfig().getDouble("combat.abilities.magma_burst.damage", 8.0);
        int fireTicks = plugin.getConfig().getInt("combat.abilities.magma_burst.fire_ticks", 60);
        double knockback = plugin.getConfig().getDouble("combat.abilities.magma_burst.knockback", 0.65);

        Location center = mob.getLocation().clone().add(0, 0.45, 0);
        mob.getWorld().playSound(center, Sound.ENTITY_MAGMA_CUBE_JUMP, 1.0f, 0.8f);
        mob.getWorld().playSound(center, Sound.BLOCK_LAVA_POP, 0.9f, 1.0f);
        spawnParticle(mob.getWorld(), Particle.LAVA, center, 30, 0.45, 0.25, 0.45, 0.02);
        spawnParticle(mob.getWorld(), Particle.FLAME, center, 24, 0.55, 0.3, 0.55, 0.015);
        spawnParticle(mob.getWorld(), Particle.ASH, center, 18, 0.55, 0.2, 0.55, 0.01);

        int ringPoints = Math.max(28, (int) Math.round(radius * 12.0));
        double innerRadius = radius * 0.66;
        double[] layerHeights = new double[]{0.02, 0.34};
        for (int i = 0; i < ringPoints; i++) {
            double angle = (Math.PI * 2.0 * i) / ringPoints;

            for (double layerY : layerHeights) {
                double outerX = Math.cos(angle) * radius;
                double outerZ = Math.sin(angle) * radius;
                Location outer = center.clone().add(outerX, layerY, outerZ);
                spawnParticle(mob.getWorld(), Particle.DUST, outer, 1, 0.0, 0.0, 0.0, 0.0,
                        i % 2 == 0 ? MAGMA_GOLD : MAGMA_RED);
                spawnParticle(mob.getWorld(), Particle.FLAME, outer, 1, 0.01, 0.01, 0.01, 0.0);

                double innerX = Math.cos(angle + 0.12) * innerRadius;
                double innerZ = Math.sin(angle + 0.12) * innerRadius;
                Location inner = center.clone().add(innerX, layerY + 0.03, innerZ);
                spawnParticle(mob.getWorld(), Particle.DUST, inner, 1, 0.0, 0.0, 0.0, 0.0,
                        i % 2 == 0 ? MAGMA_RED : MAGMA_GOLD);
                spawnParticle(mob.getWorld(), Particle.SMOKE, inner, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }

        for (Entity nearby : mob.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof Player player)) continue;
            if (player.isDead()) continue;
            if (player.getLocation().distanceSquared(center) > (radius * radius)) continue;

            player.damage(damage, mob);
            player.setFireTicks(Math.max(player.getFireTicks(), fireTicks));
            spawnAoEHitParticles(player);

            Vector push = player.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() > 0.0001) {
                player.setVelocity(push.normalize().multiply(knockback).setY(0.22));
            }
        }
    }

    private void castLavaSurge(LivingEntity mob) {
        double range = plugin.getConfig().getDouble("combat.abilities.lava_surge.range", 16.0);
        double lungeStrength = plugin.getConfig().getDouble("combat.abilities.lava_surge.lunge_strength", 1.05);
        double lungeY = plugin.getConfig().getDouble("combat.abilities.lava_surge.lunge_y", 0.4);
        long impactDelayTicks = Math.max(4L, plugin.getConfig().getLong("combat.abilities.lava_surge.impact_delay_ticks", 10L));
        double impactRadius = plugin.getConfig().getDouble("combat.abilities.lava_surge.impact_radius", 3.2);
        double impactDamage = plugin.getConfig().getDouble("combat.abilities.lava_surge.impact_damage", 10.0);
        int impactFireTicks = plugin.getConfig().getInt("combat.abilities.lava_surge.impact_fire_ticks", 80);

        Player target = findNearestPlayer(mob, range);
        if (target == null) return;

        Location center = mob.getLocation().clone().add(0, 0.45, 0);
        Vector toward = target.getLocation().toVector().subtract(mob.getLocation().toVector());
        toward.setY(0.0);
        if (toward.lengthSquared() <= 0.0001) return;

        Vector jump = toward.normalize().multiply(lungeStrength).setY(lungeY);
        mob.setVelocity(jump);
        mob.getWorld().playSound(center, Sound.ENTITY_MAGMA_CUBE_SQUISH, 1.0f, 0.7f);
        spawnParticle(mob.getWorld(), Particle.FLAME, center, 14, 0.3, 0.2, 0.3, 0.01);
        spawnParticle(mob.getWorld(), Particle.LAVA, center, 10, 0.25, 0.15, 0.25, 0.01);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!mob.isValid() || mob.isDead()) return;

            Location impact = mob.getLocation().clone().add(0, 0.35, 0);
            mob.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.1f);
            mob.getWorld().playSound(impact, Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.4f);
            spawnParticle(mob.getWorld(), Particle.EXPLOSION, impact, 2, 0.2, 0.1, 0.2, 0.01);
            spawnParticle(mob.getWorld(), Particle.LAVA, impact, 26, 0.45, 0.2, 0.45, 0.02);
            spawnParticle(mob.getWorld(), Particle.FLAME, impact, 22, 0.5, 0.2, 0.5, 0.02);
            spawnParticle(mob.getWorld(), Particle.SMOKE, impact, 16, 0.45, 0.15, 0.45, 0.01);

            for (Entity nearby : mob.getWorld().getNearbyEntities(impact, impactRadius, impactRadius, impactRadius)) {
                if (!(nearby instanceof Player player)) continue;
                if (player.isDead()) continue;
                if (player.getLocation().distanceSquared(impact) > (impactRadius * impactRadius)) continue;

                player.damage(impactDamage, mob);
                player.setFireTicks(Math.max(player.getFireTicks(), impactFireTicks));
                spawnAoEHitParticles(player);
            }
        }, impactDelayTicks);
    }

    private void castHoglinFlamethrower(LivingEntity mob) {
        double range = plugin.getConfig().getDouble("combat.abilities.hoglin_flamethrower.range", 8.0);
        double arcDegrees = plugin.getConfig().getDouble("combat.abilities.hoglin_flamethrower.arc_degrees", 100.0);
        double pulseDamage = plugin.getConfig().getDouble("combat.abilities.hoglin_flamethrower.pulse_damage", 2.2);
        int fireTicks = plugin.getConfig().getInt("combat.abilities.hoglin_flamethrower.fire_ticks", 90);
        int pulses = Math.max(4, plugin.getConfig().getInt("combat.abilities.hoglin_flamethrower.pulses", 10));
        long pulseIntervalTicks = Math.max(1L, plugin.getConfig().getLong("combat.abilities.hoglin_flamethrower.pulse_interval_ticks", 2L));
        int maxHitsPerCast = Math.max(1, plugin.getConfig().getInt("combat.abilities.hoglin_flamethrower.max_hits_per_cast", 4));

        Vector forward = mob.getLocation().getDirection().clone();
        forward.setY(0.0);
        if (forward.lengthSquared() <= 0.0001) return;
        forward.normalize();

        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        Location mouth = mob.getEyeLocation().clone().add(forward.clone().multiply(0.65)).add(0, -0.15, 0);

        mob.getWorld().playSound(mouth, Sound.ENTITY_HOGLIN_ANGRY, 1.0f, 0.85f);
        mob.getWorld().playSound(mouth, Sound.ITEM_FIRECHARGE_USE, 0.95f, 0.75f);

        Map<UUID, Integer> hitCounts = new HashMap<>();

        new BukkitRunnable() {
            int pulse = 0;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead() || pulse >= pulses) {
                    cancel();
                    return;
                }

                double progress = (double) pulse / Math.max(1, pulses - 1);
                double pulseRange = range * (0.55 + (progress * 0.45));
                double halfArcRad = Math.toRadians(arcDegrees * 0.5);

                for (double r = 0.9; r <= pulseRange; r += 0.4) {
                    int samples = Math.max(5, (int) Math.round(6 + (r * 2.2)));
                    for (int i = 0; i <= samples; i++) {
                        double t = (double) i / samples;
                        double angle = (-halfArcRad) + (t * halfArcRad * 2.0);
                        double side = Math.tan(angle) * r;
                        Location p = mouth.clone()
                                .add(forward.clone().multiply(r))
                                .add(right.clone().multiply(side))
                                .add(0, 0.05 + (Math.sin((pulse * 0.7) + r) * 0.04), 0);

                        if ((i + pulse) % 3 == 0) {
                            spawnParticle(p.getWorld(), Particle.SOUL_FIRE_FLAME, p, 1, 0.01, 0.01, 0.01, 0.0);
                        } else {
                            spawnParticle(p.getWorld(), Particle.FLAME, p, 1, 0.01, 0.01, 0.01, 0.0);
                        }
                        spawnParticle(p.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0,
                                (i + pulse) % 2 == 0 ? HOG_FIRE_ORANGE : HOG_FIRE_RED);
                        if ((i + pulse) % 4 == 0) {
                            spawnParticle(p.getWorld(), Particle.SMOKE, p, 1, 0.02, 0.02, 0.02, 0.0);
                        }

                        for (Entity nearby : p.getWorld().getNearbyEntities(p, 0.65, 0.9, 0.65)) {
                            if (!(nearby instanceof Player player)) continue;
                            if (player.isDead()) continue;

                            int hits = hitCounts.getOrDefault(player.getUniqueId(), 0);
                            if (hits >= maxHitsPerCast) continue;

                            hitCounts.put(player.getUniqueId(), hits + 1);
                            player.damage(pulseDamage, mob);
                            player.setFireTicks(Math.max(player.getFireTicks(), fireTicks));
                            spawnAoEHitParticles(player);
                        }
                    }
                }

                if (pulse % 2 == 0) {
                    mouth.getWorld().playSound(mouth, Sound.BLOCK_FIRE_AMBIENT, 0.7f, 1.15f);
                }
                pulse++;
            }
        }.runTaskTimer(plugin, 0L, pulseIntervalTicks);
    }

    private void castFireGeysers(LivingEntity mob) {
        LivingEntity mount = resolveMountedBossMount(mob);
        if (mount == null) return;

        double fieldRadius = plugin.getConfig().getDouble("combat.abilities.fire_geysers.field_radius", 8.5);
        int pulses = Math.max(1, plugin.getConfig().getInt("combat.abilities.fire_geysers.pulses", 4));
        int geysersPerPulse = Math.max(1, plugin.getConfig().getInt("combat.abilities.fire_geysers.geysers_per_pulse", 7));
        long pulseInterval = Math.max(2L, plugin.getConfig().getLong("combat.abilities.fire_geysers.pulse_interval_ticks", 8L));
        double columnHeight = plugin.getConfig().getDouble("combat.abilities.fire_geysers.column_height", 3.6);
        double hitRadius = plugin.getConfig().getDouble("combat.abilities.fire_geysers.hit_radius", 1.0);
        double hitDamage = plugin.getConfig().getDouble("combat.abilities.fire_geysers.hit_damage", 8.0);
        int fireTicks = plugin.getConfig().getInt("combat.abilities.fire_geysers.fire_ticks", 90);

        Location center = mount.getLocation().clone();
        center.getWorld().playSound(center, Sound.BLOCK_LAVA_POP, 1.0f, 0.7f);
        center.getWorld().playSound(center, Sound.ENTITY_STRIDER_HAPPY, 0.9f, 0.55f);

        Map<UUID, Long> hitCooldownUntil = new HashMap<>();

        new BukkitRunnable() {
            int pulse = 0;

            @Override
            public void run() {
                if (!mount.isValid() || mount.isDead() || pulse >= pulses) {
                    cancel();
                    return;
                }

                Location liveCenter = mount.getLocation().clone();
                for (int i = 0; i < geysersPerPulse; i++) {
                    double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
                    double distance = ThreadLocalRandom.current().nextDouble(1.0, fieldRadius);
                    Location base = liveCenter.clone().add(Math.cos(angle) * distance, 0.0, Math.sin(angle) * distance);
                    spawnFireGeyserColumn(base, columnHeight, pulse * 0.2);

                    for (Entity nearby : base.getWorld().getNearbyEntities(base, hitRadius, columnHeight + 0.8, hitRadius)) {
                        if (!(nearby instanceof Player player)) continue;
                        if (player.isDead()) continue;
                        if (player.getLocation().distanceSquared(base) > (hitRadius * hitRadius)) continue;

                        long now = System.currentTimeMillis();
                        long lock = hitCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
                        if (now < lock) continue;

                        hitCooldownUntil.put(player.getUniqueId(), now + 500L);
                        player.damage(hitDamage, mount);
                        player.setFireTicks(Math.max(player.getFireTicks(), fireTicks));
                        spawnAoEHitParticles(player);
                    }
                }

                pulse++;
            }
        }.runTaskTimer(plugin, 0L, pulseInterval);
    }

    private void spawnFireGeyserColumn(Location base, double height, double phase) {
        for (double y = 0.12; y <= height; y += 0.28) {
            Location p = base.clone().add(
                    Math.sin((y * 1.9) + phase) * 0.08,
                    y,
                    Math.cos((y * 1.6) + phase) * 0.08
            );
            spawnParticle(p.getWorld(), Particle.FLAME, p, 5, 0.11, 0.08, 0.11, 0.01);
            spawnParticle(p.getWorld(), Particle.LAVA, p, 3, 0.08, 0.05, 0.08, 0.0);
            spawnParticle(p.getWorld(), Particle.DUST, p, 4, 0.1, 0.08, 0.1, 0.0,
                    ThreadLocalRandom.current().nextBoolean() ? ABOMINATION_ORANGE : HOG_FIRE_RED);
            if (((int) (y * 10)) % 3 == 0) {
                spawnParticle(p.getWorld(), Particle.CAMPFIRE_COSY_SMOKE, p, 1, 0.05, 0.06, 0.05, 0.0);
            }
        }

        Location cap = base.clone().add(0, height, 0);
        spawnParticle(cap.getWorld(), Particle.FLAME, cap, 14, 0.24, 0.10, 0.24, 0.02);
        spawnParticle(cap.getWorld(), Particle.LAVA, cap, 6, 0.16, 0.08, 0.16, 0.0);
    }

    private void castStriderLavaSpew(LivingEntity mob) {
        LivingEntity mount = resolveMountedBossMount(mob);
        if (mount == null) return;

        double range = plugin.getConfig().getDouble("combat.abilities.strider_lava_spew.range", 14.0);
        double arcDegrees = plugin.getConfig().getDouble("combat.abilities.strider_lava_spew.arc_degrees", 120.0);
        double pulseDamage = plugin.getConfig().getDouble("combat.abilities.strider_lava_spew.pulse_damage", 4.5);
        int fireTicks = plugin.getConfig().getInt("combat.abilities.strider_lava_spew.fire_ticks", 80);
        int pulses = Math.max(5, plugin.getConfig().getInt("combat.abilities.strider_lava_spew.pulses", 14));
        long pulseIntervalTicks = Math.max(1L, plugin.getConfig().getLong("combat.abilities.strider_lava_spew.pulse_interval_ticks", 2L));
        int maxHitsPerCast = Math.max(1, plugin.getConfig().getInt("combat.abilities.strider_lava_spew.max_hits_per_cast", 5));

        Player target = findNearestPlayer(mount, range + 10.0);
        Vector forward = target != null
                ? target.getLocation().toVector().subtract(mount.getLocation().toVector())
                : mount.getLocation().getDirection().clone();
        forward.setY(0.0);
        if (forward.lengthSquared() <= 0.0001) return;
        forward.normalize();

        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        Location mouth = mount.getEyeLocation().clone().add(forward.clone().multiply(0.6)).add(0, -0.4, 0);

        mount.getWorld().playSound(mouth, Sound.ENTITY_STRIDER_HAPPY, 1.0f, 0.45f);
        mount.getWorld().playSound(mouth, Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.7f);

        Map<UUID, Integer> hitCounts = new HashMap<>();

        new BukkitRunnable() {
            int pulse = 0;

            @Override
            public void run() {
                if (!mount.isValid() || mount.isDead() || pulse >= pulses) {
                    cancel();
                    return;
                }

                double progress = (double) pulse / Math.max(1, pulses - 1);
                double pulseRange = range * (0.45 + (progress * 0.55));
                double halfArcRad = Math.toRadians(arcDegrees * 0.5);

                for (double r = 0.7; r <= pulseRange; r += 0.28) {
                    int samples = Math.max(8, (int) Math.round(10 + (r * 3.0)));
                    for (int i = 0; i <= samples; i++) {
                        double t = (double) i / samples;
                        double angle = (-halfArcRad) + (t * halfArcRad * 2.0);
                        double side = Math.tan(angle) * r;
                        Location p = mouth.clone()
                                .add(forward.clone().multiply(r))
                                .add(right.clone().multiply(side))
                                .add(0, -0.06 + (Math.sin((pulse * 0.55) + (r * 1.1)) * 0.05), 0);

                        spawnParticle(p.getWorld(), Particle.FLAME, p, 2, 0.015, 0.015, 0.015, 0.0);
                        spawnParticle(p.getWorld(), Particle.LAVA, p, 1, 0.02, 0.02, 0.02, 0.0);
                        spawnParticle(p.getWorld(), Particle.DUST, p, 2, 0.0, 0.0, 0.0, 0.0,
                                (i + pulse) % 2 == 0 ? HOG_FIRE_ORANGE : MAGMA_RED);
                        if ((i + pulse) % 3 == 0) {
                            spawnParticle(p.getWorld(), Particle.SMOKE, p, 1, 0.02, 0.02, 0.02, 0.0);
                        }

                        for (Entity nearby : p.getWorld().getNearbyEntities(p, 0.6, 0.9, 0.6)) {
                            if (!(nearby instanceof Player player)) continue;
                            if (player.isDead()) continue;

                            int hits = hitCounts.getOrDefault(player.getUniqueId(), 0);
                            if (hits >= maxHitsPerCast) continue;

                            hitCounts.put(player.getUniqueId(), hits + 1);
                            player.damage(pulseDamage, mount);
                            player.setFireTicks(Math.max(player.getFireTicks(), fireTicks));
                            spawnAoEHitParticles(player);
                        }
                    }
                }

                if (pulse % 2 == 0) {
                    mouth.getWorld().playSound(mouth, Sound.BLOCK_LAVA_AMBIENT, 0.7f, 1.2f);
                }
                pulse++;
            }
        }.runTaskTimer(plugin, 0L, pulseIntervalTicks);
    }

    private void castNetherRend(LivingEntity mob) {
        LivingEntity rider = resolveMountedBossRider(resolveMountedBossMount(mob));
        LivingEntity source = rider != null ? rider : mob;

        double range = plugin.getConfig().getDouble("combat.abilities.nether_rend.range", 18.0);
        double slashLength = plugin.getConfig().getDouble("combat.abilities.nether_rend.slash_length", 7.0);
        long chargeTicks = Math.max(8L, plugin.getConfig().getLong("combat.abilities.nether_rend.charge_ticks", 18L));
        double hitRadius = plugin.getConfig().getDouble("combat.abilities.nether_rend.hit_radius", 0.95);
        double hitDamage = plugin.getConfig().getDouble("combat.abilities.nether_rend.hit_damage", 11.0);
        int fireTicks = plugin.getConfig().getInt("combat.abilities.nether_rend.fire_ticks", 80);

        Player target = findNearestPlayer(source, range);
        if (target == null) return;

        Location anchor = target.getLocation().clone();
        source.getWorld().playSound(source.getLocation(), Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.0f, 0.7f);
        source.getWorld().playSound(source.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.9f, 0.75f);

        new BukkitRunnable() {
            long lived = 0L;

            @Override
            public void run() {
                if (!source.isValid() || source.isDead()) {
                    cancel();
                    return;
                }

                Location c = anchor.clone().add(0, 0.08, 0);
                if (lived < chargeTicks) {
                    spawnNetherRendSigil(c, slashLength * 0.55, lived * 0.12);
                    spawnParticle(c.getWorld(), Particle.ASH, c, 8, 0.45, 0.08, 0.45, 0.01);
                    lived += 2L;
                    return;
                }

                Set<UUID> hit = new HashSet<>();
                double[] angles = new double[] { Math.PI / 4.0, -Math.PI / 4.0 };
                for (double angle : angles) {
                    Vector dir = new Vector(Math.cos(angle), 0, Math.sin(angle));
                    for (double d = 0.3; d <= slashLength; d += 0.3) {
                        Location p = c.clone().add(dir.clone().multiply(d));
                        spawnParticle(p.getWorld(), Particle.DUST, p, 3, 0.04, 0.04, 0.04, 0.0,
                                ((int) (d * 10)) % 2 == 0 ? ABOMINATION_ORANGE : BLOOD_CRIMSON);
                        spawnParticle(p.getWorld(), Particle.FLAME, p, 1, 0.03, 0.03, 0.03, 0.0);
                        spawnParticle(p.getWorld(), Particle.ASH, p, 1, 0.02, 0.02, 0.02, 0.0);

                        for (Entity nearby : p.getWorld().getNearbyEntities(p, hitRadius, 1.2, hitRadius)) {
                            if (!(nearby instanceof Player player)) continue;
                            if (player.isDead()) continue;
                            if (!hit.add(player.getUniqueId())) continue;

                            player.damage(hitDamage, source);
                            player.setFireTicks(Math.max(player.getFireTicks(), fireTicks));
                            spawnAoEHitParticles(player);
                        }
                    }
                }

                c.getWorld().playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.65f);
                c.getWorld().playSound(c, Sound.ENTITY_BLAZE_SHOOT, 0.85f, 0.8f);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void castWitherLances(LivingEntity mob) {
        double range = plugin.getConfig().getDouble("combat.abilities.wither_lances.range", 24.0);
        int lances = Math.max(3, plugin.getConfig().getInt("combat.abilities.wither_lances.lances", 6));
        double spread = plugin.getConfig().getDouble("combat.abilities.wither_lances.spread", 6.0);
        long telegraphTicks = Math.max(8L, plugin.getConfig().getLong("combat.abilities.wither_lances.telegraph_ticks", 18L));
        double hitRadius = plugin.getConfig().getDouble("combat.abilities.wither_lances.hit_radius", 1.2);
        double hitDamage = plugin.getConfig().getDouble("combat.abilities.wither_lances.hit_damage", 12.0);
        int witherTicks = plugin.getConfig().getInt("combat.abilities.wither_lances.wither_ticks", 80);

        Player target = findNearestPlayer(mob, range);
        if (target == null) return;

        Location center = target.getLocation().clone();
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.65f);
        mob.getWorld().playSound(mob.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.7f, 0.6f);

        for (int i = 0; i < lances; i++) {
            double ox = ThreadLocalRandom.current().nextDouble(-spread, spread);
            double oz = ThreadLocalRandom.current().nextDouble(-spread, spread);
            Location strike = center.clone().add(ox, 0, oz);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!mob.isValid() || mob.isDead()) return;

                new BukkitRunnable() {
                    long lived = 0L;

                    @Override
                    public void run() {
                        if (!mob.isValid() || mob.isDead()) {
                            cancel();
                            return;
                        }

                        Location base = strike.clone();
                        if (lived < telegraphTicks) {
                            spawnWitherLanceTelegraph(base, hitRadius * 1.8, lived * 0.14);
                            lived += 2L;
                            return;
                        }

                        for (double y = 0.2; y <= 5.6; y += 0.25) {
                            Location p = base.clone().add(0, y, 0);
                            spawnParticle(p.getWorld(), Particle.DUST, p, 5, 0.08, 0.08, 0.08, 0.0,
                                    y < 2.6 ? WITHER_VIOLET : SOUL_BLACK);
                            spawnParticle(p.getWorld(), Particle.SOUL, p, 2, 0.06, 0.06, 0.06, 0.0);
                            if (((int) (y * 10)) % 3 == 0) {
                                spawnParticle(p.getWorld(), Particle.SMOKE, p, 1, 0.03, 0.05, 0.03, 0.0);
                            }
                        }

                        base.getWorld().playSound(base, Sound.ENTITY_WITHER_SHOOT, 0.9f, 0.55f);
                        base.getWorld().playSound(base, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.55f, 1.45f);

                        for (Entity nearby : base.getWorld().getNearbyEntities(base, hitRadius, 6.0, hitRadius)) {
                            if (!(nearby instanceof Player player)) continue;
                            if (player.isDead()) continue;
                            if (player.getLocation().distanceSquared(base) > (hitRadius * hitRadius)) continue;

                            player.damage(hitDamage, mob);
                            player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, witherTicks, 1));
                            spawnAoEHitParticles(player);
                        }

                        cancel();
                    }
                }.runTaskTimer(plugin, 0L, 2L);
            }, i * 4L);
        }
    }

    private void spawnWitherLanceTelegraph(Location center, double radius, double phase) {
        int points = 22;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i / points) + phase;
            Location p = center.clone().add(Math.cos(angle) * radius, 0.08, Math.sin(angle) * radius);
            spawnParticle(center.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0,
                    i % 2 == 0 ? WITHER_VIOLET : SOUL_BLACK);
        }
        spawnParticle(center.getWorld(), Particle.ASH, center.clone().add(0, 0.12, 0), 8, 0.28, 0.02, 0.28, 0.01);
    }

    private void castAbyssalNova(LivingEntity mob) {
        double radius = plugin.getConfig().getDouble("combat.abilities.abyssal_nova.radius", 7.5);
        long chargeTicks = Math.max(10L, plugin.getConfig().getLong("combat.abilities.abyssal_nova.charge_ticks", 26L));
        double damage = plugin.getConfig().getDouble("combat.abilities.abyssal_nova.damage", 18.0);
        int witherTicks = plugin.getConfig().getInt("combat.abilities.abyssal_nova.wither_ticks", 100);
        double knockback = plugin.getConfig().getDouble("combat.abilities.abyssal_nova.knockback", 0.75);

        Location start = mob.getLocation().clone().add(0, 0.4, 0);
        mob.getWorld().playSound(start, Sound.ENTITY_WITHER_SPAWN, 0.65f, 1.35f);
        mob.getWorld().playSound(start, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 0.65f);

        new BukkitRunnable() {
            long lived = 0L;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead()) {
                    cancel();
                    return;
                }

                Location c = mob.getLocation().clone().add(0, 0.45, 0);
                if (lived < chargeTicks) {
                    spawnDarkRitualCircle(c, radius * 0.45, lived * 0.18);
                    spawnDarkRitualCircle(c, radius * 0.72, -lived * 0.14);
                    spawnAbyssalNovaHalo(c, radius, lived * 0.12);
                    spawnParticle(c.getWorld(), Particle.SCULK_SOUL, c, 18, 0.45, 0.2, 0.45, 0.02);
                    spawnParticle(c.getWorld(), Particle.REVERSE_PORTAL, c, 10, 0.28, 0.12, 0.28, 0.0);
                    lived += 2L;
                    return;
                }

                spawnParticle(c.getWorld(), Particle.EXPLOSION_EMITTER, c, 1);
                spawnParticle(c.getWorld(), Particle.DUST, c, 48, 1.1, 0.45, 1.1, 0.0, WITHER_VIOLET);
                spawnParticle(c.getWorld(), Particle.DUST, c, 36, 0.95, 0.35, 0.95, 0.0, SOUL_BLACK);
                spawnParticle(c.getWorld(), Particle.SCULK_SOUL, c, 24, 0.9, 0.3, 0.9, 0.02);
                c.getWorld().playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 0.7f);
                c.getWorld().playSound(c, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.0f, 0.75f);

                for (Entity nearby : c.getWorld().getNearbyEntities(c, radius, radius, radius)) {
                    if (!(nearby instanceof Player player)) continue;
                    if (player.isDead()) continue;
                    if (player.getLocation().distanceSquared(c) > (radius * radius)) continue;

                    player.damage(damage, mob);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, witherTicks, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
                    spawnAoEHitParticles(player);

                    Vector push = player.getLocation().toVector().subtract(c.toVector());
                    if (push.lengthSquared() > 0.0001) {
                        player.setVelocity(push.normalize().multiply(knockback).setY(0.18));
                    }
                }

                cancel();
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void spawnAbyssalNovaHalo(Location center, double radius, double phase) {
        int points = 30;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i / points) + phase;
            Location p = center.clone().add(
                    Math.cos(angle) * radius,
                    0.25 + Math.sin(angle * 2.0 + phase) * 0.14,
                    Math.sin(angle) * radius
            );
            spawnParticle(center.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0,
                    i % 2 == 0 ? WITHER_PURPLE : SOUL_BLACK);
            if (i % 3 == 0) {
                spawnParticle(center.getWorld(), Particle.REVERSE_PORTAL, p, 1, 0.01, 0.01, 0.01, 0.0);
            }
        }
    }

    private void castTorsoTentacles(LivingEntity mob) {
        double range = plugin.getConfig().getDouble("combat.abilities.torso_tentacles.range", 16.0);
        int shots = Math.max(3, plugin.getConfig().getInt("combat.abilities.torso_tentacles.shots", 6));
        long shotIntervalTicks = Math.max(1L, plugin.getConfig().getLong("combat.abilities.torso_tentacles.shot_interval_ticks", 4L));
        int durationTicks = Math.max(12, plugin.getConfig().getInt("combat.abilities.torso_tentacles.duration_ticks", 22));
        double speed = plugin.getConfig().getDouble("combat.abilities.torso_tentacles.speed", 0.62);
        double turnStrength = plugin.getConfig().getDouble("combat.abilities.torso_tentacles.turn_strength", 0.28);
        double hitRadius = plugin.getConfig().getDouble("combat.abilities.torso_tentacles.hit_radius", 0.9);
        double hitDamage = plugin.getConfig().getDouble("combat.abilities.torso_tentacles.hit_damage", 4.0);
        double grazeDamage = plugin.getConfig().getDouble("combat.abilities.torso_tentacles.graze_damage", 1.2);

        Player target = findNearestPlayer(mob, range);
        if (target == null) return;

        Location torso = mob.getLocation().clone().add(0, mob.getHeight() * 0.42, 0);
        mob.getWorld().playSound(torso, Sound.ENTITY_WITHER_AMBIENT, 0.85f, 0.8f);
        mob.getWorld().playSound(torso, Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.8f, 0.65f);
        spawnParticle(mob.getWorld(), Particle.DUST, torso, 20, 0.35, 0.22, 0.35, 0.0, WITHER_VIOLET);
        spawnParticle(mob.getWorld(), Particle.REVERSE_PORTAL, torso, 10, 0.18, 0.12, 0.18, 0.0);

        for (int i = 0; i < shots; i++) {
            final int shotIndex = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!mob.isValid() || mob.isDead()) return;
                double normalized = shots == 1 ? 0.0 : ((shotIndex / (double) (shots - 1)) - 0.5);
                launchTorsoTentacle(mob, target.getUniqueId(), range, durationTicks, speed, turnStrength, hitRadius,
                        hitDamage, grazeDamage, normalized);
            }, i * shotIntervalTicks);
        }
    }

    private void launchTorsoTentacle(
            LivingEntity mob,
            UUID targetId,
            double range,
            int durationTicks,
            double speed,
            double turnStrength,
            double hitRadius,
            double hitDamage,
            double grazeDamage,
            double lateralBias
    ) {
        Location origin = mob.getLocation().clone().add(0, mob.getHeight() * 0.42, 0);
        Player firstTarget = Bukkit.getPlayer(targetId);
        if (firstTarget == null || !firstTarget.isValid() || firstTarget.isDead() || !firstTarget.getWorld().equals(mob.getWorld())) {
            firstTarget = findNearestPlayer(mob, range);
            if (firstTarget == null) return;
        }

        Vector facing = firstTarget.getLocation().clone().add(0, firstTarget.getHeight() * 0.5, 0).toVector().subtract(origin.toVector());
        if (facing.lengthSquared() <= 0.0001) return;
        facing.normalize();

        Vector right = new Vector(-facing.getZ(), 0, facing.getX());
        Vector spreadBias = right.clone().multiply(lateralBias);
        origin.add(spreadBias.clone().multiply(0.32));

        final Vector[] missilePos = {origin.toVector()};
        final Vector[] missileVel = {facing.clone().add(spreadBias.clone().multiply(0.12)).normalize().multiply(speed)};
        final Set<UUID> grazed = new HashSet<>();
        final Particle.DustOptions coreDark = new Particle.DustOptions(Color.fromRGB(62, 40, 102), 2.45f);
        final Particle.DustOptions shellDark = new Particle.DustOptions(Color.fromRGB(18, 20, 26), 2.15f);
        final Particle.DustOptions auraDark = new Particle.DustOptions(Color.fromRGB(94, 72, 134), 1.95f);
        final org.bukkit.block.data.BlockData shadowDust = Material.BLACK_CONCRETE_POWDER.createBlockData();
        final double homingBlend = Math.max(0.08, Math.min(0.92, turnStrength));

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead() || step >= durationTicks) {
                    cancel();
                    return;
                }

                Player tracked = Bukkit.getPlayer(targetId);
                if (tracked == null || !tracked.isValid() || tracked.isDead() || !tracked.getWorld().equals(mob.getWorld())) {
                    tracked = findNearestPlayer(mob, range);
                }
                if (tracked == null) {
                    cancel();
                    return;
                }

                Location targetCenter = tracked.getLocation().clone().add(0, tracked.getHeight() * 0.55, 0);
                Vector desired = targetCenter.toVector().subtract(missilePos[0]);
                if (desired.lengthSquared() <= 0.0001) {
                    cancel();
                    return;
                }

                desired = desired.normalize().multiply(speed).add(spreadBias.clone().multiply(0.025));
                missileVel[0] = missileVel[0].multiply(1.0 - homingBlend).add(desired.multiply(homingBlend));
                missilePos[0] = missilePos[0].clone().add(missileVel[0]);

                Location point = new Location(mob.getWorld(), missilePos[0].getX(), missilePos[0].getY(), missilePos[0].getZ());
                if (point.distanceSquared(origin) > (range * range)) {
                    cancel();
                    return;
                }

                double angle = (step * 0.66) + (lateralBias * 2.1);
                double outer = 0.34 + (Math.sin(step * 0.2 + (lateralBias * 1.7)) * 0.05);
                double inner = outer * 0.64;

                double ox = Math.cos(angle) * outer;
                double oz = Math.sin(angle) * outer;
                double oy = Math.sin(angle * 1.8) * 0.08;

                double ix = Math.cos(-angle * 1.34) * inner;
                double iz = Math.sin(-angle * 1.34) * inner;
                double iy = Math.sin(angle * 2.2 + 1.0) * 0.06;

                spawnParticle(point.getWorld(), Particle.FALLING_DUST, point, 16, 0.11, 0.11, 0.11, 0.0, shadowDust);
                spawnParticle(point.getWorld(), Particle.DUST, point, 10, 0.09, 0.09, 0.09, 0.0, coreDark);
                spawnParticle(point.getWorld(), Particle.DUST, point, 8, 0.08, 0.08, 0.08, 0.0, shellDark);
                spawnParticle(point.getWorld(), Particle.DUST, point.clone().add(ox, oy, oz), 4, 0.02, 0.02, 0.02, 0.0, auraDark);
                spawnParticle(point.getWorld(), Particle.DUST, point.clone().add(ix, iy, iz), 4, 0.02, 0.02, 0.02, 0.0, WITHER_PURPLE);
                spawnParticle(point.getWorld(), Particle.REVERSE_PORTAL, point, 4, 0.05, 0.05, 0.05, 0.0);
                spawnParticle(point.getWorld(), Particle.SMOKE, point, 5, 0.07, 0.07, 0.07, 0.01);

                for (Entity nearby : point.getWorld().getNearbyEntities(point, hitRadius, hitRadius, hitRadius)) {
                    if (!(nearby instanceof Player player)) continue;
                    if (player.isDead()) continue;

                    player.damage(hitDamage, mob);
                    spawnAoEHitParticles(player);
                    spawnParticle(point.getWorld(), Particle.EXPLOSION, point, 4, 0.16, 0.16, 0.16, 0.01);
                    point.getWorld().playSound(point, Sound.ENTITY_WITHER_SHOOT, 0.55f, 0.75f);
                    cancel();
                    return;
                }

                for (Entity nearby : point.getWorld().getNearbyEntities(point, hitRadius * 1.6, 1.1, hitRadius * 1.6)) {
                    if (!(nearby instanceof Player player)) continue;
                    if (player.isDead()) continue;
                    if (!grazed.add(player.getUniqueId())) continue;
                    player.damage(grazeDamage, mob);
                }

                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnNetherRendSigil(Location center, double radius, double phase) {
        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2.0 * i / 24.0) + phase;
            Location p = center.clone().add(Math.cos(angle) * radius, 0.06, Math.sin(angle) * radius);
            spawnParticle(center.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0,
                    i % 2 == 0 ? ABOMINATION_ORANGE : ABOMINATION_DEEP);
        }

        Vector diagA = new Vector(1, 0, 1).normalize();
        Vector diagB = new Vector(1, 0, -1).normalize();
        for (double d = -radius; d <= radius; d += 0.25) {
            Location a = center.clone().add(diagA.clone().multiply(d)).add(0, 0.05, 0);
            Location b = center.clone().add(diagB.clone().multiply(d)).add(0, 0.05, 0);
            spawnParticle(center.getWorld(), Particle.DUST, a, 1, 0.0, 0.0, 0.0, 0.0, BLOOD_CRIMSON);
            spawnParticle(center.getWorld(), Particle.DUST, b, 1, 0.0, 0.0, 0.0, 0.0, ABOMINATION_ORANGE);
        }
    }

    private void castBruteTuskSweep(LivingEntity mob) {
        double range = plugin.getConfig().getDouble("combat.abilities.brute_tusk_sweep.range", 4.8);
        double arcDegrees = plugin.getConfig().getDouble("combat.abilities.brute_tusk_sweep.arc_degrees", 110.0);
        double damage = plugin.getConfig().getDouble("combat.abilities.brute_tusk_sweep.damage", 12.0);
        double knockback = plugin.getConfig().getDouble("combat.abilities.brute_tusk_sweep.knockback", 0.95);

        Vector forward = mob.getLocation().getDirection().clone();
        forward.setY(0.0);
        if (forward.lengthSquared() <= 0.0001) return;
        forward.normalize();

        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        Location center = mob.getLocation().clone().add(0, 0.75, 0);

        mob.getWorld().playSound(center, Sound.ENTITY_HOGLIN_ATTACK, 1.0f, 0.75f);
        mob.getWorld().playSound(center, Sound.ENTITY_ZOGLIN_ANGRY, 0.8f, 1.15f);

        int points = 34;
        double halfArcRad = Math.toRadians(arcDegrees * 0.5);
        Set<UUID> hit = new HashSet<>();

        for (int i = 0; i < points; i++) {
            double t = (double) i / (points - 1);
            double angle = (-halfArcRad) + (t * halfArcRad * 2.0);
            for (double r = 1.0; r <= range; r += 0.45) {
                double side = Math.tan(angle) * r;
                Location p = center.clone().add(forward.clone().multiply(r)).add(right.clone().multiply(side));
                spawnParticle(p.getWorld(), Particle.FLAME, p, 1, 0.01, 0.01, 0.01, 0.0);
                if (i % 2 == 0) {
                    spawnParticle(p.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, HOG_FIRE_RED);
                }

                for (Entity nearby : p.getWorld().getNearbyEntities(p, 0.6, 0.9, 0.6)) {
                    if (!(nearby instanceof Player player)) continue;
                    if (player.isDead()) continue;
                    if (!hit.add(player.getUniqueId())) continue;

                    player.damage(damage, mob);
                    spawnAoEHitParticles(player);

                    Vector push = player.getLocation().toVector().subtract(center.toVector());
                    if (push.lengthSquared() > 0.0001) {
                        player.setVelocity(push.normalize().multiply(knockback).setY(0.24));
                    }
                }
            }
        }
    }

    private void castMagmaEyeLasers(LivingEntity mob) {
        double range = plugin.getConfig().getDouble("combat.abilities.magma_eye_lasers.range", 28.0);
        int volleys = Math.max(1, plugin.getConfig().getInt("combat.abilities.magma_eye_lasers.volleys", 5));
        long volleyIntervalTicks = Math.max(1L, plugin.getConfig().getLong("combat.abilities.magma_eye_lasers.volley_interval_ticks", 4L));
        double projectileSpeed = plugin.getConfig().getDouble("combat.abilities.magma_eye_lasers.projectile_speed", 1.65);
        double spread = plugin.getConfig().getDouble("combat.abilities.magma_eye_lasers.spread", 0.035);

        Player target = findNearestPlayer(mob, range);
        if (target == null) return;

        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 0.65f);
        mob.getWorld().playSound(mob.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.75f, 1.65f);

        for (int volley = 0; volley < volleys; volley++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!mob.isValid() || mob.isDead() || target.isDead()) return;
                if (target.getLocation().distanceSquared(mob.getLocation()) > (range * range)) return;

                Vector facing = target.getLocation().toVector().subtract(mob.getEyeLocation().toVector());
                facing.setY(0.0);
                if (facing.lengthSquared() <= 0.0001) return;
                facing.normalize();

                Vector right = new Vector(-facing.getZ(), 0, facing.getX());
                Location eyeBase = mob.getEyeLocation().clone().add(0, -0.1, 0);
                Location leftEye = eyeBase.clone().add(right.clone().multiply(-0.28));
                Location rightEye = eyeBase.clone().add(right.clone().multiply(0.28));

                fireMagmaLaserShot(mob, target, leftEye, projectileSpeed, spread);
                fireMagmaLaserShot(mob, target, rightEye, projectileSpeed, spread);

                mob.getWorld().playSound(eyeBase, Sound.ENTITY_BLAZE_SHOOT, 0.65f, 1.2f);
                spawnParticle(mob.getWorld(), Particle.LAVA, eyeBase, 10, 0.2, 0.06, 0.2, 0.01);
            }, volley * volleyIntervalTicks);
        }
    }

    private void fireMagmaLaserShot(LivingEntity mob, Player target, Location from, double speed, double spread) {
        Location targetLoc = target.getLocation().clone().add(0, target.getHeight() * 0.55, 0);
        Vector direction = targetLoc.toVector().subtract(from.toVector());
        direction.add(new Vector(
                ThreadLocalRandom.current().nextDouble(-spread, spread),
                ThreadLocalRandom.current().nextDouble(-spread * 0.5, spread * 0.5),
                ThreadLocalRandom.current().nextDouble(-spread, spread)
        ));
        if (direction.lengthSquared() <= 0.0001) return;

        SmallFireball fireball = mob.launchProjectile(SmallFireball.class);
        fireball.teleport(from);
        Vector velocity = direction.normalize().multiply(speed);
        fireball.setDirection(velocity);
        fireball.setVelocity(velocity);
        fireball.setYield(0.0f);
        fireball.setIsIncendiary(false);

        spawnParticle(from.getWorld(), Particle.DUST, from, 8, 0.06, 0.06, 0.06, 0.0, MAGMA_LASER_GOLD);
        spawnParticle(from.getWorld(), Particle.DUST, from, 6, 0.05, 0.05, 0.05, 0.0, MAGMA_LASER_RED);
        spawnParticle(from.getWorld(), Particle.FLAME, from, 8, 0.08, 0.08, 0.08, 0.01);
        spawnParticle(from.getWorld(), Particle.SMOKE, from, 4, 0.05, 0.05, 0.05, 0.01);
    }

    private void castMagmaArmRockets(LivingEntity mob) {
        double range = plugin.getConfig().getDouble("combat.abilities.magma_arm_rockets.range", 32.0);
        int rockets = Math.max(1, plugin.getConfig().getInt("combat.abilities.magma_arm_rockets.rockets", 2));
        long rocketIntervalTicks = Math.max(1L, plugin.getConfig().getLong("combat.abilities.magma_arm_rockets.rocket_interval_ticks", 8L));
        int maxSteps = Math.max(10, plugin.getConfig().getInt("combat.abilities.magma_arm_rockets.max_steps", 70));
        double speed = plugin.getConfig().getDouble("combat.abilities.magma_arm_rockets.speed", 0.58);
        double turn = plugin.getConfig().getDouble("combat.abilities.magma_arm_rockets.turn_strength", 0.24);
        double hitRadius = plugin.getConfig().getDouble("combat.abilities.magma_arm_rockets.hit_radius", 1.7);
        double hitDamage = plugin.getConfig().getDouble("combat.abilities.magma_arm_rockets.hit_damage", 14.0);
        int hitFireTicks = plugin.getConfig().getInt("combat.abilities.magma_arm_rockets.hit_fire_ticks", 90);

        Player target = findNearestPlayer(mob, range);
        if (target == null) return;

        Vector facing = target.getLocation().toVector().subtract(mob.getLocation().toVector());
        facing.setY(0.0);
        if (facing.lengthSquared() <= 0.0001) return;
        facing.normalize();

        Vector right = new Vector(-facing.getZ(), 0, facing.getX());
        Location base = mob.getLocation().clone().add(0, 1.25, 0);
        Location leftArm = base.clone().add(right.clone().multiply(-0.62));
        Location rightArm = base.clone().add(right.clone().multiply(0.62));

        mob.getWorld().playSound(base, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 0.75f);
        mob.getWorld().playSound(base, Sound.BLOCK_LAVA_POP, 0.9f, 1.0f);

        for (int i = 0; i < rockets; i++) {
            final int rocketIndex = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!mob.isValid() || mob.isDead() || target.isDead()) return;
                if (target.getLocation().distanceSquared(mob.getLocation()) > (range * range)) return;

                Location launch = rocketIndex % 2 == 0 ? leftArm.clone() : rightArm.clone();
                Vector toTarget = target.getLocation().clone().add(0, target.getHeight() * 0.5, 0).toVector().subtract(launch.toVector());
                if (toTarget.lengthSquared() <= 0.0001) return;

                final Vector[] rocketPos = {launch.toVector()};
                final Vector[] rocketVel = {toTarget.normalize().multiply(speed)};

                spawnParticle(launch.getWorld(), Particle.FLAME, launch, 12, 0.12, 0.12, 0.12, 0.01);
                spawnParticle(launch.getWorld(), Particle.LAVA, launch, 8, 0.1, 0.1, 0.1, 0.01);

                new BukkitRunnable() {
                    int step = 0;

                    @Override
                    public void run() {
                        if (!mob.isValid() || mob.isDead() || target.isDead() || step >= maxSteps) {
                            cancel();
                            return;
                        }

                        Vector targetNow = target.getLocation().clone().add(0, target.getHeight() * 0.5, 0).toVector();
                        Vector desired = targetNow.clone().subtract(rocketPos[0]);
                        if (desired.lengthSquared() > 0.0) {
                            desired = desired.normalize().multiply(speed);
                            rocketVel[0] = rocketVel[0].multiply(1.0 - turn).add(desired.multiply(turn));
                        }

                        rocketPos[0] = rocketPos[0].clone().add(rocketVel[0]);
                        Location point = new Location(mob.getWorld(), rocketPos[0].getX(), rocketPos[0].getY(), rocketPos[0].getZ());

                        spawnParticle(point.getWorld(), Particle.FLAME, point, 5, 0.08, 0.08, 0.08, 0.01);
                        spawnParticle(point.getWorld(), Particle.SMOKE, point, 4, 0.09, 0.09, 0.09, 0.01);
                        spawnParticle(point.getWorld(), Particle.DUST, point, 3, 0.04, 0.04, 0.04, 0.0,
                                step % 2 == 0 ? MAGMA_LASER_GOLD : MAGMA_LASER_RED);

                        if (point.distanceSquared(target.getLocation().add(0, target.getHeight() * 0.5, 0)) <= (hitRadius * hitRadius)) {
                            target.damage(hitDamage, mob);
                            target.setFireTicks(Math.max(target.getFireTicks(), hitFireTicks));
                            spawnAoEHitParticles(target);
                            spawnParticle(point.getWorld(), Particle.EXPLOSION, point, 4, 0.2, 0.2, 0.2, 0.02);
                            spawnParticle(point.getWorld(), Particle.LAVA, point, 10, 0.2, 0.2, 0.2, 0.01);
                            point.getWorld().playSound(point, Sound.ENTITY_GENERIC_EXPLODE, 0.75f, 1.05f);
                            cancel();
                            return;
                        }

                        step++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }, i * rocketIntervalTicks);
        }
    }

    private void castBloodTongue(LivingEntity mob) {
        double range = plugin.getConfig().getDouble("combat.abilities.blood_tongue.range", 30.0);
        int maxSteps = Math.max(12, plugin.getConfig().getInt("combat.abilities.blood_tongue.max_steps", 70));
        double speed = plugin.getConfig().getDouble("combat.abilities.blood_tongue.speed", 0.72);
        double turnStrength = plugin.getConfig().getDouble("combat.abilities.blood_tongue.turn_strength", 0.3);
        double hitRadius = plugin.getConfig().getDouble("combat.abilities.blood_tongue.hit_radius", 1.1);
        double hitDamage = plugin.getConfig().getDouble("combat.abilities.blood_tongue.hit_damage", 13.0);
        double yankStrength = plugin.getConfig().getDouble("combat.abilities.blood_tongue.yank_strength", 0.75);
        int witherTicks = plugin.getConfig().getInt("combat.abilities.blood_tongue.wither_ticks", 60);

        Player target = findNearestPlayer(mob, range);
        if (target == null) return;

        Location mouth = mob.getEyeLocation().clone().add(mob.getLocation().getDirection().normalize().multiply(0.9));
        Vector toTarget = target.getLocation().clone().add(0, target.getHeight() * 0.55, 0).toVector().subtract(mouth.toVector());
        if (toTarget.lengthSquared() <= 0.0001) return;

        final Vector[] tonguePos = {mouth.toVector()};
        final Vector[] tongueVel = {toTarget.normalize().multiply(speed)};

        mob.getWorld().playSound(mouth, Sound.ENTITY_GHAST_WARN, 1.0f, 0.65f);
        mob.getWorld().playSound(mouth, Sound.BLOCK_HONEY_BLOCK_BREAK, 0.8f, 0.6f);

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead() || target.isDead() || step >= maxSteps) {
                    cancel();
                    return;
                }

                Vector targetNow = target.getLocation().clone().add(0, target.getHeight() * 0.55, 0).toVector();
                Vector desired = targetNow.clone().subtract(tonguePos[0]);
                if (desired.lengthSquared() > 0.0001) {
                    desired = desired.normalize().multiply(speed);
                    tongueVel[0] = tongueVel[0].multiply(1.0 - turnStrength).add(desired.multiply(turnStrength));
                }

                tonguePos[0] = tonguePos[0].clone().add(tongueVel[0]);
                Location point = new Location(mob.getWorld(), tonguePos[0].getX(), tonguePos[0].getY(), tonguePos[0].getZ());

                spawnParticle(point.getWorld(), Particle.DUST, point, 36, 0.38, 0.38, 0.38, 0.0, BLOOD_CRIMSON);
                spawnParticle(point.getWorld(), Particle.DUST, point, 24, 0.3, 0.3, 0.3, 0.0, BLOOD_DARK);
                spawnParticle(point.getWorld(), Particle.SQUID_INK, point, 8, 0.14, 0.14, 0.14, 0.0);

                Vector ribbon = point.toVector().subtract(mouth.toVector());
                double ribbonLength = ribbon.length();
                if (ribbonLength > 0.0001) {
                    Vector ribbonDir = ribbon.normalize();
                    for (double d = 0.0; d <= ribbonLength; d += 0.42) {
                        Location trail = mouth.clone().add(ribbonDir.clone().multiply(d));
                        spawnParticle(trail.getWorld(), Particle.DUST, trail, 4, 0.09, 0.09, 0.09, 0.0, BLOOD_CRIMSON);
                        if (((int) (d * 10)) % 2 == 0) {
                            spawnParticle(trail.getWorld(), Particle.DUST, trail, 3, 0.08, 0.08, 0.08, 0.0, BLOOD_DARK);
                        }
                    }
                }

                if (point.distanceSquared(targetNow.toLocation(mob.getWorld())) <= (hitRadius * hitRadius)) {
                    target.damage(hitDamage, mob);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, witherTicks, 0));
                    Vector yank = mob.getLocation().toVector().subtract(target.getLocation().toVector());
                    if (yank.lengthSquared() > 0.0001) {
                        target.setVelocity(yank.normalize().multiply(yankStrength).setY(0.12));
                    }
                    spawnAoEHitParticles(target);
                    spawnParticle(point.getWorld(), Particle.EXPLOSION, point, 3, 0.15, 0.15, 0.15, 0.01);
                    point.getWorld().playSound(point, Sound.ENTITY_SLIME_ATTACK, 0.9f, 0.7f);
                    cancel();
                    return;
                }

                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void castBloodExplosion(LivingEntity mob) {
        double range = plugin.getConfig().getDouble("combat.abilities.blood_explosion.range", 32.0);
        double radius = plugin.getConfig().getDouble("combat.abilities.blood_explosion.radius", 6.4);
        double finalDamage = plugin.getConfig().getDouble("combat.abilities.blood_explosion.final_damage", 28.0);
        double knockback = plugin.getConfig().getDouble("combat.abilities.blood_explosion.knockback", 1.0);
        int phase1Ticks = Math.max(4, plugin.getConfig().getInt("combat.abilities.blood_explosion.phase1_ticks", 14));
        int phase2Ticks = Math.max(4, plugin.getConfig().getInt("combat.abilities.blood_explosion.phase2_ticks", 14));
        int phase3Ticks = Math.max(4, plugin.getConfig().getInt("combat.abilities.blood_explosion.phase3_ticks", 12));

        Player target = findNearestPlayer(mob, range);
        if (target == null) return;

        Location anchor = target.getLocation().clone();
        mob.getWorld().playSound(anchor, Sound.ENTITY_GHAST_WARN, 1.0f, 0.52f);

        int totalTicks = phase1Ticks + phase2Ticks + phase3Ticks;

        new BukkitRunnable() {
            int lived = 0;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead()) {
                    cancel();
                    return;
                }

                Location c = anchor.clone().add(0, 0.2, 0);
                if (lived < phase1Ticks) {
                    spawnBloodRing(c, radius * 0.6, lived * 0.16, BLOOD_DARK, 15);
                    spawnParticle(c.getWorld(), Particle.SMOKE, c, 5, 0.55, 0.14, 0.55, 0.02);
                } else if (lived < (phase1Ticks + phase2Ticks)) {
                    spawnBloodRing(c, radius * 0.8, lived * 0.22, BLOOD_CRIMSON, 19);
                    spawnBloodRing(c, radius * 0.45, -lived * 0.18, BLOOD_DARK, 14);
                    spawnParticle(c.getWorld(), Particle.SQUID_INK, c, 8, 0.7, 0.16, 0.7, 0.02);
                    c.getWorld().playSound(c, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.5f, 1.4f);
                } else if (lived < totalTicks) {
                    spawnBloodRing(c, radius, lived * 0.3, BLOOD_CRIMSON, 24);
                    spawnParticle(c.getWorld(), Particle.DUST, c, 13, 0.65, 0.18, 0.65, 0.0, BLOOD_CRIMSON);
                    c.getWorld().playSound(c, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
                } else {
                    spawnParticle(c.getWorld(), Particle.EXPLOSION_EMITTER, c, 1);
                    spawnParticle(c.getWorld(), Particle.DUST, c, 43, 1.15, 0.45, 1.15, 0.0, BLOOD_CRIMSON);
                    spawnParticle(c.getWorld(), Particle.DUST, c, 30, 0.95, 0.35, 0.95, 0.0, BLOOD_DARK);
                    c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.85f);

                    for (Entity nearby : c.getWorld().getNearbyEntities(c, radius, radius, radius)) {
                        if (!(nearby instanceof Player player)) continue;
                        if (player.isDead()) continue;
                        if (player.getLocation().distanceSquared(c) > (radius * radius)) continue;

                        player.damage(finalDamage, mob);
                        spawnAoEHitParticles(player);

                        Vector push = player.getLocation().toVector().subtract(c.toVector());
                        if (push.lengthSquared() > 0.0001) {
                            player.setVelocity(push.normalize().multiply(knockback).setY(0.3));
                        }
                    }

                    cancel();
                    return;
                }

                lived += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void spawnBloodRing(Location center, double radius, double phase, Particle.DustOptions color, int points) {
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i / points) + phase;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location p = center.clone().add(x, 0.06 + (Math.sin(angle * 2.0) * 0.05), z);
            spawnParticle(p.getWorld(), Particle.DUST, p, 1, 0.02, 0.02, 0.02, 0.0, color);
        }
    }

    private void castWarpedEndermanGaze(LivingEntity mob) {
        castWarpedGazeProfile(mob, "combat.abilities.warped_enderman_gaze", false);
    }

    private void castRiftboundGaze(LivingEntity mob) {
        castWarpedGazeProfile(mob, "combat.abilities.riftbound_gaze", true);
    }

    private void castWarpedGazeProfile(LivingEntity mob, String configRoot, boolean advancedVisuals) {
        double range = plugin.getConfig().getDouble(configRoot + ".range", 16.0);
        double width = plugin.getConfig().getDouble(configRoot + ".width", advancedVisuals ? 3.2 : 2.7);
        double height = plugin.getConfig().getDouble(configRoot + ".height", advancedVisuals ? 3.8 : 3.0);
        double damage = plugin.getConfig().getDouble(configRoot + ".damage", advancedVisuals ? 24.0 : 12.0);
        double coneDamage = plugin.getConfig().getDouble(configRoot + ".dark_cone_damage", advancedVisuals ? 3.75 : 1.9);
        int darknessTicks = plugin.getConfig().getInt(configRoot + ".darkness_ticks", 60);
        int weaknessTicks = plugin.getConfig().getInt(configRoot + ".weakness_ticks", 80);

        if (!advancedVisuals) {
            Player target = findNearestPlayer(mob, range + 2.0);
            if (target == null) return;

            Location origin = mob.getLocation().clone().add(0, 0.95, 0);
            Vector forward = target.getLocation().clone().add(0, target.getHeight() * 0.5, 0).toVector().subtract(origin.toVector());
            forward.setY(0.0);
            if (forward.lengthSquared() <= 0.0001) return;
            forward.normalize();

            Vector right = new Vector(-forward.getZ(), 0, forward.getX());
            Set<UUID> hit = new HashSet<>();
            Set<UUID> grazed = new HashSet<>();

            mob.getWorld().playSound(origin, Sound.ENTITY_ENDERMAN_SCREAM, 0.7f, 0.9f);
            mob.getWorld().playSound(origin, Sound.BLOCK_PORTAL_AMBIENT, 0.6f, 1.35f);

            for (double d = 0.8; d <= range; d += 0.48) {
                double spread = width * (d / range);
                int sideSamples = Math.max(5, (int) Math.round(spread * 6.0));
                for (int i = -sideSamples; i <= sideSamples; i++) {
                    double side = (i / (double) sideSamples) * spread;
                    double coneLift = Math.max(0.0, (1.0 - Math.abs(i) / (double) sideSamples) * (height * 0.3));
                    Location p = origin.clone()
                            .add(forward.clone().multiply(d))
                            .add(right.clone().multiply(side))
                            .add(0, coneLift, 0);

                    spawnParticle(p.getWorld(), Particle.REVERSE_PORTAL, p, 1, 0.01, 0.01, 0.01, 0.0);
                    spawnParticle(p.getWorld(), Particle.PORTAL, p, 1, 0.01, 0.01, 0.01, 0.0);
                    spawnParticle(p.getWorld(), Particle.SOUL, p, 1, 0.01, 0.01, 0.01, 0.0);
                    if (i % 2 == 0) {
                        spawnParticle(p.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, WARPED_DEEP);
                    } else {
                        spawnParticle(p.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, WARPED_CYAN);
                    }
                    if ((i + ((int) (d * 10))) % 3 == 0) {
                        spawnParticle(p.getWorld(), Particle.END_ROD, p, 1, 0.01, 0.01, 0.01, 0.0);
                    }

                    for (Entity nearby : p.getWorld().getNearbyEntities(p, 0.85, height * 0.5, 0.85)) {
                        if (!(nearby instanceof Player player)) continue;
                        if (player.isDead()) continue;

                        if (grazed.add(player.getUniqueId())) {
                            player.damage(coneDamage, mob);
                        }
                        if (!hit.add(player.getUniqueId())) continue;

                        player.damage(damage, mob);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, darknessTicks, 0));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weaknessTicks, 0));
                        spawnAoEHitParticles(player);
                    }
                }
            }
            return;
        }

        Player target = findNearestPlayer(mob, range + 6.0);
        if (target == null) return;
        Location start = mob.getLocation().clone().add(0, 0.85, 0);
        mob.getWorld().playSound(start, Sound.ENTITY_ENDERMAN_SCREAM, 0.8f, 0.75f);
        mob.getWorld().playSound(start, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 1.6f);

        int tentacles = Math.max(3, plugin.getConfig().getInt(configRoot + ".tentacles", 4));
        int durationTicks = Math.max(16, plugin.getConfig().getInt(configRoot + ".tentacle_duration_ticks", 34));
        double tentacleSpeed = plugin.getConfig().getDouble(configRoot + ".tentacle_speed", 0.58);
        double turnStrength = plugin.getConfig().getDouble(configRoot + ".tentacle_turn_strength", 0.22);
        double hitRadius = plugin.getConfig().getDouble(configRoot + ".tentacle_hit_radius", 1.1);

        for (int i = 0; i < tentacles; i++) {
            final int idx = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!mob.isValid() || mob.isDead()) return;

                double normalized = tentacles == 1 ? 0.0 : ((idx / (double) (tentacles - 1)) - 0.5);
                castRiftboundTentacleStream(mob, target.getUniqueId(), range, damage, coneDamage,
                        darknessTicks, weaknessTicks, durationTicks, tentacleSpeed, turnStrength, hitRadius,
                        normalized * width * 0.85);
            }, i * 2L);
        }
    }

    private void castRiftboundTentacleStream(
            LivingEntity mob,
            UUID targetId,
            double range,
            double damage,
            double grazeDamage,
            int darknessTicks,
            int weaknessTicks,
            int durationTicks,
            double speed,
            double turnStrength,
            double hitRadius,
            double lateralBias
    ) {
        Location origin = mob.getEyeLocation().clone().add(0, -0.18, 0);
        Player firstTarget = Bukkit.getPlayer(targetId);
        if (firstTarget == null || !firstTarget.isValid() || firstTarget.isDead() || !firstTarget.getWorld().equals(mob.getWorld())) {
            firstTarget = findNearestPlayer(mob, range + 8.0);
            if (firstTarget == null) return;
        }

        Vector facing = firstTarget.getLocation().clone().add(0, firstTarget.getHeight() * 0.5, 0).toVector().subtract(origin.toVector());
        if (facing.lengthSquared() <= 0.0001) return;
        facing.normalize();

        Vector right = new Vector(-facing.getZ(), 0, facing.getX());
        Vector spreadBias = right.clone().multiply(lateralBias);
        origin.add(spreadBias.clone().multiply(0.18));

        final Vector[] streamPos = {origin.toVector()};
        final Vector[] streamVel = {facing.clone().add(spreadBias.clone().multiply(0.08)).normalize().multiply(speed)};
        final Set<UUID> grazed = new HashSet<>();

        new BukkitRunnable() {
            int lived = 0;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead() || lived >= durationTicks) {
                    cancel();
                    return;
                }

                Player tracked = Bukkit.getPlayer(targetId);
                if (tracked == null || !tracked.isValid() || tracked.isDead() || !tracked.getWorld().equals(mob.getWorld())) {
                    tracked = findNearestPlayer(mob, range + 8.0);
                }
                if (tracked == null) {
                    cancel();
                    return;
                }

                Location targetCenter = tracked.getLocation().clone().add(0, tracked.getHeight() * 0.55, 0);
                Vector desired = targetCenter.toVector().subtract(streamPos[0]);
                if (desired.lengthSquared() <= 0.0001) {
                    cancel();
                    return;
                }

                desired = desired.normalize().multiply(speed).add(spreadBias.clone().multiply(0.012));
                streamVel[0] = streamVel[0].multiply(1.0 - turnStrength).add(desired.multiply(turnStrength));
                streamPos[0] = streamPos[0].clone().add(streamVel[0]);

                Location point = new Location(mob.getWorld(), streamPos[0].getX(), streamPos[0].getY(), streamPos[0].getZ());
                if (point.distanceSquared(mob.getLocation()) > (range * range)) {
                    cancel();
                    return;
                }

                spawnParticle(point.getWorld(), Particle.REVERSE_PORTAL, point, 10, 0.13, 0.13, 0.13, 0.0);
                spawnParticle(point.getWorld(), Particle.PORTAL, point, 8, 0.11, 0.11, 0.11, 0.0);
                spawnParticle(point.getWorld(), Particle.SQUID_INK, point, 4, 0.08, 0.08, 0.08, 0.0);
                spawnParticle(point.getWorld(), Particle.DUST, point, 6, 0.08, 0.08, 0.08, 0.0,
                        lived % 2 == 0 ? WARPED_CYAN : WARPED_DEEP);

                Vector ribbon = point.toVector().subtract(origin.toVector());
                double ribbonLength = ribbon.length();
                if (ribbonLength > 0.0001) {
                    Vector ribbonDir = ribbon.normalize();
                    for (double d = 0.0; d <= ribbonLength; d += 0.55) {
                        Location trail = origin.clone().add(ribbonDir.clone().multiply(d));
                        spawnParticle(trail.getWorld(), Particle.DUST, trail, 4, 0.06, 0.06, 0.06, 0.0,
                                ((int) (d * 10 + lived) % 2 == 0) ? WARPED_DEEP : WARPED_CYAN);
                        if (((int) (d * 10)) % 3 == 0) {
                            spawnParticle(trail.getWorld(), Particle.REVERSE_PORTAL, trail, 1, 0.02, 0.02, 0.02, 0.0);
                        }
                    }
                }

                for (Entity nearby : point.getWorld().getNearbyEntities(point, hitRadius, hitRadius, hitRadius)) {
                    if (!(nearby instanceof Player player)) continue;
                    if (player.isDead()) continue;

                    player.damage(damage, mob);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, darknessTicks, 0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weaknessTicks, 0));
                    Vector pull = mob.getLocation().toVector().subtract(player.getLocation().toVector());
                    if (pull.lengthSquared() > 0.0001) {
                        player.setVelocity(pull.normalize().multiply(0.45).setY(0.1));
                    }
                    spawnAoEHitParticles(player);
                    point.getWorld().playSound(point, Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.65f, 0.9f);
                    cancel();
                    return;
                }

                for (Entity nearby : point.getWorld().getNearbyEntities(point, hitRadius * 1.6, 1.1, hitRadius * 1.6)) {
                    if (!(nearby instanceof Player player)) continue;
                    if (player.isDead()) continue;
                    if (!grazed.add(player.getUniqueId())) continue;
                    player.damage(grazeDamage, mob);
                }

                lived++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void castWarpedEndermanSnare(LivingEntity mob) {
        castVoidSnareProfile(mob, "combat.abilities.warped_enderman_snare", false);
    }

    private void castRiftboundSnare(LivingEntity mob) {
        castVoidSnareProfile(mob, "combat.abilities.riftbound_snare", true);
    }

    private void castVoidSnareProfile(LivingEntity mob, String configRoot, boolean advancedVisuals) {
        double range = plugin.getConfig().getDouble(configRoot + ".range", 18.0);
        double radius = plugin.getConfig().getDouble(configRoot + ".radius", 4.2);
        double damage = plugin.getConfig().getDouble(configRoot + ".damage", advancedVisuals ? 12.0 : 6.0);
        double pullStrength = plugin.getConfig().getDouble(configRoot + ".pull_strength", 0.95);
        int slownessTicks = plugin.getConfig().getInt(configRoot + ".slowness_ticks", 80);
        long chargeTicks = Math.max(8L, plugin.getConfig().getLong(configRoot + ".charge_ticks", 20L));

        Player target = findNearestPlayer(mob, range);
        if (target == null) return;

        final UUID targetId = target.getUniqueId();
        final Location anchor = target.getLocation().clone();
        mob.getWorld().playSound(anchor, Sound.ENTITY_ENDERMAN_AMBIENT, 0.9f, 0.55f);

        new BukkitRunnable() {
            long lived = 0L;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead()) {
                    cancel();
                    return;
                }

                Player tracked = Bukkit.getPlayer(targetId);
                Location centerBase = (tracked != null
                    && tracked.isValid()
                    && !tracked.isDead()
                    && tracked.getWorld().equals(anchor.getWorld()))
                    ? tracked.getLocation()
                    : anchor;
                Location center = centerBase.clone().add(0, 0.2, 0);
                double phase = lived * 0.22;
                int points = advancedVisuals ? 36 : 24;
                for (int i = 0; i < points; i++) {
                    double angle = (Math.PI * 2.0 * i / points) + phase;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location p = center.clone().add(x, 0.05 + Math.sin(angle * 2.0) * (advancedVisuals ? 0.08 : 0.04), z);
                    spawnParticle(p.getWorld(), Particle.REVERSE_PORTAL, p, 1, 0.01, 0.01, 0.01, 0.0);
                    if (advancedVisuals) {
                        spawnParticle(p.getWorld(), Particle.PORTAL, p, 1, 0.01, 0.01, 0.01, 0.0);
                        if (i % 3 == 0) {
                            spawnParticle(p.getWorld(), Particle.END_ROD, p, 1, 0.02, 0.02, 0.02, 0.0);
                        }
                        spawnParticle(p.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0,
                                i % 2 == 0 ? WARPED_CYAN : WARPED_DEEP);
                    }
                }

                if (advancedVisuals) {
                    spawnParticle(center.getWorld(), Particle.SOUL, center, 10, 0.45, 0.15, 0.45, 0.01);
                    spawnParticle(center.getWorld(), Particle.SQUID_INK, center, 6, 0.35, 0.12, 0.35, 0.01);
                    spawnParticle(center.getWorld(), Particle.SMOKE, center, 7, 0.4, 0.15, 0.4, 0.01);

                    for (int k = 0; k < 10; k++) {
                        double swirl = phase + (k * (Math.PI * 2.0 / 10.0));
                        double swirlRadius = radius * 0.55;
                        Location sp = center.clone().add(
                                Math.cos(swirl) * swirlRadius,
                                0.2 + Math.sin(swirl * 2.0 + phase) * 0.2,
                                Math.sin(swirl) * swirlRadius
                        );
                        spawnParticle(sp.getWorld(), Particle.REVERSE_PORTAL, sp, 1, 0.015, 0.015, 0.015, 0.0);
                        spawnParticle(sp.getWorld(), Particle.DUST, sp, 1, 0.0, 0.0, 0.0, 0.0,
                                k % 2 == 0 ? WARPED_DEEP : WARPED_CYAN);
                    }
                } else {
                    spawnParticle(center.getWorld(), Particle.SQUID_INK, center, 3, 0.2, 0.08, 0.2, 0.01);
                    spawnParticle(center.getWorld(), Particle.SMOKE, center, 3, 0.25, 0.08, 0.25, 0.01);

                    int coneLayers = 4;
                    for (int layer = 0; layer < coneLayers; layer++) {
                        double y = 0.06 + (layer * 0.18);
                        double layerRadius = radius * (1.0 - (layer / (double) coneLayers) * 0.55);
                        int layerPoints = 8 + (layer * 3);
                        for (int p = 0; p < layerPoints; p++) {
                            double ang = (Math.PI * 2.0 * p / layerPoints) + (phase * 0.7) + (layer * 0.15);
                            Location cone = center.clone().add(Math.cos(ang) * layerRadius, y, Math.sin(ang) * layerRadius);
                            spawnParticle(cone.getWorld(), Particle.REVERSE_PORTAL, cone, 1, 0.01, 0.01, 0.01, 0.0);
                            if ((p + layer) % 2 == 0) {
                                spawnParticle(cone.getWorld(), Particle.DUST, cone, 1, 0.0, 0.0, 0.0, 0.0,
                                        layer % 2 == 0 ? WARPED_DEEP : WARPED_CYAN);
                            }
                        }
                    }
                }

                if (lived >= chargeTicks) {
                    center.getWorld().playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.9f, advancedVisuals ? 0.8f : 1.0f);

                    for (Entity nearby : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                        if (!(nearby instanceof Player player)) continue;
                        if (player.isDead()) continue;
                        if (player.getLocation().distanceSquared(center) > (radius * radius)) continue;

                        Vector pull = center.toVector().subtract(player.getLocation().toVector());
                        if (pull.lengthSquared() > 0.0001) {
                            player.setVelocity(pull.normalize().multiply(pullStrength).setY(0.12));
                        }

                        player.damage(damage, mob);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessTicks, 1));
                        spawnAoEHitParticles(player);
                    }

                    cancel();
                    return;
                }

                lived += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void castSoulRupture(LivingEntity mob) {
        double radius = plugin.getConfig().getDouble("combat.abilities.soul_rupture.radius", 5.2);
        double damage = plugin.getConfig().getDouble("combat.abilities.soul_rupture.damage", 9.0);
        int weaknessTicks = plugin.getConfig().getInt("combat.abilities.soul_rupture.weakness_ticks", 90);
        int witherTicks = plugin.getConfig().getInt("combat.abilities.soul_rupture.wither_ticks", 50);

        Location center = mob.getLocation().clone().add(0, 0.2, 0);
        mob.getWorld().playSound(center, Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.75f);
        mob.getWorld().playSound(center, Sound.ENTITY_WITHER_SKELETON_AMBIENT, 0.8f, 0.7f);

        int points = 38;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i / points);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location pLow = center.clone().add(x, 0.05, z);
            Location pHigh = center.clone().add(x * 0.86, 0.38, z * 0.86);

            spawnParticle(pLow.getWorld(), Particle.DUST, pLow, 1, 0.0, 0.0, 0.0, 0.0, i % 2 == 0 ? SOUL_GRAY : SOUL_BLACK);
            spawnParticle(pHigh.getWorld(), Particle.DUST, pHigh, 1, 0.0, 0.0, 0.0, 0.0, i % 2 == 0 ? SOUL_ICE : SOUL_GRAY);
            spawnParticle(pLow.getWorld(), Particle.SOUL, pLow, 1, 0.01, 0.01, 0.01, 0.0);
            if (i % 3 == 0) {
                spawnParticle(pHigh.getWorld(), Particle.ASH, pHigh, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }

        for (Entity nearby : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof Player player)) continue;
            if (player.isDead()) continue;
            if (player.getLocation().distanceSquared(center) > (radius * radius)) continue;

            player.damage(damage, mob);
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weaknessTicks, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, witherTicks, 0));
            spawnAoEHitParticles(player);
        }
    }

    private void castSoulGeysers(LivingEntity mob) {
        double fieldRadius = plugin.getConfig().getDouble("combat.abilities.soul_geysers.field_radius", 7.5);
        int pulses = Math.max(1, plugin.getConfig().getInt("combat.abilities.soul_geysers.pulses", 4));
        int columnsPerPulse = Math.max(1, plugin.getConfig().getInt("combat.abilities.soul_geysers.columns_per_pulse", 6));
        long pulseInterval = Math.max(2L, plugin.getConfig().getLong("combat.abilities.soul_geysers.pulse_interval_ticks", 8L));
        double columnHeight = plugin.getConfig().getDouble("combat.abilities.soul_geysers.column_height", 3.2);
        double hitRadius = plugin.getConfig().getDouble("combat.abilities.soul_geysers.hit_radius", 0.9);
        double hitDamage = plugin.getConfig().getDouble("combat.abilities.soul_geysers.hit_damage", 7.0);
        int blindTicks = plugin.getConfig().getInt("combat.abilities.soul_geysers.blind_ticks", 60);

        Location center = mob.getLocation().clone();
        mob.getWorld().playSound(center, Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.7f);
        mob.getWorld().playSound(center, Sound.BLOCK_SOUL_SAND_BREAK, 0.8f, 0.8f);

        Map<UUID, Long> hitCooldownUntil = new HashMap<>();

        new BukkitRunnable() {
            int pulse = 0;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead() || pulse >= pulses) {
                    cancel();
                    return;
                }

                Location liveCenter = mob.getLocation().clone();
                for (int i = 0; i < columnsPerPulse; i++) {
                    double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
                    double distance = ThreadLocalRandom.current().nextDouble(0.8, fieldRadius);
                    double x = Math.cos(angle) * distance;
                    double z = Math.sin(angle) * distance;
                    Location base = liveCenter.clone().add(x, 0.0, z);

                    spawnSoulGeyserColumn(base, columnHeight);

                    for (Entity nearby : base.getWorld().getNearbyEntities(base, hitRadius, columnHeight + 0.5, hitRadius)) {
                        if (!(nearby instanceof Player player)) continue;
                        if (player.isDead()) continue;
                        if (player.getLocation().distanceSquared(base) > (hitRadius * hitRadius)) continue;

                        long now = System.currentTimeMillis();
                        long lock = hitCooldownUntil.getOrDefault(player.getUniqueId(), 0L);
                        if (now < lock) continue;

                        hitCooldownUntil.put(player.getUniqueId(), now + 500L);
                        player.damage(hitDamage, mob);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindTicks, 0));
                        spawnAoEHitParticles(player);
                    }
                }

                pulse++;
            }
        }.runTaskTimer(plugin, 0L, pulseInterval);
    }

    private void spawnSoulGeyserColumn(Location base, double height) {
        for (double y = 0.08; y <= height; y += 0.3) {
            Location p = base.clone().add(0, y, 0);
            spawnParticle(p.getWorld(), Particle.DUST, p, 6, 0.14, 0.08, 0.14, 0.0, SOUL_ICE);
            spawnParticle(p.getWorld(), Particle.SOUL_FIRE_FLAME, p, 4, 0.10, 0.08, 0.10, 0.0);
            spawnParticle(p.getWorld(), Particle.SOUL, p, 3, 0.08, 0.05, 0.08, 0.0);
        }

        Location cap = base.clone().add(0, height, 0);
        spawnParticle(cap.getWorld(), Particle.DUST, cap, 12, 0.26, 0.10, 0.26, 0.0, SOUL_ICE);
        spawnParticle(cap.getWorld(), Particle.SOUL_FIRE_FLAME, cap, 6, 0.2, 0.05, 0.2, 0.0);
    }

    private void castValleyDirge(LivingEntity mob) {
        double radius = plugin.getConfig().getDouble("combat.abilities.valley_dirge.radius", 6.4);
        int pulses = Math.max(2, plugin.getConfig().getInt("combat.abilities.valley_dirge.pulses", 3));
        long pulseInterval = Math.max(4L, plugin.getConfig().getLong("combat.abilities.valley_dirge.pulse_interval_ticks", 10L));
        double damagePerPulse = plugin.getConfig().getDouble("combat.abilities.valley_dirge.damage_per_pulse", 4.5);
        int darknessTicks = plugin.getConfig().getInt("combat.abilities.valley_dirge.darkness_ticks", 50);
        double pullStrength = plugin.getConfig().getDouble("combat.abilities.valley_dirge.pull_strength", 0.55);

        Location center = mob.getLocation().clone().add(0, 0.25, 0);
        mob.getWorld().playSound(center, Sound.ENTITY_ALLAY_HURT, 0.9f, 0.55f);
        mob.getWorld().playSound(center, Sound.BLOCK_SOUL_SAND_PLACE, 0.8f, 0.8f);

        new BukkitRunnable() {
            int pulse = 0;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead() || pulse >= pulses) {
                    cancel();
                    return;
                }

                Location c = mob.getLocation().clone().add(0, 0.25, 0);
                int points = 34;
                for (int i = 0; i < points; i++) {
                    double angle = (Math.PI * 2.0 * i / points) + (pulse * 0.22);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location p = c.clone().add(x, 0.12 + Math.sin(angle * 2.0) * 0.07, z);
                    spawnParticle(p.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0,
                            i % 2 == 0 ? SOUL_BLACK : SOUL_ICE);
                    spawnParticle(p.getWorld(), Particle.REVERSE_PORTAL, p, 1, 0.01, 0.01, 0.01, 0.0);
                }
                spawnParticle(c.getWorld(), Particle.SOUL, c, 22, 0.45, 0.15, 0.45, 0.02);
                spawnParticle(c.getWorld(), Particle.ASH, c, 18, 0.5, 0.16, 0.5, 0.01);

                for (Entity nearby : c.getWorld().getNearbyEntities(c, radius, radius, radius)) {
                    if (!(nearby instanceof Player player)) continue;
                    if (player.isDead()) continue;
                    if (player.getLocation().distanceSquared(c) > (radius * radius)) continue;

                    Vector pull = c.toVector().subtract(player.getLocation().toVector());
                    if (pull.lengthSquared() > 0.0001) {
                        player.setVelocity(player.getVelocity().add(pull.normalize().multiply(pullStrength).setY(0.06)));
                    }

                    player.damage(damagePerPulse, mob);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, darknessTicks, 0));
                    spawnAoEHitParticles(player);
                }

                pulse++;
            }
        }.runTaskTimer(plugin, 0L, pulseInterval);
    }

    private void castGroundSlam(LivingEntity mob) {
        double radius = plugin.getConfig().getDouble("combat.abilities.ground_slam.radius", 4.0);
        double damage = plugin.getConfig().getDouble("combat.abilities.ground_slam.damage", 8.0) * 2.0;
        double knockback = plugin.getConfig().getDouble("combat.abilities.ground_slam.knockback", 1.1);

        Location origin = mob.getLocation().clone();
        spawnParticle(mob.getWorld(), Particle.WITCH, origin.clone().add(0, 1.0, 0), 35, 0.5, 0.5, 0.5, 0.01);
        mob.getWorld().playSound(origin, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.7f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!mob.isValid() || mob.isDead()) return;
            Location center = mob.getLocation();
            spawnParticle(mob.getWorld(), Particle.EXPLOSION_EMITTER, center, 1);
            mob.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

            for (Entity nearby : mob.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                if (!(nearby instanceof Player player)) continue;
                if (player.isDead()) continue;
                player.damage(damage, mob);
                spawnAoEHitParticles(player);

                Vector push = player.getLocation().toVector().subtract(center.toVector());
                if (push.lengthSquared() > 0) {
                    player.setVelocity(push.normalize().multiply(knockback).setY(0.35));
                }
            }
        }, 20L);
    }

    private void castMiniBlast(LivingEntity mob) {
        double radius = plugin.getConfig().getDouble("combat.abilities.mini_blast.radius", 2.5);
        double damage = plugin.getConfig().getDouble("combat.abilities.ground_slam.damage", 8.0) * 2.0;
        double knockback = plugin.getConfig().getDouble("combat.abilities.ground_slam.knockback", 1.1);

        Location center = mob.getLocation();
        spawnParticle(mob.getWorld(), Particle.EXPLOSION, center.clone().add(0, 0.5, 0), 2, 0.15, 0.15, 0.15, 0.01);
        mob.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.1f);

        for (Entity nearby : mob.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof Player player)) continue;
            if (player.isDead()) continue;
            player.damage(damage, mob);
            spawnAoEHitParticles(player);

            Vector push = player.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() > 0) {
                player.setVelocity(push.normalize().multiply(knockback).setY(0.3));
            }
        }
    }

    private void castTidalPull(LivingEntity mob) {
        double radius = plugin.getConfig().getDouble("combat.abilities.tidal_pull.radius", 8.0);
        double pullStrength = plugin.getConfig().getDouble("combat.abilities.tidal_pull.strength", 1.2);

        Location center = mob.getLocation();
        spawnParticle(mob.getWorld(), Particle.BUBBLE, center.clone().add(0, 1.0, 0), 40, 1.2, 0.6, 1.2, 0.05);
        mob.getWorld().playSound(center, Sound.ENTITY_DROWNED_SHOOT, 1.0f, 0.8f);

        for (Entity nearby : mob.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof Player player)) continue;
            if (player.isDead()) continue;

            Vector pull = center.toVector().subtract(player.getLocation().toVector());
            if (pull.lengthSquared() == 0) continue;
            player.setVelocity(pull.normalize().multiply(pullStrength).setY(0.2));
        }
    }

    private void castFrostBeam(LivingEntity mob) {
        double range = plugin.getConfig().getDouble("combat.abilities.frost_beam.range", 28.0);
        double damage = plugin.getConfig().getDouble("combat.abilities.frost_beam.damage", 8.0) * 1.75;
        int slownessTicks = plugin.getConfig().getInt("combat.abilities.frost_beam.slowness_ticks", 60);
        int slownessAmplifier = plugin.getConfig().getInt("combat.abilities.frost_beam.slowness_amplifier", 1);
        int stackedParticles = Math.max(1, plugin.getConfig().getInt("combat.abilities.frost_beam.stacked_particles", 10));

        castElementBeam(mob, range, damage, slownessTicks, slownessAmplifier, stackedParticles,
            FROST_BEAM_BLUE, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.8f, true);
        }

        private void castDuneBeam(LivingEntity mob) {
        double range = plugin.getConfig().getDouble("combat.abilities.frost_beam.range", 28.0);
        double damage = plugin.getConfig().getDouble("combat.abilities.frost_beam.damage", 8.0) * 1.75;
        int slownessTicks = plugin.getConfig().getInt("combat.abilities.frost_beam.slowness_ticks", 60);
        int slownessAmplifier = plugin.getConfig().getInt("combat.abilities.frost_beam.slowness_amplifier", 1);
        int stackedParticles = Math.max(1, plugin.getConfig().getInt("combat.abilities.frost_beam.stacked_particles", 10));

        castElementBeam(mob, range, damage, slownessTicks, slownessAmplifier, stackedParticles,
            DUNE_BEAM_SAND, Sound.BLOCK_SAND_BREAK, 0.9f, 0.9f, false);
        }

        private void castElementBeam(LivingEntity mob,
                     double range,
                     double damage,
                     int slownessTicks,
                     int slownessAmplifier,
                     int stackedParticles,
                     Particle.DustOptions beamColor,
                     Sound sound,
                     float soundVolume,
                     float soundPitch,
                     boolean useSnowflake) {

        Player target = findNearestPlayer(mob, range);
        if (target == null) return;

        Location start = mob.getEyeLocation();
        Location targetLoc = target.getLocation().add(0, target.getHeight() * 0.5, 0);
        Vector toTarget = targetLoc.toVector().subtract(start.toVector());
        double distance = Math.min(range, toTarget.length());
        if (distance <= 0.01) return;

        Vector dir = toTarget.normalize();
        double step = 0.18;
        for (double d = 0.0; d <= distance; d += step) {
            Location point = start.clone().add(dir.clone().multiply(d));
            spawnParticle(mob.getWorld(), Particle.DUST, point, stackedParticles, 0.06, 0.06, 0.06, 0.0, beamColor);
            if (useSnowflake) {
                spawnParticle(mob.getWorld(), Particle.SNOWFLAKE, point, Math.max(2, stackedParticles / 2), 0.03, 0.03, 0.03, 0.0);
            } else {
                spawnParticle(mob.getWorld(), Particle.FALLING_DUST, point, Math.max(2, stackedParticles / 2), 0.03, 0.03, 0.03, 0.0, Material.SAND.createBlockData());
            }
        }

        mob.getWorld().playSound(start, sound, soundVolume, soundPitch);

        if (target.isDead()) return;
        if (target.getLocation().distanceSquared(mob.getLocation()) > (range * range)) return;

        target.damage(damage, mob);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessTicks, slownessAmplifier));
    }

    private void castVultureMissile(LivingEntity mob) {
        double range = plugin.getConfig().getDouble("combat.abilities.vulture_missile.range", 34.0);
        double damage = plugin.getConfig().getDouble("combat.abilities.frost_beam.damage", 16.0) * 1.75;
        int slownessTicks = plugin.getConfig().getInt("combat.abilities.frost_beam.slowness_ticks", 60);
        int slownessAmplifier = plugin.getConfig().getInt("combat.abilities.frost_beam.slowness_amplifier", 1);
        int maxSteps = Math.max(12, plugin.getConfig().getInt("combat.abilities.vulture_missile.max_steps", 65));
        double speed = plugin.getConfig().getDouble("combat.abilities.vulture_missile.speed", 0.5);

        Player target = findNearestPlayer(mob, range);
        if (target == null) return;

        Location start = mob.getEyeLocation().clone().add(mob.getLocation().getDirection().normalize().multiply(0.6));
        Vector startPos = start.toVector();
        Vector targetCenter = target.getLocation().add(0, target.getHeight() * 0.5, 0).toVector();
        Vector initialVelocity = targetCenter.clone().subtract(startPos).normalize().multiply(speed);

        mob.getWorld().playSound(start, Sound.ENTITY_PHANTOM_FLAP, 0.9f, 0.7f);

        final Vector[] missilePos = { startPos };
        final Vector[] missileVel = { initialVelocity };

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead() || target.isDead() || step >= maxSteps) {
                    cancel();
                    return;
                }

                Vector targetNow = target.getLocation().add(0, target.getHeight() * 0.5, 0).toVector();
                Vector desired = targetNow.clone().subtract(missilePos[0]);
                if (desired.lengthSquared() > 0) {
                    desired = desired.normalize().multiply(speed);
                    missileVel[0] = missileVel[0].multiply(0.78).add(desired.multiply(0.22));
                }

                missilePos[0] = missilePos[0].clone().add(missileVel[0]);
                Location point = new Location(mob.getWorld(), missilePos[0].getX(), missilePos[0].getY(), missilePos[0].getZ());

                double angle = step * 0.55;
                double rx = Math.cos(angle) * 0.23;
                double rz = Math.sin(angle) * 0.23;
                double ry = Math.sin(angle * 2.0) * 0.08;

                spawnParticle(point.getWorld(), Particle.FALLING_DUST, point, 7, 0.08, 0.08, 0.08, 0.0, Material.SAND.createBlockData());
                spawnParticle(point.getWorld(), Particle.DUST, point.clone().add(rx, ry, rz), 2, 0.02, 0.02, 0.02, 0.0, VULTURE_GREEN_LIGHT);
                spawnParticle(point.getWorld(), Particle.DUST, point.clone().add(-rx * 0.7, -ry * 0.7, -rz * 0.7), 2, 0.02, 0.02, 0.02, 0.0, VULTURE_GREEN_DARK);
                spawnParticle(point.getWorld(), Particle.END_ROD, point, 1, 0.01, 0.01, 0.01, 0.0);

                if (point.distanceSquared(target.getLocation().add(0, target.getHeight() * 0.5, 0)) <= 1.6) {
                    target.damage(damage, mob);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessTicks, slownessAmplifier));
                    spawnAoEHitParticles(target);
                    spawnParticle(point.getWorld(), Particle.EXPLOSION, point, 4, 0.2, 0.2, 0.2, 0.02);
                    point.getWorld().playSound(point, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
                    cancel();
                    return;
                }

                step++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void castTempleRitual(LivingEntity mob) {
        double radius = plugin.getConfig().getDouble("combat.abilities.temple_ritual.radius", 6.0);
        double damage = plugin.getConfig().getDouble("combat.abilities.temple_ritual.damage", 30.0);
        long chargeTicks = plugin.getConfig().getLong("combat.abilities.temple_ritual.charge_ticks", 28L);

        Location center = mob.getLocation().clone();
        mob.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.8f);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead()) {
                    cancel();
                    return;
                }

                Location liveCenter = mob.getLocation().clone();
                spawnTempleRitualCircle(liveCenter, radius * 0.45, TEMPLE_GREEN, tick * 0.18);
                spawnTempleRitualCircle(liveCenter, radius * 0.75, TEMPLE_ORANGE, -tick * 0.14);
                spawnTempleRitualCircle(liveCenter, radius, TEMPLE_YELLOW, tick * 0.11);
                spawnParticle(liveCenter.getWorld(), Particle.END_ROD, liveCenter.clone().add(0, 1.1, 0), 6, 0.12, 0.25, 0.12, 0.0);

                if (tick >= chargeTicks) {
                    liveCenter.getWorld().playSound(liveCenter, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.7f);

                    for (Entity nearby : liveCenter.getWorld().getNearbyEntities(liveCenter, radius, radius, radius)) {
                        if (!(nearby instanceof Player player)) continue;
                        if (player.isDead()) continue;
                        if (player.getLocation().distanceSquared(liveCenter) > (radius * radius)) continue;
                        player.damage(damage, mob);
                        spawnAoEHitParticles(player);
                        spawnParticle(player.getWorld(), Particle.DUST,
                                player.getLocation().add(0, player.getHeight() * 0.5, 0),
                                10, 0.22, 0.28, 0.22, 0.0, TEMPLE_YELLOW);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                    }

                    cancel();
                    return;
                }

                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void castPoseidonsWave(LivingEntity mob) {
        double range = plugin.getConfig().getDouble("combat.abilities.poseidon_wave.range", 14.0);
        double width = plugin.getConfig().getDouble("combat.abilities.poseidon_wave.width", 3.8);
        double height = plugin.getConfig().getDouble("combat.abilities.poseidon_wave.height", 2.4);
        double damage = plugin.getConfig().getDouble("combat.abilities.poseidon_wave.damage", 28.0);
        double knockback = plugin.getConfig().getDouble("combat.abilities.poseidon_wave.knockback", 0.65);
        double verticalKnockback = plugin.getConfig().getDouble("combat.abilities.poseidon_wave.vertical_knockback", 0.14);
        int slownessTicks = plugin.getConfig().getInt("combat.abilities.poseidon_wave.slowness_ticks", 60);

        Player target = findNearestPlayer(mob, range + 14.0);
        if (target == null) return;

        Location origin = mob.getLocation().clone().add(0, 0.25, 0);
        Vector forward = target.getLocation().toVector().subtract(origin.toVector());
        forward.setY(0);
        if (forward.lengthSquared() < 0.0001) return;
        final Vector forwardDir = forward.normalize();
        final Vector rightDir = new Vector(-forwardDir.getZ(), 0, forwardDir.getX());

        mob.getWorld().playSound(origin, Sound.ENTITY_DROWNED_SHOOT, 1.0f, 0.65f);
        mob.getWorld().playSound(origin, Sound.ITEM_TRIDENT_RIPTIDE_2, 0.8f, 0.85f);

        final double stepDistance = 0.7;
        final int maxSteps = Math.max(10, (int) Math.ceil(range / stepDistance));
        final Set<UUID> hitPlayers = new HashSet<>();

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead() || step > maxSteps) {
                    cancel();
                    return;
                }

                double progress = (double) step / maxSteps;
                double rise = Math.min(1.0, step / 5.0);
                double d = progress * range;
                Location crestCenter = origin.clone().add(forwardDir.clone().multiply(d));
                double localWidth = width * (0.6 + progress * 0.4);
                double localHeight = height * (0.35 + rise * 0.65);

                for (double lateral = -localWidth; lateral <= localWidth; lateral += 0.4) {
                    double yWave = 0.35 + Math.sin((progress * 8.0) + (lateral * 0.8)) * (localHeight * 0.32);
                    Location p = crestCenter.clone().add(rightDir.clone().multiply(lateral)).add(0, yWave, 0);
                    spawnParticle(mob.getWorld(), Particle.DUST, p, 2, 0.03, 0.08, 0.03, 0.0, POSEIDON_WAVE_AQUA);
                    spawnParticle(mob.getWorld(), Particle.DUST, p.clone().add(0, -0.22, 0), 1, 0.03, 0.03, 0.03, 0.0, POSEIDON_WAVE_DEEP);
                    spawnParticle(mob.getWorld(), Particle.SPLASH, p, 1, 0.05, 0.10, 0.05, 0.0);
                    spawnParticle(mob.getWorld(), Particle.BUBBLE, p, 1, 0.04, 0.08, 0.04, 0.0);
                }

                double frontThickness = 0.95;
                for (Entity nearby : mob.getWorld().getNearbyEntities(crestCenter, localWidth + 1.4, localHeight + 1.8, localWidth + 1.4)) {
                    if (!(nearby instanceof Player player)) continue;
                    if (player.isDead()) continue;
                    if (hitPlayers.contains(player.getUniqueId())) continue;

                    Vector rel = player.getLocation().toVector().subtract(origin.toVector());
                    double forwardDist = rel.dot(forwardDir);
                    if (Math.abs(forwardDist - d) > frontThickness) continue;

                    Vector lateralVec = rel.clone().subtract(forwardDir.clone().multiply(forwardDist));
                    if (lateralVec.length() > localWidth) continue;

                    double playerY = player.getLocation().getY() - origin.getY();
                    if (playerY < -1.2 || playerY > (localHeight + 1.2)) continue;

                    hitPlayers.add(player.getUniqueId());
                    player.damage(damage, mob);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessTicks, 1));
                    spawnAoEHitParticles(player);
                    player.setVelocity(forwardDir.clone().multiply(knockback).setY(verticalKnockback));
                }

                step++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void castPoseidonStormWave(LivingEntity mob) {
        double range = plugin.getConfig().getDouble("combat.abilities.poseidon_storm_wave.range", 24.0);
        int strikes = Math.max(3, plugin.getConfig().getInt("combat.abilities.poseidon_storm_wave.strikes", 6));
        double strikeSpread = plugin.getConfig().getDouble("combat.abilities.poseidon_storm_wave.strike_spread", 5.0);
        double strikeDamage = plugin.getConfig().getDouble("combat.abilities.poseidon_storm_wave.strike_damage", 9.0);

        Player target = findNearestPlayer(mob, range);
        if (target == null) return;

        Location center = target.getLocation().clone();
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.75f, 1.25f);

        for (int i = 0; i < strikes; i++) {
            int delay = i * 4;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!mob.isValid() || mob.isDead()) return;

                double ox = ThreadLocalRandom.current().nextDouble(-strikeSpread, strikeSpread);
                double oz = ThreadLocalRandom.current().nextDouble(-strikeSpread, strikeSpread);
                Location strikeLoc = center.clone().add(ox, 0, oz);

                spawnParticle(strikeLoc.getWorld(), Particle.ELECTRIC_SPARK, strikeLoc.clone().add(0, 1.0, 0), 20, 0.35, 0.7, 0.35, 0.02);
                strikeLoc.getWorld().strikeLightningEffect(strikeLoc);

                for (Entity nearby : strikeLoc.getWorld().getNearbyEntities(strikeLoc, 2.2, 3.0, 2.2)) {
                    if (!(nearby instanceof Player player)) continue;
                    if (player.isDead()) continue;
                    player.damage(strikeDamage, mob);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                    spawnAoEHitParticles(player);
                }
            }, delay);
        }
    }

    private void castDarkRitual(LivingEntity mob) {
        double radius = plugin.getConfig().getDouble("combat.abilities.dark_ritual.radius", 6.0);
        double damagePerHit = plugin.getConfig().getDouble("combat.abilities.dark_ritual.damage_per_hit", 5.0);
        int weaknessTicks = plugin.getConfig().getInt("combat.abilities.dark_ritual.weakness_ticks", 80);
        int weaknessAmplifier = plugin.getConfig().getInt("combat.abilities.dark_ritual.weakness_amplifier", 0);
        int pulseCount = Math.max(1, plugin.getConfig().getInt("combat.abilities.dark_ritual.pulses", 3));
        long pulseInterval = Math.max(2L, plugin.getConfig().getLong("combat.abilities.dark_ritual.pulse_interval_ticks", 8L));

        Location center = mob.getLocation().clone();
        mob.getWorld().playSound(center, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.8f, 0.85f);

        new BukkitRunnable() {
            int pulse = 0;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead() || pulse >= pulseCount) {
                    cancel();
                    return;
                }

                Location liveCenter = mob.getLocation().clone();
                spawnDarkRitualCircle(liveCenter, radius, pulse * 0.42);
                spawnDarkRitualStar(liveCenter, radius * 0.78, pulse * 0.27);
                spawnParticle(liveCenter.getWorld(), Particle.SQUID_INK, liveCenter.clone().add(0, 0.35, 0), 25, 0.35, 0.18, 0.35, 0.01);
                liveCenter.getWorld().playSound(liveCenter, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.45f, 1.3f);

                for (Entity nearby : liveCenter.getWorld().getNearbyEntities(liveCenter, radius, radius, radius)) {
                    if (!(nearby instanceof Player player)) continue;
                    if (player.isDead()) continue;
                    if (player.getLocation().distanceSquared(liveCenter) > (radius * radius)) continue;

                    player.damage(damagePerHit, mob);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weaknessTicks, weaknessAmplifier));
                    spawnAoEHitParticles(player);
                    Location hit = player.getLocation().add(0, player.getHeight() * 0.5, 0);
                    spawnParticle(player.getWorld(), Particle.DUST, hit, 7, 0.2, 0.25, 0.2, 0.0, DARK_RITUAL_TEAL);
                    spawnParticle(player.getWorld(), Particle.SQUID_INK, hit, 5, 0.12, 0.2, 0.12, 0.01);
                }

                pulse++;
            }
        }.runTaskTimer(plugin, 0L, pulseInterval);
    }

    private void castWardenPhaseTwo(LivingEntity mob) {
        double radius = plugin.getConfig().getDouble("combat.abilities.warden_phase_two.radius", 7.0);
        double damagePerHit = plugin.getConfig().getDouble("combat.abilities.warden_phase_two.damage_per_hit", 6.0);
        int pulseCount = Math.max(2, plugin.getConfig().getInt("combat.abilities.warden_phase_two.pulses", 4));
        long pulseInterval = Math.max(2L, plugin.getConfig().getLong("combat.abilities.warden_phase_two.pulse_interval_ticks", 6L));

        Location center = mob.getLocation().clone();
        mob.getWorld().playSound(center, Sound.ENTITY_WARDEN_ROAR, 0.8f, 0.8f);

        new BukkitRunnable() {
            int pulse = 0;

            @Override
            public void run() {
                if (!mob.isValid() || mob.isDead() || pulse >= pulseCount) {
                    cancel();
                    return;
                }

                Location liveCenter = mob.getLocation().clone();
                spawnDarkRitualCircle(liveCenter, radius, pulse * 0.35);
                spawnDarkRitualStar(liveCenter, radius * 0.72, pulse * 0.25);
                spawnParticle(liveCenter.getWorld(), Particle.SCULK_SOUL, liveCenter.clone().add(0, 0.5, 0), 24, 0.45, 0.2, 0.45, 0.02);

                for (Entity nearby : liveCenter.getWorld().getNearbyEntities(liveCenter, radius, radius, radius)) {
                    if (!(nearby instanceof Player player)) continue;
                    if (player.isDead()) continue;
                    if (player.getLocation().distanceSquared(liveCenter) > (radius * radius)) continue;

                    player.damage(damagePerHit, mob);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
                    spawnAoEHitParticles(player);
                }

                pulse++;
            }
        }.runTaskTimer(plugin, 0L, pulseInterval);
    }

    private void spawnDarkRitualCircle(Location center, double radius, double phase) {
        int points = 34;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i / points) + phase;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location p = center.clone().add(x, 0.08, z);
            spawnParticle(center.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0,
                    (i % 2 == 0) ? DARK_RITUAL_TEAL : DARK_RITUAL_BLACK);
            spawnParticle(center.getWorld(), Particle.SQUID_INK, p, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private void spawnDarkRitualStar(Location center, double radius, double rotation) {
        Location[] points = new Location[5];
        for (int i = 0; i < 5; i++) {
            double angle = rotation + (Math.PI * 2.0 * i / 5.0) - (Math.PI / 2.0);
            points[i] = center.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius);
        }

        for (int i = 0; i < 5; i++) {
            Location a = points[i];
            Location b = points[(i + 2) % 5];
            drawRitualLine(a, b);
        }
    }

    private void drawRitualLine(Location from, Location to) {
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length <= 0.001) return;

        Vector dir = delta.normalize();
        for (double d = 0.0; d <= length; d += 0.28) {
            Location p = from.clone().add(dir.clone().multiply(d));
            spawnParticle(p.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, DARK_RITUAL_TEAL);
            spawnParticle(p.getWorld(), Particle.DUST, p.clone().add(0, 0.03, 0), 1, 0.0, 0.0, 0.0, 0.0, DARK_RITUAL_BLACK);
        }
    }

    private void spawnTempleRitualCircle(Location center, double radius, Particle.DustOptions color, double phase) {
        int points = 26;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i / points) + phase;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location p = center.clone().add(x, 0.1 + Math.sin(angle * 2.0) * 0.06, z);
            spawnParticle(center.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, color);
        }
    }

    private void handleKingSlimeLandingAbility(LivingEntity mob, UUID mobUuid, List<SpawnConfig.AbilityConfig> abilities) {
        SpawnConfig.AbilityConfig landingAbility = null;
        for (SpawnConfig.AbilityConfig ability : abilities) {
            if ("KING_SLIME_SPLASH".equalsIgnoreCase(ability.getId())) {
                landingAbility = ability;
                break;
            }
        }
        if (landingAbility == null) return;

        boolean onGroundNow = mob.isOnGround();
        boolean onGroundBefore = wasOnGround.getOrDefault(mobUuid, onGroundNow);
        wasOnGround.put(mobUuid, onGroundNow);

        if (!onGroundNow || onGroundBefore) return;

        Map<String, Long> perMob = cooldowns.computeIfAbsent(mobUuid, ignored -> new HashMap<>());
        String abilityKey = "KING_SLIME_SPLASH";
        long nextReadyTick = perMob.getOrDefault(abilityKey, 0L);
        if (gameTick < nextReadyTick) return;
        if (ThreadLocalRandom.current().nextDouble() > clampChance(landingAbility.getChance())) return;

        castKingSlimeLandingSplash(mob);
        perMob.put(abilityKey, gameTick + Math.max(1, landingAbility.getCooldownTicks()));
    }

    private void castKingSlimeLandingSplash(LivingEntity mob) {
        double radius = plugin.getConfig().getDouble("combat.abilities.king_slime_splash.radius", 10);
        double damage = plugin.getConfig().getDouble("combat.abilities.king_slime_splash.damage", 18.0);
        double knockback = plugin.getConfig().getDouble("combat.abilities.king_slime_splash.knockback", 0.9);

        Location center = mob.getLocation().clone();
        mob.getWorld().playSound(center, Sound.ENTITY_SLIME_SQUISH, 1.2f, 0.6f);

        int splashPoints = 72;
        for (int i = 0; i < splashPoints; i++) {
            double angle = (Math.PI * 2.0 * i) / splashPoints;
            double distance = ThreadLocalRandom.current().nextDouble(0.3, radius);
            double x = Math.cos(angle) * distance;
            double z = Math.sin(angle) * distance;
            double y = ThreadLocalRandom.current().nextDouble(0.05, 0.35);
            Location p = center.clone().add(x, y, z);

            if (ThreadLocalRandom.current().nextBoolean()) {
                spawnParticle(mob.getWorld(), Particle.DUST, p, 2, 0.03, 0.03, 0.03, 0.0, KING_SLIME_GREEN);
            } else {
                spawnParticle(mob.getWorld(), Particle.DUST, p, 2, 0.03, 0.03, 0.03, 0.0, KING_SLIME_BROWN);
            }
            spawnParticle(mob.getWorld(), Particle.FALLING_DUST, p, 1, 0.02, 0.02, 0.02, 0.0, Material.DIRT.createBlockData());
        }

        for (Entity nearby : mob.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof Player player)) continue;
            if (player.isDead()) continue;
            if (player.getLocation().distanceSquared(center) > (radius * radius)) continue;

            player.damage(damage, mob);
            spawnAoEHitParticles(player);

            Vector push = player.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() > 0) {
                player.setVelocity(push.normalize().multiply(knockback).setY(0.28));
            }
        }
    }

    private void castInkCloud(LivingEntity mob) {
        double radius = plugin.getConfig().getDouble("combat.abilities.ink_cloud.radius", 6.0);
        int durationTicks = plugin.getConfig().getInt("combat.abilities.ink_cloud.duration_ticks", 60);
        int slownessAmplifier = plugin.getConfig().getInt("combat.abilities.ink_cloud.slowness_amplifier", 1);

        Location center = mob.getLocation();
        spawnParticle(mob.getWorld(), Particle.SQUID_INK, center.clone().add(0, 1.0, 0), 45, 1.0, 0.6, 1.0, 0.01);
        mob.getWorld().playSound(center, Sound.ENTITY_SQUID_SQUIRT, 1.0f, 0.9f);

        for (Entity nearby : mob.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof Player player)) continue;
            if (player.isDead()) continue;
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, durationTicks, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, slownessAmplifier));
        }
    }

    private void spawnAoEHitParticles(LivingEntity entity) {
        Location hitLoc = entity.getLocation().add(0, entity.getHeight() * 0.5, 0);
        spawnParticle(entity.getWorld(), Particle.DUST, hitLoc, 8, 0.25, 0.35, 0.25, AOE_HIT_RED);
    }

    private LivingEntity resolveMountedBossMount(LivingEntity entity) {
        if (entity == null) return null;
        if (entity.getVehicle() instanceof LivingEntity living && areLinkedMountedEntities(entity, living)) {
            return living;
        }
        return entity;
    }

    private LivingEntity resolveMountedBossRider(LivingEntity mount) {
        if (mount == null) return null;
        for (Entity passenger : mount.getPassengers()) {
            if (passenger instanceof LivingEntity living && areLinkedMountedEntities(mount, living)) {
                return living;
            }
        }
        return null;
    }

    private boolean isMountedBossController(LivingEntity entity) {
        if (entity == null) return false;
        if (entity.getVehicle() instanceof LivingEntity living && areLinkedMountedEntities(entity, living)) {
            return false;
        }
        return true;
    }

    private boolean areLinkedMountedEntities(LivingEntity first, LivingEntity second) {
        if (first == null || second == null) return false;
        if (!plugin.getMobManager().isFishedMob(first) || !plugin.getMobManager().isFishedMob(second)) return false;
        String firstId = plugin.getMobManager().getMobId(first);
        String secondId = plugin.getMobManager().getMobId(second);
        return firstId != null && firstId.equals(secondId);
    }

    private void drawBloodYokeTether(Location from, Location to, long tick) {
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length <= 0.001) return;

        Vector dir = delta.normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() <= 0.0001) {
            right = new Vector(1, 0, 0);
        } else {
            right.normalize();
        }

        for (double d = 0.0; d <= length; d += 0.22) {
            double wave = Math.sin((d * 2.0) + (tick * 0.22)) * 0.16;
            Location a = from.clone().add(dir.clone().multiply(d)).add(right.clone().multiply(wave));
            Location b = from.clone().add(dir.clone().multiply(d)).add(right.clone().multiply(-wave));
            spawnParticle(a.getWorld(), Particle.DUST, a, 1, 0.0, 0.0, 0.0, 0.0, BLOOD_CRIMSON);
            spawnParticle(b.getWorld(), Particle.DUST, b, 1, 0.0, 0.0, 0.0, 0.0, ABOMINATION_ORANGE);
            if (((int) d * 10) % 4 == 0) {
                spawnParticle(a.getWorld(), Particle.SMOKE, a, 1, 0.01, 0.01, 0.01, 0.0);
            }
        }
    }

    private void spawnBloodYokeChargeTelegraph(Location mountLoc, Location riderLoc, Vector forward, double length, long tick) {
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        int lances = 3;
        for (int lane = -1; lane <= 1; lane++) {
            Vector laneOffset = right.clone().multiply(lane * 0.9);
            for (double d = 0.6; d <= length; d += 0.5) {
                double rise = 0.12 + Math.sin((d * 0.9) + (tick * 0.18) + lane) * 0.08;
                Location p = mountLoc.clone().add(laneOffset).add(forward.clone().multiply(d)).add(0, rise, 0);
                spawnParticle(p.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0,
                        lane == 0 ? BLOOD_CRIMSON : ABOMINATION_DEEP);
            }
        }

        spawnBloodYokeSigil(mountLoc, 1.8, tick * 0.16, false);
        spawnBloodYokeSigil(riderLoc, 0.9, -tick * 0.22, true);
    }

    private void spawnBloodYokeRushTrail(Location mountLoc, Location riderLoc, Vector forward, long tick) {
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        for (int i = 0; i < 5; i++) {
            double behind = i * 0.45;
            Location p = mountLoc.clone().subtract(forward.clone().multiply(behind)).add(right.clone().multiply(Math.sin((tick * 0.25) + i) * 0.35)).add(0, 0.18, 0);
            spawnParticle(p.getWorld(), Particle.DUST, p, 3, 0.06, 0.06, 0.06, 0.0, BLOOD_CRIMSON);
            spawnParticle(p.getWorld(), Particle.DUST, p, 2, 0.05, 0.05, 0.05, 0.0, ABOMINATION_ORANGE);
            spawnParticle(p.getWorld(), Particle.CRIMSON_SPORE, p, 1, 0.04, 0.04, 0.04, 0.0);
        }
        spawnBloodYokeSigil(riderLoc, 0.85, tick * 0.2, true);
    }

    private void spawnBloodYokeSigil(Location center, double radius, double phase, boolean accentOrange) {
        int points = 18;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i / points) + phase;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location p = center.clone().add(x, 0.08 + Math.sin(angle * 2.0) * 0.04, z);
            spawnParticle(center.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0,
                    accentOrange && i % 3 == 0 ? ABOMINATION_ORANGE : ABOMINATION_SCARLET);
        }

        for (int i = 0; i < 3; i++) {
            double angle = phase + (Math.PI * 2.0 * i / 3.0);
            Vector dir = new Vector(Math.cos(angle), 0, Math.sin(angle));
            for (double d = 0.2; d <= radius; d += 0.26) {
                Location p = center.clone().add(dir.clone().multiply(d)).add(0, 0.05, 0);
                spawnParticle(center.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0,
                        accentOrange ? ABOMINATION_ORANGE : ABOMINATION_DEEP);
            }
        }
    }

    private interface PlayerEffectAction {
        void apply(Player player);
    }

    private void affectWarDrumPlayers(LivingEntity source, Location center, double radius, double damage, PlayerEffectAction extra) {
        for (Entity nearby : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof Player player)) continue;
            if (player.isDead()) continue;
            if (player.getLocation().distanceSquared(center) > (radius * radius)) continue;
            player.damage(damage, source);
            spawnAoEHitParticles(player);
            extra.apply(player);
        }
    }

    private void spawnWarDrumCore(Location center, double phase, boolean finale) {
        for (int i = 0; i < 3; i++) {
            double angle = phase + (Math.PI * 2.0 * i / 3.0);
            Location p = center.clone().add(Math.cos(angle) * 0.9, 0.28, Math.sin(angle) * 0.9);
            spawnParticle(center.getWorld(), Particle.DUST, p, 2, 0.03, 0.04, 0.03, 0.0,
                    i % 2 == 0 ? ABOMINATION_ORANGE : BLOOD_CRIMSON);
            spawnParticle(center.getWorld(), Particle.FLAME, p, finale ? 2 : 1, 0.03, 0.03, 0.03, 0.0);
        }
        spawnParticle(center.getWorld(), Particle.DUST, center.clone().add(0, 0.25, 0), finale ? 18 : 10, 0.35, 0.12, 0.35, 0.0,
                finale ? BLOOD_CRIMSON : ABOMINATION_SCARLET);
    }

    private void spawnWarDrumRing(Location center, double radius, double phase, boolean spiked) {
        int points = 28;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i / points) + phase;
            double localRadius = radius + (spiked ? Math.sin(angle * 6.0) * 0.18 : Math.cos(angle * 4.0) * 0.12);
            Location p = center.clone().add(Math.cos(angle) * localRadius, 0.1 + Math.sin(angle * 2.0) * 0.05, Math.sin(angle) * localRadius);
            spawnParticle(center.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0,
                    i % 2 == 0 ? BLOOD_CRIMSON : ABOMINATION_ORANGE);
            if (i % 4 == 0) {
                spawnParticle(center.getWorld(), Particle.CRIMSON_SPORE, p, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    private void spawnWarDrumSpokes(Location center, double radius, double phase) {
        for (int spoke = 0; spoke < 8; spoke++) {
            double angle = phase + (Math.PI * 2.0 * spoke / 8.0);
            Vector dir = new Vector(Math.cos(angle), 0, Math.sin(angle));
            for (double d = 0.6; d <= radius; d += 0.35) {
                Location p = center.clone().add(dir.clone().multiply(d)).add(0, 0.05 + Math.sin(d * 1.7 + phase) * 0.03, 0);
                spawnParticle(center.getWorld(), Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0,
                        spoke % 2 == 0 ? ABOMINATION_DEEP : ABOMINATION_SCARLET);
            }
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

    private Player findNearestPlayer(LivingEntity mob, double range) {
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;
        double rangeSq = range * range;

        for (Player player : mob.getWorld().getPlayers()) {
            if (player.isDead()) continue;
            double distSq = player.getLocation().distanceSquared(mob.getLocation());
            if (distSq <= rangeSq && distSq < nearestDist) {
                nearest = player;
                nearestDist = distSq;
            }
        }

        return nearest;
    }
}

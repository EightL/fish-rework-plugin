package com.fishrework;

import com.fishrework.gui.LavaBagGUI;
import com.fishrework.model.ArtifactPassiveStat;
import com.fishrework.model.BiomeFishingProfile;
import com.fishrework.model.BiomeGroup;
import com.fishrework.model.CustomMob;
import com.fishrework.gui.FishBagGUI;
import com.fishrework.model.MobDrop;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Skill;
import com.fishrework.model.SpawnConfig;
import com.fishrework.registry.MobRegistry;
import com.fishrework.util.BagUtils;
import com.fishrework.util.FeatureKeys;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles mob spawning, identification, and rewards.
 * All mob DATA now lives in MobRegistry — this class is pure logic.
 * Spawn behaviour is driven by {@link SpawnConfig} loaded from mobs.yml.
 */
public class MobManager {

    private final FishRework plugin;
    public final NamespacedKey FISHED_MOB_KEY;
    public final NamespacedKey MOB_ID_KEY;
    public final NamespacedKey FISH_WEIGHT_KEY;
    public final NamespacedKey MOB_SCALE_KEY;
    public final NamespacedKey CATCH_XP_MULTIPLIER_KEY;

    // Passive-aggro AI keys (stored in PDC for AggroTask to read)
    public final NamespacedKey AGGRO_SPEED_KEY;
    public final NamespacedKey AGGRO_DAMAGE_KEY;
    public final NamespacedKey AGGRO_RANGE_KEY;
    public final NamespacedKey AGGRO_HIT_INTERVAL_KEY; // in ticks

    // Particle effect key (stored in PDC, read by AggroTask)
    public final NamespacedKey PARTICLE_TYPE_KEY;

    // Group kill-all key: mob requires all members killed for reward
    public final NamespacedKey GROUP_KILL_ALL_KEY;

    // Unique UUID per spawn group — prevents reward collisions between separate spawns
    public final NamespacedKey GROUP_UUID_KEY;

    // Shared HP tag for sea-mounted pairs
    public final NamespacedKey SHARED_MOUNTED_HP_KEY;

    private final Set<NamespacedKey> netherArmorSetKeys;

    public MobManager(FishRework plugin) {
        this.plugin = plugin;
        this.FISHED_MOB_KEY = new NamespacedKey(plugin, "fished_mob");
        this.MOB_ID_KEY = new NamespacedKey(plugin, "mob_id");
        this.FISH_WEIGHT_KEY = new NamespacedKey(plugin, "fish_weight");
        this.MOB_SCALE_KEY = new NamespacedKey(plugin, "mob_scale");
        this.CATCH_XP_MULTIPLIER_KEY = new NamespacedKey(plugin, "catch_xp_multiplier");
        this.AGGRO_SPEED_KEY = new NamespacedKey(plugin, "aggro_speed");
        this.AGGRO_DAMAGE_KEY = new NamespacedKey(plugin, "aggro_damage");
        this.AGGRO_RANGE_KEY = new NamespacedKey(plugin, "aggro_range");
        this.AGGRO_HIT_INTERVAL_KEY = new NamespacedKey(plugin, "aggro_hit_interval");
        this.PARTICLE_TYPE_KEY = new NamespacedKey(plugin, "particle_type");
        this.GROUP_KILL_ALL_KEY = new NamespacedKey(plugin, "group_kill_all");
        this.GROUP_UUID_KEY = new NamespacedKey(plugin, "group_uuid");
        this.SHARED_MOUNTED_HP_KEY = new NamespacedKey(plugin, "shared_mounted_hp");
        this.netherArmorSetKeys = Set.of(
            new NamespacedKey(plugin, "magma_scale_armor"),
            new NamespacedKey(plugin, "infernal_plate_armor"),
            new NamespacedKey(plugin, "volcanic_dreadplate_armor")
        );
    }

    // Tracking active fished mobs for optimized iteration
    private final java.util.Set<java.util.UUID> activeFishedMobs = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public void addActiveMob(java.util.UUID uuid) {
        activeFishedMobs.add(uuid);
    }

    public void removeActiveMob(java.util.UUID uuid) {
        activeFishedMobs.remove(uuid);
    }

    public java.util.Set<java.util.UUID> getActiveFishedMobs() {
        return activeFishedMobs;
    }

    // ══════════════════════════════════════════════════════════
    //  Spawning — config-driven via SpawnConfig (from mobs.yml)
    // ══════════════════════════════════════════════════════════

    /**
     * Spawns a mob by its registry ID at the given location.
     * Behaviour is driven by the mob's {@link SpawnConfig}.
     */
    public void spawnMob(Location location, String mobId) {
        spawnMob(location, mobId, null);
    }

    /**
     * Spawns a mob by its registry ID at the given location.
     * If a fishing player is provided, the creature will be pulled toward them.
     */
    public void spawnMob(Location location, String mobId, Player fishingPlayer) {
        spawnMob(location, mobId, fishingPlayer, 0.0);
    }

    public void spawnMob(Location location, String mobId, Player fishingPlayer, double catchXpMultiplierPercent) {
        CustomMob def = plugin.getMobRegistry().get(mobId);
        if (def == null) return;

        SpawnConfig config = def.getSpawnConfig();
        SpawnConfig.Type type = config != null ? config.getType() : SpawnConfig.Type.SIMPLE;

        LivingEntity primary = switch (type) {
            case MOUNTED -> spawnMounted(location, mobId, def, config, catchXpMultiplierPercent);
            case GROUP   -> spawnGroup(location, mobId, def, config, catchXpMultiplierPercent);
            case SCALED  -> spawnScaled(location, mobId, def, config, catchXpMultiplierPercent);
            default      -> spawnSimple(location, mobId, def, config, catchXpMultiplierPercent);
        };

        // Pull toward the fishing player if provided
        if (fishingPlayer != null && primary != null) {
            pullTowardPlayer(primary, fishingPlayer);
        }
    }

    // ── Unified Spawn Methods ─────────────────────────────────

    /**
     * Standard single-entity spawn with weight/scale variance.
     * Applies stats, aggro, equipment, and potion effects from config.
     */
    private LivingEntity spawnSimple(Location location, String mobId, CustomMob def, SpawnConfig config,
                                     double catchXpMultiplierPercent) {
        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, def.getEntityType());

        // Name with optional color
        net.kyori.adventure.text.format.NamedTextColor nameColor = resolveNameColor(config, def);
        if (nameColor != null) {
            entity.customName(net.kyori.adventure.text.Component.text(def.getDisplayName()).color(nameColor));
        } else {
            entity.customName(net.kyori.adventure.text.Component.text(def.getDisplayName()));
        }
        entity.setCustomNameVisible(true);

        // Tropical fish randomization
        if (entity instanceof org.bukkit.entity.TropicalFish tropicalFish) {
            org.bukkit.DyeColor[] colors = org.bukkit.DyeColor.values();
            org.bukkit.entity.TropicalFish.Pattern[] patterns = org.bukkit.entity.TropicalFish.Pattern.values();
            tropicalFish.setBodyColor(colors[ThreadLocalRandom.current().nextInt(colors.length)]);
            tropicalFish.setPatternColor(colors[ThreadLocalRandom.current().nextInt(colors.length)]);
            tropicalFish.setPattern(patterns[ThreadLocalRandom.current().nextInt(patterns.length)]);
        }

        // Iron golem special flag
        if (config != null && config.isNotPlayerCreated() && entity instanceof org.bukkit.entity.IronGolem golem) {
            golem.setPlayerCreated(false);
        }

        // PDC tags + weight/scale variance
        tagEntity(entity, mobId, catchXpMultiplierPercent);
        applyWeightScale(entity, def);

        // Stats from config (health, damage, scale)
        applyEntityStats(entity, config, def.isHostile());

        // Passive-aggro AI
        if (config != null && config.hasAggro()) {
            SpawnConfig.AggroConfig aggro = config.getAggro();
            tagPassiveAggro(entity, aggro.getSpeed(), aggro.getDamage(), aggro.getRange(), aggro.getHitInterval());
        }

        // Particle
        if (config != null && config.getParticle() != null) {
            tagParticle(entity, config.getParticle());
        }

        applyGlowColor(entity, config);

        // Equipment
        if (config != null && config.hasEquipment()) {
            applyEquipment(entity, config.getEquipment());
        }

        // Potion effects
        if (config != null) {
            applyPotionEffects(entity, config.getPotionEffects());
        }

        // Group kill-all flag
        if (config != null && config.isGroupKillAll()) {
            tagGroupKillAll(entity);
        }

        // Aggro nearest player for hostiles
        if (def.isHostile()) {
            aggroNearest(entity, location);
        }

        // Fire resistance — prevents daylight burning
        applyFireResistance(entity);

        markLavaCreatureIfNeeded(entity, location);

        return entity;
    }

    /**
     * Mounted spawn: mount + rider, both tagged with same mob ID.
     */
    private LivingEntity spawnMounted(Location location, String mobId, CustomMob def, SpawnConfig config,
                                      double catchXpMultiplierPercent) {
        SpawnConfig.EntityConfig mountConfig = config.getMount();
        if (mountConfig == null) {
            return spawnSimple(location, mobId, def, config, catchXpMultiplierPercent);
        }

        // ── Spawn mount ──
        LivingEntity mount = (LivingEntity) location.getWorld().spawnEntity(location, mountConfig.getEntityType());
        String mountName = mountConfig.getDisplayName() != null ? mountConfig.getDisplayName() : "Mount";
        mount.customName(net.kyori.adventure.text.Component.text(mountName));
        mount.setCustomNameVisible(mountConfig.isShowName());
        tagEntity(mount, mobId, catchXpMultiplierPercent);

        // Panda gene
        if (mountConfig.getPandaGene() != null && mount instanceof org.bukkit.entity.Panda panda) {
            org.bukkit.entity.Panda.Gene gene = org.bukkit.entity.Panda.Gene.valueOf(mountConfig.getPandaGene());
            panda.setMainGene(gene);
            panda.setHiddenGene(gene);
        }

        // Mount stats
        applyMountStats(mount, mountConfig);

        // Mount aggro
        if (mountConfig.hasAggro()) {
            SpawnConfig.AggroConfig aggro = mountConfig.getAggro();
            tagPassiveAggro(mount, aggro.getSpeed(), aggro.getDamage(), aggro.getRange(), aggro.getHitInterval());
        }

        // Mount weight
        applyWeightScale(mount, def);

        // Fire resistance — prevents daylight burning
        applyFireResistance(mount);

        markLavaCreatureIfNeeded(mount, location);

        // ── Spawn rider ──
        EntityType riderType = config.getRiderEntityType() != null
                ? config.getRiderEntityType() : def.getEntityType();
        LivingEntity rider = (LivingEntity) location.getWorld().spawnEntity(location, riderType);

        net.kyori.adventure.text.format.NamedTextColor riderColor = resolveNameColor(config, def);
        if (riderColor != null) {
            rider.customName(net.kyori.adventure.text.Component.text(def.getDisplayName()).color(riderColor));
        } else {
            rider.customName(net.kyori.adventure.text.Component.text(def.getDisplayName()));
        }
        rider.setCustomNameVisible(true);
        tagEntity(rider, mobId, catchXpMultiplierPercent);

        // Rider stats
        applyEntityStats(rider, config, true);

        applyGlowColor(mount, config);
        applyGlowColor(rider, config);

        // Rider equipment
        if (config.hasEquipment()) {
            applyEquipment(rider, config.getEquipment());
        }

        // Rider weight
        applyWeightScale(rider, def);

        // Fire resistance — prevents daylight burning
        applyFireResistance(rider);

        markLavaCreatureIfNeeded(rider, location);

        // Mount up
        mount.addPassenger(rider);

        // Aggro
        aggroNearest(mount, location);
        aggroNearest(rider, location);

        // Treat mount+rider as a single kill-all group so reward is given once
        String mountedGroupUUID = java.util.UUID.randomUUID().toString();
        tagGroupKillAll(mount);
        tagGroupKillAll(rider);
        tagGroupUUID(mount, mountedGroupUUID);
        tagGroupUUID(rider, mountedGroupUUID);

        if (shouldUseSharedMountedHp(mobId, mountConfig.getEntityType(), riderType)) {
            tagSharedMountedHp(mount);
            tagSharedMountedHp(rider);
            applySharedMountedHealthPool(mount, rider);
        }

        return mount;
    }

    /**
     * Group spawn: multiple entities, optionally GROUP_KILL_ALL.
     */
    private LivingEntity spawnGroup(Location location, String mobId, CustomMob def, SpawnConfig config,
                                    double catchXpMultiplierPercent) {
        double spread = config.getSpread();
        boolean killAll = config.isGroupKillAll();
        net.kyori.adventure.text.format.NamedTextColor nameColor = resolveNameColor(config, def);
        LivingEntity firstEntity = null;

        // Unique UUID for this spawn group — used for reward dedup
        String groupUUID = killAll ? java.util.UUID.randomUUID().toString() : null;

        for (SpawnConfig.GroupMemberConfig member : config.getMembers()) {
            for (int i = 0; i < member.getCount(); i++) {
                Location offset = location.clone().add(
                        ThreadLocalRandom.current().nextDouble(-spread, spread),
                        0,
                        ThreadLocalRandom.current().nextDouble(-spread, spread));

                LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(offset, member.getEntityType());
                if (firstEntity == null) firstEntity = entity;

                // Name
                String memberName = member.getDisplayName() != null ? member.getDisplayName() : def.getDisplayName();
                if (nameColor != null) {
                    entity.customName(net.kyori.adventure.text.Component.text(memberName).color(nameColor));
                } else {
                    entity.customName(net.kyori.adventure.text.Component.text(memberName));
                }
                entity.setCustomNameVisible(member.isShowName());
                applyGlowColor(entity, config);

                tagEntity(entity, mobId, catchXpMultiplierPercent);
                if (killAll) {
                    tagGroupKillAll(entity);
                    tagGroupUUID(entity, groupUUID);
                }

                // Stats
                applyGroupMemberStats(entity, member);

                // Aggro
                if (member.hasAggro()) {
                    SpawnConfig.AggroConfig aggro = member.getAggro();
                    tagPassiveAggro(entity, aggro.getSpeed(), aggro.getDamage(), aggro.getRange(), aggro.getHitInterval());
                }

                // Potion effects
                applyPotionEffects(entity, member.getPotionEffects());

                aggroNearest(entity, location);

                // Fire resistance — prevents daylight burning
                applyFireResistance(entity);

                markLavaCreatureIfNeeded(entity, location);

                // Nested rider (e.g., pillager on ravager)
                if (member.hasRider()) {
                    SpawnConfig.GroupMemberConfig riderConfig = member.getRider();
                    LivingEntity riderEntity = (LivingEntity) location.getWorld().spawnEntity(offset, riderConfig.getEntityType());
                    String riderName = riderConfig.getDisplayName() != null ? riderConfig.getDisplayName() : def.getDisplayName();
                    if (nameColor != null) {
                        riderEntity.customName(net.kyori.adventure.text.Component.text(riderName).color(nameColor));
                    } else {
                        riderEntity.customName(net.kyori.adventure.text.Component.text(riderName));
                    }
                    riderEntity.setCustomNameVisible(true);
                    applyGlowColor(riderEntity, config);
                    tagEntity(riderEntity, mobId, catchXpMultiplierPercent);
                    if (killAll) tagGroupKillAll(riderEntity);
                    applyGroupMemberStats(riderEntity, riderConfig);
                    entity.addPassenger(riderEntity);
                    aggroNearest(riderEntity, location);
                    applyFireResistance(riderEntity);
                    markLavaCreatureIfNeeded(riderEntity, location);
                }
            }
        }
        return firstEntity;
    }

    /**
     * Scaled spawn: size-scaled entity (slimes). Optionally GROUP_KILL_ALL.
     */
    private LivingEntity spawnScaled(Location location, String mobId, CustomMob def, SpawnConfig config,
                                     double catchXpMultiplierPercent) {
        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, def.getEntityType());

        // Slime size
        if (config.getSlimeSize() > 0 && entity instanceof org.bukkit.entity.Slime slime) {
            slime.setSize(config.getSlimeSize());
        }

        // Name with color
        net.kyori.adventure.text.format.NamedTextColor nameColor = resolveNameColor(config, def);
        if (nameColor != null) {
            entity.customName(net.kyori.adventure.text.Component.text(def.getDisplayName()).color(nameColor));
        } else {
            entity.customName(net.kyori.adventure.text.Component.text(def.getDisplayName()));
        }
        entity.setCustomNameVisible(true);
        applyGlowColor(entity, config);

        tagEntity(entity, mobId, catchXpMultiplierPercent);
        if (config.isGroupKillAll()) {
            tagGroupKillAll(entity);
            // Unique UUID for this spawn — used for slime split reward dedup
            tagGroupUUID(entity, java.util.UUID.randomUUID().toString());
        }

        // Stats
        applyEntityStats(entity, config, def.isHostile());

        aggroNearest(entity, location);

        // Fire resistance — prevents daylight burning
        applyFireResistance(entity);

        markLavaCreatureIfNeeded(entity, location);

        return entity;
    }

    // ── Stat Application ──────────────────────────────────────

    /**
     * Applies health/damage/scale stats from SpawnConfig to an entity.
     * If no config or -1 values, uses category defaults (2x/2x for hostile).
     */
    private void applyEntityStats(LivingEntity entity, SpawnConfig config, boolean hostile) {
        try {
            if (entity instanceof org.bukkit.entity.Ageable ageable) {
                ageable.setAdult();
            }
            if (entity instanceof org.bukkit.entity.Zombie zombie) {
                zombie.setBaby(false);
            }
        } catch (Exception ignored) {}

        double healthMult = -1, damageMult = -1, absHealth = -1, absDamage = -1, scale = -1;

        if (config != null) {
            healthMult = config.getHealthMultiplier();
            damageMult = config.getDamageMultiplier();
            absHealth = config.getHealth();
            absDamage = config.getDamage();
            scale = config.getScale();
        }

        // Defaults for hostile mobs without explicit config
        if (hostile) {
            if (absHealth <= 0 && healthMult <= 0) healthMult = plugin.getConfig().getDouble("mob_balance.hostile_default_health_mult", 2.0);
            if (absDamage <= 0 && damageMult <= 0) damageMult = plugin.getConfig().getDouble("mob_balance.hostile_default_damage_mult", 2.0);
        }

        try {
            // Scale
            if (scale > 0) {
                org.bukkit.attribute.AttributeInstance scaleAttr = entity.getAttribute(Attribute.SCALE);
                if (scaleAttr != null) scaleAttr.setBaseValue(scale);
            }

            // Health
            if (absHealth > 0) {
                org.bukkit.attribute.AttributeInstance absHp = entity.getAttribute(Attribute.MAX_HEALTH);
                if (absHp != null) {
                    absHp.setBaseValue(absHealth);
                    entity.setHealth(absHealth);
                }
            } else if (healthMult > 0 && healthMult != 1.0) {
                org.bukkit.attribute.AttributeInstance hp = entity.getAttribute(Attribute.MAX_HEALTH);
                if (hp != null) {
                    double newHp = hp.getBaseValue() * healthMult;
                    hp.setBaseValue(newHp);
                    entity.setHealth(newHp);
                }
            }

            // Damage
            if (absDamage > 0) {
                org.bukkit.attribute.AttributeInstance dmg = entity.getAttribute(Attribute.ATTACK_DAMAGE);
                if (dmg != null) dmg.setBaseValue(absDamage);
            } else if (damageMult > 0 && damageMult != 1.0) {
                org.bukkit.attribute.AttributeInstance dmg = entity.getAttribute(Attribute.ATTACK_DAMAGE);
                if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * damageMult);
            }
        } catch (Exception ignored) {}
    }

    /** Applies stats from a mount EntityConfig. */
    private void applyMountStats(LivingEntity entity, SpawnConfig.EntityConfig mc) {
        try {
            if (mc.getScale() > 0) {
                org.bukkit.attribute.AttributeInstance scaleAttr = entity.getAttribute(Attribute.SCALE);
                if (scaleAttr != null) scaleAttr.setBaseValue(mc.getScale());
            }
            if (mc.getHealth() > 0) {
                org.bukkit.attribute.AttributeInstance mhp = entity.getAttribute(Attribute.MAX_HEALTH);
                if (mhp != null) {
                    mhp.setBaseValue(mc.getHealth());
                    entity.setHealth(mc.getHealth());
                }
            } else if (mc.getHealthMultiplier() > 0 && mc.getHealthMultiplier() != 1.0) {
                org.bukkit.attribute.AttributeInstance hp = entity.getAttribute(Attribute.MAX_HEALTH);
                if (hp != null) {
                    double newHp = hp.getBaseValue() * mc.getHealthMultiplier();
                    hp.setBaseValue(newHp);
                    entity.setHealth(newHp);
                }
            }
            if (mc.getDamage() > 0) {
                org.bukkit.attribute.AttributeInstance dmg = entity.getAttribute(Attribute.ATTACK_DAMAGE);
                if (dmg != null) dmg.setBaseValue(mc.getDamage());
            } else if (mc.getDamageMultiplier() > 0 && mc.getDamageMultiplier() != 1.0) {
                org.bukkit.attribute.AttributeInstance dmg = entity.getAttribute(Attribute.ATTACK_DAMAGE);
                if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * mc.getDamageMultiplier());
            }
        } catch (Exception ignored) {}
    }

    /** Applies stats from a GroupMemberConfig. */
    private void applyGroupMemberStats(LivingEntity entity, SpawnConfig.GroupMemberConfig mc) {
        try {
            if (mc.getHealth() > 0) {
                org.bukkit.attribute.AttributeInstance ghp = entity.getAttribute(Attribute.MAX_HEALTH);
                if (ghp != null) {
                    ghp.setBaseValue(mc.getHealth());
                    entity.setHealth(mc.getHealth());
                }
            } else if (mc.getHealthMultiplier() > 0 && mc.getHealthMultiplier() != 1.0) {
                org.bukkit.attribute.AttributeInstance hp = entity.getAttribute(Attribute.MAX_HEALTH);
                if (hp != null) {
                    double newHp = hp.getBaseValue() * mc.getHealthMultiplier();
                    hp.setBaseValue(newHp);
                    entity.setHealth(newHp);
                }
            }
            if (mc.getDamage() > 0) {
                org.bukkit.attribute.AttributeInstance dmg = entity.getAttribute(Attribute.ATTACK_DAMAGE);
                if (dmg != null) dmg.setBaseValue(mc.getDamage());
            } else if (mc.getDamageMultiplier() > 0 && mc.getDamageMultiplier() != 1.0) {
                org.bukkit.attribute.AttributeInstance dmg = entity.getAttribute(Attribute.ATTACK_DAMAGE);
                if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * mc.getDamageMultiplier());
            }
        } catch (Exception ignored) {}
    }

    // ── Equipment Resolution ──────────────────────────────────

    /**
     * Resolves an equipment slot string to an ItemStack.
     * <ul>
     *   <li>{@code skull:BASE64}                      — custom player head</li>
     *   <li>{@code dyed:MATERIAL:#RRGGBB}             — dyed leather armor</li>
    *   <li>{@code dyed_trimmed:MATERIAL:#RRGGBB:TRIM_MATERIAL:TRIM_PATTERN} — dyed+trimmed leather armor</li>
     *   <li>{@code enchanted:MAT:ENCH:LVL}            — enchanted vanilla item</li>
     *   <li>{@code tipped_arrow:EFF:DUR:AMP:AMT}      — tipped arrows</li>
     *   <li>(default)                                  — ItemManager lookup, fallback vanilla Material</li>
     * </ul>
     */
    private ItemStack resolveEquipmentItem(String spec) {
        if (spec == null || spec.isEmpty()) return null;

        if (spec.startsWith("skull:")) {
            return plugin.getItemManager().getCustomSkull(spec.substring(6));
        }

        if (spec.startsWith("dyed:")) {
            // dyed:LEATHER_CHESTPLATE:#005064
            String[] parts = spec.substring(5).split(":");
            if (parts.length >= 2) {
                org.bukkit.Material mat = org.bukkit.Material.valueOf(parts[0]);
                org.bukkit.Color color = org.bukkit.Color.fromRGB(
                        Integer.parseInt(parts[1].replace("#", ""), 16));
                return createDyedLeather(mat, color);
            }
        }

        if (spec.startsWith("dyed_trimmed:")) {
            // dyed_trimmed:LEATHER_CHESTPLATE:#2f3136:DIAMOND:WARD
            String[] parts = spec.substring(13).split(":");
            if (parts.length >= 4) {
                org.bukkit.Material mat = org.bukkit.Material.valueOf(parts[0]);
                org.bukkit.Color color = org.bukkit.Color.fromRGB(
                        Integer.parseInt(parts[1].replace("#", ""), 16));

                ItemStack item = createDyedLeather(mat, color);
                org.bukkit.inventory.meta.ItemMeta rawMeta = item.getItemMeta();
                if (rawMeta instanceof ArmorMeta armorMeta) {
                    TrimMaterial trimMaterial = org.bukkit.Registry.TRIM_MATERIAL.get(
                            org.bukkit.NamespacedKey.minecraft(parts[2].toLowerCase(Locale.ROOT)));
                    TrimPattern trimPattern = org.bukkit.Registry.TRIM_PATTERN.get(
                            org.bukkit.NamespacedKey.minecraft(parts[3].toLowerCase(Locale.ROOT)));
                    if (trimMaterial != null && trimPattern != null) {
                        armorMeta.setTrim(new ArmorTrim(trimMaterial, trimPattern));
                        item.setItemMeta(armorMeta);
                    }
                }
                return item;
            }
        }

        if (spec.startsWith("enchanted:")) {
            // enchanted:TRIDENT:IMPALING:5
            String[] parts = spec.substring(10).split(":");
            if (parts.length >= 3) {
                org.bukkit.Material mat = org.bukkit.Material.valueOf(parts[0]);
                org.bukkit.enchantments.Enchantment ench = org.bukkit.Registry.ENCHANTMENT.get(
                        org.bukkit.NamespacedKey.minecraft(parts[1].toLowerCase()));
                int level = Integer.parseInt(parts[2]);
                ItemStack item = new ItemStack(mat);
                if (ench != null) {
                    org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                    meta.addEnchant(ench, level, true);
                    item.setItemMeta(meta);
                }
                return item;
            }
        }

        if (spec.startsWith("tipped_arrow:")) {
            // tipped_arrow:WEAKNESS:200:1:64
            String[] parts = spec.substring(13).split(":");
            if (parts.length >= 4) {
                int amount = Integer.parseInt(parts[3]);
                ItemStack arrows = new ItemStack(org.bukkit.Material.TIPPED_ARROW, amount);
                org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) arrows.getItemMeta();
                org.bukkit.potion.PotionEffectType effectType = org.bukkit.Registry.POTION_EFFECT_TYPE.get(
                        org.bukkit.NamespacedKey.minecraft(parts[0].toLowerCase()));
                if (effectType != null) {
                    meta.addCustomEffect(new org.bukkit.potion.PotionEffect(
                            effectType, Integer.parseInt(parts[1]), Integer.parseInt(parts[2])), true);
                }
                arrows.setItemMeta(meta);
                return arrows;
            }
        }

        // Try ItemManager first
        ItemStack item = plugin.getItemManager().getItem(spec);
        if (item != null) return item;

        // Fallback: vanilla Material
        try {
            return new ItemStack(org.bukkit.Material.valueOf(spec));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Applies equipment config to an entity. */
    private void applyEquipment(LivingEntity entity, SpawnConfig.EquipmentConfig equip) {
        org.bukkit.inventory.EntityEquipment ee = entity.getEquipment();
        if (ee == null) return;

        ItemStack helmet = resolveEquipmentItem(equip.getHelmet());
        if (helmet != null) ee.setHelmet(helmet);
        ItemStack chestplate = resolveEquipmentItem(equip.getChestplate());
        if (chestplate != null) ee.setChestplate(chestplate);
        ItemStack leggings = resolveEquipmentItem(equip.getLeggings());
        if (leggings != null) ee.setLeggings(leggings);
        ItemStack boots = resolveEquipmentItem(equip.getBoots());
        if (boots != null) ee.setBoots(boots);
        ItemStack mainhand = resolveEquipmentItem(equip.getMainhand());
        if (mainhand != null) ee.setItemInMainHand(mainhand);
        ItemStack offhand = resolveEquipmentItem(equip.getOffhand());
        if (offhand != null) ee.setItemInOffHand(offhand);
    }

    /** Applies potion effects to an entity. */
    private void applyPotionEffects(LivingEntity entity, List<SpawnConfig.PotionConfig> effects) {
        if (effects == null || effects.isEmpty()) return;
        for (SpawnConfig.PotionConfig pe : effects) {
            org.bukkit.potion.PotionEffectType effectType = org.bukkit.Registry.POTION_EFFECT_TYPE.get(
                    org.bukkit.NamespacedKey.minecraft(pe.getType().toLowerCase()));
            if (effectType != null) {
                int duration = pe.getDuration() < 0 ? Integer.MAX_VALUE : pe.getDuration();
                entity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        effectType, duration, pe.getAmplifier(), false, false));
            }
        }
    }

    // ── Name Color Resolution ─────────────────────────────────

    /** Resolves a name color from SpawnConfig, defaulting to RED for hostiles. */
    private net.kyori.adventure.text.format.NamedTextColor resolveNameColor(SpawnConfig config, CustomMob def) {
        if (config != null && config.getNameColor() != null) {
            return resolveColor(config.getNameColor());
        }
        return def.isHostile() ? net.kyori.adventure.text.format.NamedTextColor.RED : null;
    }

    /** Parses a named text color string. */
    private net.kyori.adventure.text.format.NamedTextColor resolveColor(String name) {
        if (name == null) return null;
        return switch (name.toUpperCase()) {
            case "ORANGE" -> net.kyori.adventure.text.format.NamedTextColor.GOLD;
            case "DARK_RED" -> net.kyori.adventure.text.format.NamedTextColor.DARK_RED;
            case "RED" -> net.kyori.adventure.text.format.NamedTextColor.RED;
            case "AQUA" -> net.kyori.adventure.text.format.NamedTextColor.AQUA;
            case "GOLD" -> net.kyori.adventure.text.format.NamedTextColor.GOLD;
            case "GRAY", "GREY" -> net.kyori.adventure.text.format.NamedTextColor.GRAY;
            case "DARK_GREEN" -> net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN;
            case "BLACK" -> net.kyori.adventure.text.format.NamedTextColor.BLACK;
            case "DARK_GRAY", "DARK_GREY" -> net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
            case "DARK_PURPLE" -> net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE;
            case "DARK_AQUA" -> net.kyori.adventure.text.format.NamedTextColor.DARK_AQUA;
            case "GREEN" -> net.kyori.adventure.text.format.NamedTextColor.GREEN;
            case "YELLOW" -> net.kyori.adventure.text.format.NamedTextColor.YELLOW;
            case "WHITE" -> net.kyori.adventure.text.format.NamedTextColor.WHITE;
            default -> net.kyori.adventure.text.format.NamedTextColor.RED;
        };
    }

    private void applyGlowColor(LivingEntity entity, SpawnConfig config) {
        if (entity == null || config == null || config.getGlowColor() == null || config.getGlowColor().isBlank()) return;

        net.kyori.adventure.text.format.NamedTextColor glowColor = resolveColor(config.getGlowColor());
        if (glowColor == null) return;

        Team team = getOrCreateGlowTeam(config.getGlowColor(), glowColor);
        if (team == null) return;

        String entry = entity.getUniqueId().toString();
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
        entity.setGlowing(true);
    }

    public void removeGlowColorEntry(java.util.UUID entityId) {
        if (entityId == null) return;
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard scoreboard = manager.getMainScoreboard();
        String entry = entityId.toString();

        for (Team team : scoreboard.getTeams()) {
            if (!team.getName().startsWith("fr_glow_")) continue;
            if (team.hasEntry(entry)) {
                team.removeEntry(entry);
            }
        }
    }

    private Team getOrCreateGlowTeam(String colorName, net.kyori.adventure.text.format.NamedTextColor color) {
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return null;
        Scoreboard scoreboard = manager.getMainScoreboard();

        String suffix = (colorName == null ? "red" : colorName.toLowerCase(Locale.ROOT)).replace(' ', '_');
        if (suffix.length() > 8) {
            suffix = suffix.substring(0, 8);
        }
        String teamName = "fr_glow_" + suffix;
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        team.color(color);
        return team;
    }

    // ── Spawn Helpers ─────────────────────────────────────────

    /** Tags a living entity as a fished mob with shared mob ID. */
    private void tagEntity(LivingEntity entity, String mobId, double catchXpMultiplierPercent) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(FISHED_MOB_KEY, PersistentDataType.BYTE, (byte) 1);
        pdc.set(MOB_ID_KEY, PersistentDataType.STRING, mobId);
        pdc.set(FISH_WEIGHT_KEY, PersistentDataType.DOUBLE, 0.0);
        pdc.set(CATCH_XP_MULTIPLIER_KEY, PersistentDataType.DOUBLE, Math.max(0.0, catchXpMultiplierPercent));
        addActiveMob(entity.getUniqueId());
    }

    public double getCatchXpMultiplierPercent(LivingEntity entity) {
        if (entity == null) return 0.0;
        return entity.getPersistentDataContainer().getOrDefault(
                CATCH_XP_MULTIPLIER_KEY,
                PersistentDataType.DOUBLE,
                0.0
        );
    }

    /** Applies weight and scale variance to a tagged entity. */
    private void applyWeightScale(LivingEntity entity, CustomMob def) {
        double variancePercent = plugin.getConfig().getDouble("mob_balance.size_variance_percent", 0.3)
                * ThreadLocalRandom.current().nextDouble();
        double scale = 1.0 + variancePercent;
        double weight = def.getBaseWeight() * scale;

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(FISH_WEIGHT_KEY, PersistentDataType.DOUBLE, weight);
        pdc.set(MOB_SCALE_KEY, PersistentDataType.DOUBLE, scale);

        // Visual scale (only if no explicit scale override from config)
        try {
            org.bukkit.attribute.AttributeInstance scaleAttr = entity.getAttribute(Attribute.SCALE);
            if (scaleAttr != null && scaleAttr.getBaseValue() == scaleAttr.getDefaultValue()) {
                scaleAttr.setBaseValue(scale);
            }
            // Health scaling with size variance
            org.bukkit.attribute.AttributeInstance healthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
            if (healthAttr != null) {
                double newMaxHealth = healthAttr.getBaseValue() * scale;
                healthAttr.setBaseValue(newMaxHealth);
                entity.setHealth(newMaxHealth);
            }
        } catch (Exception ignored) {}
    }

    /** Makes a mob aggressive toward nearest player. */
    private void aggroNearest(LivingEntity entity, Location location) {
        if (entity instanceof org.bukkit.entity.Mob mob) {
            double aggroRange = plugin.getConfig().getDouble("mob_balance.initial_aggro_range", 32);
            org.bukkit.entity.Player nearest = location.getWorld().getNearbyPlayers(location, aggroRange).stream().findFirst().orElse(null);
            if (nearest != null) {
                mob.setTarget(nearest);
            }
        }
    }

    /** Tags an entity with passive-aggro AI config for AggroTask. */
    private void tagPassiveAggro(LivingEntity entity, double speed, double damage, double range, int hitIntervalTicks) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(AGGRO_SPEED_KEY, PersistentDataType.DOUBLE, speed);
        pdc.set(AGGRO_DAMAGE_KEY, PersistentDataType.DOUBLE, damage);
        pdc.set(AGGRO_RANGE_KEY, PersistentDataType.DOUBLE, range);
        pdc.set(AGGRO_HIT_INTERVAL_KEY, PersistentDataType.INTEGER, hitIntervalTicks);
    }

    /** Tags an entity for group kill-all reward logic. */
    private void tagGroupKillAll(LivingEntity entity) {
        entity.getPersistentDataContainer().set(GROUP_KILL_ALL_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    /** Tags an entity with a group UUID for reward dedup across separate spawns. */
    public void tagGroupUUID(LivingEntity entity, String uuid) {
        entity.getPersistentDataContainer().set(GROUP_UUID_KEY, PersistentDataType.STRING, uuid);
    }

    /** Gets the group UUID from entity PDC, or null if not present. */
    public String getGroupUUID(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(GROUP_UUID_KEY, PersistentDataType.STRING, null);
    }

    /** Tags mounted sea entities to use shared HP handling in MobListener. */
    public void tagSharedMountedHp(LivingEntity entity) {
        entity.getPersistentDataContainer().set(SHARED_MOUNTED_HP_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    /** Checks whether entity uses shared mounted HP behavior. */
    public boolean hasSharedMountedHp(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(SHARED_MOUNTED_HP_KEY, PersistentDataType.BYTE);
    }

    /** Tags an entity with a particle type for AggroTask. */
    private void tagParticle(LivingEntity entity, String particleType) {
        entity.getPersistentDataContainer().set(PARTICLE_TYPE_KEY, PersistentDataType.STRING, particleType);
    }

    /** Grants infinite fire resistance to a fished entity (prevents daylight burning). */
    private void applyFireResistance(LivingEntity entity) {
        entity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        entity.setVisualFire(false);
    }

    private void markLavaCreatureIfNeeded(LivingEntity entity, Location spawnLocation) {
        if (plugin.getLavaCreatureManager() == null || entity == null || spawnLocation == null) return;

        Material at = spawnLocation.getBlock().getType();
        Material below = spawnLocation.clone().subtract(0, 1, 0).getBlock().getType();
        if (entity.isInLava() || at == Material.LAVA || below == Material.LAVA) {
            plugin.getLavaCreatureManager().trackCreature(entity);
        }
    }

    /**
     * Pulls a sea creature toward the player who fished it up.
     * Runs every 5 ticks for up to 3 seconds, or until within 2 blocks.
     */
    private void pullTowardPlayer(LivingEntity entity, Player player) {
        int maxTicks = plugin.getConfig().getInt("mob_balance.pull_max_duration_ticks", 60);
        double stopDist = plugin.getConfig().getDouble("mob_balance.pull_stop_distance", 2.0);
        double maxSpeed = plugin.getConfig().getDouble("mob_balance.pull_max_speed", 1.2);
        double speedMult = plugin.getConfig().getDouble("mob_balance.pull_speed_multiplier", 0.15);
        long initialDelay = plugin.getConfig().getLong("mob_balance.pull_initial_delay_ticks", 2);
        long interval = plugin.getConfig().getLong("mob_balance.pull_interval_ticks", 5);

        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (entity.isDead() || !entity.isValid() || !player.isOnline() || ticks >= maxTicks) {
                    cancel();
                    return;
                }
                double distance = entity.getLocation().distance(player.getLocation());
                if (distance < stopDist) {
                    cancel();
                    return;
                }
                org.bukkit.util.Vector direction = player.getLocation().toVector()
                        .subtract(entity.getLocation().toVector()).normalize();
                double speed = Math.min(maxSpeed, distance * speedMult);
                entity.setVelocity(direction.multiply(speed));
                ticks += (int) interval;
            }
        };
        task.runTaskTimer(plugin, initialDelay, interval);
    }

    /** Creates a dyed leather armor piece. */
    private ItemStack createDyedLeather(org.bukkit.Material material, org.bukkit.Color color) {
        ItemStack item = new ItemStack(material);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            boolean colored = false;
            try {
                java.lang.reflect.Method setColor = meta.getClass().getMethod("setColor", org.bukkit.Color.class);
                setColor.invoke(meta, color);
                colored = true;
            } catch (Exception ignored) {
                if (meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta leatherMeta) {
                    leatherMeta.setColor(color);
                    colored = true;
                }
            }
            if (colored) {
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    /** Checks if a mob is a group-kill-all mob (reads PDC tag). */
    public boolean isGroupKillAll(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(GROUP_KILL_ALL_KEY, PersistentDataType.BYTE);
    }

    private void applySharedMountedHealthPool(LivingEntity mount, LivingEntity rider) {
        org.bukkit.attribute.AttributeInstance mountHp = mount.getAttribute(Attribute.MAX_HEALTH);
        org.bukkit.attribute.AttributeInstance riderHp = rider.getAttribute(Attribute.MAX_HEALTH);
        if (mountHp == null || riderHp == null) return;

        // Shared HP uses rider as the single source of truth.
        double sharedMax = riderHp.getBaseValue() > 0 ? riderHp.getBaseValue() : mountHp.getBaseValue();
        sharedMax = Math.max(1.0, sharedMax);
        mountHp.setBaseValue(sharedMax);
        riderHp.setBaseValue(sharedMax);
        mount.setHealth(sharedMax);
        rider.setHealth(sharedMax);
    }

    private boolean shouldUseSharedMountedHp(String mobId, EntityType mountType, EntityType riderType) {
        if (isSeaCreatureType(mountType) || isSeaCreatureType(riderType)) {
            return true;
        }
        if (mobId == null) return false;
        return switch (mobId) {
            case "dune_rider", "temple_guardian", "spider_jockey", "crimson_abomination", "nether_lord" -> true;
            default -> false;
        };
    }

    private boolean isSeaCreatureType(EntityType type) {
        if (type == null) return false;
        return switch (type) {
            case DOLPHIN,
                 SQUID,
                 GLOW_SQUID,
                 GUARDIAN,
                 ELDER_GUARDIAN,
                 TURTLE,
                 COD,
                 SALMON,
                 TROPICAL_FISH,
                 PUFFERFISH,
                 AXOLOTL,
                 DROWNED -> true;
            default -> false;
        };
    }

    // ── Mob Selection ─────────────────────────────────────────

    /**
     * Biome-aware mob selection for fishing events.
     * Passive mob weights are adjusted per biome. Hostile mob weights are unchanged.
     * An independent land-mob bonus roll may fire before the main pool.
     * Glow Squid also spawns anywhere below Y=30.
     */
    public String getMobToSpawn(Player player, Skill skill, Location fishLocation) {
        return getMobToSpawn(player, skill, fishLocation, 0, 0);
    }

    /**
     * Bait-aware mob selection. Extra bonuses from bait are added to the
     * rare creature and treasure multipliers.
     */
    public String getMobToSpawn(Player player, Skill skill, Location fishLocation,
                                 double baitRareCreatureBonus, double baitTreasureBonus) {
        return getMobToSpawn(player, skill, fishLocation, baitRareCreatureBonus, baitTreasureBonus, null);
    }

    public String getMobToSpawn(Player player, Skill skill, Location fishLocation,
                                 double baitRareCreatureBonus, double baitTreasureBonus,
                                 String targetedHostileMobId) {
        Collection<String> targetedHostileMobIds = (targetedHostileMobId == null || targetedHostileMobId.isBlank())
            ? List.of()
            : List.of(targetedHostileMobId);
        return getMobToSpawn(player, skill, fishLocation, baitRareCreatureBonus, baitTreasureBonus,
            targetedHostileMobIds, Set.of());
        }

        public String getMobToSpawn(Player player, Skill skill, Location fishLocation,
                     double baitRareCreatureBonus, double baitTreasureBonus,
                     Collection<String> targetedHostileMobIds,
                     Set<BiomeGroup> nativeBiomeGroups) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        int level = data != null ? data.getLevel(skill) : 0;

        double rareBonus = getEquipmentRareCreatureBonus(player) + baitRareCreatureBonus;
        double hostileMultiplier = 1.0 + (rareBonus / 100.0);

        BiomeGroup biomeGroup = resolveBiomeGroup(fishLocation);
        BiomeFishingProfile biomeProfile = plugin.getBiomeFishingRegistry().get(biomeGroup);
        
        boolean isHarmonyRod = plugin.getItemManager().isHarmonyRod(player.getInventory().getItemInMainHand()) 
                            || plugin.getItemManager().isHarmonyRod(player.getInventory().getItemInOffHand());

        // 1. Land mob bonus roll (independent from fish pool)
        if (biomeProfile != null && !biomeProfile.getLandMobs().isEmpty()) {
            double landChance = getEffectiveLandMobChance(biomeProfile);
            if (isHarmonyRod) {
                landChance *= plugin.getConfig().getDouble("item_balance.harmony_rod_land_mob_multiplier", 2.0);
            }
            
            if (ThreadLocalRandom.current().nextDouble() * 100 < landChance) {
                List<String> pool = biomeProfile.getLandMobs();
                String landMob = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                CustomMob landDef = plugin.getMobRegistry().get(landMob);
                if (landDef != null && level >= landDef.getRequiredLevel()) {
                    return landMob;
                }
            }
        }

        // 2. Build biome-aware weight map
        // Calculate multipliers
        double treasureBonus = getTreasureChance(player) + baitTreasureBonus;
        // Power curve: makes treasure gear feel progressively more impactful
        double treasureMultiplier = Math.pow(1.0 + (treasureBonus / 100.0), plugin.getConfig().getDouble("treasure_balance.power_curve_exponent", 3.0));

        Map<String, Double> mobWeights = buildWeightMap(
            skill,
            level,
            hostileMultiplier,
            treasureMultiplier,
            biomeProfile,
            fishLocation,
            isHarmonyRod,
            targetedHostileMobIds,
            nativeBiomeGroups
        );

        // 3. Roll and select
        return rollWeightedMob(skill, mobWeights);
    }

    /** Backwards-compatible overload (no biome adjustments). Used by admin commands. */
    public String getMobToSpawn(Player player, Skill skill) {
        return getMobToSpawn(player, skill, player.getLocation());
    }

    /**
     * Builds the effective weight map for a fishing event, applying biome overrides
     * and Y-level checks. Hostile weights are now biome-aware via hostileWeights.
     */
    public Map<String, Double> buildWeightMap(Skill skill, int level, double hostileMultiplier, double treasureMultiplier,
                                                BiomeFishingProfile biomeProfile, Location fishLocation, boolean isHarmonyRod) {
        return buildWeightMap(skill, level, hostileMultiplier, treasureMultiplier, biomeProfile, fishLocation, isHarmonyRod, null);
    }

    public Map<String, Double> buildWeightMap(Skill skill, int level, double hostileMultiplier, double treasureMultiplier,
                                              BiomeFishingProfile biomeProfile, Location fishLocation, boolean isHarmonyRod,
                                              String targetedHostileMobId) {
        Collection<String> targetedHostileMobIds = (targetedHostileMobId == null || targetedHostileMobId.isBlank())
            ? List.of()
            : List.of(targetedHostileMobId);
        return buildWeightMap(skill, level, hostileMultiplier, treasureMultiplier, biomeProfile, fishLocation,
            isHarmonyRod, targetedHostileMobIds, Set.of());
        }

        public Map<String, Double> buildWeightMap(Skill skill, int level, double hostileMultiplier, double treasureMultiplier,
                              BiomeFishingProfile biomeProfile, Location fishLocation, boolean isHarmonyRod,
                              Collection<String> targetedHostileMobIds,
                              Set<BiomeGroup> nativeBiomeGroups) {
        List<CustomMob> mobs = plugin.getMobRegistry().getBySkill(skill);
        Map<String, Double> mobWeights = new HashMap<>();
        boolean glowSquidEligible = fishLocation != null 
                && fishLocation.getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL
                && fishLocation.getY() < plugin.getConfig().getInt("mob_balance.glow_squid_y_threshold", 30);

        // Check if it's night time for night-only hostiles
        long nightStart = plugin.getConfig().getLong("mob_balance.night_start_time", 13000);
        long nightEnd = plugin.getConfig().getLong("mob_balance.night_end_time", 23000);
        boolean isNight = fishLocation != null && fishLocation.getWorld() != null
                && (fishLocation.getWorld().getTime() >= nightStart && fishLocation.getWorld().getTime() < nightEnd);

        double totalHostileWeight = 0.0;
        double totalPassiveWeight = 0.0;

        for (CustomMob mob : mobs) {
            if (level < mob.getRequiredLevel()) continue;

            double weight;

            if (mob.isHostile()) {
                if (biomeProfile != null && biomeProfile.hasHostileWeight(mob.getId())) {
                    if (biomeProfile.isNightOnly(mob.getId()) && !isNight) {
                        continue; // Skip night-only mobs during daytime
                    }
                    weight = biomeProfile.getHostileWeight(mob.getId());
                } else if (biomeProfile != null && !biomeProfile.getHostileWeights().isEmpty()) {
                    continue; // Skip it
                } else {
                    weight = getConfigChance(mob);
                }
                
                if (mob.isBoostByRareCreature()) {
                    weight *= hostileMultiplier;
                }
            } else if (mob.isTreasure()) {
                // Treasure chests: Base chance + Treasure Chance stat
                weight = getConfigChance(mob);
                weight *= treasureMultiplier;
            } else {
                if (biomeProfile != null && biomeProfile.hasWeight(mob.getId())) {
                    weight = biomeProfile.getWeight(mob.getId());
                } else if (biomeProfile != null && !biomeProfile.getPassiveWeights().isEmpty()) {
                    weight = 0; // Biome has custom passives, and this mob isn't listed
                } else {
                    weight = getConfigChance(mob);
                }
                
                totalPassiveWeight += weight;
            }
            
            // Add to map only if weight is positive
            if (weight > 0) {
                mobWeights.put(mob.getId(), weight);
                if (mob.isHostile()) {
                    totalHostileWeight += weight;
                }
            }
        }

        // Glow Squid Y-level bonus: spawns anywhere below Y=30 even if not in biome profile
        if (glowSquidEligible && !mobWeights.containsKey("glow_squid")) {
            CustomMob glowDef = plugin.getMobRegistry().get("glow_squid");
            if (glowDef != null && level >= glowDef.getRequiredLevel()) {
                double glowWeight = plugin.getConfig().getDouble("mob_balance.glow_squid_depth_weight", 5.0);
                mobWeights.put("glow_squid", glowWeight);
                totalPassiveWeight += glowWeight;
            }
        }

        // Harmony Rod Logic: Redistribute hostile weight to passives
        if (isHarmonyRod && totalHostileWeight > 0) {
            if (totalPassiveWeight > 0) {
                for (Map.Entry<String, Double> entry : new HashMap<>(mobWeights).entrySet()) {
                    String id = entry.getKey();
                    if (isHostile(id)) {
                        mobWeights.remove(id); // Remove hostile
                    } else if (id.equals("glow_squid") || (!plugin.getMobRegistry().get(id).isTreasure())) {
                        // Passive: Increase weight
                        double oldWeight = entry.getValue();
                        double addedWeight = (oldWeight / totalPassiveWeight) * totalHostileWeight;
                        mobWeights.put(id, oldWeight + addedWeight);
                    }
                    // Treasures remain unchanged
                }
            } else {
                // No passives to distribute to? Just remove hostiles.
                for (String id : new java.util.HashSet<>(mobWeights.keySet())) {
                    if (isHostile(id)) mobWeights.remove(id);
                }
            }
        }

        BiomeGroup currentBiomeGroup = resolveBiomeGroup(fishLocation);
        applyTargetedHostileBaits(mobWeights, level, hostileMultiplier, biomeProfile,
                targetedHostileMobIds, currentBiomeGroup, nativeBiomeGroups);

        return mobWeights;
    }

    private void applyTargetedHostileBaits(Map<String, Double> mobWeights,
                                           int level,
                                           double hostileMultiplier,
                                           BiomeFishingProfile biomeProfile,
                                           Collection<String> targetedHostileMobIds,
                                           BiomeGroup currentBiomeGroup,
                                           Set<BiomeGroup> nativeBiomeGroups) {
        if (targetedHostileMobIds == null || targetedHostileMobIds.isEmpty()) return;

        for (String targetedHostileMobId : targetedHostileMobIds) {
            if (targetedHostileMobId == null || targetedHostileMobId.isBlank()) continue;

            CustomMob targeted = plugin.getMobRegistry().get(targetedHostileMobId);
            if (targeted == null || !targeted.isHostile()) continue;
            if (level < targeted.getRequiredLevel()) continue;

            double baseWeight = getConfigChance(targeted);
            if (baseWeight <= 0.0) {
                baseWeight = getFallbackHostileBaitBaseWeight(targetedHostileMobId, targeted);
            }
            if (baseWeight <= 0.0) continue;

            boolean hasNativeSet = nativeBiomeGroups != null && !nativeBiomeGroups.isEmpty();
            boolean inNativeBiome = hasNativeSet
                    ? nativeBiomeGroups.contains(currentBiomeGroup)
                    : biomeProfile != null && biomeProfile.hasHostileWeight(targetedHostileMobId);

            double baitMultiplier = inNativeBiome ? 2.0 : 1.0;
            double boostedWeight = baseWeight * hostileMultiplier * baitMultiplier;
            mobWeights.put(targetedHostileMobId, boostedWeight);
        }
    }

    private double getFallbackHostileBaitBaseWeight(String mobId, CustomMob mob) {
        double maxBiomeWeight = 0.0;
        for (BiomeFishingProfile profile : plugin.getBiomeFishingRegistry().getProfiles().values()) {
            if (profile != null && profile.hasHostileWeight(mobId)) {
                maxBiomeWeight = Math.max(maxBiomeWeight, profile.getHostileWeight(mobId));
            }
        }

        if (maxBiomeWeight > 0.0) {
            return maxBiomeWeight;
        }

        return mob.getDefaultChance();
    }

    /** Weighted random selection from a pre-built weight map. Always selects a mob (no vanilla fallback). */
    private String rollWeightedMob(Skill skill, Map<String, Double> mobWeights) {
        double totalMobWeight = mobWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalMobWeight <= 0) return null;
        double roll = ThreadLocalRandom.current().nextDouble() * totalMobWeight;

        List<CustomMob> mobs = plugin.getMobRegistry().getBySkill(skill);
        double currentTotal = 0.0;
        for (CustomMob mob : mobs) {
            if (!mobWeights.containsKey(mob.getId())) continue;
            double weight = mobWeights.get(mob.getId());
            if (roll >= currentTotal && roll < currentTotal + weight) {
                return mob.getId();
            }
            currentTotal += weight;
        }
        // Fallback to last mob if floating point rounding causes overshoot
        for (int i = mobs.size() - 1; i >= 0; i--) {
            if (mobWeights.containsKey(mobs.get(i).getId())) return mobs.get(i).getId();
        }
        return null;
    }

    // ── Rewards ───────────────────────────────────────────────

    /**
     * Gives XP and handles drops/collection for killing a fished mob.
     * Drops fall naturally at the mob's location.
     */
    public void giveMobReward(Player player, LivingEntity entity) {
        giveMobReward(player, entity, false);
    }

    /**
     * Gives XP and handles drops/collection for killing a fished mob.
     * @param directToInventory If true, adds drops to player inventory (spilling to ground if full).
     */
    public void giveMobReward(Player player, LivingEntity entity, boolean directToInventory) {
        if (!isFishedMob(entity)) return;

        String mobId = getMobId(entity).toLowerCase();
        CustomMob def = plugin.getMobRegistry().get(mobId);
        if (def == null) return;

        // Drops
        dropMobLoot(player, entity, def, directToInventory);

        // Collection & XP
        double weight = 0.0;
        if (entity.getPersistentDataContainer().has(FISH_WEIGHT_KEY, PersistentDataType.DOUBLE)) {
            weight = entity.getPersistentDataContainer().get(FISH_WEIGHT_KEY, PersistentDataType.DOUBLE);
        }
        
        double catchXpMultiplierPercent = getCatchXpMultiplierPercent(entity);
        registerCatch(player, mobId, weight, def, catchXpMultiplierPercent);
    }

    /**
     * Drops mob loot for a given CustomMob definition.
     * Can be called independently of the main reward loop (e.g. for Group Kill-All mobs).
     */
    public void dropMobLoot(Player player, LivingEntity entity, CustomMob def, boolean directToInventory) {
        // Get scale multiplier from entity (default 1.0)
        double scale = entity.getPersistentDataContainer().getOrDefault(MOB_SCALE_KEY, PersistentDataType.DOUBLE, 1.0);
        dropMobLoot(player, entity.getLocation(), def, directToInventory, scale);
    }

    /**
     * Overload for when the entity instance is not available (e.g. group kill checks).
     */
    public void dropMobLoot(Player player, Location location, CustomMob def, boolean directToInventory, double scale) {
        boolean collectToLavaBag = BagUtils.hasLavaBagInInventory(plugin, player);
        boolean collectToFishBag = BagUtils.hasFishBagInInventory(plugin, player);
        PlayerData data = (collectToLavaBag || collectToFishBag) ? plugin.getPlayerData(player.getUniqueId()) : null;
        boolean lavaBagChanged = false;
        boolean fishBagChanged = false;

        for (MobDrop drop : def.getDrops()) {
            java.util.List<ItemStack> items = drop.roll(scale);
            for (ItemStack item : items) {
                ItemStack toHandle = item;

                if (data != null) {
                    ItemStack overflow = item;

                    if (collectToLavaBag) {
                        overflow = LavaBagGUI.addToBag(plugin, data, overflow);
                        if (overflow == null || overflow != item) {
                            lavaBagChanged = true;
                        }
                    }

                    if (overflow != null && collectToFishBag && BagUtils.isAllowedInFishBag(plugin, overflow)) {
                        ItemStack fishOverflow = FishBagGUI.addToBag(data, overflow);
                        if (fishOverflow == null || fishOverflow != overflow) {
                            fishBagChanged = true;
                        }
                        overflow = fishOverflow;
                    }

                    if (overflow == null) {
                        continue;
                    }
                    toHandle = overflow;
                }

                if (directToInventory) {
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(toHandle);
                    for (ItemStack remaining : leftover.values()) {
                        Item itemEntity = location.getWorld().dropItemNaturally(location, remaining);
                        itemEntity.setInvulnerable(true);
                    }
                } else {
                    Item itemEntity = location.getWorld().dropItemNaturally(location, toHandle);
                    itemEntity.setInvulnerable(true);
                }
            }
        }

        if (lavaBagChanged && data != null) {
            plugin.getDatabaseManager().saveLavaBag(player.getUniqueId(), data.getLavaBagContents());
        }
        if (fishBagChanged && data != null) {
            plugin.getDatabaseManager().saveFishBag(player.getUniqueId(), data.getFishBagContents());
        }
    }

    public void dropMobExperience(Location location, CustomMob def) {
        int amount = getMobExperienceDrop(def);
        if (amount <= 0 || location == null || location.getWorld() == null) return;

        location.getWorld().spawn(location, org.bukkit.entity.ExperienceOrb.class, orb -> orb.setExperience(amount));
    }

    public int getMobExperienceDrop(CustomMob def) {
        if (def == null || def.getXp() <= 0.0) return 0;
        if (!plugin.getConfig().getBoolean("fishing.mob_experience_orbs.enabled", true)) return 0;

        double multiplier = Math.max(0.0, plugin.getConfig().getDouble("fishing.mob_experience_orbs.multiplier", 0.04));
        int min = Math.max(0, plugin.getConfig().getInt("fishing.mob_experience_orbs.min", 1));
        int max = Math.max(min, plugin.getConfig().getInt("fishing.mob_experience_orbs.max", 18));

        int amount = (int) Math.round(def.getXp() * multiplier);
        if (amount > 0) {
            amount = Math.max(min, amount);
        } else if (min > 0) {
            amount = min;
        }

        return Math.min(max, amount);
    }

    /**
     * Centralized method to register a catch/kill for the encyclopedia.
     * Handles: Database update, PlayerData update, First Catch message, Advancement check.
     * @param def Can be null if only mobId is known (will attempt to look up)
     */
    public void registerCatch(Player player, String mobId, double weight, CustomMob def) {
        registerCatch(player, mobId, weight, def, 0.0);
    }

    public void registerCatch(Player player, String mobId, double weight, CustomMob def,
                              double catchXpMultiplierPercent) {
        if (def == null) {
            def = plugin.getMobRegistry().get(mobId);
        }
        
        // Database Update
        plugin.getDatabaseManager().updateCollection(player.getUniqueId(), mobId, weight);

        // PlayerData & Advancement & Message
        PlayerData playerData = plugin.getPlayerData(player.getUniqueId());
        if (playerData != null) {
            if (!playerData.hasCaughtMob(mobId)) {
                // First catch!
                String displayName = (def != null) ? def.getDisplayName() : mobId;
                player.sendMessage(net.kyori.adventure.text.Component.text(displayName + " added to encyclopedia!")
                    .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("Click to view Encyclopedia!")))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/fishing encyclopedia")));

                // ── QOL: Session discovery tracking ──
                playerData.getSession().recordDiscovery(mobId);

                // ── QOL: Collection progress notification ──
                int totalMobs = plugin.getMobRegistry().getAllIds().size();
                int caughtCount = playerData.getCaughtMobs().size() + 1; // +1 for the one we're about to add
                player.sendMessage(net.kyori.adventure.text.Component.text("   Encyclopedia Progress: " + caughtCount + "/" + totalMobs)
                        .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, true));
                int remaining = totalMobs - caughtCount;
                if (remaining <= 5 && remaining > 0) {
                    player.sendMessage(net.kyori.adventure.text.Component.text("   Only " + remaining + " more to complete the encyclopedia!")
                            .color(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, true));
                } else if (remaining == 0) {
                    player.sendMessage(net.kyori.adventure.text.Component.text("   \u2B50 ENCYCLOPEDIA COMPLETE! Congratulations!")
                            .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                            .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
                }
            }
            playerData.addCaughtMob(mobId);
            plugin.getAdvancementManager().checkCatchAll(player);
        }

        // XP via SkillManager (only if def is present and has XP)
        if (def != null && def.getXp() > 0) {
            double finalXp = def.getXp() * (1.0 + Math.max(0.0, catchXpMultiplierPercent) / 100.0);
            plugin.getSkillManager().grantXp(player, def.getSkill(), finalXp, def.getSkill().getDisplayName() + " Mob");
        }
    }

    // ── Equipment Scanning ────────────────────────────────────

    /**
     * Helper to retrieve a combined PDC double bonus from equipped armor and held items.
     */
    public double getEquipmentBonus(Player player, org.bukkit.NamespacedKey key) {
        double bonus = 0;
        for (ItemStack armorItem : player.getInventory().getArmorContents()) {
            bonus += getScaledArmorBonus(player, armorItem, key);
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.hasItemMeta()) {
            org.bukkit.persistence.PersistentDataContainer pdc = mainHand.getItemMeta().getPersistentDataContainer();
            if (pdc.has(key, org.bukkit.persistence.PersistentDataType.DOUBLE)) {
                bonus += pdc.get(key, org.bukkit.persistence.PersistentDataType.DOUBLE);
            }
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.hasItemMeta()) {
            org.bukkit.persistence.PersistentDataContainer pdc = offHand.getItemMeta().getPersistentDataContainer();
            if (pdc.has(key, org.bukkit.persistence.PersistentDataType.DOUBLE)) {
                bonus += pdc.get(key, org.bukkit.persistence.PersistentDataType.DOUBLE);
            }
        }

        ArtifactPassiveStat artifactStat = getArtifactStatForKey(key);
        if (artifactStat != null) {
            bonus += getArtifactPassiveBonus(player, artifactStat);
        }

        return bonus;
    }

    /**
     * Scans player equipment for rare_creature_chance PDC tags.
     */
    public double getEquipmentRareCreatureBonus(Player player) {
        double total = 0.0;
        NamespacedKey key = plugin.getItemManager().RARE_CREATURE_CHANCE_KEY;
        NamespacedKey deadmanKey = plugin.getItemManager().DEADMAN_ARMOR_KEY;
        boolean isNight = isNightTime(player);

        total += getItemBonus(player.getInventory().getItemInMainHand(), key);
        total += getItemBonus(player.getInventory().getItemInOffHand(), key);
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            PersistentDataContainer pdc = armor.getItemMeta().getPersistentDataContainer();
            double bonus = 0.0;
            if (pdc.has(key, PersistentDataType.DOUBLE)) {
                bonus = pdc.get(key, PersistentDataType.DOUBLE);
            }
            if (bonus > 0 && isNight && pdc.has(deadmanKey, PersistentDataType.BYTE)) {
                bonus *= plugin.getConfig().getDouble("item_balance.deadman_armor_night_multiplier", 2.0);
            }
            bonus *= getNetherArmorWorldMultiplier(player, pdc);
            total += bonus;
        }
        
        // Add "Sea Creature Chance" enchantment bonus
        // Each level grants +5% chance
        org.bukkit.enchantments.Enchantment seaCreatureEnchant = org.bukkit.Registry.ENCHANTMENT.get(new NamespacedKey("fishrework", "sea_creature_chance"));
        if (seaCreatureEnchant != null) {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand != null && mainHand.containsEnchantment(seaCreatureEnchant)) {
                total += mainHand.getEnchantmentLevel(seaCreatureEnchant) * plugin.getConfig().getDouble("enchantments.sea_creature_chance_per_level", 5.0);
            }
            ItemStack offHand = player.getInventory().getItemInOffHand();
             if (offHand != null && offHand.containsEnchantment(seaCreatureEnchant)) {
                total += offHand.getEnchantmentLevel(seaCreatureEnchant) * plugin.getConfig().getDouble("enchantments.sea_creature_chance_per_level", 5.0);
            }
        }
        
        
        total += getArtifactPassiveBonus(player, ArtifactPassiveStat.RARE_CREATURE_CHANCE);
        return total;
    }

    /**
     * Scans player equipment for TREASURE_CHANCE_KEY PDC tags.
     */
    public double getTreasureChance(Player player) {
        double total = 0.0;
        
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        int level = data != null ? data.getLevel(Skill.FISHING) : 0;
        double levelBonus = plugin.getLevelManager().getTreasureIncrease(level);
        total += levelBonus;

        // Treasure Totem bonus (+5 if near an active totem)
        if (plugin.getTotemManager() != null) {
            total += plugin.getTotemManager().getTreasureBonus(player);
        }

        // Equipment treasure chance bonus (from Treasure armor sets)
        NamespacedKey tcKey = plugin.getItemManager().TREASURE_CHANCE_BONUS_KEY;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            total += getScaledArmorBonus(player, armor, tcKey);
        }

        // Luck of the Sea enchantment on the fishing rod
        double luckPerLevel = plugin.getConfig().getDouble("enchantments.luck_of_sea_treasure_per_level", 2.0);
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.getType() == org.bukkit.Material.FISHING_ROD && mainHand.hasItemMeta()) {
            total += mainHand.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA) * luckPerLevel;
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() == org.bukkit.Material.FISHING_ROD && offHand.hasItemMeta()) {
            total += offHand.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA) * luckPerLevel;
        }

        total += getArtifactPassiveBonus(player, ArtifactPassiveStat.TREASURE_CHANCE);
        return total;
    }

    private double getItemBonus(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return 0.0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(key, PersistentDataType.DOUBLE)) {
            return pdc.get(key, PersistentDataType.DOUBLE);
        }
        return 0.0;
    }

    /**
     * Scans player equipment for FISHING_XP_BONUS_KEY PDC tags.
     * Returns a multiplier bonus (e.g. 5.0 means +5% per piece).
     */
    public double getEquipmentFishingXpBonus(Player player) {
        double total = 0.0;
        NamespacedKey xpKey = plugin.getItemManager().FISHING_XP_BONUS_KEY;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            total += getScaledArmorBonus(player, armor, xpKey);
        }
        total += getArtifactPassiveBonus(player, ArtifactPassiveStat.FISHING_XP_BONUS);
        return total;
    }

    /**
     * Scans player equipment for SEA_CREATURE_DEFENSE_KEY PDC tags.
     * Deadman armor bonuses double at night.
     */
    public double getEquipmentSeaCreatureDefense(Player player) {
        double total = 0.0;
        NamespacedKey scdKey = plugin.getItemManager().SEA_CREATURE_DEFENSE_KEY;
        NamespacedKey deadmanKey = plugin.getItemManager().DEADMAN_ARMOR_KEY;
        boolean isNight = isNightTime(player);
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            PersistentDataContainer pdc = armor.getItemMeta().getPersistentDataContainer();
            double bonus = 0.0;
            if (pdc.has(scdKey, PersistentDataType.DOUBLE)) {
                bonus = pdc.get(scdKey, PersistentDataType.DOUBLE);
            }
            if (bonus > 0 && isNight && pdc.has(deadmanKey, PersistentDataType.BYTE)) {
                bonus *= plugin.getConfig().getDouble("item_balance.deadman_armor_night_multiplier", 2.0);
            }
            bonus *= getNetherArmorWorldMultiplier(player, pdc);
            total += bonus;
        }
        total += getArtifactPassiveBonus(player, ArtifactPassiveStat.SEA_CREATURE_DEFENSE);
        return total;
    }

    /**
     * Scans player equipment for SEA_CREATURE_ATTACK_KEY PDC tags.
     * Deadman armor bonuses double at night.
     */
    public double getEquipmentSeaCreatureAttack(Player player) {
        double total = 0.0;
        NamespacedKey scaKey = plugin.getItemManager().SEA_CREATURE_ATTACK_KEY;
        NamespacedKey deadmanKey = plugin.getItemManager().DEADMAN_ARMOR_KEY;
        boolean isNight = isNightTime(player);
        
        // Check Armor
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            PersistentDataContainer pdc = armor.getItemMeta().getPersistentDataContainer();
            double bonus = 0.0;
            if (pdc.has(scaKey, PersistentDataType.DOUBLE)) {
                bonus = pdc.get(scaKey, PersistentDataType.DOUBLE);
            }
            if (bonus > 0 && isNight && pdc.has(deadmanKey, PersistentDataType.BYTE)) {
                bonus *= plugin.getConfig().getDouble("item_balance.deadman_armor_night_multiplier", 2.0);
            }
            bonus *= getNetherArmorWorldMultiplier(player, pdc);
            total += bonus;
        }

        // Check Main Hand (Tridents/Weapons)
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.hasItemMeta()) {
            PersistentDataContainer pdc = mainHand.getItemMeta().getPersistentDataContainer();
            if (pdc.has(scaKey, PersistentDataType.DOUBLE)) {
                total += pdc.get(scaKey, PersistentDataType.DOUBLE);
            }
        }

        total += getArtifactPassiveBonus(player, ArtifactPassiveStat.SEA_CREATURE_ATTACK);
        return total;
    }

    /**
     * Scans player equipment for DOUBLE_CATCH_BONUS_KEY PDC tags.
     * Returns total double catch bonus percentage from equipment.
     */
    public double getEquipmentDoubleCatchBonus(Player player) {
        double total = 0.0;
        NamespacedKey dcKey = plugin.getItemManager().DOUBLE_CATCH_BONUS_KEY;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            total += getScaledArmorBonus(player, armor, dcKey);
        }
        total += getArtifactPassiveBonus(player, ArtifactPassiveStat.DOUBLE_CATCH_CHANCE);
        return total;
    }

    /** Fishing speed from rods (PDC + Lure). Main + off hand only. */
    public int getEquipmentFishingSpeed(Player player) {
        NamespacedKey speedKey = plugin.getItemManager().FISHING_SPEED_KEY;
        int total = 0;
        for (ItemStack stack : new ItemStack[]{ player.getInventory().getItemInMainHand(), player.getInventory().getItemInOffHand() }) {
            if (stack == null || stack.getType() != org.bukkit.Material.FISHING_ROD || !stack.hasItemMeta()) continue;
            total += (int) getItemBonus(stack, speedKey);
            total += stack.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LURE) * plugin.getConfig().getInt("enchantments.lure_fishing_speed_per_level", 5);
        }
        total += (int) Math.round(getArtifactPassiveBonus(player, ArtifactPassiveStat.FISHING_SPEED));
        return total;
    }

    private double getArtifactPassiveBonus(Player player, ArtifactPassiveStat stat) {
        if (player == null || stat == null) return 0.0;
        if (!plugin.isFeatureEnabled(FeatureKeys.ARTIFACT_PASSIVES_ENABLED)) return 0.0;
        if (plugin.getArtifactPassiveManager() == null) return 0.0;
        return plugin.getArtifactPassiveManager().getStatBonus(player, stat);
    }

    private ArtifactPassiveStat getArtifactStatForKey(NamespacedKey key) {
        if (key == null || plugin.getItemManager() == null) return null;

        if (key.equals(plugin.getItemManager().RARE_CREATURE_CHANCE_KEY)) return ArtifactPassiveStat.RARE_CREATURE_CHANCE;
        if (key.equals(plugin.getItemManager().TREASURE_CHANCE_BONUS_KEY)) return ArtifactPassiveStat.TREASURE_CHANCE;
        if (key.equals(plugin.getItemManager().FISHING_XP_BONUS_KEY)) return ArtifactPassiveStat.FISHING_XP_BONUS;
        if (key.equals(plugin.getItemManager().SEA_CREATURE_ATTACK_KEY)) return ArtifactPassiveStat.SEA_CREATURE_ATTACK;
        if (key.equals(plugin.getItemManager().SEA_CREATURE_DEFENSE_KEY)) return ArtifactPassiveStat.SEA_CREATURE_DEFENSE;
        if (key.equals(plugin.getItemManager().DOUBLE_CATCH_BONUS_KEY)) return ArtifactPassiveStat.DOUBLE_CATCH_CHANCE;
        if (key.equals(plugin.getItemManager().FISHING_SPEED_KEY)) return ArtifactPassiveStat.FISHING_SPEED;
        if (key.equals(plugin.getItemManager().HEAT_RESISTANCE_KEY)) return ArtifactPassiveStat.HEAT_RESISTANCE;
        return null;
    }

    /**
     * Checks if it's night time in the player's world (13000-23000 ticks).
     */
    private boolean isNightTime(Player player) {
        long time = player.getWorld().getTime();
        long nightStart = plugin.getConfig().getLong("mob_balance.night_start_time", 13000);
        long nightEnd = plugin.getConfig().getLong("mob_balance.night_end_time", 23000);
        return time >= nightStart && time < nightEnd;
    }

    private double getScaledArmorBonus(Player player, ItemStack armorItem, NamespacedKey statKey) {
        if (armorItem == null || !armorItem.hasItemMeta()) return 0.0;
        PersistentDataContainer pdc = armorItem.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(statKey, PersistentDataType.DOUBLE)) return 0.0;
        double bonus = pdc.get(statKey, PersistentDataType.DOUBLE);
        return bonus * getNetherArmorWorldMultiplier(player, pdc);
    }

    private double getNetherArmorWorldMultiplier(Player player, PersistentDataContainer pdc) {
        if (!isNetherArmorPiece(pdc)) {
            return 1.0;
        }
        return player.getWorld().getEnvironment() == World.Environment.NETHER ? 1.0 : 0.5;
    }

    private boolean isNetherArmorPiece(PersistentDataContainer pdc) {
        for (NamespacedKey setKey : netherArmorSetKeys) {
            if (pdc.has(setKey, PersistentDataType.BYTE)) {
                return true;
            }
        }
        return false;
    }

    // ── PDC Helpers ───────────────────────────────────────────

    public boolean isFishedMob(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(FISHED_MOB_KEY, PersistentDataType.BYTE);
    }

    public String getMobId(LivingEntity entity) {
        return entity.getPersistentDataContainer().get(MOB_ID_KEY, PersistentDataType.STRING);
    }

    public boolean isHostile(String mobId) {
        CustomMob def = plugin.getMobRegistry().get(mobId);
        return def != null && def.isHostile();
    }

    public boolean isPassive(String mobId) {
        return !isHostile(mobId);
    }

    // ── Config / Chance ───────────────────────────────────────

    /**
     * Gets the effective spawn chance, preferring config override, falling back to registry default.
     */
    public double getConfigChance(CustomMob mob) {
        return plugin.getConfig().getDouble("mobs." + mob.getId() + ".chance", mob.getDefaultChance());
    }

    public double getMobChance(String mobId, double fallback) {
        return plugin.getConfig().getDouble("mobs." + mobId + ".chance", fallback);
    }

    public void setMobChance(String mobId, double chance) {
        plugin.getConfig().set("mobs." + mobId + ".chance", chance);
        plugin.saveConfig();
    }
    
    public double getEffectiveLandMobChance(BiomeFishingProfile profile) {
        if (profile == null) return 0.0;
        // Check config for global override "land_mob_bonus"
        if (plugin.getConfig().contains("mobs.land_mob_bonus.chance")) {
            return plugin.getConfig().getDouble("mobs.land_mob_bonus.chance");
        }
        return profile.getLandMobChance();
    }

    // ── Debug ────────────────────────────────────────────────

    /**
     * Returns effective spawn chances at the player's current location (biome-aware).
     */
    public Map<String, Double> getSpawnChances(Player player, Skill skill) {
        return getSpawnChances(player, skill, player.getLocation());
    }

    /**
     * Returns effective spawn chances for a given location, accounting for biome adjustments.
     */
    public Map<String, Double> getSpawnChances(Player player, Skill skill, Location location) {
        return getSpawnChances(player, skill, location, 0.0, 0.0);
    }

    /**
     * Returns effective spawn chances for a given location, accounting for biome adjustments,
     * with optional extra rare/treasure bonuses (e.g. active bait).
     */
    public Map<String, Double> getSpawnChances(Player player, Skill skill, Location location,
                                               double baitRareCreatureBonus, double baitTreasureBonus) {
        return getSpawnChances(player, skill, location, baitRareCreatureBonus, baitTreasureBonus, null);
    }

    public Map<String, Double> getSpawnChances(Player player, Skill skill, Location location,
                                               double baitRareCreatureBonus, double baitTreasureBonus,
                                               String targetedHostileMobId) {
        Collection<String> targetedHostileMobIds = (targetedHostileMobId == null || targetedHostileMobId.isBlank())
            ? List.of()
            : List.of(targetedHostileMobId);
        return getSpawnChances(player, skill, location, baitRareCreatureBonus, baitTreasureBonus,
            targetedHostileMobIds, Set.of());
        }

        public Map<String, Double> getSpawnChances(Player player, Skill skill, Location location,
                               double baitRareCreatureBonus, double baitTreasureBonus,
                               Collection<String> targetedHostileMobIds,
                               Set<BiomeGroup> nativeBiomeGroups) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        int level = data != null ? data.getLevel(skill) : 0;

        double rareBonus = getEquipmentRareCreatureBonus(player) + baitRareCreatureBonus;
        double hostileMultiplier = 1.0 + (rareBonus / 100.0);
        
        double treasureBonus = getTreasureChance(player) + baitTreasureBonus;
        double treasureMultiplier = Math.pow(1.0 + (treasureBonus / 100.0), plugin.getConfig().getDouble("treasure_balance.power_curve_exponent", 3.0));

        boolean isHarmonyRod = plugin.getItemManager().isHarmonyRod(player.getInventory().getItemInMainHand()) 
                            || plugin.getItemManager().isHarmonyRod(player.getInventory().getItemInOffHand());

        BiomeGroup biomeGroup = resolveBiomeGroup(location);
        BiomeFishingProfile biomeProfile = plugin.getBiomeFishingRegistry().get(biomeGroup);

        Map<String, Double> mobWeights = buildWeightMap(
            skill,
            level,
            hostileMultiplier,
            treasureMultiplier,
            biomeProfile,
            location,
            isHarmonyRod,
            targetedHostileMobIds,
            nativeBiomeGroups
        );
        double totalMobWeight = mobWeights.values().stream().mapToDouble(Double::doubleValue).sum();

        Map<String, Double> percentChances = new HashMap<>();
        for (Map.Entry<String, Double> entry : mobWeights.entrySet()) {
            if (totalMobWeight <= 0.0) {
                percentChances.put(entry.getKey(), 0.0);
            } else {
                percentChances.put(entry.getKey(), (entry.getValue() / totalMobWeight) * 100.0);
            }
        }

        // Add land mob bonus info if applicable
        if (biomeProfile != null && !biomeProfile.getLandMobs().isEmpty()) {
            double landChance = getEffectiveLandMobChance(biomeProfile);
            if (isHarmonyRod) {
                landChance *= plugin.getConfig().getDouble("item_balance.harmony_rod_land_mob_multiplier", 2.0);
            }
            percentChances.put("land_mob_bonus", landChance);
        }

        return percentChances;
    }

    private BiomeGroup resolveBiomeGroup(Location location) {
        if (location == null || location.getBlock() == null) {
            return BiomeGroup.OTHER;
        }

        String biomeKey = location.getBlock().getBiome().getKey().toString();
        BiomeGroup biomeGroup = BiomeGroup.fromBiomeKey(biomeKey);
        if (biomeGroup == BiomeGroup.OTHER) {
            biomeGroup = BiomeGroup.fromBiome(location.getBlock().getBiome());
        }
        return biomeGroup;
    }

    public boolean isAquatic(org.bukkit.entity.EntityType type) {
        switch (type) {
            case COD:
            case SALMON:
            case PUFFERFISH:
            case TROPICAL_FISH:
            case SQUID:
            case GLOW_SQUID:
            case TADPOLE:
            case AXOLOTL:
                return true;
            default:
                return false;
        }
    }
}

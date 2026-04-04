package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.model.CustomMob;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Skill;
import com.fishrework.model.FishingSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

public class MobListener implements Listener {

    private final FishRework plugin;
    /** Tracks recently rewarded mob groups to prevent double rewards from simultaneous deaths. */
    private final java.util.Map<String, Long> rewardedGroups = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Tracks group UUIDs that have a pending slime split (children spawning / being tagged).
     * While a group UUID is in this set, no reward will be given for that group.
     * Entries are added synchronously when SlimeSplitEvent fires (before EntityDeathEvent)
     * and removed after the delayed child-tagging task completes.
     */
    private final java.util.Set<String> pendingSlimeSplits = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public MobListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Fireball fireball
            && fireball.getShooter() instanceof LivingEntity shooter
            && plugin.getMobManager().isFishedMob(shooter)) {
            String mobId = plugin.getMobManager().getMobId(shooter);
            if ("ghastling".equals(mobId) || "wailing_ghast_duo".equals(mobId) || "ghast_broodmother".equals(mobId)) {
                event.blockList().clear();
            }
        }
    }

    @EventHandler
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Creeper creeper)) return;
        if (!plugin.getMobManager().isFishedMob(creeper)) return;

        String mobId = plugin.getMobManager().getMobId(creeper);
        if (!"vine_strangler".equals(mobId)) return;

        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.WitherSkull skull)) return;
        if (!(skull.getShooter() instanceof LivingEntity shooter)) return;
        if (!plugin.getMobManager().isFishedMob(shooter)) return;
        if (!"the_wither".equals(plugin.getMobManager().getMobId(shooter))) return;

        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, skull::remove);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!plugin.getMobManager().isFishedMob(entity)) return;
        if (!plugin.getMobManager().hasSharedMountedHp(entity)) return;

        LivingEntity sibling = findSharedMountedSibling(entity);
        if (sibling == null) return;

        double projected = entity.getHealth() - event.getFinalDamage();
        if (projected <= 0.0) return;

        double siblingMax = sibling.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                ? sibling.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue()
                : sibling.getMaxHealth();
        sibling.setHealth(Math.max(1.0, Math.min(siblingMax, projected)));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!plugin.getMobManager().isFishedMob(entity)) return;

        plugin.getMobManager().removeActiveMob(entity.getUniqueId());
        plugin.getBossBarManager().removeMobBossBar(entity.getUniqueId());
        plugin.getMobManager().removeGlowColorEntry(entity.getUniqueId());

        if (plugin.getMobManager().hasSharedMountedHp(entity)) {
            LivingEntity sibling = findSharedMountedSibling(entity);
            if (sibling != null && sibling.isValid() && !sibling.isDead()) {
                plugin.getMobManager().removeActiveMob(sibling.getUniqueId());
                plugin.getBossBarManager().removeMobBossBar(sibling.getUniqueId());
                sibling.remove();
            }
        }

        String mobId = plugin.getMobManager().getMobId(entity);
        CustomMob def = mobId != null ? plugin.getMobRegistry().get(mobId) : null;
        boolean isSlimeMob = "king_slime".equals(mobId) || "slime".equals(mobId);
        boolean isGroupedReward = isSlimeMob || plugin.getMobManager().isGroupKillAll(entity);

        event.getDrops().clear(); // Remove vanilla drops
        event.setDroppedExp(isGroupedReward ? 0 : plugin.getMobManager().getMobExperienceDrop(def));

        Player killer = entity.getKiller();
        if (killer == null) return;

        // ── QOL: Track mob kill in session ──
        PlayerData killerData = plugin.getPlayerData(killer.getUniqueId());
        if (killerData != null) {
            killerData.getSession().recordMobKill();
        }

        // Group kill-all mobs: only reward when ALL members are dead
        // For slime-type mobs, always use group-kill-all logic regardless of PDC tag,
        // since they split on death and we never want to reward per-entity.
        if (isGroupedReward) {
            handleGroupKillAll(entity, killer, mobId);
        } else {
            // Normal: handle multi-entity composites, then give reward
            checkMultiEntityDeath(entity);
            plugin.getMobManager().giveMobReward(killer, entity);
        }

        // Grant Advancements
        if ("dead_rider".equals(mobId)) {
            plugin.getAdvancementManager().grantAdvancement(killer, plugin.getAdvancementManager().KILL_DEAD_RIDER_KEY);
        } else if ("elder_guardian".equals(mobId)) {
            plugin.getAdvancementManager().grantAdvancement(killer, plugin.getAdvancementManager().KILL_ELDER_GUARDIAN_KEY);
        } else if ("poseidon".equals(mobId)) {
            plugin.getAdvancementManager().grantAdvancement(killer, plugin.getAdvancementManager().KILL_POSEIDON_KEY);
        } else if ("spider_jockey".equals(mobId)) {
            plugin.getAdvancementManager().grantAdvancement(killer, plugin.getAdvancementManager().KILL_SPIDER_JOCKEY_KEY);
        } else if ("vulture_king".equals(mobId)) {
            plugin.getAdvancementManager().grantAdvancement(killer, plugin.getAdvancementManager().KILL_VULTURE_KING_KEY);
        } else if ("king_slime".equals(mobId)) {
            plugin.getAdvancementManager().grantAdvancement(killer, plugin.getAdvancementManager().KILL_KING_SLIME_KEY);
        } else if ("warden".equals(mobId)) {
            plugin.getAdvancementManager().grantAdvancement(killer, plugin.getAdvancementManager().KILL_WARDEN_KEY);
        }
    }

    /**
     * Handles slime split for fished slimes and king slimes.
     * Prevents splitting to size 1 (tiniest). Tags children as same mob.
     * <p>
     * IMPORTANT: In Bukkit, SlimeSplitEvent fires BEFORE EntityDeathEvent for the same
     * slime death. We leverage this ordering to mark the group as "pending split" so that
     * the EntityDeathEvent handler (which runs next) won't prematurely award the reward.
     */
    @EventHandler
    public void onSlimeSplit(SlimeSplitEvent event) {
        org.bukkit.entity.Slime slime = event.getEntity();
        if (!plugin.getMobManager().isFishedMob(slime)) return;

        String mobId = plugin.getMobManager().getMobId(slime);
        if (mobId == null) return;

        // Prevent splitting when parent is size 2 (would produce size 1 = tiniest)
        if (slime.getSize() <= 2) {
            event.setCancelled(true);
            return;
        }

        // Allow split, but tag children after they spawn (1 tick delay)
        String parentGroupUUID = plugin.getMobManager().getGroupUUID(slime);
        Location splitLocation = slime.getLocation().clone();

        // Mark this group as having a pending split. This runs synchronously BEFORE
        // the EntityDeathEvent handler, so checkAndRewardIfGroupDead will see this
        // flag and skip the reward. The flag is cleared after children are tagged.
        if (parentGroupUUID != null) {
            pendingSlimeSplits.add(parentGroupUUID);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (org.bukkit.entity.Entity nearby : splitLocation.getWorld()
                    .getNearbyEntities(splitLocation, 10, 10, 10)) {
                if (!(nearby instanceof org.bukkit.entity.Slime childSlime)) continue;
                if (childSlime.isDead()) continue;
                // Only tag un-tagged slimes (new children)
                if (plugin.getMobManager().isFishedMob(childSlime)) continue;

                PersistentDataContainer pdc = childSlime.getPersistentDataContainer();
                pdc.set(plugin.getMobManager().FISHED_MOB_KEY, PersistentDataType.BYTE, (byte) 1);
                pdc.set(plugin.getMobManager().MOB_ID_KEY, PersistentDataType.STRING, mobId);
                pdc.set(plugin.getMobManager().FISH_WEIGHT_KEY, PersistentDataType.DOUBLE, 0.0);
                double parentXpBonus = plugin.getMobManager().getCatchXpMultiplierPercent(slime);
                pdc.set(plugin.getMobManager().CATCH_XP_MULTIPLIER_KEY, PersistentDataType.DOUBLE, parentXpBonus);
                pdc.set(plugin.getMobManager().GROUP_KILL_ALL_KEY, PersistentDataType.BYTE, (byte) 1);
                // Propagate parent's group UUID to children for reward dedup
                if (parentGroupUUID != null) {
                    plugin.getMobManager().tagGroupUUID(childSlime, parentGroupUUID);
                }

                NamedTextColor color = "king_slime".equals(mobId)
                        ? NamedTextColor.DARK_GREEN : NamedTextColor.RED;
                String displayName = "king_slime".equals(mobId) ? "King Slime" : "Bog Blob";
                childSlime.customName(Component.text(displayName).color(color));
                childSlime.setCustomNameVisible(true);

                // Make children aggressive
                if (childSlime instanceof org.bukkit.entity.Mob mob) {
                    Player nearest = childSlime.getWorld().getNearbyPlayers(childSlime.getLocation(), 32)
                            .stream().findFirst().orElse(null);
                    if (nearest != null) mob.setTarget(nearest);
                }
            }

            // Children are now tagged — clear the pending flag so future death
            // checks can proceed normally.
            if (parentGroupUUID != null) {
                pendingSlimeSplits.remove(parentGroupUUID);
            }
        }, 1L);
    }

    /**
     * Auto-despawn vexes spawned by evokers after 8 seconds.
     * This applies to pillager_quartet, pillager_quintet, and temple_guardian.
     */
    @EventHandler
    public void onVexSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Vex vex)) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPELL) return;

        int despawnSeconds = plugin.getConfig().getInt("combat.vex_despawn_seconds", 8);
        long delayTicks = 20L * despawnSeconds;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (vex.isValid() && !vex.isDead()) {
                vex.remove();
            }
        }, delayTicks);
    }

    /**
     * Group kill-all logic: pillager groups, king slime splits, and regular slime splits.
     * Only gives reward when ALL remaining siblings with the same mobId are dead.
     */
    private void handleGroupKillAll(LivingEntity deadEntity, Player killer, String mobId) {
        boolean isSlimeType = "king_slime".equals(mobId) || "slime".equals(mobId);
        String groupUUID = plugin.getMobManager().getGroupUUID(deadEntity);
        double catchXpMultiplierPercent = plugin.getMobManager().getCatchXpMultiplierPercent(deadEntity);

        // For slimes, wait 5 ticks for split children to spawn and be tagged.
        // The pending-split flag prevents premature rewards, but we still delay to give
        // the tagging task (1 tick) time to complete before we scan for remaining entities.
        int delay = isSlimeType ? 5 : 0;

        if (delay > 0) {
            Location loc = deadEntity.getLocation().clone();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                checkAndRewardIfGroupDead(loc, killer, mobId, groupUUID, true, catchXpMultiplierPercent);
            }, delay);
        } else {
            checkAndRewardIfGroupDead(deadEntity.getLocation(), killer, mobId, groupUUID, false, catchXpMultiplierPercent);
        }
    }

    /** Checks if all entities with the given mobId and groupUUID are dead. If so, grants reward (once). */
    private void checkAndRewardIfGroupDead(Location loc, Player killer, String mobId, String groupUUID,
                                           boolean isSlimeType, double catchXpMultiplierPercent) {
        // Fast-path: if this group has a pending slime split (children spawning/being tagged),
        // don't give the reward yet. A future child death will re-trigger this check.
        if (isSlimeType && groupUUID != null && pendingSlimeSplits.contains(groupUUID)) {
            return;
        }

        int remaining = 0;
        for (org.bukkit.entity.Entity nearby : loc.getWorld().getNearbyEntities(loc, 40, 20, 40)) {
            if (!(nearby instanceof LivingEntity living)) continue;
            if (living.isDead()) continue;

            if (!plugin.getMobManager().isFishedMob(living)) continue;
            String otherMobId = plugin.getMobManager().getMobId(living);
            if (!mobId.equals(otherMobId)) continue;

            // If we have a group UUID, only count entities from this spawn group
            if (groupUUID != null) {
                String otherUUID = plugin.getMobManager().getGroupUUID(living);
                if (!groupUUID.equals(otherUUID)) continue;
            }
            remaining++;
        }

        if (remaining == 0) {
            // Prevent double reward from simultaneous deaths — dedup by group UUID (or mobId fallback)
            String dedupKey = groupUUID != null ? groupUUID : mobId;
            long currentTick = Bukkit.getCurrentTick();
            Long lastReward = rewardedGroups.get(dedupKey);
            if (lastReward != null && (currentTick - lastReward) < 20) return; // Skip if rewarded within 1 sec
            rewardedGroups.put(dedupKey, currentTick);

            // Last one dead — give full reward
            CustomMob def = plugin.getMobRegistry().get(mobId);
            if (def != null) {
                plugin.getMobManager().dropMobLoot(killer, loc, def, false, 1.0);
                plugin.getMobManager().dropMobExperience(loc, def);
                plugin.getMobManager().registerCatch(killer, mobId, 0, def, catchXpMultiplierPercent);
            }
        }
    }

    /**
     * Handles multi-entity mob death for composites that do not use group-kill-all.
     * Mounted mobs are tagged as group-kill-all at spawn and are handled earlier.
     */
    private void checkMultiEntityDeath(LivingEntity entity) {
        if (!plugin.getMobManager().isFishedMob(entity)) return;
        String mobId = plugin.getMobManager().getMobId(entity);
        if (mobId == null) return;

        // Mounted pairs are handled via group-kill-all; only creaking_gang is manual.
        switch (mobId) {
            case "creaking_gang":
                removeNearbySiblings(entity, mobId);
                break;
            default:
                break;
        }
    }

    /** Remove all nearby entities that share the same fished mob ID (for group spawns). */
    private void removeNearbySiblings(LivingEntity entity, String mobId) {
        for (org.bukkit.entity.Entity nearby : entity.getLocation().getWorld().getNearbyEntities(entity.getLocation(), 20, 10, 20)) {
            if (nearby == entity) continue;
            if (!(nearby instanceof LivingEntity living)) continue;
            if (!plugin.getMobManager().isFishedMob(living)) continue;
            String otherMobId = plugin.getMobManager().getMobId(living);
            if (mobId.equals(otherMobId)) {
                // Also remove any passengers (e.g. pillager on ravager)
                for (org.bukkit.entity.Entity passenger : nearby.getPassengers()) {
                    if (passenger instanceof LivingEntity passengerLiving
                            && plugin.getMobManager().isFishedMob(passengerLiving)) {
                        plugin.getMobManager().removeActiveMob(passengerLiving.getUniqueId());
                        plugin.getBossBarManager().removeMobBossBar(passengerLiving.getUniqueId());
                    }
                    passenger.remove();
                }
                plugin.getMobManager().removeActiveMob(living.getUniqueId());
                plugin.getBossBarManager().removeMobBossBar(living.getUniqueId());
                nearby.remove();
            }
        }
    }

    private LivingEntity findSharedMountedSibling(LivingEntity entity) {
        String mobId = plugin.getMobManager().getMobId(entity);
        String groupUUID = plugin.getMobManager().getGroupUUID(entity);
        if (mobId == null || groupUUID == null) return null;

        for (org.bukkit.entity.Entity nearby : entity.getLocation().getWorld().getNearbyEntities(entity.getLocation(), 40, 20, 40)) {
            if (nearby == entity) continue;
            if (!(nearby instanceof LivingEntity sibling)) continue;
            if (!plugin.getMobManager().isFishedMob(sibling)) continue;
            if (!plugin.getMobManager().hasSharedMountedHp(sibling)) continue;

            String siblingMobId = plugin.getMobManager().getMobId(sibling);
            String siblingGroupUUID = plugin.getMobManager().getGroupUUID(sibling);
            if (!mobId.equals(siblingMobId)) continue;
            if (!groupUUID.equals(siblingGroupUUID)) continue;
            return sibling;
        }

        return null;
    }
}

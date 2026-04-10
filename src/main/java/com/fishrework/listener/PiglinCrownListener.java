package com.fishrework.listener;

import com.fishrework.FishRework;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PiglinCrownListener implements Listener {

    private static final double PACIFY_RADIUS_XZ = 16.0;
    private static final double PACIFY_RADIUS_Y = 8.0;
    private static final long PROTECTED_MOB_AGGRO_GRACE_MS = 8000L;
    private static final Set<String> PIGLIN_AGGRO_PROTECTED_MOBS = Set.of(
            "piglin_floater",
            "crimson_abomination"
    );

    private final FishRework plugin;
    private final Map<UUID, Long> protectedMobAggroBypassUntil = new ConcurrentHashMap<>();
    private final NamespacedKey magmaScaleArmorKey;
    private final NamespacedKey infernalPlateArmorKey;
    private final NamespacedKey volcanicDreadplateArmorKey;

    public PiglinCrownListener(FishRework plugin) {
        this.plugin = plugin;
        this.magmaScaleArmorKey = new NamespacedKey(plugin, "magma_scale_armor");
        this.infernalPlateArmorKey = new NamespacedKey(plugin, "infernal_plate_armor");
        this.volcanicDreadplateArmorKey = new NamespacedKey(plugin, "volcanic_dreadplate_armor");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerHitsProtectedPiglinMob(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttackingPlayer(event.getDamager());
        if (attacker == null) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!isFishedMob(target)) return;

        String mobId = plugin.getMobManager().getMobId(target);
        if (mobId == null || !PIGLIN_AGGRO_PROTECTED_MOBS.contains(mobId)) return;

        markProtectedMobAggroBypass(attacker);
        pacifyNearbyPiglins(target);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerHitsZombifiedPiglin(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof PigZombie piglin)) return;
        if (isFishedMob(piglin)) return;

        Player attacker = resolveAttackingPlayer(event.getDamager());
        if (attacker == null || !isWearingPiglinCrown(attacker)) return;

        pacify(piglin);
        pacifyNearbyPiglins(piglin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onZombifiedPiglinTargetsPlayer(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof PigZombie piglin)) return;
        if (isFishedMob(piglin)) return;
        if (!(event.getTarget() instanceof Player player)) return;
        if (!isWearingPiglinCrown(player) && !hasProtectedMobAggroBypass(player)) return;

        event.setCancelled(true);
        pacify(piglin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onZombifiedPiglinDamagesCrownedPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDamager() instanceof PigZombie piglin)) return;
        if (isFishedMob(piglin)) return;
        if (!isWearingPiglinCrown(player) && !hasProtectedMobAggroBypass(player)) return;

        event.setCancelled(true);
        pacify(piglin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPiglinTargetsProtectedPlayer(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Piglin piglin)) return;
        if (isFishedMob(piglin)) return;
        if (!(event.getTarget() instanceof Player player)) return;
        if (!hasProtectedMobAggroBypass(player) && !isWearingPiglinNeutralLavaArmor(player)) return;

        event.setCancelled(true);
        pacify(piglin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPiglinDamagesProtectedPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDamager() instanceof Piglin piglin)) return;
        if (isFishedMob(piglin)) return;
        if (!hasProtectedMobAggroBypass(player) && !isWearingPiglinNeutralLavaArmor(player)) return;

        event.setCancelled(true);
        pacify(piglin);
    }

    private Player resolveAttackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean isWearingPiglinCrown(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        return plugin.getItemManager().isCustomItem(helmet, "piglin_crown_helmet");
    }

    private boolean isWearingPiglinNeutralLavaArmor(Player player) {
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            var pdc = armor.getItemMeta().getPersistentDataContainer();
            if (pdc.has(magmaScaleArmorKey, PersistentDataType.BYTE)
                    || pdc.has(infernalPlateArmorKey, PersistentDataType.BYTE)
                    || pdc.has(volcanicDreadplateArmorKey, PersistentDataType.BYTE)) {
                return true;
            }
        }
        return false;
    }

    private void markProtectedMobAggroBypass(Player player) {
        protectedMobAggroBypassUntil.put(player.getUniqueId(), System.currentTimeMillis() + PROTECTED_MOB_AGGRO_GRACE_MS);
    }

    private boolean hasProtectedMobAggroBypass(Player player) {
        Long expireAt = protectedMobAggroBypassUntil.get(player.getUniqueId());
        if (expireAt == null) return false;
        if (System.currentTimeMillis() > expireAt) {
            protectedMobAggroBypassUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private boolean isFishedMob(LivingEntity entity) {
        return plugin.getMobManager() != null && plugin.getMobManager().isFishedMob(entity);
    }

    private void pacifyNearbyPiglins(LivingEntity source) {
        for (Entity nearby : source.getNearbyEntities(PACIFY_RADIUS_XZ, PACIFY_RADIUS_Y, PACIFY_RADIUS_XZ)) {
            if (nearby instanceof PigZombie nearbyPiglin && !isFishedMob(nearbyPiglin)) {
                pacify(nearbyPiglin);
            }
            if (nearby instanceof Piglin nearbyPiglin && !isFishedMob(nearbyPiglin)) {
                pacify(nearbyPiglin);
            }
        }
    }

    private void pacify(PigZombie piglin) {
        piglin.setTarget(null);
        piglin.setAnger(0);
    }

    private void pacify(Piglin piglin) {
        piglin.setTarget(null);
    }
}
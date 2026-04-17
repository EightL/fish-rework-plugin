package com.fishrework.manager;

import com.fishrework.FishRework;
import com.fishrework.model.Artifact;
import com.fishrework.model.ArtifactPassiveEffect;
import com.fishrework.model.ArtifactPassiveStat;
import com.fishrework.model.ArtifactPassiveType;
import com.fishrework.util.FeatureKeys;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ArtifactPassiveManager {

    private final FishRework plugin;
    private final Map<UUID, EnumMap<ArtifactPassiveStat, Double>> statBonusCache = new HashMap<>();
    private BukkitTask task;

    public ArtifactPassiveManager(FishRework plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) return;
        if (!plugin.isFeatureEnabled(FeatureKeys.ARTIFACT_PASSIVES_ENABLED)) return;

        long interval = Math.max(2L, plugin.getConfig().getLong("artifact_passives.scan_interval_ticks", 20L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::reconcileAllPlayers, 0L, interval);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        statBonusCache.clear();
    }

    public double getStatBonus(Player player, ArtifactPassiveStat stat) {
        if (player == null || stat == null) return 0.0;
        EnumMap<ArtifactPassiveStat, Double> cache = statBonusCache.get(player.getUniqueId());
        if (cache == null) return 0.0;
        return cache.getOrDefault(stat, 0.0);
    }

    private void reconcileAllPlayers() {
        if (!plugin.isFeatureEnabled(FeatureKeys.ARTIFACT_PASSIVES_ENABLED)) {
            statBonusCache.clear();
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            reconcilePlayer(player);
        }

        statBonusCache.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    private void reconcilePlayer(Player player) {
        Set<String> artifactIds = collectActiveArtifactIds(player);
        if (artifactIds.isEmpty()) {
            statBonusCache.remove(player.getUniqueId());
            return;
        }

        EnumMap<ArtifactPassiveStat, Double> totals = new EnumMap<>(ArtifactPassiveStat.class);
        for (String artifactId : artifactIds) {
            Artifact artifact = plugin.getArtifactRegistry().get(artifactId);
            if (artifact == null) continue;

            for (ArtifactPassiveEffect effect : artifact.getPassiveEffects()) {
                if (effect.getType() == ArtifactPassiveType.STAT_BONUS && effect.getStat() != null) {
                    totals.merge(effect.getStat(), effect.getValue(), Double::sum);
                    continue;
                }

                if (effect.getType() == ArtifactPassiveType.POTION && effect.getPotionEffectType() != null) {
                    player.addPotionEffect(new PotionEffect(
                        effect.getPotionEffectType(),
                        effect.getPotionDurationTicks(),
                        effect.getPotionAmplifier(),
                        true,
                        false,
                        false
                    ));
                }
            }
        }

        applyStatCaps(totals);
        if (totals.isEmpty()) {
            statBonusCache.remove(player.getUniqueId());
            return;
        }
        statBonusCache.put(player.getUniqueId(), totals);
    }

    private void applyStatCaps(EnumMap<ArtifactPassiveStat, Double> totals) {
        for (Map.Entry<ArtifactPassiveStat, Double> entry : totals.entrySet()) {
            String path = "artifact_passives.caps." + entry.getKey().getConfigKey();
            if (!plugin.getConfig().isSet(path)) continue;

            double cap = plugin.getConfig().getDouble(path, entry.getValue());
            double value = entry.getValue();
            if (cap >= 0.0) {
                entry.setValue(Math.min(cap, value));
            }
        }
    }

    private Set<String> collectActiveArtifactIds(Player player) {
        Set<String> ids = new HashSet<>();
        collectFromItems(player.getInventory().getStorageContents(), ids);
        collectFromItems(player.getInventory().getArmorContents(), ids);
        collectFromItem(player.getInventory().getItemInOffHand(), ids);
        return ids;
    }

    private void collectFromItems(ItemStack[] items, Set<String> ids) {
        if (items == null) return;
        for (ItemStack item : items) {
            collectFromItem(item, ids);
        }
    }

    private void collectFromItem(ItemStack item, Set<String> ids) {
        if (item == null || !item.hasItemMeta()) return;
        String artifactId = item.getItemMeta().getPersistentDataContainer()
            .get(plugin.getItemManager().ARTIFACT_KEY, PersistentDataType.STRING);
        if (artifactId == null || artifactId.isBlank()) return;
        ids.add(artifactId);
    }
}
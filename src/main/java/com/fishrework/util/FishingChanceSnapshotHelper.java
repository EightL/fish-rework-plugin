package com.fishrework.util;

import com.fishrework.FishRework;
import com.fishrework.model.Bait;
import com.fishrework.model.BiomeGroup;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Skill;
import com.fishrework.registry.RecipeDefinition;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared helper that computes the current fishing chance snapshot for a player.
 * Keeps command and GUI output in sync.
 */
public final class FishingChanceSnapshotHelper {

    private FishingChanceSnapshotHelper() {
    }

    public record ChanceSnapshot(
            Skill skill,
            BiomeGroup biomeGroup,
            boolean lavaContext,
            String activeBaitId,
            String activeBaitDisplayName,
            boolean baitAppliesToContext,
            double baitRareCreatureBonus,
            double baitTreasureBonus,
            List<String> baitTargetMobIds,
            Set<BiomeGroup> baitNativeBiomeGroups,
            double equipmentRareCreatureBonus,
            double equipmentTreasureBonus,
            double totalRareCreatureBonus,
            double totalTreasureBonus,
            Map<String, Double> chances,
            Map<String, Double> rawWeights
    ) {
    }

    public static ChanceSnapshot capture(FishRework plugin, Player player, Skill skill) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        boolean lavaContext = player.getWorld().getEnvironment() == World.Environment.NETHER;

        String activeBaitId = null;
        String activeBaitDisplayName = null;
        boolean baitAppliesToContext = false;
        double baitRareCreatureBonus = 0.0;
        double baitTreasureBonus = 0.0;
        List<String> baitTargetMobIds = Collections.emptyList();
        Set<BiomeGroup> baitNativeBiomeGroups = Collections.emptySet();

        if (plugin.getItemManager().isBait(offhand)) {
            activeBaitId = plugin.getItemManager().getBaitId(offhand);
            baitAppliesToContext = lavaContext
                    ? plugin.getItemManager().isBaitApplicableForLava(offhand)
                    : plugin.getItemManager().isBaitApplicableForWater(offhand);

            if (activeBaitId != null) {
                Bait bait = plugin.getBaitRegistry().get(activeBaitId);
                activeBaitDisplayName = bait != null ? bait.getDisplayName() : RecipeDefinition.toFriendlyName(activeBaitId);

                if (bait != null && baitAppliesToContext) {
                    baitRareCreatureBonus = bait.getBonus(Bait.RARE_CREATURE_CHANCE);
                    baitTreasureBonus = bait.getBonus(Bait.TREASURE_CHANCE);
                }
            }

            if (baitAppliesToContext) {
                baitTargetMobIds = plugin.getItemManager().getBaitTargetMobIds(offhand);
                baitNativeBiomeGroups = plugin.getItemManager().getBaitNativeBiomeGroups(offhand);
            }
        }

        Location location = player.getLocation();
        BiomeGroup biomeGroup = resolveBiomeGroup(location);

        Map<String, Double> chances = plugin.getMobManager().getSpawnChances(
                player,
                skill,
                location,
                baitRareCreatureBonus,
                baitTreasureBonus,
                baitTargetMobIds,
                baitNativeBiomeGroups
        );

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        int level = data != null ? data.getLevel(skill) : 0;

        double equipmentRareCreatureBonus = plugin.getMobManager().getEquipmentRareCreatureBonus(player);
        double equipmentTreasureBonus = plugin.getMobManager().getTreasureChance(player);
        double totalRareCreatureBonus = equipmentRareCreatureBonus + baitRareCreatureBonus;
        double totalTreasureBonus = equipmentTreasureBonus + baitTreasureBonus;

        double hostileMultiplier = 1.0 + (totalRareCreatureBonus / 100.0);
        double treasureMultiplier = Math.pow(
                1.0 + (totalTreasureBonus / 100.0),
                plugin.getConfig().getDouble("treasure_balance.power_curve_exponent", 3.0)
        );

        boolean isHarmonyRod = plugin.getItemManager().isHarmonyRod(player.getInventory().getItemInMainHand())
                || plugin.getItemManager().isHarmonyRod(player.getInventory().getItemInOffHand());

        com.fishrework.model.BiomeFishingProfile biomeProfile = plugin.getBiomeFishingRegistry().get(biomeGroup);

        Map<String, Double> rawWeights = plugin.getMobManager().buildWeightMap(
                skill,
                level,
                hostileMultiplier,
                treasureMultiplier,
                biomeProfile,
                location,
                isHarmonyRod,
                baitTargetMobIds,
                baitNativeBiomeGroups
        );

        return new ChanceSnapshot(
                skill,
                biomeGroup,
                lavaContext,
                activeBaitId,
                activeBaitDisplayName,
                baitAppliesToContext,
                baitRareCreatureBonus,
                baitTreasureBonus,
                List.copyOf(baitTargetMobIds),
                Set.copyOf(baitNativeBiomeGroups),
                equipmentRareCreatureBonus,
                equipmentTreasureBonus,
                totalRareCreatureBonus,
                totalTreasureBonus,
                Map.copyOf(chances),
                Map.copyOf(rawWeights)
        );
    }

    public static List<Map.Entry<String, Double>> sortByChanceDescending(Map<String, Double> chances) {
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(chances.entrySet());
        sorted.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        return sorted;
    }

    public static String displayNameForEntry(FishRework plugin, String entryId) {
        if ("land_mob_bonus".equals(entryId)) {
            return "Land Mob Bonus";
        }
        com.fishrework.model.CustomMob mob = plugin.getMobRegistry().get(entryId);
        if (mob != null) {
            return mob.getDisplayName();
        }
        return RecipeDefinition.toFriendlyName(entryId);
    }

    private static BiomeGroup resolveBiomeGroup(Location location) {
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
}
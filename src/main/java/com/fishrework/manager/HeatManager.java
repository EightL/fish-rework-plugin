package com.fishrework.manager;

import com.fishrework.FishRework;
import com.fishrework.model.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages the Heat System for Lava Fishing.
 * Heat increases with each lava catch and passive hostiles.
 * Heat decreases passively over time and via cooling items.
 * High heat grants Sea Creature Chance (SCC) bonuses but risks fishing penalties.
 */
public class HeatManager {

    private final FishRework plugin;

    // Config thresholds
    private final double warmThreshold;
    private final double hotThreshold;
    private final double scorchingThreshold;
    private final double infernalThreshold;

    private final double warmSccBonus;
    private final double hotSccBonus;
    private final double scorchingSccBonus;
    private final double infernalSccBonus;

    private final NamespacedKey magmaFilterResistanceUntilKey;
    
    private final double decayPerTick;
    private final int decayIntervalSeconds;

    public HeatManager(FishRework plugin) {
        this.plugin = plugin;
        this.warmThreshold = plugin.getConfig().getDouble("heat.warm_threshold", 25.0);
        this.hotThreshold = plugin.getConfig().getDouble("heat.hot_threshold", 50.0);
        this.scorchingThreshold = plugin.getConfig().getDouble("heat.scorching_threshold", 75.0);
        this.infernalThreshold = plugin.getConfig().getDouble("heat.infernal_threshold", 90.0);

        this.warmSccBonus = plugin.getConfig().getDouble("heat.warm_scc_bonus", 10.0);
        this.hotSccBonus = plugin.getConfig().getDouble("heat.hot_scc_bonus", 25.0);
        this.scorchingSccBonus = plugin.getConfig().getDouble("heat.scorching_scc_bonus", 50.0);
        this.infernalSccBonus = plugin.getConfig().getDouble("heat.infernal_scc_bonus", 100.0);

        this.magmaFilterResistanceUntilKey = new NamespacedKey(plugin, "magma_filter_resistance_until");
        
        this.decayPerTick = plugin.getConfig().getDouble("heat.decay_per_tick", 1.0);
        this.decayIntervalSeconds = plugin.getConfig().getInt("heat.decay_interval_seconds", 10);
    }

    /**
     * Enum for current heat tier to easily derive colors and names.
     */
    public enum HeatTier {
        NORMAL(NamedTextColor.GREEN, "Normal"),
        WARM(NamedTextColor.YELLOW, "Warm"),
        HOT(NamedTextColor.GOLD, "Hot"),
        SCORCHING(NamedTextColor.RED, "Scorching"),
        INFERNAL(NamedTextColor.DARK_RED, "INFERNAL");

        private final TextColor color;
        private final String displayName;

        HeatTier(TextColor color, String displayName) {
            this.color = color;
            this.displayName = displayName;
        }

        public TextColor getColor() { return color; }
        public String getDisplayName() { return displayName; }
    }

    /**
     * Gets the current heat tier for a player based on their heat value.
     */
    public HeatTier getHeatTier(Player player) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return HeatTier.NORMAL;
        double heat = data.getHeat();

        if (heat >= infernalThreshold) return HeatTier.INFERNAL;
        if (heat >= scorchingThreshold) return HeatTier.SCORCHING;
        if (heat >= hotThreshold) return HeatTier.HOT;
        if (heat >= warmThreshold) return HeatTier.WARM;
        return HeatTier.NORMAL;
    }

    /**
     * Gets the total Sea Creature Chance bonus from the current heat tier.
     */
    public double getHeatSccBonus(Player player) {
        HeatTier tier = getHeatTier(player);
        return switch (tier) {
            case INFERNAL -> infernalSccBonus;
            case SCORCHING -> scorchingSccBonus;
            case HOT -> hotSccBonus;
            case WARM -> warmSccBonus;
            default -> 0.0;
        };
    }

    /**
     * Adds heat to a player's gauge.
     * Respects heat resistance from equipment.
     */
    public void addHeat(Player player, double amount) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        long now = System.currentTimeMillis();
        expireMagmaFilterIfNeeded(player, now, true);

        // Calculate heat resistance from equipment + temporary buffs.
        double resistance = plugin.getMobManager().getEquipmentBonus(player, plugin.getItemManager().HEAT_RESISTANCE_KEY);
        resistance += getTemporaryHeatResistance(player);
        double actualAmount = amount;
        
        // If resistance is 30, it reduces heat gain by 30%.
        if (resistance > 0) {
            double reduction = Math.min(100.0, resistance) / 100.0;
            actualAmount = amount * (1.0 - reduction);
        }

        if (actualAmount <= 0) return;

        double newHeat = Math.min(100.0, data.getHeat() + actualAmount);
        data.setHeat(newHeat);
        data.getSession().recordPeakHeat(newHeat);

        // Penalty procs are applied on each lava-fishing heat gain event.
        applyFishingPenaltyProc(player, data, newHeat);
        showHeatGauge(player, newHeat);
    }

    /**
     * Reduces heat. E.g., via passive decay or cooling items.
     */
    public void reduceHeat(Player player, double amount) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        double newHeat = Math.max(0.0, data.getHeat() - amount);
        data.setHeat(newHeat);
        
        // Only show gauge on active reduce (cooling items), passive decay happens quietly
    }

    /**
     * Applies one scaled penalty chance while lava fishing under heat.
     */
    private void applyFishingPenaltyProc(Player player, PlayerData data, double currentHeat) {
        if (currentHeat <= 0.0) return;

        double maxChance = plugin.getConfig().getDouble("heat.fishing_penalty_max_chance_at_100_heat", 0.55);
        maxChance = Math.max(0.0, Math.min(1.0, maxChance));
        double triggerChance = maxChance * Math.min(1.0, currentHeat / 100.0);
        if (!roll(triggerChance)) return;

        int effectIndex = ThreadLocalRandom.current().nextInt(3);
        switch (effectIndex) {
            case 0 -> {
                int ticks = Math.max(20, plugin.getConfig().getInt("heat.fishing_penalty_slowness_ticks", 100));
                int amplifier = Math.max(0, plugin.getConfig().getInt("heat.fishing_penalty_slowness_amplifier", 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, amplifier, false, true, true));
                player.sendMessage(Component.text("[Heat] ").color(NamedTextColor.DARK_GRAY)
                        .append(Component.text("Due to heat, you got Slowness.").color(NamedTextColor.RED)));
            }
            case 1 -> {
                int ticks = Math.max(20, plugin.getConfig().getInt("heat.fishing_penalty_weakness_ticks", 120));
                int amplifier = Math.max(0, plugin.getConfig().getInt("heat.fishing_penalty_weakness_amplifier", 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, amplifier, false, true, true));
                player.sendMessage(Component.text("[Heat] ").color(NamedTextColor.DARK_GRAY)
                        .append(Component.text("Due to heat, you got Weakness.").color(NamedTextColor.RED)));
            }
            default -> {
                double damage = Math.max(0.5, plugin.getConfig().getDouble("heat.fishing_penalty_raw_damage", 4.0));
                player.damage(damage);
                data.getSession().addHeatDamageTaken(damage);
                player.sendMessage(Component.text("[Heat] ").color(NamedTextColor.DARK_GRAY)
                        .append(Component.text("Due to heat, you took " + String.format("%.1f", damage) + " damage.")
                                .color(NamedTextColor.RED)));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.75f, 0.8f);
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 10, 0.25, 0.2, 0.25, 0.02);
            }
        }
    }

    private boolean roll(double chance) {
        return Math.random() < chance;
    }

    public void applyMagmaFilterResistance(Player player) {
        if (player == null) return;

        int durationSeconds = Math.max(1, plugin.getConfig().getInt("heat.magma_filter_duration_seconds", 300));
        long until = System.currentTimeMillis() + (durationSeconds * 1000L);
        player.getPersistentDataContainer().set(magmaFilterResistanceUntilKey, PersistentDataType.LONG, until);
    }

    public double getTemporaryHeatResistance(Player player) {
        if (player == null) return 0.0;
        long remainingSeconds = getMagmaFilterRemainingSeconds(player);
        if (remainingSeconds <= 0) {
            return 0.0;
        }
        return Math.max(0.0, plugin.getConfig().getDouble("heat.magma_filter_resistance_percent", 50.0));
    }

    public long getMagmaFilterRemainingSeconds(Player player) {
        if (player == null) return 0L;
        Long until = player.getPersistentDataContainer().get(magmaFilterResistanceUntilKey, PersistentDataType.LONG);
        if (until == null) return 0L;

        long remainingMillis = until - System.currentTimeMillis();
        if (remainingMillis <= 0L) return 0L;
        return (remainingMillis + 999L) / 1000L;
    }

    private boolean expireMagmaFilterIfNeeded(Player player, long now, boolean notify) {
        if (player == null) return false;

        Long until = player.getPersistentDataContainer().get(magmaFilterResistanceUntilKey, PersistentDataType.LONG);
        if (until == null || now < until) return false;

        player.getPersistentDataContainer().remove(magmaFilterResistanceUntilKey);
        if (notify) {
            player.sendMessage(Component.text("[Heat] ").color(NamedTextColor.DARK_GRAY)
                    .append(Component.text("Magma Filter wore off.").color(NamedTextColor.RED)));
        }
        return true;
    }

    /**
     * Displays an action bar representation of the heat gauge.
     */
    public void showHeatGauge(Player player, double currentHeat) {
        HeatTier tier = getHeatTier(player);
        
        int totalBars = 20; // Each bar is 5 heat
        int filledBars = (int) (currentHeat / 5);
        int emptyBars = totalBars - filledBars;

        Component gauge = Component.text(" HEAT [").color(NamedTextColor.GRAY)
                .append(Component.text("|".repeat(Math.max(0, filledBars))).color(tier.getColor()))
                .append(Component.text("|".repeat(Math.max(0, emptyBars))).color(NamedTextColor.DARK_GRAY))
                .append(Component.text("] ").color(NamedTextColor.GRAY))
                .append(Component.text(String.format("%.1f", currentHeat) + "%").color(tier.getColor()));

        // Add SCC bonus indicator if any
        double sccBonus = getHeatSccBonus(player);
        if (sccBonus > 0) {
            gauge = gauge.append(Component.text(" (+" + String.format("%.0f", sccBonus) + "% SCC)").color(NamedTextColor.AQUA));
        }

        player.sendActionBar(gauge);
    }
    
    /**
     * Called periodically to passively decay heat for all online players.
     */
    public void processPassiveDecay() {
        long now = System.currentTimeMillis();
        long decayMillis = decayIntervalSeconds * 1000L;
        
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            expireMagmaFilterIfNeeded(player, now, true);

            PlayerData data = plugin.getPlayerData(player.getUniqueId());
            if (data == null || data.getHeat() <= 0) continue;
            
            if (now - data.getLastHeatDecayTime() >= decayMillis) {
                // Actually reduce
                double newHeat = Math.max(0.0, data.getHeat() - decayPerTick);
                data.setHeat(newHeat);
                data.setLastHeatDecayTime(now);
            }
        }
    }
}

package com.fishrework.manager;

import com.fishrework.FishRework;
import com.fishrework.model.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Manages the Heat System for Lava Fishing.
 * Heat increases with each lava catch and passive hostiles.
 * Heat decreases passively over time and via cooling items.
 * High heat grants Sea Creature Chance (SCC) bonuses but risks meltdowns.
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

    private final double meltdownDamage;
    private final int meltdownDurabilityLoss;
    private final double meltdownResetHeat;
    
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

        this.meltdownDamage = plugin.getConfig().getDouble("heat.meltdown_damage", 12.0);
        this.meltdownDurabilityLoss = plugin.getConfig().getInt("heat.meltdown_durability_loss", 200);
        this.meltdownResetHeat = plugin.getConfig().getDouble("heat.meltdown_reset_heat", 50.0);
        
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
     * Adds heat to a player's gauge. If it reaches 100, triggers a meltdown.
     * Respects heat resistance from equipment.
     */
    public void addHeat(Player player, double amount) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        // Calculate heat resistance from equipment
        double resistance = plugin.getMobManager().getEquipmentBonus(player, plugin.getItemManager().HEAT_RESISTANCE_KEY);
        double actualAmount = amount;
        
        // If resistance is 30, it reduces heat gain by 30%
        if (resistance > 0) {
            double reduction = Math.min(100.0, resistance) / 100.0;
            actualAmount = amount * (1.0 - reduction);
        }

        if (actualAmount <= 0) return;

        double newHeat = Math.min(100.0, data.getHeat() + actualAmount);
        data.setHeat(newHeat);
        data.getSession().recordPeakHeat(newHeat);

        // Tier debuff procs are applied as heat rises, before meltdown.
        applyTierDebuffProcs(player, data, newHeat);

        if (newHeat >= 100.0) {
            triggerMeltdown(player, data);
        } else {
            showHeatGauge(player, newHeat);
        }
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
     * Triggers the meltdown punishment.
     */
    private void triggerMeltdown(Player player, PlayerData data) {
        // Punish player
        player.damage(meltdownDamage);
        data.getSession().addHeatDamageTaken(meltdownDamage);

        // Damage rod heavily
        org.bukkit.inventory.ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod.getType() == org.bukkit.Material.FISHING_ROD && rod.getItemMeta() != null) {
            org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) rod.getItemMeta();
            damageable.setDamage(damageable.getDamage() + meltdownDurabilityLoss);
            if (damageable.getDamage() > rod.getType().getMaxDurability()) {
                // Let Bukkit break it later, just set it very high
                damageable.setDamage(rod.getType().getMaxDurability());
            }
            rod.setItemMeta(damageable);
        }

        // Reset heat to config value
        data.setHeat(meltdownResetHeat);

        // Feedback
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 50, 1.0, 1.0, 1.0, 0.1);

        player.sendMessage(Component.text("[Heat] ").color(NamedTextColor.DARK_GRAY)
                .append(Component.text("Meltdown! ").color(NamedTextColor.DARK_RED))
                .append(Component.text("Your rod overheated and vented violently.").color(NamedTextColor.RED)));
        
        // Log to console for analytics
        plugin.getLogger().info("Player " + player.getName() + " suffered a heat meltdown!");
    }

    /**
     * Applies chance-based tier penalties. Higher tiers are harsher and proc more often.
     */
    private void applyTierDebuffProcs(Player player, PlayerData data, double currentHeat) {
        if (currentHeat >= 100.0) return;

        HeatTier tier = getHeatTier(player);
        switch (tier) {
            case HOT -> {
                // 5%: rod takes 2x total catch durability (extra +1 because base catch already did +1)
                if (roll(0.05)) {
                    applyExtraRodDamage(player, 1);
                    player.sendMessage(Component.text("[Heat] ").color(NamedTextColor.DARK_GRAY)
                            .append(Component.text("HOT proc: ").color(NamedTextColor.GOLD))
                            .append(Component.text("your rod strains under the heat (+1 durability damage).")
                                    .color(NamedTextColor.YELLOW)));
                }
            }
            case SCORCHING -> {
                // 15%: rod takes 3x total catch durability (extra +2)
                if (roll(0.15)) {
                    applyExtraRodDamage(player, 2);
                    player.sendMessage(Component.text("[Heat] ").color(NamedTextColor.DARK_GRAY)
                            .append(Component.text("SCORCHING proc: ").color(NamedTextColor.RED))
                            .append(Component.text("your rod chars (+2 durability damage).")
                                    .color(NamedTextColor.GOLD)));
                }
                // 5%: 2 hearts of heat damage
                if (roll(0.05)) {
                    player.damage(4.0);
                    data.getSession().addHeatDamageTaken(4.0);
                    player.sendMessage(Component.text("[Heat] ").color(NamedTextColor.DARK_GRAY)
                            .append(Component.text("SCORCHING proc: ").color(NamedTextColor.RED))
                            .append(Component.text("you are burned for 2 hearts.")
                                    .color(NamedTextColor.DARK_RED)));
                }
            }
            case INFERNAL -> {
                // 30%: rod takes 5x total catch durability (extra +4)
                if (roll(0.30)) {
                    applyExtraRodDamage(player, 4);
                    player.sendMessage(Component.text("[Heat] ").color(NamedTextColor.DARK_GRAY)
                            .append(Component.text("INFERNAL proc: ").color(NamedTextColor.DARK_RED))
                            .append(Component.text("your rod warps (+4 durability damage).")
                                    .color(NamedTextColor.RED)));
                }
                // 15%: 4 hearts of heat damage
                if (roll(0.15)) {
                    player.damage(8.0);
                    data.getSession().addHeatDamageTaken(8.0);
                    player.sendMessage(Component.text("[Heat] ").color(NamedTextColor.DARK_GRAY)
                            .append(Component.text("INFERNAL proc: ").color(NamedTextColor.DARK_RED))
                            .append(Component.text("you are burned for 4 hearts.")
                                    .color(NamedTextColor.RED)));
                }
                // 5%: rod takes +50 bonus durability damage
                if (roll(0.05)) {
                    applyExtraRodDamage(player, 50);
                    player.sendMessage(Component.text("[Heat] ").color(NamedTextColor.DARK_GRAY)
                            .append(Component.text("INFERNAL proc: ").color(NamedTextColor.DARK_RED))
                            .append(Component.text("searing backlash hits your rod (+50 durability damage).")
                                    .color(NamedTextColor.GOLD)));
                }
            }
            default -> {
                // No tier penalties below HOT.
            }
        }
    }

    private boolean roll(double chance) {
        return Math.random() < chance;
    }

    private void applyExtraRodDamage(Player player, int extraDamage) {
        if (extraDamage <= 0) return;

        org.bukkit.inventory.ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod.getType() != org.bukkit.Material.FISHING_ROD || rod.getItemMeta() == null) return;

        org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) rod.getItemMeta();
        int cappedDamage = Math.min(rod.getType().getMaxDurability(), damageable.getDamage() + extraDamage);
        damageable.setDamage(cappedDamage);
        rod.setItemMeta(damageable);
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

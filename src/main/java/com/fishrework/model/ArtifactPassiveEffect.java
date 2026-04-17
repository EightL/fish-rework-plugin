package com.fishrework.model;

import org.bukkit.potion.PotionEffectType;

public final class ArtifactPassiveEffect {

    private final ArtifactPassiveType type;
    private final ArtifactPassiveStat stat;
    private final double value;
    private final PotionEffectType potionEffectType;
    private final int potionAmplifier;
    private final int potionDurationTicks;

    private ArtifactPassiveEffect(ArtifactPassiveType type,
                                  ArtifactPassiveStat stat,
                                  double value,
                                  PotionEffectType potionEffectType,
                                  int potionAmplifier,
                                  int potionDurationTicks) {
        this.type = type;
        this.stat = stat;
        this.value = value;
        this.potionEffectType = potionEffectType;
        this.potionAmplifier = potionAmplifier;
        this.potionDurationTicks = potionDurationTicks;
    }

    public static ArtifactPassiveEffect statBonus(ArtifactPassiveStat stat, double value) {
        return new ArtifactPassiveEffect(ArtifactPassiveType.STAT_BONUS, stat, value, null, 0, 0);
    }

    public static ArtifactPassiveEffect potion(PotionEffectType potionEffectType,
                                               int potionAmplifier,
                                               int potionDurationTicks) {
        return new ArtifactPassiveEffect(ArtifactPassiveType.POTION, null, 0.0, potionEffectType,
            potionAmplifier, potionDurationTicks);
    }

    public ArtifactPassiveType getType() {
        return type;
    }

    public ArtifactPassiveStat getStat() {
        return stat;
    }

    public double getValue() {
        return value;
    }

    public PotionEffectType getPotionEffectType() {
        return potionEffectType;
    }

    public int getPotionAmplifier() {
        return potionAmplifier;
    }

    public int getPotionDurationTicks() {
        return potionDurationTicks;
    }
}
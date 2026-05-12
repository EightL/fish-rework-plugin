package com.fishrework.model;

import java.util.random.RandomGenerator;

/**
 * Runtime weight/size/drop profile for one caught non-treasure creature.
 */
public final class SeaCreatureWeightProfile {

    private final double sizeMultiplier;
    private final double weightKg;
    private final double dropRollMultiplier;

    private SeaCreatureWeightProfile(double sizeMultiplier, double weightKg, double dropRollMultiplier) {
        this.sizeMultiplier = sizeMultiplier;
        this.weightKg = weightKg;
        this.dropRollMultiplier = dropRollMultiplier;
    }

    public double getSizeMultiplier() {
        return sizeMultiplier;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public double getDropRollMultiplier() {
        return dropRollMultiplier;
    }

    public static SeaCreatureWeightProfile roll(double baseWeightKg, Tuning tuning, RandomGenerator random) {
        Tuning safeTuning = tuning != null ? tuning : Tuning.defaults();
        RandomGenerator rng = random != null ? random : RandomGenerator.getDefault();

        boolean lighter = rng.nextDouble() < safeTuning.lighterSideChance();
        double u = rng.nextDouble();
        double sizeMultiplier;
        if (lighter) {
            double spread = (1.0 - safeTuning.minSizeMultiplier()) * Math.pow(u, safeTuning.lighterShape());
            sizeMultiplier = 1.0 - spread;
        } else {
            double spread = (safeTuning.maxSizeMultiplier() - 1.0) * Math.pow(u, safeTuning.heavierShape());
            sizeMultiplier = 1.0 + spread;
        }

        return fromSizeMultiplier(baseWeightKg, sizeMultiplier, safeTuning);
    }

    public static SeaCreatureWeightProfile fromSizeMultiplier(double baseWeightKg,
                                                               double sizeMultiplier,
                                                               Tuning tuning) {
        Tuning safeTuning = tuning != null ? tuning : Tuning.defaults();
        double clampedSize = clamp(sizeMultiplier, safeTuning.minSizeMultiplier(), safeTuning.maxSizeMultiplier());
        double normalizedBaseWeight = Math.max(0.0, baseWeightKg);
        double weightKg = normalizedBaseWeight * Math.pow(clampedSize, safeTuning.weightExponent());
        double dropRollMultiplier = calculateDropRollMultiplier(clampedSize, safeTuning);
        return new SeaCreatureWeightProfile(clampedSize, weightKg, dropRollMultiplier);
    }

    public static Rarity classifyWeight(double baseWeightKg, double weightKg, Tuning tuning) {
        if (baseWeightKg <= 0.0 || weightKg <= 0.0) {
            return Rarity.COMMON;
        }
        Tuning safeTuning = tuning != null ? tuning : Tuning.defaults();
        double sizeMultiplier = Math.pow(weightKg / baseWeightKg, 1.0 / safeTuning.weightExponent());
        return classifySizeMultiplier(sizeMultiplier, safeTuning);
    }

    public static Rarity classifySizeMultiplier(double sizeMultiplier, Tuning tuning) {
        Tuning safeTuning = tuning != null ? tuning : Tuning.defaults();
        double clampedSize = clamp(sizeMultiplier, safeTuning.minSizeMultiplier(), safeTuning.maxSizeMultiplier());
        if (clampedSize >= safeTuning.mythicWeightMinSize()) {
            return Rarity.MYTHIC;
        }
        if (clampedSize >= safeTuning.legendaryWeightMinSize()) {
            return Rarity.LEGENDARY;
        }
        if (clampedSize >= safeTuning.epicWeightMinSize()) {
            return Rarity.EPIC;
        }
        if (clampedSize >= safeTuning.rareWeightMinSize()) {
            return Rarity.RARE;
        }
        if (clampedSize >= safeTuning.uncommonWeightMinSize()) {
            return Rarity.UNCOMMON;
        }
        return Rarity.COMMON;
    }

    private static double calculateDropRollMultiplier(double sizeMultiplier, Tuning tuning) {
        if (sizeMultiplier <= 1.0) {
            double normalized = normalize(sizeMultiplier, tuning.minSizeMultiplier(), 1.0);
            double eased = Math.pow(normalized, tuning.dropUnderweightCurve());
            return lerp(tuning.dropAtMinSize(), tuning.dropAtNormalSize(), eased);
        }

        double normalized = normalize(sizeMultiplier, 1.0, tuning.maxSizeMultiplier());
        double eased = Math.pow(normalized, tuning.dropOverweightCurve());
        return lerp(tuning.dropAtNormalSize(), tuning.dropAtMaxSize(), eased);
    }

    private static double normalize(double value, double min, double max) {
        if (max <= min) {
            return 0.0;
        }
        return clamp((value - min) / (max - min), 0.0, 1.0);
    }

    private static double lerp(double start, double end, double amount) {
        return start + ((end - start) * amount);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Tuning(
            double minSizeMultiplier,
            double maxSizeMultiplier,
            double lighterSideChance,
            double lighterShape,
            double heavierShape,
            double weightExponent,
            double dropAtMinSize,
            double dropAtNormalSize,
            double dropAtMaxSize,
            double dropUnderweightCurve,
            double dropOverweightCurve,
            double uncommonWeightMinSize,
            double rareWeightMinSize,
            double epicWeightMinSize,
            double legendaryWeightMinSize,
            double mythicWeightMinSize
    ) {
        public Tuning {
            minSizeMultiplier = clamp(minSizeMultiplier, 0.05, 0.999);
            maxSizeMultiplier = Math.max(1.001, maxSizeMultiplier);
            lighterSideChance = clamp(lighterSideChance, 0.0, 1.0);
            lighterShape = Math.max(0.05, lighterShape);
            heavierShape = Math.max(0.05, heavierShape);
            weightExponent = Math.max(0.05, weightExponent);
            dropAtNormalSize = Math.max(0.0, dropAtNormalSize);
            dropAtMinSize = clamp(Math.max(0.0, dropAtMinSize), 0.0, dropAtNormalSize);
            dropAtMaxSize = Math.max(dropAtNormalSize, dropAtMaxSize);
            dropUnderweightCurve = Math.max(0.05, dropUnderweightCurve);
            dropOverweightCurve = Math.max(0.05, dropOverweightCurve);
            uncommonWeightMinSize = clamp(uncommonWeightMinSize, 1.0, maxSizeMultiplier);
            rareWeightMinSize = clamp(Math.max(uncommonWeightMinSize, rareWeightMinSize), uncommonWeightMinSize, maxSizeMultiplier);
            epicWeightMinSize = clamp(Math.max(rareWeightMinSize, epicWeightMinSize), rareWeightMinSize, maxSizeMultiplier);
            legendaryWeightMinSize = clamp(Math.max(epicWeightMinSize, legendaryWeightMinSize), epicWeightMinSize, maxSizeMultiplier);
            mythicWeightMinSize = clamp(Math.max(legendaryWeightMinSize, mythicWeightMinSize), legendaryWeightMinSize, maxSizeMultiplier);
        }

        public static Tuning defaults() {
            return new Tuning(
                    0.70,
                    2.00,
                    0.54,
                    1.60,
                    3.50,
                    1.50,
                    0.22,
                    0.40,
                    6.20,
                    0.80,
                    1.25,
                    1.10,
                    1.25,
                    1.50,
                    1.80,
                    1.95
            );
        }
    }
}

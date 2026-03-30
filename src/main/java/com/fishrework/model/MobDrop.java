package com.fishrework.model;

import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Defines a drop from a custom mob.
 * Use the builder to create instances.
 */
public class MobDrop {

    private final Supplier<ItemStack> itemSupplier;
    private final double chance; // 0.0 - 1.0
    private final int minAmount;
    private final int maxAmount;

    private MobDrop(Supplier<ItemStack> itemSupplier, double chance, int minAmount, int maxAmount) {
        this.itemSupplier = itemSupplier;
        this.chance = chance;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    /**
     * Rolls the drop with a chance multiplier.
     * @param multiplier Chance multiplier (e.g. 1.3 for 30% more drops).
     * @return List of drops (can be empty, single, or multiple).
     */
    public java.util.List<ItemStack> roll(double multiplier) {
        double effectiveChance = this.chance * multiplier;
        java.util.List<ItemStack> drops = new java.util.ArrayList<>();
        
        // Calculate how many times to drop
        // e.g. chance 0.5 * mult 1.0 = 0.5 -> 0 guaranteed, 50% for 1
        // e.g. chance 1.0 * mult 1.5 = 1.5 -> 1 guaranteed, 50% for another
        int guaranteed = (int) effectiveChance;
        double leftoverChance = effectiveChance - guaranteed;
        
        int totalRolls = guaranteed;
        if (ThreadLocalRandom.current().nextDouble() < leftoverChance) {
            totalRolls++;
        }
        
        for (int i = 0; i < totalRolls; i++) {
            int amount = minAmount == maxAmount
                    ? minAmount
                    : ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1);

            if (amount <= 0) continue;

            ItemStack item = itemSupplier.get();
            if (item != null) {
                item.setAmount(amount);
                drops.add(item);
            }
        }
        
        return drops;
    }

    public double getChance() { return chance; }
    public int getMinAmount() { return minAmount; }
    public int getMaxAmount() { return maxAmount; }
    public ItemStack getSampleItem() { return itemSupplier.get(); }

    /**
     * Legacy roll method. Defaults to multiplier 1.0 and returns the first item (or null).
     * Maintained for backward compatibility if needed, though most callers should use the list version.
     */
    public ItemStack roll() {
        java.util.List<ItemStack> drops = roll(1.0);
        return drops.isEmpty() ? null : drops.get(0);
    }

    // --- Builder ---

    public static Builder builder(Supplier<ItemStack> itemSupplier) {
        return new Builder(itemSupplier);
    }

    public static class Builder {
        private final Supplier<ItemStack> itemSupplier;
        private double chance = 1.0;
        private int minAmount = 1;
        private int maxAmount = 1;

        private Builder(Supplier<ItemStack> itemSupplier) {
            this.itemSupplier = itemSupplier;
        }

        public Builder chance(double chance) {
            this.chance = chance;
            return this;
        }

        public Builder amount(int min, int max) {
            this.minAmount = min;
            this.maxAmount = max;
            return this;
        }

        public Builder amount(int fixed) {
            this.minAmount = fixed;
            this.maxAmount = fixed;
            return this;
        }

        public MobDrop build() {
            return new MobDrop(itemSupplier, chance, minAmount, maxAmount);
        }
    }
}

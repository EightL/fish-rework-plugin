package com.fishrework.model;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeaCreatureWeightProfileTest {

    private static final SeaCreatureWeightProfile.Tuning TUNING = SeaCreatureWeightProfile.Tuning.defaults();

    @Test
    void normalSizeKeepsBaseWeightButCutsDefaultDropRolls() {
        SeaCreatureWeightProfile profile = SeaCreatureWeightProfile.fromSizeMultiplier(100.0, 1.0, TUNING);

        assertEquals(1.0, profile.getSizeMultiplier(), 0.000001);
        assertEquals(100.0, profile.getWeightKg(), 0.000001);
        assertEquals(0.40, profile.getDropRollMultiplier(), 0.000001);
    }

    @Test
    void configuredEndpointsStayBoundedAndRewardLargeOutliers() {
        SeaCreatureWeightProfile light = SeaCreatureWeightProfile.fromSizeMultiplier(100.0, 0.20, TUNING);
        SeaCreatureWeightProfile giant = SeaCreatureWeightProfile.fromSizeMultiplier(100.0, 4.00, TUNING);

        assertEquals(0.70, light.getSizeMultiplier(), 0.000001);
        assertEquals(1.60, giant.getSizeMultiplier(), 0.000001);
        assertTrue(light.getWeightKg() < 100.0);
        assertTrue(giant.getWeightKg() > 100.0);
        assertEquals(0.22, light.getDropRollMultiplier(), 0.000001);
        assertEquals(4.00, giant.getDropRollMultiplier(), 0.000001);
    }

    @Test
    void defaultSamplingTargetsLowerLootVolumeWithTighterOutlierRewards() {
        Random random = new Random(0xF15A_C0DE);
        int sampleCount = 200_000;
        double dropRollSum = 0.0;
        int nearMaximumSize = 0;
        int lightOutliers = 0;

        for (int i = 0; i < sampleCount; i++) {
            SeaCreatureWeightProfile profile = SeaCreatureWeightProfile.roll(100.0, TUNING, random);
            dropRollSum += profile.getDropRollMultiplier();
            if (profile.getSizeMultiplier() >= 1.57) {
                nearMaximumSize++;
            }
            if (profile.getSizeMultiplier() <= 0.72) {
                lightOutliers++;
            }
        }

        double averageDropRollMultiplier = dropRollSum / sampleCount;
        assertTrue(averageDropRollMultiplier > 0.56 && averageDropRollMultiplier < 0.59,
                "Expected total drop roll volume to land near a 41-44% nerf, got " + averageDropRollMultiplier);
        assertTrue(nearMaximumSize < sampleCount * 0.01,
                "1.6x-scale creatures should stay exceptional");
        assertTrue(lightOutliers > nearMaximumSize,
                "0.7x-scale outliers should be more common than 1.6x-scale outliers");
    }
}

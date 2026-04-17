package com.fishrework.model;

import java.util.Locale;

public enum ArtifactPassiveStat {
    RARE_CREATURE_CHANCE("rare_creature_chance"),
    TREASURE_CHANCE("treasure_chance"),
    FISHING_XP_BONUS("fishing_xp_bonus"),
    DOUBLE_CATCH_CHANCE("double_catch_chance"),
    FISHING_SPEED("fishing_speed"),
    SEA_CREATURE_ATTACK("sea_creature_attack"),
    SEA_CREATURE_DEFENSE("sea_creature_defense"),
    HEAT_RESISTANCE("heat_resistance");

    private final String configKey;

    ArtifactPassiveStat(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }

    public static ArtifactPassiveStat fromConfigValue(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (ArtifactPassiveStat stat : values()) {
            if (stat.configKey.equals(normalized)) {
                return stat;
            }
        }
        return null;
    }
}
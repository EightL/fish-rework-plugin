package com.fishrework.model;

import java.util.Locale;

public enum ArtifactPassiveType {
    STAT_BONUS,
    POTION;

    public static ArtifactPassiveType fromConfigValue(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (ArtifactPassiveType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
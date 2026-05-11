package com.fishrework.model;

import java.util.Locale;

public enum SeaCreatureMessageMode {
    NONE,
    RARE_ONLY,
    ALL;

    public String getId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public SeaCreatureMessageMode next() {
        return switch (this) {
            case ALL -> RARE_ONLY;
            case RARE_ONLY -> NONE;
            case NONE -> ALL;
        };
    }

    public static SeaCreatureMessageMode fromInput(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "none", "off", "false", "0" -> NONE;
            case "rare", "rare_only", "rares", "1" -> RARE_ONLY;
            case "all", "on", "true", "2" -> ALL;
            default -> null;
        };
    }
}

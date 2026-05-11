package com.fishrework.model;

import java.util.Locale;

public enum AutoSellMode {
    OFF,
    OTHER,
    ALL;

    public String getId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public AutoSellMode next() {
        return switch (this) {
            case OFF -> OTHER;
            case OTHER -> ALL;
            case ALL -> OFF;
        };
    }

    public static AutoSellMode fromInput(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "off", "false", "none", "0" -> OFF;
            case "other", "junk", "safe" -> OTHER;
            case "all", "on", "true", "1" -> ALL;
            default -> null;
        };
    }
}

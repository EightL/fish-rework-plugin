package com.fishrework.model;

public enum ParticleDetailMode {
    HIGH("high", 1.0),
    MEDIUM("medium", 0.55),
    LOW("low", 0.25);

    private final String id;
    private final double particleScale;

    ParticleDetailMode(String id, double particleScale) {
        this.id = id;
        this.particleScale = particleScale;
    }

    public String getId() {
        return id;
    }

    public double getParticleScale() {
        return particleScale;
    }

    public static ParticleDetailMode fromInput(String input) {
        if (input == null) return null;
        String normalized = input.trim().toLowerCase();
        return switch (normalized) {
            case "high", "full", "max", "3" -> HIGH;
            case "medium", "med", "mid", "2" -> MEDIUM;
            case "low", "minimal", "min", "1" -> LOW;
            default -> null;
        };
    }
}
package com.fishrework.model;

import org.bukkit.Material;

public enum Skill {
    FISHING("Fishing", Material.FISHING_ROD, "Catch fish and treasures to level up!");

    private final String displayName;
    private final Material icon;
    private final String description;

    Skill(String displayName, Material icon, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }
}

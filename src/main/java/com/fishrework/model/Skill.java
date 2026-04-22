package com.fishrework.model;

import com.fishrework.manager.LanguageManager;
import org.bukkit.Material;

import java.util.Locale;

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

    public String getLocalizedDisplayName(LanguageManager languageManager) {
        return languageManager.getString("skill." + name().toLowerCase(Locale.ROOT) + ".name", displayName);
    }

    public Material getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public String getLocalizedDescription(LanguageManager languageManager) {
        return languageManager.getString("skill." + name().toLowerCase(Locale.ROOT) + ".description", description);
    }

    public String getLocalizedMobSource(LanguageManager languageManager) {
        return languageManager.getString(
                "skill." + name().toLowerCase(Locale.ROOT) + ".mob_source",
                getLocalizedDisplayName(languageManager) + " Mob",
                "skill", getLocalizedDisplayName(languageManager));
    }
}

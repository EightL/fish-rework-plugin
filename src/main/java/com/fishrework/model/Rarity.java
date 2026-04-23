package com.fishrework.model;

import com.fishrework.manager.LanguageManager;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Locale;

public enum Rarity {
    COMMON(NamedTextColor.WHITE),
    UNCOMMON(NamedTextColor.GREEN),
    RARE(NamedTextColor.BLUE),
    EPIC(NamedTextColor.DARK_PURPLE),
    LEGENDARY(NamedTextColor.GOLD),
    MYTHIC(TextColor.color(0x8B0000)),
    SPECIAL(TextColor.color(0xFF69B4));

    private final TextColor color;

    Rarity(TextColor color) {
        this.color = color;
    }

    public TextColor getColor() {
        return color;
    }

    public String getLocalizedName(LanguageManager languageManager) {
        return languageManager.getString(
                "rarity." + name().toLowerCase(Locale.ROOT) + ".name",
                toFriendlyName());
    }

    private String toFriendlyName() {
        String lower = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}

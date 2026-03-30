package com.fishrework.model;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public enum Rarity {
    COMMON(NamedTextColor.WHITE),
    UNCOMMON(NamedTextColor.GREEN),
    RARE(NamedTextColor.BLUE),
    EPIC(NamedTextColor.DARK_PURPLE),
    LEGENDARY(NamedTextColor.GOLD),
    MYTHIC(TextColor.color(0x8B0000));

    private final TextColor color;

    Rarity(TextColor color) {
        this.color = color;
    }

    public TextColor getColor() {
        return color;
    }
}

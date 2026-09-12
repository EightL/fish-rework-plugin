package com.fishrework.loader;

import com.fishrework.FishRework;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Locale;
import java.util.Set;

final class YamlParseSupport {
    private static final Set<String> NEWER_ENTITIES = Set.of(
            "CREAKING", "PARCHED", "CAMEL_HUSK", "NAUTILUS", "ZOMBIE_NAUTILUS", "SULFUR_CUBE");

    private YamlParseSupport() {
    }

    static <E extends Enum<E>> E parseEnum(FishRework plugin,
                                           Class<E> enumClass,
                                           String rawValue,
                                           E fallback,
                                           String context) {
        E parsed = parseEnumOrNull(plugin, enumClass, rawValue, context);
        return parsed != null ? parsed : fallback;
    }

    static <E extends Enum<E>> E parseEnumOrNull(FishRework plugin,
                                                 Class<E> enumClass,
                                                 String rawValue,
                                                 String context) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        try {
            return Enum.valueOf(enumClass, normalized);
        } catch (IllegalArgumentException ex) {
            // Expected differences in bundled content are not invalid configuration.
            if (enumClass == EntityType.class && NEWER_ENTITIES.contains(normalized)) {
                return null;
            }
            if (enumClass == Material.class) {
                String olderMaterial = switch (normalized) {
                    case "COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST" -> "CHEST";
                    case "NETHERITE_SPEAR" -> "NETHERITE_SWORD";
                    default -> null;
                };
                if (olderMaterial != null) return Enum.valueOf(enumClass, olderMaterial);
            }
            plugin.getLogger().warning("Invalid " + enumClass.getSimpleName() + " '" + rawValue
                    + "' in " + context + " - skipping/fallback will be used.");
            return null;
        }
    }
}

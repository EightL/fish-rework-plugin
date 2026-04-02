package com.fishrework.loader;

import com.fishrework.FishRework;

import java.util.Locale;

final class YamlParseSupport {

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
            plugin.getLogger().warning("Invalid " + enumClass.getSimpleName() + " '" + rawValue
                    + "' in " + context + " - skipping/fallback will be used.");
            return null;
        }
    }
}
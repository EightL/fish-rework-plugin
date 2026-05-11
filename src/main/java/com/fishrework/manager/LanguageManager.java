package com.fishrework.manager;

import com.fishrework.FishRework;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class LanguageManager {

    private final FishRework plugin;
    private final Map<String, FileConfiguration> localeConfigs = new ConcurrentHashMap<>();
    private final Map<String, String> normalizedLocaleCache = new ConcurrentHashMap<>();
    private final ThreadLocal<Player> playerContext = new ThreadLocal<>();
    private volatile Set<String> knownLocales = Set.of("en", "es", "zh_CN");
    private String defaultLocale = "en";
    private FileConfiguration langConfig;
    private File langFile;

    public LanguageManager(FishRework plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        localeConfigs.clear();
        normalizedLocaleCache.clear();
        String configuredDefaultLocale = plugin.getConfig().getString("locale", "en");

        Set<String> locales = new LinkedHashSet<>(List.of("en", "es", "zh_CN"));
        File[] files = plugin.getDataFolder().listFiles((dir, name) -> name.startsWith("lang_") && name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                locales.add(name.substring("lang_".length(), name.length() - ".yml".length()));
            }
        }
        knownLocales = Set.copyOf(locales);

        defaultLocale = normalizeLocale(configuredDefaultLocale);
        if (defaultLocale == null) {
            defaultLocale = "en";
        }
        locales.add(defaultLocale);
        knownLocales = Set.copyOf(locales);

        for (String locale : locales) {
            loadLocale(locale);
        }

        langConfig = localeConfigs.getOrDefault(defaultLocale, localeConfigs.get("en"));
        if (langConfig == null) {
            langConfig = new YamlConfiguration();
        }
        langFile = new File(plugin.getDataFolder(), "lang_" + defaultLocale + ".yml");
    }

    public void reload() {
        initialize();
    }

    public <T> T withPlayer(Player player, Supplier<T> supplier) {
        Player previous = playerContext.get();
        playerContext.set(player);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                playerContext.remove();
            } else {
                playerContext.set(previous);
            }
        }
    }

    public void withPlayer(Player player, Runnable runnable) {
        withPlayer(player, () -> {
            runnable.run();
            return null;
        });
    }

    public Component getMessage(String key) {
        String text = getContextConfig().getString(key);
        if (text == null) {
            return Component.text(key);
        }
        return parseString(text);
    }

    public Component getMessage(Player player, String key) {
        String text = getConfig(player).getString(key);
        if (text == null) {
            return Component.text(key);
        }
        return parseString(text);
    }
    
    public Component getMessage(String key, String fallback) {
        String text = getContextConfig().getString(key, fallback);
        return parseString(text);
    }

    public Component getMessage(Player player, String key, String fallback) {
        return parseString(getConfig(player).getString(key, fallback));
    }

    public Component getMessage(String key, String fallback, Map<String, String> placeholders) {
        String text = getContextConfig().getString(key, fallback);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                text = text.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return parseString(text);
    }

    public Component getMessage(Player player, String key, String fallback, Map<String, String> placeholders) {
        String text = getConfig(player).getString(key, fallback);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                text = text.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return parseString(text);
    }

    public Component getMessage(String key, String fallback, String... placeholders) {
        String text = getContextConfig().getString(key, fallback);
        if (placeholders != null) {
            int pairCount = placeholders.length - (placeholders.length % 2);
            for (int i = 0; i < pairCount; i += 2) {
                text = text.replace("%" + placeholders[i] + "%", placeholders[i + 1]);
            }
        }
        return parseString(text);
    }

    public Component getMessage(Player player, String key, String fallback, String... placeholders) {
        String text = getConfig(player).getString(key, fallback);
        if (placeholders != null) {
            int pairCount = placeholders.length - (placeholders.length % 2);
            for (int i = 0; i < pairCount; i += 2) {
                text = text.replace("%" + placeholders[i] + "%", placeholders[i + 1]);
            }
        }
        return parseString(text);
    }

    public String getString(String key, String fallback) {
        String text = getContextConfig().getString(key, fallback);
        return text.replace('&', '§');
    }

    public String getString(Player player, String key, String fallback) {
        return getConfig(player).getString(key, fallback).replace('&', '§');
    }

    public String getString(String key, String fallback, Map<String, String> placeholders) {
        String text = getContextConfig().getString(key, fallback);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                text = text.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return text.replace('&', '§');
    }

    public String getString(Player player, String key, String fallback, Map<String, String> placeholders) {
        String text = getConfig(player).getString(key, fallback);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                text = text.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        return text.replace('&', '§');
    }

    public String getString(String key, String fallback, String... placeholders) {
        String text = getContextConfig().getString(key, fallback);
        if (placeholders != null) {
            int pairCount = placeholders.length - (placeholders.length % 2);
            for (int i = 0; i < pairCount; i += 2) {
                text = text.replace("%" + placeholders[i] + "%", placeholders[i + 1]);
            }
        }
        return text.replace('&', '§');
    }

    public String getString(Player player, String key, String fallback, String... placeholders) {
        String text = getConfig(player).getString(key, fallback);
        if (placeholders != null) {
            int pairCount = placeholders.length - (placeholders.length % 2);
            for (int i = 0; i < pairCount; i += 2) {
                text = text.replace("%" + placeholders[i] + "%", placeholders[i + 1]);
            }
        }
        return text.replace('&', '§');
    }

    public String getCurrencyName() {
        String fallback = plugin.getConfig().getString("economy.currency_name", "Doubloons");
        return getString("common.currency_name", fallback);
    }

    public String getCurrencyName(Player player) {
        String fallback = plugin.getConfig().getString("economy.currency_name", "Doubloons");
        return getString(player, "common.currency_name", fallback);
    }

    public List<String> getAvailableLocales() {
        List<String> locales = new ArrayList<>(localeConfigs.keySet());
        Collections.sort(locales);
        return locales;
    }

    public boolean isLocaleAvailable(String locale) {
        String normalized = normalizeLocale(locale);
        if (normalized == null) {
            return false;
        }
        return localeConfigs.containsKey(normalized)
                || new File(plugin.getDataFolder(), "lang_" + normalized + ".yml").exists()
                || plugin.getResource("lang_" + normalized + ".yml") != null;
    }

    public String normalizeLocale(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String requested = input.trim();
        if (requested.equalsIgnoreCase("chinese") || requested.equalsIgnoreCase("zh") || requested.equalsIgnoreCase("zh_cn")) {
            requested = "zh_CN";
        } else if (requested.equalsIgnoreCase("spanish")) {
            requested = "es";
        } else if (requested.equalsIgnoreCase("english")) {
            requested = "en";
        }
        String cacheKey = requested.toLowerCase();
        String cached = normalizedLocaleCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Set<String> known = new HashSet<>(knownLocales);
        known.addAll(localeConfigs.keySet());
        for (String locale : known) {
            if (locale.equalsIgnoreCase(requested)) {
                normalizedLocaleCache.put(cacheKey, locale);
                return locale;
            }
        }
        return null;
    }

    public String getPlayerLocale(Player player) {
        if (player == null) {
            return defaultLocale;
        }
        com.fishrework.model.PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null || data.getLanguageLocale() == null || data.getLanguageLocale().isBlank()) {
            return defaultLocale;
        }
        String normalized = normalizeLocale(data.getLanguageLocale());
        return normalized == null ? defaultLocale : normalized;
    }

    public String getLocaleDisplayName(Player player, String locale) {
        String normalized = normalizeLocale(locale);
        if (normalized == null) {
            return locale;
        }
        FileConfiguration config = getConfig(normalized);
        return config.getString("language.name", defaultLocaleName(normalized));
    }

    public String cycleLocale(Player player) {
        List<String> locales = getAvailableLocales();
        if (locales.isEmpty()) {
            return defaultLocale;
        }
        String current = getPlayerLocale(player);
        int index = locales.indexOf(current);
        return locales.get((index + 1 + locales.size()) % locales.size());
    }

    private FileConfiguration getConfig(Player player) {
        return getConfig(getPlayerLocale(player));
    }

    private FileConfiguration getContextConfig() {
        Player player = playerContext.get();
        return player == null ? langConfig : getConfig(player);
    }

    private FileConfiguration getConfig(String locale) {
        FileConfiguration config = localeConfigs.get(locale);
        if (config != null) {
            return config;
        }
        FileConfiguration loaded = loadLocale(locale);
        if (loaded != null) {
            return loaded;
        }
        return langConfig == null ? new YamlConfiguration() : langConfig;
    }

    private FileConfiguration loadLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return null;
        }
        FileConfiguration existing = localeConfigs.get(locale);
        if (existing != null) {
            return existing;
        }

        String fileName = "lang_" + locale + ".yml";
        File file = new File(plugin.getDataFolder(), fileName);

        if (!file.exists()) {
            file.getParentFile().mkdirs();
            if (plugin.getResource(fileName) != null) {
                plugin.saveResource(fileName, false);
            } else {
                return null;
            }
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        try (InputStream defaultStream = plugin.getResource(fileName)) {
            if (defaultStream != null) {
                YamlConfiguration defaultConf = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                YamlConfiguration englishDefaults = "en".equals(locale) ? null : loadBundledLocale("en");
                int[] counts = mergeDefaults(config, defaultConf, englishDefaults);
                if (counts[0] + counts[1] > 0) {
                    config.save(file);
                    plugin.getLogger().info(String.format(
                            "Updated %s: added %d new key(s), refreshed %d stale English fallback(s).",
                            fileName, counts[0], counts[1]));
                }
                config.setDefaults(defaultConf);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load default language values for " + fileName + ".");
        }

        localeConfigs.put(locale, config);
        Set<String> updated = new HashSet<>(knownLocales);
        updated.add(locale);
        knownLocales = Set.copyOf(updated);
        normalizedLocaleCache.clear();
        return config;
    }

    /**
     * Merges bundled defaults into the user's lang file.
     * Returns [addedCount, refreshedCount].
     *
     * Two cases overwrite the user's value:
     *   1. Key absent from user file → copied from locale defaults (added).
     *   2. Key present, but user value equals the bundled English value AND the bundled
     *      locale value differs from English → user has a stale English fallback that
     *      now has a real translation (refreshed).
     * Admin-customized values (different from both en and locale defaults) are preserved.
     */
    private int[] mergeDefaults(FileConfiguration target, YamlConfiguration localeDefaults, YamlConfiguration englishDefaults) {
        int added = 0;
        int refreshed = 0;
        for (String path : localeDefaults.getKeys(true)) {
            if (localeDefaults.isConfigurationSection(path)) {
                continue;
            }
            if (!target.isSet(path)) {
                target.set(path, localeDefaults.get(path));
                added++;
                continue;
            }
            if (englishDefaults == null) {
                continue;
            }
            Object userValue = target.get(path);
            Object enValue = englishDefaults.get(path);
            Object localeValue = localeDefaults.get(path);
            if (userValue != null && enValue != null && localeValue != null
                    && userValue.equals(enValue) && !localeValue.equals(enValue)) {
                target.set(path, localeValue);
                refreshed++;
            }
        }
        return new int[] { added, refreshed };
    }

    private YamlConfiguration loadBundledLocale(String locale) {
        try (InputStream stream = plugin.getResource("lang_" + locale + ".yml")) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private String defaultLocaleName(String locale) {
        return switch (locale) {
            case "en" -> "English";
            case "es" -> "Español";
            case "zh_CN" -> "中文";
            default -> locale;
        };
    }

    private Component parseString(String text) {
        if (text.contains("<") && text.contains(">")) {
            try {
                return MiniMessage.miniMessage().deserialize(text);
            } catch (Exception ignored) {}
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
    
    public FileConfiguration getConfig() {
        return langConfig;
    }
}

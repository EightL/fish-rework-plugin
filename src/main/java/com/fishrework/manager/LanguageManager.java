package com.fishrework.manager;

import com.fishrework.FishRework;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class LanguageManager {

    private final FishRework plugin;
    private FileConfiguration langConfig;
    private File langFile;

    public LanguageManager(FishRework plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        String locale = plugin.getConfig().getString("locale", "en");
        String fileName = "lang_" + locale + ".yml";
        langFile = new File(plugin.getDataFolder(), fileName);

        if (!langFile.exists()) {
            langFile.getParentFile().mkdirs();
            if (plugin.getResource(fileName) != null) {
                plugin.saveResource(fileName, false);
            } else {
                try {
                    langFile.createNewFile();
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not create language file " + fileName);
                }
            }
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);

        try {
            InputStream defaultStream = plugin.getResource(fileName);
            if (defaultStream != null) {
                YamlConfiguration defaultConf = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                langConfig.setDefaults(defaultConf);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load default language values.");
        }
    }

    public void reload() {
        initialize();
    }

    public Component getMessage(String key) {
        String text = langConfig.getString(key);
        if (text == null) {
            return Component.text(key);
        }
        return parseString(text);
    }
    
    public Component getMessage(String key, String fallback) {
        String text = langConfig.getString(key, fallback);
        return parseString(text);
    }

    public String getString(String key, String fallback) {
        String text = langConfig.getString(key, fallback);
        return text.replace('&', '§');
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

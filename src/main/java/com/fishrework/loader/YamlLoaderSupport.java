package com.fishrework.loader;

import com.fishrework.FishRework;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

final class YamlLoaderSupport {

    private YamlLoaderSupport() {
    }

    static YamlConfiguration loadYaml(FishRework plugin, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    static ConfigurationSection requireSection(FishRework plugin, YamlConfiguration yaml,
                                               String sectionName, String fileName) {
        ConfigurationSection section = yaml.getConfigurationSection(sectionName);
        if (section == null) {
            plugin.getLogger().warning("No '" + sectionName + "' section found in " + fileName);
        }
        return section;
    }
}
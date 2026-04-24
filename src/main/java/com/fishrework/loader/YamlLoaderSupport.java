package com.fishrework.loader;

import com.fishrework.FishRework;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class YamlLoaderSupport {

    private YamlLoaderSupport() {
    }

    static YamlConfiguration loadYaml(FishRework plugin, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource(fileName)) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                boolean migrated = YamlContentMigrator.migrate(plugin, fileName, file, config, defaults);
                boolean addedDefaults = copyMissingPaths(config, defaults);
                if (migrated || addedDefaults) {
                    config.save(file);
                    if (addedDefaults) {
                        plugin.getLogger().info("Added missing defaults to " + fileName + ".");
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to update defaults for " + fileName + ": " + e.getMessage());
        }
        return config;
    }

    private static boolean copyMissingPaths(YamlConfiguration target, YamlConfiguration defaults) {
        boolean changed = false;
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path)) {
                continue;
            }
            if (!target.contains(path)) {
                target.set(path, defaults.get(path));
                changed = true;
            }
        }
        return changed;
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

package com.fishrework.loader;

import com.fishrework.FishRework;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class YamlContentMigrator {

    private static final String VERSION_KEY = "content_version";
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final List<String> RECIPE_V2_REPLACEMENTS = List.of(
            "shaped.treasure_totem_recipe",
            "shaped.trident_2_recipe",
            "shaped.trident_3_recipe",
            "shapeless.shredder_recipe",
            "upgrade.dreadplate_helmet_recipe",
            "upgrade.dreadplate_chestplate_recipe",
            "upgrade.dreadplate_leggings_recipe",
            "upgrade.dreadplate_boots_recipe",
            "upgrade.volcanic_dreadplate_leggings_recipe",
            "upgrade.leviathan_recipe",
            "upgrade.quickcharge_repeater_2_recipe",
            "upgrade.quickcharge_repeater_3_recipe",
            "upgrade.multishot_volley_2_recipe",
            "upgrade.multishot_volley_3_recipe"
    );

    private YamlContentMigrator() {
    }

    static boolean migrate(FishRework plugin, String fileName, File file,
                           YamlConfiguration config, YamlConfiguration defaults) throws IOException {
        int currentVersion = defaults.getInt(VERSION_KEY, 1);
        int fileVersion = config.getInt(VERSION_KEY, 1);
        if (fileVersion >= currentVersion) {
            return false;
        }

        backup(plugin, fileName, file, fileVersion);

        if ("recipes.yml".equals(fileName) && fileVersion < 2) {
            for (String path : RECIPE_V2_REPLACEMENTS) {
                replaceSectionFromDefaults(config, defaults, path);
            }
        }

        config.set(VERSION_KEY, currentVersion);
        plugin.getLogger().info("Migrated " + fileName + " content from v" + fileVersion + " to v" + currentVersion + ".");
        return true;
    }

    private static boolean replaceSectionFromDefaults(YamlConfiguration target, YamlConfiguration defaults, String path) {
        ConfigurationSection source = defaults.getConfigurationSection(path);
        if (source == null) {
            return false;
        }

        target.set(path, null);
        for (String childPath : source.getKeys(true)) {
            if (!source.isConfigurationSection(childPath)) {
                target.set(path + "." + childPath, source.get(childPath));
            }
        }
        return true;
    }

    private static void backup(FishRework plugin, String fileName, File file, int fileVersion) throws IOException {
        Path backupDir = plugin.getDataFolder().toPath().resolve("backups");
        Files.createDirectories(backupDir);

        String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP);
        Path backup = backupDir.resolve(fileName + ".v" + fileVersion + "." + timestamp + ".bak");
        Files.copy(file.toPath(), backup, StandardCopyOption.COPY_ATTRIBUTES);
        plugin.getLogger().info("Backed up " + fileName + " before migration to " + backup.getFileName() + ".");
    }
}

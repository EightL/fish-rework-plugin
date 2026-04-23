package com.fishrework.util;

import com.fishrework.FishRework;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    private static final String API_URL    = "https://api.modrinth.com/v2/project/%s/version";
    private static final String MODRINTH_PAGE = "https://modrinth.com/plugin/%s";

    private final FishRework plugin;
    private final String projectId;

    private volatile boolean updateAvailable = false;
    private volatile String latestVersion = null;

    public UpdateChecker(FishRework plugin, String projectId) {
        this.plugin = plugin;
        this.projectId = projectId;
    }

    /** Fires off an async Modrinth version check. Safe to call from onEnable(). */
    public void checkAsync() {
        if (projectId == null || projectId.isBlank()) return;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(com.fishrework.util.FormatUtil.format(API_URL, projectId)).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5_000);
                conn.setReadTimeout(5_000);
                // Modrinth requires a descriptive User-Agent
                conn.setRequestProperty("User-Agent",
                    "FishRework/" + plugin.getDescription().getVersion() +
                    " (github.com/EightL/fish-rework)");

                if (conn.getResponseCode() != 200) return;

                JsonArray versions = JsonParser.parseReader(
                    new InputStreamReader(conn.getInputStream())
                ).getAsJsonArray();

                if (versions.isEmpty()) return;

                String remote  = versions.get(0).getAsJsonObject().get("version_number").getAsString();
                String current = plugin.getDescription().getVersion();

                if (!remote.equalsIgnoreCase(current)) {
                    latestVersion = remote;
                    updateAvailable = true;
                    plugin.getLogger().info("[Fish Rework] Update available: v" + remote
                        + " (running v" + current + ") — modrinth.com/plugin/" + projectId);
                }
            } catch (Exception ignored) {
                // Never crash startup over an update check
            }
        });
    }

    /**
     * Sends a clickable update notification to a player.
     * No-ops if no update was found or the check hasn't returned yet.
     */
    public void notifyPlayer(Player player) {
        if (!updateAvailable || latestVersion == null) return;
        player.sendMessage(
            plugin.getLanguageManager().getMessage("updatechecker.prefix", "[FishRework] ").color(NamedTextColor.AQUA)
                .append(plugin.getLanguageManager().getMessage("updatechecker.update_available", "Update available: ").color(NamedTextColor.YELLOW))
                .append(Component.text(plugin.getLanguageManager().getString(
                                "updatechecker.version_label",
                                "v%version%",
                                "version", latestVersion), NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD))
                .append(plugin.getLanguageManager().getMessage("updatechecker.click_to_download", "  Click to download").color(NamedTextColor.GRAY)
                    .clickEvent(ClickEvent.openUrl(com.fishrework.util.FormatUtil.format(MODRINTH_PAGE, projectId))))
        );
    }

    public boolean isUpdateAvailable() { return updateAvailable; }
    public String getLatestVersion()   { return latestVersion; }
}

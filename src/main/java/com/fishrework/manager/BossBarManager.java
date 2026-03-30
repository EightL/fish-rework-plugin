package com.fishrework.manager;

import com.fishrework.FishRework;
import com.fishrework.leveling.LevelManager;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Skill;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossBarManager {

    private final FishRework plugin;
    private final Map<UUID, BossBarData> activeBossBars = new HashMap<>();

    public BossBarManager(FishRework plugin) {
        this.plugin = plugin;
    }

    private static class BossBarData {
        BossBar bossBar;
        BukkitTask removeTask;

        BossBarData(BossBar bossBar, BukkitTask removeTask) {
            this.bossBar = bossBar;
            this.removeTask = removeTask;
        }
    }

    public void showBossBar(Player player, Skill skill) {
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerData(uuid);
        LevelManager levelManager = plugin.getLevelManager();

        double currentXp = data.getXp(skill);
        double nextXp = data.getNextLevelXp(skill, levelManager);

        // Calculate progress (XP is per-level, resets each level)
        double progress = 0.0;
        if (nextXp > 0) {
            progress = currentXp / nextXp;
        }
        if (progress < 0) progress = 0;
        if (progress > 1) progress = 1;

        String title = String.format("§b%s Level %d §7(%.1f/%.1f)",
                skill.getDisplayName(), data.getLevel(skill), currentXp, nextXp);

        BossBarData barData = activeBossBars.get(uuid);

        if (barData != null) {
            // Update existing
            barData.bossBar.setTitle(title);
            barData.bossBar.setProgress(progress);
            if (barData.removeTask != null) {
                barData.removeTask.cancel();
            }
        } else {
            // Create new
            BossBar bar = Bukkit.createBossBar(title, BarColor.BLUE, BarStyle.SEGMENTED_10);
            bar.setProgress(progress);
            bar.addPlayer(player);
            barData = new BossBarData(bar, null);
            activeBossBars.put(uuid, barData);
        }

        // Schedule removal
        BossBar finalBar = barData.bossBar;
        barData.removeTask = new BukkitRunnable() {
            @Override
            public void run() {
                finalBar.removePlayer(player);
                activeBossBars.remove(uuid);
            }
        }.runTaskLater(plugin, plugin.getConfig().getLong("gui.boss_bar_duration_ticks", 100)); // 5 seconds (20 ticks * 5)
    }
    public void removeAllBossBars(Player player) {
        BossBarData data = activeBossBars.remove(player.getUniqueId());
        if (data != null) {
            data.bossBar.removePlayer(player);
            if (data.removeTask != null) {
                data.removeTask.cancel();
            }
        }
    }
}

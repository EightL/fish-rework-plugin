package com.fishrework.manager;

import com.fishrework.FishRework;
import com.fishrework.leveling.LevelManager;
import com.fishrework.model.CustomMob;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Skill;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BossBarManager {

    private final FishRework plugin;
    private final Map<UUID, BossBarData> activeBossBars = new HashMap<>();
    private final Map<UUID, MobBossBarData> activeMobBossBars = new HashMap<>();

    private static final Set<String> THEMED_BOSS_MOBS = Set.of(
            "king_slime",
            "ghast_broodmother",
            "poseidon",
            "crimson_abomination",
            "wailing_ghast_duo",
            "ender_angler",      // Riftbound Colossus
            "nether_lord",
            "dead_rider",
            "warden"
    );

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

    private static class MobBossBarData {
        String mobId;
        BossBar bossBar;

        MobBossBarData(String mobId, BossBar bossBar) {
            this.mobId = mobId;
            this.bossBar = bossBar;
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

        for (MobBossBarData mobData : activeMobBossBars.values()) {
            mobData.bossBar.removePlayer(player);
        }
    }

    public void updateMobBossBar(LivingEntity entity, String mobId) {
        if (entity == null || mobId == null) {
            return;
        }

        UUID uuid = entity.getUniqueId();
        if (!isThemedBossMob(mobId)) {
            removeMobBossBar(uuid);
            return;
        }

        double maxHealth = resolveMaxHealth(entity);
        if (maxHealth <= 0.0) {
            removeMobBossBar(uuid);
            return;
        }

        MobBossBarData data = activeMobBossBars.get(uuid);
        BarColor color = getMobBossBarColor(mobId);
        String title = buildMobBossTitle(entity, mobId, maxHealth);
        double progress = Math.max(0.0, Math.min(1.0, entity.getHealth() / maxHealth));

        if (data == null || !mobId.equals(data.mobId)) {
            if (data != null) {
                data.bossBar.removeAll();
            }
            BossBar bar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
            data = new MobBossBarData(mobId, bar);
            activeMobBossBars.put(uuid, data);
        }

        data.bossBar.setColor(color);
        data.bossBar.setTitle(title);
        data.bossBar.setProgress(progress);
        syncMobBossBarPlayers(entity, data.bossBar);
    }

    public void removeMobBossBar(UUID entityId) {
        MobBossBarData data = activeMobBossBars.remove(entityId);
        if (data != null) {
            data.bossBar.removeAll();
        }
    }

    public void cleanupMobBossBars() {
        Iterator<Map.Entry<UUID, MobBossBarData>> iterator = activeMobBossBars.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MobBossBarData> entry = iterator.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                entry.getValue().bossBar.removeAll();
                iterator.remove();
            }
        }
    }

    private boolean isThemedBossMob(String mobId) {
        return THEMED_BOSS_MOBS.contains(mobId);
    }

    private void syncMobBossBarPlayers(LivingEntity entity, BossBar bossBar) {
        double range = plugin.getConfig().getDouble("combat.mob_boss_bar_range", 48.0);
        double rangeSquared = range * range;

        for (Player worldPlayer : entity.getWorld().getPlayers()) {
            boolean shouldSee = !worldPlayer.isDead()
                    && (worldPlayer.getGameMode() == GameMode.SURVIVAL
                    || worldPlayer.getGameMode() == GameMode.ADVENTURE)
                    && worldPlayer.getLocation().distanceSquared(entity.getLocation()) <= rangeSquared;

            boolean sees = bossBar.getPlayers().contains(worldPlayer);
            if (shouldSee && !sees) {
                bossBar.addPlayer(worldPlayer);
            } else if (!shouldSee && sees) {
                bossBar.removePlayer(worldPlayer);
            }
        }
    }

    private double resolveMaxHealth(LivingEntity entity) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            return attribute.getValue();
        }
        return entity.getMaxHealth();
    }

    private String buildMobBossTitle(LivingEntity entity, String mobId, double maxHealth) {
        String name = resolveBossDisplayName(entity, mobId);
        return String.format("%s (%.0f/%.0f)", name, Math.max(0.0, entity.getHealth()), maxHealth);
    }

    private String resolveBossDisplayName(LivingEntity entity, String mobId) {
        if (entity.customName() != null) {
            String customName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(entity.customName());
            if (!customName.isBlank()) {
                return customName;
            }
        }

        CustomMob mob = plugin.getMobRegistry().get(mobId);
        if (mob != null && mob.getDisplayName() != null && !mob.getDisplayName().isBlank()) {
            return mob.getDisplayName();
        }

        return toFriendlyName(mobId);
    }

    private BarColor getMobBossBarColor(String mobId) {
        return switch (mobId) {
            case "king_slime" -> BarColor.GREEN;
            case "ghast_broodmother" -> BarColor.RED;
            case "poseidon" -> BarColor.BLUE;
            case "crimson_abomination" -> BarColor.RED;
            case "wailing_ghast_duo" -> BarColor.PINK;
            case "ender_angler" -> BarColor.PURPLE;
            case "nether_lord" -> BarColor.YELLOW;
            case "dead_rider" -> BarColor.WHITE;
            case "warden" -> BarColor.BLUE;
            default -> BarColor.RED;
        };
    }

    private String toFriendlyName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }

        StringBuilder builder = new StringBuilder();
        String[] words = raw.toLowerCase().split("_");
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return builder.toString().trim();
    }
}

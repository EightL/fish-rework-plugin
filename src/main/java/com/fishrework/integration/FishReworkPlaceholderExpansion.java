package com.fishrework.integration;

import com.fishrework.FishRework;
import com.fishrework.model.Bait;
import com.fishrework.model.PlayerData;
import com.fishrework.model.PlayerPlaceholderSnapshot;
import com.fishrework.model.Skill;
import com.fishrework.util.FormatUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Optional, internal PlaceholderAPI expansion with identifier {@code fishrework}. */
public final class FishReworkPlaceholderExpansion extends PlaceholderExpansion {

    private static final long OFFLINE_CACHE_TTL_MS = 60_000L;
    private static final int CACHE_CLEANUP_THRESHOLD = 2_048;

    private final FishRework plugin;
    private final Map<UUID, CachedSnapshot> offlineCache = new ConcurrentHashMap<>();
    private final Set<UUID> pendingLoads = ConcurrentHashMap.newKeySet();

    public FishReworkPlaceholderExpansion(FishRework plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "fishrework";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) return "";

        String key = params.toLowerCase(Locale.ROOT);
        Player player = offlinePlayer.getPlayer();
        if (player != null && player.isOnline()) {
            PlayerData data = plugin.getPlayerDataMap().get(player.getUniqueId());
            // Player data may briefly be unavailable during join. Use the same
            // persisted/default view as an offline request so global values
            // (max level, collection totals, provider name) remain accurate.
            return data == null
                    ? resolveOffline(PlayerPlaceholderSnapshot.EMPTY, key)
                    : resolveOnline(player, data, key);
        }

        PlayerPlaceholderSnapshot snapshot = getOfflineSnapshot(offlinePlayer.getUniqueId());
        return resolveOffline(snapshot, key);
    }

    private String resolveOnline(Player player, PlayerData data, String key) {
        int level = data.getLevel(Skill.FISHING);
        double balance = plugin.getEconomyManager().getBalance(player);

        return switch (key) {
            case "fish_caught", "total_fish_caught" -> Long.toString(data.getTotalFishCaught());
            case "treasures_caught", "treasure_catches", "treasure_chest_catches", "total_treasures_caught" ->
                    Long.toString(data.getTotalTreasuresCaught());
            case "fishing_level" -> Integer.toString(level);
            case "fishing_xp" -> decimal(data.getXp(Skill.FISHING), 1);
            case "fishing_xp_required" -> decimal(data.getNextLevelXp(Skill.FISHING, plugin.getLevelManager()), 1);
            case "fishing_xp_remaining" -> decimal(xpRemaining(data.getXp(Skill.FISHING), level), 1);
            case "fishing_xp_percent" -> xpPercent(data.getXp(Skill.FISHING), level);
            case "fishing_max_level" -> Integer.toString(plugin.getLevelManager().getMaxLevel());
            case "balance", "balance_raw", "doubloons" -> Double.toString(balance);
            case "balance_fixed", "doubloons_fixed" -> decimal(balance, 2);
            case "balance_formatted", "doubloons_formatted" -> plugin.getEconomyManager().format(balance);
            case "currency_name" -> plugin.getEconomyManager().getCurrencyName();
            case "lifetime_doubloons" -> Long.toString(data.getLifetimeDoubloons());
            case "encyclopedia_caught" -> Integer.toString(data.getCaughtMobs().size());
            case "encyclopedia_total" -> Integer.toString(encyclopediaTotal());
            case "encyclopedia_remaining" -> Integer.toString(
                    Math.max(0, encyclopediaTotal() - data.getCaughtMobs().size()));
            case "encyclopedia_percent" -> completionPercent(data.getCaughtMobs().size(), encyclopediaTotal());
            case "artifacts_found" -> Integer.toString(data.getCollectedArtifacts().size());
            case "artifacts_total" -> Integer.toString(artifactsTotal());
            case "artifacts_remaining" -> Integer.toString(
                    Math.max(0, artifactsTotal() - data.getCollectedArtifacts().size()));
            case "artifacts_percent" -> completionPercent(data.getCollectedArtifacts().size(), artifactsTotal());
            case "auto_sell_mode" -> data.getSession().getAutoSellMode().getId();
            case "economy_provider" -> plugin.getEconomyManager().getProviderId();
            case "economy_provider_name" -> plugin.getEconomyManager().getProviderDisplayName();
            case "double_catch_bonus" -> decimal(
                    plugin.getLevelManager().getDoubleCatchChance(level)
                            + plugin.getMobManager().getEquipmentDoubleCatchBonus(player), 1);
            case "double_catch_bonus_level" -> decimal(plugin.getLevelManager().getDoubleCatchChance(level), 1);
            case "treasure_bonus" -> decimal(
                    plugin.getMobManager().getTreasureChance(player) + baitBonus(player, Bait.TREASURE_CHANCE), 1);
            case "treasure_bonus_level" -> decimal(plugin.getLevelManager().getTreasureIncrease(level), 1);
            case "rare_creature_bonus" -> decimal(
                    plugin.getMobManager().getEquipmentRareCreatureBonus(player)
                            + baitBonus(player, Bait.RARE_CREATURE_CHANCE), 1);
            case "xp_multiplier" -> decimal(effectiveXpMultiplier(player, level), 2);
            case "xp_multiplier_level" -> decimal(plugin.getLevelManager().getXpMultiplier(level), 2);
            case "fishing_speed" -> Integer.toString(plugin.getMobManager().getEquipmentFishingSpeed(player));
            case "heat" -> decimal(data.getHeat(), 1);
            case "active_bait" -> activeBaitName(player);
            case "session_catches" -> Integer.toString(data.getSession().getTotalCatches());
            case "session_treasures" -> Integer.toString(data.getSession().getTreasuresFound());
            case "session_xp" -> decimal(data.getSession().getXpEarned(), 1);
            case "session_doubloons" -> decimal(data.getSession().getDoubloonsEarned(), 1);
            case "session_mobs_killed" -> Integer.toString(data.getSession().getMobsKilled());
            case "session_levels_gained" -> Integer.toString(data.getSession().getLevelsGained());
            case "session_discoveries" -> Integer.toString(data.getSession().getNewDiscoveries());
            case "session_peak_heat" -> decimal(data.getSession().getPeakHeat(), 1);
            case "session_heat_damage" -> decimal(data.getSession().getHeatDamageTaken(), 1);
            default -> null;
        };
    }

    private String resolveOffline(PlayerPlaceholderSnapshot snapshot, String key) {
        int level = snapshot.fishingLevel();
        return switch (key) {
            case "fish_caught", "total_fish_caught" -> Long.toString(snapshot.totalFishCaught());
            case "treasures_caught", "treasure_catches", "treasure_chest_catches", "total_treasures_caught" ->
                    Long.toString(snapshot.totalTreasuresCaught());
            case "fishing_level" -> Integer.toString(level);
            case "fishing_xp" -> decimal(snapshot.fishingXp(), 1);
            case "fishing_xp_required" -> decimal(requiredXp(level), 1);
            case "fishing_xp_remaining" -> decimal(xpRemaining(snapshot.fishingXp(), level), 1);
            case "fishing_xp_percent" -> xpPercent(snapshot.fishingXp(), level);
            case "fishing_max_level" -> Integer.toString(plugin.getLevelManager().getMaxLevel());
            case "balance", "balance_raw", "doubloons" -> plugin.getEconomyManager().isExternal()
                    ? "" : Double.toString(snapshot.balance());
            case "balance_fixed", "doubloons_fixed" -> plugin.getEconomyManager().isExternal()
                    ? "" : decimal(snapshot.balance(), 2);
            case "balance_formatted", "doubloons_formatted" -> plugin.getEconomyManager().isExternal()
                    ? "" : plugin.getEconomyManager().format(snapshot.balance());
            case "currency_name" -> plugin.getEconomyManager().getCurrencyName();
            case "lifetime_doubloons" -> Long.toString(snapshot.lifetimeDoubloons());
            case "encyclopedia_caught" -> Integer.toString(snapshot.encyclopediaCaught());
            case "encyclopedia_total" -> Integer.toString(encyclopediaTotal());
            case "encyclopedia_remaining" -> Integer.toString(
                    Math.max(0, encyclopediaTotal() - snapshot.encyclopediaCaught()));
            case "encyclopedia_percent" -> completionPercent(snapshot.encyclopediaCaught(), encyclopediaTotal());
            case "artifacts_found" -> Integer.toString(snapshot.artifactsFound());
            case "artifacts_total" -> Integer.toString(artifactsTotal());
            case "artifacts_remaining" -> Integer.toString(Math.max(0, artifactsTotal() - snapshot.artifactsFound()));
            case "artifacts_percent" -> completionPercent(snapshot.artifactsFound(), artifactsTotal());
            case "auto_sell_mode" -> snapshot.autoSellMode().getId();
            case "economy_provider" -> plugin.getEconomyManager().getProviderId();
            case "economy_provider_name" -> plugin.getEconomyManager().getProviderDisplayName();
            case "double_catch_bonus_level" ->
                    decimal(plugin.getLevelManager().getDoubleCatchChance(level), 1);
            case "treasure_bonus_level" ->
                    decimal(plugin.getLevelManager().getTreasureIncrease(level), 1);
            case "xp_multiplier_level" ->
                    decimal(plugin.getLevelManager().getXpMultiplier(level), 2);
            case "double_catch_bonus", "treasure_bonus", "xp_multiplier",
                 "rare_creature_bonus", "fishing_speed", "heat",
                 "active_bait", "session_catches", "session_treasures",
                 "session_xp", "session_doubloons", "session_mobs_killed",
                 "session_levels_gained", "session_discoveries", "session_peak_heat",
                 "session_heat_damage" -> "";
            default -> null;
        };
    }

    private PlayerPlaceholderSnapshot getOfflineSnapshot(UUID uuid) {
        long now = System.currentTimeMillis();
        CachedSnapshot cached = offlineCache.get(uuid);
        if (cached == null || cached.expiresAtMs() <= now) {
            scheduleSnapshotLoad(uuid);
        }
        if (offlineCache.size() > CACHE_CLEANUP_THRESHOLD) {
            offlineCache.entrySet().removeIf(entry -> entry.getValue().expiresAtMs() <= now);
        }
        return cached == null ? PlayerPlaceholderSnapshot.EMPTY : cached.snapshot();
    }

    private void scheduleSnapshotLoad(UUID uuid) {
        if (!pendingLoads.add(uuid)) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerPlaceholderSnapshot snapshot = plugin.getDatabaseManager().loadPlaceholderSnapshot(uuid);
                offlineCache.put(uuid, new CachedSnapshot(
                        snapshot, System.currentTimeMillis() + OFFLINE_CACHE_TTL_MS));
            } finally {
                pendingLoads.remove(uuid);
            }
        });
    }

    private double baitBonus(Player player, String bonusKey) {
        var offhand = player.getInventory().getItemInOffHand();
        if (!plugin.getItemManager().isBait(offhand)) return 0.0;

        boolean lava = player.getWorld().getEnvironment() == World.Environment.NETHER;
        boolean applicable = lava
                ? plugin.getItemManager().isBaitApplicableForLava(offhand)
                : plugin.getItemManager().isBaitApplicableForWater(offhand);
        if (!applicable) return 0.0;

        Bait bait = plugin.getBaitRegistry().get(plugin.getItemManager().getBaitId(offhand));
        return bait == null ? 0.0 : bait.getBonus(bonusKey);
    }

    private String activeBaitName(Player player) {
        var offhand = player.getInventory().getItemInOffHand();
        if (!plugin.getItemManager().isBait(offhand)) return "None";
        Bait bait = plugin.getBaitRegistry().get(plugin.getItemManager().getBaitId(offhand));
        return bait == null ? "Unknown" : bait.getLocalizedDisplayName(plugin.getLanguageManager());
    }

    private double effectiveXpMultiplier(Player player, int level) {
        double levelMultiplier = plugin.getLevelManager().getXpMultiplier(level);
        double globalMultiplier = Math.max(0.0, plugin.getConfig().getDouble("general.xp_multiplier", 1.0));
        double equipmentMultiplier = 1.0 + plugin.getMobManager().getEquipmentFishingXpBonus(player) / 100.0;
        return levelMultiplier * globalMultiplier * equipmentMultiplier;
    }

    private String xpPercent(double xp, int level) {
        double required = requiredXp(level);
        return decimal(required <= 0.0 ? 100.0 : Math.min(100.0, xp / required * 100.0), 1);
    }

    private double xpRemaining(double xp, int level) {
        return Math.max(0.0, requiredXp(level) - xp);
    }

    private int encyclopediaTotal() {
        return plugin.getMobRegistry().getAllIds().size();
    }

    private int artifactsTotal() {
        return plugin.getArtifactRegistry().size();
    }

    private String completionPercent(int completed, int total) {
        return decimal(total <= 0 ? 100.0 : Math.min(100.0, completed * 100.0 / total), 1);
    }

    private double requiredXp(int level) {
        return level >= plugin.getLevelManager().getMaxLevel()
                ? 0.0 : plugin.getLevelManager().getXpForLevel(level + 1);
    }

    private String decimal(double value, int places) {
        return FormatUtil.format("%." + places + "f", value);
    }

    private record CachedSnapshot(PlayerPlaceholderSnapshot snapshot, long expiresAtMs) {
    }
}

package com.fishrework;

import com.fishrework.command.FishingCommand;
import com.fishrework.gui.GuiListener;
import com.fishrework.leveling.LevelManager;
import com.fishrework.listener.*;
import com.fishrework.loader.YamlArtifactLoader;
import com.fishrework.loader.YamlBiomeLoader;
import com.fishrework.loader.YamlItemLoader;
import com.fishrework.loader.YamlMobLoader;
import com.fishrework.manager.AdvancementManager;
import com.fishrework.manager.BossBarManager;
import com.fishrework.manager.ItemManager;
import com.fishrework.manager.LavaCreatureManager;
import com.fishrework.manager.LavaRingManager;
import com.fishrework.manager.RecipeCraftingManager;
import com.fishrework.manager.TotemManager;
import com.fishrework.model.PlayerData;
import com.fishrework.registry.BiomeFishingRegistry;
import com.fishrework.registry.MobRegistry;
import com.fishrework.registry.RecipeRegistry;
import com.fishrework.registry.ArtifactRegistry;
import com.fishrework.skill.SkillManager;
import com.fishrework.storage.DatabaseManager;
import com.fishrework.util.FeatureKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class FishRework extends JavaPlugin {

    private static FishRework instance;

    // Core systems
    private DatabaseManager databaseManager;
    private LevelManager levelManager;
    private SkillManager skillManager;
    private MobManager mobManager;
    private BossBarManager bossBarManager;
    private ItemManager itemManager;
    private AdvancementManager advancementManager;
    private com.fishrework.manager.TreasureManager treasureManager;
    private TotemManager totemManager;
    private com.fishrework.manager.DisplayCaseManager displayCaseManager;
    private com.fishrework.manager.PetManager petManager;
    private com.fishrework.manager.NetheriteRelicManager netheriteRelicManager;
    private com.fishrework.manager.HeatManager heatManager;
    private LavaFishingListener lavaFishingListener;
    private LavaCreatureManager lavaCreatureManager;
    private LavaRingManager lavaRingManager;
    private RecipeCraftingManager recipeCraftingManager;

    // Registries
    private MobRegistry mobRegistry;
    private RecipeRegistry recipeRegistry;
    private BiomeFishingRegistry biomeFishingRegistry;
    private ArtifactRegistry artifactRegistry;
    private com.fishrework.manager.LoreManager loreManager;
    private com.fishrework.registry.BaitRegistry baitRegistry;

    // Player cache
    private final Map<UUID, PlayerData> playerDataMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFishingActivityMs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextFishingTipAtMs = new ConcurrentHashMap<>();
    private List<String> fishingTips = Collections.emptyList();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        extractFishingEnchantDatapack();

        // ── 1. Core systems (order matters) ──
        databaseManager = new DatabaseManager(this);
        levelManager = new LevelManager(this);
        bossBarManager = new BossBarManager(this);
        itemManager = new ItemManager(this);
        advancementManager = new AdvancementManager(this);
        treasureManager = new com.fishrework.manager.TreasureManager(this);
        totemManager = new TotemManager(this);
        displayCaseManager = new com.fishrework.manager.DisplayCaseManager(this);
        petManager = new com.fishrework.manager.PetManager(this);
        netheriteRelicManager = new com.fishrework.manager.NetheriteRelicManager(this);
        lavaCreatureManager = new LavaCreatureManager(this);
        lavaRingManager = new LavaRingManager(this);

        // ── 2. Registries ──
        mobRegistry = new MobRegistry();
        recipeRegistry = new RecipeRegistry(this);
        biomeFishingRegistry = new BiomeFishingRegistry();
        artifactRegistry = new ArtifactRegistry();
        loreManager = new com.fishrework.manager.LoreManager(this);

        // ── 3. Register content (YAML-driven) ──
        new YamlItemLoader(this).load(itemManager);
        itemManager.initJavaItems();
        new YamlMobLoader(this).load(mobRegistry, itemManager, treasureManager);
        new YamlBiomeLoader(this).load(biomeFishingRegistry);
        new YamlArtifactLoader(this).load(artifactRegistry);
        new com.fishrework.loader.YamlRecipeLoader(this).load(itemManager);

        // Load bait definitions and register bait items
        baitRegistry = new com.fishrework.registry.BaitRegistry();
        baitRegistry.loadFromConfig(this);
        for (com.fishrework.model.Bait bait : baitRegistry.getAll()) {
            final com.fishrework.model.Bait b = bait;
            itemManager.getItemRegistry().put(bait.getId(), () -> itemManager.createBaitItem(b));
        }

        // ── 4. Systems that depend on registries ──
        heatManager = new com.fishrework.manager.HeatManager(this);
        mobManager = new MobManager(this);
        skillManager = new SkillManager(this);
        recipeCraftingManager = new RecipeCraftingManager(this);

        // ── 5. Listeners ──
        getServer().getPluginManager().registerEvents(new FishingListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new MobListener(this), this);
        getServer().getPluginManager().registerEvents(new FishBucketListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftingListener(this), this);
        getServer().getPluginManager().registerEvents(new AdvancementListener(this), this);
        getServer().getPluginManager().registerEvents(new EnchantmentListener(this), this);
        getServer().getPluginManager().registerEvents(new HephaesteanTridentListener(this), this);
        getServer().getPluginManager().registerEvents(new FishHookLureListener(this), this);
        getServer().getPluginManager().registerEvents(new LoreUpdateListener(this), this);
        getServer().getPluginManager().registerEvents(new com.fishrework.listener.TreasureListener(this), this);
        getServer().getPluginManager().registerEvents(new com.fishrework.listener.TotemListener(this), this);
        getServer().getPluginManager().registerEvents(new com.fishrework.listener.DisplayCaseListener(this), this);
        getServer().getPluginManager().registerEvents(new com.fishrework.listener.NetheriteRelicListener(this), this);
        getServer().getPluginManager().registerEvents(new FishingJournalListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatBonusListener(this), this);
        getServer().getPluginManager().registerEvents(new PiglinCrownListener(this), this);
        getServer().getPluginManager().registerEvents(new WeaponAoEListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockPlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new com.fishrework.listener.FishBagListener(this), this);
        getServer().getPluginManager().registerEvents(new com.fishrework.listener.LavaBagListener(this), this);
        getServer().getPluginManager().registerEvents(new com.fishrework.listener.CoolingItemListener(this), this);
        lavaFishingListener = new LavaFishingListener(this);
        getServer().getPluginManager().registerEvents(lavaFishingListener, this);
        getServer().getPluginManager().registerEvents(new AnvilProtectionListener(this), this);

        lavaRingManager.start();

        // ── 5b. Start TotemManager tick task ──
        totemManager.start();

        // ── 5c. Start HeatManager passive decay task ──
        getServer().getScheduler().runTaskTimer(this, () -> heatManager.processPassiveDecay(), 20L, 20L); // check every second

        // ── 5d. Start AggroTask — re-targets hostile fished mobs 4x per second ──
        getServer().getScheduler().runTaskTimer(this, new com.fishrework.task.AggroTask(this), 5L, 5L);

        // ── 5e. Start BossAbilityTask — executes YAML-defined mob abilities every second ──
        getServer().getScheduler().runTaskTimer(this, new com.fishrework.task.BossAbilityTask(this), 20L, 20L);

        // ── 6. Advancements ──
        getServer().getScheduler().runTask(this, () -> advancementManager.loadAdvancements());

        // ── 7. Commands ──
        getCommand("fishing").setExecutor(new FishingCommand(this));

        // ── 7b. Fishing tip notifications ──
        loadFishingTipsFromConfig();
        startFishingTipsTask();

        // ── 8. Load online players (reload safety) ──
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                PlayerData data = databaseManager.loadPlayer(player.getUniqueId());
                getServer().getScheduler().runTask(this, () -> {
                    playerDataMap.put(player.getUniqueId(), data);
                });
            });
        }

        // ── 9. Plugin conflict detection ──
        if (getServer().getPluginManager().getPlugin("AuraSkills") != null) {
            getLogger().warning("═══════════════════════════════════════════════════════");
            getLogger().warning(" AuraSkills detected! Its Fishing skill may conflict");
            getLogger().warning(" with Fish Rework. Please disable AuraSkills'");
            getLogger().warning(" Fishing skill in its config to avoid issues.");
            getLogger().warning(" (skills.yml → fishing → enabled: false)");
            getLogger().warning("═══════════════════════════════════════════════════════");
        }

        getLogger().info("[Fish Rework] Enabled v" + getDescription().getVersion() + "!");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        if (totemManager != null) totemManager.stop();
        if (lavaRingManager != null) lavaRingManager.shutdown();
        if (lavaCreatureManager != null) lavaCreatureManager.shutdown();
        for (PlayerData data : playerDataMap.values()) {
            databaseManager.savePlayer(data);
        }
        if (advancementManager != null) advancementManager.unloadAdvancements();
        if (databaseManager != null) databaseManager.close();
        getLogger().info("[Fish Rework] Disabled.");
    }
    private void extractFishingEnchantDatapack() {
        // Find the overworld by environment type rather than assuming index 0
        org.bukkit.World w = null;
        for (org.bukkit.World world : getServer().getWorlds()) {
            if (world.getEnvironment() == org.bukkit.World.Environment.NORMAL) {
                w = world;
                break;
            }
        }
        if (w == null) {
            getLogger().warning("[Fish Rework] Could not find overworld — datapack extraction skipped.");
            return;
        }
        List<String> paths = List.of(
                "pack.mcmeta",
                "data/minecraft/enchantment/lure.json",
                "data/minecraft/enchantment/luck_of_the_sea.json",
            "data/fishrework/enchantment/sea_creature_chance.json",
            "data/fishrework/enchantment/shotgun_volley.json");

        List<String> datapackNames = List.of("fishrework_fishing", "hybesskills_fishing");
        for (String datapackName : datapackNames) {
            Path datapacksDir = w.getWorldFolder().toPath().resolve("datapacks").resolve(datapackName);
            for (String p : paths) {
                try (InputStream in = getResource("pack/" + p)) {
                    if (in == null) continue;
                    Path out = datapacksDir.resolve(p);
                    Files.createDirectories(out.getParent());
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    getLogger().warning("Failed to extract datapack file: " + datapackName + "/" + p + " — " + e.getMessage());
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Feature Toggles
    // ══════════════════════════════════════════════════════════

    /**
     * Checks whether a feature is enabled in config.yml under features.&lt;key&gt;.
     * Enforces auto-dependency rules:
     *  - economy_enabled: false  → shop_enabled returns false
     *  - fish_bag_enabled: false → lava_bag (handled in listener) returns false
     */
    public boolean isFeatureEnabled(String featureKey) {
        boolean value = getConfig().getBoolean("features." + featureKey, true);
        if (!value) return false;

        // Dependency rules
        if (featureKey.equals(FeatureKeys.SHOP_ENABLED)
            && !getConfig().getBoolean("features." + FeatureKeys.ECONOMY_ENABLED, true)) {
            return false;
        }
        if (featureKey.equals(FeatureKeys.LAVA_BAG)
            && !getConfig().getBoolean("features." + FeatureKeys.FISH_BAG_ENABLED, true)) {
            return false;
        }
        if (featureKey.equals(FeatureKeys.HEAT_SYSTEM_ENABLED)
            && !getConfig().getBoolean("features." + FeatureKeys.LAVA_FISHING_ENABLED, true)) {
            return false;
        }

        return true;
    }

    /**
     * Reloads the plugin configuration without a full server restart.
     * Reloads config.yml values; does not reinitialize managers or registries.
     */
    public void reload() {
        reloadConfig();
        loadFishingTipsFromConfig();
        getLogger().info("[Fish Rework] Configuration reloaded.");
    }

    // ══════════════════════════════════════════════════════════
    //  Accessors
    // ══════════════════════════════════════════════════════════

    public static FishRework getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public LevelManager getLevelManager() { return levelManager; }
    public SkillManager getSkillManager() { return skillManager; }
    public MobManager getMobManager() { return mobManager; }
    public BossBarManager getBossBarManager() { return bossBarManager; }
    public ItemManager getItemManager() { return itemManager; }
    public AdvancementManager getAdvancementManager() { return advancementManager; }
    public com.fishrework.manager.TreasureManager getTreasureManager() { return treasureManager; }
    public TotemManager getTotemManager() { return totemManager; }
    public com.fishrework.manager.DisplayCaseManager getDisplayCaseManager() { return displayCaseManager; }
    public com.fishrework.manager.PetManager getPetManager() { return petManager; }
    public MobRegistry getMobRegistry() { return mobRegistry; }
    public RecipeRegistry getRecipeRegistry() { return recipeRegistry; }
    public BiomeFishingRegistry getBiomeFishingRegistry() { return biomeFishingRegistry; }
    public ArtifactRegistry getArtifactRegistry() { return artifactRegistry; }
    public com.fishrework.manager.LoreManager getLoreManager() { return loreManager; }
    public Map<UUID, PlayerData> getPlayerDataMap() { return playerDataMap; }

    public PlayerData getPlayerData(UUID uuid) {
        return playerDataMap.get(uuid);
    }

    public com.fishrework.manager.NetheriteRelicManager getNetheriteRelicManager() {
        return netheriteRelicManager;
    }

    public com.fishrework.registry.BaitRegistry getBaitRegistry() {
        return baitRegistry;
    }

    public RecipeCraftingManager getRecipeCraftingManager() {
        return recipeCraftingManager;
    }

    public com.fishrework.manager.HeatManager getHeatManager() {
        return heatManager;
    }

    public LavaFishingListener getLavaFishingListener() {
        return lavaFishingListener;
    }

    public LavaCreatureManager getLavaCreatureManager() {
        return lavaCreatureManager;
    }

    public void markFishingActivity(org.bukkit.entity.Player player) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        lastFishingActivityMs.put(uuid, now);
        nextFishingTipAtMs.computeIfAbsent(uuid, ignored -> now + randomTipDelayMs());
    }

    public void clearFishingTipTracking(UUID uuid) {
        if (uuid == null) return;
        lastFishingActivityMs.remove(uuid);
        nextFishingTipAtMs.remove(uuid);
    }

    private void loadFishingTipsFromConfig() {
        List<String> configuredTips = getConfig().getStringList("fishing_tips.tips");
        if (configuredTips == null || configuredTips.isEmpty()) {
            this.fishingTips = defaultFishingTips();
            return;
        }

        List<String> sanitized = new ArrayList<>();
        for (String tip : configuredTips) {
            if (tip == null) continue;
            String trimmed = tip.trim();
            if (!trimmed.isEmpty()) sanitized.add(trimmed);
        }
        this.fishingTips = sanitized.isEmpty() ? defaultFishingTips() : List.copyOf(sanitized);
    }

    private List<String> defaultFishingTips() {
        return List.of(
                "Use /fish or /fishing to open your fishing progression GUI.",
                "Use /fish help to list all available fishing commands.",
                "Visit the wiki for recipes, exact sea creature info, and custom item details.",
                "Struggling against sea creatures? Craft custom gear and check the wiki for builds.",
                "Use /fish bag to store catches and keep your inventory clean.",
                "Use /fish autosell to auto-sell common fish while you grind.",
                "Use /fish notifications to toggle these tips anytime."
        );
    }

    private void startFishingTipsTask() {
        if (!getConfig().getBoolean("fishing_tips.enabled", true)) return;

        long periodTicks = Math.max(20L, getConfig().getLong("fishing_tips.check_interval_seconds", 30L) * 20L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();
            long activeWindowMs = Math.max(60_000L, getConfig().getLong("fishing_tips.active_window_seconds", 600L) * 1000L);

            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                PlayerData data = getPlayerData(player.getUniqueId());
                if (data == null || !data.isFishingTipsEnabled()) continue;

                Long lastActivity = lastFishingActivityMs.get(player.getUniqueId());
                if (lastActivity == null || now - lastActivity > activeWindowMs) continue;

                long nextAt = nextFishingTipAtMs.computeIfAbsent(player.getUniqueId(), ignored -> now + randomTipDelayMs());
                if (now < nextAt) continue;

                String tip = pickRandomTip();
                if (tip != null && !tip.isBlank()) {
                    String wikiUrl = getConfig().getString("fishing_tips.wiki_url", "");
                    String renderedTip = tip.replace("{wiki}", wikiUrl == null ? "" : wikiUrl);
                    player.sendMessage(Component.text("[Fishing Tip] ").color(NamedTextColor.AQUA)
                            .append(Component.text(renderedTip).color(NamedTextColor.GRAY)));
                }

                nextFishingTipAtMs.put(player.getUniqueId(), now + randomTipDelayMs());
            }
        }, periodTicks, periodTicks);
    }

    private String pickRandomTip() {
        if (fishingTips == null || fishingTips.isEmpty()) return null;
        int index = ThreadLocalRandom.current().nextInt(fishingTips.size());
        return fishingTips.get(index);
    }

    private long randomTipDelayMs() {
        long minSec = Math.max(30L, getConfig().getLong("fishing_tips.min_delay_seconds", 180L));
        long maxSec = Math.max(minSec, getConfig().getLong("fishing_tips.max_delay_seconds", 420L));
        long delaySec = ThreadLocalRandom.current().nextLong(minSec, maxSec + 1L);
        return delaySec * 1000L;
    }
}

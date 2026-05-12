package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.gui.FishBagGUI;
import com.fishrework.model.FishingSession;
import com.fishrework.model.LavaBobberState;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Rarity;
import com.fishrework.model.SeaCreatureWeightProfile;
import com.fishrework.model.SeaCreatureMessageMode;
import com.fishrework.model.Skill;
import com.fishrework.task.LavaBobberTask;
import com.fishrework.util.BagUtils;
import com.fishrework.util.FeatureKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import com.fishrework.util.FishingUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles lava fishing — the custom bobber system for fishing in Nether lava.
 * <p>
 * Since vanilla FishHook burns in lava and CAUGHT_FISH never fires, this listener
 * implements custom bite detection through {@link LavaBobberTask} and handles
 * the reel-in catch processing by reusing existing {@code MobManager} logic.
 */
public class LavaFishingListener implements Listener {

    private static final int LAVA_FISHING_REQUIRED_LEVEL = 27;

    private final FishRework plugin;

    /** Active lava fishing sessions keyed by player UUID. */
    private final Map<UUID, LavaBobberState> activeSessions = new ConcurrentHashMap<>();

    // Timing config (loaded once from config.yml)
    private final int minBiteTicks;
    private final int maxBiteTicks;
    private final int reelWindowTicks;
    private final int baseCastTicks;
    private final int lureTickReduction;
    private final double fishingSpeedDivisor;
    private final NamespacedKey emberVolleyAllowUntilKey;

    public LavaFishingListener(FishRework plugin) {
        this.plugin = plugin;
        this.minBiteTicks = plugin.getConfig().getInt("lava_fishing.min_bite_ticks", 100);
        this.maxBiteTicks = plugin.getConfig().getInt("lava_fishing.max_bite_ticks", 400);
        this.reelWindowTicks = plugin.getConfig().getInt("lava_fishing.reel_window_ticks", 40);
        this.baseCastTicks = plugin.getConfig().getInt("lava_fishing.base_cast_ticks", 400);
        this.lureTickReduction = plugin.getConfig().getInt("lava_fishing.lure_tick_reduction", 10);
        this.fishingSpeedDivisor = plugin.getConfig().getDouble("lava_fishing.fishing_speed_divisor", 100.0);
        this.emberVolleyAllowUntilKey = new NamespacedKey(plugin, "ember_volley_allow_until");
    }

    // ══════════════════════════════════════════════════════════
    //  Event: Bobber Launch
    // ══════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Fireball fireball
                && fireball.getShooter() instanceof org.bukkit.entity.Ghast ghast
                && plugin.getMobManager().isFishedMob(ghast)
                && ("ghast_broodmother".equals(plugin.getMobManager().getMobId(ghast))
                || "wailing_ghast_duo".equals(plugin.getMobManager().getMobId(ghast)))) {
            event.setCancelled(true);
            return;
        }

        if (event.getEntity() instanceof SmallFireball fireball
                && fireball.getShooter() instanceof org.bukkit.entity.Blaze blaze
                && plugin.getMobManager().isFishedMob(blaze)
                && "blaze_fisher".equals(plugin.getMobManager().getMobId(blaze))) {
            Long allowUntil = blaze.getPersistentDataContainer().get(emberVolleyAllowUntilKey, PersistentDataType.LONG);
            long now = System.currentTimeMillis();
            if (allowUntil == null || now > allowUntil) {
                event.setCancelled(true);
            }
            return;
        }

        if (!(event.getEntity() instanceof FishHook hook)) return;
        if (!(hook.getShooter() instanceof Player player)) return;

        plugin.getLanguageManager().withPlayer(player, () -> handleProjectileLaunch(event, hook, player));
    }

    private void handleProjectileLaunch(ProjectileLaunchEvent event, FishHook hook, Player player) {

        // Only in Nether
        if (player.getWorld().getEnvironment() != World.Environment.NETHER) return;

        // Feature toggle check
        if (!plugin.isFeatureEnabled(FeatureKeys.LAVA_FISHING_ENABLED)) {
            return;
        }

        // Must be holding a lava rod
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (!isLavaRod(rod)) return;

        plugin.markFishingActivity(player);

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        int fishingLevel = data != null ? data.getLevel(Skill.FISHING) : 0;
        if (fishingLevel < LAVA_FISHING_REQUIRED_LEVEL) {
            event.setCancelled(true);
            player.sendMessage(Component.text(plugin.getLanguageManager().getString(
                            "lavafishinglistener.required_level",
                            "You need Fishing level %level% to lava fish.",
                            "level", String.valueOf(LAVA_FISHING_REQUIRED_LEVEL)))
                .color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
            return;
        }

        // Cancel any existing session
        if (activeSessions.containsKey(player.getUniqueId())) {
            removeSession(player.getUniqueId());
        }

        // Make hook invulnerable so it doesn't burn
        hook.setInvulnerable(true);

        // Set vanilla wait time to MAX_VALUE to prevent vanilla catch interference
        hook.setWaitTime(Integer.MAX_VALUE, Integer.MAX_VALUE);

        // Hypixel-like timer model:
        // ticks = rand(base, base - lureReduction * lureLevel) * fishingSpeedMultiplier
        int lureLevel = rod.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LURE);

        // Reuse the shared fishing-speed calculation so lava fishing respects the same custom stat source.
        int totalFishingSpeed = plugin.getMobManager().getEquipmentFishingSpeed(player);
        int lureSpeedBonus = Math.max(0, lureLevel)
                * plugin.getConfig().getInt("enchantments.lure_fishing_speed_per_level", 5);
        double rodFishingSpeed = Math.max(0.0, totalFishingSpeed - lureSpeedBonus);

        int lureCap = plugin.getConfig().getInt("enchantments.lure_cap", 3);
        int customTicksPerLure = plugin.getConfig().getInt("enchantments.ticks_per_lure_level", 100);
        int customLureLevels = Math.max(0, lureLevel - Math.max(0, lureCap));
        int customLureReduction = customLureLevels * Math.max(0, customTicksPerLure);

        int baseUpper = Math.max(40, baseCastTicks - customLureReduction);
        int baseLower = Math.max(20, baseUpper - (lureTickReduction * Math.max(0, lureLevel)));

        double safeDivisor = Math.max(1.0, fishingSpeedDivisor);
        double fishingSpeedMultiplier = safeDivisor / (safeDivisor + Math.max(0.0, rodFishingSpeed));

        int adjustedMin = Math.max(20, (int) Math.round(baseLower * fishingSpeedMultiplier));
        int adjustedMax = Math.max(adjustedMin, (int) Math.round(baseUpper * fishingSpeedMultiplier));

        // Fallback to old config if a server owner configures impossible values
        if (adjustedMax <= 0 || adjustedMin <= 0) {
            adjustedMin = minBiteTicks;
            adjustedMax = maxBiteTicks;
        }

        // Create state and start task
        LavaBobberState state = new LavaBobberState(hook, adjustedMin, adjustedMax, reelWindowTicks);
        activeSessions.put(player.getUniqueId(), state);

        LavaBobberTask task = new LavaBobberTask(plugin, player.getUniqueId(), state);
        task.runTaskTimer(plugin, 1L, 1L);  // Every tick

        // Feedback
        player.sendActionBar(plugin.getLanguageManager().getMessage("lavafishinglistener.lava_fishing_cast_into_lava", "🔥 Lava fishing! Cast into lava...")
                .color(NamedTextColor.GOLD));
    }

    // ══════════════════════════════════════════════════════════
    //  Event: Reel In
    // ══════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        plugin.getLanguageManager().withPlayer(player, () -> handleFish(event, player));
    }

    private void handleFish(PlayerFishEvent event, Player player) {
        UUID uuid = player.getUniqueId();

        if (!activeSessions.containsKey(uuid)) return;

        plugin.markFishingActivity(player);

        LavaBobberState state = activeSessions.get(uuid);

        // REEL_IN or FAILED_ATTEMPT — handle catch or cleanup
        if (event.getState() == PlayerFishEvent.State.REEL_IN
                || event.getState() == PlayerFishEvent.State.FAILED_ATTEMPT) {

            if (state.hasCatch()) {
                // Successful reel during bite window!
                processLavaCatch(player, state);
            }

            // Always clean up the session on reel
            removeSession(uuid);

            // Cancel the vanilla event to prevent default drop
            event.setCancelled(true);
            return;
        }

        // If they cast again while session active (FISHING state), we already
        // handled re-initialization in onProjectileLaunch, but also cancel
        // vanilla for safety
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            // Let the ProjectileLaunchEvent handle it
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Catch Processing (mirrors FishingListener.onFish CAUGHT_FISH)
    // ══════════════════════════════════════════════════════════

    private void processLavaCatch(Player player, LavaBobberState state) {
        plugin.markFishingActivity(player);

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;
        FishingSession session = data.getSession();

        Location hookLoc = state.getHook().getLocation();
        LavaBaitContext baitContext = resolveLavaBaitContext(player);
        double heatSccBonus = plugin.getHeatManager().getHeatSccBonus(player);
        String mobId = resolveLavaMobId(player, hookLoc, baitContext, heatSccBonus);

        handleLavaCatchOutcome(player, hookLoc, data, session, baitContext, mobId);

        grantLavaCatchXp(player, baitContext.xpMultiplier);
        consumeLavaRodDurability(player);
        playLavaCatchFeedback(player, hookLoc);
        applyLavaHeat(player, mobId);
    }

    private LavaBaitContext resolveLavaBaitContext(Player player) {
        com.fishrework.model.Bait activeBait = null;
        ItemStack offhand = player.getInventory().getItemInOffHand();
        List<String> baitTargetMobIds = Collections.emptyList();
        Set<com.fishrework.model.BiomeGroup> baitNativeBiomeGroups = Collections.emptySet();
        if (plugin.isFeatureEnabled(FeatureKeys.BAIT_SYSTEM_ENABLED)
            && plugin.getItemManager().isBait(offhand)
            && plugin.getItemManager().isBaitApplicableForLava(offhand)) {
            baitTargetMobIds = plugin.getItemManager().getBaitTargetMobIds(offhand);
            baitNativeBiomeGroups = plugin.getItemManager().getBaitNativeBiomeGroups(offhand);
            String baitId = plugin.getItemManager().getBaitId(offhand);
            if (baitId != null) {
                activeBait = plugin.getBaitRegistry().get(baitId);
                consumeOneOffhandBait(player, offhand);
            }
        }

        double rareCreatureBonus = activeBait != null
                ? activeBait.getBonus(com.fishrework.model.Bait.RARE_CREATURE_CHANCE)
                : 0;
        double treasureBonus = activeBait != null
                ? activeBait.getBonus(com.fishrework.model.Bait.TREASURE_CHANCE)
                : 0;
        double xpMultiplier = activeBait != null
                ? activeBait.getBonus(com.fishrework.model.Bait.XP_MULTIPLIER)
                : 0;

        return new LavaBaitContext(baitTargetMobIds, baitNativeBiomeGroups, rareCreatureBonus, treasureBonus, xpMultiplier);
    }

    private String resolveLavaMobId(Player player,
                                    Location hookLoc,
                                    LavaBaitContext baitContext,
                                    double heatSccBonus) {
        String mobId = plugin.getMobManager().getMobToSpawn(
                player,
                Skill.FISHING,
                hookLoc,
                baitContext.rareCreatureBonus + heatSccBonus,
                baitContext.treasureBonus,
                baitContext.targetMobIds,
                baitContext.nativeBiomeGroups
        );

        if (mobId == null) {
            return null;
        }

        com.fishrework.model.CustomMob mob = plugin.getMobRegistry().get(mobId);
        if (mob != null && mob.isTreasure() && !plugin.isFeatureEnabled(FeatureKeys.TREASURE_CHESTS_ENABLED)) {
            return null;
        }

        return mobId;
    }

    private void handleLavaCatchOutcome(Player player,
                                        Location hookLoc,
                                        PlayerData data,
                                        FishingSession session,
                                        LavaBaitContext baitContext,
                                        String mobId) {
        if (mobId == null) {
            session.recordCatch();
            player.sendActionBar(plugin.getLanguageManager().getMessage("lavafishinglistener.you_pulled_up_some_lava", "You pulled up some lava debris...")
                    .color(NamedTextColor.GRAY));
            return;
        }

        com.fishrework.model.CustomMob mobDef = plugin.getMobRegistry().get(mobId);
        if (mobDef == null) {
            session.recordCatch();
            return;
        }

        if (mobDef.isTreasure()) {
            ItemStack treasureItem = null;
            if (!mobDef.getDrops().isEmpty()) {
                treasureItem = mobDef.getDrops().get(0).roll();
            }
            if (treasureItem == null) {
                treasureItem = plugin.getTreasureManager().getRandomTreasure();
            }

            if (BagUtils.hasFishBagInInventory(plugin, player)) {
                ItemStack overflow = FishBagGUI.addToBag(data, treasureItem);
                plugin.getDatabaseManager().saveFishBag(player.getUniqueId(), data.getFishBagContents());

                if (overflow != null) {
                    Item itemEntity = hookLoc.getWorld().dropItemNaturally(hookLoc, overflow);
                    itemEntity.setInvulnerable(true);
                } else {
                    player.sendActionBar(plugin.getLanguageManager().getMessage("lavafishinglistener.treasure_sent_to_fish_bag", "Treasure sent to Fish Bag!").color(NamedTextColor.GOLD));
                }
            } else {
                Item itemEntity = hookLoc.getWorld().dropItemNaturally(hookLoc, treasureItem);
                itemEntity.setInvulnerable(true);
            }

            player.sendMessage(Component.text(plugin.getLanguageManager().getString(
                            "lavafishinglistener.you_pulled_up",
                            "You pulled up a %mob%!",
                            "mob", mobDef.getLocalizedDisplayName(plugin.getLanguageManager())))
                    .color(mobDef.getRarity().getColor()));

            plugin.getMobManager().registerCatch(player, mobId, 0.0, mobDef, baitContext.xpMultiplier);

            session.recordCatch();
            session.recordTreasure();
            FishingUtils.playCatchEffects(player, mobDef.getRarity(), hookLoc);
            FishingUtils.broadcastRareCatch(plugin, player, mobDef.getLocalizedDisplayName(plugin.getLanguageManager()), mobDef.getRarity(), true);
            return;
        }

        if (!plugin.getMobManager().shouldSpawnLiveCatch(mobDef)) {
            SeaCreatureWeightProfile weightProfile = plugin.getMobManager().rollWeightProfile(mobDef);
            plugin.getMobManager().dropMobLoot(
                    player,
                    hookLoc,
                    mobDef,
                    false,
                    weightProfile.getDropRollMultiplier(),
                    true
            );
            plugin.getMobManager().registerCatch(player, mobId, weightProfile.getWeightKg(), mobDef, baitContext.xpMultiplier);
            session.recordCatch();
            player.sendActionBar(plugin.getLanguageManager().getMessage(
                    "lavafishinglistener.you_pulled_up_some_lava",
                    "You pulled up some lava debris...").color(NamedTextColor.GRAY));
            maybeSendLavaSeaCreatureMessage(player, data, mobDef, weightProfile);
            if (mobDef.getRarity() != null && mobDef.getRarity().ordinal() >= Rarity.RARE.ordinal()) {
                FishingUtils.playCatchEffects(player, mobDef.getRarity(), hookLoc);
                FishingUtils.broadcastRareCatch(plugin, player, mobDef.getLocalizedDisplayName(plugin.getLanguageManager()), mobDef.getRarity(), true);
            }
            return;
        }

        SeaCreatureWeightProfile weightProfile = plugin.getMobManager().spawnMob(hookLoc, mobId, player, baitContext.xpMultiplier);

        maybeSendLavaSeaCreatureMessage(player, data, mobDef, weightProfile);

        session.recordCatch();

        if (mobDef.getRarity() != null && mobDef.getRarity().ordinal() >= Rarity.RARE.ordinal()) {
            FishingUtils.playCatchEffects(player, mobDef.getRarity(), hookLoc);
            FishingUtils.broadcastRareCatch(plugin, player, mobDef.getLocalizedDisplayName(plugin.getLanguageManager()), mobDef.getRarity(), true);
        }
    }

    private void maybeSendLavaSeaCreatureMessage(Player player, PlayerData data, com.fishrework.model.CustomMob mobDef,
                                                 SeaCreatureWeightProfile weightProfile) {
        SeaCreatureMessageMode mode = data == null ? SeaCreatureMessageMode.ALL : data.getSeaCreatureMessageMode();
        if (mode == SeaCreatureMessageMode.NONE) {
            return;
        }
        if (mode == SeaCreatureMessageMode.RARE_ONLY
                && (mobDef.getRarity() == null || mobDef.getRarity().ordinal() < Rarity.RARE.ordinal())) {
            return;
        }
        String mobName = mobDef.getLocalizedDisplayName(plugin.getLanguageManager());
        TextColor rarityColor = mobDef.getRarity() != null ? mobDef.getRarity().getColor() : NamedTextColor.GOLD;
        double weightKg = weightProfile != null ? weightProfile.getWeightKg() : 0.0;
        Rarity weightRarity = plugin.getMobManager().getWeightRarity(weightProfile);
        String template = plugin.getLanguageManager().getString(
                "lavafishinglistener.you_hooked_sea_creature",
                "You hooked a %mob% (%weight%)!");
        player.sendMessage(FishingUtils.buildHookedMessage(
                template,
                mobName,
                rarityColor,
                com.fishrework.util.FormatUtil.format("%.2fkg", weightKg),
                weightRarity.getColor(),
                NamedTextColor.GOLD
        ));
    }

    private void grantLavaCatchXp(Player player, double baitXpMultiplier) {
        double baseXp = plugin.getConfig().getDouble("fishing.xp.lava_catch", 30.0);
        if (baitXpMultiplier > 0) {
            baseXp *= (1.0 + baitXpMultiplier / 100.0);
        }
        plugin.getSkillManager().grantXp(player, Skill.FISHING, baseXp, Skill.FISHING.getLocalizedDisplayName(plugin.getLanguageManager()));
    }

    private void consumeLavaRodDurability(Player player) {
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod.getType() == Material.FISHING_ROD && rod.getItemMeta() != null) {
            org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) rod.getItemMeta();
            damageable.setDamage(damageable.getDamage() + 1);
            rod.setItemMeta(damageable);
        }
    }

    private void playLavaCatchFeedback(Player player, Location hookLoc) {
        hookLoc.getWorld().spawnParticle(Particle.LAVA, hookLoc, 20, 0.5, 0.5, 0.5);
        Sound lavaExtinguish = FishingUtils.getLavaExtinguishSound();
        if (lavaExtinguish != null) {
            player.playSound(hookLoc, lavaExtinguish, 0.8f, 1.0f);
        }
    }

    private void applyLavaHeat(Player player, String mobId) {
        if (!plugin.isFeatureEnabled(FeatureKeys.HEAT_SYSTEM_ENABLED)) {
            return;
        }

        double heatGained = plugin.getConfig().getDouble("heat.gain_per_catch", 3.0);
        if (mobId != null) {
            if (plugin.getMobManager().isHostile(mobId)) {
                heatGained += plugin.getConfig().getDouble("heat.gain_per_hostile", 2.0);
            }

            com.fishrework.model.CustomMob mobDef = plugin.getMobRegistry().get(mobId);
            if (mobDef != null && mobDef.getRarity() == Rarity.LEGENDARY) {
                heatGained += plugin.getConfig().getDouble("heat.gain_per_legendary", 5.0);
            }
        }
        plugin.getHeatManager().addHeat(player, heatGained);
    }

    private static final class LavaBaitContext {
        private final List<String> targetMobIds;
        private final Set<com.fishrework.model.BiomeGroup> nativeBiomeGroups;
        private final double rareCreatureBonus;
        private final double treasureBonus;
        private final double xpMultiplier;

        private LavaBaitContext(List<String> targetMobIds,
                                Set<com.fishrework.model.BiomeGroup> nativeBiomeGroups,
                                double rareCreatureBonus,
                                double treasureBonus,
                                double xpMultiplier) {
            this.targetMobIds = targetMobIds;
            this.nativeBiomeGroups = nativeBiomeGroups;
            this.rareCreatureBonus = rareCreatureBonus;
            this.treasureBonus = treasureBonus;
            this.xpMultiplier = xpMultiplier;
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Public API
    // ══════════════════════════════════════════════════════════

    /** Returns true if the player currently has an active lava fishing session. */
    public boolean isActiveLavaFishing(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    /** Removes a session from the active map (called by LavaBobberTask on cleanup). */
    public void removeSession(UUID playerId) {
        LavaBobberState state = activeSessions.remove(playerId);
        if (state != null && state.getHook() != null && state.getHook().isValid()) {
            state.getHook().remove(); // Remove the vanilla hook entity
        }

    }

    private void consumeOneOffhandBait(Player player, ItemStack offhand) {
        if (offhand == null || offhand.getType().isAir()) return;

        if (offhand.getAmount() <= 1) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        } else {
            offhand.setAmount(offhand.getAmount() - 1);
        }
    }

    /** Get the active session for a player, or null. */
    public LavaBobberState getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }

    // ══════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════

    /** Checks if the given item is a lava fishing rod via PDC. */
    private boolean isLavaRod(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(plugin.getItemManager().LAVA_ROD_KEY, PersistentDataType.BYTE)
                || pdc.has(plugin.getItemManager().LAVA_ROD_KEY, PersistentDataType.DOUBLE)
                || pdc.has(plugin.getItemManager().LAVA_ROD_KEY, PersistentDataType.INTEGER);
    }

}

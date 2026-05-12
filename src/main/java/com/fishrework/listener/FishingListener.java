package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.gui.FishBagGUI;
import com.fishrework.model.AutoSellMode;
import com.fishrework.model.FishingSession;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Rarity;
import com.fishrework.model.SeaCreatureWeightProfile;
import com.fishrework.model.SeaCreatureMessageMode;
import com.fishrework.model.Skill;
import com.fishrework.util.AutoSellUtil;
import com.fishrework.util.BagUtils;
import com.fishrework.util.FeatureKeys;
import com.fishrework.util.FishingUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Random;
import java.util.Set;

public class FishingListener implements Listener {

    private final FishRework plugin;
    private final Random random = new Random();

    public FishingListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        plugin.getLanguageManager().withPlayer(event.getPlayer(), () -> handleFish(event));
    }

    private void handleFish(PlayerFishEvent event) {
        PlayerFishEvent.State state = event.getState();
        if (state == PlayerFishEvent.State.FISHING
                || state == PlayerFishEvent.State.CAUGHT_FISH
                || state == PlayerFishEvent.State.REEL_IN
                || state == PlayerFishEvent.State.FAILED_ATTEMPT) {
            plugin.markFishingActivity(event.getPlayer());
        }

        // ── Lava fishing: handled by LavaFishingListener ──
        if (plugin.getLavaFishingListener() != null
                && plugin.getLavaFishingListener().isActiveLavaFishing(event.getPlayer())) {
            return;
        }

        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item)) return;

        Player player = event.getPlayer();
        Item caughtItem = (Item) event.getCaught();
        ItemStack itemStack = caughtItem.getItemStack();
        Material type = itemStack.getType();

        double baseXp = getXpForFish(type);
        if (baseXp <= 0) return;

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;
        FishingSession session = data.getSession();

        BaitContext baitContext = resolveBaitContext(player);

        int currentLevel = data.getLevel(Skill.FISHING);
        double doubleCatchChance = resolveDoubleCatchChance(player, currentLevel, baitContext.doubleCatchBonus);

        String mobId = resolveMobId(player, caughtItem, baitContext);
        if (mobId != null) {
            MobCatchResult mobCatchResult = handleCustomMobCatch(
                    player,
                    caughtItem,
                    itemStack,
                    data,
                    session,
                    mobId,
                    currentLevel,
                    doubleCatchChance,
                    baitContext.xpMultiplier
            );
            if (mobCatchResult.stopProcessing) {
                return;
            }
            itemStack = mobCatchResult.resultingItemStack;
            type = itemStack.getType();
        }

        if (mobId == null) {
            session.recordCatch();
        }

        if (tryAutoSellCatch(player, caughtItem, itemStack, mobId, data, session, baseXp,
                baitContext.xpMultiplier)) {
            return;
        }

        maybeStoreCatchInFishBag(player, caughtItem, itemStack, mobId, data);

        tryDoubleCatchDrop(player, itemStack, doubleCatchChance);

        double finalBaseXp = applyXpMultipliers(baseXp, baitContext.xpMultiplier);
        plugin.getSkillManager().grantXp(player, Skill.FISHING, finalBaseXp, Skill.FISHING.getLocalizedDisplayName(plugin.getLanguageManager()));
    }

    private BaitContext resolveBaitContext(Player player) {
        com.fishrework.model.Bait activeBait = null;
        List<com.fishrework.model.BiomeGroup> baitNativeBiomeGroups = java.util.Collections.emptyList();
        List<String> baitTargetMobIds = java.util.Collections.emptyList();

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (plugin.isFeatureEnabled(FeatureKeys.BAIT_SYSTEM_ENABLED)
            && plugin.getItemManager().isBait(offhand)
            && plugin.getItemManager().isBaitApplicableForWater(offhand)) {
            baitTargetMobIds = plugin.getItemManager().getBaitTargetMobIds(offhand);
            baitNativeBiomeGroups = new java.util.ArrayList<>(plugin.getItemManager().getBaitNativeBiomeGroups(offhand));
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
        double doubleCatchBonus = activeBait != null
                ? activeBait.getBonus(com.fishrework.model.Bait.DOUBLE_CATCH_CHANCE)
                : 0;
        double xpMultiplier = activeBait != null
                ? activeBait.getBonus(com.fishrework.model.Bait.XP_MULTIPLIER)
                : 0;

        return new BaitContext(baitTargetMobIds, new java.util.HashSet<>(baitNativeBiomeGroups),
                rareCreatureBonus, treasureBonus, doubleCatchBonus, xpMultiplier);
    }

    private double resolveDoubleCatchChance(Player player, int currentLevel, double baitDoubleCatchBonus) {
        if (!plugin.isFeatureEnabled(FeatureKeys.DOUBLE_CATCH_ENABLED)) {
            return 0;
        }
        double doubleCatchChance = plugin.getLevelManager().getDoubleCatchChance(currentLevel);
        doubleCatchChance += plugin.getMobManager().getEquipmentDoubleCatchBonus(player);
        doubleCatchChance += baitDoubleCatchBonus;
        return doubleCatchChance;
    }

    private String resolveMobId(Player player, Item caughtItem, BaitContext baitContext) {
        if (!plugin.isFeatureEnabled(FeatureKeys.CUSTOM_MOBS_ENABLED)) {
            return null;
        }
        String mobId = plugin.getMobManager().getMobToSpawn(
                player,
                Skill.FISHING,
                caughtItem.getLocation(),
                baitContext.rareCreatureBonus,
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

    private MobCatchResult handleCustomMobCatch(Player player,
                                                Item caughtItem,
                                                ItemStack currentItemStack,
                                                PlayerData data,
                                                FishingSession session,
                                                String mobId,
                                                int currentLevel,
                                                double doubleCatchChance,
                                                double baitXpMultiplier) {
        com.fishrework.model.CustomMob mobDef = plugin.getMobRegistry().get(mobId);
        if (mobDef == null) {
            return new MobCatchResult(false, currentItemStack);
        }

        if (mobDef.isTreasure()) {
            ItemStack treasureItem = null;
            if (!mobDef.getDrops().isEmpty()) {
                treasureItem = mobDef.getDrops().get(0).roll();
            }
            if (treasureItem == null) {
                treasureItem = plugin.getTreasureManager().getRandomTreasure();
            }

            ItemStack resultingItemStack = treasureItem;
            if (BagUtils.hasFishBagInInventory(plugin, player)) {
                ItemStack overflow = FishBagGUI.addToBag(data, treasureItem);
                plugin.getDatabaseManager().saveFishBag(player.getUniqueId(), data.getFishBagContents());

                if (overflow == null) {
                    caughtItem.remove();
                    player.sendActionBar(plugin.getLanguageManager().getMessage("fishinglistener.treasure_sent_to_fish_bag", "Treasure sent to Fish Bag!").color(NamedTextColor.GOLD));
                } else {
                    caughtItem.setItemStack(overflow);
                    resultingItemStack = overflow;
                }
            } else {
                caughtItem.setItemStack(treasureItem);
            }

            player.sendMessage(Component.text(plugin.getLanguageManager().getString(
                            "fishinglistener.you_caught",
                            "You caught a %mob%!",
                            "mob", mobDef.getLocalizedDisplayName(plugin.getLanguageManager())))
                    .color(mobDef.getRarity().getColor()));

            plugin.getMobManager().registerCatch(player, mobId, 0.0, mobDef, baitXpMultiplier);

            session.recordCatch();
            session.recordTreasure();
            FishingUtils.playCatchEffects(player, mobDef.getRarity(), caughtItem.getLocation());
            FishingUtils.broadcastRareCatch(plugin, player, mobDef.getLocalizedDisplayName(plugin.getLanguageManager()), mobDef.getRarity(), false);

            return new MobCatchResult(false, resultingItemStack);
        }

        if (!plugin.getMobManager().shouldSpawnLiveCatch(mobDef)) {
            Location rewardLocation = caughtItem.getLocation().clone();
            SeaCreatureWeightProfile weightProfile = plugin.getMobManager().rollWeightProfile(mobDef);
            caughtItem.remove();
            plugin.getMobManager().dropMobLoot(
                    player,
                    rewardLocation,
                    mobDef,
                    false,
                    weightProfile.getDropRollMultiplier(),
                    true
            );
            plugin.getMobManager().registerCatch(player, mobId, weightProfile.getWeightKg(), mobDef, baitXpMultiplier);
            session.recordCatch();
            maybeSendSeaCreatureMessage(player, data, mobDef, weightProfile);
            grantWaterCreatureCatchProgress(player);
            if (mobDef.getRarity() != null && mobDef.getRarity().ordinal() >= Rarity.RARE.ordinal()) {
                FishingUtils.playCatchEffects(player, mobDef.getRarity(), rewardLocation);
                FishingUtils.broadcastRareCatch(plugin, player, mobDef.getLocalizedDisplayName(plugin.getLanguageManager()), mobDef.getRarity(), false);
            }
            return new MobCatchResult(true, currentItemStack);
        }

        Location spawnLocation = caughtItem.getLocation().clone().add(0, -0.5, 0);
        caughtItem.remove();
        SeaCreatureWeightProfile weightProfile = plugin.getMobManager().spawnMob(spawnLocation, mobId, player, baitXpMultiplier);

        Rarity doubleSpawnCap = getDoubleSpawnRarityCap(currentLevel);
        boolean canDoubleSpawn = mobDef.getRarity() != null
                && mobDef.getRarity().ordinal() <= doubleSpawnCap.ordinal();
        if (canDoubleSpawn && random.nextDouble() * 100 < doubleCatchChance) {
            Location secondSpawn = spawnLocation.clone().add(
                    (random.nextDouble() - 0.5) * 1.5,
                    0,
                    (random.nextDouble() - 0.5) * 1.5
            );
            plugin.getMobManager().spawnMob(secondSpawn, mobId, player, baitXpMultiplier);
            player.sendMessage(plugin.getLanguageManager().getMessage("fishinglistener.double_catch", "🎣 DOUBLE CATCH!").color(NamedTextColor.GOLD));
        }

        maybeSendSeaCreatureMessage(player, data, mobDef, weightProfile);

        grantWaterCreatureCatchProgress(player);

        session.recordCatch();
        if (mobDef.getRarity() != null && mobDef.getRarity().ordinal() >= Rarity.RARE.ordinal()) {
            FishingUtils.playCatchEffects(player, mobDef.getRarity(), spawnLocation);
            FishingUtils.broadcastRareCatch(plugin, player, mobDef.getLocalizedDisplayName(plugin.getLanguageManager()), mobDef.getRarity(), false);
        }

        return new MobCatchResult(true, currentItemStack);
    }

    private void grantWaterCreatureCatchProgress(Player player) {
        plugin.getAdvancementManager().grantAdvancement(player, plugin.getAdvancementManager().FISHH_KEY);

        if (!player.hasDiscoveredRecipe(plugin.getItemManager().FISH_BUCKET_RECIPE_KEY)) {
            player.discoverRecipe(plugin.getItemManager().FISH_BUCKET_RECIPE_KEY);
            player.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishinglistener.unlocked_recipe_fish_bucket",
                    "Unlocked Recipe: Fish Bucket!").color(NamedTextColor.GOLD));
        }
    }

    private void maybeSendSeaCreatureMessage(Player player, PlayerData data, com.fishrework.model.CustomMob mobDef,
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
        TextColor rarityColor = mobDef.getRarity() != null ? mobDef.getRarity().getColor() : NamedTextColor.WHITE;
        double weightKg = weightProfile != null ? weightProfile.getWeightKg() : 0.0;
        Rarity weightRarity = plugin.getMobManager().getWeightRarity(weightProfile);
        String template = plugin.getLanguageManager().getString(
                "fishinglistener.you_hooked_sea_creature",
                "You hooked in a %mob% (%weight%)!");
        player.sendMessage(FishingUtils.buildHookedMessage(
                template,
                mobName,
                rarityColor,
                com.fishrework.util.FormatUtil.format("%.2fkg", weightKg),
                weightRarity.getColor(),
                NamedTextColor.AQUA
        ));
    }

    private boolean tryAutoSellCatch(Player player,
                                     Item caughtItem,
                                     ItemStack itemStack,
                                     String mobId,
                                     PlayerData data,
                                     FishingSession session,
                                     double baseXp,
                                     double baitXpMultiplier) {
        if (!plugin.isFeatureEnabled(FeatureKeys.AUTO_SELL_ENABLED)
                || session.getAutoSellMode() == AutoSellMode.OFF
                || mobId != null) {
            return false;
        }

        double sellPrice = AutoSellUtil.getAutoSellPrice(plugin.getItemManager(), itemStack, session.getAutoSellMode());
        if (sellPrice <= 0) {
            return false;
        }

        double total = sellPrice * itemStack.getAmount();
        data.addBalance(total);
        session.addDoubloonsEarned(total);
        String currencyName = plugin.getLanguageManager().getCurrencyName();
        caughtItem.remove();
        player.sendActionBar(Component.text(plugin.getLanguageManager().getString(
                        "fishinglistener.auto_sold_for",
                        "Auto-sold for %total% %currency%",
                        "total", com.fishrework.util.FormatUtil.format("%.0f", total),
                        "currency", currencyName))
                .color(NamedTextColor.GREEN));

        double finalBaseXp = applyXpMultipliers(baseXp, baitXpMultiplier);
        plugin.getSkillManager().grantXp(player, Skill.FISHING, finalBaseXp, Skill.FISHING.getLocalizedDisplayName(plugin.getLanguageManager()));
        return true;
    }

    private void maybeStoreCatchInFishBag(Player player,
                                          Item caughtItem,
                                          ItemStack itemStack,
                                          String mobId,
                                          PlayerData data) {
        if (!plugin.isFeatureEnabled(FeatureKeys.FISH_BAG_ENABLED)
                || mobId != null
                || !BagUtils.hasFishBagInInventory(plugin, player)
                || !BagUtils.isAllowedInFishBag(plugin, itemStack)) {
            return;
        }

        ItemStack overflow = FishBagGUI.addToBag(data, itemStack);
        plugin.getDatabaseManager().saveFishBag(player.getUniqueId(), data.getFishBagContents());
        if (overflow == null) {
            caughtItem.remove();
            player.sendActionBar(plugin.getLanguageManager().getMessage("fishinglistener.catch_sent_to_fish_bag", "Catch sent to Fish Bag!").color(NamedTextColor.GOLD));
        } else {
            caughtItem.setItemStack(overflow);
        }
    }

    private void tryDoubleCatchDrop(Player player, ItemStack itemStack, double doubleCatchChance) {
        if (random.nextDouble() * 100 < doubleCatchChance) {
            player.getWorld().dropItemNaturally(player.getLocation(), itemStack.clone());
            player.sendMessage(plugin.getLanguageManager().getMessage("fishinglistener.ud83cudfa3_double_catch", "\uD83C\uDFA3 DOUBLE CATCH!").color(NamedTextColor.GOLD));
        }
    }

    private double applyXpMultipliers(double baseXp, double baitXpMultiplier) {
        double finalXp = baseXp;
        if (baitXpMultiplier > 0) {
            finalXp *= (1.0 + baitXpMultiplier / 100.0);
        }
        return finalXp;
    }

    private static final class BaitContext {
        private final List<String> targetMobIds;
        private final Set<com.fishrework.model.BiomeGroup> nativeBiomeGroups;
        private final double rareCreatureBonus;
        private final double treasureBonus;
        private final double doubleCatchBonus;
        private final double xpMultiplier;

        private BaitContext(List<String> targetMobIds,
                            Set<com.fishrework.model.BiomeGroup> nativeBiomeGroups,
                            double rareCreatureBonus,
                            double treasureBonus,
                            double doubleCatchBonus,
                            double xpMultiplier) {
            this.targetMobIds = targetMobIds;
            this.nativeBiomeGroups = nativeBiomeGroups;
            this.rareCreatureBonus = rareCreatureBonus;
            this.treasureBonus = treasureBonus;
            this.doubleCatchBonus = doubleCatchBonus;
            this.xpMultiplier = xpMultiplier;
        }
    }

    private static final class MobCatchResult {
        private final boolean stopProcessing;
        private final ItemStack resultingItemStack;

        private MobCatchResult(boolean stopProcessing, ItemStack resultingItemStack) {
            this.stopProcessing = stopProcessing;
            this.resultingItemStack = resultingItemStack;
        }
    }

    private Rarity getDoubleSpawnRarityCap(int fishingLevel) {
        if (fishingLevel >= 44) {
            return Rarity.EPIC;
        }
        if (fishingLevel >= 31) {
            return Rarity.RARE;
        }
        return Rarity.UNCOMMON;
    }

    @EventHandler
    public void onHookHitEntity(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof FishHook hook)) return;
        if (!(hook.getShooter() instanceof Player player)) return;
        if (!(event.getHitEntity() instanceof LivingEntity target)) return;

        // Only apply bobber damage to plugin-spawned fishing mobs.
        if (!plugin.getMobManager().isFishedMob(target)) return;

        ItemStack rod = getFishingRodInHands(player);
        if (rod == null) return;

        org.bukkit.persistence.PersistentDataContainer pdc = rod.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(plugin.getItemManager().BOBBER_DAMAGE_KEY, PersistentDataType.DOUBLE)) return;

        double damage = pdc.get(plugin.getItemManager().BOBBER_DAMAGE_KEY, PersistentDataType.DOUBLE);
        double reducedDamage = Math.max(0.0, Math.round(damage * 0.25));
        if (reducedDamage <= 0.0) return;
        target.damage(reducedDamage, player);
    }

    private ItemStack getFishingRodInHands(Player player) {
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod.getType() == Material.FISHING_ROD && rod.hasItemMeta()) return rod;

        rod = player.getInventory().getItemInOffHand();
        if (rod.getType() == Material.FISHING_ROD && rod.hasItemMeta()) return rod;

        return null;
    }

    private void consumeOneOffhandBait(Player player, ItemStack offhand) {
        if (offhand == null || offhand.getType().isAir()) return;

        if (offhand.getAmount() <= 1) {
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        } else {
            offhand.setAmount(offhand.getAmount() - 1);
        }
    }

    private double getXpForFish(Material type) {
        switch (type) {
            case COD: return plugin.getConfig().getDouble("fishing.xp.cod", 10.0);
            case SALMON: return plugin.getConfig().getDouble("fishing.xp.salmon", 15.0);
            case TROPICAL_FISH: return plugin.getConfig().getDouble("fishing.xp.tropical_fish", 25.0);
            case PUFFERFISH: return plugin.getConfig().getDouble("fishing.xp.pufferfish", 30.0);
            case ENCHANTED_BOOK:
            case BOW:
            case FISHING_ROD:
            case NAME_TAG:
            case SADDLE:
            case NAUTILUS_SHELL:
                return plugin.getConfig().getDouble("fishing.xp.treasure", 50.0);
            default:
                return plugin.getConfig().getDouble("fishing.xp.junk", 5.0);
        }
    }
}

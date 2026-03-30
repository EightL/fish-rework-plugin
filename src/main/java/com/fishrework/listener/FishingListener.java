package com.fishrework.listener;

import com.fishrework.FishRework;
import com.fishrework.gui.FishBagGUI;
import com.fishrework.model.FishingSession;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Rarity;
import com.fishrework.model.Skill;
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

    // Vanilla fish that can be auto-sold
    private static final Set<Material> AUTO_SELL_FISH = Set.of(
            Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH
    );

    public FishingListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
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

        // 1. Calculate base XP
        double baseXp = getXpForFish(type);
        if (baseXp <= 0) return;

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;
        FishingSession session = data.getSession();

        // ── Bait Check ──
        com.fishrework.model.Bait activeBait = null;
        List<String> baitTargetMobIds = java.util.Collections.emptyList();
        Set<com.fishrework.model.BiomeGroup> baitNativeBiomeGroups = java.util.Collections.emptySet();
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (plugin.isFeatureEnabled("bait_system_enabled") && plugin.getItemManager().isBait(offhand)) {
            baitTargetMobIds = plugin.getItemManager().getBaitTargetMobIds(offhand);
            baitNativeBiomeGroups = plugin.getItemManager().getBaitNativeBiomeGroups(offhand);
            String baitId = plugin.getItemManager().getBaitId(offhand);
            if (baitId != null) {
                activeBait = plugin.getBaitRegistry().get(baitId);
                consumeOneOffhandBait(player, offhand);
            }
        }

        // Extract bait bonuses
        double baitRareCreatureBonus = activeBait != null ? activeBait.getBonus(com.fishrework.model.Bait.RARE_CREATURE_CHANCE) : 0;
        double baitTreasureBonus = activeBait != null ? activeBait.getBonus(com.fishrework.model.Bait.TREASURE_CHANCE) : 0;
        double baitDoubleCatchBonus = activeBait != null ? activeBait.getBonus(com.fishrework.model.Bait.DOUBLE_CATCH_CHANCE) : 0;
        double baitXpMultiplier = activeBait != null ? activeBait.getBonus(com.fishrework.model.Bait.XP_MULTIPLIER) : 0;

        int currentLevel = data.getLevel(Skill.FISHING);
        double doubleCatchChance = 0;
        if (plugin.isFeatureEnabled("double_catch_enabled")) {
            doubleCatchChance = plugin.getLevelManager().getDoubleCatchChance(currentLevel);
            doubleCatchChance += plugin.getMobManager().getEquipmentDoubleCatchBonus(player);
            doubleCatchChance += baitDoubleCatchBonus;
        }

        // 2. Custom mob/treasure check
        String mobId = null;
        if (plugin.isFeatureEnabled("custom_mobs_enabled")) {
             mobId = plugin.getMobManager().getMobToSpawn(
                player,
                Skill.FISHING,
                caughtItem.getLocation(),
                baitRareCreatureBonus,
                baitTreasureBonus,
                baitTargetMobIds,
                baitNativeBiomeGroups
            );
        }
        if (mobId != null) {
            com.fishrework.model.CustomMob mobDef = plugin.getMobRegistry().get(mobId);

            if (mobDef.isTreasure()) {
                // It's a treasure chest! Replace the caught item.
                ItemStack treasureItem = null;
                if (!mobDef.getDrops().isEmpty()) {
                    treasureItem = mobDef.getDrops().get(0).roll();
                }
                if (treasureItem == null) treasureItem = plugin.getTreasureManager().getRandomTreasure();

                if (hasFishBagInInventory(player)) {
                    ItemStack overflow = FishBagGUI.addToBag(data, treasureItem);
                    plugin.getDatabaseManager().saveFishBag(player.getUniqueId(), data.getFishBagContents());

                    if (overflow == null) {
                        caughtItem.remove();
                        itemStack = treasureItem;
                        player.sendActionBar(Component.text("Treasure sent to Fish Bag!").color(NamedTextColor.GOLD));
                    } else {
                        caughtItem.setItemStack(overflow);
                        itemStack = overflow;
                    }
                } else {
                    caughtItem.setItemStack(treasureItem);
                    itemStack = treasureItem;
                }

                player.sendMessage(Component.text("You caught a " + mobDef.getDisplayName() + "!").color(mobDef.getRarity().getColor()));

                // Register Catch (XP, Collection, Advancement, Msg)
                plugin.getMobManager().registerCatch(player, mobId, 0.0, mobDef, baitXpMultiplier);

                // ── QOL: Session tracking ──
                session.recordCatch();
                session.recordTreasure();

                // ── QOL: Rarity sound & particles ──
                FishingUtils.playCatchEffects(player, mobDef.getRarity(), caughtItem.getLocation());

                // ── QOL: Rare catch broadcast ──
                FishingUtils.broadcastRareCatch(plugin, player, mobDef.getDisplayName(), mobDef.getRarity(), false);

            } else {
                // It's a living entity (hostile or passive)
                event.getCaught().remove();
                plugin.getMobManager().spawnMob(caughtItem.getLocation().add(0, -0.5, 0), mobId, player, baitXpMultiplier);

                Rarity doubleSpawnCap = getDoubleSpawnRarityCap(currentLevel);
                boolean canDoubleSpawn = mobDef.getRarity() != null
                    && mobDef.getRarity().ordinal() <= doubleSpawnCap.ordinal();
                if (canDoubleSpawn && random.nextDouble() * 100 < doubleCatchChance) {
                    Location secondSpawn = caughtItem.getLocation().clone().add(
                            (random.nextDouble() - 0.5) * 1.5,
                            -0.5,
                            (random.nextDouble() - 0.5) * 1.5
                    );
                    plugin.getMobManager().spawnMob(secondSpawn, mobId, player, baitXpMultiplier);
                    player.sendMessage(Component.text("🎣 DOUBLE CATCH!").color(NamedTextColor.GOLD));
                }

                if (plugin.getMobManager().isHostile(mobId)) {
                    player.sendMessage(Component.text("You hooked a rare sea creature!").color(NamedTextColor.AQUA));
                }

                // Grant "Fishh" advancement
                plugin.getAdvancementManager().grantAdvancement(player, plugin.getAdvancementManager().FISHH_KEY);

                // Unlock Fish Bucket recipe
                if (!player.hasDiscoveredRecipe(plugin.getItemManager().FISH_BUCKET_RECIPE_KEY)) {
                    player.discoverRecipe(plugin.getItemManager().FISH_BUCKET_RECIPE_KEY);
                    player.sendMessage(Component.text("Unlocked Recipe: Fish Bucket!").color(NamedTextColor.GOLD));
                }

                // ── QOL: Session tracking ──
                session.recordCatch();

                // ── QOL: Rarity sound & particles for rare+ mobs ──
                if (mobDef.getRarity() != null && mobDef.getRarity().ordinal() >= Rarity.RARE.ordinal()) {
                    FishingUtils.playCatchEffects(player, mobDef.getRarity(), caughtItem.getLocation());
                    FishingUtils.broadcastRareCatch(plugin, player, mobDef.getDisplayName(), mobDef.getRarity(), false);
                }

                return; // Mob spawned — no item XP
            }
        }

        // ── QOL: Session tracking (normal fish catch) ──
        if (mobId == null) {
            session.recordCatch();
        }

        // ── QOL: Catch streak display ──
        double streakMultiplier = 1.0;
        if (plugin.isFeatureEnabled("catch_streak_enabled")) {
            int streakTier = session.getStreakTier();
            streakMultiplier = session.getStreakMultiplier();
            if (streakTier > 0) {
                String streakText = "\u2B50".repeat(Math.min(streakTier, 5));
                double bonus = (streakMultiplier - 1.0) * 100;
                player.sendActionBar(Component.text(streakText + " Streak x" + session.getCurrentStreak()
                        + " (+" + String.format("%.0f", bonus) + "% Bonus) " + streakText)
                        .color(NamedTextColor.GOLD));
            }
        }

        // ── QOL: Auto-sell common fish ──
        if (plugin.isFeatureEnabled("auto_sell_enabled") && session.isAutoSellEnabled() && AUTO_SELL_FISH.contains(type) && mobId == null) {
            double sellPrice = plugin.getItemManager().getSellPrice(itemStack);
            if (sellPrice > 0) {
                double total = sellPrice * itemStack.getAmount();
                total *= streakMultiplier;
                data.addBalance(total);
                session.addDoubloonsEarned(total);
                String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");
                caughtItem.remove();
                player.sendActionBar(Component.text("Auto-sold for " + String.format("%.0f", total) + " " + currencyName)
                        .color(NamedTextColor.GREEN));
                double finalBaseXp = baseXp;
                if (baitXpMultiplier > 0) {
                    finalBaseXp *= (1.0 + baitXpMultiplier / 100.0);
                }
                finalBaseXp *= streakMultiplier;
                plugin.getSkillManager().grantXp(player, Skill.FISHING, finalBaseXp, "Fishing");
                return;
            }
        }

        // Auto-store eligible caught items into Fish Bag (same convenience flow as lava bag auto-collection).
        if (plugin.isFeatureEnabled("fish_bag_enabled") && mobId == null && hasFishBagInInventory(player) && isAllowedInFishBag(itemStack)) {
            ItemStack overflow = FishBagGUI.addToBag(data, itemStack);
            plugin.getDatabaseManager().saveFishBag(player.getUniqueId(), data.getFishBagContents());
            if (overflow == null) {
                caughtItem.remove();
                player.sendActionBar(Component.text("Catch sent to Fish Bag!").color(NamedTextColor.GOLD));
            } else {
                caughtItem.setItemStack(overflow);
            }
        }

        // 4. Double catch bonus
        if (random.nextDouble() * 100 < doubleCatchChance) {
            player.getWorld().dropItemNaturally(player.getLocation(), itemStack.clone());
            player.sendMessage(Component.text("\uD83C\uDFA3 DOUBLE CATCH!").color(NamedTextColor.GOLD));
        }

        // 5. Grant XP via SkillManager (with bait XP bonus + streak bonus)
        double finalBaseXp = baseXp;
        if (baitXpMultiplier > 0) {
            finalBaseXp *= (1.0 + baitXpMultiplier / 100.0);
        }
        finalBaseXp *= streakMultiplier;
        plugin.getSkillManager().grantXp(player, Skill.FISHING, finalBaseXp, "Fishing");
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

    private boolean hasFishBagInInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (plugin.getItemManager().isFishBag(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedInFishBag(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (AUTO_SELL_FISH.contains(item.getType())) return true;
        if (item.getType() == Material.INK_SAC) return true;
        if (!item.hasItemMeta()) return false;

        return item.getItemMeta().getPersistentDataContainer().has(
                plugin.getItemManager().CUSTOM_ITEM_KEY,
                PersistentDataType.STRING
        );
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

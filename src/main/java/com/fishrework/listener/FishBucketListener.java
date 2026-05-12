package com.fishrework.listener;

import com.fishrework.FishRework;
import java.util.HashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import com.fishrework.manager.ItemManager;
import com.fishrework.MobManager;
import com.fishrework.model.CustomMob;

public class FishBucketListener implements Listener {

    private final FishRework plugin;

    public FishBucketListener(FishRework plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBucketMob(PlayerInteractEntityEvent event) {
        plugin.getLanguageManager().withPlayer(event.getPlayer(), () -> handleBucketMob(event));
    }

    private void handleBucketMob(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // 1. Check if holding Fish Bucket
        if (item.getType() == Material.WATER_BUCKET && plugin.getItemManager().isFishBucket(item)) {
            // 2. ALWAYS Cancel to prevent vanilla water bucket behavior (placing water, catching vanilla fish)
            event.setCancelled(true);
            
            if (!(event.getRightClicked() instanceof LivingEntity)) return;
            LivingEntity entity = (LivingEntity) event.getRightClicked();
            
            // 3. Check if it is a custom FISHED mob
            if (plugin.getMobManager().isFishedMob(entity)) {
                String mobId = plugin.getMobManager().getMobId(entity);
                CustomMob def = plugin.getMobRegistry().get(mobId);
                
                // 4. Check if Passive (catchable)
                if (plugin.getMobManager().isPassive(mobId)) {
                    
                    if (!plugin.getMobManager().isAquatic(entity.getType())) {
                        // Land Mob -> Give Spawn Egg
                        Material spawnEgg = Material.getMaterial(entity.getType().name() + "_SPAWN_EGG");
                        if (spawnEgg != null) {
                            ItemStack eggItem = new ItemStack(spawnEgg);
                            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(eggItem);
                            for (ItemStack remaining : leftover.values()) {
                                player.getWorld().dropItemNaturally(player.getLocation(), remaining);
                            }
                        }
                        
                        // Register Catch (XP, Collection, Advancement, Msg)
                        plugin.getMobManager().registerCatch(
                                player,
                                mobId,
                                plugin.getMobManager().getMobWeight(entity),
                                def
                        );
                        
                        // Effects
                        player.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL_FISH, 1.0f, 1.0f);
                        player.sendMessage(Component.text(plugin.getLanguageManager().getString(
                                        "fishbucketlistener.you_caught",
                                        "You caught a %mob%!",
                                        "mob", def != null
                                                ? def.getLocalizedDisplayName(plugin.getLanguageManager())
                                                : plugin.getLanguageManager().getString("fishbucketlistener.creature", "Creature")))
                                .color(NamedTextColor.GREEN));

                        // Advancement & Recipe
                        plugin.getAdvancementManager().grantAdvancement(player, plugin.getAdvancementManager().BUCKET_CATCHER_KEY);
                        plugin.getRecipeRegistry().syncRecipes(player);

                        // Remove mob
                        entity.remove();
                        return;
                    }

                    // Water Mob (Fish) -> Standard behavior
                    plugin.getMobManager().giveMobReward(player, entity, true);
                    
                        // Collection update handled by giveMobReward -> registerCatch
                        double weight = 0.0;
                        if (entity.getPersistentDataContainer().has(plugin.getMobManager().FISH_WEIGHT_KEY, org.bukkit.persistence.PersistentDataType.DOUBLE)) {
                            weight = entity.getPersistentDataContainer().get(plugin.getMobManager().FISH_WEIGHT_KEY, org.bukkit.persistence.PersistentDataType.DOUBLE);
                        }
                    
                    // Effects
                    player.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL_FISH, 1.0f, 1.0f);
                    String weightStr = com.fishrework.util.FormatUtil.format("%.2f", weight);
                    player.sendMessage(Component.text(plugin.getLanguageManager().getString(
                                    "fishbucketlistener.you_caught_fish_weight",
                                    "You caught the fish! (%weight%kg)",
                                    "weight", weightStr))
                            .color(NamedTextColor.GREEN));
                    
                    // Advancement & Recipe
                    plugin.getAdvancementManager().grantAdvancement(player, plugin.getAdvancementManager().BUCKET_CATCHER_KEY);
                    plugin.getRecipeRegistry().syncRecipes(player);

                    // Remove mob
                    entity.remove();
                } else {
                    // Hostile
                    player.sendMessage(plugin.getLanguageManager().getMessage("fishbucketlistener.you_cannot_catch_monsters_with", "You cannot catch monsters with a bucket!").color(NamedTextColor.RED));
                }
            } 
            // If not a fished mob (e.g. vanilla sheep or salmon), nothing happens because event is cancelled.
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        plugin.getLanguageManager().withPlayer(event.getPlayer(), () -> handleBucketEmpty(event));
    }

    private void handleBucketEmpty(PlayerBucketEmptyEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        
        if (plugin.getItemManager().isFishBucket(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getLanguageManager().getMessage("fishbucketlistener.you_cannot_empty_the_fish", "You cannot empty the Fish Bucket!").color(NamedTextColor.RED));
        }
    }
}

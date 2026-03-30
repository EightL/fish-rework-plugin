package com.fishrework.manager;

import com.fishrework.FishRework;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies passive effects while players carry a lava ring.
 */
public class LavaRingManager {

    private final FishRework plugin;
    private BukkitTask task;
    private final Map<UUID, Boolean> lavaFlightPlayers = new HashMap<>();

    public LavaRingManager(FishRework plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) return;

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean hasLavaRing = hasLavaRing(player);
                boolean hasEruptionRing = hasEruptionRing(player);
                if (hasLavaRing || hasEruptionRing) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, true, false, false));
                }

                if (hasEruptionRing && player.isInLava()) {
                    enableLavaFlight(player);
                } else {
                    disableLavaFlight(player);
                }
            }
        }, 0L, 2L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        for (UUID playerId : lavaFlightPlayers.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                restorePlayerFlight(player, lavaFlightPlayers.get(playerId));
            }
        }
        lavaFlightPlayers.clear();
    }

    private boolean hasLavaRing(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (plugin.getItemManager().isCustomItem(item, "lava_ring")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEruptionRing(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (plugin.getItemManager().isCustomItem(item, "eruption_ring")) {
                return true;
            }
        }
        return false;
    }

    private void enableLavaFlight(Player player) {
        player.setFireTicks(0);

        UUID playerId = player.getUniqueId();
        if (!lavaFlightPlayers.containsKey(playerId)) {
            lavaFlightPlayers.put(playerId, player.getAllowFlight());
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
        }

        if (!player.isFlying()) {
            player.setFlying(true);
        }
    }

    private void disableLavaFlight(Player player) {
        UUID playerId = player.getUniqueId();
        Boolean previousAllowFlight = lavaFlightPlayers.remove(playerId);
        if (previousAllowFlight == null) return;

        restorePlayerFlight(player, previousAllowFlight);
    }

    private void restorePlayerFlight(Player player, boolean previousAllowFlight) {
        if (player.isFlying()) {
            player.setFlying(false);
        }
        if (!previousAllowFlight && player.getAllowFlight()) {
            player.setAllowFlight(false);
        }
    }
}
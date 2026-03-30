package com.fishrework.util;

import com.fishrework.FishRework;
import com.fishrework.model.Rarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

public class FishingUtils {

    /**
     * Plays sound effects and particles based on catch rarity.
     */
    public static void playCatchEffects(Player player, Rarity rarity, Location loc) {
        if (rarity == null) return;
        switch (rarity) {
            case RARE -> {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 15, 0.5, 0.5, 0.5);
            }
            case EPIC -> {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 30, 1.0, 1.0, 1.0);
            }
            case LEGENDARY -> {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
                loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 50, 1.0, 1.5, 1.0);
            }
            default -> { /* No special effects for common/uncommon */ }
        }
    }

    /**
     * Broadcasts rare catches (Epic+) to the whole server if the feature is enabled.
     */
    public static void broadcastRareCatch(FishRework plugin, Player player, String mobName, Rarity rarity, boolean isLava) {
        if (!plugin.isFeatureEnabled("catch_broadcast_enabled")) return;
        if (rarity == null || rarity.ordinal() < Rarity.EPIC.ordinal()) return;

        TextColor color = rarity.getColor();
        Component message;
        
        String actionText = isLava ? " lava-fished a " : " caught a ";
        String actionAnText = isLava ? " lava-fished an " : " caught an ";
        
        if (rarity == Rarity.LEGENDARY) {
            message = Component.text("\u2728 ").color(NamedTextColor.GOLD)
                    .append(Component.text(player.getName()).color(NamedTextColor.WHITE))
                    .append(Component.text(actionText).color(NamedTextColor.GRAY))
                    .append(Component.text("LEGENDARY ").color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true))
                    .append(Component.text(mobName).color(color))
                    .append(Component.text("!").color(NamedTextColor.GRAY))
                    .append(Component.text(" \u2728").color(NamedTextColor.GOLD));
        } else {
            message = Component.text(player.getName()).color(NamedTextColor.WHITE)
                    .append(Component.text(actionAnText).color(NamedTextColor.GRAY))
                    .append(Component.text(rarity.name() + " ").color(color))
                    .append(Component.text(mobName).color(color))
                    .append(Component.text("!").color(NamedTextColor.GRAY));
        }

        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online != player) {
                online.sendMessage(message);
            }
        }
    }
}

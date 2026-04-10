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

    private static final Particle EPIC_CATCH_PARTICLE = resolveParticle("ENCHANT", "ENCHANTED_HIT");
    private static final Sound LAVA_EXTINGUISH_SOUND = resolveSound("BLOCK_LAVA_EXTINGUISH", "BLOCKLAVAEXTINGUISH");

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
                Particle epicParticle = EPIC_CATCH_PARTICLE != null ? EPIC_CATCH_PARTICLE : Particle.END_ROD;
                loc.getWorld().spawnParticle(epicParticle, loc, 30, 1.0, 1.0, 1.0);
            }
            case LEGENDARY -> {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
                loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 50, 1.0, 1.5, 1.0);
            }
            case SPECIAL -> {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.3f);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.2f, 1.8f);
                loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 70, 1.2, 1.6, 1.2);
                loc.getWorld().spawnParticle(Particle.END_ROD, loc, 45, 0.8, 0.8, 0.8);
            }
            default -> { /* No special effects for common/uncommon */ }
        }
    }

    /**
     * Broadcasts rare catches (Epic+) to the whole server if the feature is enabled.
     */
    public static void broadcastRareCatch(FishRework plugin, Player player, String mobName, Rarity rarity, boolean isLava) {
        if (!plugin.isFeatureEnabled(FeatureKeys.CATCH_BROADCAST_ENABLED)) return;
        if (rarity == null || rarity.ordinal() < Rarity.EPIC.ordinal()) return;

        TextColor color = rarity.getColor();
        Component message;
        
        String actionText = isLava ? " lava-fished a " : " caught a ";
        String actionAnText = isLava ? " lava-fished an " : " caught an ";
        
        if (rarity == Rarity.LEGENDARY || rarity == Rarity.SPECIAL) {
            String rarityLabel = rarity == Rarity.SPECIAL ? "SPECIAL " : "LEGENDARY ";
            message = Component.text("\u2728 ").color(NamedTextColor.GOLD)
                    .append(Component.text(player.getName()).color(NamedTextColor.WHITE))
                    .append(Component.text(actionText).color(NamedTextColor.GRAY))
                .append(Component.text(rarityLabel).color(color).decoration(TextDecoration.BOLD, true))
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

    public static Sound getLavaExtinguishSound() {
        return LAVA_EXTINGUISH_SOUND;
    }

    private static Particle resolveParticle(String... candidates) {
        for (String candidate : candidates) {
            try {
                return Particle.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
                // Try next candidate for cross-version enum compatibility.
            }
        }
        return null;
    }

    private static Sound resolveSound(String... candidates) {
        for (String candidate : candidates) {
            try {
                return Sound.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
                // Try next candidate for cross-version enum compatibility.
            }
        }
        return null;
    }
}

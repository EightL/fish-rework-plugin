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

    public static void broadcastLegendaryTreasureCatch(FishRework plugin, Player player, String treasureName, Rarity rarity, boolean isLava) {
        if (!plugin.isFeatureEnabled(FeatureKeys.CATCH_BROADCAST_ENABLED)) return;
        if (rarity == null || rarity.ordinal() < Rarity.LEGENDARY.ordinal()) return;

        TextColor color = rarity.getColor();
        String actionText = isLava
                ? plugin.getLanguageManager().getString("fishingutils.action_lava_fished_a", " lava-fished a ")
                : plugin.getLanguageManager().getString("fishingutils.action_caught_a", " caught a ");

        Component message = plugin.getLanguageManager().getMessage("fishingutils.u2728", "\u2728 ").color(NamedTextColor.GOLD)
                .append(Component.text(player.getName()).color(NamedTextColor.WHITE))
                .append(Component.text(actionText).color(NamedTextColor.GRAY))
                .append(Component.text(treasureName).color(color).decoration(TextDecoration.BOLD, true))
                .append(plugin.getLanguageManager().getMessage("fishingutils.exclamation", "!").color(NamedTextColor.GRAY))
                .append(plugin.getLanguageManager().getMessage("fishingutils.u2728", " \u2728").color(NamedTextColor.GOLD));

        for (Player online : plugin.getServer().getOnlinePlayers()) {
            online.sendMessage(message);
        }
    }

    public static void broadcastMythicWeightCatch(FishRework plugin, Player player, String mobName, Rarity mobRarity,
                                                  double weightKg, boolean isLava) {
        if (!plugin.isFeatureEnabled(FeatureKeys.CATCH_BROADCAST_ENABLED)) return;

        TextColor mobColor = mobRarity != null ? mobRarity.getColor() : NamedTextColor.WHITE;
        String actionText = isLava
                ? plugin.getLanguageManager().getString("fishingutils.action_lava_fished_a_mythic_weight", " lava-fished a mythic-weight ")
                : plugin.getLanguageManager().getString("fishingutils.action_caught_a_mythic_weight", " caught a mythic-weight ");
        String weight = FormatUtil.format("%.2fkg", weightKg);

        Component message = plugin.getLanguageManager().getMessage("fishingutils.u2728", "\u2728 ").color(NamedTextColor.GOLD)
                .append(Component.text(player.getName()).color(NamedTextColor.WHITE))
                .append(Component.text(actionText).color(NamedTextColor.GRAY))
                .append(Component.text(mobName).color(mobColor))
                .append(Component.text(" (").color(NamedTextColor.GRAY))
                .append(Component.text(weight).color(Rarity.MYTHIC.getColor()).decoration(TextDecoration.BOLD, true))
                .append(Component.text(")!").color(NamedTextColor.GRAY))
                .append(plugin.getLanguageManager().getMessage("fishingutils.u2728", " \u2728").color(NamedTextColor.GOLD));

        for (Player online : plugin.getServer().getOnlinePlayers()) {
            online.sendMessage(message);
        }
    }

    /**
     * Builds a hooked message where the mob name placeholder %mob% is replaced
     * with the actual mob name colored by rarity, while the surrounding text
     * uses a consistent textColor.
     */
    public static Component buildHookedMessage(String template, String mobName, TextColor rarityColor, TextColor textColor) {
        return buildHookedMessage(template, mobName, rarityColor, "", textColor, textColor);
    }

    /**
     * Builds a hooked message where %mob% and %weight% can be colored independently.
     */
    public static Component buildHookedMessage(String template,
                                               String mobName,
                                               TextColor rarityColor,
                                               String weight,
                                               TextColor weightColor,
                                               TextColor textColor) {
        net.kyori.adventure.text.TextComponent.Builder builder = Component.text();
        int cursor = 0;
        while (cursor < template.length()) {
            int mobIdx = template.indexOf("%mob%", cursor);
            int weightIdx = template.indexOf("%weight%", cursor);
            int nextIdx = nextPlaceholderIndex(mobIdx, weightIdx);
            if (nextIdx < 0) {
                builder.append(Component.text(template.substring(cursor), textColor));
                break;
            }
            if (nextIdx > cursor) {
                builder.append(Component.text(template.substring(cursor, nextIdx), textColor));
            }
            if (nextIdx == mobIdx) {
                builder.append(Component.text(mobName, rarityColor));
                cursor = mobIdx + "%mob%".length();
            } else {
                builder.append(Component.text(weight, weightColor));
                cursor = weightIdx + "%weight%".length();
            }
        }
        return builder.build();
    }

    private static int nextPlaceholderIndex(int first, int second) {
        if (first < 0) return second;
        if (second < 0) return first;
        return Math.min(first, second);
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

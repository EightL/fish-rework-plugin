package com.fishrework.skill;

import com.fishrework.FishRework;
import com.fishrework.leveling.LevelManager;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Skill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Locale;

/**
 * Centralized XP granting and level-up ceremony.
 * ALL XP awards go through here — eliminates duplicate level-up code
 * across FishingListener, MobManager, etc.
 */
public class SkillManager {

    private final FishRework plugin;

    public SkillManager(FishRework plugin) {
        this.plugin = plugin;
    }

    /**
     * Grants XP to a player and handles all side effects:
     * - Applies XP multiplier from level bonuses
     * - Shows action bar / boss bar
     * - Handles level-up ceremony (title, sound, messages, advancement sync)
     *
     * @param player the player
     * @param skill  the skill
     * @param baseXp raw XP before multiplier
     * @param source optional label for the action bar (e.g. "Fishing", "Fishing Mob")
     * @return true if the player leveled up
     */
    public boolean grantXp(Player player, Skill skill, double baseXp, String source) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return false;

        LevelManager levelManager = plugin.getLevelManager();
        int currentLevel = data.getLevel(skill);

        // Apply XP multiplier bonus
        double multiplier = levelManager.getXpMultiplier(currentLevel);
        double finalXp = baseXp * multiplier;

        // Apply global XP multiplier from config.
        double globalMultiplier = Math.max(0.0, plugin.getConfig().getDouble("general.xp_multiplier", 1.0));
        finalXp *= globalMultiplier;

        // Apply equipment fishing XP bonus (from Scholar/Apprentice/Grand Professor sets)
        double equipXpBonus = plugin.getMobManager().getEquipmentFishingXpBonus(player);
        if (equipXpBonus > 0) {
            finalXp *= (1.0 + equipXpBonus / 100.0);
        }

        // ── QOL: Track session XP ──
        data.getSession().addXpEarned(finalXp);

        // Grant XP (per-level model: XP resets on level-up)
        int oldLevel = currentLevel;
        boolean levelUp = data.addXp(skill, finalXp, levelManager);

        // Action bar
        String actionMsg = "§b+ " + com.fishrework.util.FormatUtil.format("%.1f", finalXp) + " XP"
                + (source != null ? " (" + source + ")" : "");
        player.sendActionBar(Component.text(actionMsg));

        // Boss bar
        plugin.getBossBarManager().showBossBar(player, skill);

        // Level-up ceremony (fire for each intermediate level in case of multi-level-up)
        if (levelUp) {
            int newLevel = data.getLevel(skill);
            for (int lvl = oldLevel + 1; lvl <= newLevel; lvl++) {
                onLevelUp(player, skill, lvl, levelManager);
                data.getSession().recordLevelUp();
            }
        }

        return levelUp;
    }

    /**
     * Grants raw XP without the level-based multiplier.
     * Useful for admin commands or fixed XP rewards.
     */
    public boolean grantRawXp(Player player, Skill skill, double xp) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return false;

        int oldLevel = data.getLevel(skill);
        boolean levelUp = data.addXp(skill, xp, plugin.getLevelManager());

        plugin.getBossBarManager().showBossBar(player, skill);

        if (levelUp) {
            int newLevel = data.getLevel(skill);
            for (int lvl = oldLevel + 1; lvl <= newLevel; lvl++) {
                onLevelUp(player, skill, lvl, plugin.getLevelManager());
            }
        }

        return levelUp;
    }

    /**
     * The level-up ceremony — single source of truth.
     */
    private void onLevelUp(Player player, Skill skill, int newLevel, LevelManager levelManager) {
        // Title
        Title title = Title.title(
                plugin.getLanguageManager().getMessage("skillmanager.level_up", "LEVEL UP!").color(NamedTextColor.AQUA),
                Component.text(plugin.getLanguageManager().getString(
                                "skillmanager.title_subtitle",
                                "%skill% Level %level%",
                                "skill", skill.getLocalizedDisplayName(plugin.getLanguageManager()),
                                "level", String.valueOf(newLevel)))
                        .color(NamedTextColor.YELLOW),
                Title.Times.times(
                        Duration.ofMillis(plugin.getConfig().getLong("gui.levelup_title_fade_in_ms", 500)),
                        Duration.ofMillis(plugin.getConfig().getLong("gui.levelup_title_stay_ms", 3000)),
                        Duration.ofMillis(plugin.getConfig().getLong("gui.levelup_title_fade_out_ms", 1000)))
        );
        player.showTitle(title);

        // Sound
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // Chat messages
        player.sendMessage(plugin.getLanguageManager().getMessage("skillmanager.divider", "--------------------------------").color(NamedTextColor.DARK_AQUA));
        player.sendMessage(Component.text(plugin.getLanguageManager().getString(
                        "skillmanager.chat_level_up",
                        "   %skill% LEVEL UP! %from% ➜ %to%",
                        "skill", skill.getLocalizedDisplayName(plugin.getLanguageManager()).toUpperCase(Locale.ROOT),
                        "from", String.valueOf(newLevel - 1),
                        "to", String.valueOf(newLevel)))
                .color(NamedTextColor.AQUA));

        // Show stat gains (fishing-specific for now, extensible per-skill later)
        showStatGains(player, skill, newLevel, levelManager);

        // Show unlocked creatures & recipes
        showUnlocks(player, skill, newLevel, levelManager);

        player.sendMessage(plugin.getLanguageManager().getMessage("skillmanager.divider", "--------------------------------").color(NamedTextColor.DARK_AQUA));

        // Sync advancements & recipes
        plugin.getAdvancementManager().syncAdvancements(player, newLevel);
        // Ensure recipes are fully synced (advancements + levels)
        plugin.getRecipeRegistry().syncRecipes(player);

        // ── QOL: Level Milestone Doubloon Rewards ──
        if (skill == Skill.FISHING && newLevel % 10 == 0 && newLevel <= 50) {
            double milestoneReward = newLevel * 50.0; // 500 at 10, 1000 at 20, etc.
            PlayerData pd = plugin.getPlayerData(player.getUniqueId());
            if (pd != null) {
                pd.addBalance(milestoneReward);
                pd.getSession().addDoubloonsEarned(milestoneReward);
                String currencyName = plugin.getLanguageManager().getCurrencyName();
                player.sendMessage(Component.text(plugin.getLanguageManager().getString(
                                "skillmanager.milestone_reward",
                                "  \uD83C\uDFC6 Milestone Reward: %amount% %currency%!",
                                "amount", com.fishrework.util.FormatUtil.format("%.0f", milestoneReward),
                                "currency", currencyName))
                        .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);
            }
        }

        // ── Grant Fishing Journal at milestone levels ──
        if (skill == Skill.FISHING && (newLevel == 1 || (newLevel % 5 == 0 && newLevel <= 50))) {
            org.bukkit.inventory.ItemStack journal = plugin.getItemManager().getRequiredItem("fishing_journal");
            java.util.Map<Integer, org.bukkit.inventory.ItemStack> leftover = player.getInventory().addItem(journal);
            for (org.bukkit.inventory.ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            player.sendMessage(plugin.getLanguageManager().getMessage("skillmanager.u2b50_you_received_a_fishing", "  \u2B50 You received a Fishing Journal!").color(NamedTextColor.GREEN)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
    }

    private void showStatGains(Player player, Skill skill, int newLevel, LevelManager levelManager) {
        // TODO: Make this skill-generic via a Skill -> BonusProvider map
        // For now, fishing bonuses are the only ones
        if (skill != Skill.FISHING) return;

        double dc = levelManager.getDoubleCatchChance(newLevel) - levelManager.getDoubleCatchChance(newLevel - 1);
        double tr = levelManager.getTreasureIncrease(newLevel) - levelManager.getTreasureIncrease(newLevel - 1);
        double xp = levelManager.getXpMultiplier(newLevel) - levelManager.getXpMultiplier(newLevel - 1);

        if (dc > 0 || tr > 0 || xp > 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage("skillmanager.attributes_gained", "   Attributes Gained:").color(NamedTextColor.GOLD));
            if (dc > 0) player.sendMessage(Component.text(plugin.getLanguageManager().getString(
                            "skillmanager.double_catch_gain",
                            "    + %amount% Double Catch Chance",
                            "amount", com.fishrework.util.FormatUtil.format("%.1f%%", dc)))
                    .color(NamedTextColor.GREEN));
            if (tr > 0) player.sendMessage(Component.text(plugin.getLanguageManager().getString(
                            "skillmanager.treasure_chance_gain",
                            "    + %amount% Treasure Chance",
                            "amount", com.fishrework.util.FormatUtil.format("%.1f%%", tr)))
                    .color(NamedTextColor.GREEN));
            if (xp > 0) player.sendMessage(Component.text(plugin.getLanguageManager().getString(
                            "skillmanager.xp_multiplier_gain",
                            "    + %amount% XP Multiplier",
                            "amount", com.fishrework.util.FormatUtil.format("%.2f", xp)))
                    .color(NamedTextColor.GREEN));
        }
    }

    private void showUnlocks(Player player, Skill skill, int newLevel, LevelManager levelManager) {
        java.util.List<LevelManager.UnlockInfo> unlocks = levelManager.getUnlocksForLevel(skill, newLevel);
        if (unlocks.isEmpty()) return;

        player.sendMessage(plugin.getLanguageManager().getMessage("skillmanager.unlocked", "   Unlocked:").color(NamedTextColor.LIGHT_PURPLE));
        for (LevelManager.UnlockInfo unlock : unlocks) {
            Component icon = plugin.getLanguageManager().getMessage("skillmanager.u2605", "    \u2605 ").color(NamedTextColor.LIGHT_PURPLE);
            if (unlock.mob() != null && unlock.mob().isHostile()) {
                icon = plugin.getLanguageManager().getMessage("skillmanager.u2694", "    \u2694 ").color(plugin.getAdvancementManager().getGroupColor(unlock.mob()));
            }

            Component message = icon.append(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(unlock.text()));
            if (unlock.isRecipe() && unlock.recipe() != null && unlock.recipe().getResultId() != null) {
                message = message
                        .clickEvent(ClickEvent.runCommand("/fishing recipe " + unlock.recipe().getResultId()))
                        .hoverEvent(plugin.getLanguageManager().getMessage("skillmanager.click_to_open_recipe", "Click to open recipe").color(NamedTextColor.GREEN))
                        .decoration(TextDecoration.ITALIC, false);
            }
            player.sendMessage(message);
        }
    }
}

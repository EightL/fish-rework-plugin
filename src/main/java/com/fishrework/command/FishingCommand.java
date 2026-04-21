package com.fishrework.command;

import com.fishrework.FishRework;
import com.fishrework.gui.LeaderboardGUI;
import com.fishrework.gui.FishingSettingsGUI;
import com.fishrework.gui.RecipeBrowserGUI;
import com.fishrework.gui.RecipeGuideGUI;
import com.fishrework.gui.SkillsMenuGUI;
import com.fishrework.model.ParticleDetailMode;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Skill;
import com.fishrework.util.FeatureKeys;
import com.fishrework.util.FishingChanceSnapshotHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fishrework.model.BiomeGroup;

public class FishingCommand implements CommandExecutor, TabExecutor {

    private final FishRework plugin;
    private final Map<String, SubcommandHandler> commandHandlers = new HashMap<>();
    private final Map<String, SubcommandHandler> adminHandlers = new HashMap<>();

    @FunctionalInterface
    private interface SubcommandHandler {
        boolean handle(Player player, String[] args);
    }

    public FishingCommand(FishRework plugin) {
        this.plugin = plugin;
        registerHandlers();
    }

    private void registerHandlers() {
        registerCommand(this::handleEncyclopedia, "encyclopedia", "collection", "fish");
        registerCommand(this::handleArtifacts, "artifacts");
        registerCommand(this::handleLeaderboard, "top", "leaderboard");
        registerCommand(this::handleShop, "shop");
        registerCommand(this::handleBag, "bag");
        registerCommand(this::handleRecipe, "recipe");
        registerCommand(this::handleBalance, "balance", "bal");
        registerCommand(this::handleInfo, "info");
        registerCommand(this::handleSync, "sync");
        registerCommand(this::handleStats, "stats");
        registerCommand(this::handleSell, "sell");
        registerCommand(this::handleAutoSell, "autosell");
        registerCommand(this::handleNotifications, "notifications");
        registerCommand(this::handleSettings, "settings");
        registerCommand(this::handleXpMultiplier, "xpmultiplier");
        registerCommand(this::handleLocale, "locale", "language");
        registerCommand(this::handleReload, "reload");
        registerCommand(this::handleHelp, "help");
        registerCommand(this::handleDamageIndicator, "dmgindicator");
        registerCommand(this::handleParticles, "particles");

        registerAdminCommand(this::handleAdminAddXp, "addxp");
        registerAdminCommand(this::handleAdminSetLevel, "setlevel");
        registerAdminCommand(this::handleAdminSetChance, "setchance");
        registerAdminCommand(this::handleAdminReset, "reset");
        registerAdminCommand(this::handleAdminResetChances, "resetchances");
        registerAdminCommand(this::handleAdminGive, "give");
        registerAdminCommand(this::handleAdminSpawn, "spawn");
        registerCommand(this::handleAdminChances, "chances");
        registerAdminCommand(this::handleAdminFulfill, "fulfill");

        registerCommand(this::handleAdminHeat, "heat");
        registerAdminCommand(this::handleAdminSetHeat, "setheat");
        registerAdminCommand(this::handleAdminSetCoins, "setcoins");
    }

    private void registerCommand(SubcommandHandler handler, String... aliases) {
        for (String alias : aliases) {
            commandHandlers.put(alias, handler);
        }
    }

    private void registerAdminCommand(SubcommandHandler handler, String... aliases) {
        for (String alias : aliases) {
            adminHandlers.put(alias, handler);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.only_players_can_use_this", "Only players can use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("fishrework.use")) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.you_dont_have_permission_to", "You don't have permission to use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            new com.fishrework.gui.SkillDetailGUI(plugin, player, Skill.FISHING).open(player);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        SubcommandHandler commandHandler = commandHandlers.get(subcommand);
        if (commandHandler != null) {
            return commandHandler.handle(player, args);
        }

        SubcommandHandler adminHandler = adminHandlers.get(subcommand);
        if (adminHandler != null) {
            if (!checkAdmin(player)) {
                return true;
            }
            return adminHandler.handle(player, args);
        }

        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.unknown_subcommand_use_fishing_help", "Unknown subcommand. Use /fishing help").color(NamedTextColor.RED));
        return true;
    }

    private boolean handleEncyclopedia(Player player, String[] args) {
        if (!requireFeature(player,
                FeatureKeys.ENCYCLOPEDIA_ENABLED,
                "fishingcommand.feature_fishing_encyclopedia_disabled",
                "The Fishing Encyclopedia is disabled on this server.")) {
            return true;
        }
        new com.fishrework.gui.CollectionGui(plugin, player).open(player);
        return true;
    }

    private boolean handleArtifacts(Player player, String[] args) {
        if (!requireFeature(player,
                FeatureKeys.ARTIFACT_COLLECTION_ENABLED,
                "fishingcommand.feature_artifact_collection_disabled",
                "Artifact collection is disabled on this server.")) {
            return true;
        }
        new com.fishrework.gui.ArtifactCollectionGUI(plugin, player).open(player);
        return true;
    }

    private boolean handleLeaderboard(Player player, String[] args) {
        if (!requireFeature(player,
                FeatureKeys.LEADERBOARD_ENABLED,
                "fishingcommand.feature_leaderboard_disabled",
                "The leaderboard is disabled on this server.")) {
            return true;
        }
        new LeaderboardGUI(plugin, player, Skill.FISHING).open(player);
        return true;
    }

    private boolean handleShop(Player player, String[] args) {
        if (!requireFeature(player,
                FeatureKeys.SHOP_ENABLED,
                "fishingcommand.feature_shop_disabled",
                "The shop is disabled on this server.")) {
            return true;
        }
        new com.fishrework.gui.ShopMenuGUI(plugin, player).open(player);
        return true;
    }

    private boolean handleBag(Player player, String[] args) {
        if (!requireFeature(player,
                FeatureKeys.FISH_BAG_ENABLED,
                "fishingcommand.feature_fish_bag_disabled",
                "Fish Bags are disabled on this server.")) {
            return true;
        }
        new com.fishrework.gui.FishBagGUI(plugin, player).open(player);
        return true;
    }

    private boolean handleRecipe(Player player, String[] args) {
        if (!requireFeature(player,
                FeatureKeys.RECIPE_BROWSER_ENABLED,
                "fishingcommand.feature_recipe_browser_disabled",
                "The recipe browser is disabled on this server.")) {
            return true;
        }
        openRecipeGuide(player, args);
        return true;
    }

    private boolean handleBalance(Player player, String[] args) {
        if (!requireFeature(player,
                FeatureKeys.ECONOMY_ENABLED,
                "fishingcommand.feature_economy_disabled",
                "The economy system is disabled on this server.")) {
            return true;
        }
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        double balance = data != null ? data.getBalance() : 0;
        String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");
        player.sendMessage(plugin.getLanguageManager().getMessage(
                "fishingcommand.status_balance",
                "Balance: %balance% %currency%",
                "balance", com.fishrework.util.FormatUtil.format("%.0f", balance),
                "currency", currencyName
        ).color(NamedTextColor.GOLD));
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        new SkillsMenuGUI(plugin, player).open(player);
        return true;
    }

    private boolean handleSync(Player player, String[] args) {
        plugin.getRecipeRegistry().syncRecipes(player);
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.synced_your_recipes_and_advancements", "Synced your recipes and advancements!").color(NamedTextColor.GREEN));
        return true;
    }

    private boolean handleStats(Player player, String[] args) {
        showSessionStats(player);
        return true;
    }

    private boolean handleSell(Player player, String[] args) {
        quickSell(player);
        return true;
    }

    private boolean handleAutoSell(Player player, String[] args) {
        if (!requireFeature(player,
            FeatureKeys.AUTO_SELL_ENABLED,
            "fishingcommand.feature_autosell_disabled",
            "Auto-sell is disabled on this server.")) {
            return true;
        }
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null) {
            boolean newState = !data.getSession().isAutoSellEnabled();
            data.getSession().setAutoSellEnabled(newState);
            plugin.getDatabaseManager().saveSetting(player.getUniqueId(), "auto_sell", String.valueOf(newState));
            player.sendMessage(plugin.getLanguageManager().getMessage(
                "fishingcommand.status_autosell_now",
                "Auto-sell is now %state%!",
                "state", newState ? "ENABLED" : "DISABLED"
            ).color(newState ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (newState) {
                player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.common_fish_will_be_automatically", "Common fish will be automatically sold for Doubloons.")
                        .color(NamedTextColor.GRAY));
            }
        }
        return true;
    }

    private boolean handleNotifications(Player player, String[] args) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) {
            return true;
        }

        boolean enabled;
        if (args.length >= 2) {
            String mode = args[1].toLowerCase();
            if (!mode.equals("on") && !mode.equals("off") && !mode.equals("toggle")) {
                player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.usage_fishing_notifications_onofftoggle", "Usage: /fishing notifications [on|off|toggle]").color(NamedTextColor.RED));
                return true;
            }
            enabled = mode.equals("toggle") ? !data.isFishingTipsEnabled() : mode.equals("on");
        } else {
            enabled = !data.isFishingTipsEnabled();
        }

        data.setFishingTipsEnabled(enabled);
        plugin.getDatabaseManager().saveSetting(player.getUniqueId(), "tips_notifications", String.valueOf(enabled));
        player.sendMessage(plugin.getLanguageManager().getMessage(
            "fishingcommand.status_notifications_now",
            "Fishing tip notifications are now %state%.",
            "state", enabled ? "ENABLED" : "DISABLED"
        ).color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
        return true;
    }

    private boolean handleSettings(Player player, String[] args) {
        new FishingSettingsGUI(plugin, player,
                reopenedPlayer -> new com.fishrework.gui.SkillDetailGUI(plugin, reopenedPlayer, Skill.FISHING).open(reopenedPlayer))
                .open(player);
        return true;
    }

    private boolean handleXpMultiplier(Player player, String[] args) {
        if (args.length == 1) {
            double current = plugin.getConfig().getDouble("general.xp_multiplier", 1.0);
            player.sendMessage(plugin.getLanguageManager().getMessage(
                "fishingcommand.status_xpmultiplier_current",
                "Global XP multiplier: x%value%",
                "value", com.fishrework.util.FormatUtil.format("%.2f", current)
            ).color(NamedTextColor.AQUA));
            return true;
        }

        if (!checkAdmin(player)) {
            return true;
        }

        try {
            double requested = Double.parseDouble(args[1]);
            double maxMultiplier = plugin.getConfig().getDouble("general.xp_multiplier_max", 100.0);
            if (requested < 0.0 || requested > maxMultiplier) {
                player.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishingcommand.status_xpmultiplier_range",
                    "Multiplier must be between 0.0 and %max%.",
                    "max", com.fishrework.util.FormatUtil.format("%.1f", maxMultiplier)
                ).color(NamedTextColor.RED));
                return true;
            }

            plugin.getConfig().set("general.xp_multiplier", requested);
            plugin.saveConfig();

            player.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishingcommand.status_xpmultiplier_set",
                    "Set global XP multiplier to x%value%",
                    "value", com.fishrework.util.FormatUtil.format("%.2f", requested)
            ).color(NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.usage_fishing_xpmultiplier_value", "Usage: /fishing xpmultiplier [value]").color(NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleLocale(Player player, String[] args) {
        if (!checkAdmin(player)) {
            return true;
        }

        if (args.length < 2) {
            String current = plugin.getConfig().getString("locale", "en");
            player.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishingcommand.locale_usage",
                    "Usage: /fishing locale <code> (current: %locale%)",
                    "locale", current
            ).color(NamedTextColor.YELLOW));
            return true;
        }

        String locale = args[1].toLowerCase();
        String fileName = "lang_" + locale + ".yml";
        File externalFile = new File(plugin.getDataFolder(), fileName);
        boolean bundled = plugin.getResource(fileName) != null;
        if (!externalFile.exists() && !bundled) {
            player.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishingcommand.locale_not_found",
                    "Language file not found: %file%",
                    "file", fileName
            ).color(NamedTextColor.RED));
            return true;
        }

        plugin.getConfig().set("locale", locale);
        plugin.saveConfig();
        plugin.reload();
        player.sendMessage(plugin.getLanguageManager().getMessage(
                "fishingcommand.locale_updated",
                "Language updated to %locale%.",
                "locale", locale
        ).color(NamedTextColor.GREEN));
        return true;
    }

    private boolean handleReload(Player player, String[] args) {
        if (!player.hasPermission("fishrework.reload") && !player.hasPermission("fishrework.admin")) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.you_dont_have_permission_to", "You don't have permission to reload the config.").color(NamedTextColor.RED));
            return true;
        }
        plugin.reload();
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fish_rework_config_reloaded_successfully", "[Fish Rework] Config reloaded successfully!").color(NamedTextColor.GREEN));
        return true;
    }

    private boolean handleHelp(Player player, String[] args) {
        sendHelp(player);
        return true;
    }

    private boolean handleDamageIndicator(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.usage_fishing_dmgindicator_onoff", "Usage: /fishing dmgindicator <on|off>").color(NamedTextColor.RED));
            return true;
        }
        boolean enabled = args[1].equalsIgnoreCase("on");
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        data.setDamageIndicatorsEnabled(enabled);
        plugin.getDatabaseManager().saveSetting(player.getUniqueId(), "dmg_indicator", String.valueOf(enabled));
        player.sendMessage(plugin.getLanguageManager().getMessage(
            "fishingcommand.status_damage_indicators_now",
            "Damage indicators are now %state%.",
            "state", enabled ? "enabled" : "disabled"
        ).color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
        return true;
    }

    private boolean handleParticles(Player player, String[] args) {
        String[] particleArgs = args.length >= 2 ? java.util.Arrays.copyOfRange(args, 1, args.length) : new String[0];
        return handleParticleModeCommand(player, particleArgs);
    }

    private boolean handleAdminAddXp(Player player, String[] args) {
        adminAddXp(player, args);
        return true;
    }

    private boolean handleAdminSetLevel(Player player, String[] args) {
        adminSetLevel(player, args);
        return true;
    }

    private boolean handleAdminSetChance(Player player, String[] args) {
        adminSetChance(player, args);
        return true;
    }

    private boolean handleAdminReset(Player player, String[] args) {
        adminReset(player, args);
        return true;
    }

    private boolean handleAdminResetChances(Player player, String[] args) {
        adminResetChances(player);
        return true;
    }

    private boolean handleAdminGive(Player player, String[] args) {
        adminGive(player, args);
        return true;
    }

    private boolean handleAdminSpawn(Player player, String[] args) {
        adminSpawn(player, args);
        return true;
    }

    private boolean handleAdminChances(Player player, String[] args) {
        adminChances(player, args);
        return true;
    }

    private boolean handleAdminFulfill(Player player, String[] args) {
        adminFulfill(player, args);
        return true;
    }



    private boolean handleAdminHeat(Player player, String[] args) {
        adminHeat(player, args);
        return true;
    }

    private boolean handleAdminSetHeat(Player player, String[] args) {
        adminSetHeat(player, args);
        return true;
    }

    private boolean handleAdminSetCoins(Player player, String[] args) {
        adminSetCoins(player, args);
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishrework_help", "--- FishRework Help ---").color(NamedTextColor.GOLD));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing", "/fishing").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.open_main_menu", " - Open main menu").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_top", "/fishing top").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.view_leaderboard", " - View leaderboard").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_info", "/fishing info").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.open_the_skills_overview_menu", " - Open the skills overview menu").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_encyclopedia", "/fishing encyclopedia").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.view_fish_encyclopedia", " - View fish encyclopedia").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_artifacts", "/fishing artifacts").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.view_artifact_encyclopedia", " - View artifact encyclopedia").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_sync", "/fishing sync").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.fix_missing_recipes", " - Fix missing recipes").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_recipe_itemid", "/fishing recipe [item_id]").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.open_the_recipe_browser_for", " - Open the recipe browser for a held/custom item").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_shop", "/fishing shop").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.open_the_fishing_shop", " - Open the fishing shop").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_bag", "/fishing bag").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.open_your_fish_bag", " - Open your fish bag").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_balance", "/fishing balance").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.check_your_doubloons_balance", " - Check your Doubloons balance").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_stats", "/fishing stats").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.view_session_statistics", " - View session statistics").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_sell", "/fishing sell").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.quick_sell_all_fish_from", " - Quick sell all fish from inventory").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_autosell", "/fishing autosell").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.toggle_autosell_for_common_fish", " - Toggle auto-sell for common fish").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_settings", "/fishing settings").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.open_fishing_settings", " - Open fishing settings").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_notifications_onofftoggle", "/fishing notifications [on|off|toggle]").color(NamedTextColor.YELLOW)
            .append(plugin.getLanguageManager().getMessage("fishingcommand.toggle_fishing_tip_notifications", " - Toggle fishing tip notifications").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_xpmultiplier_value", "/fishing xpmultiplier [value]").color(NamedTextColor.YELLOW)
            .append(plugin.getLanguageManager().getMessage("fishingcommand.viewset_global_xp_multiplier", " - View/set global XP multiplier").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_locale_code", "/fishing locale <code>").color(NamedTextColor.YELLOW)
            .append(plugin.getLanguageManager().getMessage("fishingcommand.change_plugin_language", " - Change plugin language").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_dmgindicator_onoff", "/fishing dmgindicator <on|off>").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.toggle_damage_indicators", " - Toggle damage indicators").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_particles_highmediumlow", "/fishing particles [high|medium|low]").color(NamedTextColor.YELLOW)
            .append(plugin.getLanguageManager().getMessage("fishingcommand.set_sea_creature_effect_detail", " - Set sea creature effect detail").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fs_particles_highmediumlow", "/fs particles <high|medium|low>").color(NamedTextColor.YELLOW)
            .append(plugin.getLanguageManager().getMessage("fishingcommand.quick_particle_detail_shortcut", " - Quick particle detail shortcut").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_chances", "/fishing chances").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.view_biome_spawn_chances_at", " - View biome spawn chances at your location").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_heat", "/fishing heat").color(NamedTextColor.YELLOW)
                .append(plugin.getLanguageManager().getMessage("fishingcommand.view_your_lava_heat_stats", " - View your lava heat stats").color(NamedTextColor.GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.milestone_fishing_level_27", "Milestone: Fishing Level 27").color(NamedTextColor.GOLD)
            .append(plugin.getLanguageManager().getMessage("fishingcommand.unlocks_lava_fishing__magma", " unlocks Lava Fishing + Magma Satchel in shop").color(NamedTextColor.GRAY)));

        if (player.hasPermission("fishrework.admin")) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.admin_commands", "--- Admin Commands ---").color(NamedTextColor.RED));
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_addxp_player_amount", "/fishing addxp <player> <amount>").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("fishingcommand.give_xp_to_player", " - Give XP to player").color(NamedTextColor.GRAY)));
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_setlevel_player_level", "/fishing setlevel <player> <level>").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("fishingcommand.set_player_level", " - Set player level").color(NamedTextColor.GRAY)));
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_setchance_mobartifact_chance", "/fishing setchance <mob|artifact> <chance>").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("fishingcommand.set_mobartifact_spawn_chance", " - Set mob/artifact spawn chance").color(NamedTextColor.GRAY)));
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_resetchances", "/fishing resetchances").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("fishingcommand.reset_all_chances_to_defaults", " - Reset all chances to defaults").color(NamedTextColor.GRAY)));
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_reset_player", "/fishing reset <player>").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("fishingcommand.reset_player_data", " - Reset player data").color(NamedTextColor.GRAY)));
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_give_player_item_count", "/fishing give <player> <item> [count]").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("fishingcommand.give_custom_item", " - Give custom item").color(NamedTextColor.GRAY)));
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_spawn_mob", "/fishing spawn <mob>").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("fishingcommand.spawn_mob_at_location", " - Spawn mob at location").color(NamedTextColor.GRAY)));
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_fulfill_player_allcreaturesartifacts", "/fishing fulfill <player> <all|creatures|artifacts>").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("fishingcommand.unlock_all_creaturesartifacts_for_a", " - Unlock all creatures/artifacts for a player").color(NamedTextColor.GRAY)));
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_heat_player", "/fishing heat <player>").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("fishingcommand.view_another_players_heat_stats", " - View another player's heat stats").color(NamedTextColor.GRAY)));
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_setheat_player_amount", "/fishing setheat <player> <amount>").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("fishingcommand.set_heat_clamped_to_0100", " - Set heat (clamped to 0-100)").color(NamedTextColor.GRAY)));
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_setcoins_player_amount", "/fishing setcoins <player> <amount>").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("fishingcommand.set_doubloons_balance", " - Set doubloons balance").color(NamedTextColor.GRAY)));
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_xpmultiplier_value", "/fishing xpmultiplier <value>").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("fishingcommand.set_global_xp_multiplier", " - Set global XP multiplier").color(NamedTextColor.GRAY)));
                player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_locale_code", "/fishing locale <code>").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("fishingcommand.set_plugin_language", " - Set plugin language").color(NamedTextColor.GRAY)));

            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_reload", "/fishing reload").color(NamedTextColor.YELLOW)
                    .append(plugin.getLanguageManager().getMessage("fishingcommand.reload_configyml_without_restart", " - Reload config.yml without restart").color(NamedTextColor.GRAY)));
        }
    }

    private boolean handleParticleModeCommand(Player player, String[] args) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return true;

        if (args.length != 1) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.usage_fishing_particles_highmediumlow", "Usage: /fishing particles <high|medium|low>").color(NamedTextColor.RED));
            return true;
        }

        ParticleDetailMode mode = ParticleDetailMode.fromInput(args[0]);
        if (mode == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.unknown_mode_use_high_medium", "Unknown mode. Use high, medium, or low.").color(NamedTextColor.RED));
            return true;
        }

        data.setParticleDetailMode(mode);
        plugin.getDatabaseManager().saveSetting(player.getUniqueId(), "particle_mode", mode.getId());

        NamedTextColor color = switch (mode) {
            case HIGH -> NamedTextColor.GREEN;
            case MEDIUM -> NamedTextColor.YELLOW;
            case LOW -> NamedTextColor.RED;
        };

        player.sendMessage(plugin.getLanguageManager().getMessage(
            "fishingcommand.status_particles_set",
            "Sea creature particles set to %mode%.",
            "mode", mode.getId().toUpperCase()
        ).color(color));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.use_fs_particles_highmediumlow_to", "Use /fs particles <high|medium|low> to change this later.")
                .color(NamedTextColor.GRAY));
        return true;
    }

    private void showSessionStats(Player player) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;
        com.fishrework.model.FishingSession session = data.getSession();
        String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");

        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_session_stats", "--- Fishing Session Stats ---").color(NamedTextColor.GOLD));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.total_catches", "  Total Catches: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(session.getTotalCatches())).color(NamedTextColor.WHITE)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.mobs_killed", "  Mobs Killed: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(session.getMobsKilled())).color(NamedTextColor.RED)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.treasures_found", "  Treasures Found: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(session.getTreasuresFound())).color(NamedTextColor.GOLD)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.xp_earned", "  XP Earned: ").color(NamedTextColor.GRAY)
                .append(Component.text(com.fishrework.util.FormatUtil.format("%.0f", session.getXpEarned())).color(NamedTextColor.AQUA)));
        player.sendMessage(plugin.getLanguageManager().getMessage(
            "fishingcommand.currency_earned",
            "  %currency% Earned: ",
            "currency", currencyName
        ).color(NamedTextColor.GRAY)
                .append(Component.text(com.fishrework.util.FormatUtil.format("%.0f", session.getDoubloonsEarned())).color(NamedTextColor.GREEN)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.levels_gained", "  Levels Gained: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(session.getLevelsGained())).color(NamedTextColor.YELLOW)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.new_discoveries", "  New Discoveries: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(session.getNewDiscoveries())).color(NamedTextColor.LIGHT_PURPLE)));

        // Streak info
        if (session.getBestStreak() > 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.current_streak", "  Current Streak: ").color(NamedTextColor.GRAY)
                    .append(Component.text("x" + session.getCurrentStreak()).color(NamedTextColor.GOLD)));
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.best_streak", "  Best Streak: ").color(NamedTextColor.GRAY)
                    .append(Component.text("x" + session.getBestStreak()).color(NamedTextColor.GOLD)));
        }

        // Auto-sell status
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.autosell", "  Auto-Sell: ").color(NamedTextColor.GRAY)
                .append(Component.text(session.isAutoSellEnabled() ? "ON" : "OFF")
                        .color(session.isAutoSellEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.separator", "----------------------------").color(NamedTextColor.GOLD));
    }

    private void openRecipeGuide(Player player, String[] args) {
        List<com.fishrework.registry.RecipeDefinition> recipes;

        if (args.length >= 2) {
            recipes = plugin.getRecipeRegistry().getRecipesForResultId(args[1].toLowerCase());
            if (recipes.isEmpty()) {
                player.sendMessage(plugin.getLanguageManager().getMessage(
                        "fishingcommand.recipe_not_found_for",
                        "No recipe GUI found for '%item%'.",
                        "item", args[1]
                ).color(NamedTextColor.RED));
                return;
            }
        } else {
            recipes = plugin.getRecipeRegistry().getRecipesForResultItem(player.getInventory().getItemInMainHand());
            if (recipes.isEmpty()) {
                new RecipeBrowserGUI(plugin, player).open(player);
                return;
            }
        }

        new RecipeGuideGUI(plugin, player, recipes).open(player);
    }

    private void quickSell(Player player) {
        String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");
        double totalEarnings = 0;
        int totalItems = 0;

        org.bukkit.inventory.ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            org.bukkit.inventory.ItemStack item = contents[i];
            if (item == null) continue;
            double price = plugin.getItemManager().getSellPrice(item);
            if (price > 0) {
                totalEarnings += price * item.getAmount();
                totalItems += item.getAmount();
                player.getInventory().setItem(i, null);
            }
        }

        if (totalItems > 0) {
            PlayerData data = plugin.getPlayerData(player.getUniqueId());
            if (data != null) {
                data.addBalance(totalEarnings);
                data.getSession().addDoubloonsEarned(totalEarnings);
                plugin.getDatabaseManager().saveBalance(player.getUniqueId(), data.getBalance());
            }
            player.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishingcommand.sold_items_for_currency",
                    "Sold %count% items for %amount% %currency%!",
                    "count", String.valueOf(totalItems),
                    "amount", com.fishrework.util.FormatUtil.format("%.0f", totalEarnings),
                    "currency", currencyName
            ).color(NamedTextColor.GREEN));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.2f);
        } else {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.you_dont_have_anything_to", "You don't have anything to sell!")
                    .color(NamedTextColor.RED));
        }
    }

    private boolean checkAdmin(Player player) {
        if (!player.hasPermission("fishrework.admin")) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.no_permission", "No permission.").color(NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private boolean requireFeature(Player player, String featureKey, String disabledMessageKey, String disabledMessageFallback) {
        if (plugin.isFeatureEnabled(featureKey)) {
            return true;
        }
        player.sendMessage(plugin.getLanguageManager().getMessage(disabledMessageKey, disabledMessageFallback).color(NamedTextColor.RED));
        return false;
    }


    private void adminAddXp(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.usage_fishing_addxp_player_amount", "Usage: /fishing addxp <player> <amount>").color(NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.player_not_found", "Player not found.").color(NamedTextColor.RED)); return; }

        try {
            Skill skill = Skill.FISHING;
            double amount = Double.parseDouble(args[2]);

            if (amount < 0 || amount > 1_000_000) {
                player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.amount_must_be_between_0", "Amount must be between 0 and 1,000,000.").color(NamedTextColor.RED));
                return;
            }

            boolean levelUp = plugin.getSkillManager().grantRawXp(target, skill, amount);
            player.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishingcommand.added_xp_to_player",
                    "Added %amount% XP to %player%",
                    "amount", com.fishrework.util.FormatUtil.format("%.0f", amount),
                    "player", target.getName()
            ).color(NamedTextColor.GREEN));
            if (levelUp) target.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.you_leveled_up_from_admin", "You leveled up from admin command!").color(NamedTextColor.GOLD));
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.invalid_number", "Invalid number.").color(NamedTextColor.RED));
        }
    }

    private void adminSetLevel(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.usage_fishing_setlevel_player_level", "Usage: /fishing setlevel <player> <level>").color(NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.player_not_found", "Player not found.").color(NamedTextColor.RED)); return; }

        try {
            Skill skill = Skill.FISHING;
            int level = Integer.parseInt(args[2]);

            if (level < 0 || level > plugin.getLevelManager().getMaxLevel()) {
                player.sendMessage(plugin.getLanguageManager().getMessage(
                        "fishingcommand.level_must_be_between_0_max",
                        "Level must be 0-%max%",
                        "max", String.valueOf(plugin.getLevelManager().getMaxLevel())
                ).color(NamedTextColor.RED));
                return;
            }

            PlayerData data = plugin.getPlayerData(target.getUniqueId());
            data.setLevel(skill, level);
            data.setXp(skill, 0); // Per-level XP: reset to 0 at the new level

                player.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishingcommand.set_player_skill_level",
                    "Set %player%'s %skill% to level %level%",
                    "player", target.getName(),
                    "skill", skill.name(),
                    "level", String.valueOf(level)
                ).color(NamedTextColor.GREEN));
                target.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishingcommand.your_skill_level_set",
                    "Your %skill% level was set to %level%",
                    "skill", skill.name(),
                    "level", String.valueOf(level)
                ).color(NamedTextColor.GOLD));

            plugin.getAdvancementManager().syncAdvancements(target, level);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.invalid_number", "Invalid number.").color(NamedTextColor.RED));
        }
    }

    private void adminSetChance(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.usage_fishing_setchance_mobartifact_chance", "Usage: /fishing setchance <mob|artifact> <chance>").color(NamedTextColor.RED));
            return;
        }

        String target = args[1].toLowerCase();
        
        // Handle artifact chance separately
        if (target.equals("artifact")) {
            try {
                double chance = Double.parseDouble(args[2]);
                if (chance < 0.0 || chance > 100.0) {
                    player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.chance_must_be_between_00", "Chance must be between 0.0 and 100.0.").color(NamedTextColor.RED));
                    return;
                }
                double oldChance = plugin.getConfig().getDouble("artifacts.chance", 3.0);
                plugin.getConfig().set("artifacts.chance", chance);
                plugin.saveConfig();
                player.sendMessage(plugin.getLanguageManager().getMessage(
                        "fishingcommand.updated_artifact_chance",
                        "Updated artifact chance: %old%% -> %new%%",
                        "old", com.fishrework.util.FormatUtil.format("%.1f", oldChance),
                        "new", com.fishrework.util.FormatUtil.format("%.1f", chance)
                ).color(NamedTextColor.GREEN));
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.invalid_number", "Invalid number.").color(NamedTextColor.RED));
            }
            return;
        }

        List<String> known = new ArrayList<>(plugin.getMobRegistry().getAllIds());
        known.add("land_mob_bonus");
        
        if (!known.contains(target)) {
            player.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishingcommand.unknown_mob_available_list",
                    "Unknown mob. Available: %mobs%",
                    "mobs", String.join(", ", known)
            ).color(NamedTextColor.RED));
            return;
        }

        try {
            double chance = Double.parseDouble(args[2]);
            if (chance < 0.0 || chance > 100.0) {
                player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.chance_must_be_between_00", "Chance must be between 0.0 and 100.0.").color(NamedTextColor.RED));
                return;
            }
            double oldChance = plugin.getMobManager().getMobChance(target, -1);
            
            plugin.getMobManager().setMobChance(target, chance);
            player.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishingcommand.updated_mob_chance",
                    "Updated %mob% chance: %old%% -> %new%%",
                    "mob", target,
                    "old", com.fishrework.util.FormatUtil.format("%.1f", oldChance),
                    "new", com.fishrework.util.FormatUtil.format("%.1f", chance)
            ).color(NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.invalid_number", "Invalid number.").color(NamedTextColor.RED));
        }
    }

    private void adminReset(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.usage_fishing_reset_player", "Usage: /fishing reset <player>").color(NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.player_not_found_online", "Player not found online.").color(NamedTextColor.RED)); return; }

        plugin.getDatabaseManager().resetData(target.getUniqueId());
        PlayerData data = plugin.getPlayerData(target.getUniqueId());
        if (data != null) {
            data.reset();
        }

        plugin.getRecipeRegistry().syncRecipes(target);
        target.closeInventory();

        player.sendMessage(plugin.getLanguageManager().getMessage(
            "fishingcommand.reset_all_data_for_player",
            "Reset all data for %player%",
            "player", target.getName()
        ).color(NamedTextColor.GREEN));
        target.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.your_skills_and_collection_have", "Your skills and collection have been reset.").color(NamedTextColor.RED));
    }

    private void adminGive(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.usage_fishing_give_player_item", "Usage: /fishing give <player> <item> [count]").color(NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.player_not_found", "Player not found.").color(NamedTextColor.RED)); return; }

        String itemName = args[2].toLowerCase();
        org.bukkit.inventory.ItemStack item = plugin.getItemManager().getItem(itemName);

        if (item == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.unknown_item_use_tab_completion", "Unknown item. Use tab completion.").color(NamedTextColor.RED));
            return;
        }

        int count = 1;
        if (args.length >= 4) {
            try {
                count = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
                player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.count_must_be_a_number", "Count must be a number.").color(NamedTextColor.RED));
                return;
            }
        }

        if (count <= 0 || count > 10_000) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.count_must_be_between_1", "Count must be between 1 and 10,000.").color(NamedTextColor.RED));
            return;
        }

        int remaining = count;
        while (remaining > 0) {
            ItemStack stack = item.clone();
            int amount = Math.min(stack.getMaxStackSize(), remaining);
            stack.setAmount(amount);
            java.util.Map<Integer, ItemStack> overflow = target.getInventory().addItem(stack);
            for (ItemStack leftover : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            }
            remaining -= amount;
        }

        String displayItemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ?
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()) : itemName;

        player.sendMessage(plugin.getLanguageManager().getMessage(
                "fishingcommand.gave_item_to_player",
                "Gave %count%x %item% to %player%",
                "count", String.valueOf(count),
                "item", displayItemName,
                "player", target.getName()
        ).color(NamedTextColor.GREEN));
    }

    private void adminSpawn(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.usage_fishing_spawn_mob", "Usage: /fishing spawn <mob>").color(NamedTextColor.RED));
            return;
        }

        String mobId = args[1].toLowerCase();
        com.fishrework.model.CustomMob mob = plugin.getMobRegistry().get(mobId);
        if (mob == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.unknown_mob_id", "Unknown mob ID.").color(NamedTextColor.RED));
            return;
        }

        if (mob.isTreasure()) {
            player.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishingcommand.treasure_chest_spawn_denied",
                    "Treasure chests cannot be spawned. Use /fishing give <player> treasure_chest_%id% instead.",
                    "id", mobId.replace("treasure_", "")
            ).color(NamedTextColor.RED));
            return;
        }

        plugin.getMobManager().spawnMob(player.getLocation(), mobId);
        player.sendMessage(plugin.getLanguageManager().getMessage(
                "fishingcommand.spawned_mob",
                "Spawned %mob%",
                "mob", mob.getLocalizedDisplayName(plugin.getLanguageManager())
        ).color(NamedTextColor.GREEN));
    }


    private void adminResetChances(Player player) {
        for (com.fishrework.model.CustomMob mob : plugin.getMobRegistry().getAll()) {
            plugin.getConfig().set("mobs." + mob.getId() + ".chance", mob.getDefaultChance());
        }
        // Also reset land_mob_bonus
        plugin.getConfig().set("mobs.land_mob_bonus.chance", null);
        // Reset artifact chance
        plugin.getConfig().set("artifacts.chance", null);
        
        plugin.saveConfig();
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.all_mob_chances_land_mob", "All mob chances, land mob bonus, and artifact chance reset to defaults!").color(NamedTextColor.GREEN));
    }

    private void adminChances(Player player, String[] args) {
        Skill skill = Skill.FISHING;

        FishingChanceSnapshotHelper.ChanceSnapshot snapshot =
                FishingChanceSnapshotHelper.capture(plugin, player, skill);
        java.util.Map<String, Double> chances = snapshot.chances();
        java.util.Map<String, Double> rawWeights = snapshot.rawWeights();

        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.fishing_spawn_chances", "--- Fishing Spawn Chances ---").color(NamedTextColor.GOLD));
        player.sendMessage(plugin.getLanguageManager().getMessage(
            "fishingcommand.chances_biome",
            "Biome: %biome%",
            "biome", snapshot.biomeGroup().name()
        ).color(NamedTextColor.GRAY));

        if (snapshot.activeBaitId() != null && !snapshot.baitAppliesToContext()) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.held_bait_is_inactive_in", "Held bait is inactive in this dimension.")
                    .color(NamedTextColor.RED));
        }

        if (snapshot.baitRareCreatureBonus() > 0 || snapshot.baitTreasureBonus() > 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage(
                "fishingcommand.chances_active_bait_bonus",
                "Active Bait Bonus: +%rare%% Rare, +%treasure%% Treasure",
                "rare", com.fishrework.util.FormatUtil.format("%.1f", snapshot.baitRareCreatureBonus()),
                "treasure", com.fishrework.util.FormatUtil.format("%.1f", snapshot.baitTreasureBonus())
            ).color(NamedTextColor.AQUA));
        }
        if (!snapshot.baitTargetMobIds().isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getMessage(
                "fishingcommand.chances_biome_bait_targets",
                "Biome Bait Targets: %targets%",
                "targets", String.join(", ", snapshot.baitTargetMobIds())
            ).color(NamedTextColor.AQUA));
        }

        // Sort by chance descending
        chances.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .forEach(entry -> {
                    String name = entry.getKey();
                    double chance = entry.getValue();
                    double weight = rawWeights.getOrDefault(name, 0.0);
                    
                    NamedTextColor color = NamedTextColor.AQUA;
                    if (name.equals("land_mob_bonus")) {
                        color = NamedTextColor.GREEN;
                    } else {
                        com.fishrework.model.CustomMob mob = plugin.getMobRegistry().get(name);
                        if (mob != null && mob.isHostile()) {
                            color = NamedTextColor.RED;
                        }
                    }
                    
                    if (name.equals("land_mob_bonus")) {
                         player.sendMessage(Component.text(com.fishrework.util.FormatUtil.format("- %s: %.2f%%", name, chance)).color(color));
                    } else {
                         player.sendMessage(Component.text(com.fishrework.util.FormatUtil.format("- %s: %.2f%% (Weight: %.1f)", name, chance, weight)).color(color));
                    }
                });
    }

    private void adminFulfill(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.usage_fishing_fulfill_player_allcreaturesartifacts", "Usage: /fishing fulfill <player> <all|creatures|artifacts>").color(NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.player_not_found", "Player not found.").color(NamedTextColor.RED));
            return;
        }

        String type = args[2].toLowerCase();
        PlayerData data = plugin.getPlayerData(target.getUniqueId());
        
        boolean doCreatures = type.equals("all") || type.equals("creatures") || type.equals("fish");
        boolean doArtifacts = type.equals("all") || type.equals("artifacts");

        if (!doCreatures && !doArtifacts) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.unknown_type_use_all_creatures", "Unknown type. Use all, creatures, or artifacts.").color(NamedTextColor.RED));
            return;
        }

        if (doCreatures) {
            for (String mobId : plugin.getMobRegistry().getAllIds()) {
                if (!data.hasCaughtMob(mobId)) {
                    data.addCaughtMob(mobId);
                    plugin.getDatabaseManager().updateCollection(target.getUniqueId(), mobId, 1.0);
                }
            }
            player.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishingcommand.unlocked_all_creatures_for_player",
                    "Unlocked all creatures for %player%",
                    "player", target.getName()
            ).color(NamedTextColor.GREEN));
        }

        if (doArtifacts) {
             for (String artId : plugin.getArtifactRegistry().getAllIds()) {
                if (!data.hasArtifact(artId)) {
                    data.addArtifact(artId);
                    plugin.getDatabaseManager().saveArtifact(target.getUniqueId(), artId);
                }
            }
            player.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishingcommand.unlocked_all_artifacts_for_player",
                    "Unlocked all artifacts for %player%",
                    "player", target.getName()
            ).color(NamedTextColor.GREEN));
        }
        
        // Sync to trigger advancements
        plugin.getAdvancementManager().syncAdvancements(target, data.getLevel(Skill.FISHING));
    }

    private void adminHeat(Player player, String[] args) {
        Player target = player;
        if (args.length > 1) {
            if (!player.hasPermission("fishrework.admin")) {
                player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.no_permission", "No permission.").color(NamedTextColor.RED));
                return;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.player_not_found", "Player not found.").color(NamedTextColor.RED));
                return;
            }
        }

        PlayerData data = plugin.getPlayerData(target.getUniqueId());
        com.fishrework.manager.HeatManager heatManager = plugin.getHeatManager();
        com.fishrework.manager.HeatManager.HeatTier tier = heatManager.getHeatTier(target);
        double equipmentHeatResist = plugin.getMobManager().getEquipmentBonus(target, plugin.getItemManager().HEAT_RESISTANCE_KEY);
        double tempHeatResist = heatManager.getTemporaryHeatResistance(target);
        long magmaFilterRemaining = heatManager.getMagmaFilterRemainingSeconds(target);
        double totalHeatResist = equipmentHeatResist + tempHeatResist;

        player.sendMessage(plugin.getLanguageManager().getMessage(
            "fishingcommand.heat_debug_header",
            "--- Heat Debug (%player%) ---",
            "player", target.getName()
        ).color(NamedTextColor.GOLD));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.current_heat", "Current Heat: ").color(NamedTextColor.GRAY)
                .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f / 100.0", data.getHeat())).color(NamedTextColor.RED)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.heat_tier", "Heat Tier: ").color(NamedTextColor.GRAY)
                .append(Component.text(tier.name()).color(tier.getColor())));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.equipment_heat_resist", "Equipment Heat Resist: ").color(NamedTextColor.GRAY)
            .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f%%", equipmentHeatResist)).color(NamedTextColor.AQUA)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.magma_filter_resist", "Magma Filter Resist: ").color(NamedTextColor.GRAY)
            .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f%%", tempHeatResist)).color(NamedTextColor.AQUA))
            .append(plugin.getLanguageManager().getMessage(
                magmaFilterRemaining > 0
                    ? "fishingcommand.magma_filter_time_left"
                    : "fishingcommand.magma_filter_inactive",
                magmaFilterRemaining > 0 ? " (%seconds%s left)" : " (inactive)",
                "seconds", String.valueOf(magmaFilterRemaining)
            )
                .color(magmaFilterRemaining > 0 ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.total_heat_resist", "Total Heat Resist: ").color(NamedTextColor.GRAY)
            .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f%%", totalHeatResist)).color(NamedTextColor.GREEN)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.peak_heat_session", "Peak Heat (Session): ").color(NamedTextColor.GRAY)
                .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f", data.getSession().getPeakHeat())).color(NamedTextColor.YELLOW)));
        player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.damage_taken", "Damage Taken: ").color(NamedTextColor.GRAY)
                .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f", data.getSession().getHeatDamageTaken())).color(NamedTextColor.DARK_RED)));
    }

    private void adminSetHeat(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.usage_fishing_setheat_player_amount", "Usage: /fishing setheat <player> <amount>").color(NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.player_not_found", "Player not found.").color(NamedTextColor.RED));
            return;
        }

        PlayerData data = plugin.getPlayerData(target.getUniqueId());
        if (data == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.no_player_data_found_for", "No player data found for that player.").color(NamedTextColor.RED));
            return;
        }

        try {
            double requestedHeat = Double.parseDouble(args[2]);
            double clampedHeat = Math.max(0.0, Math.min(100.0, requestedHeat));
            data.setHeat(clampedHeat);

            plugin.getHeatManager().showHeatGauge(target, clampedHeat);

            if (requestedHeat != clampedHeat) {
                player.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishingcommand.set_player_heat_clamped",
                    "Set %player%'s heat to %value% (clamped from %requested%)",
                    "player", target.getName(),
                    "value", com.fishrework.util.FormatUtil.format("%.1f", clampedHeat),
                    "requested", com.fishrework.util.FormatUtil.format("%.1f", requestedHeat)
                ).color(NamedTextColor.YELLOW));
            } else {
                player.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishingcommand.set_player_heat",
                    "Set %player%'s heat to %value%",
                    "player", target.getName(),
                    "value", com.fishrework.util.FormatUtil.format("%.1f", clampedHeat)
                ).color(NamedTextColor.GREEN));
            }

                target.sendMessage(plugin.getLanguageManager().getMessage(
                    "fishingcommand.your_heat_set_by_admin",
                    "Your lava heat was set to %value% by an admin.",
                    "value", com.fishrework.util.FormatUtil.format("%.1f", clampedHeat)
                ).color(NamedTextColor.GOLD));
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.invalid_heat_amount_use_a", "Invalid heat amount. Use a number from 0 to 100.").color(NamedTextColor.RED));
        }
    }

    private void adminSetCoins(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.usage_fishing_setcoins_player_amount", "Usage: /fishing setcoins <player> <amount>").color(NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.player_not_found_online", "Player not found online.").color(NamedTextColor.RED));
            return;
        }

        PlayerData data = plugin.getPlayerData(target.getUniqueId());
        if (data == null) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.no_player_data_found_for", "No player data found for that player.").color(NamedTextColor.RED));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.invalid_amount", "Invalid amount.").color(NamedTextColor.RED));
            return;
        }

        if (amount < 0) {
            player.sendMessage(plugin.getLanguageManager().getMessage("fishingcommand.amount_must_be__0", "Amount must be >= 0.").color(NamedTextColor.RED));
            return;
        }

        data.setBalance(amount);
        plugin.getDatabaseManager().saveBalance(target.getUniqueId(), amount);

        String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");
        player.sendMessage(plugin.getLanguageManager().getMessage(
            "fishingcommand.set_player_balance",
            "Set %player%'s balance to %amount% %currency%.",
            "player", target.getName(),
            "amount", com.fishrework.util.FormatUtil.format("%.0f", amount),
            "currency", currencyName
        ).color(NamedTextColor.GREEN));
        target.sendMessage(plugin.getLanguageManager().getMessage(
            "fishingcommand.your_balance_set_by_admin",
            "Your balance was set to %amount% %currency% by an admin.",
            "amount", com.fishrework.util.FormatUtil.format("%.0f", amount),
            "currency", currencyName
        ).color(NamedTextColor.GOLD));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        boolean isAdmin = sender.hasPermission("fishrework.admin");

        if ("fs".equalsIgnoreCase(label)) {
            if (args.length == 1) {
                completions.add("particles");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("particles")) {
                completions.addAll(List.of("high", "medium", "low"));
            }
        }

        if (args.length == 1) {
            completions.add("top");
            completions.add("encyclopedia");
            completions.add("artifacts");
            completions.add("sync");
            completions.add("recipe");
            completions.add("shop");
            completions.add("bag");
            completions.add("balance");
            completions.add("stats");
            completions.add("sell");
            completions.add("autosell");
            completions.add("settings");
            completions.add("notifications");
            completions.add("particles");
            completions.add("xpmultiplier");
            completions.add("locale");
            completions.add("help");
            completions.addAll(List.of("chances", "heat", "dmgindicator"));
            if (isAdmin) {
                completions.addAll(List.of("addxp", "setlevel", "setchance", "resetchances", "reset", "give", "spawn", "fulfill", "setheat", "setcoins"));
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("dmgindicator")) {
                completions.addAll(List.of("on", "off"));
            } else if (args[0].equalsIgnoreCase("notifications")) {
                completions.addAll(List.of("on", "off", "toggle"));
            } else if (args[0].equalsIgnoreCase("particles")) {
                completions.addAll(List.of("high", "medium", "low"));
            } else if (args[0].equalsIgnoreCase("recipe")) {
                completions.addAll(plugin.getRecipeRegistry().getRecipeResultIds());
            } else if (args[0].equalsIgnoreCase("xpmultiplier")) {
                if (isAdmin) {
                    completions.addAll(List.of("0.5", "1.0", "1.5", "2.0"));
                }
            } else if (args[0].equalsIgnoreCase("locale") || args[0].equalsIgnoreCase("language")) {
                if (isAdmin) {
                    completions.addAll(List.of("en", "es"));
                }
            } else if (isAdmin) {
                if (args[0].equalsIgnoreCase("setchance")) {
                    completions.addAll(plugin.getMobRegistry().getAllIds());
                    completions.add("land_mob_bonus");
                    completions.add("artifact");
                } else if (args[0].equalsIgnoreCase("spawn")) {
                    completions.addAll(plugin.getMobRegistry().getAllIds().stream()
                            .filter(id -> {
                                com.fishrework.model.CustomMob mob = plugin.getMobRegistry().get(id);
                                return mob != null && !mob.isTreasure();
                            })
                            .toList());
                } else if (args[0].equalsIgnoreCase("give")) {
                    return null; // Player list
                } else if (args[0].equalsIgnoreCase("addxp") || args[0].equalsIgnoreCase("setlevel") || args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("fulfill") || args[0].equalsIgnoreCase("heat") || args[0].equalsIgnoreCase("setheat") || args[0].equalsIgnoreCase("setcoins")) {
                    return null; // Player list (admin only)

                }
            }
        } else if (args.length == 3) {
            if (!isAdmin) {
                return completions;
            } else if (args[0].equalsIgnoreCase("setchance")) {
                completions.addAll(List.of("5.0", "10.0", "25.0"));
            } else if (args[0].equalsIgnoreCase("give")) {
                completions.addAll(plugin.getItemManager().getItemNames());
            } else if (args[0].equalsIgnoreCase("addxp") || args[0].equalsIgnoreCase("setlevel")) {
                return null;
            } else if (args[0].equalsIgnoreCase("fulfill")) {
                completions.addAll(List.of("all", "creatures", "artifacts"));
            } else if (args[0].equalsIgnoreCase("setheat")) {
                completions.addAll(List.of("0", "25", "50", "75", "100"));
            } else if (args[0].equalsIgnoreCase("setcoins")) {
                completions.addAll(List.of("0", "100", "1000", "10000"));
            }
        } else if (args.length == 4 && isAdmin) {
            if (args[0].equalsIgnoreCase("give")) {
                completions.addAll(List.of("1", "8", "16", "32", "64"));
            }
        }

        return completions;
    }
}

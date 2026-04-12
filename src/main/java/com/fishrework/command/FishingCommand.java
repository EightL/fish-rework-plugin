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
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("fishrework.use")) {
            player.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
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

        player.sendMessage(Component.text("Unknown subcommand. Use /fishing help").color(NamedTextColor.RED));
        return true;
    }

    private boolean handleEncyclopedia(Player player, String[] args) {
        if (!requireFeature(player, FeatureKeys.ENCYCLOPEDIA_ENABLED,
                "The Fishing Encyclopedia is disabled on this server.")) {
            return true;
        }
        new com.fishrework.gui.CollectionGui(plugin, player).open(player);
        return true;
    }

    private boolean handleArtifacts(Player player, String[] args) {
        if (!requireFeature(player, FeatureKeys.ARTIFACT_COLLECTION_ENABLED,
                "Artifact collection is disabled on this server.")) {
            return true;
        }
        new com.fishrework.gui.ArtifactCollectionGUI(plugin, player).open(player);
        return true;
    }

    private boolean handleLeaderboard(Player player, String[] args) {
        if (!requireFeature(player, FeatureKeys.LEADERBOARD_ENABLED,
                "The leaderboard is disabled on this server.")) {
            return true;
        }
        new LeaderboardGUI(plugin, player, Skill.FISHING).open(player);
        return true;
    }

    private boolean handleShop(Player player, String[] args) {
        if (!requireFeature(player, FeatureKeys.SHOP_ENABLED,
                "The shop is disabled on this server.")) {
            return true;
        }
        new com.fishrework.gui.ShopMenuGUI(plugin, player).open(player);
        return true;
    }

    private boolean handleBag(Player player, String[] args) {
        if (!requireFeature(player, FeatureKeys.FISH_BAG_ENABLED,
                "Fish Bags are disabled on this server.")) {
            return true;
        }
        new com.fishrework.gui.FishBagGUI(plugin, player).open(player);
        return true;
    }

    private boolean handleRecipe(Player player, String[] args) {
        if (!requireFeature(player, FeatureKeys.RECIPE_BROWSER_ENABLED,
                "The recipe browser is disabled on this server.")) {
            return true;
        }
        openRecipeGuide(player, args);
        return true;
    }

    private boolean handleBalance(Player player, String[] args) {
        if (!requireFeature(player, FeatureKeys.ECONOMY_ENABLED,
                "The economy system is disabled on this server.")) {
            return true;
        }
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        double balance = data != null ? data.getBalance() : 0;
        String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");
        player.sendMessage(Component.text("Balance: " + com.fishrework.util.FormatUtil.format("%.0f", balance) + " " + currencyName)
                .color(NamedTextColor.GOLD));
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        new SkillsMenuGUI(plugin, player).open(player);
        return true;
    }

    private boolean handleSync(Player player, String[] args) {
        plugin.getRecipeRegistry().syncRecipes(player);
        player.sendMessage(Component.text("Synced your recipes and advancements!").color(NamedTextColor.GREEN));
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
        if (!requireFeature(player, FeatureKeys.AUTO_SELL_ENABLED,
                "Auto-sell is disabled on this server.")) {
            return true;
        }
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null) {
            boolean newState = !data.getSession().isAutoSellEnabled();
            data.getSession().setAutoSellEnabled(newState);
            plugin.getDatabaseManager().saveSetting(player.getUniqueId(), "auto_sell", String.valueOf(newState));
            player.sendMessage(Component.text("Auto-sell is now " + (newState ? "ENABLED" : "DISABLED") + "!")
                    .color(newState ? NamedTextColor.GREEN : NamedTextColor.RED));
            if (newState) {
                player.sendMessage(Component.text("Common fish will be automatically sold for Doubloons.")
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
                player.sendMessage(Component.text("Usage: /fishing notifications [on|off|toggle]").color(NamedTextColor.RED));
                return true;
            }
            enabled = mode.equals("toggle") ? !data.isFishingTipsEnabled() : mode.equals("on");
        } else {
            enabled = !data.isFishingTipsEnabled();
        }

        data.setFishingTipsEnabled(enabled);
        plugin.getDatabaseManager().saveSetting(player.getUniqueId(), "tips_notifications", String.valueOf(enabled));
        player.sendMessage(Component.text("Fishing tip notifications are now " + (enabled ? "ENABLED" : "DISABLED") + ".")
                .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
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
            player.sendMessage(Component.text("Global XP multiplier: x" + com.fishrework.util.FormatUtil.format("%.2f", current))
                    .color(NamedTextColor.AQUA));
            return true;
        }

        if (!checkAdmin(player)) {
            return true;
        }

        try {
            double requested = Double.parseDouble(args[1]);
            double maxMultiplier = plugin.getConfig().getDouble("general.xp_multiplier_max", 100.0);
            if (requested < 0.0 || requested > maxMultiplier) {
                player.sendMessage(Component.text("Multiplier must be between 0.0 and " + com.fishrework.util.FormatUtil.format("%.1f", maxMultiplier) + ".")
                        .color(NamedTextColor.RED));
                return true;
            }

            plugin.getConfig().set("general.xp_multiplier", requested);
            plugin.saveConfig();

            player.sendMessage(Component.text("Set global XP multiplier to x" + com.fishrework.util.FormatUtil.format("%.2f", requested))
                    .color(NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Usage: /fishing xpmultiplier [value]").color(NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleReload(Player player, String[] args) {
        if (!player.hasPermission("fishrework.reload") && !player.hasPermission("fishrework.admin")) {
            player.sendMessage(Component.text("You don't have permission to reload the config.").color(NamedTextColor.RED));
            return true;
        }
        plugin.reload();
        player.sendMessage(Component.text("[Fish Rework] Config reloaded successfully!").color(NamedTextColor.GREEN));
        return true;
    }

    private boolean handleHelp(Player player, String[] args) {
        sendHelp(player);
        return true;
    }

    private boolean handleDamageIndicator(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /fishing dmgindicator <on|off>").color(NamedTextColor.RED));
            return true;
        }
        boolean enabled = args[1].equalsIgnoreCase("on");
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        data.setDamageIndicatorsEnabled(enabled);
        plugin.getDatabaseManager().saveSetting(player.getUniqueId(), "dmg_indicator", String.valueOf(enabled));
        player.sendMessage(Component.text("Damage indicators are now " + (enabled ? "enabled" : "disabled") + ".")
                .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
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
        player.sendMessage(Component.text("--- FishRework Help ---").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("/fishing").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Open main menu").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing top").color(NamedTextColor.YELLOW)
                .append(Component.text(" - View leaderboard").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing info").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Open the skills overview menu").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing encyclopedia").color(NamedTextColor.YELLOW)
                .append(Component.text(" - View fish encyclopedia").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing artifacts").color(NamedTextColor.YELLOW)
                .append(Component.text(" - View artifact encyclopedia").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing sync").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Fix missing recipes").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing recipe [item_id]").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Open the recipe browser for a held/custom item").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing shop").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Open the fishing shop").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing bag").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Open your fish bag").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing balance").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Check your Doubloons balance").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing stats").color(NamedTextColor.YELLOW)
                .append(Component.text(" - View session statistics").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing sell").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Quick sell all fish from inventory").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing autosell").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Toggle auto-sell for common fish").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing settings").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Open fishing settings").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing notifications [on|off|toggle]").color(NamedTextColor.YELLOW)
            .append(Component.text(" - Toggle fishing tip notifications").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing xpmultiplier [value]").color(NamedTextColor.YELLOW)
            .append(Component.text(" - View/set global XP multiplier").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing dmgindicator <on|off>").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Toggle damage indicators").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing particles [high|medium|low]").color(NamedTextColor.YELLOW)
            .append(Component.text(" - Set sea creature effect detail").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fs particles <high|medium|low>").color(NamedTextColor.YELLOW)
            .append(Component.text(" - Quick particle detail shortcut").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing chances").color(NamedTextColor.YELLOW)
                .append(Component.text(" - View biome spawn chances at your location").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing heat").color(NamedTextColor.YELLOW)
                .append(Component.text(" - View your lava heat stats").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("Milestone: Fishing Level 27").color(NamedTextColor.GOLD)
            .append(Component.text(" unlocks Lava Fishing + Magma Satchel in shop").color(NamedTextColor.GRAY)));

        if (player.hasPermission("fishrework.admin")) {
            player.sendMessage(Component.text("--- Admin Commands ---").color(NamedTextColor.RED));
            player.sendMessage(Component.text("/fishing addxp <player> <amount>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Give XP to player").color(NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/fishing setlevel <player> <level>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Set player level").color(NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/fishing setchance <mob|artifact> <chance>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Set mob/artifact spawn chance").color(NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/fishing resetchances").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Reset all chances to defaults").color(NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/fishing reset <player>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Reset player data").color(NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/fishing give <player> <item> [count]").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Give custom item").color(NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/fishing spawn <mob>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Spawn mob at location").color(NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/fishing fulfill <player> <all|creatures|artifacts>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Unlock all creatures/artifacts for a player").color(NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/fishing heat <player>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - View another player's heat stats").color(NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/fishing setheat <player> <amount>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Set heat (clamped to 0-100)").color(NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/fishing setcoins <player> <amount>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Set doubloons balance").color(NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/fishing xpmultiplier <value>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Set global XP multiplier").color(NamedTextColor.GRAY)));

            player.sendMessage(Component.text("/fishing reload").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Reload config.yml without restart").color(NamedTextColor.GRAY)));
        }
    }

    private boolean handleParticleModeCommand(Player player, String[] args) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return true;

        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /fishing particles <high|medium|low>").color(NamedTextColor.RED));
            return true;
        }

        ParticleDetailMode mode = ParticleDetailMode.fromInput(args[0]);
        if (mode == null) {
            player.sendMessage(Component.text("Unknown mode. Use high, medium, or low.").color(NamedTextColor.RED));
            return true;
        }

        data.setParticleDetailMode(mode);
        plugin.getDatabaseManager().saveSetting(player.getUniqueId(), "particle_mode", mode.getId());

        NamedTextColor color = switch (mode) {
            case HIGH -> NamedTextColor.GREEN;
            case MEDIUM -> NamedTextColor.YELLOW;
            case LOW -> NamedTextColor.RED;
        };

        player.sendMessage(Component.text("Sea creature particles set to " + mode.getId().toUpperCase() + ".")
                .color(color));
        player.sendMessage(Component.text("Use /fs particles <high|medium|low> to change this later.")
                .color(NamedTextColor.GRAY));
        return true;
    }

    private void showSessionStats(Player player) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;
        com.fishrework.model.FishingSession session = data.getSession();
        String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");

        player.sendMessage(Component.text("--- Fishing Session Stats ---").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("  Total Catches: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(session.getTotalCatches())).color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Mobs Killed: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(session.getMobsKilled())).color(NamedTextColor.RED)));
        player.sendMessage(Component.text("  Treasures Found: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(session.getTreasuresFound())).color(NamedTextColor.GOLD)));
        player.sendMessage(Component.text("  XP Earned: ").color(NamedTextColor.GRAY)
                .append(Component.text(com.fishrework.util.FormatUtil.format("%.0f", session.getXpEarned())).color(NamedTextColor.AQUA)));
        player.sendMessage(Component.text("  " + currencyName + " Earned: ").color(NamedTextColor.GRAY)
                .append(Component.text(com.fishrework.util.FormatUtil.format("%.0f", session.getDoubloonsEarned())).color(NamedTextColor.GREEN)));
        player.sendMessage(Component.text("  Levels Gained: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(session.getLevelsGained())).color(NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("  New Discoveries: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(session.getNewDiscoveries())).color(NamedTextColor.LIGHT_PURPLE)));

        // Streak info
        if (session.getBestStreak() > 0) {
            player.sendMessage(Component.text("  Current Streak: ").color(NamedTextColor.GRAY)
                    .append(Component.text("x" + session.getCurrentStreak()).color(NamedTextColor.GOLD)));
            player.sendMessage(Component.text("  Best Streak: ").color(NamedTextColor.GRAY)
                    .append(Component.text("x" + session.getBestStreak()).color(NamedTextColor.GOLD)));
        }

        // Auto-sell status
        player.sendMessage(Component.text("  Auto-Sell: ").color(NamedTextColor.GRAY)
                .append(Component.text(session.isAutoSellEnabled() ? "ON" : "OFF")
                        .color(session.isAutoSellEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        player.sendMessage(Component.text("----------------------------").color(NamedTextColor.GOLD));
    }

    private void openRecipeGuide(Player player, String[] args) {
        List<com.fishrework.registry.RecipeDefinition> recipes;

        if (args.length >= 2) {
            recipes = plugin.getRecipeRegistry().getRecipesForResultId(args[1].toLowerCase());
            if (recipes.isEmpty()) {
                player.sendMessage(Component.text("No recipe GUI found for '" + args[1] + "'.").color(NamedTextColor.RED));
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
            player.sendMessage(Component.text("Sold " + totalItems + " items for "
                    + com.fishrework.util.FormatUtil.format("%.0f", totalEarnings) + " " + currencyName + "!")
                    .color(NamedTextColor.GREEN));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.2f);
        } else {
            player.sendMessage(Component.text("You don't have anything to sell!")
                    .color(NamedTextColor.RED));
        }
    }

    private boolean checkAdmin(Player player) {
        if (!player.hasPermission("fishrework.admin")) {
            player.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private boolean requireFeature(Player player, String featureKey, String disabledMessage) {
        if (plugin.isFeatureEnabled(featureKey)) {
            return true;
        }
        player.sendMessage(Component.text(disabledMessage).color(NamedTextColor.RED));
        return false;
    }


    private void adminAddXp(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /fishing addxp <player> <amount>").color(NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { player.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED)); return; }

        try {
            Skill skill = Skill.FISHING;
            double amount = Double.parseDouble(args[2]);

            if (amount < 0 || amount > 1_000_000) {
                player.sendMessage(Component.text("Amount must be between 0 and 1,000,000.").color(NamedTextColor.RED));
                return;
            }

            boolean levelUp = plugin.getSkillManager().grantRawXp(target, skill, amount);
            player.sendMessage(Component.text("Added " + amount + " XP to " + target.getName()).color(NamedTextColor.GREEN));
            if (levelUp) target.sendMessage(Component.text("You leveled up from admin command!").color(NamedTextColor.GOLD));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid number.").color(NamedTextColor.RED));
        }
    }

    private void adminSetLevel(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /fishing setlevel <player> <level>").color(NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { player.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED)); return; }

        try {
            Skill skill = Skill.FISHING;
            int level = Integer.parseInt(args[2]);

            if (level < 0 || level > plugin.getLevelManager().getMaxLevel()) {
                player.sendMessage(Component.text("Level must be 0-" + plugin.getLevelManager().getMaxLevel()).color(NamedTextColor.RED));
                return;
            }

            PlayerData data = plugin.getPlayerData(target.getUniqueId());
            data.setLevel(skill, level);
            data.setXp(skill, 0); // Per-level XP: reset to 0 at the new level

            player.sendMessage(Component.text("Set " + target.getName() + "'s " + skill.name() + " to level " + level).color(NamedTextColor.GREEN));
            target.sendMessage(Component.text("Your " + skill.name() + " level was set to " + level).color(NamedTextColor.GOLD));

            plugin.getAdvancementManager().syncAdvancements(target, level);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid number.").color(NamedTextColor.RED));
        }
    }

    private void adminSetChance(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /fishing setchance <mob|artifact> <chance>").color(NamedTextColor.RED));
            return;
        }

        String target = args[1].toLowerCase();
        
        // Handle artifact chance separately
        if (target.equals("artifact")) {
            try {
                double chance = Double.parseDouble(args[2]);
                if (chance < 0.0 || chance > 100.0) {
                    player.sendMessage(Component.text("Chance must be between 0.0 and 100.0.").color(NamedTextColor.RED));
                    return;
                }
                double oldChance = plugin.getConfig().getDouble("artifacts.chance", 3.0);
                plugin.getConfig().set("artifacts.chance", chance);
                plugin.saveConfig();
                player.sendMessage(Component.text("Updated artifact chance: " + oldChance + "% -> " + chance + "%").color(NamedTextColor.GREEN));
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid number.").color(NamedTextColor.RED));
            }
            return;
        }

        List<String> known = new ArrayList<>(plugin.getMobRegistry().getAllIds());
        known.add("land_mob_bonus");
        
        if (!known.contains(target)) {
            player.sendMessage(Component.text("Unknown mob. Available: " + String.join(", ", known)).color(NamedTextColor.RED));
            return;
        }

        try {
            double chance = Double.parseDouble(args[2]);
            if (chance < 0.0 || chance > 100.0) {
                player.sendMessage(Component.text("Chance must be between 0.0 and 100.0.").color(NamedTextColor.RED));
                return;
            }
            double oldChance = plugin.getMobManager().getMobChance(target, -1);
            
            plugin.getMobManager().setMobChance(target, chance);
            player.sendMessage(Component.text("Updated " + target + " chance: " + oldChance + "% -> " + chance + "%").color(NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid number.").color(NamedTextColor.RED));
        }
    }

    private void adminReset(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /fishing reset <player>").color(NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { player.sendMessage(Component.text("Player not found online.").color(NamedTextColor.RED)); return; }

        plugin.getDatabaseManager().resetData(target.getUniqueId());
        PlayerData data = plugin.getPlayerData(target.getUniqueId());
        if (data != null) {
            data.reset();
        }

        plugin.getRecipeRegistry().syncRecipes(target);
        target.closeInventory();

        player.sendMessage(Component.text("Reset all data for " + target.getName()).color(NamedTextColor.GREEN));
        target.sendMessage(Component.text("Your skills and collection have been reset.").color(NamedTextColor.RED));
    }

    private void adminGive(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /fishing give <player> <item> [count]").color(NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { player.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED)); return; }

        String itemName = args[2].toLowerCase();
        org.bukkit.inventory.ItemStack item = plugin.getItemManager().getItem(itemName);

        if (item == null) {
            player.sendMessage(Component.text("Unknown item. Use tab completion.").color(NamedTextColor.RED));
            return;
        }

        int count = 1;
        if (args.length >= 4) {
            try {
                count = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
                player.sendMessage(Component.text("Count must be a number.").color(NamedTextColor.RED));
                return;
            }
        }

        if (count <= 0 || count > 10_000) {
            player.sendMessage(Component.text("Count must be between 1 and 10,000.").color(NamedTextColor.RED));
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

        player.sendMessage(Component.text("Gave " + count + "x " + itemName + " to " + target.getName()).color(NamedTextColor.GREEN));
    }

    private void adminSpawn(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /fishing spawn <mob>").color(NamedTextColor.RED));
            return;
        }

        String mobId = args[1].toLowerCase();
        com.fishrework.model.CustomMob mob = plugin.getMobRegistry().get(mobId);
        if (mob == null) {
            player.sendMessage(Component.text("Unknown mob ID.").color(NamedTextColor.RED));
            return;
        }

        if (mob.isTreasure()) {
            player.sendMessage(Component.text("Treasure chests cannot be spawned. Use /fishing give <player> treasure_chest_" + mobId.replace("treasure_", "") + " instead.").color(NamedTextColor.RED));
            return;
        }

        plugin.getMobManager().spawnMob(player.getLocation(), mobId);
        player.sendMessage(Component.text("Spawned " + mobId).color(NamedTextColor.GREEN));
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
        player.sendMessage(Component.text("All mob chances, land mob bonus, and artifact chance reset to defaults!").color(NamedTextColor.GREEN));
    }

    private void adminChances(Player player, String[] args) {
        Skill skill = Skill.FISHING;

        FishingChanceSnapshotHelper.ChanceSnapshot snapshot =
                FishingChanceSnapshotHelper.capture(plugin, player, skill);
        java.util.Map<String, Double> chances = snapshot.chances();
        java.util.Map<String, Double> rawWeights = snapshot.rawWeights();

        player.sendMessage(Component.text("--- Fishing Spawn Chances ---").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("Biome: " + snapshot.biomeGroup().name()).color(NamedTextColor.GRAY));

        if (snapshot.activeBaitId() != null && !snapshot.baitAppliesToContext()) {
            player.sendMessage(Component.text("Held bait is inactive in this dimension.")
                    .color(NamedTextColor.RED));
        }

        if (snapshot.baitRareCreatureBonus() > 0 || snapshot.baitTreasureBonus() > 0) {
            player.sendMessage(Component.text(com.fishrework.util.FormatUtil.format("Active Bait Bonus: +%.1f%% Rare, +%.1f%% Treasure",
                    snapshot.baitRareCreatureBonus(), snapshot.baitTreasureBonus()))
                .color(NamedTextColor.AQUA));
        }
        if (!snapshot.baitTargetMobIds().isEmpty()) {
            player.sendMessage(Component.text("Biome Bait Targets: " + String.join(", ", snapshot.baitTargetMobIds()))
                .color(NamedTextColor.AQUA));
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
            player.sendMessage(Component.text("Usage: /fishing fulfill <player> <all|creatures|artifacts>").color(NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED));
            return;
        }

        String type = args[2].toLowerCase();
        PlayerData data = plugin.getPlayerData(target.getUniqueId());
        
        boolean doCreatures = type.equals("all") || type.equals("creatures") || type.equals("fish");
        boolean doArtifacts = type.equals("all") || type.equals("artifacts");

        if (!doCreatures && !doArtifacts) {
            player.sendMessage(Component.text("Unknown type. Use all, creatures, or artifacts.").color(NamedTextColor.RED));
            return;
        }

        if (doCreatures) {
            for (String mobId : plugin.getMobRegistry().getAllIds()) {
                if (!data.hasCaughtMob(mobId)) {
                    data.addCaughtMob(mobId);
                    plugin.getDatabaseManager().updateCollection(target.getUniqueId(), mobId, 1.0);
                }
            }
            player.sendMessage(Component.text("Unlocked all creatures for " + target.getName()).color(NamedTextColor.GREEN));
        }

        if (doArtifacts) {
             for (String artId : plugin.getArtifactRegistry().getAllIds()) {
                if (!data.hasArtifact(artId)) {
                    data.addArtifact(artId);
                    plugin.getDatabaseManager().saveArtifact(target.getUniqueId(), artId);
                }
            }
            player.sendMessage(Component.text("Unlocked all artifacts for " + target.getName()).color(NamedTextColor.GREEN));
        }
        
        // Sync to trigger advancements
        plugin.getAdvancementManager().syncAdvancements(target, data.getLevel(Skill.FISHING));
    }

    private void adminHeat(Player player, String[] args) {
        Player target = player;
        if (args.length > 1) {
            if (!player.hasPermission("fishrework.admin")) {
                player.sendMessage(Component.text("No permission.").color(NamedTextColor.RED));
                return;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED));
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

        player.sendMessage(Component.text("--- Heat Debug (" + target.getName() + ") ---").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("Current Heat: ").color(NamedTextColor.GRAY)
                .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f / 100.0", data.getHeat())).color(NamedTextColor.RED)));
        player.sendMessage(Component.text("Heat Tier: ").color(NamedTextColor.GRAY)
                .append(Component.text(tier.name()).color(tier.getColor())));
        player.sendMessage(Component.text("Equipment Heat Resist: ").color(NamedTextColor.GRAY)
            .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f%%", equipmentHeatResist)).color(NamedTextColor.AQUA)));
        player.sendMessage(Component.text("Magma Filter Resist: ").color(NamedTextColor.GRAY)
            .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f%%", tempHeatResist)).color(NamedTextColor.AQUA))
            .append(Component.text(magmaFilterRemaining > 0 ? " (" + magmaFilterRemaining + "s left)" : " (inactive)")
                .color(magmaFilterRemaining > 0 ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)));
        player.sendMessage(Component.text("Total Heat Resist: ").color(NamedTextColor.GRAY)
            .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f%%", totalHeatResist)).color(NamedTextColor.GREEN)));
        player.sendMessage(Component.text("Peak Heat (Session): ").color(NamedTextColor.GRAY)
                .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f", data.getSession().getPeakHeat())).color(NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Damage Taken: ").color(NamedTextColor.GRAY)
                .append(Component.text(com.fishrework.util.FormatUtil.format("%.1f", data.getSession().getHeatDamageTaken())).color(NamedTextColor.DARK_RED)));
    }

    private void adminSetHeat(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /fishing setheat <player> <amount>").color(NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED));
            return;
        }

        PlayerData data = plugin.getPlayerData(target.getUniqueId());
        if (data == null) {
            player.sendMessage(Component.text("No player data found for that player.").color(NamedTextColor.RED));
            return;
        }

        try {
            double requestedHeat = Double.parseDouble(args[2]);
            double clampedHeat = Math.max(0.0, Math.min(100.0, requestedHeat));
            data.setHeat(clampedHeat);

            plugin.getHeatManager().showHeatGauge(target, clampedHeat);

            if (requestedHeat != clampedHeat) {
                player.sendMessage(Component.text(com.fishrework.util.FormatUtil.format("Set %s's heat to %.1f (clamped from %.1f)", target.getName(), clampedHeat, requestedHeat))
                        .color(NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text(com.fishrework.util.FormatUtil.format("Set %s's heat to %.1f", target.getName(), clampedHeat))
                        .color(NamedTextColor.GREEN));
            }

            target.sendMessage(Component.text(com.fishrework.util.FormatUtil.format("Your lava heat was set to %.1f by an admin.", clampedHeat))
                    .color(NamedTextColor.GOLD));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid heat amount. Use a number from 0 to 100.").color(NamedTextColor.RED));
        }
    }

    private void adminSetCoins(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /fishing setcoins <player> <amount>").color(NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(Component.text("Player not found online.").color(NamedTextColor.RED));
            return;
        }

        PlayerData data = plugin.getPlayerData(target.getUniqueId());
        if (data == null) {
            player.sendMessage(Component.text("No player data found for that player.").color(NamedTextColor.RED));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid amount.").color(NamedTextColor.RED));
            return;
        }

        if (amount < 0) {
            player.sendMessage(Component.text("Amount must be >= 0.").color(NamedTextColor.RED));
            return;
        }

        data.setBalance(amount);
        plugin.getDatabaseManager().saveBalance(target.getUniqueId(), amount);

        String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");
        player.sendMessage(Component.text("Set " + target.getName() + "'s balance to " + com.fishrework.util.FormatUtil.format("%.0f", amount) + " " + currencyName + ".")
                .color(NamedTextColor.GREEN));
        target.sendMessage(Component.text("Your balance was set to " + com.fishrework.util.FormatUtil.format("%.0f", amount) + " " + currencyName + " by an admin.")
                .color(NamedTextColor.GOLD));
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

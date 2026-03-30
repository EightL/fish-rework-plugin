package com.fishrework.command;

import com.fishrework.FishRework;
import com.fishrework.gui.LeaderboardGUI;
import com.fishrework.gui.RecipeBrowserGUI;
import com.fishrework.gui.RecipeGuideGUI;
import com.fishrework.gui.SkillsMenuGUI;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Skill;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fishrework.model.BiomeGroup;

public class FishingCommand implements CommandExecutor, TabExecutor {

    private final FishRework plugin;

    public FishingCommand(FishRework plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            new com.fishrework.gui.SkillDetailGUI(plugin, player, Skill.FISHING).open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "encyclopedia", "collection", "fish" -> {
                if (!plugin.isFeatureEnabled("encyclopedia_enabled")) {
                    player.sendMessage(Component.text("The Fishing Encyclopedia is disabled on this server.").color(NamedTextColor.RED));
                    return true;
                }
                new com.fishrework.gui.CollectionGui(plugin, player).open(player);
            }

            case "artifacts" -> {
                if (!plugin.isFeatureEnabled("artifact_collection_enabled")) {
                    player.sendMessage(Component.text("Artifact collection is disabled on this server.").color(NamedTextColor.RED));
                    return true;
                }
                new com.fishrework.gui.ArtifactCollectionGUI(plugin, player).open(player);
            }

            case "top", "leaderboard" -> {
                if (!plugin.isFeatureEnabled("leaderboard_enabled")) {
                    player.sendMessage(Component.text("The leaderboard is disabled on this server.").color(NamedTextColor.RED));
                    return true;
                }
                new LeaderboardGUI(plugin, player, Skill.FISHING).open(player);
            }

            case "shop" -> {
                if (!plugin.isFeatureEnabled("shop_enabled")) {
                    player.sendMessage(Component.text("The shop is disabled on this server.").color(NamedTextColor.RED));
                    return true;
                }
                new com.fishrework.gui.ShopMenuGUI(plugin, player).open(player);
            }

            case "bag" -> {
                if (!plugin.isFeatureEnabled("fish_bag_enabled")) {
                    player.sendMessage(Component.text("Fish Bags are disabled on this server.").color(NamedTextColor.RED));
                    return true;
                }
                new com.fishrework.gui.FishBagGUI(plugin, player).open(player);
            }

            case "recipe" -> {
                if (!plugin.isFeatureEnabled("recipe_browser_enabled")) {
                    player.sendMessage(Component.text("The recipe browser is disabled on this server.").color(NamedTextColor.RED));
                    return true;
                }
                openRecipeGuide(player, args);
            }

            case "balance", "bal" -> {
                if (!plugin.isFeatureEnabled("economy_enabled")) {
                    player.sendMessage(Component.text("The economy system is disabled on this server.").color(NamedTextColor.RED));
                    return true;
                }
                com.fishrework.model.PlayerData data = plugin.getPlayerData(player.getUniqueId());
                double balance = data != null ? data.getBalance() : 0;
                String currencyName = plugin.getConfig().getString("economy.currency_name", "Doubloons");
                player.sendMessage(Component.text("Balance: " + String.format("%.0f", balance) + " " + currencyName)
                        .color(NamedTextColor.GOLD));
            }

            case "info" ->
                    new SkillsMenuGUI(plugin, player).open(player);

            case "sync" -> {
                plugin.getRecipeRegistry().syncRecipes(player);
                player.sendMessage(Component.text("Synced your recipes and advancements!").color(NamedTextColor.GREEN));
            }

            case "stats" -> showSessionStats(player);

            case "sell" -> quickSell(player);

            case "autosell" -> {
                if (!plugin.isFeatureEnabled("auto_sell_enabled")) {
                    player.sendMessage(Component.text("Auto-sell is disabled on this server.").color(NamedTextColor.RED));
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
            }

            case "notifications" -> {
                PlayerData data = plugin.getPlayerData(player.getUniqueId());
                if (data == null) return true;

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
            }

            case "xpmultiplier" -> {
                if (args.length == 1) {
                    double current = plugin.getConfig().getDouble("general.xp_multiplier", 1.0);
                    player.sendMessage(Component.text("Global XP multiplier: x" + String.format("%.2f", current))
                            .color(NamedTextColor.AQUA));
                    break;
                }

                if (!checkAdmin(player)) return true;

                try {
                    double requested = Double.parseDouble(args[1]);
                    double maxMultiplier = plugin.getConfig().getDouble("general.xp_multiplier_max", 100.0);
                    if (requested < 0.0 || requested > maxMultiplier) {
                        player.sendMessage(Component.text("Multiplier must be between 0.0 and " + String.format("%.1f", maxMultiplier) + ".")
                                .color(NamedTextColor.RED));
                        return true;
                    }

                    plugin.getConfig().set("general.xp_multiplier", requested);
                    plugin.saveConfig();

                    player.sendMessage(Component.text("Set global XP multiplier to x" + String.format("%.2f", requested))
                            .color(NamedTextColor.GREEN));
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Usage: /fishing xpmultiplier [value]").color(NamedTextColor.RED));
                }
            }

            case "reload" -> {
                if (!sender.hasPermission("fishrework.reload") && !sender.hasPermission("fishrework.admin")) {
                    player.sendMessage(Component.text("You don't have permission to reload the config.").color(NamedTextColor.RED));
                    return true;
                }
                plugin.reload();
                player.sendMessage(Component.text("[Fish Rework] Config reloaded successfully!").color(NamedTextColor.GREEN));
            }

            case "help" -> sendHelp(player);

            case "addxp" -> {
                if (!checkAdmin(player)) return true;
                adminAddXp(player, args);
            }
            case "setlevel" -> {
                if (!checkAdmin(player)) return true;
                adminSetLevel(player, args);
            }
            case "setchance" -> {
                if (!checkAdmin(player)) return true;
                adminSetChance(player, args);
            }
            case "reset" -> {
                if (!checkAdmin(player)) return true;
                adminReset(player, args);
            }
            case "resetchances" -> {
                if (!checkAdmin(player)) return true;
                adminResetChances(player);
            }
            case "give" -> {
                if (!checkAdmin(player)) return true;
                adminGive(player, args);
            }
            case "spawn" -> {
                if (!checkAdmin(player)) return true;
                adminSpawn(player, args);
            }
            case "chances" -> {
                if (!checkAdmin(player)) return true;
                adminChances(player, args);
            }
            case "fulfill" -> {
                if (!checkAdmin(player)) return true;
                adminFulfill(player, args);
            }
            case "pet" -> {
                if (!checkAdmin(player)) return true;
                plugin.getPetManager().spawnCustomPig(player);
                player.sendMessage(Component.text("Spawned your custom pig friend!").color(NamedTextColor.GREEN));
            }

            case "dmgindicator" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /fishing dmgindicator <on|off>").color(NamedTextColor.RED));
                    return true;
                }
                boolean enabled = args[1].equalsIgnoreCase("on");
                PlayerData data = plugin.getPlayerData(player.getUniqueId());
                data.setDamageIndicatorsEnabled(enabled);
                plugin.getDatabaseManager().saveSetting(player.getUniqueId(), "dmg_indicator", String.valueOf(enabled));
                player.sendMessage(Component.text("Damage indicators are now " + (enabled ? "enabled" : "disabled") + ".").color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
            }

            case "heat" -> {
                if (!checkAdmin(player)) return true;
                adminHeat(player, args);
            }
            case "setheat" -> {
                if (!checkAdmin(player)) return true;
                adminSetHeat(player, args);
            }
            case "setcoins" -> {
                if (!checkAdmin(player)) return true;
                adminSetCoins(player, args);
            }

            default ->
                    player.sendMessage(Component.text("Unknown subcommand. Use /fishing help").color(NamedTextColor.RED));
        }


        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("--- FishRework Help ---").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("/fishing").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Open main menu").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing top").color(NamedTextColor.YELLOW)
                .append(Component.text(" - View leaderboard").color(NamedTextColor.GRAY)));
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
        player.sendMessage(Component.text("/fishing notifications [on|off|toggle]").color(NamedTextColor.YELLOW)
            .append(Component.text(" - Toggle fishing tip notifications").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing xpmultiplier [value]").color(NamedTextColor.YELLOW)
            .append(Component.text(" - View/set global XP multiplier").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/fishing dmgindicator <on|off>").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Toggle damage indicators").color(NamedTextColor.GRAY)));
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
            player.sendMessage(Component.text("/fishing chances").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - View biome spawn chances").color(NamedTextColor.GRAY)));
            player.sendMessage(Component.text("/fishing heat <player>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - View heat debug stats").color(NamedTextColor.GRAY)));
                player.sendMessage(Component.text("/fishing setheat <player> <amount>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Set heat (clamped to 0-100)").color(NamedTextColor.GRAY)));
                player.sendMessage(Component.text("/fishing setcoins <player> <amount>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Set doubloons balance").color(NamedTextColor.GRAY)));
                player.sendMessage(Component.text("/fishing xpmultiplier <value>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Set global XP multiplier").color(NamedTextColor.GRAY)));
        }
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
                .append(Component.text(String.format("%.0f", session.getXpEarned())).color(NamedTextColor.AQUA)));
        player.sendMessage(Component.text("  " + currencyName + " Earned: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.format("%.0f", session.getDoubloonsEarned())).color(NamedTextColor.GREEN)));
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
                    + String.format("%.0f", totalEarnings) + " " + currencyName + "!")
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

        if (count <= 0) {
            player.sendMessage(Component.text("Count must be at least 1.").color(NamedTextColor.RED));
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

        // Include active bait (offhand) in debug chances so command matches real catch rolls.
        double baitRareCreatureBonus = 0.0;
        double baitTreasureBonus = 0.0;
        List<String> baitTargetMobIds = Collections.emptyList();
        Set<BiomeGroup> baitNativeBiomeGroups = Collections.emptySet();
        org.bukkit.inventory.ItemStack offhand = player.getInventory().getItemInOffHand();
        if (plugin.getItemManager().isBait(offhand)) {
            baitTargetMobIds = plugin.getItemManager().getBaitTargetMobIds(offhand);
            baitNativeBiomeGroups = plugin.getItemManager().getBaitNativeBiomeGroups(offhand);
            String baitId = plugin.getItemManager().getBaitId(offhand);
            if (baitId != null) {
                com.fishrework.model.Bait bait = plugin.getBaitRegistry().get(baitId);
                if (bait != null) {
                    baitRareCreatureBonus = bait.getBonus(com.fishrework.model.Bait.RARE_CREATURE_CHANCE);
                    baitTreasureBonus = bait.getBonus(com.fishrework.model.Bait.TREASURE_CHANCE);
                }
            }
        }

        java.util.Map<String, Double> chances = plugin.getMobManager().getSpawnChances(
    player, skill, player.getLocation(), baitRareCreatureBonus, baitTreasureBonus,
        baitTargetMobIds, baitNativeBiomeGroups);

        // Get biome context
        // Geat biome context - matching logic from MobManager
        org.bukkit.block.Biome bukkitBiome = player.getLocation().getBlock().getBiome();
        String biomeKey = bukkitBiome.getKey().toString();
        com.fishrework.model.BiomeGroup biome = com.fishrework.model.BiomeGroup.fromBiomeKey(biomeKey);
        if (biome == com.fishrework.model.BiomeGroup.OTHER) {
            biome = com.fishrework.model.BiomeGroup.fromBiome(bukkitBiome);
        }

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        int level = data != null ? data.getLevel(skill) : 0;

        player.sendMessage(Component.text("--- Fishing Spawn Chances ---").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("Biome: " + biome.name()).color(NamedTextColor.GRAY));
        
        // Get raw weights to show alongside percentages
        Map<String, Double> rawWeights = plugin.getMobManager().buildWeightMap(skill, level, 1.0 + ((plugin.getMobManager().getEquipmentRareCreatureBonus(player) + baitRareCreatureBonus)/100.0), 
                                               1.0 + ((plugin.getMobManager().getTreasureChance(player) + baitTreasureBonus)/100.0), 
                                                                               (biome != BiomeGroup.OTHER ? plugin.getBiomeFishingRegistry().get(biome) : null), 
                                                                               player.getLocation(), 
                                                                               plugin.getItemManager().isHarmonyRod(player.getInventory().getItemInMainHand()) || plugin.getItemManager().isHarmonyRod(player.getInventory().getItemInOffHand()),
                                                                               baitTargetMobIds,
                                                                               baitNativeBiomeGroups);

        if (baitRareCreatureBonus > 0 || baitTreasureBonus > 0) {
            player.sendMessage(Component.text(String.format("Active Bait Bonus: +%.1f%% Rare, +%.1f%% Treasure", baitRareCreatureBonus, baitTreasureBonus))
                .color(NamedTextColor.AQUA));
        }
        if (!baitTargetMobIds.isEmpty()) {
            player.sendMessage(Component.text("Biome Bait Targets: " + String.join(", ", baitTargetMobIds))
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
                         player.sendMessage(Component.text(String.format("- %s: %.2f%%", name, chance)).color(color));
                    } else {
                         player.sendMessage(Component.text(String.format("- %s: %.2f%% (Weight: %.1f)", name, chance, weight)).color(color));
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
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED));
                return;
            }
        }

        PlayerData data = plugin.getPlayerData(target.getUniqueId());
        com.fishrework.manager.HeatManager heatManager = plugin.getHeatManager();
        com.fishrework.manager.HeatManager.HeatTier tier = heatManager.getHeatTier(target);
        double heatResist = plugin.getMobManager().getEquipmentBonus(target, plugin.getItemManager().HEAT_RESISTANCE_KEY);

        player.sendMessage(Component.text("--- Heat Debug (" + target.getName() + ") ---").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("Current Heat: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.format("%.1f / 100.0", data.getHeat())).color(NamedTextColor.RED)));
        player.sendMessage(Component.text("Heat Tier: ").color(NamedTextColor.GRAY)
                .append(Component.text(tier.name()).color(tier.getColor())));
        player.sendMessage(Component.text("PDC Heat Resist: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.format("%.1f", heatResist)).color(NamedTextColor.AQUA)));
        player.sendMessage(Component.text("Peak Heat (Session): ").color(NamedTextColor.GRAY)
                .append(Component.text(String.format("%.1f", data.getSession().getPeakHeat())).color(NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Damage Taken: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.format("%.1f", data.getSession().getHeatDamageTaken())).color(NamedTextColor.DARK_RED)));
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
                player.sendMessage(Component.text(String.format("Set %s's heat to %.1f (clamped from %.1f)", target.getName(), clampedHeat, requestedHeat))
                        .color(NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text(String.format("Set %s's heat to %.1f", target.getName(), clampedHeat))
                        .color(NamedTextColor.GREEN));
            }

            target.sendMessage(Component.text(String.format("Your lava heat was set to %.1f by an admin.", clampedHeat))
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
        player.sendMessage(Component.text("Set " + target.getName() + "'s balance to " + String.format("%.0f", amount) + " " + currencyName + ".")
                .color(NamedTextColor.GREEN));
        target.sendMessage(Component.text("Your balance was set to " + String.format("%.0f", amount) + " " + currencyName + " by an admin.")
                .color(NamedTextColor.GOLD));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        boolean isAdmin = sender.hasPermission("fishrework.admin");

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
            completions.add("notifications");
            completions.add("xpmultiplier");
            completions.add("help");
            if (isAdmin) {
                completions.addAll(List.of("addxp", "setlevel", "setchance", "resetchances", "reset", "give", "spawn", "chances", "fulfill", "heat", "setheat", "setcoins", "dmgindicator"));
            } else {
                completions.add("dmgindicator");
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("dmgindicator")) {
                completions.addAll(List.of("on", "off"));
            } else if (args[0].equalsIgnoreCase("notifications")) {
                completions.addAll(List.of("on", "off", "toggle"));
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
                    return null; // Player list
                }
            }
        } else if (args.length == 3 && isAdmin) {
            if (args[0].equalsIgnoreCase("setchance")) {
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

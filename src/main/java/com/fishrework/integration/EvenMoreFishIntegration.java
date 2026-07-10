package com.fishrework.integration;

import com.fishrework.FishRework;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional compatibility bridge for EvenMoreFish.
 *
 * <p>Fish Rework remains fully loadable without EMF. Reflection is deliberately
 * isolated in this class so EMF API classes are never linked by the JVM when the
 * other plugin is absent.</p>
 */
public final class EvenMoreFishIntegration implements Listener {

    private static final String EMF_PLUGIN_NAME = "EvenMoreFish";
    private static final String CONFIG_ROOT = "integrations.even_more_fish.";
    private static final Pattern REQUIRE_CUSTOM_ROD = Pattern.compile(
            "(?m)^(\\s*require-custom-rod\\s*:\\s*)false(\\s*(?:#.*)?)$"
    );

    private final FishRework plugin;
    private final NamespacedKey recipeKey;

    private Plugin emfPlugin;
    private Object rodManager;
    private Method getRodByItem;
    private Method getRodById;
    private boolean active;
    private boolean recipeRegistered;
    private boolean rodLookupFailureLogged;

    public EvenMoreFishIntegration(FishRework plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "evenmorefish_default_rod");
    }

    public void initialize() {
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        unregisterRecipe();
        clearRuntimeState();

        if (!plugin.getConfig().getBoolean(CONFIG_ROOT + "enabled", true)) {
            return;
        }

        Plugin detected = plugin.getServer().getPluginManager().getPlugin(EMF_PLUGIN_NAME);
        if (detected == null || !detected.isEnabled()) {
            return;
        }
        this.emfPlugin = detected;

        if (!loadRodApi()) {
            plugin.getLogger().warning("[Fish Rework] EvenMoreFish was detected, but its custom-rod API is unavailable. Compatibility was not enabled.");
            return;
        }

        File emfConfig = new File(detected.getDataFolder(), "config.yml");
        boolean requiresCustomRod = readRequireCustomRod(emfConfig);
        if (!requiresCustomRod && plugin.getConfig().getBoolean(CONFIG_ROOT + "auto_configure", true)) {
            requiresCustomRod = enableRequireCustomRod(emfConfig);
            if (requiresCustomRod && !reloadEvenMoreFish()) {
                plugin.getLogger().warning("[Fish Rework] EMF was configured, but its live reload failed. Restart the server to activate compatibility.");
                return;
            }
        }

        if (!requiresCustomRod) {
            plugin.getLogger().warning("[Fish Rework] EvenMoreFish detected, but fishing.require-custom-rod is false. "
                    + "Enable integrations.even_more_fish.auto_configure or configure EMF manually.");
            return;
        }

        active = true;
        registerDefaultRodRecipe();
        plugin.getLogger().info("[Fish Rework] EvenMoreFish compatibility enabled: EMF rods route to EMF; other rods route to Fish Rework.");
    }

    public void shutdown() {
        unregisterRecipe();
        clearRuntimeState();
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Mirrors EMF's own rod choice: the casting hand for FISHING, otherwise
     * the main-hand fishing rod first and then the off-hand rod.
     */
    public boolean shouldBypassFishRework(PlayerFishEvent event) {
        if (!active || event == null) return false;

        Player player = event.getPlayer();
        ItemStack rod = null;
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            EquipmentSlot hand = event.getHand();
            if (hand != null) {
                rod = player.getInventory().getItem(hand);
            }
        } else {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand.getType() == Material.FISHING_ROD) {
                rod = mainHand;
            } else {
                ItemStack offHand = player.getInventory().getItemInOffHand();
                if (offHand.getType() == Material.FISHING_ROD) {
                    rod = offHand;
                }
            }
        }
        return isEvenMoreFishRod(rod);
    }

    public boolean isEvenMoreFishRod(ItemStack item) {
        if (!active || item == null || item.getType() != Material.FISHING_ROD) return false;
        try {
            return getRodByItem.invoke(rodManager, item) != null;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            if (!rodLookupFailureLogged) {
                rodLookupFailureLogged = true;
                plugin.getLogger().warning("[Fish Rework] Failed to identify an EvenMoreFish rod: " + rootMessage(e));
            }
            return false;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (active && recipeRegistered
                && plugin.getConfig().getBoolean(CONFIG_ROOT + "default_rod_recipe.discover_automatically", true)) {
            event.getPlayer().discoverRecipe(recipeKey);
        }
    }

    private boolean loadRodApi() {
        try {
            ClassLoader loader = emfPlugin.getClass().getClassLoader();
            Class<?> managerType = Class.forName(
                    "com.oheers.fish.api.fishing.rods.AbstractRodManager",
                    true,
                    loader
            );
            Method getInstance = managerType.getMethod("getInstance");
            this.rodManager = getInstance.invoke(null);
            this.getRodByItem = managerType.getMethod("getRod", ItemStack.class);
            this.getRodById = managerType.getMethod("getRod", String.class);
            return rodManager != null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            plugin.getLogger().warning("[Fish Rework] Could not initialize the EvenMoreFish rod API: " + rootMessage(e));
            return false;
        }
    }

    private void registerDefaultRodRecipe() {
        if (!active || !plugin.getConfig().getBoolean(CONFIG_ROOT + "default_rod_recipe.enabled", true)) {
            return;
        }

        String rodId = plugin.getConfig().getString(CONFIG_ROOT + "default_rod_recipe.rod_id", "default");
        Material upgradeMaterial = parseMaterial(plugin.getConfig().getString(
                CONFIG_ROOT + "default_rod_recipe.upgrade_material",
                "NAUTILUS_SHELL"
        ));
        int upgradeAmount = Math.max(1, Math.min(8, plugin.getConfig().getInt(
                CONFIG_ROOT + "default_rod_recipe.upgrade_material_amount",
                1
        )));

        if (rodId == null || rodId.isBlank() || upgradeMaterial == null || upgradeMaterial.isAir()) {
            plugin.getLogger().warning("[Fish Rework] Invalid EvenMoreFish default rod recipe configuration; recipe was not registered.");
            return;
        }

        try {
            Object rod = getRodById.invoke(rodManager, rodId.trim());
            if (rod == null) {
                plugin.getLogger().warning("[Fish Rework] EvenMoreFish rod '" + rodId + "' was not found; recipe was not registered.");
                return;
            }

            Method create = rod.getClass().getMethod("create");
            Object created = create.invoke(rod);
            if (!(created instanceof ItemStack result) || result.isEmpty()) {
                plugin.getLogger().warning("[Fish Rework] EvenMoreFish returned no item for rod '" + rodId + "'.");
                return;
            }

            plugin.getServer().removeRecipe(recipeKey);
            ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, result.clone());
            recipe.addIngredient(Material.FISHING_ROD);
            for (int i = 0; i < upgradeAmount; i++) {
                recipe.addIngredient(upgradeMaterial);
            }

            recipeRegistered = plugin.getServer().addRecipe(recipe);
            if (!recipeRegistered) {
                plugin.getLogger().warning("[Fish Rework] The EvenMoreFish default rod recipe could not be registered.");
                return;
            }

            if (plugin.getConfig().getBoolean(CONFIG_ROOT + "default_rod_recipe.discover_automatically", true)) {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    player.discoverRecipe(recipeKey);
                }
            }
            plugin.getLogger().info("[Fish Rework] Registered craftable EMF rod recipe: fishing rod + "
                    + upgradeAmount + "x " + upgradeMaterial.name().toLowerCase(Locale.ROOT) + ".");
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("[Fish Rework] Failed to register the EvenMoreFish rod recipe: " + rootMessage(e));
        }
    }

    private boolean enableRequireCustomRod(File configFile) {
        if (!configFile.isFile()) {
            plugin.getLogger().warning("[Fish Rework] Cannot auto-configure EvenMoreFish because its config.yml was not found.");
            return false;
        }

        try {
            File backup = new File(configFile.getParentFile(), "config.yml.fishrework-backup");
            if (!backup.exists()) {
                Files.copy(configFile.toPath(), backup.toPath());
            }

            String yaml = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
            Matcher matcher = REQUIRE_CUSTOM_ROD.matcher(yaml);
            if (!matcher.find()) {
                plugin.getLogger().warning("[Fish Rework] Could not locate fishing.require-custom-rod: false in the EvenMoreFish config.");
                return false;
            }

            String updated = matcher.replaceFirst("$1true$2");
            Files.writeString(configFile.toPath(), updated, StandardCharsets.UTF_8);
            plugin.getLogger().info("[Fish Rework] Configured EvenMoreFish to require custom rods. Backup: " + backup.getName());
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("[Fish Rework] Failed to auto-configure EvenMoreFish: " + e.getMessage());
            return false;
        }
    }

    private boolean readRequireCustomRod(File configFile) {
        if (!configFile.isFile()) return false;
        try {
            String yaml = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
            return Pattern.compile("(?m)^\\s*require-custom-rod\\s*:\\s*true\\s*(?:#.*)?$")
                    .matcher(yaml)
                    .find();
        } catch (IOException e) {
            plugin.getLogger().warning("[Fish Rework] Failed to read the EvenMoreFish config: " + e.getMessage());
            return false;
        }
    }

    private boolean reloadEvenMoreFish() {
        try {
            Method reload = emfPlugin.getClass().getMethod("reload", org.bukkit.command.CommandSender.class);
            reload.invoke(emfPlugin, new Object[]{null});
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("[Fish Rework] Failed to reload EvenMoreFish: " + rootMessage(e));
            return false;
        }
    }

    private Material parseMaterial(String configured) {
        if (configured == null || configured.isBlank()) return null;
        return Material.matchMaterial(configured.trim());
    }

    private void unregisterRecipe() {
        if (recipeRegistered || plugin.getServer().getRecipe(recipeKey) != null) {
            plugin.getServer().removeRecipe(recipeKey);
        }
        recipeRegistered = false;
    }

    private void clearRuntimeState() {
        active = false;
        emfPlugin = null;
        rodManager = null;
        getRodByItem = null;
        getRodById = null;
        rodLookupFailureLogged = false;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}

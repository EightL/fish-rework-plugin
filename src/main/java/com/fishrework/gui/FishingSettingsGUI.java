package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.model.AutoSellMode;
import com.fishrework.model.ParticleDetailMode;
import com.fishrework.model.PlayerData;
import com.fishrework.model.SeaCreatureMessageMode;
import com.fishrework.util.FeatureKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.function.Consumer;

public class FishingSettingsGUI extends BaseGUI {

    private final Player player;
    private final Consumer<Player> backAction;

    public FishingSettingsGUI(FishRework plugin, Player player, Consumer<Player> backAction) {
        super(plugin, 3, plugin.getLanguageManager().getString(player, "fishingsettingsgui.title", "Fishing Settings"));
        this.player = player;
        this.backAction = backAction;
        initializeItems();
    }

    private void initializeItems() {
        fillBackground(Material.GRAY_STAINED_GLASS_PANE);
        inventory.setItem(4, createLanguageItem());
        inventory.setItem(10, createAutoSellItem());
        inventory.setItem(11, createSeaCreatureMessageItem());
        inventory.setItem(12, createNotificationsItem());
        inventory.setItem(14, createDamageIndicatorsItem());
        inventory.setItem(16, createParticleModeItem());
        setLocalizedBackButton(22);
    }

    private ItemStack createLanguageItem() {
        String locale = plugin.getLanguageManager().getPlayerLocale(player);
        String languageName = plugin.getLanguageManager().getLocaleDisplayName(player, locale);

        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(plugin.getLanguageManager().getString(player,
                        "fishingsettingsgui.language",
                        "Language") + ": " + languageName)
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                plugin.getLanguageManager().getMessage(player, "fishingsettingsgui.current_language", "Current: %language%",
                                "language", languageName)
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                plugin.getLanguageManager().getMessage(player, "fishingsettingsgui.command_fish_language", "Command: /fs language <en|es|zh_CN>")
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                plugin.getLanguageManager().getMessage(player, "fishingsettingsgui.click_to_cycle", "Click to cycle.")
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSeaCreatureMessageItem() {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        SeaCreatureMessageMode mode = data == null ? SeaCreatureMessageMode.ALL : data.getSeaCreatureMessageMode();

        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        String label = plugin.getLanguageManager().getString(player,
                "fishingsettingsgui.sea_creature_chat",
                "Sea Creature Chat");
        String modeLabel = plugin.getLanguageManager().getString(player,
                "fishingsettingsgui.sea_creature_chat_mode_" + mode.getId(),
                mode.name());
        NamedTextColor color = switch (mode) {
            case ALL -> NamedTextColor.GREEN;
            case RARE_ONLY -> NamedTextColor.YELLOW;
            case NONE -> NamedTextColor.RED;
        };

        meta.displayName(Component.text(label + ": " + modeLabel)
                .color(color)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                plugin.getLanguageManager().getMessage(player,
                                "fishingsettingsgui.sea_creature_chat_desc_" + mode.getId(),
                                "Shows hooked sea creature chat messages.")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                plugin.getLanguageManager().getMessage(player,
                                "fishingsettingsgui.click_to_cycle",
                                "Click to cycle.")
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private void setLocalizedBackButton(int slot) {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta meta = back.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getMessage(player, "basegui.back", "Back")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(meta);
        inventory.setItem(slot, back);
    }

    private ItemStack createAutoSellItem() {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        AutoSellMode mode = data != null ? data.getSession().getAutoSellMode() : AutoSellMode.OFF;
        boolean featureEnabled = plugin.isFeatureEnabled(FeatureKeys.AUTO_SELL_ENABLED);
        ItemStack item = new ItemStack(featureEnabled ? Material.GOLD_INGOT : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        String label = plugin.getLanguageManager().getString(player, "fishingsettingsgui.autosell", "Auto-Sell");
        if (!featureEnabled) {
            meta.displayName(Component.text(label + ": "
                    + plugin.getLanguageManager().getString(player, "common.disabled", "DISABLED"))
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(plugin.getLanguageManager().getMessage(player,
                    "fishingsettingsgui.disabled_by_server_admin",
                    "Disabled by server admin.")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
            return item;
        }

        String modeLabel = plugin.getLanguageManager().getString(player,
            "fishingsettingsgui.autosell_mode_" + mode.getId(),
            mode.name());
        NamedTextColor color = switch (mode) {
            case ALL -> NamedTextColor.GREEN;
            case OTHER -> NamedTextColor.YELLOW;
            case OFF -> NamedTextColor.RED;
        };

        meta.displayName(Component.text(label + ": " + modeLabel)
            .color(color)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            plugin.getLanguageManager().getMessage(player,
                    "fishingsettingsgui.autosell_desc_" + mode.getId(),
                    "Auto-sell mode.")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            plugin.getLanguageManager().getMessage(player,
                    "fishingsettingsgui.command_fish_autosell",
                    "Command: /fish autosell [off|other|all]")
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            plugin.getLanguageManager().getMessage(player,
                    "fishingsettingsgui.click_to_cycle",
                    "Click to cycle.")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNotificationsItem() {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        boolean enabled = data == null || data.isFishingTipsEnabled();
        boolean featureEnabled = plugin.isFeatureEnabled(FeatureKeys.FISHING_TIPS_ENABLED);
        return createToggleItem(
                featureEnabled ? Material.BELL : Material.BARRIER,
                plugin.getLanguageManager().getString(player, "fishingsettingsgui.tip_notifications", "Tip Notifications"),
                featureEnabled ? enabled : null,
                featureEnabled
                        ? List.of(
                                plugin.getLanguageManager().getMessage(player, "fishingsettingsgui.shows_occasional_fishing_tips", "Shows occasional fishing tips.").color(NamedTextColor.GRAY)
                                        .decoration(TextDecoration.ITALIC, false),
                                plugin.getLanguageManager().getMessage(player, "fishingsettingsgui.command_fish_notifications", "Command: /fish notifications").color(NamedTextColor.DARK_GRAY)
                                        .decoration(TextDecoration.ITALIC, false)
                        )
                        : List.of(plugin.getLanguageManager().getMessage(player, "fishingsettingsgui.disabled_by_server_admin", "Disabled by server admin.").color(NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false))
        );
    }

    private ItemStack createDamageIndicatorsItem() {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        boolean enabled = data == null || data.isDamageIndicatorsEnabled();
        boolean featureEnabled = plugin.isFeatureEnabled(FeatureKeys.DAMAGE_INDICATORS_ENABLED);
        return createToggleItem(
                featureEnabled ? Material.REDSTONE : Material.BARRIER,
                plugin.getLanguageManager().getString(player, "fishingsettingsgui.damage_indicators", "Damage Indicators"),
                featureEnabled ? enabled : null,
                featureEnabled
                        ? List.of(
                                plugin.getLanguageManager().getMessage(player, "fishingsettingsgui.shows_floating_damage_numbers", "Shows floating damage numbers.").color(NamedTextColor.GRAY)
                                        .decoration(TextDecoration.ITALIC, false),
                                plugin.getLanguageManager().getMessage(player, "fishingsettingsgui.command_fish_dmgindicator", "Command: /fish dmgindicator").color(NamedTextColor.DARK_GRAY)
                                        .decoration(TextDecoration.ITALIC, false)
                        )
                        : List.of(plugin.getLanguageManager().getMessage(player, "fishingsettingsgui.disabled_by_server_admin", "Disabled by server admin.").color(NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false))
        );
    }

    private ItemStack createParticleModeItem() {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        ParticleDetailMode mode = data == null ? ParticleDetailMode.HIGH : data.getParticleDetailMode();

        ItemStack item = new ItemStack(switch (mode) {
            case HIGH -> Material.BLAZE_POWDER;
            case MEDIUM -> Material.GLOWSTONE_DUST;
            case LOW -> Material.GUNPOWDER;
        });
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(plugin.getLanguageManager().getString(player,
                        "fishingsettingsgui.particles_prefix",
                        "Sea Creature Particles: ") + mode.getId().toUpperCase())
                .color(switch (mode) {
                    case HIGH -> NamedTextColor.GREEN;
                    case MEDIUM -> NamedTextColor.YELLOW;
                    case LOW -> NamedTextColor.RED;
                })
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                plugin.getLanguageManager().getMessage(player, "fishingsettingsgui.changes_how_intense_sea_creature", "Changes how intense sea creature effects look.").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                plugin.getLanguageManager().getMessage(player, "fishingsettingsgui.command_fish_particles_highmediumlow", "Command: /fish particles <high|medium|low>").color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                plugin.getLanguageManager().getMessage(player, "fishingsettingsgui.click_to_cycle", "Click to cycle.").color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createToggleItem(Material material, String label, Boolean enabled, List<Component> description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        NamedTextColor color;
        String suffix;
        if (enabled == null) {
            color = NamedTextColor.GRAY;
            suffix = plugin.getLanguageManager().getString(player, "common.disabled", "DISABLED");
        } else {
            color = enabled ? NamedTextColor.GREEN : NamedTextColor.RED;
            suffix = plugin.getLanguageManager().getString(player, enabled ? "common.on" : "common.off", enabled ? "ON" : "OFF");
        }

        meta.displayName(Component.text(label + ": " + suffix)
                .color(color)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(description);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) {
            return;
        }

        if (slot == 4) {
            String next = plugin.getLanguageManager().cycleLocale(player);
            data.setLanguageLocale(next);
            plugin.getDatabaseManager().saveSetting(player.getUniqueId(), "language", next);
            plugin.refreshPlayerCustomItems(player);
            new FishingSettingsGUI(plugin, player, backAction).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 22) {
            if (backAction != null) {
                backAction.accept(player);
            } else {
                player.closeInventory();
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (slot == 10 && plugin.isFeatureEnabled(FeatureKeys.AUTO_SELL_ENABLED)) {
            AutoSellMode next = data.getSession().getAutoSellMode().next();
            data.getSession().setAutoSellMode(next);
            plugin.getDatabaseManager().saveSetting(player.getUniqueId(), "auto_sell_mode", next.getId());
        } else if (slot == 11) {
            SeaCreatureMessageMode next = data.getSeaCreatureMessageMode().next();
            data.setSeaCreatureMessageMode(next);
            plugin.getDatabaseManager().saveSetting(player.getUniqueId(), "sea_creature_message_mode", next.getId());
        } else if (slot == 12 && plugin.isFeatureEnabled(FeatureKeys.FISHING_TIPS_ENABLED)) {
            boolean enabled = !data.isFishingTipsEnabled();
            data.setFishingTipsEnabled(enabled);
            plugin.getDatabaseManager().saveSetting(player.getUniqueId(), "tips_notifications", String.valueOf(enabled));
        } else if (slot == 14 && plugin.isFeatureEnabled(FeatureKeys.DAMAGE_INDICATORS_ENABLED)) {
            boolean enabled = !data.isDamageIndicatorsEnabled();
            data.setDamageIndicatorsEnabled(enabled);
            plugin.getDatabaseManager().saveSetting(player.getUniqueId(), "dmg_indicator", String.valueOf(enabled));
        } else if (slot == 16) {
            ParticleDetailMode next = switch (data.getParticleDetailMode()) {
                case HIGH -> ParticleDetailMode.MEDIUM;
                case MEDIUM -> ParticleDetailMode.LOW;
                case LOW -> ParticleDetailMode.HIGH;
            };
            data.setParticleDetailMode(next);
            plugin.getDatabaseManager().saveSetting(player.getUniqueId(), "particle_mode", next.getId());
        } else {
            return;
        }

        initializeItems();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }
}

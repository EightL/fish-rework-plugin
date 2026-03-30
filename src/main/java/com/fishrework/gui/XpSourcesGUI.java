package com.fishrework.gui;

import com.fishrework.FishRework;
import com.fishrework.model.CustomMob;
import com.fishrework.model.PlayerData;
import com.fishrework.model.Skill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * XP Sources GUI — auto-populated from MobRegistry + config.
 * Adding a new mob to the registry automatically shows it here.
 * Supports pagination for large mob lists.
 */
public class XpSourcesGUI extends BaseGUI {

    private final Player player;
    private int page;

    /** Slots used for mob display (3 rows × 7 columns). */
    private static final int[] MOB_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int MOBS_PER_PAGE = MOB_SLOTS.length;

    public XpSourcesGUI(FishRework plugin, Player player) {
        this(plugin, player, 0);
    }

    public XpSourcesGUI(FishRework plugin, Player player, int page) {
        super(plugin, 6, "XP Sources: Fishing");
        this.player = player;
        this.page = page;
        initializeItems();
    }

    private void initializeItems() {
        // ── Static fish item XP sources (from config) ──
        if (page == 0) {
            addItem(10, Material.COD, "Cod", plugin.getConfig().getDouble("fishing.xp.cod", 10.0));
            addItem(11, Material.SALMON, "Salmon", plugin.getConfig().getDouble("fishing.xp.salmon", 15.0));
            addItem(12, Material.TROPICAL_FISH, "Tropical Fish", plugin.getConfig().getDouble("fishing.xp.tropical_fish", 25.0));
            addItem(13, Material.PUFFERFISH, "Pufferfish", plugin.getConfig().getDouble("fishing.xp.pufferfish", 30.0));
            addItem(14, Material.ENCHANTED_BOOK, "Treasure", plugin.getConfig().getDouble("fishing.xp.treasure", 50.0));
            addItem(15, Material.ROTTEN_FLESH, "Junk", plugin.getConfig().getDouble("fishing.xp.junk", 5.0));
        }

        // ── Mob XP sources — auto-populated from registry (paginated) ──
        List<CustomMob> mobs = new ArrayList<>(plugin.getMobRegistry().getBySkill(Skill.FISHING));
        mobs.sort(java.util.Comparator.comparingDouble(CustomMob::getXp));
        // Biome-aware chances at the player's current location
        Map<String, Double> realChances = plugin.getMobManager().getSpawnChances(player, Skill.FISHING, player.getLocation());

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        int level = data != null ? data.getLevel(Skill.FISHING) : 0;
        double xpMulti = plugin.getLevelManager().getXpMultiplier(level);

        int totalPages = Math.max(1, (int) Math.ceil((double) mobs.size() / MOBS_PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;

        int startIndex = page * MOBS_PER_PAGE;
        int endIndex = Math.min(startIndex + MOBS_PER_PAGE, mobs.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slotIdx = i - startIndex;
            if (slotIdx >= MOB_SLOTS.length) break;
            CustomMob mob = mobs.get(i);

            double chance = realChances.getOrDefault(mob.getId(), 0.0);

            // Chance component
            Component chanceComp = Component.text("Spawn Chance: ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.2f%%", chance))
                            .color(chance > 0 ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));

            if (mob.getRequiredLevel() > 0) {
                NamedTextColor reqColor = level >= mob.getRequiredLevel() ? NamedTextColor.GREEN : NamedTextColor.RED;
                chanceComp = chanceComp.append(Component.text(" | Req. Lvl " + mob.getRequiredLevel()).color(reqColor));
            }

            // Biome-only indicator
            if (mob.getDefaultChance() <= 0 && chance <= 0) {
                chanceComp = chanceComp.append(Component.text(" (Biome-specific)").color(NamedTextColor.DARK_GRAY));
            }

            // XP calculation
            double baseXp = mob.getXp();
            double finalXp = baseXp * xpMulti;
            Component xpComp = Component.text("XP: ").color(NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.0f", baseXp)).color(NamedTextColor.YELLOW));
            if (xpMulti > 1.0) {
                xpComp = xpComp.append(Component.text(String.format(" (* %.2f = %.0f)", xpMulti, finalXp)).color(NamedTextColor.AQUA));
            }

            ItemStack item = new ItemStack(mob.getCollectionIcon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Fished " + mob.getDisplayName()).color(NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(xpComp);
            lore.add(chanceComp);
            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(MOB_SLOTS[slotIdx], item);
        }

        // Navigation
        int totalPagesNav = totalPages; // capture for pagination
        setPaginationControls(45, 53, page, totalPagesNav);

        // Back button
        setBackButton(49);
    }

    private void addItem(int slot, Material material, String name, double xp) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("XP: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.format("%.0f", xp)).color(NamedTextColor.YELLOW)));
        meta.lore(lore);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getSlot();

        if (slot == 49) {
            new SkillDetailGUI(plugin, player, Skill.FISHING).open(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
        } else if (slot == 45) {
            if (page > 0) {
                new XpSourcesGUI(plugin, player, page - 1).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            }
        } else if (slot == 53) {
            List<CustomMob> mobs = plugin.getMobRegistry().getBySkill(Skill.FISHING);
            int maxPage = (int) Math.ceil((double) mobs.size() / MOBS_PER_PAGE) - 1;
            if (page < maxPage) {
                new XpSourcesGUI(plugin, player, page + 1).open(player);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1);
            }
        }
    }
}

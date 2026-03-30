package com.fishrework.manager;

import com.fishrework.FishRework;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.Collection;

public class DisplayCaseManager {

    private final FishRework plugin;
    public final NamespacedKey DISPLAY_CASE_ENTITY_KEY;

    // Style Keys
    public final NamespacedKey CASE_STYLE_SLAB;
    public final NamespacedKey CASE_STYLE_GLASS;
    public final NamespacedKey CASE_STYLE_CARPET;

    public DisplayCaseManager(FishRework plugin) {
        this.plugin = plugin;
        this.DISPLAY_CASE_ENTITY_KEY = new NamespacedKey(plugin, "display_case_entity");
        this.CASE_STYLE_SLAB = new NamespacedKey(plugin, "case_style_slab");
        this.CASE_STYLE_GLASS = new NamespacedKey(plugin, "case_style_glass");
        this.CASE_STYLE_CARPET = new NamespacedKey(plugin, "case_style_carpet");
    }

    /**
     * Creates a new Display Case at the given location with default style.
     */
    public void createDisplayCase(Location location, Player player) {
        // Defaults
        Material slab = Material.POLISHED_ANDESITE_SLAB;
        Material glassMat = Material.GLASS;
        Material carpet = Material.MAGENTA_CARPET;
        
        createDisplayCase(location, player.getLocation().getYaw(), slab, glassMat, carpet);
    }

    public void createDisplayCase(Location location, float yaw, Material slab, Material glassMat, Material carpet) {
        // 1. Set the physical block to Iron Trapdoor (Height 0.1875, prevents snowy grass)
        location.getBlock().setType(Material.IRON_TRAPDOOR);
        if (location.getBlock().getBlockData() instanceof org.bukkit.block.data.type.TrapDoor trapdoor) {
            trapdoor.setHalf(org.bukkit.block.data.Bisected.Half.BOTTOM);
            trapdoor.setOpen(false);
            // Trapdoors have facing, but for the physical block it doesn't matter much visually since it's covered.
            // But we can set it to match player direction if we want.
            location.getBlock().setBlockData(trapdoor);
        }

        Location center = location.clone().add(0.5, 0, 0.5);
        
        // Calculate snapped rotation (cardinal directions)
        float snappedYaw = (Math.round(yaw / 90f) * 90f + 180f) % 360f;

        // 2. Spawn the Base Slab (Block Display)
        // Physical Trapdoor is ~0.1875 high.
        // We render the slab slightly larger to avoid z-fighting and cover it.
        // Slab (0.5) * 0.5 scale = 0.25 visual height. 
        // 0.25 > 0.1875, so it covers the trapdoor.
        BlockDisplay base = (BlockDisplay) location.getWorld().spawnEntity(center.clone(), EntityType.BLOCK_DISPLAY);
        base.setBlock(org.bukkit.Bukkit.createBlockData(slab));
        base.setRotation(snappedYaw, 0); // Apply rotation
        
        Transformation baseTrans = base.getTransformation();
        // Scale 1.01 to avoid Z-fighting on the sides (requested expansion)
        // Scale Y 0.501 to avoid Z-fighting with the item/carpet on top
        baseTrans.getScale().set(1.01f, 0.501f, 1.01f); 
        // Translate: Centered (-0.505), and pushed down slightly (-0.01) to cover bottom Z-fighting
        baseTrans.getTranslation().set(-0.505f, -0.01f, -0.505f); 
        base.setTransformation(baseTrans);
        configureDisplay(base);

        // 3. Spawn the Pillow (Carpet Block Display)
        // Sits on top of the visual base (0.25)
        BlockDisplay pillow = (BlockDisplay) location.getWorld().spawnEntity(center.clone().add(0, 0.25, 0), EntityType.BLOCK_DISPLAY);
        pillow.setBlock(org.bukkit.Bukkit.createBlockData(carpet));
        pillow.setRotation(snappedYaw, 0); // Apply rotation
        
        Transformation pillowTrans = pillow.getTransformation();
        pillowTrans.getScale().set(0.7f, 0.6f, 0.7f);
        pillowTrans.getTranslation().set(-0.35f, 0.0f, -0.35f);
        pillow.setTransformation(pillowTrans);
        configureDisplay(pillow);

        // 4. Spawn the Glass Case
        // Enclosing the pillow. Starts at 0.25.
        BlockDisplay glass = (BlockDisplay) location.getWorld().spawnEntity(center.clone().add(0, 0.25, 0), EntityType.BLOCK_DISPLAY);
        glass.setBlock(org.bukkit.Bukkit.createBlockData(glassMat));
        glass.setRotation(snappedYaw, 0); // Apply rotation
        
        Transformation glassTrans = glass.getTransformation();
        glassTrans.getScale().set(0.8f, 0.7f, 0.8f);
        glassTrans.getTranslation().set(-0.4f, -0.1f, -0.4f);
        glass.setTransformation(glassTrans);
        configureDisplay(glass);

        // 5. Spawn the Item Display
        // Resting on the pillow.
        // Was 0.6, requested "tiny bit down" -> 0.5
        ItemDisplay itemDisplay = (ItemDisplay) location.getWorld().spawnEntity(center.clone().add(0, 0.5, 0), EntityType.ITEM_DISPLAY);
        itemDisplay.setItemStack(new ItemStack(Material.AIR));
        itemDisplay.setRotation(snappedYaw, 0); // Apply rotation
        
        Transformation itemTrans = itemDisplay.getTransformation();
        itemTrans.getScale().set(0.5f, 0.5f, 0.5f);
        itemDisplay.setTransformation(itemTrans);
        itemDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        configureDisplay(itemDisplay);
        
        // Save style info
        itemDisplay.getPersistentDataContainer().set(CASE_STYLE_SLAB, PersistentDataType.STRING, slab.name());
        itemDisplay.getPersistentDataContainer().set(CASE_STYLE_GLASS, PersistentDataType.STRING, glassMat.name());
        itemDisplay.getPersistentDataContainer().set(CASE_STYLE_CARPET, PersistentDataType.STRING, carpet.name());
    }

    private void configureDisplay(Display display) {
        display.setPersistent(true);
        display.getPersistentDataContainer().set(DISPLAY_CASE_ENTITY_KEY, PersistentDataType.BYTE, (byte) 1);
        display.setInvulnerable(true);
        display.setViewRange(1.0f);
        display.setBillboard(Display.Billboard.FIXED);
        display.setShadowRadius(0);
        display.setShadowStrength(0);
    }

    /**
     * Updates the style of an existing Display Case.
     */
    public void updateStyle(Block block, Material slab, Material glassMat, Material carpet) {
        if (!isDisplayCaseBlock(block)) return;

        // Physical block stays Iron Trapdoor. We interact with the entities.

        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        Collection<Entity> entities = block.getWorld().getNearbyEntities(center, 1.0, 1.0, 1.0);

        for (Entity entity : entities) {
            if (!entity.getPersistentDataContainer().has(DISPLAY_CASE_ENTITY_KEY, PersistentDataType.BYTE)) continue;

            if (entity instanceof BlockDisplay bd) {
                Material type = bd.getBlock().getMaterial();
                
                // Identify based on material type
                if (glassMat != null && (type == Material.GLASS || type.name().contains("STAINED_GLASS") && !type.name().contains("PANE"))) {
                     bd.setBlock(org.bukkit.Bukkit.createBlockData(glassMat));
                } else if (carpet != null && type.name().contains("CARPET")) {
                     bd.setBlock(org.bukkit.Bukkit.createBlockData(carpet));
                } else if (slab != null && type.name().contains("SLAB")) {
                     // This is the base
                     bd.setBlock(org.bukkit.Bukkit.createBlockData(slab));
                }
            } else if (entity instanceof ItemDisplay id) {
                // Update storage
                if (slab != null) id.getPersistentDataContainer().set(CASE_STYLE_SLAB, PersistentDataType.STRING, slab.name());
                if (glassMat != null) id.getPersistentDataContainer().set(CASE_STYLE_GLASS, PersistentDataType.STRING, glassMat.name());
                if (carpet != null) id.getPersistentDataContainer().set(CASE_STYLE_CARPET, PersistentDataType.STRING, carpet.name());
            }
        }
    }

    public void removeDisplayCase(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        Collection<Entity> entities = block.getWorld().getNearbyEntities(center, 1.0, 1.0, 1.0);
        
        boolean droppedCase = false;
        
        for (Entity entity : entities) {
            if (entity.getPersistentDataContainer().has(DISPLAY_CASE_ENTITY_KEY, PersistentDataType.BYTE)) {
                if (entity instanceof ItemDisplay itemDisplay) {
                     ItemStack item = itemDisplay.getItemStack();
                    if (item != null && item.getType() != Material.AIR) {
                        block.getWorld().dropItemNaturally(block.getLocation(), item);
                    }
                }
                entity.remove();
                droppedCase = true;
            }
        }
        
        if (droppedCase) {
             ItemStack displayCaseItem = plugin.getItemManager().getRequiredItem("display_case");
             block.getWorld().dropItemNaturally(block.getLocation(), displayCaseItem);
        }
    }

    public boolean interact(Block block, Player player, ItemStack handItem) {
        return getDisplayEntity(block).map(display -> {
            ItemStack currentItem = display.getItemStack();
            boolean caseHasItem = currentItem != null && currentItem.getType() != Material.AIR;
            boolean handHasItem = handItem != null && handItem.getType() != Material.AIR;

            if (!caseHasItem && handHasItem) {
                setItem(display, handItem.clone());
                player.getInventory().setItemInMainHand(null);
            } else if (caseHasItem && !handHasItem) {
                player.getInventory().setItemInMainHand(currentItem);
                setItem(display, null);
            } else if (caseHasItem && handHasItem) {
                setItem(display, handItem.clone());
                player.getInventory().setItemInMainHand(currentItem);
            }
            return true;
        }).orElse(false);
    }

    private void setItem(ItemDisplay display, ItemStack item) {
        display.setItemStack(item);
        if (item != null && item.getType() != Material.AIR) {
            // Show Item Name
            Component name = item.getItemMeta().hasDisplayName() ? item.getItemMeta().displayName() : Component.translatable(item.getType().translationKey());
            display.customName(name);
            display.setCustomNameVisible(false); // Only visible when looking at it
        } else {
            display.customName(null);
            display.setCustomNameVisible(false);
        }
    }

    public java.util.Optional<ItemDisplay> getDisplayEntity(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        Collection<Entity> entities = block.getWorld().getNearbyEntities(center, 1.0, 1.0, 1.0);
        
        for (Entity entity : entities) {
            if (entity instanceof ItemDisplay display) {
                if (display.getPersistentDataContainer().has(DISPLAY_CASE_ENTITY_KEY, PersistentDataType.BYTE)) {
                    return java.util.Optional.of(display);
                }
            }
        }
        return java.util.Optional.empty();
    }
    
    public boolean isDisplayCaseBlock(Block block) {
        return getDisplayEntity(block).isPresent();
    }
}

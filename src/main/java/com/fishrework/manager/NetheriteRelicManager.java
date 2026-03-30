package com.fishrework.manager;

import com.fishrework.FishRework;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.Collection;

public class NetheriteRelicManager {

    private final FishRework plugin;
    public final NamespacedKey RELIC_ENTITY_KEY;

    public NetheriteRelicManager(FishRework plugin) {
        this.plugin = plugin;
        this.RELIC_ENTITY_KEY = new NamespacedKey(plugin, "netherite_relic_entity");
    }

    public void createRelic(Location location, float playerYaw) {
        // 1. Set the physical block to Iron Trapdoor (Prevents snowy grass, consistent with Display Case)
        location.getBlock().setType(Material.IRON_TRAPDOOR);
        if (location.getBlock().getBlockData() instanceof org.bukkit.block.data.type.TrapDoor trapdoor) {
            trapdoor.setHalf(org.bukkit.block.data.Bisected.Half.BOTTOM);
            trapdoor.setOpen(false);
            location.getBlock().setBlockData(trapdoor);
        }

        // Calculate snapped rotation (cardinal directions)
        float snappedYaw = (Math.round(playerYaw / 90f) * 90f) % 360f;
        Location center = location.clone().add(0.5, 0.0, 0.5);

        // 2. Visual Base (Mossy Cobblestone Slab)
        spawnBlockDisplay(center, Material.MOSSY_COBBLESTONE_SLAB, 
            new Vector3f(1.01f, 0.501f, 1.01f), new Vector3f(-0.505f, -0.01f, -0.505f), snappedYaw);

        // 3. Legs (Bottom Netherite Block)
        spawnBlockDisplay(center, Material.NETHERITE_BLOCK, 
            new Vector3f(0.35f, 0.35f, 0.35f), new Vector3f(-0.175f, 0.25f, -0.175f), snappedYaw);

        // 4. Lower Torso - "Abs" (Two Netherite Blocks, overlapping slightly at center)
        spawnBlockDisplay(center, Material.NETHERITE_BLOCK, 
            new Vector3f(0.3f, 0.35f, 0.35f), new Vector3f(-0.295f, 0.58f, -0.175f), snappedYaw);
        spawnBlockDisplay(center, Material.NETHERITE_BLOCK, 
            new Vector3f(0.3f, 0.35f, 0.35f), new Vector3f(-0.005f, 0.58f, -0.175f), snappedYaw);

        // 5. Chest Layer (Two longer rectangle pieces, overlapping slightly at center)
        spawnBlockDisplay(center, Material.NETHERITE_BLOCK, 
            new Vector3f(0.48f, 0.35f, 0.35f), new Vector3f(-0.475f, 0.91f, -0.175f), snappedYaw);
        spawnBlockDisplay(center, Material.NETHERITE_BLOCK, 
            new Vector3f(0.48f, 0.35f, 0.35f), new Vector3f(-0.005f, 0.91f, -0.175f), snappedYaw);

        // 6. Head (Custom Head: Netherite Gladiator)
        String headTexture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTI4ZDJlZGYwZTlhMTFhNDkyZDM2YjU3ZjkyM2U5M2E2MGU4MDQ4NjM3MmIyNGZmMGY1MmY0NjBiZDM5YjlkNiJ9fX0=";
        spawnItemDisplay(center, plugin.getItemManager().getCustomSkull(headTexture),
            new Vector3f(0.7f, 0.7f, 0.7f), new Vector3f(0, 1.62f, 0), snappedYaw);

        // 7. Horns (3 Gold Blocks each side, pushed down closer to head)
        spawnHorn(center, true, snappedYaw);
        spawnHorn(center, false, snappedYaw);
    }

    private void spawnHorn(Location center, boolean left, float yaw) {
        float side = left ? -1 : 1;
        
        // Pushed down relative to Head position
        // Block 1
        spawnBlockDisplay(center, Material.GOLD_BLOCK, 
            new Vector3f(0.12f, 0.12f, 0.12f), 
            new Vector3f(left ? -0.25f : 0.13f, 1.64f, -0.06f), yaw);
            
        // Block 2
        spawnBlockDisplay(center, Material.GOLD_BLOCK, 
            new Vector3f(0.10f, 0.10f, 0.10f), 
            new Vector3f(left ? -0.33f : 0.21f, 1.72f, -0.05f), yaw);
            
        // Block 3
        spawnBlockDisplay(center, Material.GOLD_BLOCK, 
            new Vector3f(0.08f, 0.08f, 0.08f), 
            new Vector3f(left ? -0.40f : 0.28f, 1.80f, -0.04f), yaw);
    }

    private void spawnBlockDisplay(Location loc, Material mat, Vector3f scale, Vector3f translation, float yaw) {
        BlockDisplay display = (BlockDisplay) loc.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY);
        display.setBlock(org.bukkit.Bukkit.createBlockData(mat));
        display.setRotation(yaw, 0);
        
        Transformation trans = display.getTransformation();
        trans.getScale().set(scale);
        trans.getTranslation().set(translation);
        display.setTransformation(trans);
        
        configureDisplay(display);
    }

    private void spawnItemDisplay(Location loc, org.bukkit.inventory.ItemStack item, Vector3f scale, Vector3f translation, float yaw) {
        org.bukkit.entity.ItemDisplay display = (org.bukkit.entity.ItemDisplay) loc.getWorld().spawnEntity(loc, EntityType.ITEM_DISPLAY);
        display.setItemStack(item);
        display.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.HEAD);
        display.setRotation(yaw, 0);
        
        Transformation trans = display.getTransformation();
        trans.getScale().set(scale);
        trans.getTranslation().set(translation);
        display.setTransformation(trans);
        
        configureDisplay(display);
    }

    private void configureDisplay(Display display) {
        display.setPersistent(true);
        display.getPersistentDataContainer().set(RELIC_ENTITY_KEY, PersistentDataType.BYTE, (byte) 1);
        display.setInvulnerable(true);
        display.setViewRange(1.5f);
        display.setBillboard(Display.Billboard.FIXED);
    }

    public void removeRelic(Location location) {
        Location searchCenter = location.clone().add(0.5, 1.0, 0.5);
        Collection<Entity> entities = location.getWorld().getNearbyEntities(searchCenter, 2.0, 2.0, 2.0);
        for (Entity entity : entities) {
            if (entity.getPersistentDataContainer().has(RELIC_ENTITY_KEY, PersistentDataType.BYTE)) {
                entity.remove();
            }
        }
    }
    
    public boolean isRelicBlock(Location location) {
        Location searchCenter = location.clone().add(0.5, 0.5, 0.5);
        Collection<Entity> entities = location.getWorld().getNearbyEntities(searchCenter, 0.6, 0.6, 0.6);
        for (Entity entity : entities) {
            if (entity.getPersistentDataContainer().has(RELIC_ENTITY_KEY, PersistentDataType.BYTE)) {
                return true;
            }
        }
        return false;
    }
}

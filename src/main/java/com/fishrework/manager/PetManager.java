package com.fishrework.manager;

import com.fishrework.FishRework;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class PetManager {

    private final FishRework plugin;
    private final Map<UUID, List<Entity>> activePets = new HashMap<>(); // Wolf UUID -> List of Display Entities
    
    // Textures from custom_heads.md
    private static final String PIG_HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjU4MTBiN2U2ZWE2NWE3MmE1OWFiZGNlNzI1YWRkZTJlYWM3MzViMDFiODI4YmUzZDY1NzE3NDJmZGYwZGRhMCJ9fX0=";
    private static final String PIG_BODY_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjI5NTE3OTMxNzlhODYzODhjYWUyMTk5OWYzZDQzZGZiN2ZlZjZjY2Y2ZDhiNzFkMzZjODU4ZWMzOTViZmEifX19";

    public PetManager(FishRework plugin) {
        this.plugin = plugin;
        startPetTicker();
    }

    public void removePet(Player player) {
        // Logic to remove existing pet for player (simple version: kill all wolves owned by player for now, or track them)
        // For this specific request, we'll just handle the entity structure.
    }

    public void spawnCustomPig(Player owner) {
        Location loc = owner.getLocation();
        
        // 1. The Driver (Invisible Wolf)
        Wolf wolf = (Wolf) loc.getWorld().spawnEntity(loc, EntityType.WOLF);
        wolf.setTamed(true);
        wolf.setOwner(owner);
        wolf.setInvisible(true);
        wolf.setSilent(true);
        wolf.setInvulnerable(true);
        wolf.setCollidable(false);
        wolf.setAgeLock(true);
        wolf.setAdult();
        
        // 2. The Visuals
        List<Entity> parts = new ArrayList<>();
        
        // Head
        ItemDisplay head = spawnItemDisplay(loc, plugin.getItemManager().getCustomSkull(PIG_HEAD_TEXTURE));
        parts.add(head);
        
        // Body
        ItemDisplay body = spawnItemDisplay(loc, plugin.getItemManager().getCustomSkull(PIG_BODY_TEXTURE));
        parts.add(body);
        
        // Legs (Pink Terracotta)
        Material legMat = Material.PINK_TERRACOTTA;
        parts.add(spawnBlockDisplay(loc, legMat)); // FL
        parts.add(spawnBlockDisplay(loc, legMat)); // FR
        parts.add(spawnBlockDisplay(loc, legMat)); // BL
        parts.add(spawnBlockDisplay(loc, legMat)); // BR

        activePets.put(wolf.getUniqueId(), parts);
    }

    private ItemDisplay spawnItemDisplay(Location loc, ItemStack item) {
        ItemDisplay display = (ItemDisplay) loc.getWorld().spawnEntity(loc, EntityType.ITEM_DISPLAY);
        display.setItemStack(item);
        display.setBillboard(Display.Billboard.FIXED);
        display.setViewRange(1.0f);
        display.setPersistent(false); // Clean up on restart
        return display;
    }

    private BlockDisplay spawnBlockDisplay(Location loc, Material mat) {
        BlockDisplay display = (BlockDisplay) loc.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY);
        display.setBlock(Bukkit.createBlockData(mat));
        display.setBillboard(Display.Billboard.FIXED);
        display.setPersistent(false); // Clean up on restart
        return display;
    }

    private void startPetTicker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Iterator<Map.Entry<UUID, List<Entity>>> it = activePets.entrySet().iterator();
            
            while (it.hasNext()) {
                Map.Entry<UUID, List<Entity>> entry = it.next();
                UUID wolfId = entry.getKey();
                List<Entity> parts = entry.getValue();
                
                Entity wolfEntity = Bukkit.getEntity(wolfId);
                
                // If wolf is gone/dead, remove parts
                if (wolfEntity == null || !wolfEntity.isValid()) {
                    parts.forEach(Entity::remove);
                    it.remove();
                    continue;
                }

                Location baseLoc = wolfEntity.getLocation();
                float yaw = baseLoc.getYaw();
                float pitch = baseLoc.getPitch(); // Get pitch 
                
                // --- Positioning Math ---
                // Adjusted based on user feedback (Lower, Closer, Scaled)
                
                // Head: Closer (0.18), Lower (0.45), Slightly Right (0.02), Tinier (0.70)
                // Pitch inverted: If wolf looks up (-pitch), head should look down (+pitch relative to flipped?)
                // Since we rotated 180, the pitch axis might be flipped too.
                // Let's try inverting the pitch passed to the head.
                updatePart(parts.get(0), baseLoc, 0.18, 0.45, 0.01, 0.70f, yaw + 180, -pitch); 
                
                // Body: Center (-0.1), Lower (0.45)
                updatePart(parts.get(1), baseLoc, -0.1, 0.45, 0, 0.7f, yaw, 0); 
                
                // Legs: Pushed to middle (0.07) and shifted "left" (from player view)
                double legX = 0.07;
                double legZ = 0.12;
                double shift = -0.03; // Total shift to one side
                
                // Leg 1 (FL)
                updateLeg(parts.get(2), baseLoc, legX + shift, legZ, yaw);
                // Leg 2 (FR)
                updateLeg(parts.get(3), baseLoc, -legX + shift, legZ, yaw);
                // Leg 3 (BL)
                updateLeg(parts.get(4), baseLoc, legX + shift, -legZ, yaw);
                // Leg 4 (BR)
                updateLeg(parts.get(5), baseLoc, -legX + shift, -legZ, yaw);
            }
        }, 1L, 1L);
    }
    
    private void updatePart(Entity entity, Location base, double forward, double up, double right, float scale, float yaw, float pitch) {
        if (!(entity instanceof ItemDisplay display)) return;

        // Convert local offsets to global based on Wolves native yaw
        // We use the original yaw for positioning so parts stay in relative place
        // But we use the passed 'yaw' (which might include 180 offset) for the entity rotation itself
        
        // POSITIONAL MATH (Use base yaw, not the rotated yaw)
        // We need to re-extract the base yaw because 'yaw' arg might be modified
        float baseYaw = base.getYaw(); 
        
        // If we passed (yaw + 180), we need to check if we should use that for position.
        // Actually, let's keep it simple. The 'yaw' arg determines rotation.
        // But for "Forward/Right", we always mean relative to the wolf's front.
        // So we strictly use base.getYaw() for math.
        
        double rads = Math.toRadians(-baseYaw); 
        double dx = (forward * Math.sin(rads)) + (right * Math.cos(rads));
        double dz = (forward * Math.cos(rads)) - (right * Math.sin(rads));
        
        Location target = base.clone().add(dx, up, dz);
        target.setYaw(yaw); // Use the requested rotation (e.g. flipped head)
        target.setPitch(pitch); // Apply pitch
        
        display.teleport(target);
        
        Transformation t = display.getTransformation();
        t.getScale().set(scale);
        display.setTransformation(t);
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
    }

    private void updateLeg(Entity entity, Location base, double right, double forward, float yaw) {
         if (!(entity instanceof BlockDisplay display)) return;

        double rads = Math.toRadians(-yaw);
        double dx = (forward * Math.sin(rads)) + (right * Math.cos(rads));
        double dz = (forward * Math.cos(rads)) - (right * Math.sin(rads));
        
        // Lowered Y to 0.05
        Location target = base.clone().add(dx, 0.05, dz); 
        target.setYaw(yaw);
        
        display.teleport(target);
        
        Transformation t = display.getTransformation();
        t.getScale().set(0.08f, 0.15f, 0.08f); // Even smaller legs
        display.setTransformation(t);
        display.setInterpolationDuration(1);
        display.setInterpolationDelay(0);
    }
    
    // Cleanup on disable
    public void stop() {
        for (List<Entity> parts : activePets.values()) {
            parts.forEach(Entity::remove);
        }
        activePets.clear();
    }
}

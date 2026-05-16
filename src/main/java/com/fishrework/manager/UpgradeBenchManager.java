package com.fishrework.manager;

import com.fishrework.FishRework;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;

import java.util.Collection;

public class UpgradeBenchManager {

    private final FishRework plugin;
    public final NamespacedKey UPGRADE_BENCH_ENTITY_KEY;
    public final NamespacedKey UPGRADE_BENCH_ANCHOR_KEY;

    public UpgradeBenchManager(FishRework plugin) {
        this.plugin = plugin;
        this.UPGRADE_BENCH_ENTITY_KEY = new NamespacedKey(plugin, "upgrade_bench_entity");
        this.UPGRADE_BENCH_ANCHOR_KEY = new NamespacedKey(plugin, "upgrade_bench_anchor");
    }

    public void createUpgradeBench(Location location, float yaw) {
        location.getBlock().setType(Material.IRON_TRAPDOOR, false);
        if (location.getBlock().getBlockData() instanceof TrapDoor trapdoor) {
            trapdoor.setHalf(Bisected.Half.BOTTOM);
            trapdoor.setOpen(false);
            location.getBlock().setBlockData(trapdoor, false);
        }

        String anchorKey = locationKey(location.getBlock());
        float snappedYaw = (Math.round(yaw / 90f) * 90f + 180f) % 360f;

        BlockDisplay root = (BlockDisplay) location.getWorld().spawnEntity(rootLocation(location, snappedYaw), EntityType.BLOCK_DISPLAY);
        root.setBlock(Bukkit.createBlockData(Material.AIR));
        root.setRotation(snappedYaw, 0f);
        configureDisplay(root, anchorKey);

        spawnBlockPart(root, "minecraft:crafter[crafting=false,orientation=down_east,triggered=false]",
                matrix(0f, -1.01f, 0f, 0.005f,
                        0.3225f, 0f, 0f, -0.005f,
                        0f, 0f, 1.01f, -0.005f,
                        0f, 0f, 0f, 1f),
                anchorKey);
        spawnBlockPart(root, "minecraft:anvil[facing=east]",
                matrix(1f, 0f, 0f, -1f,
                        0f, 0.875f, 0f, 0f,
                        0f, 0f, 1f, 0f,
                        0f, 0f, 0f, 1f),
                anchorKey);
        spawnBlockPart(root, "minecraft:stonecutter[facing=east]",
                matrix(0.4375f, 0f, 0f, -0.48125f,
                        0f, 0.375f, 0f, 0.6875f,
                        0f, 0f, 0.588f, 0.20875f,
                        0f, 0f, 0f, 1f),
                anchorKey);
        spawnBlockPart(root, "minecraft:loom[facing=east]",
                matrix(0f, 0f, -0.375f, -0.5625f,
                        0f, 0.1875f, 0f, 0.72875f,
                        0.25f, 0f, 0f, 0.24625f,
                        0f, 0f, 0f, 1f),
                anchorKey);
        spawnItemPart(root, new ItemStack(Material.MACE),
                matrix(0.25f, 0f, 0f, -0.8125f,
                        0f, 0f, 0.5625f, 0.875f,
                        0f, -0.25f, 0f, 0.63875f,
                        0f, 0f, 0f, 1f),
                anchorKey);
        spawnBlockPart(root, "minecraft:iron_block",
                matrix(0.137f, 0f, 0f, -0.65375f,
                        0f, 0.125f, 0f, 0.79125f,
                        0f, 0f, 0.205f, 0.52875f,
                        0f, 0f, 0f, 1f),
                anchorKey);
    }

    private void spawnBlockPart(Entity root, String blockData, Matrix4f matrix, String anchorKey) {
        BlockDisplay part = (BlockDisplay) root.getWorld().spawnEntity(root.getLocation(), EntityType.BLOCK_DISPLAY);
        part.setBlock(Bukkit.createBlockData(blockData));
        part.setTransformationMatrix(matrix);
        configureDisplay(part, anchorKey);
        root.addPassenger(part);
    }

    private void spawnItemPart(Entity root, ItemStack item, Matrix4f matrix, String anchorKey) {
        ItemDisplay part = (ItemDisplay) root.getWorld().spawnEntity(root.getLocation(), EntityType.ITEM_DISPLAY);
        part.setItemStack(item);
        part.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        part.setTransformationMatrix(matrix);
        configureDisplay(part, anchorKey);
        root.addPassenger(part);
    }

    private Matrix4f matrix(float m00, float m01, float m02, float m03,
                           float m10, float m11, float m12, float m13,
                           float m20, float m21, float m22, float m23,
                           float m30, float m31, float m32, float m33) {
        return new Matrix4f(
                m00, m10, m20, m30,
                m01, m11, m21, m31,
                m02, m12, m22, m32,
                m03, m13, m23, m33);
    }

    private void configureDisplay(Display display, String anchorKey) {
        display.setPersistent(true);
        display.getPersistentDataContainer().set(UPGRADE_BENCH_ENTITY_KEY, PersistentDataType.BYTE, (byte) 1);
        display.getPersistentDataContainer().set(UPGRADE_BENCH_ANCHOR_KEY, PersistentDataType.STRING, anchorKey);
        display.setInvulnerable(true);
        display.setViewRange(1.0f);
        display.setBillboard(Display.Billboard.FIXED);
        display.setShadowRadius(0);
        display.setShadowStrength(0);
    }

    private Location rootLocation(Location anchor, float snappedYaw) {
        int yaw = Math.floorMod(Math.round(snappedYaw), 360);
        Location root = anchor.clone();
        switch (yaw) {
            case 0 -> root.add(1.0, 0.0, 0.0);
            case 90 -> root.add(1.0, 0.0, 1.0);
            case 180 -> root.add(0.0, 0.0, 1.0);
            default -> {
                // 270 degrees is the exported/west-facing orientation and needs no compensation.
            }
        }
        return root;
    }

    public void removeUpgradeBench(Block block) {
        boolean removed = false;
        for (Entity entity : getNearbyBenchEntities(block)) {
            if (isUpgradeBenchEntityForBlock(entity, block)) {
                entity.remove();
                removed = true;
            }
        }

        if (removed) {
            block.getWorld().dropItemNaturally(block.getLocation(), plugin.getItemManager().getRequiredItem("upgrade_bench"));
        }
    }

    public boolean isUpgradeBenchBlock(Block block) {
        for (Entity entity : getNearbyBenchEntities(block)) {
            if (isUpgradeBenchEntityForBlock(entity, block)) {
                return true;
            }
        }
        return false;
    }

    private Collection<Entity> getNearbyBenchEntities(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        return block.getWorld().getNearbyEntities(center, 2.0, 2.0, 2.0);
    }

    private boolean isUpgradeBenchEntityForBlock(Entity entity, Block block) {
        if (!entity.getPersistentDataContainer().has(UPGRADE_BENCH_ENTITY_KEY, PersistentDataType.BYTE)) {
            return false;
        }
        String anchorKey = entity.getPersistentDataContainer().get(UPGRADE_BENCH_ANCHOR_KEY, PersistentDataType.STRING);
        return locationKey(block).equals(anchorKey);
    }

    private String locationKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}

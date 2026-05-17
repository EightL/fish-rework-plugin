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

    public enum StationType {
        UPGRADE_BENCH("upgrade_bench"),
        BOOK_MERGER("book_merger");

        private final String itemId;

        StationType(String itemId) {
            this.itemId = itemId;
        }

        public String itemId() {
            return itemId;
        }

        private static StationType fromItemId(String itemId) {
            if (itemId == null) return UPGRADE_BENCH;
            for (StationType type : values()) {
                if (type.itemId.equals(itemId)) {
                    return type;
                }
            }
            return UPGRADE_BENCH;
        }
    }

    private final FishRework plugin;
    public final NamespacedKey UPGRADE_BENCH_ENTITY_KEY;
    public final NamespacedKey UPGRADE_BENCH_ANCHOR_KEY;
    public final NamespacedKey UPGRADE_STATION_TYPE_KEY;

    public UpgradeBenchManager(FishRework plugin) {
        this.plugin = plugin;
        this.UPGRADE_BENCH_ENTITY_KEY = new NamespacedKey(plugin, "upgrade_bench_entity");
        this.UPGRADE_BENCH_ANCHOR_KEY = new NamespacedKey(plugin, "upgrade_bench_anchor");
        this.UPGRADE_STATION_TYPE_KEY = new NamespacedKey(plugin, "upgrade_station_type");
    }

    public void createUpgradeBench(Location location, float yaw) {
        createStation(location, yaw, StationType.UPGRADE_BENCH);
    }

    public void createBookMerger(Location location, float yaw) {
        createStation(location, yaw, StationType.BOOK_MERGER);
    }

    private void createStation(Location location, float yaw, StationType type) {
        location.getBlock().setType(Material.IRON_TRAPDOOR, false);
        if (location.getBlock().getBlockData() instanceof TrapDoor trapdoor) {
            trapdoor.setHalf(Bisected.Half.BOTTOM);
            trapdoor.setOpen(false);
            location.getBlock().setBlockData(trapdoor, false);
        }

        String anchorKey = locationKey(location.getBlock());
        float snappedYaw = (Math.round(yaw / 90f) * 90f + 180f) % 360f;

        Location displayRootLocation = type == StationType.BOOK_MERGER
                ? location.clone().add(0.5, 0.0, 0.5)
                : rootLocation(location, snappedYaw);
        float displayYaw = type == StationType.BOOK_MERGER
                ? (Math.round(yaw / 90f) * 90f + 90f) % 360f
                : snappedYaw;

        BlockDisplay root = (BlockDisplay) location.getWorld().spawnEntity(displayRootLocation, EntityType.BLOCK_DISPLAY);
        root.setBlock(Bukkit.createBlockData(Material.AIR));
        root.setRotation(displayYaw, 0f);
        configureDisplay(root, anchorKey, type);

        if (type == StationType.BOOK_MERGER) {
            spawnBookMergerParts(root, anchorKey, type);
        } else {
            spawnUpgradeBenchParts(root, anchorKey, type);
        }
    }

    private void spawnUpgradeBenchParts(Entity root, String anchorKey, StationType type) {
        spawnBlockPart(root, "minecraft:crafter[crafting=false,orientation=down_east,triggered=false]",
                matrix(0f, -1.01f, 0f, 0.005f,
                        0.3225f, 0f, 0f, -0.005f,
                        0f, 0f, 1.01f, -0.005f,
                        0f, 0f, 0f, 1f),
                anchorKey, type);
        spawnBlockPart(root, "minecraft:anvil[facing=east]",
                matrix(1f, 0f, 0f, -1f,
                        0f, 0.875f, 0f, 0f,
                        0f, 0f, 1f, 0f,
                        0f, 0f, 0f, 1f),
                anchorKey, type);
        spawnBlockPart(root, "minecraft:stonecutter[facing=east]",
                matrix(0.4375f, 0f, 0f, -0.48125f,
                        0f, 0.375f, 0f, 0.6875f,
                        0f, 0f, 0.588f, 0.20875f,
                        0f, 0f, 0f, 1f),
                anchorKey, type);
        spawnBlockPart(root, "minecraft:loom[facing=east]",
                matrix(0f, 0f, -0.375f, -0.5625f,
                        0f, 0.1875f, 0f, 0.72875f,
                        0.25f, 0f, 0f, 0.24625f,
                        0f, 0f, 0f, 1f),
                anchorKey, type);
        spawnItemPart(root, new ItemStack(Material.MACE),
                matrix(0.25f, 0f, 0f, -0.8125f,
                        0f, 0f, 0.5625f, 0.875f,
                        0f, -0.25f, 0f, 0.63875f,
                        0f, 0f, 0f, 1f),
                anchorKey, type);
        spawnBlockPart(root, "minecraft:iron_block",
                matrix(0.137f, 0f, 0f, -0.65375f,
                        0f, 0.125f, 0f, 0.79125f,
                        0f, 0f, 0.205f, 0.52875f,
                        0f, 0f, 0f, 1f),
                anchorKey, type);
    }

    private void spawnBookMergerParts(Entity root, String anchorKey, StationType type) {
        spawnBlockPart(root, "minecraft:enchanting_table",
                bookMatrix(1.013f, 0f, 0f, -0.00625f,
                        0f, 0.5f, 0f, 0f,
                        0f, 0f, 1.024f, -0.01125f,
                        0f, 0f, 0f, 1f),
                anchorKey, type);
        spawnBlockPart(root, "minecraft:calibrated_sculk_sensor[facing=east,sculk_sensor_phase=active]",
                bookMatrix(0.634f, 0f, 0f, 0.175f,
                        0f, 0.491f, 0f, 0.375f,
                        0f, 0f, 0.603f, 0.198125f,
                        0f, 0f, 0f, 1f),
                anchorKey, type);
        spawnBlockPart(root, "minecraft:respawn_anchor[charges=0]",
                bookMatrix(0.76f, 0f, 0f, 0.115f,
                        0f, 0.625f, 0f, 0.00625f,
                        0f, 0f, 0.75f, 0.125f,
                        0f, 0f, 0f, 1f),
                anchorKey, type);
        spawnBookMergerCorner(root, anchorKey, type, 0.01f, 0.01f, -0.008125f, 0.003125f, -0.015f, 0.208f, 0.215f);
        spawnBookMergerCorner(root, anchorKey, type, 0.01f, 0.8125f, -0.006875f, 0.00375f, 0.80125f, 0.207f, 0.215f);
        spawnBookMergerCorner(root, anchorKey, type, 0.8125f, 0.8125f, 0.80125f, 0.003125f, 0.80125f, 0.206f, 0.212f);
        spawnBookMergerCorner(root, anchorKey, type, 0.8125f, 0f, 0.80125f, 0.003125f, -0.013125f, 0.208f, 0.203f);
        spawnItemPart(root, new ItemStack(Material.ENCHANTED_BOOK),
                bookMatrix(0f, 0f, -0.2f, 0.106875f,
                        0f, 0.2f, 0f, 0.5f,
                        0.2f, 0f, 0f, 0.5f,
                        0f, 0f, 0f, 1f),
                anchorKey, type);
    }

    private void spawnBookMergerCorner(Entity root, String anchorKey, StationType type,
                                       float fireX, float fireZ,
                                       float tileX, float tileY, float tileZ,
                                       float tileScaleX, float tileScaleZ) {
        spawnBlockPart(root, "minecraft:soul_fire",
                bookMatrix(0.179f, 0f, 0f, fireX,
                        0f, 0.2f, 0f, 0.5625f,
                        0f, 0f, 0.18f, fireZ,
                        0f, 0f, 0f, 1f),
                anchorKey, type);
        spawnBlockPart(root, "minecraft:cracked_deepslate_tiles",
                bookMatrix(tileScaleX, 0f, 0f, tileX,
                        0f, 0.559f, 0f, tileY,
                        0f, 0f, tileScaleZ, tileZ,
                        0f, 0f, 0f, 1f),
                anchorKey, type);
    }

    private void spawnBlockPart(Entity root, String blockData, Matrix4f matrix, String anchorKey, StationType type) {
        BlockDisplay part = (BlockDisplay) root.getWorld().spawnEntity(root.getLocation(), EntityType.BLOCK_DISPLAY);
        part.setBlock(Bukkit.createBlockData(blockData));
        part.setTransformationMatrix(matrix);
        configureDisplay(part, anchorKey, type);
        root.addPassenger(part);
    }

    private void spawnItemPart(Entity root, ItemStack item, Matrix4f matrix, String anchorKey, StationType type) {
        ItemDisplay part = (ItemDisplay) root.getWorld().spawnEntity(root.getLocation(), EntityType.ITEM_DISPLAY);
        part.setItemStack(item);
        part.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        part.setTransformationMatrix(matrix);
        configureDisplay(part, anchorKey, type);
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

    private Matrix4f bookMatrix(float m00, float m01, float m02, float m03,
                                float m10, float m11, float m12, float m13,
                                float m20, float m21, float m22, float m23,
                                float m30, float m31, float m32, float m33) {
        return matrix(
                m00, m01, m02, m03 - 0.5f,
                m10, m11, m12, m13,
                m20, m21, m22, m23 - 0.5f,
                m30, m31, m32, m33);
    }

    private void configureDisplay(Display display, String anchorKey, StationType type) {
        display.setPersistent(true);
        display.getPersistentDataContainer().set(UPGRADE_BENCH_ENTITY_KEY, PersistentDataType.BYTE, (byte) 1);
        display.getPersistentDataContainer().set(UPGRADE_BENCH_ANCHOR_KEY, PersistentDataType.STRING, anchorKey);
        display.getPersistentDataContainer().set(UPGRADE_STATION_TYPE_KEY, PersistentDataType.STRING, type.itemId());
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
        removeStation(block);
    }

    public void removeStation(Block block) {
        StationType type = getStationType(block);
        boolean removed = false;
        for (Entity entity : getNearbyBenchEntities(block)) {
            if (isUpgradeBenchEntityForBlock(entity, block)) {
                entity.remove();
                removed = true;
            }
        }

        if (removed) {
            StationType dropType = type == null ? StationType.UPGRADE_BENCH : type;
            block.getWorld().dropItemNaturally(block.getLocation(), plugin.getItemManager().getRequiredItem(dropType.itemId()));
        }
    }

    public boolean isUpgradeBenchBlock(Block block) {
        return getStationType(block) != null;
    }

    public StationType getStationType(Block block) {
        for (Entity entity : getNearbyBenchEntities(block)) {
            if (isUpgradeBenchEntityForBlock(entity, block)) {
                String typeId = entity.getPersistentDataContainer().get(UPGRADE_STATION_TYPE_KEY, PersistentDataType.STRING);
                return StationType.fromItemId(typeId);
            }
        }
        return null;
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

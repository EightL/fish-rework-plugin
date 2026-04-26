package com.fishrework.task;

import com.fishrework.FishRework;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attaches BlockDisplay cosmetics to a living mob. Each tick we teleport every
 * display to the mob's current position rotated by its body yaw, so the parent
 * never carries passengers — vanilla melee attack reach stays intact.
 */
public final class AttachedBlockCosmetics implements Runnable {

    private static final int TELEPORT_DURATION = 1;

    private final FishRework plugin;
    private final NamespacedKey cosmeticKey;
    private final Map<UUID, List<Segment>> segmentsByParent = new ConcurrentHashMap<>();

    public AttachedBlockCosmetics(FishRework plugin) {
        this.plugin = plugin;
        this.cosmeticKey = new NamespacedKey(plugin, "attached_block_cosmetic");
    }

    public static final class BlockSpec {
        final Material material;
        final float localX;
        final float localY;
        final float localZ;
        final float size;

        public BlockSpec(Material material, float localX, float localY, float localZ, float size) {
            this.material = material;
            this.localX = localX;
            this.localY = localY;
            this.localZ = localZ;
            this.size = size;
        }
    }

    public void attach(LivingEntity parent, Material material, float localX, float localY, float localZ, float size) {
        attach(parent, List.of(new BlockSpec(material, localX, localY, localZ, size)));
    }

    public void attach(LivingEntity parent, List<BlockSpec> specs) {
        if (parent == null || parent.isDead() || !parent.isValid() || specs == null || specs.isEmpty()) return;
        cleanup(parent);

        World world = parent.getWorld();
        Location spawnAt = parent.getLocation();
        List<Segment> segments = new ArrayList<>(specs.size());

        for (BlockSpec spec : specs) {
            if (spec.material == null) continue;

            BlockDisplay display = (BlockDisplay) world.spawnEntity(spawnAt, EntityType.BLOCK_DISPLAY);
            display.setBlock(Bukkit.createBlockData(spec.material));
            display.getPersistentDataContainer().set(cosmeticKey, PersistentDataType.BYTE, (byte) 1);
            configureDisplay(display);
            applyCenteredTransform(display, spec);
            segments.add(new Segment(display.getUniqueId(), spec));
        }

        if (segments.isEmpty()) return;
        segmentsByParent.put(parent.getUniqueId(), segments);
    }

    public void cleanup(LivingEntity parent) {
        if (parent == null) return;
        List<Segment> segments = segmentsByParent.remove(parent.getUniqueId());
        if (segments != null) {
            for (Segment seg : segments) {
                Entity child = Bukkit.getEntity(seg.childId);
                if (child != null) child.remove();
            }
        }
        // Sweep any orphaned cosmetic passengers left from the old passenger-chain implementation.
        for (Entity passenger : new ArrayList<>(parent.getPassengers())) {
            if (passenger.getPersistentDataContainer().has(cosmeticKey, PersistentDataType.BYTE)) {
                passenger.remove();
            }
        }
    }

    public void cleanupAll() {
        for (List<Segment> segments : segmentsByParent.values()) {
            for (Segment seg : segments) {
                Entity child = Bukkit.getEntity(seg.childId);
                if (child != null) child.remove();
            }
        }
        segmentsByParent.clear();
    }

    @Override
    public void run() {
        Iterator<Map.Entry<UUID, List<Segment>>> it = segmentsByParent.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, List<Segment>> entry = it.next();
            Entity parent = Bukkit.getEntity(entry.getKey());
            if (!(parent instanceof LivingEntity living) || !parent.isValid() || living.isDead()) {
                for (Segment seg : entry.getValue()) {
                    Entity child = Bukkit.getEntity(seg.childId);
                    if (child != null) child.remove();
                }
                it.remove();
                continue;
            }

            float bodyYaw = living instanceof org.bukkit.entity.Mob mob ? mob.getBodyYaw() : living.getYaw();
            double yawRad = Math.toRadians(bodyYaw);
            float cosYaw = (float) Math.cos(yawRad);
            float sinYaw = (float) Math.sin(yawRad);
            Location parentLoc = living.getLocation();
            World world = living.getWorld();

            Iterator<Segment> segIt = entry.getValue().iterator();
            while (segIt.hasNext()) {
                Segment seg = segIt.next();
                Entity child = Bukkit.getEntity(seg.childId);
                if (!(child instanceof BlockDisplay display) || !child.isValid()) {
                    segIt.remove();
                    continue;
                }

                // Spider body yaw 0 faces +Z in Minecraft, so rotate (localX, localZ) into world space.
                // Standard Minecraft yaw rotation: worldX = -sin*Z + cos*X (yaw 0 = south = +Z)
                // For a "back" offset (negative localZ), positive yaw rotation should move it behind the mob.
                float lx = seg.spec.localX;
                float lz = seg.spec.localZ;
                double worldOffX = -lx * cosYaw - lz * sinYaw;
                double worldOffZ = -lx * sinYaw + lz * cosYaw;

                Location target = new Location(
                        world,
                        parentLoc.getX() + worldOffX,
                        parentLoc.getY() + seg.spec.localY,
                        parentLoc.getZ() + worldOffZ,
                        bodyYaw,
                        0.0f
                );
                display.teleport(target);
            }

            if (entry.getValue().isEmpty()) it.remove();
        }
    }

    private void applyCenteredTransform(BlockDisplay display, BlockSpec spec) {
        Transformation transform = display.getTransformation();
        transform.getScale().set(spec.size, spec.size, spec.size);
        // Center the block on the display's anchor; world position is set via teleport each tick.
        transform.getTranslation().set(-spec.size * 0.5f, -spec.size * 0.5f, -spec.size * 0.5f);
        transform.getLeftRotation().identity();
        transform.getRightRotation().identity();
        display.setTransformation(transform);
    }

    private void configureDisplay(Display display) {
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setViewRange(5.0f);
        display.setBillboard(Display.Billboard.FIXED);
        display.setShadowRadius(0.0f);
        display.setShadowStrength(0.0f);
        display.setBrightness(new Display.Brightness(13, 15));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(0);
        display.setTeleportDuration(TELEPORT_DURATION);
    }

    private static final class Segment {
        private final UUID childId;
        private final BlockSpec spec;

        private Segment(UUID childId, BlockSpec spec) {
            this.childId = childId;
            this.spec = spec;
        }
    }
}

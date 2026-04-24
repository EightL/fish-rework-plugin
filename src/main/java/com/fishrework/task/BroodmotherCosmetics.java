package com.fishrework.task;

import com.fishrework.FishRework;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws the Ghast Broodmother's extra silhouette as nether wart chunks anchored
 * to body-local points. The display blocks are teleported from the broodmother's
 * bounding-box center every tick, so the strand roots stay glued to the ghast cube
 * and the whole shape turns with her yaw.
 */
public final class BroodmotherCosmetics implements Runnable {

    private static final int TICK_PERIOD = 1;
    private static final int INTERPOLATION_DURATION = 2;
    private static final int TELEPORT_DURATION = 2;
    private static final double FACE_TARGET_RANGE_SQ = 60.0 * 60.0;
    private static final float MAX_FACE_TURN_DEGREES_PER_TICK = 4.5f;
    private static final double POSITION_SMOOTHING = 0.36;
    private static final float YAW_SMOOTHING = 0.28f;
    private static final double SMOOTH_SNAP_DISTANCE_SQ = 8.0 * 8.0;

    private static final StrandSpec[] STRANDS = {
            new StrandSpec(-0.96f, 0.36f, 0.08f, -1.00f, 0.12f, 0.14f, 0.00f, 0.25f, -0.62f, 5, 0.20f),
            new StrandSpec(-0.96f, -0.18f, -0.34f, -0.92f, -0.38f, -0.14f, 0.00f, -0.16f, -0.54f, 5, 1.15f),
            new StrandSpec(0.96f, 0.30f, -0.12f, 0.98f, 0.08f, -0.18f, 0.00f, 0.20f, 0.58f, 5, 2.20f),
            new StrandSpec(0.96f, -0.08f, 0.36f, 0.93f, -0.32f, 0.18f, 0.00f, -0.12f, 0.60f, 5, 3.05f),
            new StrandSpec(-0.35f, 0.96f, -0.18f, -0.28f, 0.72f, -0.30f, 0.58f, 0.10f, 0.18f, 4, 3.80f),
            new StrandSpec(0.38f, 0.96f, 0.12f, 0.34f, 0.64f, 0.18f, -0.50f, 0.16f, 0.34f, 4, 4.55f),
            new StrandSpec(-0.68f, 0.10f, -0.96f, -0.40f, 0.02f, -0.92f, 0.45f, -0.10f, 0.00f, 4, 5.45f),
            new StrandSpec(0.42f, -0.22f, -0.96f, 0.16f, -0.24f, -0.98f, -0.50f, -0.08f, 0.00f, 4, 6.20f),
            new StrandSpec(-0.12f, -0.34f, -0.96f, -0.06f, -0.34f, -0.94f, 0.36f, -0.16f, 0.00f, 4, 7.05f),
    };

    private final FishRework plugin;
    private final NamespacedKey cosmeticKey;
    private final NamespacedKey segmentKey;
    private final Map<UUID, List<UUID>> cosmeticsByParent = new ConcurrentHashMap<>();
    private final Map<UUID, SegmentState> segmentStateByEntity = new ConcurrentHashMap<>();
    private final Map<UUID, SmoothFrame> smoothFrameByParent = new ConcurrentHashMap<>();
    private long tickCounter = 0L;

    public BroodmotherCosmetics(FishRework plugin) {
        this.plugin = plugin;
        this.cosmeticKey = new NamespacedKey(plugin, "broodmother_cosmetic");
        this.segmentKey = new NamespacedKey(plugin, "broodmother_tentacle_segment");
    }

    public int getTickPeriod() {
        return TICK_PERIOD;
    }

    public void attach(LivingEntity broodmother) {
        if (!(broodmother instanceof Ghast)) return;

        cleanup(broodmother);

        World world = broodmother.getWorld();
        Location spawnAt = bodyCenter(broodmother);
        List<UUID> children = new ArrayList<>();
        int flatIndex = 0;

        for (int strandIndex = 0; strandIndex < STRANDS.length; strandIndex++) {
            StrandSpec strand = STRANDS[strandIndex];
            for (int segmentIndex = 0; segmentIndex < strand.segments; segmentIndex++) {
                BlockDisplay segment = spawnSegment(world, spawnAt, flatIndex);
                children.add(segment.getUniqueId());
                segmentStateByEntity.put(segment.getUniqueId(), new SegmentState(strandIndex, segmentIndex));
                flatIndex++;
            }
        }

        UUID parentId = broodmother.getUniqueId();
        smoothFrameByParent.put(parentId, SmoothFrame.from(BodyFrame.from(broodmother)));
        cosmeticsByParent.put(parentId, children);
        animate(broodmother, children);
    }

    public void cleanup(LivingEntity broodmother) {
        UUID parentId = broodmother.getUniqueId();
        List<UUID> children = cosmeticsByParent.remove(parentId);
        smoothFrameByParent.remove(parentId);
        if (children != null) {
            removeChildren(children);
        }
        removeLegacyPassengers(broodmother);
    }

    public void cleanupAll() {
        for (List<UUID> children : cosmeticsByParent.values()) {
            removeChildren(children);
        }
        cosmeticsByParent.clear();
        segmentStateByEntity.clear();
        smoothFrameByParent.clear();
    }

    @Override
    public void run() {
        tickCounter++;
        Iterator<Map.Entry<UUID, List<UUID>>> iterator = cosmeticsByParent.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, List<UUID>> entry = iterator.next();
            Entity parent = Bukkit.getEntity(entry.getKey());
            if (!(parent instanceof LivingEntity alive) || !parent.isValid() || alive.isDead()) {
                removeChildren(entry.getValue());
                smoothFrameByParent.remove(entry.getKey());
                iterator.remove();
                continue;
            }

            faceNearestPlayer(alive);
            animate(alive, entry.getValue());
        }
    }

    private void animate(LivingEntity broodmother, List<UUID> children) {
        BodyFrame targetFrame = BodyFrame.from(broodmother);
        SmoothFrame smoothFrame = smoothFrameByParent.computeIfAbsent(
                broodmother.getUniqueId(),
                ignored -> SmoothFrame.from(targetFrame)
        );
        BodyFrame frame = smoothFrame.advance(targetFrame);

        Iterator<UUID> iterator = children.iterator();
        while (iterator.hasNext()) {
            UUID childId = iterator.next();
            Entity entity = Bukkit.getEntity(childId);
            SegmentState state = segmentStateByEntity.get(childId);
            if (!(entity instanceof BlockDisplay display) || state == null || !entity.isValid()) {
                segmentStateByEntity.remove(childId);
                iterator.remove();
                continue;
            }

            placeSegment(display, state, frame);
        }
    }

    private void placeSegment(BlockDisplay display, SegmentState state, BodyFrame frame) {
        StrandSpec strand = STRANDS[state.strandIndex];
        int segment = state.segmentIndex;
        float depth = strand.segments <= 1 ? 0.0f : segment / (float) (strand.segments - 1);

        float distance = frame.spacing * (segment + 0.35f);
        float bend = depth * depth * frame.spacing * 1.55f;
        float wave = (float) Math.sin(tickCounter * 0.08 + strand.phase + segment * 0.55f)
                * frame.spacing * 0.18f * depth;
        float bob = (float) Math.cos(tickCounter * 0.07 + strand.phase + segment * 0.35f)
                * frame.spacing * 0.10f * depth;

        float localX = strand.anchorX * frame.halfX
                + strand.dirX * distance
                + strand.bendX * bend
                + strand.bendZ * wave;
        float localY = strand.anchorY * frame.halfY
                + strand.dirY * distance
                + strand.bendY * bend
                + bob;
        float localZ = strand.anchorZ * frame.halfZ
                + strand.dirZ * distance
                + strand.bendZ * bend
                - strand.bendX * wave;

        double worldX = frame.centerX + localX * frame.cosYaw - localZ * frame.sinYaw;
        double worldZ = frame.centerZ + localX * frame.sinYaw + localZ * frame.cosYaw;

        float taper = Math.max(0.62f, 1.0f - depth * 0.30f);
        float rootBoost = segment == 0 ? 1.18f : 1.0f;
        float size = frame.blockSize * taper * rootBoost;

        display.teleport(new Location(frame.world, worldX, frame.centerY + localY, worldZ, frame.yaw, 0.0f));

        Transformation transform = display.getTransformation();
        transform.getScale().set(size, size, size);
        transform.getTranslation().set(-size * 0.5f, -size * 0.5f, -size * 0.5f);
        transform.getLeftRotation().identity();
        transform.getRightRotation().identity();
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(INTERPOLATION_DURATION);
        display.setTransformation(transform);
    }

    private BlockDisplay spawnSegment(World world, Location loc, int flatIndex) {
        BlockDisplay segment = (BlockDisplay) world.spawnEntity(loc, EntityType.BLOCK_DISPLAY);
        segment.setBlock(Bukkit.createBlockData(Material.NETHER_WART_BLOCK));
        segment.getPersistentDataContainer().set(cosmeticKey, PersistentDataType.BYTE, (byte) 1);
        segment.getPersistentDataContainer().set(segmentKey, PersistentDataType.INTEGER, flatIndex);
        configureDisplay(segment);
        return segment;
    }

    private void configureDisplay(Display display) {
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setViewRange(5.0f);
        display.setBillboard(Display.Billboard.FIXED);
        display.setShadowRadius(0.0f);
        display.setShadowStrength(0.0f);
        display.setBrightness(new Display.Brightness(13, 15));
        display.setInterpolationDuration(INTERPOLATION_DURATION);
        display.setTeleportDuration(TELEPORT_DURATION);
    }

    /**
     * Vanilla ghast body yaw is subtle, so we ease the broodmother toward her
     * current audience. The tentacles then inherit the same body-local frame.
     */
    private void faceNearestPlayer(LivingEntity broodmother) {
        Player target = null;
        double bestSq = FACE_TARGET_RANGE_SQ;
        Location broodLoc = broodmother.getLocation();

        for (Player player : broodmother.getWorld().getPlayers()) {
            if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) continue;
            double distSq = player.getLocation().distanceSquared(broodLoc);
            if (distSq < bestSq) {
                bestSq = distSq;
                target = player;
            }
        }
        if (target == null) return;

        double dx = target.getLocation().getX() - broodLoc.getX();
        double dz = target.getLocation().getZ() - broodLoc.getZ();
        if (dx * dx + dz * dz < 0.0001) return;

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float yaw = approachAngle(broodmother.getYaw(), targetYaw, MAX_FACE_TURN_DEGREES_PER_TICK);
        broodmother.setRotation(yaw, broodmother.getPitch());
    }

    private static float approachAngle(float current, float target, float maxStep) {
        float delta = wrapDegrees(target - current);
        if (Math.abs(delta) <= maxStep) return target;
        return current + Math.copySign(maxStep, delta);
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    private Location bodyCenter(LivingEntity broodmother) {
        BoundingBox box = broodmother.getBoundingBox();
        return new Location(
                broodmother.getWorld(),
                (box.getMinX() + box.getMaxX()) * 0.5,
                (box.getMinY() + box.getMaxY()) * 0.5,
                (box.getMinZ() + box.getMaxZ()) * 0.5
        );
    }

    private void removeChildren(List<UUID> children) {
        for (UUID childId : children) {
            segmentStateByEntity.remove(childId);
            Entity child = Bukkit.getEntity(childId);
            if (child != null) child.remove();
        }
    }

    private void removeLegacyPassengers(LivingEntity broodmother) {
        for (Entity passenger : new ArrayList<>(broodmother.getPassengers())) {
            if (passenger.getPersistentDataContainer().has(cosmeticKey, PersistentDataType.BYTE)) {
                segmentStateByEntity.remove(passenger.getUniqueId());
                passenger.remove();
            }
        }
    }

    private static final class BodyFrame {
        private final World world;
        private final double centerX;
        private final double centerY;
        private final double centerZ;
        private final float halfX;
        private final float halfY;
        private final float halfZ;
        private final float blockSize;
        private final float spacing;
        private final float yaw;
        private final float sinYaw;
        private final float cosYaw;

        private BodyFrame(
                World world,
                double centerX,
                double centerY,
                double centerZ,
                float halfX,
                float halfY,
                float halfZ,
                float blockSize,
                float spacing,
                float yaw,
                float sinYaw,
                float cosYaw
        ) {
            this.world = world;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.halfX = halfX;
            this.halfY = halfY;
            this.halfZ = halfZ;
            this.blockSize = blockSize;
            this.spacing = spacing;
            this.yaw = yaw;
            this.sinYaw = sinYaw;
            this.cosYaw = cosYaw;
        }

        private static BodyFrame from(LivingEntity broodmother) {
            BoundingBox box = broodmother.getBoundingBox();
            float halfX = Math.max(2.0f, (float) ((box.getMaxX() - box.getMinX()) * 0.5));
            float halfY = Math.max(2.0f, (float) ((box.getMaxY() - box.getMinY()) * 0.5));
            float halfZ = Math.max(2.0f, (float) ((box.getMaxZ() - box.getMinZ()) * 0.5));
            float bodyHalf = Math.max(halfX, halfZ);
            float baseBlockSize = clamp(bodyHalf * 0.27f, 0.82f, 1.18f);
            float blockSize = baseBlockSize * 2.0f;
            float spacing = baseBlockSize * 0.78f;
            float yaw = broodmother.getYaw();
            float yawRad = (float) Math.toRadians(yaw);

            return new BodyFrame(
                    broodmother.getWorld(),
                    (box.getMinX() + box.getMaxX()) * 0.5,
                    (box.getMinY() + box.getMaxY()) * 0.5,
                    (box.getMinZ() + box.getMaxZ()) * 0.5,
                    halfX,
                    halfY,
                    halfZ,
                    blockSize,
                    spacing,
                    yaw,
                    (float) Math.sin(yawRad),
                    (float) Math.cos(yawRad)
            );
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    private static final class SmoothFrame {
        private double centerX;
        private double centerY;
        private double centerZ;
        private float yaw;

        private SmoothFrame(double centerX, double centerY, double centerZ, float yaw) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.yaw = yaw;
        }

        private static SmoothFrame from(BodyFrame frame) {
            return new SmoothFrame(frame.centerX, frame.centerY, frame.centerZ, frame.yaw);
        }

        private BodyFrame advance(BodyFrame target) {
            double dx = target.centerX - centerX;
            double dy = target.centerY - centerY;
            double dz = target.centerZ - centerZ;
            if (dx * dx + dy * dy + dz * dz > SMOOTH_SNAP_DISTANCE_SQ) {
                centerX = target.centerX;
                centerY = target.centerY;
                centerZ = target.centerZ;
                yaw = target.yaw;
            } else {
                centerX += dx * POSITION_SMOOTHING;
                centerY += dy * POSITION_SMOOTHING;
                centerZ += dz * POSITION_SMOOTHING;
                yaw += wrapDegrees(target.yaw - yaw) * YAW_SMOOTHING;
            }

            float yawRad = (float) Math.toRadians(yaw);
            return new BodyFrame(
                    target.world,
                    centerX,
                    centerY,
                    centerZ,
                    target.halfX,
                    target.halfY,
                    target.halfZ,
                    target.blockSize,
                    target.spacing,
                    yaw,
                    (float) Math.sin(yawRad),
                    (float) Math.cos(yawRad)
            );
        }
    }

    private static final class SegmentState {
        private final int strandIndex;
        private final int segmentIndex;

        private SegmentState(int strandIndex, int segmentIndex) {
            this.strandIndex = strandIndex;
            this.segmentIndex = segmentIndex;
        }
    }

    private static final class StrandSpec {
        private final float anchorX;
        private final float anchorY;
        private final float anchorZ;
        private final float dirX;
        private final float dirY;
        private final float dirZ;
        private final float bendX;
        private final float bendY;
        private final float bendZ;
        private final int segments;
        private final float phase;

        private StrandSpec(
                float anchorX,
                float anchorY,
                float anchorZ,
                float dirX,
                float dirY,
                float dirZ,
                float bendX,
                float bendY,
                float bendZ,
                int segments,
                float phase
        ) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;

            float length = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            this.dirX = dirX / length;
            this.dirY = dirY / length;
            this.dirZ = dirZ / length;

            this.bendX = bendX;
            this.bendY = bendY;
            this.bendZ = bendZ;
            this.segments = segments;
            this.phase = phase;
        }
    }
}

package com.fishrework.task;

import com.fishrework.FishRework;
import com.fishrework.model.LavaBobberState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Runs every tick for each active lava bobber, managing the custom bite loop.
 * <p>
 * Flow: FLYING → IN_LAVA → (bite timer countdown) → NIBBLE → BITE → (reel window) → EXPIRED
 */
public class LavaBobberTask extends BukkitRunnable {

    private final FishRework plugin;
    private final UUID playerId;
    private final LavaBobberState state;
    private final Random random = new Random();

    private final double approachRadius;
    private final int approachDurationTicks;
    private final int approachParticlesPerTick;
    private final double biteDistanceSquared;
    private final double floatBobAmplitude;
    private final double floatBobSpeed;
    private final Particle approachParticle;

    private Location floatAnchor;
    private Location approachStart;
    private double approachProgress;
    private double approachStep;
    private int floatTicks;

    public LavaBobberTask(FishRework plugin, UUID playerId, LavaBobberState state) {
        this.plugin = plugin;
        this.playerId = playerId;
        this.state = state;

        this.approachRadius = plugin.getConfig().getDouble("lava_fishing.approach_radius", 2.6);
        this.approachDurationTicks = Math.max(6, plugin.getConfig().getInt("lava_fishing.approach_duration_ticks", 22));
        this.approachParticlesPerTick = Math.max(1, plugin.getConfig().getInt("lava_fishing.approach_particles_per_tick", 2));
        double biteDistance = Math.max(0.15, plugin.getConfig().getDouble("lava_fishing.approach_bite_distance", 0.45));
        this.biteDistanceSquared = biteDistance * biteDistance;
        this.floatBobAmplitude = Math.max(0.0, plugin.getConfig().getDouble("lava_fishing.float_bob_amplitude", 0.03));
        this.floatBobSpeed = plugin.getConfig().getDouble("lava_fishing.float_bob_speed", 0.28);
        this.approachParticle = parseParticle(plugin.getConfig().getString("lava_fishing.approach_particle", "FLAME"));
    }

    @Override
    public void run() {
        FishHook hook = state.getHook();
        Player player = plugin.getServer().getPlayer(playerId);

        // ── Cancel conditions ──
        if (player == null || !player.isOnline() || hook == null || hook.isDead() || !hook.isValid()) {
            cleanup();
            return;
        }

        // Keep the hook alive — critical: prevents lava fire damage every tick
        hook.setFireTicks(0);

        Location hookLoc = hook.getLocation();

        if (state.getPhase() != LavaBobberState.Phase.FLYING && isInLava(hookLoc)) {
            maintainFloatingHook(hook);
            hookLoc = hook.getLocation();
        }

        switch (state.getPhase()) {
            case FLYING -> handleFlying(hookLoc);
            case IN_LAVA -> handleInLava(hookLoc, player);
            case NIBBLE -> handleNibble(hookLoc, player);
            case BITE -> handleBite(hookLoc, player);
            case EXPIRED -> cleanup();
        }
    }

    private void handleFlying(Location hookLoc) {
        // Check if the hook has entered lava
        if (isInLava(hookLoc)) {
            state.setPhase(LavaBobberState.Phase.IN_LAVA);
            startBiteTimer();
            clearApproach();
            floatAnchor = hookLoc.clone();
            floatTicks = 0;
            state.getHook().setGravity(false);
            state.getHook().setVelocity(new Vector(0, 0, 0));
        }
    }

    private void handleInLava(Location hookLoc, Player player) {
        // Check if hook has left the lava
        if (!isInLava(hookLoc)) {
            switchToFlying();
            return;
        }

        state.decrementBiteTimer();

        // Start a visible approach trail from a random point to mimic Hypixel's bite cue.
        if (state.getBiteTimer() <= 0) {
            startApproachTrail(hookLoc, player);
        }
    }

    private void handleNibble(Location hookLoc, Player player) {
        // Check if hook left lava
        if (!isInLava(hookLoc)) {
            switchToFlying();
            return;
        }

        if (approachStart == null) {
            startApproachTrail(hookLoc, player);
            return;
        }

        Location bobber = state.getHook().getLocation().clone();
        approachProgress = Math.min(1.0, approachProgress + approachStep);
        Location current = lerp(approachStart, bobber, approachProgress);
        current.getWorld().spawnParticle(approachParticle, current, approachParticlesPerTick, 0.03, 0.03, 0.03, 0.0);

        if (floatTicks % 5 == 0) {
            float volume = (float) plugin.getConfig().getDouble("lava_fishing.nibble_sound_volume", 0.8);
            player.playSound(hookLoc, Sound.BLOCK_LAVA_POP, volume, 1.2f);
        }

        if (current.distanceSquared(bobber) <= biteDistanceSquared || approachProgress >= 1.0) {
            triggerBite(hookLoc, player);
        }
    }

    private void handleBite(Location hookLoc, Player player) {
        state.decrementReelWindowTimer();

        // Gentle lava bubble particles while waiting
        if (state.getReelWindowTimer() % 5 == 0) {
            hookLoc.getWorld().spawnParticle(Particle.LAVA, hookLoc, 2, 0.2, 0.1, 0.2);
        }

        // Window expired — they missed it
        if (state.getReelWindowTimer() <= 0) {
            state.setHasCatch(false);
            player.playSound(hookLoc, Sound.ENTITY_GENERIC_SPLASH, 0.5f, 0.5f);
            player.sendActionBar(plugin.getLanguageManager().getMessage("lavabobbertask.lava_catch_escaped", "The catch escaped!")
                    .color(net.kyori.adventure.text.format.NamedTextColor.RED));

            // Restart timer for next bite attempt
            state.setPhase(LavaBobberState.Phase.IN_LAVA);
            clearApproach();
            startBiteTimer();
        }
    }

    private void triggerBite(Location hookLoc, Player player) {
        state.setPhase(LavaBobberState.Phase.BITE);
        state.setHasCatch(true);
        state.setReelWindowTimer(state.getReelWindowTicks());

        int particleCount = plugin.getConfig().getInt("lava_fishing.bite_particle_count", 15);

        // Big burst of lava particles + sound
        hookLoc.getWorld().spawnParticle(Particle.LAVA, hookLoc, particleCount, 0.5, 0.3, 0.5);
        hookLoc.getWorld().spawnParticle(Particle.FLAME, hookLoc, 8, 0.4, 0.2, 0.4);
        player.playSound(hookLoc, Sound.ENTITY_GENERIC_BURN, 1.0f, 0.8f);

        player.sendActionBar(plugin.getLanguageManager().getMessage("lavabobbertask.lava_reel_now", "⚡ REEL IN NOW! ⚡")
                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
    }

    private void startBiteTimer() {
        int min = state.getMinBiteTicks();
        int max = state.getMaxBiteTicks();

        if (max < min) {
            int swap = min;
            min = max;
            max = swap;
        }

        int ticks = min + random.nextInt(max - min + 1);
        state.setBiteTimer(ticks);
    }

    private void startApproachTrail(Location hookLoc, Player player) {
        Location center = state.getHook().getLocation().clone();
        this.approachStart = randomPointInCircle(center, approachRadius);
        this.approachProgress = 0.0;
        this.approachStep = 1.0 / approachDurationTicks;

        state.setPhase(LavaBobberState.Phase.NIBBLE);
        float volume = (float) plugin.getConfig().getDouble("lava_fishing.nibble_sound_volume", 0.8);
        player.playSound(hookLoc, Sound.BLOCK_LAVA_POP, volume, 1.0f);
    }

    private Location randomPointInCircle(Location center, double radius) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = Math.sqrt(random.nextDouble()) * radius;
        double x = center.getX() + Math.cos(angle) * distance;
        double z = center.getZ() + Math.sin(angle) * distance;
        double y = center.getY() + (random.nextDouble() * 0.14) - 0.04;
        return new Location(center.getWorld(), x, y, z);
    }

    private Location lerp(Location from, Location to, double t) {
        double x = from.getX() + (to.getX() - from.getX()) * t;
        double y = from.getY() + (to.getY() - from.getY()) * t;
        double z = from.getZ() + (to.getZ() - from.getZ()) * t;
        return new Location(from.getWorld(), x, y, z);
    }

    private boolean isInLava(Location location) {
        return location.getBlock().getType() == Material.LAVA;
    }

    private void maintainFloatingHook(FishHook hook) {
        if (floatAnchor == null) {
            floatAnchor = hook.getLocation().clone();
        }

        floatTicks++;
        double surfaceY = floatAnchor.getBlockY() + 0.92;
        double bobOffset = Math.sin(floatTicks * floatBobSpeed) * floatBobAmplitude;
        Location target = floatAnchor.clone();
        target.setY(surfaceY + bobOffset);

        hook.setGravity(false);
        hook.setVelocity(new Vector(0, 0, 0));

        if (hook.getLocation().distanceSquared(target) > 0.0004) {
            hook.teleport(target);
        }
    }

    private void switchToFlying() {
        state.setPhase(LavaBobberState.Phase.FLYING);
        state.setBiteTimer(-1);
        state.getHook().setGravity(true);
        clearApproach();
        floatAnchor = null;
        floatTicks = 0;
    }

    private void clearApproach() {
        approachStart = null;
        approachProgress = 0.0;
        approachStep = 0.0;
    }

    private Particle parseParticle(String particleName) {
        if (particleName == null || particleName.isBlank()) {
            return Particle.FLAME;
        }

        try {
            return Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Particle.FLAME;
        }
    }

    private void cleanup() {
        state.setPhase(LavaBobberState.Phase.EXPIRED);
        state.setHasCatch(false);
        clearApproach();
        floatAnchor = null;
        floatTicks = 0;
        // Remove from the active sessions map in LavaFishingListener
        plugin.getLavaFishingListener().removeSession(playerId);
        cancel(); // Stop this BukkitRunnable
    }
}

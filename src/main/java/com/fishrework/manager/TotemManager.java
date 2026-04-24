package com.fishrework.manager;

import com.fishrework.FishRework;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active Totems using ItemDisplay (visual) + Interaction (hitbox) entities.
 * <p>
 * The ItemDisplay shows a conduit item that spins and bobs like an activated vanilla conduit.
 * Continuous nautilus particles spiral inward to replicate the conduit ambient effect.
 * <p>
 * Totems are persisted via PersistentDataContainer tags on the entities themselves.
 * On chunk load we scan for tagged entities and re-cache them; on chunk unload we evict.
 */
public class TotemManager {

    private final FishRework plugin;

    // PDC keys
    public final NamespacedKey TOTEM_TAG_KEY;       // Byte(1) – marks both display + interaction
    public final NamespacedKey TOTEM_OWNER_KEY;     // String – UUID of the player who placed it
    public final NamespacedKey TOTEM_PAIR_KEY;       // String – UUID of the paired entity
    public final NamespacedKey TOTEM_TYPE_KEY;       // String – totem item id/type

    /** Buff radius in blocks. */
    public double getBuffRadius() { return plugin.getConfig().getDouble("totems.buff_radius", 10.0); }
    /** Treasure chance bonus granted to nearby players. */
    public double getTreasureBonusConfig() { return plugin.getConfig().getDouble("totems.treasure_bonus", 5.0); }
    /** Sea creature attack modifier granted by the requested battle totem tier. */
    public double getBattleAttackModifierConfig(TotemType type) {
        return plugin.getConfig().getDouble(type.configKey, type.defaultAttackModifier);
    }

    /**
     * Active totems keyed by the Interaction entity UUID.
     * Value is the paired ItemDisplay UUID.
     */
    private final Map<UUID, TotemEntry> activeTotems = new ConcurrentHashMap<>();

    /** Players currently receiving the treasure totem buff (for caching). */
    private final Set<UUID> buffedPlayers = ConcurrentHashMap.newKeySet();
    /** Strongest battle totem attack modifier currently affecting each player. */
    private final Map<UUID, Double> battleAttackModifiers = new ConcurrentHashMap<>();

    private BukkitTask tickTask;
    private long tickCount = 0;

    public TotemManager(FishRework plugin) {
        this.plugin = plugin;
        this.TOTEM_TAG_KEY = new NamespacedKey(plugin, "treasure_totem");
        this.TOTEM_OWNER_KEY = new NamespacedKey(plugin, "totem_owner");
        this.TOTEM_PAIR_KEY = new NamespacedKey(plugin, "totem_pair");
        this.TOTEM_TYPE_KEY = new NamespacedKey(plugin, "totem_type");
    }

    // ── Lifecycle ─────────────────────────────────────────────

    /** Start the repeating tick task. Call from onEnable. */
    public void start() {
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 2L);
        // Scan already-loaded chunks for totems (reload safety)
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk);
            }
        }
    }

    /** Stop the tick task. Call from onDisable. */
    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        buffedPlayers.clear();
        battleAttackModifiers.clear();
    }

    // ── Spawning / Removing ───────────────────────────────────

    /**
     * Spawns a Treasure Totem at the given location.
     * Creates an ItemDisplay (conduit visual, floating + spinning) and an Interaction entity (hitbox).
     *
     * @param location The block-level location to place the totem.
     * @param placer   The player placing the totem.
     */
    public void spawnTotem(Location location, Player placer) {
        spawnTotem(location, placer, TotemType.TREASURE);
    }

    public void spawnTotem(Location location, Player placer, TotemType type) {
        if (type == null) type = TotemType.TREASURE;
        World world = location.getWorld();
        if (world == null) return;

        // Center the totem on the block, float 1.5 blocks above ground
        Location spawnLoc = location.clone().add(0.5, 1.5, 0.5);

        // 1. ItemDisplay (visual) — conduit item gives the "eye" model
        ItemDisplay display = (ItemDisplay) world.spawnEntity(spawnLoc, EntityType.ITEM_DISPLAY);
        display.setItemStack(new ItemStack(type.displayMaterial));
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.setGravity(false);
        display.setPersistent(true);
        display.customName(getTotemDisplayName(type));
        display.setCustomNameVisible(true);

        // Initial transformation — centered and scaled up
        Transformation t = new Transformation(
                new Vector3f(-0.5f, -0.5f, -0.5f), // translation (center the model)
                new AxisAngle4f(0, 0, 1, 0),        // left rotation
                new Vector3f(type.scale, type.scale, type.scale), // scale
                new AxisAngle4f(0, 0, 1, 0)          // right rotation
        );
        display.setTransformation(t);

        // Glow effect
        display.setGlowing(true);
        display.setGlowColorOverride(type.glowColor);

        // 2. Interaction entity (hitbox for breaking)
        Location hitboxLoc = location.clone().add(0.5, 1.0, 0.5);
        Interaction interaction = (Interaction) world.spawnEntity(hitboxLoc, EntityType.INTERACTION);
        interaction.setInteractionWidth(1.5f);
        interaction.setInteractionHeight(2.0f);
        interaction.setPersistent(true);

        // 3. Tag both entities with PDC
        PersistentDataContainer dPdc = display.getPersistentDataContainer();
        dPdc.set(TOTEM_TAG_KEY, PersistentDataType.BYTE, (byte) 1);
        dPdc.set(TOTEM_OWNER_KEY, PersistentDataType.STRING, placer.getUniqueId().toString());
        dPdc.set(TOTEM_PAIR_KEY, PersistentDataType.STRING, interaction.getUniqueId().toString());
        dPdc.set(TOTEM_TYPE_KEY, PersistentDataType.STRING, type.itemId);

        PersistentDataContainer iPdc = interaction.getPersistentDataContainer();
        iPdc.set(TOTEM_TAG_KEY, PersistentDataType.BYTE, (byte) 1);
        iPdc.set(TOTEM_OWNER_KEY, PersistentDataType.STRING, placer.getUniqueId().toString());
        iPdc.set(TOTEM_PAIR_KEY, PersistentDataType.STRING, display.getUniqueId().toString());
        iPdc.set(TOTEM_TYPE_KEY, PersistentDataType.STRING, type.itemId);

        // 4. Cache
        activeTotems.put(interaction.getUniqueId(), new TotemEntry(display.getUniqueId(), interaction.getUniqueId(), spawnLoc, type));

        // 5. Placement feedback
        world.playSound(spawnLoc, Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.0f);
        spawnPlacementParticles(world, spawnLoc, type);

        plugin.getLogger().info(placer.getName() + " placed a " + type.fallbackName + " at " +
                spawnLoc.getBlockX() + ", " + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ());
    }

    /**
     * Removes a totem by its Interaction entity UUID.
     * Removes both the Interaction and paired ItemDisplay.
     *
     * @return true if successfully removed.
     */
    public boolean removeTotem(UUID interactionUuid) {
        TotemEntry entry = activeTotems.remove(interactionUuid);
        if (entry == null) return false;

        // Remove entities from world
        for (World world : plugin.getServer().getWorlds()) {
            Entity displayEntity = findEntity(world, entry.displayUuid);
            Entity interactionEntity = findEntity(world, entry.interactionUuid);
            if (displayEntity != null) {
                displayEntity.getLocation().getWorld().playSound(
                        displayEntity.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, 1.0f, 1.0f);
                displayEntity.remove();
            }
            if (interactionEntity != null) {
                interactionEntity.remove();
            }
        }
        return true;
    }

    /**
     * Drops the matching totem item at the totem's location and removes it.
     */
    public void breakTotem(UUID interactionUuid, Player breaker) {
        TotemEntry entry = activeTotems.get(interactionUuid);
        Location dropLoc = entry != null ? entry.location : (breaker != null ? breaker.getLocation() : null);
        if (removeTotem(interactionUuid) && dropLoc != null && dropLoc.getWorld() != null) {
            TotemType type = entry != null ? entry.type : TotemType.TREASURE;
            dropLoc.getWorld().dropItemNaturally(dropLoc, plugin.getItemManager().getRequiredItem(type.itemId));
        }
    }

    // ── Tick Loop ─────────────────────────────────────────────

    private void tick() {
        tickCount++;

        // Rotation: smooth continuous spin (Y-axis)
        float rotationAngle = (tickCount * (float) plugin.getConfig().getDouble("totems.rotation_speed", 0.1)) % ((float)(Math.PI * 2));

        // Bobbing: gentle sine-wave float, ±0.15 blocks
        float bobOffset = (float)(Math.sin(tickCount * plugin.getConfig().getDouble("totems.bob_frequency", 0.08)) * plugin.getConfig().getDouble("totems.bob_amplitude", 0.15));

        // Recalculate buffed players
        Set<UUID> newBuffed = new HashSet<>();
        Map<UUID, Double> newBattleModifiers = new HashMap<>();

        Iterator<Map.Entry<UUID, TotemEntry>> it = activeTotems.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TotemEntry> mapEntry = it.next();
            TotemEntry entry = mapEntry.getValue();
            TotemType type = entry.type;
            Location loc = entry.location;
            World world = loc.getWorld();
            if (world == null) { it.remove(); continue; }

            // Find the display entity to animate
            Entity displayEntity = findEntity(world, entry.displayUuid);
            if (displayEntity instanceof ItemDisplay display) {
                // Animate: rotate + bob
                Transformation rotated = new Transformation(
                        new Vector3f(-0.5f, -0.5f + bobOffset, -0.5f),
                        new AxisAngle4f(rotationAngle, 0, 1, 0),
                        new Vector3f(type.scale, type.scale, type.scale),
                        new AxisAngle4f(0, 0, 1, 0)
                );
                display.setInterpolationDuration(2);
                display.setInterpolationDelay(0);
                display.setTransformation(rotated);

                spawnAmbientParticles(world, loc, type);

                // Ambient conduit sound (rarely, ~every 8 seconds)
                if (tickCount % 160 == 0) {
                    world.playSound(loc, Sound.BLOCK_CONDUIT_AMBIENT, 0.6f, 1.0f);
                }
            } else {
                // Display entity gone — remove totem
                Entity interactionEntity = findEntity(world, entry.interactionUuid);
                if (interactionEntity != null) interactionEntity.remove();
                it.remove();
                continue;
            }

            // Check interaction still exists
            Entity interactionEntity = findEntity(world, entry.interactionUuid);
            if (interactionEntity == null) {
                if (displayEntity != null) displayEntity.remove();
                it.remove();
                continue;
            }

            // Buff nearby players
            double radius = getBuffRadius();
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(loc) <= radius * radius) {
                    if (type.treasureTotem) {
                        newBuffed.add(player.getUniqueId());
                    }
                    if (type.defaultAttackModifier > 0) {
                        newBattleModifiers.merge(
                                player.getUniqueId(),
                                getBattleAttackModifierConfig(type),
                                Math::max);
                    }
                }
            }
        }

        buffedPlayers.clear();
        buffedPlayers.addAll(newBuffed);
        battleAttackModifiers.clear();
        battleAttackModifiers.putAll(newBattleModifiers);
    }

    // ── Buff Query ────────────────────────────────────────────

    /**
     * Returns the treasure bonus for a player from nearby totems.
     * Called by MobManager.getTreasureChance().
     */
    public double getTreasureBonus(Player player) {
        if (buffedPlayers.contains(player.getUniqueId())) {
            return getTreasureBonusConfig();
        }
        return 0.0;
    }

    /**
     * Returns the strongest nearby battle totem flat attack modifier for the player.
     */
    public double getSeaCreatureAttackModifier(Player player) {
        if (player == null) return 0.0;
        return battleAttackModifiers.getOrDefault(player.getUniqueId(), 0.0);
    }

    // ── Chunk Scanning ────────────────────────────────────────

    /** Scans a chunk for totem entities and adds them to the cache. */
    public void scanChunk(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Interaction interaction) {
                PersistentDataContainer pdc = interaction.getPersistentDataContainer();
                if (pdc.has(TOTEM_TAG_KEY, PersistentDataType.BYTE)) {
                    String pairStr = pdc.get(TOTEM_PAIR_KEY, PersistentDataType.STRING);
                    UUID displayUuid = pairStr != null ? UUID.fromString(pairStr) : null;
                    if (displayUuid != null && !activeTotems.containsKey(interaction.getUniqueId())) {
                        // Find the display entity's location for the cached entry
                        Entity displayEntity = chunk.getWorld().getEntity(displayUuid);
                        Location loc = displayEntity != null ? displayEntity.getLocation() : interaction.getLocation();
                        activeTotems.put(interaction.getUniqueId(),
                                new TotemEntry(displayUuid, interaction.getUniqueId(), loc, getTotemType(interaction)));
                    }
                }
            }
        }
    }

    /** Evicts cached totems whose entities are in the unloading chunk. */
    public void evictChunk(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Interaction) {
                activeTotems.remove(entity.getUniqueId());
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────

    public boolean isTotemInteraction(Entity entity) {
        if (!(entity instanceof Interaction)) return false;
        return entity.getPersistentDataContainer().has(TOTEM_TAG_KEY, PersistentDataType.BYTE);
    }

    public boolean isTotemDisplay(Entity entity) {
        if (!(entity instanceof ItemDisplay)) return false;
        return entity.getPersistentDataContainer().has(TOTEM_TAG_KEY, PersistentDataType.BYTE);
    }

    public TotemType getTotemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String itemId = pdc.get(plugin.getItemManager().CUSTOM_ITEM_KEY, PersistentDataType.STRING);
        TotemType type = TotemType.fromItemId(itemId);
        if (type != null) {
            return type;
        }
        if (pdc.has(plugin.getItemManager().TREASURE_TOTEM_KEY, PersistentDataType.BYTE)) {
            return TotemType.TREASURE;
        }
        return null;
    }

    public TotemType getTotemType(Entity entity) {
        if (entity == null || !entity.getPersistentDataContainer().has(TOTEM_TAG_KEY, PersistentDataType.BYTE)) {
            return TotemType.TREASURE;
        }
        String typeId = entity.getPersistentDataContainer().get(TOTEM_TYPE_KEY, PersistentDataType.STRING);
        return Objects.requireNonNullElse(TotemType.fromItemId(typeId), TotemType.TREASURE);
    }

    public Component getTotemDisplayName(TotemType type) {
        return plugin.getLanguageManager()
                .getMessage(type.languageKey, type.fallbackName)
                .color(type.nameColor);
    }

    public Map<UUID, TotemEntry> getActiveTotems() {
        return Collections.unmodifiableMap(activeTotems);
    }

    private Entity findEntity(World world, UUID uuid) {
        return world.getEntity(uuid);
    }

    private void spawnPlacementParticles(World world, Location loc, TotemType type) {
        if (type.treasureTotem) {
            world.spawnParticle(Particle.NAUTILUS, loc, 40, 1.0, 1.0, 1.0, 0.08);
            return;
        }
        world.spawnParticle(Particle.DUST, loc, 35, 1.0, 1.0, 1.0, 0.02,
                new Particle.DustOptions(type.glowColor, 1.25f));
    }

    private void spawnAmbientParticles(World world, Location loc, TotemType type) {
        if (type.treasureTotem) {
            if (tickCount % 3 == 0) {
                double angle = tickCount * 0.3;
                double radius = 1.5 + Math.sin(tickCount * 0.05) * 0.5;
                double px = loc.getX() + Math.cos(angle) * radius;
                double py = loc.getY() + (Math.random() - 0.5) * 1.5;
                double pz = loc.getZ() + Math.sin(angle) * radius;
                world.spawnParticle(Particle.NAUTILUS, px, py, pz, 1,
                        loc.getX() - px, loc.getY() - py, loc.getZ() - pz, 0.5);
            }
            if (tickCount % 10 == 0) {
                world.spawnParticle(Particle.NAUTILUS, loc, 3, 1.5, 1.0, 1.5, 0.04);
            }
            return;
        }

        if (tickCount % 4 == 0) {
            double angle = tickCount * 0.35;
            double radius = 1.15 + Math.sin(tickCount * 0.08) * 0.25;
            double px = loc.getX() + Math.cos(angle) * radius;
            double py = loc.getY() + (Math.random() - 0.5) * 1.2;
            double pz = loc.getZ() + Math.sin(angle) * radius;
            world.spawnParticle(Particle.DUST, px, py, pz, 1, 0.05, 0.05, 0.05, 0.01,
                    new Particle.DustOptions(type.glowColor, 0.95f));
        }
        if (tickCount % 12 == 0) {
            world.spawnParticle(Particle.DUST, loc, 4, 1.0, 0.85, 1.0, 0.02,
                    new Particle.DustOptions(type.glowColor, 1.1f));
        }
    }

    // ── Inner Data Class ──────────────────────────────────────

    public enum TotemType {
        TREASURE("treasure_totem", "totemmanager.treasure_totem", "Treasure Totem",
                NamedTextColor.GOLD, Color.fromRGB(255, 215, 0), Material.CONDUIT, 1.0f,
                true, 0.0, "totems.treasure_bonus"),
        BATTLE_1("battle_totem_1", "totemmanager.battle_totem_1", "Battle Totem I",
                NamedTextColor.RED, Color.fromRGB(232, 74, 43), Material.CONDUIT, 1.0f,
                false, 1.0, "totems.battle_totem_1_attack_modifier"),
        BATTLE_2("battle_totem_2", "totemmanager.battle_totem_2", "Battle Totem II",
                NamedTextColor.DARK_RED, Color.fromRGB(173, 32, 50), Material.CONDUIT, 1.08f,
                false, 2.0, "totems.battle_totem_2_attack_modifier"),
        BATTLE_3("battle_totem_3", "totemmanager.battle_totem_3", "Battle Totem III",
                NamedTextColor.LIGHT_PURPLE, Color.fromRGB(178, 73, 225), Material.CONDUIT, 1.16f,
                false, 3.0, "totems.battle_totem_3_attack_modifier");

        private final String itemId;
        private final String languageKey;
        private final String fallbackName;
        private final NamedTextColor nameColor;
        private final Color glowColor;
        private final Material displayMaterial;
        private final float scale;
        private final boolean treasureTotem;
        private final double defaultAttackModifier;
        private final String configKey;

        TotemType(String itemId,
                String languageKey,
                String fallbackName,
                NamedTextColor nameColor,
                Color glowColor,
                Material displayMaterial,
                float scale,
                boolean treasureTotem,
                double defaultAttackModifier,
                String configKey) {
            this.itemId = itemId;
            this.languageKey = languageKey;
            this.fallbackName = fallbackName;
            this.nameColor = nameColor;
            this.glowColor = glowColor;
            this.displayMaterial = displayMaterial;
            this.scale = scale;
            this.treasureTotem = treasureTotem;
            this.defaultAttackModifier = defaultAttackModifier;
            this.configKey = configKey;
        }

        private static TotemType fromItemId(String itemId) {
            if (itemId == null || itemId.isBlank()) return null;
            for (TotemType type : values()) {
                if (type.itemId.equals(itemId)) {
                    return type;
                }
            }
            return null;
        }
    }

    public static class TotemEntry {
        public final UUID displayUuid;
        public final UUID interactionUuid;
        public final Location location;
        public final TotemType type;

        public TotemEntry(UUID displayUuid, UUID interactionUuid, Location location, TotemType type) {
            this.displayUuid = displayUuid;
            this.interactionUuid = interactionUuid;
            this.location = location;
            this.type = type != null ? type : TotemType.TREASURE;
        }
    }
}

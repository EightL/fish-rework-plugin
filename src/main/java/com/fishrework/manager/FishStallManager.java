// Credit to: https://block-display.com/bd/54006/

package com.fishrework.manager;

import com.fishrework.FishRework;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;

import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.logging.Filter;
import java.util.logging.Logger;

public class FishStallManager implements Runnable {

    private final FishRework plugin;
    private final NamespacedKey stallKey;
    private final NamespacedKey interactKey;
    private final NamespacedKey modelIdKey;
    private final NamespacedKey modelRoleKey;
    private final NamespacedKey attachedParentKey;
    private final CommandSender silentCommandSender;
    private final Map<String, ModelDefinition> registry = new LinkedHashMap<>();
    private final Map<String, UUID> activeInstances = new LinkedHashMap<>();
    private final Map<UUID, AttachedModel> attachedModels = new java.util.concurrent.ConcurrentHashMap<>();
    public FishStallManager(FishRework plugin) {
        this.plugin = plugin;
        this.stallKey = new NamespacedKey(plugin, "fish_model_instance");
        this.interactKey = new NamespacedKey(plugin, "fish_stall_interact");
        this.modelIdKey = new NamespacedKey(plugin, "fish_model_id");
        this.modelRoleKey = new NamespacedKey(plugin, "fish_model_role");
        this.attachedParentKey = new NamespacedKey(plugin, "fish_model_attached_parent");
        this.silentCommandSender = Bukkit.getConsoleSender();
        registerModel("fish_stall", "Fish Market Stall", "fr_fish_stall", FISH_STALL_COMMAND);
    }

    public void registerModel(String id, String displayName, String tag, String summonTemplate) {
        registerModel(id, displayName, tag, summonTemplate, 0.0f);
    }

    public void registerModel(String id, String displayName, String tag, String summonTemplate, float yawOffsetDegrees) {
        registry.put(id, new ModelDefinition(displayName, tag, summonTemplate, yawOffsetDegrees));
    }

    public boolean hasModel(String id) {
        return registry.containsKey(id);
    }

    public Set<String> getModelIds() {
        return registry.keySet();
    }

    public String getDisplayName(String id) {
        ModelDefinition def = registry.get(id);
        return def != null ? def.displayName : "?";
    }

    public String getModelTag(String id) {
        ModelDefinition def = registry.get(id);
        return def != null ? def.tag : "";
    }

    public Map<String, String> getActiveInstances() {
        Map<String, String> result = new LinkedHashMap<>();
        activeInstances.forEach((iid, uuid) -> {
            Entity e = Bukkit.getEntity(uuid);
            String loc = e != null ? String.format(Locale.US, "%.0f, %.0f, %.0f", e.getLocation().getX(), e.getLocation().getY(), e.getLocation().getZ()) : "unloaded";
            result.put(iid, loc);
        });
        return result;
    }

    public String spawnModel(String modelId, Location loc) {
        return spawnModel(modelId, loc, Map.of());
    }

    public String spawnModel(String modelId, Location loc, Map<NamespacedKey, String> hitboxData) {
        ModelDefinition def = registry.get(modelId);
        if (def == null || loc.getWorld() == null) return null;

        String instanceId = modelId + "_" + System.currentTimeMillis();
        Entity root = dispatchModelSummon(def, loc.clone().add(-0.5, 0.0, -0.5));
        if (root == null) {
            return null;
        }

        tagModelTree(root, instanceId, modelId, "standalone", null);
        activeInstances.put(instanceId, root.getUniqueId());

        // Spawn an invisible interaction entity for right-click detection
        Interaction hitbox = (Interaction) loc.getWorld().spawnEntity(
                loc.clone().add(0.5, 0, 0.5),
                org.bukkit.entity.EntityType.INTERACTION
        );
        hitbox.setInteractionWidth(3.5f);
        hitbox.setInteractionHeight(3.0f);
        hitbox.setResponsive(true);
        hitbox.setPersistent(true);
        hitbox.getPersistentDataContainer().set(interactKey, PersistentDataType.STRING, instanceId);
        if (hitboxData != null) {
            hitboxData.forEach((key, value) -> {
                if (key != null && value != null) {
                    hitbox.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
                }
            });
        }
        activeInstances.put(instanceId + "_hitbox", hitbox.getUniqueId());
        return instanceId;
    }

    public String spawnLiteStall(Location loc, Map<NamespacedKey, String> hitboxData) {
        if (loc.getWorld() == null) return null;

        String instanceId = "fish_stall_lite_" + System.currentTimeMillis();

        org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager)
                loc.getWorld().spawnEntity(loc.clone().add(0.5, 0, 0.5), org.bukkit.entity.EntityType.VILLAGER);
        villager.customName(Component.text("Fish Market"));
        villager.setCustomNameVisible(true);
        villager.setProfession(org.bukkit.entity.Villager.Profession.FISHERMAN);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setPersistent(true);
        villager.setCollidable(false);
        villager.getPersistentDataContainer().set(stallKey, PersistentDataType.STRING, instanceId);
        villager.getPersistentDataContainer().set(interactKey, PersistentDataType.STRING, instanceId);
        if (hitboxData != null) {
            hitboxData.forEach((key, value) -> {
                if (key != null && value != null) {
                    villager.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
                }
            });
        }

        activeInstances.put(instanceId, villager.getUniqueId());
        return instanceId;
    }

    public boolean attachModel(String modelId, LivingEntity parent) {
        return attachModel(modelId, parent, 1.0);
    }

    public boolean attachModel(String modelId, LivingEntity parent, double scaleMultiplier) {
        ModelDefinition def = registry.get(modelId);
        if (def == null || parent == null || parent.getWorld() == null || parent.isDead() || !parent.isValid()) {
            return false;
        }

        cleanupAttachedModel(parent);
        Set<UUID> existingRoots = getTaggedRootIds(parent.getWorld(), def.tag);
        dispatchModelSummon(def, parent.getLocation());

        List<Entity> spawnedRoots = findNewModelRoots(parent.getWorld(), def, existingRoots);
        if (!spawnedRoots.isEmpty()) {
            return finishAttachModel(modelId, parent, def, scaleMultiplier, spawnedRoots);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World world = parent.getWorld();
            List<Entity> delayedRoots = findNewModelRoots(world, def, existingRoots);
            if (delayedRoots.isEmpty()) {
                parent.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
                plugin.getLogger().warning("Unable to attach display model '" + modelId + "' to " + parent.getType() + ": no summoned model root was found.");
                return;
            }
            if (parent.isDead() || !parent.isValid()) {
                delayedRoots.forEach(this::removeWithPassengers);
                return;
            }
            finishAttachModel(modelId, parent, def, scaleMultiplier, delayedRoots);
        }, 1L);
        return true;
    }

    private boolean finishAttachModel(String modelId, LivingEntity parent, ModelDefinition def, double scaleMultiplier, List<Entity> spawnedRoots) {
        if (spawnedRoots.isEmpty()) {
            return false;
        }
        Entity root = spawnedRoots.get(0);
        for (int i = 1; i < spawnedRoots.size(); i++) {
            removeWithPassengers(spawnedRoots.get(i));
        }
        setNonPersistentTree(root);
        tagModelTree(root, "attached:" + parent.getUniqueId(), modelId, "attached", parent.getUniqueId());
        double safeScale = Math.max(0.05, scaleMultiplier);
        configureDisplayTree(root, safeScale);
        attachedModels.put(parent.getUniqueId(), new AttachedModel(root.getUniqueId(), modelId, def.yawOffsetDegrees));
        teleportAttachedModel(parent, root, def.yawOffsetDegrees);
        return true;
    }

    private List<Entity> findNewModelRoots(World world, ModelDefinition def, Set<UUID> existingRoots) {
        if (world == null || def == null) return List.of();
        return world.getEntities().stream()
                .filter(e -> isModelRoot(e, def))
                .filter(e -> !existingRoots.contains(e.getUniqueId()))
                .sorted(Comparator.comparingLong(Entity::getEntityId).reversed())
                .toList();
    }

    private Set<UUID> getTaggedRootIds(World world, String tag) {
        Set<UUID> ids = new HashSet<>();
        if (world == null || tag == null || tag.isBlank()) {
            return ids;
        }
        for (Entity entity : world.getEntities()) {
            if (isModelRoot(entity, tag)) {
                ids.add(entity.getUniqueId());
            }
        }
        return ids;
    }

    public void cleanupAttachedModel(Entity parent) {
        if (parent == null) return;
        AttachedModel attached = attachedModels.remove(parent.getUniqueId());
        if (attached == null) return;
        Entity root = Bukkit.getEntity(attached.rootId);
        if (root != null) {
            removeWithPassengers(root);
        }
    }

    public boolean destroyInstance(String instanceId) {
        UUID uid = activeInstances.remove(instanceId);
        if (uid == null) {
            return destroyInstanceByMetadata(instanceId) > 0;
        }
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getUniqueId().equals(uid)) {
                    removeWithPassengers(e);
                }
            }
        }
        // Also clean up the hitbox interaction entity
        UUID hitboxUid = activeInstances.remove(instanceId + "_hitbox");
        if (hitboxUid != null) {
            for (World w : Bukkit.getWorlds()) {
                for (Entity e : w.getEntities()) {
                    if (e.getUniqueId().equals(hitboxUid)) {
                        e.remove();
                    }
                }
            }
        }
        return true;
    }

    public int destroyInstanceByMetadata(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) return 0;

        PersistentDataType<String, String> stringType = PersistentDataType.STRING;
        Set<UUID> removed = new HashSet<>();
        int count = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                String modelInstance = entity.getPersistentDataContainer().get(stallKey, stringType);
                String interactionInstance = entity.getPersistentDataContainer().get(interactKey, stringType);
                if (!instanceId.equals(modelInstance) && !instanceId.equals(interactionInstance)) {
                    continue;
                }

                if (entity instanceof Interaction interaction) {
                    if (removed.add(interaction.getUniqueId())) {
                        interaction.remove();
                        count++;
                    }
                    continue;
                }

                Entity root = getTopVehicle(entity);
                if (root != null && removed.add(root.getUniqueId())) {
                    removeWithPassengers(root);
                    count++;
                }
            }
        }

        activeInstances.entrySet().removeIf(entry ->
                entry.getKey().equals(instanceId) || entry.getKey().equals(instanceId + "_hitbox"));
        return count;
    }

    public String findStandaloneInstanceForShop(World world, NamespacedKey shopIdKey, String shopId) {
        if (world == null || shopIdKey == null || shopId == null || shopId.isBlank()) {
            return null;
        }
        PersistentDataType<String, String> stringType = PersistentDataType.STRING;
        for (Entity entity : world.getEntities()) {
            String foundShopId = entity.getPersistentDataContainer().get(shopIdKey, stringType);
            if (!shopId.equals(foundShopId)) continue;
            String instanceId = getStandaloneInstanceId(entity, stringType);
            if (instanceId != null && !instanceId.isBlank()) {
                return instanceId;
            }
        }
        return null;
    }

    public List<String> findStandaloneInstancesForShop(World world, NamespacedKey shopIdKey, String shopId) {
        if (world == null || shopIdKey == null || shopId == null || shopId.isBlank()) {
            return List.of();
        }

        PersistentDataType<String, String> stringType = PersistentDataType.STRING;
        Set<String> instanceIds = new LinkedHashSet<>();
        for (Entity entity : world.getEntities()) {
            String foundShopId = entity.getPersistentDataContainer().get(shopIdKey, stringType);
            if (!shopId.equals(foundShopId)) continue;
            String instanceId = getStandaloneInstanceId(entity, stringType);
            if (instanceId != null && !instanceId.isBlank()) {
                instanceIds.add(instanceId);
            }
        }
        return new ArrayList<>(instanceIds);
    }

    private String getStandaloneInstanceId(Entity entity, PersistentDataType<String, String> stringType) {
        String instanceId = entity.getPersistentDataContainer().get(interactKey, stringType);
        if (instanceId != null && !instanceId.isBlank()) {
            return instanceId;
        }
        return entity.getPersistentDataContainer().get(stallKey, stringType);
    }

    public int destroyAll(World world) {
        int count = 0;
        Iterator<Map.Entry<String, UUID>> it = activeInstances.entrySet().iterator();
        while (it.hasNext()) {
            UUID uid = it.next().getValue();
            for (Entity e : world.getEntities()) {
                if (e.getUniqueId().equals(uid)) {
                    removeWithPassengers(e);
                    count++;
                }
            }
            it.remove();
        }
        Iterator<Map.Entry<UUID, AttachedModel>> attachedIt = attachedModels.entrySet().iterator();
        while (attachedIt.hasNext()) {
            AttachedModel attached = attachedIt.next().getValue();
            Entity root = Bukkit.getEntity(attached.rootId);
            if (root != null && root.getWorld().equals(world)) {
                removeWithPassengers(root);
                count++;
                attachedIt.remove();
            } else if (root == null) {
                attachedIt.remove();
            }
        }
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (isModelRoot(entity)) {
                removeWithPassengers(entity);
                count++;
            }
        }
        return count;
    }

    private boolean isModelRoot(Entity entity) {
        if (entity == null) return false;
        for (ModelDefinition def : registry.values()) {
            if (isModelRoot(entity, def)) {
                return true;
            }
        }
        return false;
    }

    private boolean isModelRoot(Entity entity, ModelDefinition def) {
        return def != null && isModelRoot(entity, def.tag);
    }

    private boolean isModelRoot(Entity entity, String tag) {
        return entity != null
                && entity.getVehicle() == null
                && entity.getScoreboardTags().contains(tag);
    }

    @Override
    public void run() {
        Iterator<Map.Entry<UUID, AttachedModel>> it = attachedModels.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, AttachedModel> entry = it.next();
            Entity parent = Bukkit.getEntity(entry.getKey());
            Entity root = Bukkit.getEntity(entry.getValue().rootId);

            if (!(parent instanceof LivingEntity living) || !parent.isValid() || living.isDead()) {
                if (root != null) {
                    removeWithPassengers(root);
                }
                it.remove();
                continue;
            }

            if (root == null || !root.isValid()) {
                it.remove();
                continue;
            }

            teleportAttachedModel(living, root, entry.getValue().yawOffsetDegrees);
        }
    }

    private void removeWithPassengers(Entity entity) {
        if (entity == null) return;
        for (Entity passenger : new ArrayList<>(entity.getPassengers())) {
            removeWithPassengers(passenger);
        }
        entity.remove();
    }

    private void setNonPersistentTree(Entity entity) {
        if (entity == null) return;
        entity.setPersistent(false);
        for (Entity passenger : entity.getPassengers()) {
            setNonPersistentTree(passenger);
        }
    }

    private void tagModelTree(Entity entity, String instanceId, String modelId, String role, UUID attachedParentId) {
        if (entity == null) return;
        PersistentDataType<String, String> stringType = PersistentDataType.STRING;
        entity.getPersistentDataContainer().set(stallKey, stringType, instanceId);
        entity.getPersistentDataContainer().set(modelIdKey, stringType, modelId);
        entity.getPersistentDataContainer().set(modelRoleKey, stringType, role);
        if (attachedParentId != null) {
            entity.getPersistentDataContainer().set(attachedParentKey, stringType, attachedParentId.toString());
        }
        for (Entity passenger : entity.getPassengers()) {
            tagModelTree(passenger, instanceId, modelId, role, attachedParentId);
        }
    }

    private void configureDisplayTree(Entity entity, double scaleMultiplier) {
        if (entity instanceof Display display) {
            Transformation transform = display.getTransformation();
            transform.getScale().mul((float) scaleMultiplier);
            transform.getTranslation().mul((float) scaleMultiplier);
            display.setTransformation(transform);
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(0);
            display.setTeleportDuration(1);
            display.setShadowRadius(0.0f);
            display.setShadowStrength(0.0f);
        }
        for (Entity passenger : entity.getPassengers()) {
            configureDisplayTree(passenger, scaleMultiplier);
        }
    }

    private void teleportAttachedModel(LivingEntity parent, Entity root, float yawOffsetDegrees) {
        Location target = parent.getLocation().clone();
        float yaw = parent instanceof org.bukkit.entity.Mob mob ? mob.getBodyYaw() : parent.getYaw();
        target.setYaw(yaw + yawOffsetDegrees);
        root.teleport(target);
    }

    private String buildSummonCommand(ModelDefinition def, Location loc) {
        return def.summonTemplate
                .replace("%x%", String.format(Locale.US, "%.2f", loc.getX()))
                .replace("%y%", String.format(Locale.US, "%.2f", loc.getY()))
                .replace("%z%", String.format(Locale.US, "%.2f", loc.getZ()));
    }

    /**
     * Dispatches a model summon command, splitting it into multiple commands if the
     * single-command form would exceed Minecraft's command length limit.
     * <p>
     * For large models with many passengers, this creates a Bukkit-owned root
     * entity first, then summons each child display normally and attaches it
     * through Bukkit. Newer servers can report live {@code Passengers} NBT edits
     * as successful without creating rendered passenger entities.
     */
    private Entity dispatchModelSummon(ModelDefinition def, Location loc) {
        String cmd = buildSummonCommand(def, loc);
        World world = loc.getWorld();

        if (cmd.length() < 30000) {
            Set<UUID> existingRoots = getTaggedRootIds(world, def.tag);
            dispatchSummonCommand(world, cmd);
            List<Entity> spawnedRoots = findNewModelRoots(world, def, existingRoots);
            return spawnedRoots.isEmpty() ? null : spawnedRoots.get(0);
        }

        int nbtStart = cmd.indexOf('{');
        if (nbtStart == -1) {
            dispatchSummonCommand(world, cmd);
            return null;
        }

        String fullNbt = cmd.substring(nbtStart);

        int passengersKeyPos = fullNbt.indexOf(",Passengers:[");
        if (passengersKeyPos == -1) {
            passengersKeyPos = fullNbt.indexOf("Passengers:[");
            if (passengersKeyPos == -1) {
                dispatchSummonCommand(world, cmd);
                return null;
            }
        }

        int bracketStart = fullNbt.indexOf('[', passengersKeyPos);
        int bracketEnd = findMatchingBracket(fullNbt, bracketStart);
        if (bracketEnd == -1) {
            dispatchSummonCommand(world, cmd);
            return null;
        }

        String arrayContent = fullNbt.substring(bracketStart + 1, bracketEnd);
        List<String> passengers = parseNbtArray(arrayContent);
        if (passengers.isEmpty()) {
            dispatchSummonCommand(world, cmd);
            return null;
        }

        if (world == null) {
            return null;
        }

        Entity root = world.spawnEntity(loc, org.bukkit.entity.EntityType.BLOCK_DISPLAY);
        root.addScoreboardTag(def.tag);
        root.setPersistent(false);

        List<String> delayedPassengerTags = new ArrayList<>();
        for (String passenger : passengers) {
            String delayedTag = summonAndAttachPassenger(loc, root, passenger);
            if (delayedTag != null) {
                delayedPassengerTags.add(delayedTag);
            }
        }
        if (!delayedPassengerTags.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> attachDelayedPassengers(root, delayedPassengerTags), 1L);
        }
        return root;
    }

    private String summonAndAttachPassenger(Location loc, Entity root, String passengerNbt) {
        if (loc.getWorld() == null || root == null || passengerNbt == null || passengerNbt.isBlank()) {
            return null;
        }

        String entityType = extractTopLevelId(passengerNbt);
        if (entityType == null || entityType.isBlank()) {
            return null;
        }

        String passengerTag = UUID.randomUUID().toString().replace("-", "");
        String summonNbt = addTagToNbt(removeTopLevelId(passengerNbt), passengerTag);
        String cmd = String.format(
                Locale.US,
                "summon %s %.2f %.2f %.2f %s",
                entityType,
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                summonNbt
        );
        dispatchSummonCommand(loc.getWorld(), cmd);

        Entity passenger = findSingleTaggedEntity(loc.getWorld(), passengerTag);
        if (passenger == null) {
            return passengerTag;
        }
        attachPassenger(root, passenger, passengerTag);
        return null;
    }

    private void attachDelayedPassengers(Entity root, List<String> passengerTags) {
        if (root == null || !root.isValid() || passengerTags == null || passengerTags.isEmpty()) {
            return;
        }
        World world = root.getWorld();
        for (String passengerTag : passengerTags) {
            Entity passenger = findSingleTaggedEntity(world, passengerTag);
            if (passenger != null) {
                attachPassenger(root, passenger, passengerTag);
            }
        }
    }

    private void attachPassenger(Entity root, Entity passenger, String passengerTag) {
        passenger.removeScoreboardTag(passengerTag);
        passenger.setPersistent(false);
        root.addPassenger(passenger);
    }

    private Entity findSingleTaggedEntity(World world, String tag) {
        if (world == null || tag == null || tag.isBlank()) {
            return null;
        }
        return world.getEntities().stream()
                .filter(entity -> entity.getScoreboardTags().contains(tag))
                .max(Comparator.comparingLong(Entity::getEntityId))
                .orElse(null);
    }

    private static String extractTopLevelId(String nbt) {
        String idPrefix = "{id:\"";
        if (!nbt.startsWith(idPrefix)) {
            return null;
        }
        int end = nbt.indexOf('"', idPrefix.length());
        if (end == -1) {
            return null;
        }
        return nbt.substring(idPrefix.length(), end);
    }

    private static String removeTopLevelId(String nbt) {
        String idPrefix = "{id:\"";
        if (!nbt.startsWith(idPrefix)) {
            return nbt;
        }
        int idEnd = nbt.indexOf('"', idPrefix.length());
        if (idEnd == -1 || idEnd + 1 >= nbt.length()) {
            return nbt;
        }
        int next = idEnd + 1;
        if (next < nbt.length() && nbt.charAt(next) == ',') {
            return "{" + nbt.substring(next + 1);
        }
        return "{}";
    }

    private static String addTagToNbt(String nbt, String tag) {
        if (nbt == null || nbt.length() < 2 || nbt.charAt(0) != '{') {
            return "{Tags:[\"" + tag + "\"]}";
        }

        int tagsPos = nbt.indexOf("Tags:[");
        if (tagsPos != -1) {
            int tagsEnd = findMatchingBracket(nbt, tagsPos + "Tags:".length());
            if (tagsEnd != -1) {
                return nbt.substring(0, tagsEnd) + ",\"" + tag + "\"" + nbt.substring(tagsEnd);
            }
        }

        if (nbt.equals("{}")) {
            return "{Tags:[\"" + tag + "\"]}";
        }
        return "{Tags:[\"" + tag + "\"]," + nbt.substring(1);
    }

    /** Finds the matching closing bracket for an opening {@code [}. */
    private static int findMatchingBracket(String s, int openPos) {
        if (openPos < 0 || openPos >= s.length() || s.charAt(openPos) != '[') return -1;
        int depth = 1;
        for (int i = openPos + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /** Parses top-level NBT objects from an array content string (content inside {@code [...]}). */
    private static List<String> parseNbtArray(String arrayContent) {
        List<String> entries = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    entries.add(arrayContent.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return entries;
    }

    private void dispatchSummonCommand(World world, String command) {
        Logger logger = Bukkit.getLogger();
        Filter previousFilter = logger.getFilter();
        Filter summonNoiseFilter = record -> {
            String message = record.getMessage();
            if (message != null && (
                    message.startsWith("Summoned new Block Display")
                            || message.startsWith("Summoned new Item Display")
                            || message.startsWith("Summoned new Interaction"))) {
                return false;
            }
            return previousFilter == null || previousFilter.isLoggable(record);
        };

        try {
            logger.setFilter(summonNoiseFilter);
            Bukkit.dispatchCommand(silentCommandSender, command);
        } finally {
            logger.setFilter(previousFilter);
        }
    }

    private Entity getTopVehicle(Entity entity) {
        Entity top = entity;
        while (top != null && top.getVehicle() != null) {
            top = top.getVehicle();
        }
        return top;
    }

    private record ModelDefinition(String displayName, String tag, String summonTemplate, float yawOffsetDegrees) {}
    private record AttachedModel(UUID rootId, String modelId, float yawOffsetDegrees) {}

    private static final String FISH_STALL_COMMAND = """
        summon block_display %x% %y% %z% {Tags:["fr_fish_stall"],Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0f,0f,0.99f,1.02f,0f,1f,0f,0f,-0.125f,0f,0f,0.72875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0f,0f,0.99f,2.01f,0f,1f,0f,0f,-0.125f,0f,0f,0.72875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,0f,1f,2.37375f,0.5f,0.8660254038f,0f,-0.125f,-0.8660254038f,0.5f,0f,1.03625f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,0f,1f,-0.351875f,0.5f,0.8660254038f,0f,-0.125f,-0.8660254038f,0.5f,0f,1.03625f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_spruce_log",Properties:{axis:"x"}},transformation:[0f,0f,0.25f,0.01f,-2.7f,0f,0f,2.6875f,0f,-0.253f,0f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_spruce_log",Properties:{axis:"x"}},transformation:[0f,0f,0.25f,2.76f,-2.7f,0f,0f,2.6875f,0f,-0.253f,0f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,-0.8660254038f,0.25f,2.6975f,0f,0.5f,0.4330127019f,1.6875f,-0.469f,0f,0f,0.958125f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,0.8660254038f,-0.25f,0.3225f,0f,0.5f,0.4330127019f,1.6875f,0.473f,0f,0f,0.4875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,-1f,0f,2.9475f,0f,0f,0.5f,2.125f,-0.5f,0f,0f,0.97375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,-0.877f,0f,1.94875f,0f,0f,0.5f,2.125f,-0.5f,0f,0f,0.97375f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:cyan_banner",Count:1},item_display:"none",transformation:[-0.564f,0f,0f,1.26f,0f,-0.1811733316f,0.4829629131f,2.5f,0f,0.6761480784f,0.1294095226f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:white_banner",Count:1},item_display:"none",transformation:[-0.65f,0f,0f,1.76f,0f,-0.1811733316f,0.4829629131f,2.5f,0f,0.6761480784f,0.1294095226f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:cyan_banner",Count:1},item_display:"none",transformation:[-0.571f,0f,0f,2.26f,0f,-0.1811733316f,0.4829629131f,2.5f,0f,0.6761480784f,0.1294095226f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:white_banner",Count:1},item_display:"none",transformation:[-0.65f,0f,0f,2.76f,0f,-0.1811733316f,0.4829629131f,2.5f,0f,0.6761480784f,0.1294095226f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_spruce_log",Properties:{axis:"x"}},transformation:[-1.301f,0f,0f,2.81875f,0f,0f,0.25f,2.375f,0f,0.25f,0f,0.59875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_chain",Properties:{axis:"x"}},transformation:[0.5f,0f,0f,1.135f,0f,0.5f,0f,1.6875f,0f,0f,0.5f,0.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_chain",Properties:{axis:"x"}},transformation:[0.5f,0f,0f,1.5725f,0f,0.5f,0f,1.6875f,0f,0f,0.5f,0.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_chain",Properties:{axis:"x"}},transformation:[0.5f,0f,0f,2.0725f,0f,0.5f,0f,1.6875f,0f,0f,0.5f,0.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_chain",Properties:{axis:"x"}},transformation:[0.5f,0f,0f,2.4475f,0f,0.5f,0f,1.6875f,0f,0f,0.5f,0.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:cyan_carpet",Properties:{}},transformation:[0f,0f,1.57f,1.453125f,0f,0.1f,0f,1.04875f,-0.6f,0f,0f,1.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:cyan_carpet",Properties:{}},transformation:[0f,-0.1f,0f,0.01f,0f,0f,0.5f,0.54875f,-0.6f,0f,0f,1.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:cyan_carpet",Properties:{}},transformation:[0f,-0.1f,0f,3.02f,0f,0f,0.5f,0.54625f,-0.6f,0f,0f,1.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_spruce_log",Properties:{axis:"x"}},transformation:[1e-10f,0f,0.25f,0.01f,-2.1650635093f,0.125f,0f,2f,-1.2500000002f,-0.2165063509f,0f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_spruce_log",Properties:{axis:"x"}},transformation:[1e-10f,0f,0.25f,2.76375f,-2.1650635093f,0.125f,0f,2f,-1.2500000002f,-0.2165063509f,0f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,0f,0.5f,2.635f,1f,0f,0f,-0.0625f,0f,1f,0f,-0.15125f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0f,0f,0.99f,0.028125f,0f,1f,0f,0f,-0.125f,0f,0f,0.72875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:cyan_carpet",Properties:{}},transformation:[0f,0f,1.442f,0.01f,0f,0.1f,0f,1.04875f,-0.6f,0f,0f,1.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0f,0f,1f,0.02f,0f,0.11f,0f,0.9375f,-1f,0f,0f,1.59875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,-1f,0f,1.0725f,0f,0f,0.5f,2.125f,-0.5f,0f,0f,0.97375f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:cyan_banner",Count:1},item_display:"none",transformation:[-0.563f,0f,0f,0.26f,0f,-0.1811733316f,0.4829629131f,2.5f,0f,0.6761480784f,0.1294095226f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:white_banner",Count:1},item_display:"none",transformation:[-0.65f,0f,0f,0.76f,0f,-0.1811733316f,0.4829629131f,2.5f,0f,0.6761480784f,0.1294095226f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_spruce_log",Properties:{axis:"x"}},transformation:[-1.374f,0f,0f,1.523125f,0f,0f,0.25f,2.375f,0f,0.25f,0f,0.59875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,0f,0.5f,-0.115f,1f,0f,0f,-0.0625f,0f,1f,0f,-0.15125f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_chain",Properties:{axis:"x"}},transformation:[0.5f,0f,0f,0.6975f,0f,0.5f,0f,1.6875f,0f,0f,0.5f,0.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_chain",Properties:{axis:"x"}},transformation:[0.5f,0f,0f,0.1975f,0f,0.5f,0f,1.6875f,0f,0f,0.5f,0.47375f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-590073994,344102185,2055755993,915938051],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjRiOTUzYjJjMGU5NTI1NzRmMWVkMjljODFlODJlNTNiY2RiMWJhNjgzMjU5YzIwZGFlZWY3ZDU1NGEyYTc5OCJ9fX0="}]}}},item_display:"none",transformation:[-0.772740661f,0f,0.1294095226f,2.51f,0f,0.5f,0f,1.29875f,-0.2070552361f,0f,-0.4829629131f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:iron_axe",Count:1},item_display:"none",transformation:[-0.4829629131f,-0.1294095226f,0f,1.548125f,0f,0f,0.5f,0.75f,-0.1294095226f,0.4829629131f,0f,0.294375f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;1798132656,1994539422,-1514160627,-598658127],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjYyMDUxOWI3NDUzNmMxZjg1YjdjN2U1ZTExY2U1YzA1OWMyZmY3NTljYjhkZjI1NGZjN2Y5Y2U3ODFkMjkifX19"}]}}},item_display:"none",transformation:[-0.5f,0f,0.8660254038f,1.9475f,0f,1f,0f,0.5f,-0.8660254038f,0f,-0.5f,1.28625f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;109313835,-2128702251,1720864683,-219315891],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjYyMDUxOWI3NDUzNmMxZjg1YjdjN2U1ZTExY2U1YzA1OWMyZmY3NTljYjhkZjI1NGZjN2Y5Y2U3ODFkMjkifX19"}]}}},item_display:"none",transformation:[-0.692820323f,0f,0.4f,2.1975f,0f,0.8f,0f,0.90875f,-0.4f,0f,-0.692820323f,1.28625f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;348764080,-1418629452,7796777,2095124241],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjYyMDUxOWI3NDUzNmMxZjg1YjdjN2U1ZTExY2U1YzA1OWMyZmY3NTljYjhkZjI1NGZjN2Y5Y2U3ODFkMjkifX19"}]}}},item_display:"none",transformation:[0.8660254038f,0f,0.65f,2.5725f,0f,1f,0f,0.5f,-0.5f,0f,1.1258330249f,1.16125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1706129047,-1632838415,1333728293,-1745545183],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjYyMDUxOWI3NDUzNmMxZjg1YjdjN2U1ZTExY2U1YzA1OWMyZmY3NTljYjhkZjI1NGZjN2Y5Y2U3ODFkMjkifX19"}]}}},item_display:"none",transformation:[0.7071067812f,0f,-0.7071067812f,1.3225f,0f,1f,0f,0.5f,0.7071067812f,0f,0.7071067812f,1.22375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0f,0f,1f,1.01f,0f,0.11f,0f,0.9375f,-1f,0f,0f,1.59875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0f,0f,1f,1.995f,0f,0.11f,0f,0.9375f,-1f,0f,0f,1.59875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:lantern",Properties:{hanging:"false"}},transformation:[0f,0f,0.5f,2.6975f,0f,0.5f,0f,1.9375f,-0.5f,0f,0f,1.97375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:lantern",Properties:{hanging:"false"}},transformation:[0f,0f,0.5f,1.26f,0f,0.5f,0f,1.9375f,-0.5f,0f,0f,1.97375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:lantern",Properties:{hanging:"false"}},transformation:[0f,0f,0.5f,-0.1775f,0f,0.5f,0f,1.9375f,-0.5f,0f,0f,1.97375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,0f,0.5f,-0.115f,1f,0f,0f,0.5625f,0f,0.5f,0f,0.120625f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,0f,0.5f,2.635f,1f,0f,0f,0.5625f,0f,0.5f,0f,0.120625f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:beehive",Properties:{facing:"east",honey_level:"0"}},transformation:[0f,0f,1.5f,0.26f,0f,0.75f,0f,0f,-0.7f,0f,0f,0.59875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0f,0f,0.99f,1.76f,0f,0.7f,0f,0f,-0.05f,0f,0f,0.001875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0.05f,0f,2.54e-8f,2.76f,0f,0.7f,0f,0f,-1.8e-9f,0f,0.7f,0.001875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;1232297372,-1009617394,709773356,-297844982],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjYyMDUxOWI3NDUzNmMxZjg1YjdjN2U1ZTExY2U1YzA1OWMyZmY3NTljYjhkZjI1NGZjN2Y5Y2U3ODFkMjkifX19"}]}}},item_display:"none",transformation:[0.772740661f,0f,-0.2070552361f,0.51f,0f,0.8f,0f,1.125f,0.2070552361f,0f,0.772740661f,0.28625f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0.05f,0f,2.54e-8f,1.76f,0f,0.7f,0f,0f,-1.8e-9f,0f,0.7f,0.001875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:salmon",Count:1},item_display:"none",transformation:[0.4084283342f,0.3867719558f,8.6e-9f,0.676875f,-0.3867719558f,0.4084283342f,-8.2e-9f,1.68375f,-1.19e-8f,0f,0.5625f,0.7525f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:cod",Count:1},item_display:"none",transformation:[0.4084283342f,0.3867719558f,1.83e-8f,1.0725f,-0.3867719558f,0.4084283342f,-1.73e-8f,1.68375f,-2.51e-8f,0f,0.5625f,0.7525f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:tropical_fish",Count:1},item_display:"none",transformation:[0.4084283342f,0.3867719558f,1.92e-8f,2.26f,-0.3867719558f,0.4084283342f,-1.82e-8f,1.68375f,-2.65e-8f,0f,0.5625f,0.7525f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:cod",Count:1},item_display:"none",transformation:[0.547095291f,0.1307401721f,2.45e-8f,0.898125f,-2.51e-8f,0f,0.5625f,1.068125f,0.1307401721f,-0.547095291f,5.8e-9f,1.203125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:cod",Count:1},item_display:"none",transformation:[0.4774974159f,-0.2892338329f,0.0688930886f,1.01625f,-0.0934971888f,-0.0223431558f,0.5542249625f,1.079375f,-0.2822423488f,-0.4819240846f,-0.067042399f,1.01f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:suspicious_sand",Properties:{dusted:"0"}},transformation:[0f,0f,0.99f,1.76f,0.05f,0f,0f,0.5f,0f,0.7f,0f,0.001875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1375834106,-283742074,-1064724453,-1796022227],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmVkMjljMGI0ZjE3NWI2NDBhZmNhYjQ0NWJkMTI5YzhmYjhiYTdjNDY1MTJjYTU2M2YxMDExOTU5MzJhZDdmNCJ9fX0="}]}}},item_display:"none",transformation:[-0.1275231576f,0f,0.2150298683f,1.88375f,0f,0.25f,0f,1.201875f,-0.2150298683f,0f,-0.1275231576f,0.979375f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1566452247,464696986,1329890268,138674526],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmVkMjljMGI0ZjE3NWI2NDBhZmNhYjQ0NWJkMTI5YzhmYjhiYTdjNDY1MTJjYTU2M2YxMDExOTU5MzJhZDdmNCJ9fX0="}]}}},item_display:"none",transformation:[-0.0607344635f,0f,0.2425104636f,1.730625f,0f,0.25f,0f,1.201875f,-0.2425104636f,0f,-0.0607344635f,1.0475f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:salmon",Count:1},item_display:"none",transformation:[0.547095291f,0.1307401721f,2.45e-8f,2.5725f,-2.51e-8f,0f,0.5625f,0.5625f,0.1307401721f,-0.547095291f,5.8e-9f,0.34875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:salmon",Count:1},item_display:"none",transformation:[0.2944075279f,0.4793020525f,1.32e-8f,1.9475f,-2.52e-8f,0f,0.5625f,0.5625f,0.4793020525f,-0.2944075279f,2.15e-8f,0.34875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:salmon",Count:1},item_display:"none",transformation:[0.3735930145f,-0.3945114762f,0.1455857296f,2.02375f,-0.1001039726f,0.1057090315f,0.5433332728f,0.625f,-0.4084283342f,-0.3867719558f,-1.82e-8f,0.41125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:salmon",Count:1},item_display:"none",transformation:[-0.5391685063f,0.1603233351f,-2.41e-8f,2.26f,-2.51e-8f,0f,0.5625f,0.565f,0.1603233351f,0.5391685063f,7.2e-9f,0.22375f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:salmon",Count:1},item_display:"none",transformation:[-0.2838276727f,-0.4647132632f,0.1410307952f,2.58375f,0.0414947082f,0.1395470779f,0.5433332791f,0.59625f,-0.4838659849f,0.2845600922f,-0.0361318775f,0.281875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;100632635,-1061949407,431881124,-871901322],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2NjNGE0NTVkNTM3YzgyMTgyMGFhZDIwYjk1MzY4NjQ4NDBhODczYmM5MDE2M2FhMzU1ODY2YjMyZTM1ZDA0MCJ9fX0="}]}}},item_display:"none",transformation:[0.0461692737f,0f,0.2253184373f,1.56625f,0f,0.23f,0f,1.195f,-0.2253184373f,0f,0.0461692737f,1.056875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-2142556013,1080982683,-121412136,2128636960],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2Y4NTcyY2UyMDkyZmQwNWIyOGUxZTljN2M2YjA2NWI2MzI3MjhlYmY3ZTAwZDAxZTQ0MjcyMDkzMzc3MzNmZCJ9fX0="}]}}},item_display:"none",transformation:[0.1917937391f,0f,-0.1269455066f,2.018125f,0f,0.23f,0f,1.18875f,0.1269455066f,0f,0.1917937391f,0.845f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_birch_wood",Properties:{axis:"x"}},transformation:[0.593644971f,0f,0.1891413469f,1.396875f,0f,0.0155f,0f,1.05625f,-0.346759136f,0f,0.3238063478f,0.978125f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_birch_wood",Properties:{axis:"x"}},transformation:[0.1299542809f,0f,0.0327845001f,2.058125f,0f,0.0155f,0f,1.053125f,-0.0759087272f,0f,0.0561264336f,0.771875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_birch_wood",Properties:{axis:"x"}},transformation:[0.0798722324f,0f,0.0413589079f,2.1325f,0f,0.0155f,0f,1.055f,-0.0466548656f,0f,0.0708056547f,0.718125f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:redstone_wire",Properties:{west:"none",south:"none",north:"none",east:"none"}},transformation:[0f,0f,1f,0.44f,0f,1f,0f,0.74125f,-1f,0f,0f,0.876875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:redstone_wire",Properties:{west:"none",south:"none",north:"none",east:"none"}},transformation:[-0.9997770131f,0f,-0.0211169142f,1.88375f,0f,1f,0f,0.74125f,0.0211169142f,0f,-0.9997770131f,0.678125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;1678594224,-1482929719,-1114940117,-106496726],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmVkMjljMGI0ZjE3NWI2NDBhZmNhYjQ0NWJkMTI5YzhmYjhiYTdjNDY1MTJjYTU2M2YxMDExOTU5MzJhZDdmNCJ9fX0="}]}}},item_display:"none",transformation:[-0.0607344635f,0f,0.2425104636f,1.191875f,0f,0.25f,0f,0.879375f,-0.2425104636f,0f,-0.0607344635f,0.27625f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;478660790,-1561738004,1905798997,-1251048571],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2NjNGE0NTVkNTM3YzgyMTgyMGFhZDIwYjk1MzY4NjQ4NDBhODczYmM5MDE2M2FhMzU1ODY2YjMyZTM1ZDA0MCJ9fX0="}]}}},item_display:"none",transformation:[0.0461692737f,0f,0.2253184373f,1.0275f,0f,0.23f,0f,0.8725f,-0.2253184373f,0f,0.0461692737f,0.285625f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:cauldron",Properties:{}},transformation:[0.1060663927f,0f,0.3596872535f,0.2775f,0f,0.561f,0f,-0.0275f,-0.3596872535f,0f,0.1060663927f,-0.215625f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1331631768,-1055285444,1549897290,-944652783],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNThlYjYxZjgzNDAzNzYyM2UzMmZjYTJkMzJjZWU1MjMyOWFjNDk4YjY1ODU1N2IwMTAyY2FmOTE1NTgzOGQ0MiJ9fX0="}]}}},item_display:"none",transformation:[-0.4330127019f,0f,0.25f,2.8225f,0f,0.5f,0f,1.28625f,-0.25f,0f,-0.4330127019f,1.03625f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:tropical_fish",Count:1},item_display:"none",transformation:[0.1007254838f,0.4238493568f,0.3558262914f,0.51f,-0.3577798339f,0.3257980464f,-0.2868021504f,0.375f,-0.4222016312f,-0.1749674419f,0.3279305214f,-0.33875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:salmon",Count:1},item_display:"none",transformation:[0.3887926849f,0.141903885f,-0.3809327836f,0.488125f,-0.3569310148f,0.3714319208f,-0.2259310269f,0.34f,0.194542409f,0.3978792098f,0.3467731759f,-0.373125f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:redstone_wire",Properties:{west:"none",south:"none",north:"none",east:"none"}},transformation:[0.1057288844f,0.959412472f,0.0059551085f,0.63f,0.0079188429f,-3e-10f,-0.9997770132f,0.85375f,-0.3596994509f,0.2820065757f,-0.0202598312f,-0.131875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:redstone_wire",Properties:{west:"none",south:"none",north:"none",east:"none"}},transformation:[0.2279390998f,0f,0.7707639159f,-0.018125f,0f,1.393f,0f,0.34125f,-0.7564018554f,0f,0.2322670574f,-0.060625f,0f,0f,0f,1f]}]}
        """;
}

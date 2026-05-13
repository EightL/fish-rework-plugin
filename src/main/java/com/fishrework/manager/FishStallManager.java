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

import java.util.*;

public class FishStallManager implements Runnable {

    private final FishRework plugin;
    private final NamespacedKey stallKey;
    private final NamespacedKey interactKey;
    private final NamespacedKey modelIdKey;
    private final NamespacedKey modelRoleKey;
    private final NamespacedKey attachedParentKey;
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
        registerModel("fish_stall", "Fish Market Stall", "fr_fish_stall", FISH_STALL_COMMAND);
        registerModel("sailfish", "Sailfish", "fr_sailfish", SAILFISH_COMMAND, 180.0f);
        registerModel("catfish", "Catfish", "fr_catfish", CATFISH_COMMAND, 180.0f);
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
        ModelDefinition def = registry.get(modelId);
        if (def == null || loc.getWorld() == null) return null;

        String cmd = buildSummonCommand(def, loc.clone().add(-0.5, 0.0, -0.5));

        Set<UUID> existingRoots = getTaggedRootIds(loc.getWorld(), def.tag);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

        String instanceId = modelId + "_" + System.currentTimeMillis();
        List<Entity> spawnedRoots = findNewModelRoots(loc.getWorld(), def, existingRoots);
        if (spawnedRoots.isEmpty()) {
            return null;
        }

        Entity root = spawnedRoots.get(0);
        for (int i = 1; i < spawnedRoots.size(); i++) {
            removeWithPassengers(spawnedRoots.get(i));
        }
        setNonPersistentTree(root);
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
        hitbox.setPersistent(false);
        hitbox.getPersistentDataContainer().set(interactKey, PersistentDataType.STRING, instanceId);
        activeInstances.put(instanceId + "_hitbox", hitbox.getUniqueId());
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
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), buildSummonCommand(def, parent.getLocation()));

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
        if (uid == null) return false;
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

    private record ModelDefinition(String displayName, String tag, String summonTemplate, float yawOffsetDegrees) {}
    private record AttachedModel(UUID rootId, String modelId, float yawOffsetDegrees) {}

    private static final String FISH_STALL_COMMAND = """
        summon block_display %x% %y% %z% {Tags:["fr_fish_stall"],Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0f,0f,0.99f,1.02f,0f,1f,0f,0f,-0.125f,0f,0f,0.72875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0f,0f,0.99f,2.01f,0f,1f,0f,0f,-0.125f,0f,0f,0.72875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,0f,1f,2.37375f,0.5f,0.8660254038f,0f,-0.125f,-0.8660254038f,0.5f,0f,1.03625f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,0f,1f,-0.351875f,0.5f,0.8660254038f,0f,-0.125f,-0.8660254038f,0.5f,0f,1.03625f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_spruce_log",Properties:{axis:"x"}},transformation:[0f,0f,0.25f,0.01f,-2.7f,0f,0f,2.6875f,0f,-0.253f,0f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_spruce_log",Properties:{axis:"x"}},transformation:[0f,0f,0.25f,2.76f,-2.7f,0f,0f,2.6875f,0f,-0.253f,0f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,-0.8660254038f,0.25f,2.6975f,0f,0.5f,0.4330127019f,1.6875f,-0.469f,0f,0f,0.958125f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,0.8660254038f,-0.25f,0.3225f,0f,0.5f,0.4330127019f,1.6875f,0.473f,0f,0f,0.4875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,-1f,0f,2.9475f,0f,0f,0.5f,2.125f,-0.5f,0f,0f,0.97375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,-0.877f,0f,1.94875f,0f,0f,0.5f,2.125f,-0.5f,0f,0f,0.97375f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:cyan_banner",Count:1},item_display:"none",transformation:[-0.564f,0f,0f,1.26f,0f,-0.1811733316f,0.4829629131f,2.5f,0f,0.6761480784f,0.1294095226f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:white_banner",Count:1},item_display:"none",transformation:[-0.65f,0f,0f,1.76f,0f,-0.1811733316f,0.4829629131f,2.5f,0f,0.6761480784f,0.1294095226f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:cyan_banner",Count:1},item_display:"none",transformation:[-0.571f,0f,0f,2.26f,0f,-0.1811733316f,0.4829629131f,2.5f,0f,0.6761480784f,0.1294095226f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:white_banner",Count:1},item_display:"none",transformation:[-0.65f,0f,0f,2.76f,0f,-0.1811733316f,0.4829629131f,2.5f,0f,0.6761480784f,0.1294095226f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_spruce_log",Properties:{axis:"x"}},transformation:[-1.301f,0f,0f,2.81875f,0f,0f,0.25f,2.375f,0f,0.25f,0f,0.59875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_chain",Properties:{axis:"x"}},transformation:[0.5f,0f,0f,1.135f,0f,0.5f,0f,1.6875f,0f,0f,0.5f,0.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_chain",Properties:{axis:"x"}},transformation:[0.5f,0f,0f,1.5725f,0f,0.5f,0f,1.6875f,0f,0f,0.5f,0.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_chain",Properties:{axis:"x"}},transformation:[0.5f,0f,0f,2.0725f,0f,0.5f,0f,1.6875f,0f,0f,0.5f,0.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_chain",Properties:{axis:"x"}},transformation:[0.5f,0f,0f,2.4475f,0f,0.5f,0f,1.6875f,0f,0f,0.5f,0.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:cyan_carpet",Properties:{}},transformation:[0f,0f,1.57f,1.453125f,0f,0.1f,0f,1.04875f,-0.6f,0f,0f,1.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:cyan_carpet",Properties:{}},transformation:[0f,-0.1f,0f,0.01f,0f,0f,0.5f,0.54875f,-0.6f,0f,0f,1.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:cyan_carpet",Properties:{}},transformation:[0f,-0.1f,0f,3.02f,0f,0f,0.5f,0.54625f,-0.6f,0f,0f,1.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_spruce_log",Properties:{axis:"x"}},transformation:[1e-10f,0f,0.25f,0.01f,-2.1650635093f,0.125f,0f,2f,-1.2500000002f,-0.2165063509f,0f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_spruce_log",Properties:{axis:"x"}},transformation:[1e-10f,0f,0.25f,2.76375f,-2.1650635093f,0.125f,0f,2f,-1.2500000002f,-0.2165063509f,0f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,0f,0.5f,2.635f,1f,0f,0f,-0.0625f,0f,1f,0f,-0.15125f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0f,0f,0.99f,0.028125f,0f,1f,0f,0f,-0.125f,0f,0f,0.72875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:cyan_carpet",Properties:{}},transformation:[0f,0f,1.442f,0.01f,0f,0.1f,0f,1.04875f,-0.6f,0f,0f,1.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0f,0f,1f,0.02f,0f,0.11f,0f,0.9375f,-1f,0f,0f,1.59875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,-1f,0f,1.0725f,0f,0f,0.5f,2.125f,-0.5f,0f,0f,0.97375f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:cyan_banner",Count:1},item_display:"none",transformation:[-0.563f,0f,0f,0.26f,0f,-0.1811733316f,0.4829629131f,2.5f,0f,0.6761480784f,0.1294095226f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:white_banner",Count:1},item_display:"none",transformation:[-0.65f,0f,0f,0.76f,0f,-0.1811733316f,0.4829629131f,2.5f,0f,0.6761480784f,0.1294095226f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_spruce_log",Properties:{axis:"x"}},transformation:[-1.374f,0f,0f,1.523125f,0f,0f,0.25f,2.375f,0f,0.25f,0f,0.59875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,0f,0.5f,-0.115f,1f,0f,0f,-0.0625f,0f,1f,0f,-0.15125f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_chain",Properties:{axis:"x"}},transformation:[0.5f,0f,0f,0.6975f,0f,0.5f,0f,1.6875f,0f,0f,0.5f,0.47375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:iron_chain",Properties:{axis:"x"}},transformation:[0.5f,0f,0f,0.1975f,0f,0.5f,0f,1.6875f,0f,0f,0.5f,0.47375f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-590073994,344102185,2055755993,915938051],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjRiOTUzYjJjMGU5NTI1NzRmMWVkMjljODFlODJlNTNiY2RiMWJhNjgzMjU5YzIwZGFlZWY3ZDU1NGEyYTc5OCJ9fX0="}]}}},item_display:"none",transformation:[-0.772740661f,0f,0.1294095226f,2.51f,0f,0.5f,0f,1.29875f,-0.2070552361f,0f,-0.4829629131f,0.84875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:iron_axe",Count:1},item_display:"none",transformation:[-0.4829629131f,-0.1294095226f,0f,1.548125f,0f,0f,0.5f,0.75f,-0.1294095226f,0.4829629131f,0f,0.294375f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;1798132656,1994539422,-1514160627,-598658127],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjYyMDUxOWI3NDUzNmMxZjg1YjdjN2U1ZTExY2U1YzA1OWMyZmY3NTljYjhkZjI1NGZjN2Y5Y2U3ODFkMjkifX19"}]}}},item_display:"none",transformation:[-0.5f,0f,0.8660254038f,1.9475f,0f,1f,0f,0.5f,-0.8660254038f,0f,-0.5f,1.28625f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;109313835,-2128702251,1720864683,-219315891],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjYyMDUxOWI3NDUzNmMxZjg1YjdjN2U1ZTExY2U1YzA1OWMyZmY3NTljYjhkZjI1NGZjN2Y5Y2U3ODFkMjkifX19"}]}}},item_display:"none",transformation:[-0.692820323f,0f,0.4f,2.1975f,0f,0.8f,0f,0.90875f,-0.4f,0f,-0.692820323f,1.28625f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;348764080,-1418629452,7796777,2095124241],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjYyMDUxOWI3NDUzNmMxZjg1YjdjN2U1ZTExY2U1YzA1OWMyZmY3NTljYjhkZjI1NGZjN2Y5Y2U3ODFkMjkifX19"}]}}},item_display:"none",transformation:[0.8660254038f,0f,0.65f,2.5725f,0f,1f,0f,0.5f,-0.5f,0f,1.1258330249f,1.16125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1706129047,-1632838415,1333728293,-1745545183],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjYyMDUxOWI3NDUzNmMxZjg1YjdjN2U1ZTExY2U1YzA1OWMyZmY3NTljYjhkZjI1NGZjN2Y5Y2U3ODFkMjkifX19"}]}}},item_display:"none",transformation:[0.7071067812f,0f,-0.7071067812f,1.3225f,0f,1f,0f,0.5f,0.7071067812f,0f,0.7071067812f,1.22375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0f,0f,1f,1.01f,0f,0.11f,0f,0.9375f,-1f,0f,0f,1.59875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0f,0f,1f,1.995f,0f,0.11f,0f,0.9375f,-1f,0f,0f,1.59875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:lantern",Properties:{hanging:"false"}},transformation:[0f,0f,0.5f,2.6975f,0f,0.5f,0f,1.9375f,-0.5f,0f,0f,1.97375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:lantern",Properties:{hanging:"false"}},transformation:[0f,0f,0.5f,1.26f,0f,0.5f,0f,1.9375f,-0.5f,0f,0f,1.97375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:lantern",Properties:{hanging:"false"}},transformation:[0f,0f,0.5f,-0.1775f,0f,0.5f,0f,1.9375f,-0.5f,0f,0f,1.97375f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,0f,0.5f,-0.115f,1f,0f,0f,0.5625f,0f,0.5f,0f,0.120625f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:spruce_fence",Properties:{}},transformation:[0f,0f,0.5f,2.635f,1f,0f,0f,0.5625f,0f,0.5f,0f,0.120625f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:beehive",Properties:{facing:"east",honey_level:"0"}},transformation:[0f,0f,1.5f,0.26f,0f,0.75f,0f,0f,-0.7f,0f,0f,0.59875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0f,0f,0.99f,1.76f,0f,0.7f,0f,0f,-0.05f,0f,0f,0.001875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0.05f,0f,2.54e-8f,2.76f,0f,0.7f,0f,0f,-1.8e-9f,0f,0.7f,0.001875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;1232297372,-1009617394,709773356,-297844982],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjYyMDUxOWI3NDUzNmMxZjg1YjdjN2U1ZTExY2U1YzA1OWMyZmY3NTljYjhkZjI1NGZjN2Y5Y2U3ODFkMjkifX19"}]}}},item_display:"none",transformation:[0.772740661f,0f,-0.2070552361f,0.51f,0f,0.8f,0f,1.125f,0.2070552361f,0f,0.772740661f,0.28625f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:barrel",Properties:{facing:"down",open:"false"}},transformation:[0.05f,0f,2.54e-8f,1.76f,0f,0.7f,0f,0f,-1.8e-9f,0f,0.7f,0.001875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:salmon",Count:1},item_display:"none",transformation:[0.4084283342f,0.3867719558f,8.6e-9f,0.676875f,-0.3867719558f,0.4084283342f,-8.2e-9f,1.68375f,-1.19e-8f,0f,0.5625f,0.7525f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:cod",Count:1},item_display:"none",transformation:[0.4084283342f,0.3867719558f,1.83e-8f,1.0725f,-0.3867719558f,0.4084283342f,-1.73e-8f,1.68375f,-2.51e-8f,0f,0.5625f,0.7525f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:tropical_fish",Count:1},item_display:"none",transformation:[0.4084283342f,0.3867719558f,1.92e-8f,2.26f,-0.3867719558f,0.4084283342f,-1.82e-8f,1.68375f,-2.65e-8f,0f,0.5625f,0.7525f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:cod",Count:1},item_display:"none",transformation:[0.547095291f,0.1307401721f,2.45e-8f,0.898125f,-2.51e-8f,0f,0.5625f,1.068125f,0.1307401721f,-0.547095291f,5.8e-9f,1.203125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:cod",Count:1},item_display:"none",transformation:[0.4774974159f,-0.2892338329f,0.0688930886f,1.01625f,-0.0934971888f,-0.0223431558f,0.5542249625f,1.079375f,-0.2822423488f,-0.4819240846f,-0.067042399f,1.01f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:suspicious_sand",Properties:{dusted:"0"}},transformation:[0f,0f,0.99f,1.76f,0.05f,0f,0f,0.5f,0f,0.7f,0f,0.001875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1375834106,-283742074,-1064724453,-1796022227],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmVkMjljMGI0ZjE3NWI2NDBhZmNhYjQ0NWJkMTI5YzhmYjhiYTdjNDY1MTJjYTU2M2YxMDExOTU5MzJhZDdmNCJ9fX0="}]}}},item_display:"none",transformation:[-0.1275231576f,0f,0.2150298683f,1.88375f,0f,0.25f,0f,1.201875f,-0.2150298683f,0f,-0.1275231576f,0.979375f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1566452247,464696986,1329890268,138674526],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmVkMjljMGI0ZjE3NWI2NDBhZmNhYjQ0NWJkMTI5YzhmYjhiYTdjNDY1MTJjYTU2M2YxMDExOTU5MzJhZDdmNCJ9fX0="}]}}},item_display:"none",transformation:[-0.0607344635f,0f,0.2425104636f,1.730625f,0f,0.25f,0f,1.201875f,-0.2425104636f,0f,-0.0607344635f,1.0475f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:salmon",Count:1},item_display:"none",transformation:[0.547095291f,0.1307401721f,2.45e-8f,2.5725f,-2.51e-8f,0f,0.5625f,0.5625f,0.1307401721f,-0.547095291f,5.8e-9f,0.34875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:salmon",Count:1},item_display:"none",transformation:[0.2944075279f,0.4793020525f,1.32e-8f,1.9475f,-2.52e-8f,0f,0.5625f,0.5625f,0.4793020525f,-0.2944075279f,2.15e-8f,0.34875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:salmon",Count:1},item_display:"none",transformation:[0.3735930145f,-0.3945114762f,0.1455857296f,2.02375f,-0.1001039726f,0.1057090315f,0.5433332728f,0.625f,-0.4084283342f,-0.3867719558f,-1.82e-8f,0.41125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:salmon",Count:1},item_display:"none",transformation:[-0.5391685063f,0.1603233351f,-2.41e-8f,2.26f,-2.51e-8f,0f,0.5625f,0.565f,0.1603233351f,0.5391685063f,7.2e-9f,0.22375f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:salmon",Count:1},item_display:"none",transformation:[-0.2838276727f,-0.4647132632f,0.1410307952f,2.58375f,0.0414947082f,0.1395470779f,0.5433332791f,0.59625f,-0.4838659849f,0.2845600922f,-0.0361318775f,0.281875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;100632635,-1061949407,431881124,-871901322],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2NjNGE0NTVkNTM3YzgyMTgyMGFhZDIwYjk1MzY4NjQ4NDBhODczYmM5MDE2M2FhMzU1ODY2YjMyZTM1ZDA0MCJ9fX0="}]}}},item_display:"none",transformation:[0.0461692737f,0f,0.2253184373f,1.56625f,0f,0.23f,0f,1.195f,-0.2253184373f,0f,0.0461692737f,1.056875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-2142556013,1080982683,-121412136,2128636960],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2Y4NTcyY2UyMDkyZmQwNWIyOGUxZTljN2M2YjA2NWI2MzI3MjhlYmY3ZTAwZDAxZTQ0MjcyMDkzMzc3MzNmZCJ9fX0="}]}}},item_display:"none",transformation:[0.1917937391f,0f,-0.1269455066f,2.018125f,0f,0.23f,0f,1.18875f,0.1269455066f,0f,0.1917937391f,0.845f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_birch_wood",Properties:{axis:"x"}},transformation:[0.593644971f,0f,0.1891413469f,1.396875f,0f,0.0155f,0f,1.05625f,-0.346759136f,0f,0.3238063478f,0.978125f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_birch_wood",Properties:{axis:"x"}},transformation:[0.1299542809f,0f,0.0327845001f,2.058125f,0f,0.0155f,0f,1.053125f,-0.0759087272f,0f,0.0561264336f,0.771875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:stripped_birch_wood",Properties:{axis:"x"}},transformation:[0.0798722324f,0f,0.0413589079f,2.1325f,0f,0.0155f,0f,1.055f,-0.0466548656f,0f,0.0708056547f,0.718125f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:redstone_wire",Properties:{west:"none",south:"none",north:"none",east:"none"}},transformation:[0f,0f,1f,0.44f,0f,1f,0f,0.74125f,-1f,0f,0f,0.876875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:redstone_wire",Properties:{west:"none",south:"none",north:"none",east:"none"}},transformation:[-0.9997770131f,0f,-0.0211169142f,1.88375f,0f,1f,0f,0.74125f,0.0211169142f,0f,-0.9997770131f,0.678125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;1678594224,-1482929719,-1114940117,-106496726],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmVkMjljMGI0ZjE3NWI2NDBhZmNhYjQ0NWJkMTI5YzhmYjhiYTdjNDY1MTJjYTU2M2YxMDExOTU5MzJhZDdmNCJ9fX0="}]}}},item_display:"none",transformation:[-0.0607344635f,0f,0.2425104636f,1.191875f,0f,0.25f,0f,0.879375f,-0.2425104636f,0f,-0.0607344635f,0.27625f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;478660790,-1561738004,1905798997,-1251048571],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2NjNGE0NTVkNTM3YzgyMTgyMGFhZDIwYjk1MzY4NjQ4NDBhODczYmM5MDE2M2FhMzU1ODY2YjMyZTM1ZDA0MCJ9fX0="}]}}},item_display:"none",transformation:[0.0461692737f,0f,0.2253184373f,1.0275f,0f,0.23f,0f,0.8725f,-0.2253184373f,0f,0.0461692737f,0.285625f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:cauldron",Properties:{}},transformation:[0.1060663927f,0f,0.3596872535f,0.2775f,0f,0.561f,0f,-0.0275f,-0.3596872535f,0f,0.1060663927f,-0.215625f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1331631768,-1055285444,1549897290,-944652783],properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNThlYjYxZjgzNDAzNzYyM2UzMmZjYTJkMzJjZWU1MjMyOWFjNDk4YjY1ODU1N2IwMTAyY2FmOTE1NTgzOGQ0MiJ9fX0="}]}}},item_display:"none",transformation:[-0.4330127019f,0f,0.25f,2.8225f,0f,0.5f,0f,1.28625f,-0.25f,0f,-0.4330127019f,1.03625f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:tropical_fish",Count:1},item_display:"none",transformation:[0.1007254838f,0.4238493568f,0.3558262914f,0.51f,-0.3577798339f,0.3257980464f,-0.2868021504f,0.375f,-0.4222016312f,-0.1749674419f,0.3279305214f,-0.33875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:salmon",Count:1},item_display:"none",transformation:[0.3887926849f,0.141903885f,-0.3809327836f,0.488125f,-0.3569310148f,0.3714319208f,-0.2259310269f,0.34f,0.194542409f,0.3978792098f,0.3467731759f,-0.373125f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:redstone_wire",Properties:{west:"none",south:"none",north:"none",east:"none"}},transformation:[0.1057288844f,0.959412472f,0.0059551085f,0.63f,0.0079188429f,-3e-10f,-0.9997770132f,0.85375f,-0.3596994509f,0.2820065757f,-0.0202598312f,-0.131875f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:redstone_wire",Properties:{west:"none",south:"none",north:"none",east:"none"}},transformation:[0.2279390998f,0f,0.7707639159f,-0.018125f,0f,1.393f,0f,0.34125f,-0.7564018554f,0f,0.2322670574f,-0.060625f,0f,0f,0f,1f]}]}
        """;
    private static final String SAILFISH_COMMAND = """
        summon block_display %x% %y% %z% {Tags:["fr_sailfish"],Passengers:[{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;2084223934,-1175901449,1551848533,-263578432],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1MzQ1ODMwMCwKICAicHJvZmlsZUlkIiA6ICI1NjExYmE1MjFmNDM0OWYwOWE0MTI5ZTM0NjQzMDM1OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJHdXlOYW1lZEthaW5lIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzU1NmE4MWIzNDUyYjE3ZGVhYThmY2I2MjNkYTJlZDIwNzM5N2JlYzEwZWQxOWI0YWQ3ZTY5NDA4YmY0YzdhM2QiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="}]}}},item_display:"none",transformation:[0f,0.0011f,0f,0f,-1f,0f,0f,0.125f,0f,0f,0.25f,1f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1572694974,1857458580,1825147817,1450183820],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1MzQ1ODMwMCwKICAicHJvZmlsZUlkIiA6ICI1NjExYmE1MjFmNDM0OWYwOWE0MTI5ZTM0NjQzMDM1OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJHdXlOYW1lZEthaW5lIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzU1NmE4MWIzNDUyYjE3ZGVhYThmY2I2MjNkYTJlZDIwNzM5N2JlYzEwZWQxOWI0YWQ3ZTY5NDA4YmY0YzdhM2QiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="}]}}},item_display:"none",transformation:[-0.003f,0f,0f,0f,0f,-0.5f,0f,0.25f,0f,0f,0.25f,1.0625f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1936375654,-118568443,-1671475765,95001181],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1MzQ1ODMwMCwKICAicHJvZmlsZUlkIiA6ICI1NjExYmE1MjFmNDM0OWYwOWE0MTI5ZTM0NjQzMDM1OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJHdXlOYW1lZEthaW5lIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzU1NmE4MWIzNDUyYjE3ZGVhYThmY2I2MjNkYTJlZDIwNzM5N2JlYzEwZWQxOWI0YWQ3ZTY5NDA4YmY0YzdhM2QiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="}]}}},item_display:"none",transformation:[0.003f,0f,0f,0f,0f,0.5f,0f,0f,0f,0f,0.25f,1.0625f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;2003651733,1955542324,1140664412,-993986360],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1NDE0MzYyMywKICAicHJvZmlsZUlkIiA6ICJhNzdkNmQ2YmFjOWE0NzY3YTFhNzU1NjYxOTllYmY5MiIsCiAgInByb2ZpbGVOYW1lIiA6ICIwOEJFRDUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjM0YzBjYjllY2Y5M2M0MmYzMzM0MWZhMjcwNGE4OGNkOWE1OTE2NDMyNTEwNTZhZGUyNmJhZTZlZGE5YzExNCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[0.25f,0f,0f,0f,0f,0.5f,0f,0.25f,0f,0f,1f,0.6875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1596890108,1036914211,566257566,-1867019504],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1MzQ1ODMwMCwKICAicHJvZmlsZUlkIiA6ICI1NjExYmE1MjFmNDM0OWYwOWE0MTI5ZTM0NjQzMDM1OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJHdXlOYW1lZEthaW5lIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzU1NmE4MWIzNDUyYjE3ZGVhYThmY2I2MjNkYTJlZDIwNzM5N2JlYzEwZWQxOWI0YWQ3ZTY5NDA4YmY0YzdhM2QiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="}]}}},item_display:"none",transformation:[0f,0f,0.0011f,0f,0f,0.75f,0f,0.3125f,-0.25f,0f,0f,0.75f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;1240040553,-1866706578,1889622958,-2013167360],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1MzUzMjA1OSwKICAicHJvZmlsZUlkIiA6ICIyZmI1ZGJhYTY2NTA0OGEyYjZhYzU5YWE2Nzk5MDYzNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJkcmFnbG9uZXIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjVkZDlmNTljNmUxMzExMTAxODI4NjRkMjZhOTE3OTdhNWZkMmY5YTc3N2FlNzMxNjg0ZTkzZDNhNzZlYTY2OCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[0.0011f,0f,0f,0f,0f,1f,0f,0.8125f,0f,0f,0.5f,-0.3125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-149118295,-1650225840,-1893857699,-921622020],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1MzUzMjA1OSwKICAicHJvZmlsZUlkIiA6ICIyZmI1ZGJhYTY2NTA0OGEyYjZhYzU5YWE2Nzk5MDYzNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJkcmFnbG9uZXIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjVkZDlmNTljNmUxMzExMTAxODI4NjRkMjZhOTE3OTdhNWZkMmY5YTc3N2FlNzMxNjg0ZTkzZDNhNzZlYTY2OCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[0f,0f,-0.0011f,0f,0f,1f,0f,0.75f,1f,0f,0f,0.0625f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1778128977,-798789409,-490053647,567524731],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1MzUzMjA1OSwKICAicHJvZmlsZUlkIiA6ICIyZmI1ZGJhYTY2NTA0OGEyYjZhYzU5YWE2Nzk5MDYzNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJkcmFnbG9uZXIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjVkZDlmNTljNmUxMzExMTAxODI4NjRkMjZhOTE3OTdhNWZkMmY5YTc3N2FlNzMxNjg0ZTkzZDNhNzZlYTY2OCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[0f,0.0011f,0f,0f,-1f,0f,0f,0.4375f,0f,0f,0.25f,0.375f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-420082805,1471150462,-674531723,-1099620881],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1Mzg0MTA5MCwKICAicHJvZmlsZUlkIiA6ICJhNDAxZjEzMTZlMjI0ZTNjOTg0ODk1MmVjMzhjMTEwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJHcmVlbnNoZWVwaXJhdGUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmUyODk3ODEzOTBkNDRkMDdhMmY4OTA1ZDY0ODFlNWE2ZTFiOWU3OWU2MzY1NzA3ZWYwMmI5ZDY3NzJmNzEyZSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[0.0011f,0f,0f,0f,0f,0.5f,0f,0f,0f,0f,0.25f,0.3125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;107819188,268005115,-327121949,-611542975],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1Mzg0MTA5MCwKICAicHJvZmlsZUlkIiA6ICJhNDAxZjEzMTZlMjI0ZTNjOTg0ODk1MmVjMzhjMTEwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJHcmVlbnNoZWVwaXJhdGUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmUyODk3ODEzOTBkNDRkMDdhMmY4OTA1ZDY0ODFlNWE2ZTFiOWU3OWU2MzY1NzA3ZWYwMmI5ZDY3NzJmNzEyZSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[0f,0f,-0.0011f,0f,0f,0.25f,0f,-0.0625f,0.125f,0f,0f,0.21875f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;160065490,879851410,1144357716,1240912711],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1Mzg0MTA5MCwKICAicHJvZmlsZUlkIiA6ICJhNDAxZjEzMTZlMjI0ZTNjOTg0ODk1MmVjMzhjMTEwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJHcmVlbnNoZWVwaXJhdGUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmUyODk3ODEzOTBkNDRkMDdhMmY4OTA1ZDY0ODFlNWE2ZTFiOWU3OWU2MzY1NzA3ZWYwMmI5ZDY3NzJmNzEyZSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[0.2588190451f,-0.0010625184f,0f,-0.1411761903f,0.9512512426f,0.0002803757f,0.0434120444f,-0.0486001916f,-0.1677312595f,-0.0000494378f,0.2462019383f,-0.3029663117f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;1946441618,744650558,-1617596262,-1800493053],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1MzQ1ODMwMCwKICAicHJvZmlsZUlkIiA6ICI1NjExYmE1MjFmNDM0OWYwOWE0MTI5ZTM0NjQzMDM1OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJHdXlOYW1lZEthaW5lIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzU1NmE4MWIzNDUyYjE3ZGVhYThmY2I2MjNkYTJlZDIwNzM5N2JlYzEwZWQxOWI0YWQ3ZTY5NDA4YmY0YzdhM2QiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="}]}}},item_display:"none",transformation:[0f,0.0647047613f,0.0010625184f,-0.1897047613f,-0.0434120444f,0.2378128106f,-0.0002803757f,-0.2161067884f,-0.2462019383f,-0.0419328149f,0.0000494378f,-0.209966216f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-621725256,-1018553853,305297667,-764460294],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1MzQ1ODMwMCwKICAicHJvZmlsZUlkIiA6ICI1NjExYmE1MjFmNDM0OWYwOWE0MTI5ZTM0NjQzMDM1OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJHdXlOYW1lZEthaW5lIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzU1NmE4MWIzNDUyYjE3ZGVhYThmY2I2MjNkYTJlZDIwNzM5N2JlYzEwZWQxOWI0YWQ3ZTY5NDA4YmY0YzdhM2QiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="}]}}},item_display:"none",transformation:[0f,-0.0647047613f,0.0010625184f,0.1897047613f,-0.0434120444f,0.2378128106f,0.0002803757f,-0.2161067884f,-0.2462019383f,-0.0419328149f,-0.0000494378f,-0.209966216f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;44125985,-1056529915,765736243,-288217185],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1Mzg0MTA5MCwKICAicHJvZmlsZUlkIiA6ICJhNDAxZjEzMTZlMjI0ZTNjOTg0ODk1MmVjMzhjMTEwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJHcmVlbnNoZWVwaXJhdGUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmUyODk3ODEzOTBkNDRkMDdhMmY4OTA1ZDY0ODFlNWE2ZTFiOWU3OWU2MzY1NzA3ZWYwMmI5ZDY3NzJmNzEyZSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[-0.2588190451f,-0.0010625184f,0f,0.1411761903f,0.9512512426f,-0.0002803757f,0.0434120444f,-0.0486001916f,-0.1677312595f,0.0000494378f,0.2462019383f,-0.3029663117f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;907292857,-596813655,-2070807712,-573967751],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1Mzk0MjYwMSwKICAicHJvZmlsZUlkIiA6ICIwMzBlMDA1OWQwY2M0YTZhODY3N2RkZWU3MjEzMjg1MyIsCiAgInByb2ZpbGVOYW1lIiA6ICJTbXVnRm9vZGllIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2ZhMDU2ZjI2YTlkNzQ3YTU3YTQ5Mjg0YWM1MzkyOTcxYTU1Mzg2OTQ4NmQ1YjM1Y2JjYmE3YWJlODIzMTVmMWUiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="}]}}},item_display:"none",transformation:[0.25f,0f,0f,0f,0f,0.0019318517f,0.1294095226f,-0.0161761903f,0f,-0.0005176381f,0.4829629131f,-0.5603703641f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1438850620,-1958706964,-2014383415,353855457],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1Mzk0MjYwMSwKICAicHJvZmlsZUlkIiA6ICIwMzBlMDA1OWQwY2M0YTZhODY3N2RkZWU3MjEzMjg1MyIsCiAgInByb2ZpbGVOYW1lIiA6ICJTbXVnRm9vZGllIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2ZhMDU2ZjI2YTlkNzQ3YTU3YTQ5Mjg0YWM1MzkyOTcxYTU1Mzg2OTQ4NmQ1YjM1Y2JjYmE3YWJlODIzMTVmMWUiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="}]}}},item_display:"none",transformation:[0.125f,0f,0f,0f,0f,0f,-0.0011f,0.0625f,0f,1f,0f,-0.75f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1953992593,622587555,1324369378,-65001817],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1Mzk0MjYwMSwKICAicHJvZmlsZUlkIiA6ICIwMzBlMDA1OWQwY2M0YTZhODY3N2RkZWU3MjEzMjg1MyIsCiAgInByb2ZpbGVOYW1lIiA6ICJTbXVnRm9vZGllIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2ZhMDU2ZjI2YTlkNzQ3YTU3YTQ5Mjg0YWM1MzkyOTcxYTU1Mzg2OTQ4NmQ1YjM1Y2JjYmE3YWJlODIzMTVmMWUiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="}]}}},item_display:"none",transformation:[0f,-0.125f,0f,0.03125f,0.0011f,0f,0f,0.0625f,0f,0f,0.5f,-0.8125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1552063441,-711627837,1842842128,-296349829],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1Mzk0MjYwMSwKICAicHJvZmlsZUlkIiA6ICIwMzBlMDA1OWQwY2M0YTZhODY3N2RkZWU3MjEzMjg1MyIsCiAgInByb2ZpbGVOYW1lIiA6ICJTbXVnRm9vZGllIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2ZhMDU2ZjI2YTlkNzQ3YTU3YTQ5Mjg0YWM1MzkyOTcxYTU1Mzg2OTQ4NmQ1YjM1Y2JjYmE3YWJlODIzMTVmMWUiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="}]}}},item_display:"none",transformation:[0f,-0.125f,0f,-0.09375f,0.0011f,0f,0f,0.0625f,0f,0f,0.5f,-0.8125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;2070365226,-240106607,-273198119,-1326749201],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1NDAwOTY3MywKICAicHJvZmlsZUlkIiA6ICIyNWIyMGEwZGI3MTA0NGZjODBmZjk5YjlkM2ZmNzM4MSIsCiAgInByb2ZpbGVOYW1lIiA6ICJBbGl0YW41M1lUIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2YzMTk4ODNmNTRkYzVlODM4ZDYzMjJlOTg0NWUxYzQ2NmRkYTAzMjE2YzIxYTJhNDEzOGEyYzNmNDcxN2E2YTQiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="}]}}},item_display:"none",transformation:[0.5f,0f,0f,0f,0f,0.5f,0f,0.25f,0f,0f,0.5f,-0.625f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1045764599,1845449126,1447892748,-60300765],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1NDA3MjM3NywKICAicHJvZmlsZUlkIiA6ICJhNDAxZjEzMTZlMjI0ZTNjOTg0ODk1MmVjMzhjMTEwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJHcmVlbnNoZWVwaXJhdGUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzU3ZWZiZDA5N2U5ZTAwNThmMzVmZTQ4MzNmZDhhYjY4YmU3MzgzZGQ5OThmOTcxMjBlZGViYTUyYTFlYzc3YSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[0.5f,0f,0f,0f,0f,0.5f,0f,0.3125f,0f,0f,1f,0.25f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-524540180,962787657,1882450461,1282697341],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1NDExMTUwOSwKICAicHJvZmlsZUlkIiA6ICI1OTgyOWY1ZGY3MmM0ZmFlOTBmOGVhYmM0MjFjMzJkYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJQZXBwZXJEcmlua2VyIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2FhMzg4NjYxNGZkYjM5ZDFkYTMwYzkzZWI2ZWYzYjVjNWQ0YWU4NDU0ZTcwZTQ0NjRlODRhODVjNjQ2MmMzNzIiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="}]}}},item_display:"none",transformation:[0.5f,0f,0f,0f,0f,0.25f,0f,0.0625f,0f,0f,1f,0.25f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;625961686,-711299987,-1937873486,1815209253],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1NDA3MjM3NywKICAicHJvZmlsZUlkIiA6ICJhNDAxZjEzMTZlMjI0ZTNjOTg0ODk1MmVjMzhjMTEwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJHcmVlbnNoZWVwaXJhdGUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzU3ZWZiZDA5N2U5ZTAwNThmMzVmZTQ4MzNmZDhhYjY4YmU3MzgzZGQ5OThmOTcxMjBlZGViYTUyYTFlYzc3YSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[0.5f,0f,0f,0f,0f,0.5f,0f,0.3125f,0f,0f,1f,-0.25f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;463373991,1718805366,1766756748,-968392665],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTczNTU1NDExMTUwOSwKICAicHJvZmlsZUlkIiA6ICI1OTgyOWY1ZGY3MmM0ZmFlOTBmOGVhYmM0MjFjMzJkYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJQZXBwZXJEcmlua2VyIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2FhMzg4NjYxNGZkYjM5ZDFkYTMwYzkzZWI2ZWYzYjVjNWQ0YWU4NDU0ZTcwZTQ0NjRlODRhODVjNjQ2MmMzNzIiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="}]}}},item_display:"none",transformation:[0.5f,0f,0f,0f,0f,0.25f,0f,0.0625f,0f,0f,1f,-0.25f,0f,0f,0f,1f]}]}
        """;

    private static final String CATFISH_COMMAND = """
        summon block_display %x% %y% %z% {Tags:["fr_catfish"],Passengers:[{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1900185779,-834438409,1509801468,-2064968421],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTEzMjMxMCwKICAicHJvZmlsZUlkIiA6ICI3NjFjOTZhOTgzNzc0ODUwODc4ZjgwYWY4MzIyMzgxZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJzcGlmZnRvcGlhOCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8xODNhNjQyNThmNzMzM2FhZjUyNjliMmQzMjZlNzM1ODRlNDZjYTljZmZiYmEyNGU0ZjdkMjgwOWU0MTM3ZGMxIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0="}]}}},item_display:"none",transformation:[5000f,-0.8157959304f,0.0001290478f,-1327.9591946946f,8660.2540378444f,0.471f,0.0002235174f,-2299.8361971008f,-2.42e-8f,0f,0.936f,0.0608324893f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-268516548,922146281,-1519854402,-415892936],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTE3Mjk5NSwKICAicHJvZmlsZUlkIiA6ICIwZWQ2MDFlMDhjZTM0YjRkYWUxZmI4MDljZmEwNTM5NiIsCiAgInByb2ZpbGVOYW1lIiA6ICJOZWVkTW9yZUFjY291bnRzIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2MyNjZiNWYzOTdjZmRjYWViZTFmMzg0MmVlYmMzZjBiYWM4ZjlhYTM0ODIzYTQyMTA0M2M5ODU1ZmYzOGYzNDciLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="}]}}},item_display:"none",transformation:[-5000f,-0.8157959304f,-0.0001290478f,1327.5564248808f,8660.2540378444f,-0.471f,0.0002235174f,-2300.0713271041f,-2.42e-8f,0f,0.936f,0.0608324893f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;184899512,1272962657,-543846898,1425933377],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTE5NDU1MywKICAicHJvZmlsZUlkIiA6ICI3ZGEyYWIzYTkzY2E0OGVlODMwNDhhZmMzYjgwZTY4ZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJHb2xkYXBmZWwiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDVkYzc1OGUyM2M2MTQ5ZGM2ODUyOWUyZTIyMWYwODBiYTViYjRhNjYwNWQ2ZjBjZThiOGJjMTFhM2UzMTlkZSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[7745.1905283932f,-0.4549510642f,-0.3821501555f,-2056.9514535681f,5245.1905283629f,0.1219037702f,0.7877853206f,-1392.6838028209f,-3535.5339059413f,-0.8157959304f,0.330834723f,938.1065183515f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;420859257,282210290,1006861227,-1544058682],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTIwMDI5NSwKICAicHJvZmlsZUlkIiA6ICIzNmU5MTE1YzBjYzc0ZjhkOTdmOGFjNjA1ZGMxNGVkYSIsCiAgInByb2ZpbGVOYW1lIiA6ICJEYXJnaVYiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzU4YjBiNmIyYzc0MjYzODk2NWE0YWFhZjc3ZTAwMDZlNmU4ZjYzZDhhN2QwNDEwMjRjNGYzMzFiYWE2YjRkNSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[-5419.6663261652f,-0.6702483581f,0.4184695415f,1438.914959232f,5245.1905283629f,0.1219037702f,0.7877853206f,-1392.6838028209f,-6566.2160514411f,0.6505925063f,0.2835023776f,1743.4858670412f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;151879127,1379916297,-760513170,605242597],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTIwNTYyMiwKICAicHJvZmlsZUlkIiA6ICI3YjE3NjBiYmVkNTk0Mjc1YmU3ZjhjMGFlYmQzMmRiNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJTdmVuTmlqaHVpczQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmM0NzQ0ODg3NDRhOGFjYTBmNGM0MjU4OTg4NWY1ZWU3OTVmNTU3NTNlMTVhNjNjZjZkZjkwYWY4MDU5N2ZiZCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[2588.1904510192f,-0.8788979652f,0.2340668001f,-687.1973852912f,9659.2582628923f,0.2355f,-0.0624508097f,-2565.1423975099f,-2.33e-8f,0.2438075405f,0.9041065734f,-1.6658183509f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;196610419,-132369341,-1077064329,-1535063086],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTIxMDkwNSwKICAicHJvZmlsZUlkIiA6ICI5N2VmNDYyMzdhNGY0ZTQxYWY2ZTljYjg2MTdmNzc2OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJKaWw2NyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9lZjhlZTcwZDIzMDAwYWUyZWI0MWY5NzNlNDM5YjRlZTk3ZmFhZTQzN2UwMDU0MmI1ODc2Yzc0OTg3NDJmOTQ4IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0="}]}}},item_display:"none",transformation:[-2588.1904510192f,-0.8788979652f,-0.2340668001f,686.762120381f,9659.2582628923f,-0.2355f,-0.0624508097f,-2565.2599625115f,-2.33e-8f,-0.2438075405f,0.9041065734f,-1.7919401193f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-704916298,-1600005526,-326760950,-1100870313],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTI0NTc2OSwKICAicHJvZmlsZUlkIiA6ICIwNmY4YmNhNzc0NWY0OGIzYWVkMmMxMGY0MGMzN2VjYSIsCiAgInByb2ZpbGVOYW1lIiA6ICJEYXNoTmV0d29yayIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS85NzBiMjY4MTM5ZDk2OWUxYjVhZjA0OTZhYjhiMDgyZDViMzA0YmMyZDVjNWRlMTJhOWU1Y2EwYTc1NDY3ZWIyIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0="}]}}},item_display:"none",transformation:[-10000f,0f,-0.0002580957f,2656.2656195754f,2.42e-8f,0f,-0.936f,1.0641675107f,0f,-0.942f,0f,-0.1413800032f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-137118313,-1427250114,-262981519,-1515616542],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTI3NTM1NCwKICAicHJvZmlsZUlkIiA6ICJkODBlMGYyNjU2M2U0NzI3YWNiZDNlMmRlNDkxYzFiZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJMZXZfSXMiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWMyMGZjOGYyNWNmYzk0YTcwNTZmNjQ2ZjVlYWVlODJmNTI1OGEwMDFlMmFlNjY0YzkwMmEyNTMzOWJhY2YwZSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[-10000f,0f,-0.0002580957f,2656.2656195754f,2.42e-8f,0f,-0.936f,1.0641675107f,0f,-0.942f,0f,0.3586199968f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1177858016,1913277655,1181842865,-431469763],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTI4MDM3NywKICAicHJvZmlsZUlkIiA6ICI0MDU4NDhjMmJjNTE0ZDhkOThkOTJkMGIwYzhiZDQ0YiIsCiAgInByb2ZpbGVOYW1lIiA6ICJMaWFtX1NhZ2UiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2JmMThjZDk3YjkzYTFlN2FlMTY0MWNkMGM4MGYyYjQxOTU0NDY1NmY0MWEzNGJhNmFmNmY3NjI3ZWY4ZDM0NSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[-10000f,0f,-0.0002580957f,2656.2656195754f,2.42e-8f,0f,-0.936f,1.1197925107f,0f,-0.942f,0f,0.7336199968f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1956632353,1455928456,969040648,-1483731231],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTMxNDU2MCwKICAicHJvZmlsZUlkIiA6ICJkYTA1Y2Q3OWZkYjc0MDJlYTdjNjMzY2NkZmYzMDI4YyIsCiAgInByb2ZpbGVOYW1lIiA6ICJGZXJuYW5kMGFsMG5zMCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8zZTZhZDVlNTYyMDEyYTBiNDQxODA0Y2QzYjk0N2NkOGQ1MTc2MjE1Y2U3YWNjMzAzYzRkMjg0ZjgxYmMyOWYwIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0="}]}}},item_display:"none",transformation:[-10000f,0f,-0.0002580957f,2656.2656195754f,2.42e-8f,0f,-0.936f,1.1197925107f,0f,-0.942f,0f,1.2336199968f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;714262629,-1878591467,-710811679,-834874532],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTMyNjkwMywKICAicHJvZmlsZUlkIiA6ICJiMTM1MDRmMjMxOGI0OWNjYWFkZDcyYWVhYmMyNTQ1MCIsCiAgInByb2ZpbGVOYW1lIiA6ICJUeXBrZW4iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjkzNjhkNWFlOGEzYzY3ODkxMjcxN2FkODBkNzRhMDdhZTNkZWE1OGE2YmI5NDI3M2E4NjMzZWFkYzdiNmFmZCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[-10000f,0f,-0.0002580957f,2656.2656195754f,2.42e-8f,0f,-0.936f,0.6266675107f,0f,-0.942f,0f,0.7336199968f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-638315606,-820106135,48789055,-1991532343],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTM0NjAzNCwKICAicHJvZmlsZUlkIiA6ICIzYWE3NzRlYjRiNmY0MzlkODA1NDJiNWIzYjFmNzY5ZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGVSb3NlUXVlZW4xOTIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWY0YjBjMGVkMzEwOGQ5MjM3MzY1NjBmZjc2ZTAwOGRlMDZjZDY0ZDBjYWQyNDM4NTk4YjY3ZTBiNDIyZGVhOCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[-10000f,0f,-0.0002580957f,2656.2656195754f,2.42e-8f,0f,-0.936f,0.6266675107f,0f,-0.942f,0f,1.2336199968f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-987040357,-525644434,-766920662,-1725248231],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTM4MjM2OSwKICAicHJvZmlsZUlkIiA6ICIzNTBjN2IzODhiMzk0MWRiODZmMjVlYWUwYzM5N2VkZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJOMF9TTDMzUCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9iODIyZDcwMzcwOGY3NDAxOTI1MzNhM2QwZWMyMmQwOTM4ODliYmY0YTJjYTRkNzAzODFmM2I2ODUzZjI2NiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[-10000f,0f,-0.0002580957f,2656.2656195754f,2.42e-8f,0f,-0.936f,0.1316675107f,0f,-0.942f,0f,1.2336199968f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1774931047,-54472945,-222503150,-827343948],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTM4ODU5NSwKICAicHJvZmlsZUlkIiA6ICIwNDg0N2ZjNWM5YjY0NTQ1YjI1ZWJkYmJiNzdjNjg2NSIsCiAgInByb2ZpbGVOYW1lIiA6ICJOYXFsdWEiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTA2ZWQ5NmIyYmQ2ZGJhZjIxZDk5MDVhMGYxMmNmNTUxMGVmOTc4NTU2NGFlZTA4MDNlMjQzM2ZhMmQ0OGE1YyIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[-10000f,0f,-0.0002580957f,2656.2656195754f,2.42e-8f,0f,-0.936f,0.1316675107f,0f,-0.942f,0f,0.7336199968f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;1592578270,466031109,-1397420157,247113869],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTM5NDg3NywKICAicHJvZmlsZUlkIiA6ICJjNGRiNjkxZDNkYmU0ZWI5YThiNjgzM2I4ODNkYmM0NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJmb3NzaW5hbHQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2UxN2Y5OWYyN2RkM2QzNzEwYmUyZGIzYmVmMDg0ZDY2ZTRmNjBhODg2MmM4NTUwNWFiN2M0ZjliNGE1NDk1YiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[1f,0f,0f,-0.125f,0f,0.5f,0f,0.6875f,0f,0f,1f,-1.28125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-2111659648,1507192620,398360919,-723393106],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTQwMTQ3MiwKICAicHJvZmlsZUlkIiA6ICJhYWMyZWNmMzJiNDI0NzE0YTQ5OWUzNTY0NDAwNjBjMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJGb3hnaG9zdFgiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDVhZGU3NGY0OGFlMjBhMDkyMWZhNWIzNzc0MzAyNjc4NGJjOWNjMGIwNDVjNTUyOTEwYzFlODRjZTFlM2ZiIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0="}]}}},item_display:"none",transformation:[1f,0f,0f,-0.125f,0f,0.25f,0f,0.8125f,0f,0f,1f,-1.28125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1485936882,357903646,1186767378,569350119],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTQwNjYwMywKICAicHJvZmlsZUlkIiA6ICI1MzgyNzM1OGIzOTc0ZmJiOTg0OTY5MWM5Yzg3NTA1YiIsCiAgInByb2ZpbGVOYW1lIiA6ICJPdmVyQmlnYm95MTIzIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzRmOWMxNDEwZTU3MjIwNzY1NTk2ZmExODM2ZmY1Y2M0MzI4ZDFjZTBhYTU3NmRiZDc1YWVjYjQ0YWJkMWRhYWYiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="}]}}},item_display:"none",transformation:[1f,0f,0f,-0.125f,0f,0.125f,0f,0.875f,0f,0f,1f,-1.28125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;155681894,-1425968296,1439271117,-982585391],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTQxNDk2MSwKICAicHJvZmlsZUlkIiA6ICJiMWQ4MTJlYzI4YTU0NDNhOTUxODhmNDkyZjVjYzIyMSIsCiAgInByb2ZpbGVOYW1lIiA6ICJzcGlmZnRvcGlhIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzE1MWQxZWJhYTljNDQ2MjlkZWI0OTU0NjVkZDBhZDA3NTI2OTExOTU3YTc3OTk2ZWMwZTlkZDFjMzdmZjE3N2EiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="}]}}},item_display:"none",transformation:[1f,0f,0f,-0.125f,0f,0.5f,0f,0.6875f,0f,0f,1f,-0.78125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;1612417535,-1865033782,-1313425828,-1693533159],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTQzNDQyNCwKICAicHJvZmlsZUlkIiA6ICI4MDQ2MzdjMTA1ZGY0MzM0ODE3YTNmMDcxMTMyOTYyMSIsCiAgInByb2ZpbGVOYW1lIiA6ICJQcmluY2VDUiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS84ODc2Yzc0Nzc3MDg2NDZjODVjOTJiOGYyYTI2N2Y2NjE1OTE2ZWViYzUyMWQzODE0Y2VlMzc2MTgzMTc1YjY5IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0="}]}}},item_display:"none",transformation:[0.5f,0f,0f,0.25f,0f,0.5f,0f,0.6875f,0f,0f,1f,-0.78125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;410856863,1252430859,1125626789,2120467742],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTQ4MjY2NywKICAicHJvZmlsZUlkIiA6ICI3MzE4MWQxZDRjYWQ0ZmU0YTcxNWNjNmUxOGNjYzVkNyIsCiAgInByb2ZpbGVOYW1lIiA6ICJaZmVybjRuZGl0byIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmYzNDRmYzE4MzZhYWIwYjFlOTJmNjIzYjdmYjU5Nzk5YTE0YWJmYzBmODVmZWJiOGE5YmE5ZmE0NDM0YzlhNyIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[1f,0f,0f,-0.125f,0f,0.25f,0f,0.8125f,0f,0f,1f,-0.78125f,0f,0f,0f,1f]},{id:"minecraft:item_display",item:{id:"minecraft:player_head",Count:1,components:{"minecraft:profile":{id:[I;-1456942117,354065362,-635639273,831918719],properties:[{name:"textures",value:"ewogICJ0aW1lc3RhbXAiIDogMTc2OTI1OTQ4NzcyOCwKICAicHJvZmlsZUlkIiA6ICJhNWM5MmJlODg5MGY0NDU0OTdkNGEwOTM2Yjg1NDc5OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJDbG93ZGVyVGVjaCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWQ3MjNhZmRkNGQ0YmJiNDRkYjBhYTc0N2EzNGU4MWVkZmNmYjYzOGMzMDI5YTI0Njk3Zjc5YTUyZmEyMzNmMCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"}]}}},item_display:"none",transformation:[1f,0f,0f,-0.125f,0f,0.125f,0f,0.875f,0f,0f,1f,-0.78125f,0f,0f,0f,1f]}]}
        """;
}

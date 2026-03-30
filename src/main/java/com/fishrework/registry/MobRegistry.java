package com.fishrework.registry;

import com.fishrework.model.CustomMob;
import com.fishrework.model.Skill;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central registry for all custom mob definitions.
 * Adding a new mob = one register() call. No other code changes needed
 * for spawning, XP, drops, collection, or GUI display.
 */
public class MobRegistry {

    private final Map<String, CustomMob> mobs = new LinkedHashMap<>();

    public void register(CustomMob mob) {
        mobs.put(mob.getId().toLowerCase(), mob);
    }

    public CustomMob get(String id) {
        return mobs.get(id.toLowerCase());
    }

    public Collection<CustomMob> getAll() {
        return Collections.unmodifiableCollection(mobs.values());
    }

    public List<CustomMob> getBySkill(Skill skill) {
        return mobs.values().stream()
                .filter(m -> m.getSkill() == skill)
                .collect(Collectors.toList());
    }

    public List<CustomMob> getHostile(Skill skill) {
        return mobs.values().stream()
                .filter(m -> m.getSkill() == skill && m.isHostile())
                .collect(Collectors.toList());
    }

    public List<CustomMob> getPassive(Skill skill) {
        return mobs.values().stream()
                .filter(m -> m.getSkill() == skill && !m.isHostile())
                .collect(Collectors.toList());
    }

    public List<String> getAllIds() {
        return new ArrayList<>(mobs.keySet());
    }

    /**
     * Returns mobs sorted by rarity (lowest chance first = rarest first).
     * This is the order used for spawn-chance rolling.
     */
    public List<CustomMob> getSortedByRarity(Skill skill) {
        return mobs.values().stream()
                .filter(m -> m.getSkill() == skill)
                .sorted(Comparator.comparingDouble(CustomMob::getDefaultChance))
                .collect(Collectors.toList());
    }
}

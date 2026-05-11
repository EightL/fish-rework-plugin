package com.fishrework.registry;

import com.fishrework.model.CustomMob;
import com.fishrework.model.Skill;

import java.util.*;

/**
 * Central registry for all custom mob definitions.
 * Adding a new mob = one register() call. No other code changes needed
 * for spawning, XP, drops, collection, or GUI display.
 */
public class MobRegistry {

    private final Map<String, CustomMob> mobs = new LinkedHashMap<>();
    private final Map<Skill, List<CustomMob>> mobsBySkill = new EnumMap<>(Skill.class);
    private final Map<Skill, List<CustomMob>> hostileBySkill = new EnumMap<>(Skill.class);
    private final Map<Skill, List<CustomMob>> passiveBySkill = new EnumMap<>(Skill.class);
    private final Map<Skill, List<CustomMob>> sortedBySkill = new EnumMap<>(Skill.class);

    public void register(CustomMob mob) {
        mobs.put(mob.getId().toLowerCase(), mob);
        Skill skill = mob.getSkill();
        mobsBySkill.computeIfAbsent(skill, ignored -> new ArrayList<>()).add(mob);
        if (mob.isHostile()) {
            hostileBySkill.computeIfAbsent(skill, ignored -> new ArrayList<>()).add(mob);
        } else {
            passiveBySkill.computeIfAbsent(skill, ignored -> new ArrayList<>()).add(mob);
        }
        rebuildSortedList(skill);
    }

    public CustomMob get(String id) {
        return mobs.get(id.toLowerCase());
    }

    public Collection<CustomMob> getAll() {
        return Collections.unmodifiableCollection(mobs.values());
    }

    public List<CustomMob> getBySkill(Skill skill) {
        List<CustomMob> list = mobsBySkill.get(skill);
        if (list == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(list);
    }

    public List<CustomMob> getHostile(Skill skill) {
        List<CustomMob> list = hostileBySkill.get(skill);
        if (list == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(list);
    }

    public List<CustomMob> getPassive(Skill skill) {
        List<CustomMob> list = passiveBySkill.get(skill);
        if (list == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(list);
    }

    public List<String> getAllIds() {
        return new ArrayList<>(mobs.keySet());
    }

    /**
     * Returns mobs sorted by rarity (lowest chance first = rarest first).
     * This is the order used for spawn-chance rolling.
     */
    public List<CustomMob> getSortedByRarity(Skill skill) {
        List<CustomMob> list = sortedBySkill.get(skill);
        if (list == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(list);
    }

    private void rebuildSortedList(Skill skill) {
        List<CustomMob> list = mobsBySkill.get(skill);
        if (list == null || list.isEmpty()) {
            sortedBySkill.remove(skill);
            return;
        }
        List<CustomMob> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparingDouble(CustomMob::getDefaultChance));
        sortedBySkill.put(skill, sorted);
    }
}

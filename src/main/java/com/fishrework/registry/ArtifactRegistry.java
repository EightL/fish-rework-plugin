package com.fishrework.registry;

import com.fishrework.model.Artifact;
import com.fishrework.model.Rarity;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry holding all artifact definitions.
 * Insertion-order preserved so GUI layout is predictable.
 */
public class ArtifactRegistry {

    private final Map<String, Artifact> artifacts = new LinkedHashMap<>();
    private final Map<Rarity, List<Artifact>> byRarity = new EnumMap<>(Rarity.class);

    public void register(Artifact artifact) {
        artifacts.put(artifact.getId(), artifact);
        byRarity.computeIfAbsent(artifact.getRarity(), k -> new ArrayList<>()).add(artifact);
    }

    public Artifact get(String id) {
        return artifacts.get(id);
    }

    public List<Artifact> getByRarity(Rarity rarity) {
        return byRarity.getOrDefault(rarity, Collections.emptyList());
    }

    public List<Artifact> getAll() {
        return new ArrayList<>(artifacts.values());
    }

    public List<String> getAllIds() {
        return new ArrayList<>(artifacts.keySet());
    }

    public int size() {
        return artifacts.size();
    }
}

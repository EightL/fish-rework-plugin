package com.fishrework.model;

import org.bukkit.Material;

/**
 * Immutable definition of a collectible artifact.
 * Artifacts are rare finds from treasure chests, tracked in a separate collection GUI.
 */
public class Artifact {

    private final String id;
    private final String displayName;
    private final String description;
    private final Rarity rarity;
    private final String textureBase64; // null for standard Material items
    private final Material material;    // fallback when textureBase64 is null
    private final java.util.List<ArtifactPassiveEffect> passiveEffects;

    public Artifact(String id, String displayName, String description, Rarity rarity, String textureBase64) {
        this(id, displayName, description, rarity, textureBase64, java.util.List.of());
    }

    public Artifact(String id, String displayName, String description, Rarity rarity,
                    String textureBase64, java.util.List<ArtifactPassiveEffect> passiveEffects) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.textureBase64 = textureBase64;
        this.material = Material.PLAYER_HEAD;
        this.passiveEffects = java.util.List.copyOf(passiveEffects);
    }

    public Artifact(String id, String displayName, String description, Rarity rarity, Material material) {
        this(id, displayName, description, rarity, material, java.util.List.of());
    }

    public Artifact(String id, String displayName, String description, Rarity rarity,
                    Material material, java.util.List<ArtifactPassiveEffect> passiveEffects) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.textureBase64 = null;
        this.material = material;
        this.passiveEffects = java.util.List.copyOf(passiveEffects);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Rarity getRarity() { return rarity; }
    public String getTextureBase64() { return textureBase64; }
    public Material getMaterial() { return material; }
    public java.util.List<ArtifactPassiveEffect> getPassiveEffects() { return passiveEffects; }
    public boolean isPlayerHead() { return textureBase64 != null; }
}

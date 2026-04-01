package com.fishrework.util;

import com.fishrework.FishRework;
import com.fishrework.model.ParticleDetailMode;
import com.fishrework.model.PlayerData;
import org.bukkit.entity.Player;

public final class ParticleDetailScaler {

    private ParticleDetailScaler() {
    }

    public static int getScaledCount(FishRework plugin, Player viewer, int baseCount) {
        if (plugin == null || viewer == null || baseCount <= 0) return 0;

        ParticleDetailMode mode = ParticleDetailMode.HIGH;
        PlayerData data = plugin.getPlayerData(viewer.getUniqueId());
        if (data != null && data.getParticleDetailMode() != null) {
            mode = data.getParticleDetailMode();
        }

        double globalScale = plugin.getConfig().getDouble("combat.ability_particles.global_scale", 1.0);
        globalScale = Math.max(0.0, Math.min(1.0, globalScale));

        int minimumPerSpawn = plugin.getConfig().getInt("combat.ability_particles.minimum_per_spawn", 1);
        minimumPerSpawn = Math.max(0, minimumPerSpawn);

        int scaled = (int) Math.round(baseCount * mode.getParticleScale() * globalScale);
        if (minimumPerSpawn > 0) {
            scaled = Math.max(minimumPerSpawn, scaled);
        }
        return Math.min(baseCount, Math.max(0, scaled));
    }
}
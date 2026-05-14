package com.fishrework.model;

import java.util.UUID;

public record CustomShop(
        String id,
        UUID ownerUuid,
        String ownerName,
        String worldName,
        int x,
        int y,
        int z,
        float yaw,
        String modelInstanceId
) {
}

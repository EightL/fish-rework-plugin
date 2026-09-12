package com.fishrework;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoreUpdateRegressionTest {

    @Test
    void inventoryRefreshDoesNotRewriteUnrelatedCustomLore() throws IOException {
        String loreManager = Files.readString(Path.of("src/main/java/com/fishrework/manager/LoreManager.java"));
        String loreUpdateListener = Files.readString(Path.of("src/main/java/com/fishrework/listener/LoreUpdateListener.java"));

        assertTrue(loreManager.contains("if (!managesLore(item)) return;"),
                "Lore updates must ignore items not managed by FishRework");
        assertTrue(loreUpdateListener.contains("if (plugin.getLoreManager().managesLore(current))"),
                "Inventory close must not write unrelated equipped items back to the player");
    }
}

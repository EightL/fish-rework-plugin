package com.fishrework;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SulfurCubeContentTest {

    @Test
    void mobDefinitionHasRequestedBalanceAndExistingDrop() throws IOException {
        String mobs = Files.readString(Path.of("src/main/resources/mobs.yml"));
        int start = mobs.indexOf("  sulfur_cube:");
        int end = mobs.indexOf("\n  elder_guardian:", start);
        String definition = mobs.substring(start, end);

        assertTrue(definition.contains("required_level: 11"));
        assertTrue(definition.contains("health: 42.0"));
        assertTrue(definition.contains("item: slimy_chunk"));
        assertFalse(definition.contains("vanilla:"), "The creature should reward an existing custom drop");
    }

    @Test
    void controllerDefinesAllTwelveVanillaArchetypesWithoutHardApiLink() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/fishrework/manager/SulfurCubeController.java"));
        String enumBody = source.substring(source.indexOf("private enum Variant"));

        long variants = enumBody.lines()
                .filter(line -> line.matches("\\s{8}[A-Z_]+\\(Material\\..*"))
                .count();
        assertEquals(12, variants);
        assertFalse(source.contains("import org.bukkit.entity.SulfurCube"));
        assertTrue(source.contains("\"SULFUR_CUBE\".equals(entity.getType().name())"));
    }
}

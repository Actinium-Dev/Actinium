package com.dhj.actinium.config;

import dhj.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SodiumGameOptionsRecoveryTest {
    @Test
    void malformedConfigIsProtectedUntilRepairedAndReloaded() throws IOException {
        Path directory = Files.createDirectories(Path.of("config"));
        Path file = Files.createTempFile(directory, "actinium-recovery-", ".json");
        String name = file.getFileName().toString();
        try {
            Files.writeString(file, "{\"enable_debug_tab\":true,\r\n");
            byte[] original = Files.readAllBytes(file);

            SodiumGameOptions fallback = SodiumGameOptions.load(name);
            assertArrayEquals(original, Files.readAllBytes(file));
            assertEquals(SodiumGameOptions.defaults().enableDebugTab, fallback.enableDebugTab);
            assertTrue(fallback.isReadOnly());
            assertThrows(IllegalStateException.class, () -> SodiumGameOptions.writeToDisk(fallback));
            assertThrows(IllegalStateException.class, fallback::save);
            assertArrayEquals(original, Files.readAllBytes(file));

            Files.writeString(file, "{\"enable_debug_tab\":true}");
            SodiumGameOptions repaired = SodiumGameOptions.load(name);
            assertFalse(repaired.isReadOnly());
            assertTrue(repaired.enableDebugTab);
            repaired.enableDebugTab = false;
            repaired.save();
            assertFalse(SodiumGameOptions.load(name).enableDebugTab);
        } finally {
            Files.delete(file);
        }
    }
}

package com.gtnewhorizons.angelica.lwjgl3;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class IndirectMultidrawTest {
    @Test
    void ignoresAnExportedBatchEntryWithoutAnAdvertisedCapability() {
        assertEquals(0L, IndirectMultidraw.resolve(false, 1L, () -> false,
            name -> fail("Unexpected symbol lookup")));
    }

    @Test
    void prefersTheAdvertisedCoreEntry() {
        long address = 1L;
        assertEquals(address, IndirectMultidraw.resolve(true, address, () -> fail("Unexpected extension query"),
            name -> fail("Unexpected symbol lookup")));
    }

    @Test
    void requiresTheBatchEntryForTheAdvertisedExtension() {
        assertEquals(2L, IndirectMultidraw.resolve(false, 1L, () -> true, name -> {
            assertEquals("glMultiDrawElementsIndirectEXT", MemoryUtil.memASCII(MemoryUtil.memAddress(name)));
            return 2L;
        }));
        assertEquals(0L, IndirectMultidraw.resolve(false, 1L, () -> true, name -> 0L));
    }
}

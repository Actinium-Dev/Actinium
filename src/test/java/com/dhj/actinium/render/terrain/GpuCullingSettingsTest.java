package com.dhj.actinium.render.terrain;

import dhj.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import dhj.embeddedt.embeddium.impl.render.chunk.MultiDrawMode;
import dhj.embeddedt.embeddium.impl.render.culling.GpuCulling;
import dhj.embeddedt.embeddium.impl.render.culling.GpuCullingMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuCullingSettingsTest {
    @AfterEach
    void restoreCpuOnlyMode() {
        GpuCulling.setMode(GpuCullingMode.CPU_ONLY);
    }

    @Test
    void computeSelectionEnablesTheComputePath() {
        SodiumGameOptions options = new SodiumGameOptions();
        options.performance.gpuCullingMode = GpuCullingMode.COMPUTE;

        GpuCullingSettings.apply(options);

        assertTrue(GpuCulling.mode().computeEnabled());
    }

    @Test
    void cpuOnlySelectionKeepsTheCpuPath() {
        SodiumGameOptions options = new SodiumGameOptions();
        options.performance.gpuCullingMode = GpuCullingMode.CPU_ONLY;

        GpuCullingSettings.apply(options);

        assertFalse(GpuCulling.mode().computeEnabled());
    }

    @ParameterizedTest
    @CsvSource({
            "false, false, INDIRECT,   COMPUTE,  DIRECT,     CPU_ONLY, true",
            "false, true,  INDIRECT,   COMPUTE,  DIRECT,     CPU_ONLY, true",
            "true,  false, INDIRECT,   COMPUTE,  INDIRECT,   CPU_ONLY, true",
            "false, false, INDIRECT,   CPU_ONLY, DIRECT,     CPU_ONLY, true",
            "false, false, INDIVIDUAL, COMPUTE,  INDIVIDUAL, CPU_ONLY, true",
            "false, false, INDIVIDUAL, CPU_ONLY, INDIVIDUAL, CPU_ONLY, false",
            "true,  true,  INDIVIDUAL, CPU_ONLY, INDIVIDUAL, CPU_ONLY, false",
            "true,  true,  INDIRECT,   COMPUTE,  INDIRECT,   COMPUTE,  false"
    })
    void startupAppliesHardwareLimitsOnlyOnce(boolean supportsIndirect, boolean supportsCompute,
                                            MultiDrawMode drawMode, GpuCullingMode cullingMode,
                                            MultiDrawMode expectedDraw, GpuCullingMode expectedCulling,
                                            boolean expectedChange) {
        SodiumGameOptions options = new SodiumGameOptions();
        options.advanced.multiDrawMode = drawMode;
        options.performance.gpuCullingMode = cullingMode;

        assertEquals(expectedChange, GpuCullingSettings.applyHardwareLimits(options, supportsIndirect, supportsCompute));
        assertEquals(expectedDraw, options.advanced.multiDrawMode);
        assertEquals(expectedCulling, options.performance.gpuCullingMode);

        assertFalse(GpuCullingSettings.applyHardwareLimits(options, supportsIndirect, supportsCompute));
        assertEquals(expectedDraw, options.advanced.multiDrawMode);
        assertEquals(expectedCulling, options.performance.gpuCullingMode);
    }
}

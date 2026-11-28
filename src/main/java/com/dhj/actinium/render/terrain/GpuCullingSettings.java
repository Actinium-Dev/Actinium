package com.dhj.actinium.render.terrain;

import dhj.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import dhj.embeddedt.embeddium.impl.render.chunk.MultiDrawMode;
import dhj.embeddedt.embeddium.impl.render.culling.GpuCulling;
import dhj.embeddedt.embeddium.impl.render.culling.GpuCullingMode;
import dhj.embeddedt.embeddium.impl.render.culling.MultiDrawModeResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Applies terrain settings after the client has a current graphics context.
 */
public final class GpuCullingSettings {
    private static final Logger LOGGER = LogManager.getLogger(GpuCullingSettings.class);

    private GpuCullingSettings() {
    }

    /** Replaces unsupported settings before the first terrain renderer or options screen uses them. */
    public static void initialize(SodiumGameOptions options) {
        boolean supportsIndirect = MultiDrawModeResolver.supportsMultiDrawIndirect();
        GpuCulling.Availability hardware = GpuCulling.hardwareAvailability();
        boolean changed = applyHardwareLimits(options, supportsIndirect, hardware == GpuCulling.Availability.AVAILABLE);
        apply(options);
        if (changed) {
            LOGGER.info("Adjusted terrain settings: multidraw={}, culling={} (indirect supported={}, compute capability={})",
                    options.advanced.multiDrawMode, options.performance.gpuCullingMode, supportsIndirect, hardware);
            if (!options.isReadOnly()) {
                try {
                    SodiumGameOptions.writeToDisk(options);
                } catch (IOException e) {
                    LOGGER.error("Could not save adjusted terrain settings", e);
                }
            }
        }
    }

    static boolean applyHardwareLimits(SodiumGameOptions options, boolean supportsIndirect, boolean supportsCompute) {
        MultiDrawMode draw = options.advanced.multiDrawMode;
        GpuCullingMode culling = options.performance.gpuCullingMode;
        if (draw == MultiDrawMode.INDIRECT) {
            options.advanced.multiDrawMode = MultiDrawModeResolver.defaultMode(supportsIndirect);
        }
        if (!supportsIndirect || !supportsCompute) {
            options.performance.gpuCullingMode = GpuCullingMode.CPU_ONLY;
        }
        return options.advanced.multiDrawMode != draw || options.performance.gpuCullingMode != culling;
    }

    /** Applies {@link SodiumGameOptions.PerformanceSettings#gpuCullingMode} to the active culler. */
    public static void apply(SodiumGameOptions options) {
        GpuCulling.setMode(options.performance.gpuCullingMode);
    }
}

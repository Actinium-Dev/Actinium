package com.gtnewhorizons.angelica.rendering.particles;

import net.minecraft.client.renderer.BufferBuilder;

/** Detects a shared tessellator submission made inside a custom particle renderer. */
public final class ParticleDrawBoundary {
    private static BufferBuilder watched;

    private ParticleDrawBoundary() {}

    static boolean isWatching() {
        return watched != null;
    }

    static void watch(BufferBuilder run) {
        watched = run;
    }

    static boolean endWatch() {
        boolean submitted = watched == null;
        watched = null;
        return submitted;
    }

    /** Flushes earlier particles once, before the renderer submits any of its own geometry. */
    public static void beforeDraw(BufferBuilder buffer) {
        if (watched == buffer) {
            watched = null;
            ParticleInstancer.beforeCustomDraw();
        }
    }
}

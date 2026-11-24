package com.gtnewhorizons.angelica.api.tesr;

import com.gtnewhorizons.angelica.rendering.tesr.GlowReplayExclusions;

/** Lets renderers with per-invocation side effects opt out of Fancy translucency's second render. */
public final class TesrGlowDepth {
    private TesrGlowDepth() {}

    /** Captures glow depth during the original draw for the given tile entity or renderer class and its subclasses. */
    public static void excludeFromReplay(Class<?> type) {
        GlowReplayExclusions.exclude(type);
    }
}

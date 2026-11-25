package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import org.lwjgl.opengl.GL11;

/** Rejects fragments that make no visible contribution to a depth capture. */
public final class CoverageDiscard {
    public static final int OFF = 0;
    private static final int ALPHA = 1;
    private static final int RGB = 2;
    private static final int RGB_ALPHA = 3;

    public static final String GLSL = """
        uniform int u_CoverMode;

        void coverageTest(vec4 color) {
            if (u_CoverMode == %d) return;
            float rgb = max(color.r, max(color.g, color.b));
            float coverage = color.a;
            if (u_CoverMode == %d) coverage = rgb;
            if (u_CoverMode == %d) coverage = rgb * color.a;
            if (coverage < 0.004) discard;
        }
        """.formatted(OFF, RGB, RGB_ALPHA);

    private CoverageDiscard() {}

    public static boolean active() {
        return GLSMConfig.depthPeel() || DepthEcho.discardsCoverage();
    }

    public static int drawMode() {
        return GLSMConfig.depthPeel() || DepthEcho.replaying() ? mode() : OFF;
    }

    public static int mode() {
        if (!GLSMBlend.getBlendMode().isEnabled()) return OFF;
        final var blend = GLSMBlend.getBlendState();
        if (blend.getDstRgb() != GL11.GL_ONE) return ALPHA;
        return blend.getSrcRgb() == GL11.GL_SRC_ALPHA ? RGB_ALPHA : RGB;
    }
}

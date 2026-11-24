package com.dhj.actinium.resourcepack.sky;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

enum SkyBlend {
    ALPHA(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, false, true),
    ADD(GL11.GL_SRC_ALPHA, GL11.GL_ONE, false, true),
    SUBTRACT(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ZERO, true, false),
    MULTIPLY(GL11.GL_DST_COLOR, GL11.GL_ONE_MINUS_SRC_ALPHA, true, true),
    DODGE(GL11.GL_ONE, GL11.GL_ONE, true, false),
    BURN(GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR, true, false),
    SCREEN(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_COLOR, true, false),
    OVERLAY(GL11.GL_DST_COLOR, GL11.GL_SRC_COLOR, true, false),
    REPLACE(GL11.GL_ONE, GL11.GL_ZERO, false, false);

    private final int source;
    private final int destination;
    private final boolean fadeRgb;
    private final boolean fadeAlpha;

    SkyBlend(int source, int destination, boolean fadeRgb, boolean fadeAlpha) {
        this.source = source;
        this.destination = destination;
        this.fadeRgb = fadeRgb;
        this.fadeAlpha = fadeAlpha;
    }

    static SkyBlend parse(String value) {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    void apply(float brightness) {
        if (this == REPLACE) {
            GLStateManager.disableBlend();
            GlStateManager.enableAlpha();
        } else {
            GlStateManager.disableAlpha();
            GLStateManager.enableBlend();
            GLStateManager.glBlendFuncSeparate(source, destination, GL11.GL_ONE, GL11.GL_ZERO);
        }
        float rgb = fadeRgb ? brightness : 1;
        GLStateManager.glColor4f(rgb, rgb, rgb, fadeAlpha ? brightness : 1);
    }
}

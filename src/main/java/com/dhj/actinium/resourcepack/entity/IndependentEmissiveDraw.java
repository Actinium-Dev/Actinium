package com.dhj.actinium.resourcepack.entity;

import com.dhj.actinium.render.VanillaBufferBuilderRenderer;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorEntityIds;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/** Replays submitted geometry while retaining the caller's framebuffer and Iris phase. */
public final class IndependentEmissiveDraw {
    private IndependentEmissiveDraw() {}

    public static boolean active() { return current() != null; }

    private static IndependentEmissive.Draw current() {
        IndependentEmissive.Draw draw = ResourcePackEntities.activeDraw();
        return draw != null && !ShadowRenderingState.areShadowsCurrentlyBeingRendered()
            && !DisplayListManager.isRecording() && GLSMTextures.getActiveTextureUnit() == 0
            && GLStateManager.glIsEnabled(GL11.GL_TEXTURE_2D)
            && GLSMTextures.getBoundTextureForServerState(0) == draw.baseId() ? draw : null;
    }

    public static void callList(int list) {
        var draw = current();
        if (draw != null) BatchStateGuard.beforeChange();
        GLSMDraws.glCallList(list);
        if (draw != null) overlay(draw, () -> GLSMDraws.glCallList(list));
    }

    public static boolean drawBuffer(BufferBuilder buffer) {
        var draw = current();
        if (draw == null || TessellatorManager.shouldInterceptBufferBuilderDraw() || buffer.getVertexCount() == 0
            || !buffer.getVertexFormat().hasUvOffset(0)
            || TessellatorEntityIds.runsFor(buffer, buffer.getVertexCount()) != 0) return false;
        BatchStateGuard.beforeChange();
        buffer.finishDrawing();
        try {
            var format = buffer.getVertexFormat();
            int vertices = buffer.getVertexCount();
            int mode = buffer.getDrawMode();
            int stride = format.getSize();
            ByteBuffer data = buffer.getByteBuffer().duplicate().order(buffer.getByteBuffer().order());
            data.position(0).limit(stride * vertices);
            VanillaBufferBuilderRenderer.drawRaw(data.duplicate(), format, vertices, mode, "Entity emissive base");
            overlay(draw, () -> {
                if (format.getElements().contains(DefaultVertexFormats.TEX_2S)) {
                    for (int i = 0, offset = format.getUvOffsetById(1); i < vertices; i++, offset += stride) {
                        data.putShort(offset, (short) 240).putShort(offset + Short.BYTES, (short) 240);
                    }
                }
                VanillaBufferBuilderRenderer.drawRaw(data, format, vertices, mode, "Entity emissive overlay");
            });
        } finally {
            buffer.reset();
        }
        return true;
    }

    private static void overlay(IndependentEmissive.Draw draw, Runnable geometry) {
        float x = OpenGlHelper.lastBrightnessX;
        float y = OpenGlHelper.lastBrightnessY;
        GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        try {
            int glow = draw.glowId();
            if (glow == 0) return;
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, glow);
            GLStateManager.glDisable(GL11.GL_LIGHTING);
            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GLStateManager.glDepthMask(false);
            GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            geometry.run();
        } finally {
            try {
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, x, y);
            } finally {
                GLStateManager.glPopAttrib();
            }
        }
    }
}

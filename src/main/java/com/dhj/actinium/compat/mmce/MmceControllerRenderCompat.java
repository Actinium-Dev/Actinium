package com.dhj.actinium.compat.mmce;

import com.cleanroommc.client.shader.ShaderManager;
import com.dhj.actinium.render.terrain.TileEntityGlStateGuard;
import com.dhj.actinium.shadows.ShadowRenderingState;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import github.kasuminova.mmce.client.renderer.MachineControllerRenderer;
import net.coderbot.iris.Iris;
import net.coderbot.iris.apiimpl.IrisApiV0Impl;
import net.coderbot.iris.layer.GbufferPrograms;
import org.lwjgl.opengl.GL11;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** Runs MMCE's controller model batch at the end of the main block entity pass. */
public final class MmceControllerRenderCompat {
    private static Runnable finishPass;
    private static boolean drawingShaderBatch;

    private MmceControllerRenderCompat() {}

    /** Registers the draw and cleanup callbacks with the renderer's bloom cleanup policy. */
    public static void register(Runnable draw, Runnable checkControllerState, BooleanSupplier cleanupAfterBloom) {
        finishPass = () -> {
            draw.run();
            if (!cleanupAfterBloom.getAsBoolean()
                    || !MachineControllerRenderer.shouldUseBloom()
                    || ShaderManager.isOptifineShaderPackLoaded()) {
                checkControllerState.run();
            }
        };
    }

    /** Draws the queued models before the caller disables the world lightmap. */
    public static void finishBlockEntityPass(int pass) {
        Runnable finish = finishPass;
        if (finish == null || pass != 0 || ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            return;
        }

        boolean iris = Iris.enabled;
        if (iris) {
            GbufferPrograms.beginBlockEntities();
        }
        try {
            TileEntityGlStateGuard.push();
            boolean previousDrawingShaderBatch = drawingShaderBatch;
            try {
                drawingShaderBatch = iris && IrisApiV0Impl.INSTANCE.isShaderPackInUse();
                finish.run();
            } finally {
                drawingShaderBatch = previousDrawingShaderBatch;
                TileEntityGlStateGuard.pop();
            }
        } finally {
            if (iris) {
                GbufferPrograms.endBlockEntities();
            }
        }
    }

    /** Supplies alpha state for MMCE groups that only disable depth writes in their own renderer. */
    public static BiConsumer<Enum<?>, Map<?, ?>> withShaderTranslucency(BiConsumer<Enum<?>, Map<?, ?>> drawGroup) {
        if (!drawingShaderBatch) {
            return drawGroup;
        }

        return (type, buffers) -> {
            if (!type.name().equals("TRANSPARENT") && !type.name().equals("BLOOM_TRANSPARENT")) {
                drawGroup.accept(type, buffers);
                return;
            }

            Boolean previousTranslucency = GbufferPrograms.beginTranslucencyDeclaration(true);
            try {
                GLSMAttribStack.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);
                try {
                    GLSMBlend.enableBlend();
                    GLSMBlend.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
                    // The controller glass uses alpha below the terrain cutout threshold.
                    GLSMDepthStencilAlpha.enableAlphaTest();
                    GLSMDepthStencilAlpha.glAlphaFunc(GL11.GL_GREATER, 0.0f);
                    drawGroup.accept(type, buffers);
                } finally {
                    GLSMAttribStack.glPopAttrib();
                }
            } finally {
                GbufferPrograms.endTranslucencyDeclaration(previousTranslucency);
            }
        };
    }
}

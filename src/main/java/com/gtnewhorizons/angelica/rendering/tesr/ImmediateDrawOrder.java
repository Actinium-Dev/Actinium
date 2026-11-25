package com.gtnewhorizons.angelica.rendering.tesr;

import com.dhj.actinium.render.RenderWorldRecursionGuard;
import com.dhj.actinium.runtime.ActiniumRuntime;
import com.gtnewhorizons.angelica.glsm.DepthEcho;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import dhj.embeddedt.embeddium.api.shader.buffer.BufferBuilderExtension;
import net.coderbot.iris.Iris;
import net.coderbot.iris.apiimpl.IrisApiV0Impl;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Preserves block entity submission order when a blended draw bypasses the batchers. */
public final class ImmediateDrawOrder {
    private static final Runnable FLUSH = ImmediateDrawOrder::flush;

    private ImmediateDrawOrder() {}

    /** Enables immediate draw ordering for the current block entity pass when sorting is active. */
    public static void beginPass() {
        if (!ActiniumRuntime.options().performance.translucencyMode.sorts()
            || (Iris.enabled && IrisApiV0Impl.INSTANCE.isShaderPackInUse())) return;
        BatchStateGuard.drawFlush = FLUSH;
    }

    /** Clears the draw callback when the block entity pass exits. */
    public static void endPass() {
        BatchStateGuard.drawFlush = null;
    }

    private static void flush() {
        if (RenderWorldRecursionGuard.isNested() || DepthEcho.replaying() || !GLSMBlend.getBlendMode().isEnabled()) return;

        final int matrixMode = GLSMMatrices.getMatrixMode().getMode();
        final int textureUnit = GLSMTextures.getActiveTextureUnit();
        try {
            // Pending TESRs can reuse the singleton builder, which may still hold the current draw.
            final BufferBuilderExtension buffer = (BufferBuilderExtension) Tessellator.getInstance().getBuffer();
            if (!buffer.actinium$isDrawing()) PendingTesrDraws.flush();

            final ModelPartBatcher models = ModelPartBatcher.INSTANCE;
            final TesrBatchRenderer tesrs = TesrBatchRenderer.INSTANCE;
            if (!models.hasQueuedGeometry() && !tesrs.hasQueuedGeometry()) return;

            GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
            TranslucentPeel.flush();
            models.flushForStateChange();
            tesrs.flushAndContinue();
        } finally {
            try {
                GLSMMatrices.glMatrixMode(matrixMode);
            } finally {
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + textureUnit);
            }
        }
    }
}

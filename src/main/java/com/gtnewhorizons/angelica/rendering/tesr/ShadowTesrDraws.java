package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.rendering.RenderFailures;
import net.minecraft.client.renderer.BufferBuilder;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.ArrayList;
import java.util.List;

/** Submission boundaries for one block entity in the shadow pass. */
public final class ShadowTesrDraws {
    private static BufferBuilder forgeBuilder;
    private static final List<ModelPartBatcher.BoundTexture> textures = new ArrayList<>();
    private static int flushDepth;

    private ShadowTesrDraws() {}

    /** Releases snapshots retained for the previous world's nesting depth. */
    public static void clear() {
        textures.clear();
    }

    /** Call before replacing the parent's ID and before releasing the current tile's ID. */
    public static void flushPending() {
        PendingTesrDraws.flush();
        final ModelPartBatcher models = ModelPartBatcher.INSTANCE;
        final TesrBatchRenderer tesrs = TesrBatchRenderer.INSTANCE;
        if (!models.hasQueuedShadowGeometry() && !tesrs.hasQueuedShadowGeometry()) return;

        final int mode = GLSMMatrices.getMatrixMode().getMode();
        final int unit = GLSMTextures.getActiveTextureUnit();
        if (flushDepth == textures.size()) textures.add(new ModelPartBatcher.BoundTexture());
        final ModelPartBatcher.BoundTexture texture = textures.get(flushDepth++);
        texture.capture();
        final long before = GLSMDraws.drawCalls;
        BatchStateGuard.suspend();
        try {
            GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
            if (models.hasQueuedShadowGeometry()) models.flushForStateChange();
            if (tesrs.hasQueuedShadowGeometry()) tesrs.flushAndContinue();
        } finally {
            try {
                GLSMMatrices.glMatrixMode(mode);
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                texture.apply();
            } finally {
                try {
                    BatchEligibility.onBatchFlushed(GLSMDraws.drawCalls - before);
                } finally {
                    BatchStateGuard.resume();
                    flushDepth--;
                }
            }
        }
    }

    /** Drops the current shadow segment after a failed tile. Its parent must have flushed before entry. */
    public static void discardPending() {
        BatchStateGuard.suspend();
        try {
            Throwable failure = null;
            try {
                ModelPartBatcher.INSTANCE.discardShadowSegment();
            } catch (Throwable t) {
                failure = t;
            }
            try {
                TesrBatchRenderer.INSTANCE.discardShadowSegment();
            } catch (Throwable t) {
                failure = RenderFailures.suppress(failure, t);
            }
            RenderFailures.rethrow(failure);
        } finally {
            BatchStateGuard.resume();
        }
    }

    /** Restricts direct upload to this builder during the caller's Forge drawBatch(0). */
    public static void drawForgeBatch(BufferBuilder builder, Runnable drawBatch) {
        final BufferBuilder parent = forgeBuilder;
        forgeBuilder = builder;
        try {
            drawBatch.run();
        } finally {
            forgeBuilder = parent;
        }
    }

    public static boolean isForgeBatch(BufferBuilder builder) {
        return forgeBuilder != null && forgeBuilder == builder;
    }
}

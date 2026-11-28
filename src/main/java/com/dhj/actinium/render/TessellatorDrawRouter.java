package com.dhj.actinium.render;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.client.rendering.DeferredDrawBatcher;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.ITessellatorData;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.rendering.particles.ParticleDrawBoundary;
import com.gtnewhorizons.angelica.rendering.tesr.FlattenedItemLighting;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import com.gtnewhorizons.angelica.rendering.tesr.ShadowTesrDraws;
import net.minecraft.client.renderer.BufferBuilder;

/**
 * Routes one {@code Tessellator.draw()} call to the path that owns the vertices it holds.
 *
 * <p>The routes are exclusive: whichever one takes the draw also resets the builder, so the caller
 * must skip vanilla's upload.</p>
 *
 * A batch must receive an open builder because it consumes the vertices itself.
 */
public final class TessellatorDrawRouter {

    private TessellatorDrawRouter() {
    }

    /**
     * @param view the same builder as the shared eight-int vertex view the batch reads
     * @return whether the draw was handled here; {@code false} means vanilla's own path has to run
     */
    public static boolean draw(BufferBuilder buffer, ITessellatorData view) {
        ParticleDrawBoundary.beforeDraw(buffer);
        if (ShadowTesrDraws.isForgeBatch(buffer) && !DisplayListManager.isRecording()) {
            return upload(buffer);
        }
        if (VanillaBufferBuilderRenderer.shouldUseVanillaPositionDraw(buffer)) {
            if (buffer.getVertexCount() > 0) BatchStateGuard.beforeDraw();
            return false;
        }
        if (DeferredDrawBatcher.capture(buffer)) {
            return true;
        }
        if (TessellatorManager.shouldInterceptBufferBuilderDraw()) {
            buffer.finishDrawing();
            TessellatorManager.interceptBufferBuilderDraw(buffer);
            return true;
        }
        if (FlattenedItemLighting.bake(buffer) == FlattenedItemLighting.BakeResult.FIXED_FUNCTION) {
            BatchStateGuard.beforeDraw();
            GLSMLighting.disableLighting();
            try {
                buffer.finishDrawing();
                VanillaBufferBuilderRenderer.draw(buffer, "Tessellator");
            } finally {
                GLSMLighting.enableLighting();
            }
            return true;
        }
        if (ModelPartBatcher.isBatchingActive()) {
            ModelPartBatcher.drawTessellator(view);
            return true;
        }
        return upload(buffer);
    }

    private static boolean upload(BufferBuilder buffer) {
        if (buffer.getVertexCount() > 0) BatchStateGuard.beforeDraw();
        buffer.finishDrawing();
        VanillaBufferBuilderRenderer.draw(buffer, "Tessellator");
        return true;
    }
}

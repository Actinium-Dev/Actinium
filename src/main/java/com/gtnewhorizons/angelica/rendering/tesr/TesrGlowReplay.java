package com.gtnewhorizons.angelica.rendering.tesr;

import com.dhj.actinium.render.translucency.TranslucencyLayers;
import com.gtnewhorizons.angelica.glsm.DepthEcho;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

/** Tracks immediate draws during the block-entity layer and replays renderers that contributed glow depth. */
public final class TesrGlowReplay {
    private static final ObjectArrayList<Entry> entries = new ObjectArrayList<>();
    private static boolean collecting;
    private static boolean trackingRenderer;

    private TesrGlowReplay() {}

    public static void renderLayer(Runnable draw) {
        if (!DepthEcho.armed()) {
            draw.run();
            return;
        }
        try {
            collecting = true;
            try {
                draw.run();
            } finally {
                collecting = false;
            }
            if (entries.isEmpty() || !TranslucencyLayers.INSTANCE.beginGlowReplay()) return;
            try {
                for (Entry entry : entries) entry.replay();
            } finally {
                TranslucencyLayers.INSTANCE.endGlowReplay();
            }
        } finally {
            entries.clear();
        }
    }

    static void track(TileEntitySpecialRenderer<?> renderer, TileEntity tile, double x, double y, double z,
                      float partialTicks, int destroyStage, float alpha, Runnable draw) {
        if (!collecting || !DepthEcho.armed()) {
            draw.run();
            return;
        }
        if (GlowReplayExclusions.excludes(tile, renderer)) {
            drawImmediate(draw);
            return;
        }
        if (trackingRenderer) {
            draw.run();
            return;
        }

        final TesrGlowReplayState state = TesrGlowReplayState.capture();
        trackingRenderer = true;
        DepthEcho.beginTracking();
        boolean hit;
        try {
            draw.run();
        } finally {
            hit = DepthEcho.endTracking();
            trackingRenderer = false;
        }
        if (hit) entries.add(new Entry(renderer, tile, x, y, z, partialTicks, destroyStage, alpha, state));
    }

    /** A renderer can invoke another dispatcher during replay. Keep its batching statistics untouched. */
    static boolean renderNested(TileEntitySpecialRenderer<?> renderer, TileEntity tile, double x, double y, double z,
                                Runnable draw) {
        if (!DepthEcho.replaying()) return false;
        if (!GlowReplayExclusions.excludes(tile, renderer)) renderDirect(renderer, tile, x, y, z, draw);
        return true;
    }

    /** Submits geometry that has already been assembled, without scheduling another renderer invocation. */
    public static void drawImmediate(Runnable draw) {
        if (!DepthEcho.armed()) {
            draw.run();
            return;
        }
        DepthEcho.beginImmediate();
        try {
            draw.run();
        } finally {
            DepthEcho.endImmediate();
        }
    }

    private static void renderDirect(TileEntitySpecialRenderer<?> renderer, TileEntity tile, double x, double y,
                                     double z, Runnable draw) {
        TesrBatchRenderer.INSTANCE.beginScope();
        try {
            if (!TesrProviderDispatch.tryRender(renderer, tile, x, y, z)) draw.run();
        } finally {
            TesrBatchRenderer.INSTANCE.endScope();
        }
    }

    private record Entry(TileEntitySpecialRenderer<?> renderer, TileEntity tile, double x, double y, double z,
                         float partialTicks, int destroyStage, float alpha, TesrGlowReplayState state) {
        void replay() {
            if (tile.isInvalid()) return;
            state.run(() -> {
                renderDirect(renderer, tile, x, y, z, this::render);
                PendingTesrDraws.flush();
            });
        }

        @SuppressWarnings("unchecked")
        private void render() {
            // The dispatcher paired this renderer with the same tile entity during capture.
            ((TileEntitySpecialRenderer<TileEntity>) renderer).render(tile, x, y, z, partialTicks, destroyStage, alpha);
        }
    }
}

package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.rendering.RenderFailures;
import dhj.embeddedt.embeddium.api.shader.buffer.BufferBuilderExtension;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Gives each shadow FastTESR its own Forge batch while independent models use the shared tessellator. */
public final class ShadowFastTesr {
    private static final List<Frame> frames = new ArrayList<>();
    private static int dispatchDepth;
    private static int batchDepth;

    private ShadowFastTesr() {}

    /** Limits fast dispatch to ShadowRenderer's tile calls and their nested renderers. */
    public static void dispatch(Runnable draw) {
        dispatchDepth++;
        try {
            draw.run();
        } finally {
            dispatchDepth--;
        }
    }

    public static boolean active() {
        return dispatchDepth > 0;
    }

    /** Keeps shadow submissions inside the tile's NBT-aware ID scope. */
    public static void withBlockEntity(TileEntity tile, Runnable draw) {
        final CapturedRenderingState ids = CapturedRenderingState.INSTANCE;
        if (!active()) {
            ids.setCurrentBlockEntity(blockEntityId(tile));
            try {
                draw.run();
            } finally {
                ids.setCurrentBlockEntity(0);
            }
            return;
        }

        ShadowTesrDraws.flushPending();
        PendingTesrDraws.beforeTileEntity(tile);
        ids.pushCurrentBlockEntity();
        Throwable failure = null;
        try {
            ids.setCurrentBlockEntity(blockEntityId(tile));
            draw.run();
            ShadowTesrDraws.flushPending();
        } catch (Throwable t) {
            failure = t;
            try {
                ShadowTesrDraws.discardPending();
            } catch (Throwable cleanup) {
                failure = RenderFailures.suppress(failure, cleanup);
            }
        } finally {
            try {
                ids.popCurrentBlockEntity();
            } catch (Throwable cleanup) {
                failure = RenderFailures.suppress(failure, cleanup);
            }
        }
        RenderFailures.rethrow(failure);
    }

    /** Runs inside Forge's crash-report bracket after it has selected the fast renderer. */
    public static void render(TileEntityRendererDispatcher dispatcher, TileEntitySpecialRenderer<TileEntity> renderer,
                              TileEntity tile, double x, double y, double z, Consumer<BufferBuilder> draw) {
        if (batchDepth == frames.size()) frames.add(new Frame());
        final Frame frame = frames.get(batchDepth++);
        final Tessellator parentBuffer = dispatcher.batchBuffer;
        final boolean parentDrawing = dispatcher.drawingBatch;
        final BufferBuilder buffer = frame.tessellator.getBuffer();
        final int blockEntityId = CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
        final Class<?> parentRenderable = TesrAttribution.currentRenderable;
        Throwable failure = null;
        boolean captured = false;
        boolean scoped = false;
        try {
            frame.state.capture();
            captured = true;
            dispatcher.batchBuffer = frame.tessellator;
            dispatcher.drawingBatch = false;
            buffer.setTranslation(0, 0, 0);
            dispatcher.preDrawBatch();
            TesrBatchRenderer.INSTANCE.beginScope();
            scoped = true;
            TesrAttribution.currentRenderable = tile.getClass();
            TesrGlowReplay.drawImmediate(() -> {
                if (!TesrProviderDispatch.tryRender(renderer, tile, x, y, z)) draw.accept(buffer);
            });
            ShadowTesrDraws.flushPending();
            if (buffer.getVertexCount() > 0) {
                frame.state.restoreForAtlas();
                CapturedRenderingState.INSTANCE.setCurrentBlockEntity(blockEntityId);
                buffer.setTranslation(0, 0, 0);
                ShadowTesrDraws.drawForgeBatch(buffer, () -> dispatcher.drawBatch(0));
            }
        } catch (Throwable t) {
            failure = t;
            try {
                ShadowTesrDraws.discardPending();
            } catch (Throwable cleanup) {
                failure = RenderFailures.suppress(failure, cleanup);
            }
        } finally {
            try {
                ((BufferBuilderExtension) buffer).actinium$discard();
                buffer.setTranslation(0, 0, 0);
            } catch (Throwable cleanup) {
                failure = RenderFailures.suppress(failure, cleanup);
            }
            dispatcher.batchBuffer = parentBuffer;
            dispatcher.drawingBatch = parentDrawing;
            TesrAttribution.currentRenderable = parentRenderable;
            if (scoped) TesrBatchRenderer.INSTANCE.endScope();
            try {
                if (captured) frame.state.restore();
            } catch (Throwable cleanup) {
                failure = RenderFailures.suppress(failure, cleanup);
            } finally {
                batchDepth--;
            }
        }
        RenderFailures.rethrow(failure);
    }

    /** Releases the buffers retained for the previous world's maximum nesting depth. */
    public static void clear() {
        frames.clear();
    }

    private static int blockEntityId(TileEntity tile) {
        if (tile == null) return 0;
        final Block block = tile.getBlockType();
        if (block == null) return 0;
        final int metadata = tile.getBlockMetadata();
        final BlockRenderingSettings settings = BlockRenderingSettings.INSTANCE;
        final int nbtId = settings.resolveBlockNbtId(block, metadata, tile);
        if (nbtId != -1) return Math.max(0, nbtId);
        final Reference2ObjectMap<Block, Int2IntMap> matches = settings.getBlockMetaMatches();
        final Int2IntMap ids = matches == null ? null : matches.get(block);
        return ids == null ? 0 : Math.max(0, ids.get(metadata));
    }

    private static final class Frame {
        private final Tessellator tessellator = new Tessellator(8192);
        private final ShadowFastTesrState state = new ShadowFastTesrState();
    }
}

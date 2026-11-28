package com.dhj.actinium.test.shadow;

import com.gtnewhorizons.angelica.api.tesr.TesrMeshProvider;
import com.gtnewhorizons.angelica.api.tesr.TesrMeshSink;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.tesr.PendingTesrDraws;
import com.gtnewhorizons.angelica.rendering.tesr.ShadowFastTesr;
import com.gtnewhorizons.angelica.rendering.tesr.ShadowTesrDraws;
import com.gtnewhorizons.angelica.rendering.tesr.ShadowFastTesrScopeAssertions;
import dhj.embeddedt.embeddium.api.shader.buffer.BufferBuilderExtension;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.block_rendering.NbtConditionalIdMap;
import net.coderbot.iris.shaderpack.materialmap.PropertiesTokenizer;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ReportedException;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against loader-transformed Minecraft classes in an isolated, windowless VM. */
public final class ShadowFastTesrProbe {
    private static final int META_ID = 31;
    private static final int NBT_ID = 71;
    private static final int CHILD_ID = 83;
    private static final int CALLER_ID = 109;
    private static final ResourceLocation MODEL_TEXTURE = new ResourceLocation("test", "model");
    private static final List<Upload> uploads = new ArrayList<>();
    private static final List<ResourceLocation> textures = new ArrayList<>();
    private static final List<String> events = new ArrayList<>();
    private static ResourceLocation texture;
    private static RuntimeException uploadFailure;
    private static TileEntityRendererDispatcher dispatcher;

    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        GLStateManager.getVaoManager().init(0);
        dispatcher = TileEntityRendererDispatcher.instance;
        dispatcher.renderEngine = new TextureManager(null) {
            @Override
            public void bindTexture(ResourceLocation location) {
                texture = location;
                textures.add(location);
            }
        };
        dispatcher.renderers.put(Tile.class, new Renderer());
        Iris.getPipelineManager();
        configureIds();
        String scenario = System.getProperty("actinium.shadowScenario");
        switch (scenario) {
            case "dispatch-scope" -> dispatchScope();
            case "main-batch" -> mainBatch();
            case "ordinary-shadow" -> ordinaryShadow();
            case "already-building" -> reproducesAlreadyBuilding();
            case "mixed-model" -> mixedModel();
            case "empty-batch" -> emptyBatch();
            case "callback-failure" -> failedBatch(false);
            case "upload-failure" -> failedBatch(true);
            case "nested-fast" -> nested(false, null);
            case "nested-ordinary" -> nested(true, null);
            case "nested-callback-failure" -> nested(false, "callback");
            case "nested-upload-failure" -> nested(false, "upload");
            case "existing-batch" -> existingBatch();
            case "nbt-ids" -> nbtIds();
            case "provider" -> provider();
            case "buffer-reuse" -> bufferReuse();
            case "pending-id" -> pendingId();
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        }
        assertFalse(ShadowFastTesr.active());
        assertFalse(dispatcher.drawingBatch);
        assertFalse(extension(Tessellator.getInstance().getBuffer()).actinium$isDrawing());
        ShadowFastTesrScopeAssertions.assertBalanced();
        Files.writeString(Path.of("shadow-fast-tesr.ok"), scenario);
    }

    private static void dispatchScope() {
        RuntimeException failure = new IllegalStateException("nested dispatch failure");
        assertFalse(ShadowFastTesr.active());
        ShadowFastTesr.dispatch(() -> {
            assertTrue(ShadowFastTesr.active());
            assertSame(failure, assertThrows(IllegalStateException.class, () -> ShadowFastTesr.dispatch(() -> {
                assertTrue(ShadowFastTesr.active());
                throw failure;
            })));
            assertTrue(ShadowFastTesr.active());
        });
        assertFalse(ShadowFastTesr.active());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> ShadowFastTesr.dispatch(() -> {
            throw failure;
        })));
        ShadowFastTesr.clear();
        assertFalse(ShadowFastTesr.active());
    }

    private static void mainBatch() {
        Tile tile = tile(true, buffer -> quad(buffer, 3));
        Tessellator original = dispatcher.batchBuffer;
        dispatcher.preDrawBatch();
        render(tile);
        assertEquals(1, tile.queries);
        assertEquals(0, tile.ordinary);
        assertEquals(1, tile.fast);
        assertSame(original.getBuffer(), tile.buffer);
        assertNotSame(Tessellator.getInstance().getBuffer(), tile.buffer);
        assertTrue(uploads.isEmpty());
        assertTrue(dispatcher.drawingBatch);
        dispatcher.drawBatch(0);
        assertEquals(List.of(TextureMap.LOCATION_BLOCKS_TEXTURE), textures);
        assertUpload(0, tile.buffer, DefaultVertexFormats.BLOCK, 4, 3, 0);
    }

    private static void ordinaryShadow() {
        Tile tile = tile(false, buffer -> {
            assertTrue(ShadowFastTesr.active());
            assertEquals(NBT_ID, id());
            model(9);
        });
        shadow(tile);
        assertEquals(1, tile.ordinary);
        assertEquals(0, tile.fast);
        assertUpload(0, Tessellator.getInstance().getBuffer(), DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL,
            4, 9, NBT_ID);
        assertEquals(1, uploads.size());
    }

    private static void reproducesAlreadyBuilding() {
        Tile tile = tile(true, buffer -> {
            quad(buffer, 3);
            model(9);
        });
        ReportedException failure = assertThrows(ReportedException.class, () -> render(tile));
        Throwable cause = failure.getCrashReport().getCrashCause();
        assertInstanceOf(IllegalStateException.class, cause);
        assertEquals("Already building!", cause.getMessage());
        assertEquals(1, tile.ordinary);
        assertEquals(1, tile.fast);
        assertTrue(uploads.isEmpty());
        extension(Tessellator.getInstance().getBuffer()).actinium$discard();
        shadow(tile);
        assertEquals(1, tile.ordinary);
        assertEquals(2, tile.fast);
        assertNotSame(Tessellator.getInstance().getBuffer(), tile.buffer);
        assertUpload(0, Tessellator.getInstance().getBuffer(), DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL,
            4, 9, NBT_ID);
        assertUpload(1, tile.buffer, DefaultVertexFormats.BLOCK, 4, 3, NBT_ID);
        assertEquals(List.of("model", "atlas"), events);
    }

    private static void mixedModel() {
        Tile tile = tile(true, buffer -> {
            assertNotSame(Tessellator.getInstance().getBuffer(), buffer);
            buffer.setTranslation(2, -3, 4);
            quad(buffer, 3);
            model(9);
            assertTrue(extension(buffer).actinium$isDrawing());
            assertEquals(4, buffer.getVertexCount());
        });
        Tessellator original = dispatcher.batchBuffer;
        shadow(tile);
        assertSame(original, dispatcher.batchBuffer);
        assertEquals(0, tile.ordinary);
        assertEquals(1, tile.fast);
        assertUpload(0, Tessellator.getInstance().getBuffer(), DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL,
            4, 9, NBT_ID);
        assertUpload(1, tile.buffer, DefaultVertexFormats.BLOCK, 4, 5, NBT_ID);
        assertEquals(MODEL_TEXTURE, uploads.get(0).texture);
        assertEquals(TextureMap.LOCATION_BLOCKS_TEXTURE, uploads.get(1).texture);
        assertTrue(uploads.get(1).forgeBatch);
        assertFalse(uploads.get(0).forgeBatch);
        assertEquals(List.of("model", "atlas"), events);
        assertReleased(tile.buffer);
    }

    private static void emptyBatch() {
        Tile empty = tile(true, buffer -> buffer.setTranslation(5, 6, 7));
        shadow(empty);
        assertTrue(uploads.isEmpty());
        assertTrue(textures.isEmpty());
        assertEquals(1, empty.fast);
        assertReleased(empty.buffer);
        Tile next = tile(true, buffer -> quad(buffer, 8));
        shadow(next);
        assertSame(empty.buffer, next.buffer);
        assertUpload(0, next.buffer, DefaultVertexFormats.BLOCK, 4, 8, NBT_ID);
    }

    private static void failedBatch(boolean failUpload) {
        RuntimeException expected = new IllegalArgumentException(failUpload ? "upload failed" : "callback failed");
        Tile tile = tile(true, buffer -> {
            buffer.setTranslation(7, 8, 9);
            quad(buffer, 3);
            if (failUpload) uploadFailure = expected;
            else throw expected;
        });
        Tessellator original = dispatcher.batchBuffer;
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(CALLER_ID);
        ReportedException thrown = assertThrows(ReportedException.class, () -> shadow(tile));
        assertSame(expected, thrown.getCrashReport().getCrashCause());
        assertEquals(CALLER_ID, id());
        assertSame(original, dispatcher.batchBuffer);
        assertFalse(dispatcher.drawingBatch);
        assertFalse(ShadowFastTesr.active());
        assertTrue(uploads.isEmpty());
        assertReleased(tile.buffer);
        Tile next = tile(true, buffer -> quad(buffer, 12));
        shadow(next);
        assertSame(tile.buffer, next.buffer);
        assertUpload(0, next.buffer, DefaultVertexFormats.BLOCK, 4, 12, NBT_ID);
    }

    private static void nested(boolean ordinaryChild, String failurePoint) {
        RuntimeException expected = new IllegalArgumentException("child failed");
        Tile child = tile(!ordinaryChild, buffer -> {
            assertEquals(CHILD_ID, id());
            if (ordinaryChild) model(17);
            else {
                buffer.setTranslation(20, 30, 40);
                quad(buffer, 17);
            }
            if ("callback".equals(failurePoint)) throw expected;
            if ("upload".equals(failurePoint)) uploadFailure = expected;
        });
        child.material = "child";
        Tile parent = tile(true, buffer -> {
            buffer.setTranslation(2, 3, 4);
            quad(buffer, 5);
            int[] before = buffer.getVertexState().getRawBuffer();
            Tessellator parentBatch = dispatcher.batchBuffer;
            if (failurePoint == null) render(child);
            else {
                ReportedException thrown = assertThrows(ReportedException.class, () -> render(child));
                assertSame(expected, thrown.getCrashReport().getCrashCause());
                assertReleased(child.buffer);
            }
            assertTrue(ShadowFastTesr.active());
            assertEquals(NBT_ID, id());
            assertSame(parentBatch, dispatcher.batchBuffer);
            assertTrue(dispatcher.drawingBatch);
            assertTrue(extension(buffer).actinium$isDrawing());
            assertArrayEquals(before, buffer.getVertexState().getRawBuffer());
            quad(buffer, 8);
        });
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(CALLER_ID);
        shadow(parent);
        assertEquals(CALLER_ID, id());
        int parentUpload = failurePoint == null ? 1 : 0;
        assertEquals(parentUpload + 1, uploads.size());
        if (failurePoint == null) {
            assertEquals(CHILD_ID, uploads.get(0).id);
            assertEquals(ordinaryChild ? 1 : 0, child.ordinary);
            assertEquals(ordinaryChild ? 0 : 1, child.fast);
        }
        if (!ordinaryChild) assertNotSame(parent.buffer, child.buffer);
        assertUpload(parentUpload, parent.buffer, DefaultVertexFormats.BLOCK, 8, 7, NBT_ID);
        assertEquals(new Position(10, 3, 4), uploads.get(parentUpload).positions.get(4));
        assertReleased(parent.buffer);
        Tile next = tile(true, buffer -> quad(buffer, 42));
        shadow(next);
        assertSame(parent.buffer, next.buffer);
        assertUpload(parentUpload + 1, next.buffer, DefaultVertexFormats.BLOCK, 4, 42, NBT_ID);
    }

    private static void existingBatch() {
        Tessellator original = dispatcher.batchBuffer;
        BufferBuilder parent = original.getBuffer();
        dispatcher.preDrawBatch();
        parent.setTranslation(2, 3, 4);
        quad(parent, 5);
        int[] before = parent.getVertexState().getRawBuffer();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(CALLER_ID);
        Tile child = tile(true, buffer -> quad(buffer, 8));
        shadow(child);
        assertNotSame(parent, child.buffer);
        assertSame(original, dispatcher.batchBuffer);
        assertTrue(dispatcher.drawingBatch);
        assertEquals(CALLER_ID, id());
        assertArrayEquals(before, parent.getVertexState().getRawBuffer());
        quad(parent, 11);
        assertEquals(1, uploads.size());
        dispatcher.drawBatch(0);
        assertUpload(0, child.buffer, DefaultVertexFormats.BLOCK, 4, 8, NBT_ID);
        assertUpload(1, parent, DefaultVertexFormats.BLOCK, 8, 7, CALLER_ID);
        assertEquals(new Position(13, 3, 4), uploads.get(1).positions.get(4));
    }

    private static void nbtIds() {
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(CALLER_ID);
        Tile nbt = tile(true, buffer -> quad(buffer, 1));
        Tile metadata = tile(true, buffer -> quad(buffer, 2));
        metadata.material = "unmatched";
        Tile missing = tile(true, buffer -> quad(buffer, 3));
        missing.metadata = 7;
        Tile negative = tile(true, buffer -> quad(buffer, 4));
        negative.metadata = 4;
        for (Tile tile : List.of(nbt, metadata, missing, negative)) {
            shadow(tile);
            assertEquals(CALLER_ID, id());
        }
        assertEquals(List.of(NBT_ID, META_ID, 0, 0), uploads.stream().map(Upload::id).toList());
        assertEquals(List.of(1f, 2f, 3f, 4f), uploads.stream().map(u -> u.positions.getFirst().x).toList());
        assertEquals(1, nbt.serializations);
        assertEquals(1, metadata.serializations);
    }

    private static void provider() {
        ProviderRenderer provider = new ProviderRenderer();
        dispatcher.renderers.put(Tile.class, provider);
        Tile tile = tile(true, buffer -> quad(buffer, 3));
        shadow(tile);
        assertEquals(0, tile.ordinary);
        assertEquals(0, tile.fast);
        assertEquals(1, provider.builds);
        assertEquals(List.of(NBT_ID), provider.ids);
        assertTrue(uploads.isEmpty());
        shadow(tile);
        assertEquals(1, provider.builds);
        assertEquals(List.of(NBT_ID, NBT_ID), provider.ids);
        provider.enabled = false;
        shadow(tile);
        assertEquals(1, tile.fast);
        assertUpload(0, tile.buffer, DefaultVertexFormats.BLOCK, 4, 3, NBT_ID);
        assertEquals(3, provider.lookups);
    }

    private static void bufferReuse() {
        Tile first = tile(true, buffer -> {
            buffer.setTranslation(7, 8, 9);
            quad(buffer, 1);
        });
        Tile second = tile(true, buffer -> quad(buffer, 2));
        shadow(first);
        shadow(second);
        assertSame(first.buffer, second.buffer);
        assertUpload(0, first.buffer, DefaultVertexFormats.BLOCK, 4, 8, NBT_ID);
        assertUpload(1, second.buffer, DefaultVertexFormats.BLOCK, 4, 2, NBT_ID);
        ShadowFastTesr.clear();
        Tile third = tile(true, buffer -> quad(buffer, 3));
        shadow(third);
        assertNotSame(first.buffer, third.buffer);
        assertUpload(2, third.buffer, DefaultVertexFormats.BLOCK, 4, 3, NBT_ID);
    }

    private static void pendingId() {
        List<Integer> submittedIds = new ArrayList<>();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(CALLER_ID);
        PendingTesrDraws.beginPass();
        assertTrue(PendingTesrDraws.canDefer());
        PendingTesrDraws.hold(pending(submittedIds));
        Tile tile = tile(true, buffer -> {
            assertFalse(PendingTesrDraws.canDefer());
            PendingTesrDraws.hold(pending(submittedIds));
            quad(buffer, 2);
        });
        shadow(tile);
        assertEquals(List.of(CALLER_ID, NBT_ID), submittedIds);
        assertEquals(List.of("pending:" + CALLER_ID, "pending:" + NBT_ID, "atlas"), events);
        assertEquals(CALLER_ID, id());
        assertTrue(PendingTesrDraws.canDefer());
        PendingTesrDraws.endPass();
        assertFalse(PendingTesrDraws.canDefer());
    }

    private static PendingTesrDraws.Pending pending(List<Integer> submittedIds) {
        return new PendingTesrDraws.Pending() {
            @Override
            public boolean canWaitFor(TileEntity next) {
                return true;
            }

            @Override
            public void flush() {
                submittedIds.add(id());
                events.add("pending:" + id());
            }
        };
    }

    /** The upload fixture observes the buffer after the real Tessellator finishes it. */
    public static void upload(BufferBuilder buffer) {
        assertFalse(extension(buffer).actinium$isDrawing());
        if (uploadFailure != null) {
            RuntimeException failure = uploadFailure;
            uploadFailure = null;
            throw failure;
        }
        List<Position> positions = new ArrayList<>();
        int stride = buffer.getVertexFormat().getSize();
        for (int i = 0; i < buffer.getVertexCount(); i++) {
            int start = i * stride;
            positions.add(new Position(buffer.getByteBuffer().getFloat(start),
                buffer.getByteBuffer().getFloat(start + 4), buffer.getByteBuffer().getFloat(start + 8)));
        }
        uploads.add(new Upload(buffer, buffer.getVertexFormat(), positions, id(), texture,
            ShadowTesrDraws.isForgeBatch(buffer)));
        events.add(buffer.getVertexFormat() == DefaultVertexFormats.BLOCK ? "atlas" : "model");
    }

    private static void assertUpload(int index, BufferBuilder buffer, VertexFormat format, int vertices, float x, int id) {
        Upload upload = uploads.get(index);
        assertSame(buffer, upload.buffer);
        assertSame(format, upload.format);
        assertEquals(vertices, upload.positions.size());
        assertEquals(x, upload.positions.getFirst().x);
        assertEquals(id, upload.id);
    }

    private static void assertReleased(BufferBuilder buffer) {
        assertFalse(extension(buffer).actinium$isDrawing());
        assertEquals(0, buffer.getVertexCount());
        assertFalse(ShadowTesrDraws.isForgeBatch(buffer));
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        quad(buffer, 6);
        buffer.finishDrawing();
        assertEquals(6f, buffer.getByteBuffer().getFloat(0));
        assertEquals(0f, buffer.getByteBuffer().getFloat(4));
        assertEquals(0f, buffer.getByteBuffer().getFloat(8));
        extension(buffer).actinium$discard();
    }

    private static void configureIds() {
        Int2IntOpenHashMap metaIds = new Int2IntOpenHashMap();
        metaIds.defaultReturnValue(-1);
        metaIds.put(2, META_ID);
        metaIds.put(4, -5);
        var matches = new Reference2ObjectOpenHashMap<Block, Int2IntMap>();
        matches.put(Blocks.STONE, metaIds);
        BlockRenderingSettings.INSTANCE.setBlockMetaMatches(matches);
        NbtConditionalIdMap<Block> nbt = new NbtConditionalIdMap<>();
        nbt.addCondition(Blocks.STONE, Map.of("material", new PropertiesTokenizer.NbtValue("parent", true)),
            NBT_ID, Set.of(2));
        nbt.addCondition(Blocks.STONE, Map.of("material", new PropertiesTokenizer.NbtValue("child", true)),
            CHILD_ID, Set.of(2));
        BlockRenderingSettings.INSTANCE.setBlockNbtMap(nbt);
    }

    private static int id() {
        return CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
    }

    private static BufferBuilderExtension extension(BufferBuilder buffer) {
        return (BufferBuilderExtension) buffer;
    }

    private static Tile tile(boolean fast, Consumer<BufferBuilder> body) {
        return new Tile(fast, body);
    }

    private static void shadow(Tile tile) {
        ShadowFastTesr.dispatch(() -> render(tile));
    }

    private static void render(Tile tile) {
        dispatcher.render(tile, 1.5, -2, 3.25, 0.5f, 2, 0.75f);
    }

    private static void quad(BufferBuilder buffer, int x) {
        for (int i = 0; i < 4; i++) {
            buffer.pos(x, i & 1, i >> 1).color(255, 255, 255, 255).tex(i & 1, i >> 1).lightmap(16, 240).endVertex();
        }
    }

    private static void model(int x) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        dispatcher.renderEngine.bindTexture(MODEL_TEXTURE);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL);
        for (int i = 0; i < 4; i++) {
            buffer.pos(x, i & 1, i >> 1).tex(i & 1, i >> 1).normal(1, 0, 0).endVertex();
        }
        tessellator.draw();
    }

    private record Position(float x, float y, float z) {}
    private record Upload(BufferBuilder buffer, VertexFormat format, List<Position> positions, int id,
                          ResourceLocation texture, boolean forgeBatch) {}

    private static final class Tile extends TileEntity {
        private final boolean hasFast;
        private final Consumer<BufferBuilder> body;
        private String material = "parent";
        private int metadata = 2;
        private BufferBuilder buffer;
        private int queries, ordinary, fast, serializations;

        private Tile(boolean hasFast, Consumer<BufferBuilder> body) {
            this.hasFast = hasFast;
            this.body = body;
        }

        @Override
        public boolean hasFastRenderer() {
            queries++;
            return hasFast;
        }

        @Override
        public Block getBlockType() {
            return Blocks.STONE;
        }

        @Override
        public int getBlockMetadata() {
            return metadata;
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound compound) {
            serializations++;
            compound.setString("material", material);
            return compound;
        }

        @Override
        public void addInfoToCrashReport(CrashReportCategory category) {
            category.addCrashSection("Test tile", material);
        }
    }

    private static class Renderer extends TileEntitySpecialRenderer<Tile> {
        @Override
        public void render(Tile tile, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
            tile.ordinary++;
            if (tile.hasFast) {
                // Mirrors RenderSpikeTrap's ordinary entry opening the shared model buffer.
                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder buffer = tessellator.getBuffer();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                renderTileEntityFast(tile, x, y, z, partialTicks, destroyStage, alpha, buffer);
                buffer.setTranslation(0, 0, 0);
                tessellator.draw();
            } else tile.body.accept(null);
        }

        @Override
        public void renderTileEntityFast(Tile tile, double x, double y, double z, float partialTicks, int destroyStage,
                                         float alpha, BufferBuilder buffer) {
            tile.fast++;
            tile.buffer = buffer;
            assertEquals(1.5, x);
            assertEquals(-2, y);
            assertEquals(3.25, z);
            assertEquals(0.5f, partialTicks);
            assertEquals(2, destroyStage);
            assertEquals(0.75f, alpha);
            assertSame(DefaultVertexFormats.BLOCK, buffer.getVertexFormat());
            assertTrue(extension(buffer).actinium$isDrawing());
            tile.body.accept(buffer);
        }
    }

    private static final class ProviderRenderer extends Renderer implements TesrMeshProvider {
        private boolean enabled = true;
        private int lookups, builds;
        private final List<Integer> ids = new ArrayList<>();

        @Override
        public Object angelica$meshKey(TileEntity tile) {
            lookups++;
            return enabled ? this : null;
        }

        @Override
        public void angelica$transform(TileEntity tile, double x, double y, double z) {
            assertEquals(1.5, x);
            assertEquals(-2, y);
            assertEquals(3.25, z);
            ids.add(id());
        }

        @Override
        public void angelica$build(TesrMeshSink sink, TileEntity tile) {
            builds++;
            assertEquals(NBT_ID, id());
        }
    }
}

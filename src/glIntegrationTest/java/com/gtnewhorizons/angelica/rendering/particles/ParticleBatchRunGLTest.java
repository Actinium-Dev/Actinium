package com.gtnewhorizons.angelica.rendering.particles;

import com.dhj.actinium.render.TessellatorDrawRouter;
import com.gtnewhorizons.angelica.client.rendering.DeferredDrawBatcher;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import com.gtnewhorizons.angelica.glsm.backend.WindowBackends;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.ffp.ParticleQuadMesh;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.hooks.DrawCallObserver;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.rendering.ParticleItemPickupRenderer;
import com.gtnewhorizons.angelica.rendering.ParticleRunSplitter;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleBatchRunGLTest extends GLStateManagerIntegrationFixture {

    private static final float ROT_X = 0.36F;
    private static final float ROT_XZ = 0.21F;
    private static final float ROT_Z = 0.48F;
    private static final float ROT_YZ = 0.13F;
    private static final float ROT_XY = 0.27F;

    private long drawsBefore;
    private DrawCallObserver previousObserver;

    @BeforeEach
    void bindContext() {
        WindowBackends.useSdl();
        GLStateManager.bindDisplayContext(new Object(), context);
        GLStateManager.recreateDefaultVertexArray();
        installDefaultGlsmConfig();
        ShaderManager.enable();
        ParticleInstancer.clear();
        TesrBatchRenderer.INSTANCE.instanceRing().delete();
        drawsBefore = ParticleInstancer.getDraws();
        previousObserver = GLSMHooks.drawCallObserver;
    }

    @AfterEach
    void restore() {
        GLSMHooks.drawCallObserver = previousObserver;
        DeferredDrawBatcher.exitAndFlush();
        ParticleInstancer.clear();
        ShaderManager.disable();
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError(), "the particle run must leave no GL error");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void independentBeamDrawResumesTheOriginalParticleRun(boolean hasVanillaRunBoundary) {
        final BufferBuilder run = Tessellator.getInstance().getBuffer();
        final Particle laser = new CaptureParticle();
        beginRun(run);
        try {
            assertFalse(ParticleRunSplitter.ownsIndependentBoundary(laser));
            ParticleRunSplitter.renderIndependent(laser, () -> {
                if (hasVanillaRunBoundary && !ParticleRunSplitter.ownsIndependentBoundary(laser)) {
                    draw(run);
                }
                run.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
                draw(run);
                if (hasVanillaRunBoundary && !ParticleRunSplitter.ownsIndependentBoundary(laser)) {
                    run.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
                }
            });
            assertFalse(ParticleRunSplitter.ownsIndependentBoundary(laser));
            assertSame(DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP, run.getVertexFormat());
        } finally {
            endRun(run);
        }
    }

    @Test
    void nestedEntityDrawFlushesAndRestartsTheRun() {
        beginRun(Tessellator.getInstance().getBuffer());
        assertTrue(ParticleRunSplitter.beforeNestedDraw(), "the open run has to be flushed before the entity uses the builder");
        assertEquals(0, Tessellator.getInstance().getBuffer().getVertexCount(),
            "the nested boundary consumes the current run instead of nesting another begin");
        ParticleRunSplitter.afterNestedDraw(true);

        assertTrue(ParticleRunSplitter.beforeNestedDraw(), "the reopened run accepts the same boundary again");
        ParticleRunSplitter.endParticles();
    }

    @Test
    void nestedPickupRendererCanUseTheSharedBuilder() {
        final BufferBuilder run = Tessellator.getInstance().getBuffer();
        beginRun(run);
        try {
            assertDoesNotThrow(() -> ParticleItemPickupRenderer.render(() -> {
                run.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
                run.finishDrawing();
            }, false));

            assertSame(DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP, run.getVertexFormat());
            assertEquals(0, run.getVertexCount());
        } finally {
            endRun(run);
        }
    }

    @Test
    void particleCollectionOwnsItsDrawAndResumesInstancing() {
        final BufferBuilder run = Tessellator.getInstance().getBuffer();
        final Particle billboard = new Particle(null, 4, 5, 6) {
            @Override
            public int getBrightnessForRender(float partialTick) {
                return 0x00F000A0;
            }
        };
        beginRun(run);
        try {
            render(billboard, run);

            final Particle collection = new CaptureParticle() {
                @Override
                public void renderParticle(BufferBuilder buffer, Entity entity, float partialTicks,
                                           float rotationX, float rotationZ, float rotationYZ,
                                           float rotationXY, float rotationXZ) {
                    buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
                    assertSame(run, buffer);
                    draw(run);
                }
            };
            ParticleRunSplitter.renderIndependent(collection,
                () -> collection.renderParticle(run, null, 0.5F, ROT_X, ROT_Z, ROT_YZ, ROT_XY, ROT_XZ));

            assertEquals(drawsBefore + 1, ParticleInstancer.getDraws());
            assertSame(DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP, run.getVertexFormat());
            render(billboard, run);
        } finally {
            endRun(run);
        }
        assertEquals(drawsBefore + 2, ParticleInstancer.getDraws());
        assertClosedBuilder(run);
    }

    @ParameterizedTest
    @CsvSource({"false, false", "true, false", "false, true", "true, true"})
    void unregisteredBeamDrawsBetweenInstanceBatches(boolean deferDraws, boolean writeBeforeFirstDraw) {
        final BufferBuilder run = new ParticleTestBuffer();
        final List<Instancing> submissions = new ArrayList<>();
        final List<Integer> beamVertices = new ArrayList<>();
        GLSMHooks.drawCallObserver = flags -> {
            final Instancing mode = GLStateManager.ctx().ffpInstancing;
            submissions.add(mode);
            if (mode == Instancing.NONE) beamVertices.add(run.getVertexCount());
        };
        final Particle beam = new CaptureParticle() {
            @Override
            public void renderParticle(BufferBuilder buffer, Entity entity, float ticks, float x, float xz,
                                       float z, float yz, float xy) {
                if (writeBeforeFirstDraw) super.renderParticle(buffer, entity, ticks, x, xz, z, yz, xy);
                draw(run);
                for (int face = 0; face < 3; face++) {
                    buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
                    assertFalse(DeferredDrawBatcher.isActive());
                    super.renderParticle(buffer, entity, ticks, x, xz, z, yz, xy);
                    draw(run);
                }
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
            }
        };
        beginRun(run);
        try {
            if (deferDraws) DeferredDrawBatcher.enter();
            assertEquals(deferDraws, DeferredDrawBatcher.isActive());
            render(new CaptureParticle(), run);
            assertTrue(submissions.isEmpty());
            render(beam, run);
            assertEquals(deferDraws, DeferredDrawBatcher.isActive());
            assertEquals(writeBeforeFirstDraw ? List.of(4, 4, 4, 4) : List.of(4, 4, 4), beamVertices);
            render(new CaptureParticle(), run);
            assertEquals(drawsBefore + 1, ParticleInstancer.getDraws(), "the trailing instance waits for the layer end");
            finishLayer(run);
            assertEquals(writeBeforeFirstDraw
                ? List.of(Instancing.PARTICLE, Instancing.NONE, Instancing.NONE, Instancing.NONE, Instancing.NONE, Instancing.PARTICLE)
                : List.of(Instancing.PARTICLE, Instancing.NONE, Instancing.NONE, Instancing.NONE, Instancing.PARTICLE), submissions);
        } finally {
            ParticleRunSplitter.endParticles();
        }
    }

    @Test
    void flushUsesTheLayerMatrixAndRestoresTheBeamMatrixBeforeItsDraw() {
        final BufferBuilder run = new ParticleTestBuffer();
        final CaptureParticle quad = new CaptureParticle();
        final Matrix4f saved = new Matrix4f(GLSMMatrices.getModelViewMatrix());
        final Matrix4f layer = new Matrix4f().translation(2, 3, 4);
        final Matrix4f beam = new Matrix4f(layer).rotateY(0.5F).translate(0, 0, 6);
        final List<Matrix4f> matrices = new ArrayList<>();
        GLSMHooks.drawCallObserver = flags -> matrices.add(new Matrix4f(GLSMMatrices.getModelViewMatrix()));
        GLSMMatrices.setModelViewMatrix(layer);
        beginRun(run);
        try {
            DeferredDrawBatcher.enter();
            render(quad, run);
            final BufferBuilder deferred = new BufferBuilder(256);
            deferred.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
            quad.renderParticle(deferred, null, 0.5F, ROT_X, ROT_XZ, ROT_Z, ROT_YZ, ROT_XY);
            draw(deferred);
            assertTrue(matrices.isEmpty(), "both earlier draws must still be pending");

            render(new CaptureParticle() {
                @Override
                public void renderParticle(BufferBuilder buffer, Entity entity, float ticks, float x, float xz,
                                           float z, float yz, float xy) {
                    GLSMMatrices.setModelViewMatrix(beam);
                    try {
                        super.renderParticle(buffer, entity, ticks, x, xz, z, yz, xy);
                        draw(run);
                        assertEquals(beam, new Matrix4f(GLSMMatrices.getModelViewMatrix()));
                        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
                    } finally {
                        GLSMMatrices.setModelViewMatrix(layer);
                    }
                }
            }, run);
            assertEquals(List.of(layer, layer, beam), matrices);
            assertTrue(DeferredDrawBatcher.isActive());
            finishLayer(run);
        } finally {
            ParticleRunSplitter.endParticles();
            GLSMMatrices.setModelViewMatrix(saved);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void drawingAnotherBufferLeavesTheParticleRunAndItsDeferredStateAlone(boolean deferDraws) {
        final BufferBuilder run = new ParticleTestBuffer();
        final BufferBuilder other = new ParticleTestBuffer();
        final List<Instancing> submissions = new ArrayList<>();
        GLSMHooks.drawCallObserver = flags -> submissions.add(GLStateManager.ctx().ffpInstancing);
        final Particle particle = new CaptureParticle() {
            @Override
            public void renderParticle(BufferBuilder buffer, Entity entity, float ticks, float x, float xz,
                                       float z, float yz, float xy) {
                super.renderParticle(buffer, entity, ticks, x, xz, z, yz, xy);
                other.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
                super.renderParticle(other, entity, ticks, x, xz, z, yz, xy);
                draw(other);
                assertEquals(deferDraws, DeferredDrawBatcher.isActive());
                assertEquals(4, buffer.getVertexCount(), "the draw must leave the particle's pending quad intact");
                assertClosedBuilder(other);
            }
        };
        beginRun(run);
        try {
            if (deferDraws) DeferredDrawBatcher.enter();
            final long before = ParticleInstancer.getCaptured();
            render(new CaptureParticle(), run);
            render(particle, run);
            assertEquals(drawsBefore, ParticleInstancer.getDraws());
            assertEquals(before + 2, ParticleInstancer.getCaptured());
            assertEquals(0, run.getVertexCount());
            finishLayer(run);
            assertEquals(deferDraws ? List.of(Instancing.PARTICLE, Instancing.NONE)
                : List.of(Instancing.NONE, Instancing.PARTICLE), submissions);
        } finally {
            ParticleRunSplitter.endParticles();
        }
    }

    @Test
    void nestedParticleUsesTheOriginalCallbackAndKeepsTheOuterDrawBoundary() {
        final BufferBuilder run = new ParticleTestBuffer();
        final List<Particle> callbacks = new ArrayList<>();
        final List<Instancing> submissions = new ArrayList<>();
        GLSMHooks.drawCallObserver = flags -> submissions.add(GLStateManager.ctx().ffpInstancing);
        final Particle inner = new CaptureParticle() {
            @Override
            public void renderParticle(BufferBuilder buffer, Entity entity, float ticks, float x, float xz,
                                       float z, float yz, float xy) {
                callbacks.add(this);
                assertSame(run, buffer);
                super.renderParticle(buffer, entity, ticks, x, xz, z, yz, xy);
            }
        };
        final Particle outer = new CaptureParticle() {
            @Override
            public void renderParticle(BufferBuilder buffer, Entity entity, float ticks, float x, float xz,
                                       float z, float yz, float xy) {
                callbacks.add(this);
                final boolean handled = ParticleInstancer.renderParticle(inner, buffer, entity, ticks, x, xz, z, yz, xy);
                if (!handled) inner.renderParticle(buffer, entity, ticks, x, xz, z, yz, xy);
                assertFalse(handled, "submitParticle must call the nested renderer itself");
                assertTrue(submissions.isEmpty(), "the earlier instance waits for the outer draw");
                assertEquals(4, buffer.getVertexCount());
                draw(run);
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
            }
        };
        beginRun(run);
        try {
            render(new CaptureParticle(), run);
            render(outer, run);
            assertEquals(List.of(outer, inner), callbacks);
            assertEquals(List.of(Instancing.PARTICLE, Instancing.NONE), submissions,
                "the earlier instance must reach GL before the nested quad");
            finishLayer(run);
            assertEquals(2, submissions.size(), "the layer must not submit either particle again");
        } finally {
            ParticleRunSplitter.endParticles();
        }
    }

    @Test
    void firstDrawOnAnEmptyRunRestoresTheCustomRenderersVaoAndVbo() {
        final BufferBuilder run = new ParticleTestBuffer();
        final int savedVao = GLStateManager.getBoundVAO();
        final int savedVbo = GLStateManager.getBoundVBO();
        final int vao = GLSMVertexInput.glGenVertexArrays();
        final int vbo = GLSMBuffers.glGenBuffers();
        beginRun(run);
        try {
            assertNotEquals(0, vao);
            assertNotEquals(0, vbo);
            render(new CaptureParticle(), run);
            render(new CaptureParticle() {
                @Override
                public void renderParticle(BufferBuilder buffer, Entity entity, float ticks, float x, float xz,
                                           float z, float yz, float xy) {
                    GLSMVertexInput.glBindVertexArray(vao);
                    GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
                    assertEquals(0, run.getVertexCount());
                    assertEquals(drawsBefore, ParticleInstancer.getDraws());
                    draw(run);
                    assertEquals(drawsBefore + 1, ParticleInstancer.getDraws(), "the empty run still flushes the pending instance");
                    assertAll(
                        () -> assertEquals(vao, GL11C.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), "driver VAO"),
                        () -> assertEquals(vbo, GL11C.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING), "driver array buffer"),
                        () -> assertEquals(vao, GLStateManager.getBoundVAO(), "GLSM VAO"),
                        () -> assertEquals(vbo, GLStateManager.getBoundVBO(), "GLSM array buffer"));
                    buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
                }
            }, run);
            finishLayer(run);
        } finally {
            GLSMVertexInput.glBindVertexArray(savedVao);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedVbo);
            GLSMVertexInput.glDeleteVertexArrays(vao);
            GLSMBuffers.glDeleteBuffers(vbo);
            ParticleRunSplitter.endParticles();
        }
    }

    @Test
    void keepsTheVerticesTheBatchCannotTakeForTheRunsOwnDraw() {
        final BufferBuilder run = new ParticleTestBuffer();
        final long spilledBefore = ParticleInstancer.getSpilled();
        beginRun(run);
        try {
            render(new SpillingParticle(), run);
            ParticleInstancer.endLayer();
            assertEquals(8, run.getVertexCount(), "both quads belong to the run's own draw");
            assertEquals(drawsBefore, ParticleInstancer.getDraws());
            assertEquals(spilledBefore + 1, ParticleInstancer.getSpilled());
            draw(run);
            assertEquals(0, run.getVertexCount());
            assertClosedBuilder(run);
        } finally {
            ParticleRunSplitter.endParticles();
        }
    }

    private static void render(Particle particle, BufferBuilder run) {
        assertTrue(ParticleInstancer.renderParticle(particle, run, null, 0.5F, ROT_X, ROT_XZ, ROT_Z, ROT_YZ, ROT_XY));
    }

    private static void beginRun(BufferBuilder run) {
        ParticleRunSplitter.beginParticles();
        run.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
        ParticleRunSplitter.beginRun();
        ParticleInstancer.beginLayer();
    }

    private static void endRun(BufferBuilder run) {
        try {
            finishLayer(run);
        } finally {
            ParticleRunSplitter.endParticles();
        }
    }

    private static void finishLayer(BufferBuilder run) {
        ParticleInstancer.endLayer();
        DeferredDrawBatcher.exitAndFlush();
        draw(run);
    }

    // The harness does not install the Tessellator mixin that invokes the router in game.
    private static void draw(BufferBuilder buffer) {
        assertTrue(TessellatorDrawRouter.draw(buffer, null), "the router owns this draw");
    }

    private static void assertClosedBuilder(BufferBuilder run) {
        run.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
        run.finishDrawing();
        assertEquals(0, run.getVertexCount(), "the reopened builder is empty");
    }

    /** A particle the registry has no descriptor for, writing one decodable quad. */
    private static class CaptureParticle extends Particle {

        CaptureParticle() {
            super(null, 4.0D, 5.0D, 6.0D);
        }

        @Override
        public int getBrightnessForRender(float partialTick) {
            return 0x00F00123;
        }

        @Override
        public void renderParticle(BufferBuilder buffer, Entity entityIn, float partialTicks, float rotationX,
                                   float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
            final float half = 0.25F;
            for (int corner = 0; corner < ParticleQuadMesh.VERTEX_COUNT; corner++) {
                final float a = ParticleQuadMesh.cornerA(corner) * half;
                final float b = ParticleQuadMesh.cornerB(corner) * half;
                buffer.pos(
                        4.0D + ParticleQuadMesh.offsetX(a, b, rotationX, rotationXY),
                        5.0D + ParticleQuadMesh.offsetY(b, rotationZ),
                        6.0D + ParticleQuadMesh.offsetZ(a, b, rotationYZ, rotationXZ))
                    .tex(a < 0.0F ? 0.75F : 0.25F, b < 0.0F ? 0.5F : 0.125F)
                    .color(255, 255, 255, 255)
                    .lightmap(0x00F0, 0x00A0)
                    .endVertex();
            }
        }
    }

    private static final class SpillingParticle extends CaptureParticle {
        @Override
        public void renderParticle(BufferBuilder buffer, Entity entityIn, float partialTicks, float rotationX,
                                   float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
            super.renderParticle(buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
            final float half = 0.125F;
            for (int corner = 0; corner < ParticleQuadMesh.VERTEX_COUNT; corner++) {
                final float a = ParticleQuadMesh.cornerA(corner) * half;
                final float b = ParticleQuadMesh.cornerB(corner) * half;
                buffer.pos(
                        4.0D + ParticleQuadMesh.offsetX(a, b, rotationX, rotationXY),
                        5.0D + ParticleQuadMesh.offsetY(b, rotationZ),
                        6.5D + ParticleQuadMesh.offsetZ(a, b, rotationYZ, rotationXZ))
                    .tex(a < 0.0F ? 0.75F : 0.25F, b < 0.0F ? 0.5F : 0.125F)
                    .color(255, 255, 255, 255)
                    .lightmap(0x00F0, 0x00A0)
                    .endVertex();
            }
        }
    }
}

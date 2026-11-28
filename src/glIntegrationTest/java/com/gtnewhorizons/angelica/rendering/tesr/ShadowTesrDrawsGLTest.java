package com.gtnewhorizons.angelica.rendering.tesr;

import com.dhj.actinium.runtime.ActiniumRuntime;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrShader;
import com.gtnewhorizons.angelica.api.tesr.TesrShaders;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.COLOR_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.POSITION_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.VERTEX_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowTesrDrawsGLTest extends GLStateManagerIntegrationFixture {
    private static final TesrMaterial BAKED = TesrMaterial.builder().unlit().noCull().build();
    private static final TesrMaterial INSTANCED = TesrMaterial.builder().stream().unlit().noCull().build();
    private final ModelPartBatcher models = ModelPartBatcher.INSTANCE;
    private final TesrBatchRenderer tesrs = TesrBatchRenderer.INSTANCE;
    private boolean previousBatching;
    private int primitiveQuery;
    private long primitiveCount;

    @BeforeEach
    void setUp() {
        installDefaultGlsmConfig();
        GLStateManager.markSplashComplete("ShadowTesrDrawsGLTest");
        GLStateManager.bindDisplayContext(new Object(), context);
        routeVertexFormats(true);
        previousBatching = ActiniumRuntime.options().performance.enableEntityBatching;
        ActiniumRuntime.options().performance.enableEntityBatching = true;
        ShaderManager.enable();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.disableTexture();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.disableTexture();
        GLSMLighting.disableLighting();
        GLSMDepthStencilAlpha.enableDepthTest();
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
        tesrs.beginPass(TesrBatchRenderer.PASS_SHADOW, new Matrix4f(), 0, 0, 0);
        models.begin(ModelPartBatcher.Mode.BLOCK_ENTITIES, true);
        PendingTesrDraws.beginPass();
        primitiveQuery = GL15.glGenQueries();
        GL15.glBeginQuery(GL30.GL_PRIMITIVES_GENERATED, primitiveQuery);
    }

    @AfterEach
    void tearDown() {
        GL15.glEndQuery(GL30.GL_PRIMITIVES_GENERATED);
        GL15.glDeleteQueries(primitiveQuery);
        ShadowTesrDraws.discardPending();
        PendingTesrDraws.endPass();
        models.clear();
        tesrs.clearRetained();
        BatchEligibility.reset();
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
        ActiniumRuntime.options().performance.enableEntityBatching = previousBatching;
        routeVertexFormats(false);
        ShaderManager.disable();
    }

    @Test
    void retainedOnlyQueuesDrawAtEachBoundaryAndKeepTheShadowPassOpen() {
        final TemplateBuffer quad = quad();
        assertEmpty();
        final long before = submittedPrimitives();
        ShadowTesrDraws.flushPending();
        assertEquals(before, submittedPrimitives());

        tesrs.queue(quad, null, BAKED);
        assertTrue(tesrs.hasQueuedShadowGeometry());
        assertFalse(models.hasQueuedShadowGeometry());
        ShadowTesrDraws.flushPending();
        assertTrue(submittedPrimitives() > before);
        assertEmpty();

        final long afterBaked = submittedPrimitives();
        assertTrue(models.queueTemplate(quad, EntityMaterials.OVERLAY));
        assertTrue(models.hasQueuedShadowGeometry());
        assertFalse(tesrs.hasQueuedShadowGeometry());
        ShadowTesrDraws.flushPending();
        assertTrue(submittedPrimitives() > afterBaked);
        assertEmpty();

        final long afterModels = submittedPrimitives();
        tesrs.queue(quad, null, INSTANCED);
        assertTrue(tesrs.hasQueuedShadowGeometry());
        ShadowTesrDraws.flushPending();
        assertTrue(submittedPrimitives() > afterModels);
        assertEmpty();
        assertTrue(models.isShadowPass());
        assertTrue(tesrs.hasPendingGeometry());

        final long completed = submittedPrimitives();
        ShadowTesrDraws.flushPending();
        models.flush();
        tesrs.flush();
        assertEquals(completed, submittedPrimitives());
    }

    @Test
    void nestedTilesSubmitTheParentBeforeReplacingItsId() {
        final TemplateBuffer quad = quad();
        final CapturedRenderingState ids = CapturedRenderingState.INSTANCE;
        ids.setCurrentBlockEntity(17);
        ShadowFastTesr.dispatch(() -> ShadowFastTesr.withBlockEntity(null, () -> {
            ids.setCurrentBlockEntity(31);
            tesrs.queue(quad, null, BAKED);
            final long parentQueued = submittedPrimitives();
            ShadowFastTesr.withBlockEntity(null, () -> {
                assertTrue(submittedPrimitives() > parentQueued);
                assertEmpty();
                ids.setCurrentBlockEntity(47);
                tesrs.queue(quad, null, BAKED);
            });
            assertEquals(31, ids.getCurrentRenderedBlockEntity());
            assertEmpty();
            tesrs.queue(quad, null, BAKED);
        }));
        assertEquals(17, ids.getCurrentRenderedBlockEntity());
        assertEmpty();
    }

    @Test
    void failedTileDiscardsBothQueuesBeforeTheOuterFinalFlush() {
        final TemplateBuffer quad = quad();
        final IllegalStateException failure = new IllegalStateException("tile callback failed");
        final long before = submittedPrimitives();
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> ShadowFastTesr.dispatch(() -> ShadowFastTesr.withBlockEntity(null, () -> {
                tesrs.queue(quad, null, BAKED);
                assertTrue(models.queueTemplate(quad, EntityMaterials.OVERLAY));
                throw failure;
            }))));
        assertEmpty();
        PendingTesrDraws.endPass();
        models.flush();
        tesrs.flush();
        assertEquals(before, submittedPrimitives());
    }

    @Test
    void failedSubmissionDoesNotReplayTheSuccessfulQueueOrTheFailedRetainedGroup() {
        final TemplateBuffer quad = quad();
        final IllegalStateException failure = new IllegalStateException("shader bind failed");
        final TesrShader shader = TesrShaders.register("actinium:shadow_queue_failure", () -> {
            throw failure;
        }, () -> {});
        final TesrMaterial failing = TesrMaterial.builder().unlit().shader(shader).build();
        assertTrue(models.queueTemplate(quad, EntityMaterials.OVERLAY));
        tesrs.queue(quad, null, failing);
        assertSame(failure, assertThrows(IllegalStateException.class, ShadowTesrDraws::flushPending));
        ShadowTesrDraws.discardPending();
        assertEmpty();
        assertFalse(BatchStateGuard.isSuspended());

        final long completed = submittedPrimitives();
        tesrs.queue(quad, null, INSTANCED);
        ShadowTesrDraws.flushPending();
        assertTrue(submittedPrimitives() > completed);
        final long recovered = submittedPrimitives();
        models.flush();
        tesrs.flush();
        assertEquals(recovered, submittedPrimitives());
    }

    @Test
    void flushRestoresMatrixModeTextureUnitAndCurrentId() {
        tesrs.queue(quad(), null, BAKED);
        final Matrix4f projection = new Matrix4f().scaling(0.5f, 0.75f, 1.0f);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.setProjectionMatrix(projection);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE2);
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(73);
        ShadowTesrDraws.flushPending();
        assertEquals(GL11.GL_PROJECTION, GLSMMatrices.getMatrixMode().getMode());
        assertEquals(projection, GLSMMatrices.getProjectionMatrix());
        assertEquals(2, GLSMTextures.getActiveTextureUnit());
        assertEquals(73, CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity());
        assertFalse(BatchStateGuard.isSuspended());
    }

    private void assertEmpty() {
        assertFalse(models.hasQueuedShadowGeometry());
        assertFalse(tesrs.hasQueuedShadowGeometry());
    }

    /** Counts GPU submissions even when the optional production GL redirector is absent. */
    private long submittedPrimitives() {
        GL15.glEndQuery(GL30.GL_PRIMITIVES_GENERATED);
        primitiveCount += GL15.glGetQueryObjectui(primitiveQuery, GL15.GL_QUERY_RESULT);
        GL15.glBeginQuery(GL30.GL_PRIMITIVES_GENERATED, primitiveQuery);
        return primitiveCount;
    }

    private static TemplateBuffer quad() {
        final int[] data = new int[4 * VERTEX_SIZE];
        final float[] xs = {-0.5f, 0.5f, 0.5f, -0.5f};
        final float[] ys = {-0.5f, -0.5f, 0.5f, 0.5f};
        for (int i = 0; i < 4; i++) {
            data[i * VERTEX_SIZE + POSITION_INDEX] = Float.floatToIntBits(xs[i]);
            data[i * VERTEX_SIZE + POSITION_INDEX + 1] = Float.floatToIntBits(ys[i]);
            data[i * VERTEX_SIZE + COLOR_INDEX] = -1;
        }
        return new TemplateBuffer(data, 4, GL11.GL_QUADS);
    }
}

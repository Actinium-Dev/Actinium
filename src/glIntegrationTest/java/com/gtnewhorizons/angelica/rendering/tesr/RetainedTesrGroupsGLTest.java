package com.gtnewhorizons.angelica.rendering.tesr;

import com.dhj.actinium.runtime.ActiniumRuntime;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.glsm.DepthPeel;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMFog;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import dhj.embeddedt.embeddium.impl.gui.TranslucencyMode;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.COLOR_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.POSITION_INDEX;
import static com.gtnewhorizon.gtnhlib.client.renderer.cel.util.ModelQuadUtil.VERTEX_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetainedTesrGroupsGLTest extends GLStateManagerIntegrationFixture {
    private static final int SIZE = 32;
    private static final int CENTER = SIZE / 2;
    private static final float HALF_ALPHA = 128 / 255f;
    private static final TesrMaterial RED = translucent(1, 0, 0);
    private static final TesrMaterial GREEN = translucent(0, 1, 0);
    private static final TesrMaterial BLUE = translucent(0, 0, 1);
    private static final TesrMaterial WHITE = translucent(1, 1, 1);
    private static final TesrMaterial OPAQUE_BLUE = TesrMaterial.builder().stream().unlit().color(0, 0, 1, 1).build();

    private final TesrBatchRenderer batcher = TesrBatchRenderer.INSTANCE;
    private TranslucencyMode previousMode;
    private GlFramebuffer target;
    private int color;
    private int depth;

    @BeforeAll
    static void initializeGlsm() {
        installDefaultGlsmConfig();
        GLStateManager.markSplashComplete("RetainedTesrGroupsGLTest");
        GLStateManager.bindDisplayContext(new Object(), context);
    }

    @BeforeEach
    void setUp() {
        previousMode = ActiniumRuntime.options().performance.translucencyMode;
        ActiniumRuntime.options().performance.translucencyMode = TranslucencyMode.FANCY;
        ShaderManager.enable();
        CapturedRenderingState.INSTANCE.setCurrentEntityColor(0, 0, 0, 0);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.disableTexture();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.disableTexture();
        GLSMLighting.disableLighting();
        GLSMFog.disableFog();
        GLSMBlend.disableBlend();
        GLSMBlend.glBlendEquation(GL14.GL_FUNC_ADD);
        GLSMBlend.glColorMask(true, true, true, true);
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMDepthStencilAlpha.enableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMRasterizer.disableScissorTest();
        GLSMRasterizer.disableCull();
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();

        color = texture(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        depth = texture(GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT);
        target = new GlFramebuffer();
        target.addColorAttachment(0, color);
        target.addDepthAttachment(depth);
        target.drawBuffers(new int[] {0});
        target.readBuffer(0);
        target.bind();
        assertEquals(GL30.GL_FRAMEBUFFER_COMPLETE, GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER));
        GLSMRasterizer.glViewport(0, 0, SIZE, SIZE);
        GLSMBlend.glClearColor(0, 0, 0, 1);
        GLSMDepthStencilAlpha.glClearDepth(1.0);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        TranslucentPeel.beginPass();
        batcher.beginPass(TesrBatchRenderer.PASS_MAIN_0, new Matrix4f(), 0, 0, 0);
    }

    @AfterEach
    void tearDown() {
        batcher.clearRetained();
        TranslucentPeel.endPass();
        TranslucentPeel.destroy();
        ActiniumRuntime.options().performance.translucencyMode = previousMode;
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        BatchDrawDefaults.capturePassFramebuffer();
        if (target != null) target.destroy();
        GLSMTextures.glDeleteTextures(color);
        GLSMTextures.glDeleteTextures(depth);
        ShaderManager.disable();
    }

    @Test
    void opaqueInstancedQuadDrawsWithMaterialColor() {
        queue(quadTemplate(0.75f), OPAQUE_BLUE, 0, 0);
        final long instancedDraws = batcher.statInstancedDraws();

        batcher.flush();

        assertTrue(batcher.statInstancedDraws() > instancedDraws, "an instanced draw must be submitted");
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
        assertPixel(CENTER, CENTER, 0, 0, 255, "opaque material color");
        assertPixel(1, 1, 0, 0, 0, "outside the quad coverage");
    }

    @Test
    void fourOverlappingRetainedSurfacesBlendFromBackToFront() {
        final TemplateBuffer quad = quadTemplate(0.75f);
        queue(quad, RED, 0, -0.6f);
        queue(quad, BLUE, 0, 0.2f);
        queue(quad, GREEN, 0, -0.2f);
        queue(quad, WHITE, 0, 0.6f);

        peelPendingBatch();

        assertPixel(CENTER, CENTER, 144, 80, 48, "white, blue, green and red from far to near");
        assertPixel(1, 1, 0, 0, 0, "outside the quad coverage");
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void opaqueDepthClipsOverlappingRetainedSurfacesPerPixel() {
        final TemplateBuffer translucentQuad = quadTemplate(0.75f);
        final TemplateBuffer opaqueQuad = quadTemplate(0.375f);
        queue(translucentQuad, RED, 0, -0.5f);
        queue(translucentQuad, GREEN, 0, 0.5f);
        queue(opaqueQuad, OPAQUE_BLUE, 0.375f, 0);

        peelPendingBatch();

        assertPixel(SIZE / 4, CENTER, 128, 64, 0, "green remains visible behind red without an opaque occluder");
        assertPixel(3 * SIZE / 4, CENTER, 128, 0, 127, "opaque blue hides green while red blends over blue");
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    private void peelPendingBatch() {
        BatchStateGuard.suspend();
        try {
            batcher.drawOpaque();
            assertTrue(batcher.beginPeel(), "the retained batch must contain translucent geometry");
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + DepthPeel.TEXTURE_UNIT);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, color);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + DepthPeel.SCENE_TEXTURE_UNIT);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, depth);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            TranslucentPeel.peel(depth, SIZE, SIZE, 0, batcher::drawPeeled);
            assertEquals(target.getId(), GLSMFramebuffer.getDrawFramebuffer());
            assertEquals(0, GLSMFramebuffer.getReadFramebuffer());
            assertEquals(0, GLSMTextures.getActiveTextureUnit());
            assertEquals(color, GLSMTextures.getTextures().getTextureUnitBindings(DepthPeel.TEXTURE_UNIT).getBinding());
            assertEquals(depth, GLSMTextures.getTextures().getTextureUnitBindings(DepthPeel.SCENE_TEXTURE_UNIT).getBinding());
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.getId());
        } finally {
            try {
                batcher.endPeel();
            } finally {
                BatchStateGuard.resume();
            }
        }
    }

    private void queue(TemplateBuffer quad, TesrMaterial material, float x, float z) {
        TranslucentPeel.beforeBlockEntity();
        GLSMMatrices.setModelViewMatrix(new Matrix4f().translation(x, 0, z));
        batcher.queue(quad, null, material);
        GLSMMatrices.glLoadIdentity();
    }

    private static TesrMaterial translucent(float red, float green, float blue) {
        return TesrMaterial.builder().stream().unlit().translucent().color(red, green, blue, HALF_ALPHA).build();
    }

    private static TemplateBuffer quadTemplate(float extent) {
        final int[] xs = {-1, 1, 1, -1, 1, -1};
        final int[] ys = {-1, -1, 1, -1, 1, 1};
        final int[] data = new int[xs.length * VERTEX_SIZE];
        for (int i = 0; i < xs.length; i++) {
            final int offset = i * VERTEX_SIZE;
            data[offset + POSITION_INDEX] = Float.floatToIntBits(xs[i] * extent);
            data[offset + POSITION_INDEX + 1] = Float.floatToIntBits(ys[i] * extent);
            data[offset + COLOR_INDEX] = -1;
        }
        return new TemplateBuffer(data, xs.length, GL11.GL_TRIANGLES);
    }

    private static int texture(int internalFormat, int format, int type) {
        final int texture = GLSMTextures.glGenTextures();
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, SIZE, SIZE, 0, format, type, (ByteBuffer) null);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }

    private static void assertPixel(int x, int y, int red, int green, int blue, String description) {
        final ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GLSMFramebuffer.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        assertEquals(red, Byte.toUnsignedInt(pixel.get(0)), 2, description + " (red)");
        assertEquals(green, Byte.toUnsignedInt(pixel.get(1)), 2, description + " (green)");
        assertEquals(blue, Byte.toUnsignedInt(pixel.get(2)), 2, description + " (blue)");
    }
}

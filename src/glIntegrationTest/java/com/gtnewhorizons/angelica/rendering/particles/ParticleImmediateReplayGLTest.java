package com.gtnewhorizons.angelica.rendering.particles;

import com.dhj.actinium.render.TessellatorDrawRouter;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMFog;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.hooks.DrawCallObserver;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.rendering.ParticleRunSplitter;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer;
import net.coderbot.iris.pipeline.TesrInstancingPipeline;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleImmediateReplayGLTest extends GLStateManagerIntegrationFixture {
    private static final int SIZE = 32;
    private DrawCallObserver previousObserver;
    private Target previousTarget;

    @BeforeEach
    void setup() {
        installDefaultGlsmConfig();
        GLStateManager.bindDisplayContext(new Object(), context);
        GLStateManager.recreateDefaultVertexArray();
        previousObserver = GLSMHooks.drawCallObserver;
        previousTarget = Target.fromDriver();
        ShaderManager.enable();
        ParticleInstancer.clear();
        TesrBatchRenderer.INSTANCE.instanceRing().delete();
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.disableTexture();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.disableTexture();
        GLSMLighting.disableLighting();
        GLSMFog.disableFog();
        GLSMBlend.disableBlend();
        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMRasterizer.disableScissorTest();
        GLSMRasterizer.disableCull();
        GLSMBlend.glClearColor(0, 0, 0, 1);
    }

    @AfterEach
    void restore() {
        GLSMHooks.drawCallObserver = previousObserver;
        previousTarget.bind();
        ParticleInstancer.clear();
        TesrBatchRenderer.INSTANCE.instanceRing().delete();
        ShaderManager.disable();
        assertEquals(GL11.GL_NO_ERROR, GL11C.glGetError());
    }

    @Test
    void missingVariantReplaysOnlyItsGroupBeforeTheCustomDraw() {
        try (ColorTarget layer = new ColorTarget(); ColorTarget read = new ColorTarget(); ColorTarget draw = new ColorTarget()) {
            final Target layerTarget = new Target(layer.fbo, layer.fbo, 0, 0, SIZE, SIZE);
            final Target customTarget = new Target(read.fbo, draw.fbo, 4, 6, 20, 16);
            final BufferBuilder run = new ParticleTestBuffer();
            final List<Submission> submissions = new ArrayList<>();
            GLSMHooks.drawCallObserver = flags -> submissions.add(new Submission(Target.fromDriver(),
                GLStateManager.ctx().ffpInstancing, run.getVertexCount()));

            layerTarget.bind();
            ParticleRunSplitter.beginParticles();
            try {
                begin(run);
                ParticleRunSplitter.beginRun();
                ParticleInstancer.beginLayer(new LayerPipeline(layerTarget));
                GLSMDepthStencilAlpha.glDepthMask(false);
                render(new Quad(-0.5F, 0xFF0000), run);
                GLSMDepthStencilAlpha.glDepthMask(true);
                render(new Quad(0.5F, 0x00FF00), run);
                assertTrue(submissions.isEmpty());
                assertEquals(0, run.getVertexCount());

                render(new Quad(-0.5F, 0x0000FF) {
                    @Override
                    public void renderParticle(BufferBuilder buffer, Entity entity, float ticks, float x, float xz,
                                               float z, float yz, float xy) {
                        customTarget.bind();
                        super.renderParticle(buffer, entity, ticks, x, xz, z, yz, xy);
                        assertTrue(TessellatorDrawRouter.draw(run, null));
                        begin(buffer);
                        writeQuad(buffer, 0.5F, 0xFFFF00);
                        assertTrue(TessellatorDrawRouter.draw(run, null));
                        begin(buffer);
                    }
                }, run);
                ParticleInstancer.endLayer();
                assertTrue(TessellatorDrawRouter.draw(run, null));
            } finally {
                ParticleRunSplitter.endParticles();
            }

            assertEquals(List.of(new Submission(layerTarget, Instancing.NONE, 4),
                new Submission(layerTarget, Instancing.PARTICLE, 4), new Submission(customTarget, Instancing.NONE, 4),
                new Submission(customTarget, Instancing.NONE, 4)), submissions);
            assertEquals(0xFF0000, layer.rgbAt(8, 16), "replayed group");
            assertEquals(0x00FF00, layer.rgbAt(24, 16), "instanced group");
            assertEquals(0x0000FF, draw.rgbAt(9, 14), "custom vertices already present at the first draw");
            assertEquals(0xFFFF00, draw.rgbAt(19, 14), "subsequent custom draw");
        }
    }

    private record Submission(Target target, Instancing mode, int sharedVertices) {}

    private record Target(int read, int draw, int x, int y, int width, int height) {
        static Target fromDriver() {
            int[] viewport = new int[4];
            GL11C.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            return new Target(GL11C.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                GL11C.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), viewport[0], viewport[1], viewport[2], viewport[3]);
        }

        void bind() {
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GLSMRasterizer.glViewport(x, y, width, height);
        }
    }

    /** The depth-writing material has an instance program; the other material uses its ordinary pass. */
    private record LayerPipeline(Target target) implements TesrInstancingPipeline {
        @Override
        public void rebindCurrentPass() {
            target.bind();
            GLSMPrograms.setFfpInstancing(Instancing.NONE);
        }

        @Override
        public boolean hasInstancedVariant(Instancing kind) {
            return kind == Instancing.PARTICLE && GLSMDepthStencilAlpha.isEffectiveDepthMaskEnabled();
        }

        @Override
        public void bindInstancedVariant(Instancing kind) {
            assertTrue(hasInstancedVariant(kind));
            GLSMPrograms.setFfpInstancing(kind);
        }
    }

    private static final class ColorTarget implements AutoCloseable {
        final int fbo = GLSMFramebuffer.glGenFramebuffers();
        final int color = GLSMFramebuffer.glGenRenderbuffers();

        ColorTarget() {
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GLSMFramebuffer.glBindRenderbuffer(GL30.GL_RENDERBUFFER, color);
            GLSMFramebuffer.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_RGBA8, SIZE, SIZE);
            GLSMFramebuffer.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER, color);
            assertEquals(GL30.GL_FRAMEBUFFER_COMPLETE, GLSMFramebuffer.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER));
            GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }

        int rgbAt(int x, int y) {
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
            ByteBuffer pixel = BufferUtils.createByteBuffer(4);
            GL11C.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            return pixel.get(0) << 16 & 0xFF0000 | pixel.get(1) << 8 & 0xFF00 | pixel.get(2) & 0xFF;
        }

        @Override
        public void close() {
            GLSMFramebuffer.glDeleteFramebuffers(fbo);
            GLSMFramebuffer.glDeleteRenderbuffers(color);
        }
    }

    private static void render(Particle particle, BufferBuilder run) {
        assertTrue(ParticleInstancer.renderParticle(particle, run, null, 0, 1, 1, 0, 0, 0));
    }

    private static void begin(BufferBuilder buffer) {
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
    }

    private static class Quad extends Particle {
        private final int rgb;

        Quad(float x, int rgb) {
            super(null, x, 0, 0);
            this.rgb = rgb;
        }

        @Override
        public void renderParticle(BufferBuilder buffer, Entity entity, float ticks, float x, float xz,
                                   float z, float yz, float xy) {
            writeQuad(buffer, (float) posX, rgb);
        }
    }

    private static void writeQuad(BufferBuilder buffer, float centerX, int rgb) {
        for (int corner = 0; corner < 4; corner++) {
            boolean left = corner < 2;
            boolean bottom = corner == 0 || corner == 3;
            buffer.pos(centerX + (left ? -0.2F : 0.2F), bottom ? -0.2F : 0.2F, 0)
                .tex(left ? 1 : 0, bottom ? 1 : 0).color(rgb >> 16, rgb >> 8 & 255, rgb & 255, 255)
                .lightmap(240, 240).endVertex();
        }
    }
}

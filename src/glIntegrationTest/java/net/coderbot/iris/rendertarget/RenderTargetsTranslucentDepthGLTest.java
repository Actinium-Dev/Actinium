package net.coderbot.iris.rendertarget;

import com.dhj.actinium.render.terrain.compile.VintageTranslucentMaterials;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import dhj.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import dhj.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import dhj.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import dhj.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import dhj.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import dhj.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import net.coderbot.iris.celeritas.IrisCeleritasChunkShaderInterface;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.texture.DepthBufferFormat;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.shaderpack.PackDirectivesFixtures;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.BlockRenderLayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLCapabilities;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Covers the depth copy the deferred pipeline takes before the translucent pass. Bloom compatibility layers call
 * {@code RenderGlobal.renderBlockLayer} for the translucent layer more than once in a pass, and every one of those calls
 * reaches {@code beginTranslucents}, so depthtex1 has to keep the depth captured by the first copy of a pass and take a
 * fresh copy once the next pass starts.
 */
class RenderTargetsTranslucentDepthGLTest extends GLStateManagerIntegrationFixture {
    private static final int SIZE = 8;
    private static final float OPAQUE_DEPTH = 0.25F;
    private static final float TRANSLUCENT_DEPTH = 0.75F;
    private static final float DEPTH_TOLERANCE = 1.0E-4F;

    @BeforeAll
    static void initializeGlsm() {
        installDefaultGlsmConfig();
        // The harness cannot resolve the LWJGL2 capability facade behind GLStateManager.capabilities, so the format
        // fallback RenderTargets consults has to be supplied directly.
        final GLCapabilities capabilities = GL.getCapabilities();
        RenderSystem.initializeGlslCapabilityForTesting(capabilities.OpenGL30 ? 130 : 120);
        RenderSystem.initializeFormatFallbackSupportForTesting(capabilities.OpenGL31, capabilities.OpenGL30);
    }

    /**
     * Runs the same sequence on both copy strategies: plain depth copies through {@code glCopyTexSubImage2D}, and the
     * combined depth-stencil blit the main framebuffer uses.
     */
    @ParameterizedTest
    @EnumSource(value = DepthBufferFormat.class, names = { "DEPTH24", "DEPTH24_STENCIL8" })
    void firstTranslucentDepthCopyHoldsUntilTheNextPass(DepthBufferFormat depthFormat) {
        final PackDirectives directives = PackDirectivesFixtures.colortex0();
        final DepthTexture sceneDepth = new DepthTexture(SIZE, SIZE, depthFormat);
        final RenderTargets targets = new RenderTargets(SIZE, SIZE, sceneDepth.getTextureId(), 0, depthFormat,
            directives.getRenderTargetDirectives().getRenderTargetSettings(), directives);
        final GlFramebuffer sceneFramebuffer = new GlFramebuffer();
        final GlFramebuffer probeFramebuffer = new GlFramebuffer();
        final float previousClearDepth = GL11.glGetFloat(GL11.GL_DEPTH_CLEAR_VALUE);
        try {
            sceneFramebuffer.addDepthAttachment(sceneDepth.getTextureId());
            sceneFramebuffer.noDrawBuffers();
            probeFramebuffer.addDepthAttachment(targets.getDepthTextureNoTranslucents().getTextureId());
            probeFramebuffer.noDrawBuffers();

            writeSceneDepth(sceneFramebuffer, OPAQUE_DEPTH);
            targets.copyPreTranslucentDepth();
            assertEquals(OPAQUE_DEPTH, readTranslucentDepth(probeFramebuffer), DEPTH_TOLERANCE,
                "the first copy captures the depth the opaque pass wrote");

            writeSceneDepth(sceneFramebuffer, TRANSLUCENT_DEPTH);
            targets.copyPreTranslucentDepth();
            assertEquals(OPAQUE_DEPTH, readTranslucentDepth(probeFramebuffer), DEPTH_TOLERANCE,
                "a repeated copy in the same pass keeps depthtex1");

            targets.resizeIfNeeded(1, sceneDepth.getTextureId(), SIZE, SIZE, depthFormat, directives);
            targets.copyPreTranslucentDepth();
            assertEquals(TRANSLUCENT_DEPTH, readTranslucentDepth(probeFramebuffer), DEPTH_TOLERANCE,
                "a recreated depth texture is copied even within the same pass");

            targets.onLevelRenderStart();
            targets.copyPreTranslucentDepth();
            assertEquals(TRANSLUCENT_DEPTH, readTranslucentDepth(probeFramebuffer), DEPTH_TOLERANCE,
                "the next pass copies the current depth again");
        } finally {
            GLSMDepthStencilAlpha.glClearDepth(previousClearDepth);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            probeFramebuffer.destroy();
            sceneFramebuffer.destroy();
            targets.destroy();
            sceneDepth.destroy();
        }
    }

    private static void writeSceneDepth(GlFramebuffer sceneFramebuffer, float depth) {
        sceneFramebuffer.bind();
        GLSMDepthStencilAlpha.glClearDepth(depth);
        GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }

    @Test
    void stainedGlassOverSkyWritesSceneDepthAndPreservesTheOpaqueSnapshot() {
        Bootstrap.register();
        BlockRenderLayer layer = BlockRenderLayer.TRANSLUCENT;
        Material translucent = material("translucent", TerrainRenderPass.Semantic.TRANSLUCENT, false);
        Material fluid = material("water", TerrainRenderPass.Semantic.WATER, true);
        var configuration = new RenderPassConfiguration<>(
                Map.of(layer, translucent), Map.of(layer, List.of(fluid.pass, translucent.pass)),
                translucent, translucent, translucent, fluid);

        PackDirectives directives = PackDirectivesFixtures.colortex0();
        DepthBufferFormat format = DepthBufferFormat.DEPTH24_STENCIL8;
        DepthTexture sceneDepth = new DepthTexture(SIZE, SIZE, format);
        RenderTargets targets = new RenderTargets(SIZE, SIZE, sceneDepth.getTextureId(), 0, format,
                directives.getRenderTargetDirectives().getRenderTargetSettings(), directives);
        GlFramebuffer sceneFramebuffer = new GlFramebuffer();
        GlFramebuffer snapshotFramebuffer = new GlFramebuffer();
        float previousClearDepth = GL11.glGetFloat(GL11.GL_DEPTH_CLEAR_VALUE);
        boolean previousDepthMask = GLStateManager.getDepthState().isMaskEnabled();
        try {
            sceneFramebuffer.addDepthAttachment(sceneDepth.getTextureId());
            sceneFramebuffer.noDrawBuffers();
            snapshotFramebuffer.addDepthAttachment(targets.getDepthTextureNoTranslucents().getTextureId());
            snapshotFramebuffer.noDrawBuffers();

            for (var glass : List.of(Blocks.STAINED_GLASS, Blocks.STAINED_GLASS_PANE)) {
                Material glassMaterial = VintageTranslucentMaterials.select(
                        configuration, layer, translucent, glass, false, true);
                assertSame(translucent, VintageTranslucentMaterials.select(
                        configuration, layer, translucent, glass, false, false));

                GLSMDepthStencilAlpha.glDepthMask(true);
                writeSceneDepth(sceneFramebuffer, 1.0F);
                targets.onLevelRenderStart();
                targets.copyPreTranslucentDepth();

                GLSMDepthStencilAlpha.glDepthMask(IrisCeleritasChunkShaderInterface.shouldWriteDepth(glassMaterial.pass, false));
                writeSceneDepth(sceneFramebuffer, TRANSLUCENT_DEPTH);

                assertEquals(TRANSLUCENT_DEPTH, readTranslucentDepth(sceneFramebuffer), DEPTH_TOLERANCE,
                        "depthtex0 must identify the glass pixel as a surface");
                assertEquals(1.0F, readTranslucentDepth(snapshotFramebuffer), DEPTH_TOLERANCE,
                        "depthtex1 must preserve the sky behind the glass");
            }

            assertSame(translucent, VintageTranslucentMaterials.select(
                    configuration, layer, translucent, Blocks.ICE, false, true));
            assertSame(fluid, VintageTranslucentMaterials.select(
                    configuration, layer, translucent, Blocks.WATER, true, false));
        } finally {
            GLSMDepthStencilAlpha.glDepthMask(previousDepthMask);
            GLSMDepthStencilAlpha.glClearDepth(previousClearDepth);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            snapshotFramebuffer.destroy();
            sceneFramebuffer.destroy();
            targets.destroy();
            sceneDepth.destroy();
        }
    }

    private static Material material(String name, TerrainRenderPass.Semantic semantic, boolean writesDepth) {
        TerrainRenderPass pass = TerrainRenderPass.builder()
                .name(name)
                .semantic(semantic)
                .writesDepth(writesDepth)
                .useReverseOrder(true)
                .primitiveType(QuadPrimitiveType.TRIANGULATED)
                .vertexType(ChunkMeshFormats.COMPACT)
                .build();
        return new Material(pass, AlphaCutoffParameter.ZERO, true);
    }

    private static float readTranslucentDepth(GlFramebuffer probeFramebuffer) {
        probeFramebuffer.bindAsReadBuffer();
        final FloatBuffer depth = BufferUtils.createFloatBuffer(1);
        GLSMFramebuffer.glReadPixels(0, 0, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
        return depth.get(0);
    }
}

package com.dhj.actinium.render;

import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndPortalCompositeGLTest extends GLStateManagerIntegrationFixture {
    @BeforeAll
    static void initializeGlsm() {
        installDefaultGlsmConfig();
        GLStateManager.bindDisplayContext(new Object(), context);
    }

    @Test
    void batchFlushDoesNotSendPortalCompositeIntoTheScene() {
        final int sceneFramebuffer = GLSMFramebuffer.glGenFramebuffers();
        final int sceneTexture = createTexture();
        final int portalSource = createTexture();
        final Runnable previousFlush = BatchStateGuard.flush;
        try {
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, sceneFramebuffer);
            attach(sceneTexture);
            GLSMRasterizer.glViewport(0, 0, 1, 1);
            GLSMBlend.glClearColor(1, 0, 0, 1);
            GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);

            final TextureManager textures = new TextureManager(null) {
                @Override
                public void bindTexture(ResourceLocation texture) {
                    GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, portalSource);
                }
            };
            final List<EndPortalLayers.Layer> layers = EndPortalLayers.create(16, 0);
            EndPortalCompositeRenderer.beginFrame();
            final int composite = EndPortalCompositeRenderer.texture(layers, textures);
            assertNotEquals(0, composite);
            attach(composite);
            GLSMBlend.glClearColor(0, 0, 0, 0);
            GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
            attach(sceneTexture);

            BatchStateGuard.flush = () -> {
                BatchStateGuard.flush = null;
                GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, sceneFramebuffer);
            };
            EndPortalCompositeRenderer.beginFrame();
            assertEquals(composite, EndPortalCompositeRenderer.texture(layers, textures));
            assertNull(BatchStateGuard.flush, "The pending batch must be flushed");
            assertEquals(sceneFramebuffer, GLSMFramebuffer.getDrawFramebuffer());
            final ByteBuffer pixel = BufferUtils.createByteBuffer(4);
            GLSMFramebuffer.glReadPixels(0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            assertEquals(255, Byte.toUnsignedInt(pixel.get(0)), "Portal precomposition overwrote the scene pixel");
            assertEquals(0, Byte.toUnsignedInt(pixel.get(1)));

            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, sceneFramebuffer);
            attach(composite);
            GLSMFramebuffer.glReadPixels(0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            assertTrue(Byte.toUnsignedInt(pixel.get(1)) > 0, "The portal texture must receive the composite");
        } finally {
            BatchStateGuard.flush = previousFlush;
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GLSMFramebuffer.glDeleteFramebuffers(sceneFramebuffer);
            EndPortalCompositeRenderer.destroy();
            GLSMTextures.glDeleteTextures(sceneTexture);
            GLSMTextures.glDeleteTextures(portalSource);
        }
    }

    private static int createTexture() {
        final int texture = GLSMTextures.glGenTextures();
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        final ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put((byte) 0).put((byte) 255).put((byte) 0).put((byte) 255).flip();
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        return texture;
    }

    private static void attach(int texture) {
        GLSMFramebuffer.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D, texture, 0);
        assertEquals(GL30.GL_FRAMEBUFFER_COMPLETE, GLSMFramebuffer.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER));
    }
}

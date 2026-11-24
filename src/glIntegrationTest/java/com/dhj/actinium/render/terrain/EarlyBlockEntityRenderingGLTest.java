package com.dhj.actinium.render.terrain;

import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarlyBlockEntityRenderingGLTest extends GLStateManagerIntegrationFixture {
    @BeforeAll
    static void initializeGlsm() {
        installDefaultGlsmConfig();
        GLStateManager.bindDisplayContext(new Object(), context);
    }

    @Test
    void earlyPassRestoresCallerLightmapCoordinatesAndDrawState() {
        int lightmapUnit = GL13.GL_TEXTURE1;
        int originalPass = MinecraftForgeClient.getRenderPass();
        float originalBlockLight = GLStateManager.getLastBrightnessX();
        float originalSkyLight = GLStateManager.getLastBrightnessY();
        boolean originalDepthMask = GLSMQueries.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            ForgeHooksClient.setRenderPass(0);
            GLSMDepthStencilAlpha.glDepthMask(false);
            GLSMCurrentAttribs.setLightmapTextureCoords(lightmapUnit, 32f, 176f);

            new EarlyBlockEntityRendering().render(lightmapUnit, () -> {}, () -> {
                assertEquals(1, MinecraftForgeClient.getRenderPass());
                assertTrue(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
                GLSMCurrentAttribs.setLightmapTextureCoords(lightmapUnit, 208f, 64f);
                assertEquals(208f, GLStateManager.getLastBrightnessX());
                assertEquals(64f, GLStateManager.getLastBrightnessY());
            });

            assertEquals(32f, GLStateManager.getLastBrightnessX());
            assertEquals(176f, GLStateManager.getLastBrightnessY());
            assertEquals(0, MinecraftForgeClient.getRenderPass());
            assertFalse(GLSMQueries.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
            assertFalse(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        } finally {
            ForgeHooksClient.setRenderPass(originalPass);
            GLSMCurrentAttribs.setLightmapTextureCoords(lightmapUnit, originalBlockLight, originalSkyLight);
            GLSMDepthStencilAlpha.glDepthMask(originalDepthMask);
        }
    }
}

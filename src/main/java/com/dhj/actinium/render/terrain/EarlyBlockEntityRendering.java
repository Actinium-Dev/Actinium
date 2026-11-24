package com.dhj.actinium.render.terrain;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/** Supplies the Forge pass 1 draw state at the translucent terrain boundary. */
final class EarlyBlockEntityRendering {
    private final Matrix4f textureMatrix = new Matrix4f();
    private final Matrix4f lightmapMatrix = new Matrix4f();

    void render(int lightmapTextureUnit, Runnable setupLighting, Runnable draw) {
        int previousPass = MinecraftForgeClient.getRenderPass();
        int previousMatrixMode = GLSMQueries.glGetInteger(GL11.GL_MATRIX_MODE);
        int drawFramebuffer = GLSMFramebuffer.getDrawFramebuffer();
        int readFramebuffer = GLSMFramebuffer.getReadFramebuffer();
        int program = GLSMPrograms.getActiveProgram();
        int lightmapUnit = lightmapTextureUnit - GL13.GL_TEXTURE0;
        float brightnessX = GLStateManager.getLastBrightnessX();
        float brightnessY = GLStateManager.getLastBrightnessY();
        textureMatrix.set(GLSMTextures.getTextures().getTextureUnitMatrix(0));
        lightmapMatrix.set(GLSMTextures.getTextures().getTextureUnitMatrix(lightmapUnit));

        TileEntityGlStateGuard.push();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glPushMatrix();
        try {
            ForgeHooksClient.setRenderPass(1);
            GLSMDepthStencilAlpha.glDepthMask(true);
            GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
            GLSMDepthStencilAlpha.enableDepthTest();
            GLSMDepthStencilAlpha.enableAlphaTest();
            GLSMBlend.enableBlend();
            GLSMBlend.defaultBlendFunc();
            GLSMRasterizer.glShadeModel(GL11.GL_FLAT);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMTextures.enableTexture();
            GLSMCurrentAttribs.glColor4f(1f, 1f, 1f, 1f);
            setupLighting.run();
            draw.run();
        } finally {
            ForgeHooksClient.setRenderPass(previousPass);
            try {
                GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
                GLSMMatrices.glPopMatrix();
                GLSMMatrices.setTextureMatrix(0, textureMatrix);
                GLSMMatrices.setTextureMatrix(lightmapUnit, lightmapMatrix);
                GLSMMatrices.glMatrixMode(previousMatrixMode);
                GLSMCurrentAttribs.setLightmapTextureCoords(lightmapTextureUnit, brightnessX, brightnessY);
                GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
                GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                GLSMPrograms.glUseProgram(program);
            } finally {
                TileEntityGlStateGuard.pop();
            }
        }
    }
}

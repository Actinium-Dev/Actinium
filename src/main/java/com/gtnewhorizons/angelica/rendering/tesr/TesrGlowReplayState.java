package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.AlphaState;
import com.gtnewhorizons.angelica.glsm.states.BlendState;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.glsm.states.ColorMask;
import com.gtnewhorizons.angelica.glsm.states.PolygonState;
import com.gtnewhorizons.angelica.glsm.states.ViewportState;
import net.minecraft.client.renderer.OpenGlHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/** Captures dispatcher entry state after its lightmap and render-pass setup. */
final class TesrGlowReplayState {
    private static final int[] CAPABILITIES = {GL11.GL_BLEND, GL11.GL_DEPTH_TEST, GL11.GL_ALPHA_TEST,
        GL11.GL_CULL_FACE, GL11.GL_POLYGON_OFFSET_FILL, GL11.GL_LIGHTING, GL11.GL_LIGHT0, GL11.GL_LIGHT1,
        GL11.GL_COLOR_MATERIAL, GL11.GL_NORMALIZE, GL12.GL_RESCALE_NORMAL};

    private final Transform transform = Transform.capture();
    private final Lightmap lightmap = Lightmap.capture();
    private final BlendState blend = GLSMBlend.getBlendState().copy();
    private final Color4 color = GLSMCurrentAttribs.getColor().copy();
    private final ColorMask mask = GLSMBlend.getColorMask().copy();
    private final AlphaState alpha = GLSMDepthStencilAlpha.getAlphaState().copy();
    private final PolygonState polygon = GLSMRasterizer.getPolygonState().copy();
    private final int depthFunc = GLSMDepthStencilAlpha.getDepthState().getFunc();
    private final boolean depthMask = GLSMDepthStencilAlpha.getDepthState().isMaskEnabled();
    private final int shadeModel = GLSMRasterizer.getShadeModelState().getValue();
    private final Texture diffuse = Texture.capture(0);
    private final Texture lightmapTexture = Texture.capture(OpenGlHelper.lightmapTexUnit - GL13.GL_TEXTURE0);
    private final int program = GLSMPrograms.getActiveProgram();
    private final int enabled = captureCapabilities();

    private TesrGlowReplayState() {}

    static TesrGlowReplayState capture() {
        return new TesrGlowReplayState();
    }

    void run(Runnable draw) {
        final Transform previousTransform = Transform.capture();
        final Lightmap previousLightmap = Lightmap.capture();
        final int previousProgram = GLSMPrograms.getActiveProgram();
        final int previousDrawFramebuffer = GLSMFramebuffer.getDrawFramebuffer();
        final int previousReadFramebuffer = GLSMFramebuffer.getReadFramebuffer();
        final ViewportState previousViewport = GLSMRasterizer.getViewportState().copy();
        GLSMAttribStack.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            apply();
            draw.run();
        } finally {
            try {
                GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
                GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
                GLSMRasterizer.glViewport(previousViewport.x, previousViewport.y, previousViewport.width, previousViewport.height);
                previousTransform.apply();
                previousLightmap.apply();
                GLSMPrograms.glUseProgram(previousProgram);
            } finally {
                GLSMAttribStack.glPopAttrib();
            }
        }
    }

    private void apply() {
        transform.apply();
        lightmap.apply();
        GLSMPrograms.glUseProgram(program);
        diffuse.apply(0);
        lightmapTexture.apply(OpenGlHelper.lightmapTexUnit - GL13.GL_TEXTURE0);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + transform.activeUnit());
        for (int i = 0; i < CAPABILITIES.length; i++) setEnabled(CAPABILITIES[i], (enabled & (1 << i)) != 0);
        GLSMBlend.glBlendFuncSeparate(blend.getSrcRgb(), blend.getDstRgb(), blend.getSrcAlpha(), blend.getDstAlpha());
        GLSMBlend.glBlendEquationSeparate(blend.getEquationRgb(), blend.getEquationAlpha());
        GLSMBlend.glBlendColor(blend.getBlendColorR(), blend.getBlendColorG(), blend.getBlendColorB(), blend.getBlendColorA());
        GLSMBlend.glColorMask(mask.red, mask.green, mask.blue, mask.alpha);
        GLSMCurrentAttribs.glColor4f(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        GLSMDepthStencilAlpha.glDepthFunc(depthFunc);
        GLSMDepthStencilAlpha.glDepthMask(depthMask);
        GLSMDepthStencilAlpha.glAlphaFunc(alpha.getFunction(), alpha.getReference());
        GLSMRasterizer.glCullFace(polygon.getCullFaceMode());
        GLSMRasterizer.glFrontFace(polygon.getFrontFace());
        GLSMRasterizer.glPolygonOffset(polygon.getOffsetFactor(), polygon.getOffsetUnits());
        GLSMRasterizer.glShadeModel(shadeModel);
    }

    private static int captureCapabilities() {
        int flags = 0;
        for (int i = 0; i < CAPABILITIES.length; i++) {
            if (GLSMEnable.glIsEnabled(CAPABILITIES[i])) flags |= 1 << i;
        }
        return flags;
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) GLSMEnable.glEnable(capability);
        else GLSMEnable.glDisable(capability);
    }

    private record Lightmap(float glsmX, float glsmY, float forgeX, float forgeY) {
        static Lightmap capture() {
            return new Lightmap(GLStateManager.getLastBrightnessX(), GLStateManager.getLastBrightnessY(),
                OpenGlHelper.lastBrightnessX, OpenGlHelper.lastBrightnessY);
        }

        void apply() {
            GLSMCurrentAttribs.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, glsmX, glsmY);
            OpenGlHelper.lastBrightnessX = forgeX;
            OpenGlHelper.lastBrightnessY = forgeY;
        }
    }

    private record Texture(int binding, boolean enabled) {
        static Texture capture(int unit) {
            return new Texture(GLSMTextures.getBoundTextureForServerState(unit),
                GLSMTextures.getTextures().getTextureUnitStates(unit).isEnabled());
        }

        void apply(int unit) {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, binding);
            setEnabled(GL11.GL_TEXTURE_2D, enabled);
        }
    }

    private record Transform(Matrix4f modelView, Matrix4f projection, Matrix4f diffuse, Matrix4f lightmap,
                             int matrixMode, int activeUnit) {
        static Transform capture() {
            final int lightmapUnit = OpenGlHelper.lightmapTexUnit - GL13.GL_TEXTURE0;
            return new Transform(new Matrix4f(GLSMMatrices.getModelViewMatrix()), new Matrix4f(GLSMMatrices.getProjectionMatrix()),
                new Matrix4f(GLSMTextures.getTextures().getTextureUnitMatrix(0)),
                new Matrix4f(GLSMTextures.getTextures().getTextureUnitMatrix(lightmapUnit)),
                GLSMMatrices.getMatrixMode().getMatrix(), GLSMTextures.getActiveTextureUnit());
        }

        void apply() {
            GLSMMatrices.setModelViewMatrix(modelView);
            GLSMMatrices.setProjectionMatrix(projection);
            GLSMMatrices.setTextureMatrix(0, diffuse);
            GLSMMatrices.setTextureMatrix(OpenGlHelper.lightmapTexUnit - GL13.GL_TEXTURE0, lightmap);
            GLSMMatrices.glMatrixMode(matrixMode);
        }
    }
}

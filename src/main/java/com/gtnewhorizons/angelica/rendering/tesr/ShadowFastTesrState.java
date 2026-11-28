package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLContextState;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.PipelineManager;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.function.Supplier;

/**
 * Reusable dispatcher-entry state for one depth of the shadow FastTESR call stack.
 * Callers must flush pending geometry before restoring, balance their own GL stacks and use GLSM
 * for state changes. Saved GL objects must stay alive; their storage, attachments and MRT routing
 * belong to their owners. A capture must end with restore on the same context in nesting order.
 */
final class ShadowFastTesrState {
    private static final double LIGHT_LENGTH = Math.sqrt(0.2 * 0.2 + 1.0 + 0.7 * 0.7);

    private final Supplier<WorldRenderingPipeline> pipelineSource;
    private final Matrix4f modelView = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f[] textureMatrices = new Matrix4f[GLStateManager.MAX_TEXTURE_UNITS];
    private GLContextState context;
    private WorldRenderingPipeline pipeline;
    private WorldRenderingPhase phase;
    private int attribDepth;
    private int clientDepth;
    private int drawFramebuffer;
    private int readFramebuffer;
    private int program;
    private float brightnessX;
    private float brightnessY;
    private float forgeBrightnessX;
    private float forgeBrightnessY;

    ShadowFastTesrState() {
        this(ShadowFastTesrState::currentPipeline);
    }

    /** Allows the GL harness to supply a pass without bootstrapping Minecraft's world renderer. */
    ShadowFastTesrState(Supplier<WorldRenderingPipeline> pipelineSource) {
        this.pipelineSource = pipelineSource;
        for (int unit = 0; unit < textureMatrices.length; unit++) textureMatrices[unit] = new Matrix4f();
    }

    /** Saves the caller before supplying the standard lighting used by the main-view TE loop. */
    void capture() {
        if (context != null) throw new IllegalStateException("Shadow FastTESR state is already captured");
        final GLContextState current = GLStateManager.ctx();
        if (current.clientAttribStackPointer == current.clientAttribSavedTextureUnit.length) {
            throw new IllegalStateException("Shadow FastTESR requires a free client attribute stack slot");
        }
        pipeline = pipelineSource.get();
        phase = pipeline == null ? null : pipeline.getPhase();
        drawFramebuffer = GLSMFramebuffer.getDrawFramebuffer();
        readFramebuffer = GLSMFramebuffer.getReadFramebuffer();
        program = GLSMPrograms.getActiveProgram();
        modelView.set(GLSMMatrices.getModelViewMatrix());
        projection.set(GLSMMatrices.getProjectionMatrix());
        for (int unit = 0; unit < textureMatrices.length; unit++) {
            textureMatrices[unit].set(GLSMTextures.getTextures().getTextureUnitMatrix(unit));
        }
        brightnessX = GLStateManager.getLastBrightnessX();
        brightnessY = GLStateManager.getLastBrightnessY();
        forgeBrightnessX = OpenGlHelper.lastBrightnessX;
        forgeBrightnessY = OpenGlHelper.lastBrightnessY;
        attribDepth = GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS));
        clientDepth = current.clientAttribStackPointer;
        GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        context = current;
        try {
            prepareLighting();
        } catch (RuntimeException | Error failure) {
            try {
                restore();
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    /** Restores entry state and keeps its saved layer open for Forge's atlas draw. */
    void restoreForAtlas() {
        requireTop();
        try {
            restoreEntry();
        } finally {
            GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS));
            GLSMAttribStack.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        }
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
    }

    /** Restores the caller after the atlas draw or an exceptional callback. */
    void restore() {
        requireTop();
        try {
            restoreEntry();
        } finally {
            context = null;
            pipeline = null;
            phase = null;
        }
    }

    private void restoreEntry() {
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        try {
            GLSMAttribStack.glPopClientAttrib();
        } finally {
            GLSMAttribStack.popState();
        }
        // Pass matching must see the restored texture and blend inputs. Its rebind can itself
        // change viewport and textures, so retain those restored attributes while rebinding.
        GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS));
        try {
            if (pipeline != null) {
                if (pipeline.getPhase() != phase) pipeline.setPhase(phase);
                pipeline.rebindCurrentPass();
            }
        } finally {
            try {
                GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
                GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                GLSMPrograms.glUseProgram(program);
                GLSMMatrices.setModelViewMatrix(modelView);
                GLSMMatrices.setProjectionMatrix(projection);
                for (int unit = 0; unit < textureMatrices.length; unit++) {
                    if (!textureMatrices[unit].equals(GLSMTextures.getTextures().getTextureUnitMatrix(unit))) {
                        GLSMMatrices.setTextureMatrix(unit, textureMatrices[unit]);
                    }
                }
                GLSMCurrentAttribs.setLightmapTextureCoords(GL13.GL_TEXTURE1, brightnessX, brightnessY);
                OpenGlHelper.lastBrightnessX = forgeBrightnessX;
                OpenGlHelper.lastBrightnessY = forgeBrightnessY;
            } finally {
                GLSMAttribStack.popState();
            }
        }
    }

    private void requireTop() {
        if (context == null || context != GLStateManager.ctx()
                || GLSMAttribStack.getAttribDepth() != attribDepth + 1
                || context.clientAttribStackPointer != clientDepth + 1) {
            throw new IllegalStateException("Shadow FastTESR state must be restored in nesting order with balanced GL stacks");
        }
    }

    private static WorldRenderingPipeline currentPipeline() {
        final PipelineManager manager = Iris.getPipelineManagerNullable();
        return manager == null ? null : manager.getPipelineNullable();
    }

    private static void prepareLighting() {
        // Match the standard lighting RenderGlobal supplies before its Forge batch.
        GLSMLighting.enableLighting();
        GLSMLighting.enableLight(0);
        GLSMLighting.enableLight(1);
        GLSMLighting.enableColorMaterial();
        GLSMLighting.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);
        for (int light = 0; light < 2; light++) {
            final double direction = light == 0 ? 1.0 : -1.0;
            GLSMLighting.glLight(GL11.GL_LIGHT0 + light, GL11.GL_POSITION,
                RenderHelper.setColorBuffer((float) (direction * 0.2 / LIGHT_LENGTH), (float) (1.0 / LIGHT_LENGTH),
                    (float) (-direction * 0.7 / LIGHT_LENGTH), 0));
            GLSMLighting.glLight(GL11.GL_LIGHT0 + light, GL11.GL_DIFFUSE, RenderHelper.setColorBuffer(0.6f, 0.6f, 0.6f, 1));
            GLSMLighting.glLight(GL11.GL_LIGHT0 + light, GL11.GL_AMBIENT, RenderHelper.setColorBuffer(0, 0, 0, 1));
            GLSMLighting.glLight(GL11.GL_LIGHT0 + light, GL11.GL_SPECULAR, RenderHelper.setColorBuffer(0, 0, 0, 1));
        }
        GLSMRasterizer.glShadeModel(GL11.GL_FLAT);
        GLSMLighting.glLightModel(GL11.GL_LIGHT_MODEL_AMBIENT, RenderHelper.setColorBuffer(0.4f, 0.4f, 0.4f, 1));
    }
}

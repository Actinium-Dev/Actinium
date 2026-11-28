package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import net.coderbot.iris.pipeline.FixedFunctionWorldRenderingPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.OpenGlHelper;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowFastTesrStateGLTest extends GLStateManagerIntegrationFixture {
    @BeforeEach
    void initialize() {
        installDefaultGlsmConfig();
        GLStateManager.markSplashComplete("ShadowFastTesrStateGLTest");
        GLStateManager.bindDisplayContext(new Object(), context);
        OpenGlHelper.lightmapTexUnit = GL13.GL_TEXTURE1;
    }

    @Test
    void restoresDrawTargetMaterialAndIdsBeforeAtlasAndDepthCopy() {
        final int shadow = target();
        final int foreign = target();
        final int model = texture(255, 0, 0);
        final int atlas = texture(0, 255, 0);
        final int program = program();
        final int vao = GLSMVertexInput.glGenVertexArrays();
        final Pass pass = new Pass();
        final ShadowFastTesrState state = new ShadowFastTesrState(() -> pass);
        try {
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, shadow);
            GLSMRasterizer.glViewport(0, 0, 8, 8);
            GLSMPrograms.glUseProgram(program);
            GLSMVertexInput.glBindVertexArray(vao);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, atlas);
            GLSMEnable.glDisable(GL11.GL_BLEND);
            GLSMEnable.glDisable(GL11.GL_CULL_FACE);
            GLSMEnable.glDisable(GL11.GL_SCISSOR_TEST);
            GLSMDepthStencilAlpha.enableDepthTest();
            GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LESS);
            GLSMDepthStencilAlpha.glDepthMask(true);
            GLSMDepthStencilAlpha.glClearDepth(1);
            GLSMBlend.glClearColor(0, 0, 0, 0);
            GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GLSMMatrices.setModelViewMatrix(new Matrix4f());
            GLSMMatrices.setProjectionMatrix(new Matrix4f());
            GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
            pass.phase = WorldRenderingPhase.BLOCK_ENTITIES;

            state.capture();
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, model);
            draw(program, 31, 0.5f, 1);
            assertPixel(255, 0, 31);

            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, foreign);
            GLSMRasterizer.glViewport(1, 1, 2, 2);
            GLSMPrograms.glUseProgram(0);
            GLSMVertexInput.glBindVertexArray(0);
            GLSMDepthStencilAlpha.glDepthMask(false);
            GLSMEnable.glEnable(GL11.GL_BLEND);
            GLSMMatrices.setModelViewMatrix(new Matrix4f().translation(3, 4, 5));
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE2);
            pass.phase = WorldRenderingPhase.ENTITIES;
            state.restoreForAtlas();

            assertEquals(shadow, GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
            assertEquals(program, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            assertEquals(vao, GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));
            assertEquals(atlas, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
            assertEquals(WorldRenderingPhase.BLOCK_ENTITIES, pass.phase);
            assertEquals(new Matrix4f(), GLSMMatrices.getModelViewMatrix());
            assertTrue(GLSMDepthStencilAlpha.getDepthState().isMaskEnabled());
            assertFalse(GLSMBlend.getBlendMode().isEnabled());
            GLSMEnable.glEnable(GL11.GL_BLEND);
            GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            draw(program, 47, 0, 0.5f);
            assertPixel(128, 128, 39);
            final float[] depth = new float[1];
            GL11.glReadPixels(4, 4, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
            assertEquals(0.5f, depth[0], 0.0001f);
            final int copy = GLSMTextures.glGenTextures();
            try {
                GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, copy);
                GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, 0, 0, 8, 8, 0);
                final float[] depths = new float[64];
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depths);
                assertEquals(0.5f, depths[36], 0.0001f);
            } finally {
                GLSMTextures.glDeleteTextures(copy);
            }
            state.restore();
            assertEquals(atlas, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
            assertFalse(GLSMBlend.getBlendMode().isEnabled());
            assertEquals(0, GLSMAttribStack.getAttribDepth());
        } finally {
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GLSMPrograms.glUseProgram(0);
            GLSMVertexInput.glBindVertexArray(0);
            GLSMVertexInput.glDeleteVertexArrays(vao);
            GLSMPrograms.glDeleteProgram(program);
            GLSMTextures.glDeleteTextures(model);
            GLSMTextures.glDeleteTextures(atlas);
            deleteTarget(shadow);
            deleteTarget(foreign);
            CapturedRenderingState.INSTANCE.setCurrentBlockEntity(0);
        }
    }

    @Test
    void nestedFailureRestoresParentMatricesBuffersAndLightmapAndAllowsReuse() {
        final ShadowFastTesrState parent = new ShadowFastTesrState(() -> null);
        final ShadowFastTesrState child = new ShadowFastTesrState(() -> null);
        final int vao = GLSMVertexInput.glGenVertexArrays();
        final int buffer = GLSMBuffers.glGenBuffers();
        final Matrix4f outer = new Matrix4f().translation(1, 2, 3);
        final Matrix4f projection = new Matrix4f().scaling(2, 3, 4);
        final Matrix4f texture = new Matrix4f().translation(0.25f, 0.5f, 0);
        try {
            GLSMVertexInput.glBindVertexArray(vao);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            GLSMMatrices.setModelViewMatrix(outer);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE2);
            GLSMMatrices.setProjectionMatrix(projection);
            GLSMMatrices.setTextureMatrix(2, texture);
            GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
            GLSMCurrentAttribs.setLightmapTextureCoords(GL13.GL_TEXTURE1, 16, 32);
            OpenGlHelper.lastBrightnessX = 48;
            parent.capture();
            final Matrix4f inner = new Matrix4f().translation(4, 5, 6);
            GLSMMatrices.setModelViewMatrix(inner);
            GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
            child.capture();
            final RuntimeException failure = new IllegalStateException("child renderer failed");
            assertSame(failure, assertThrows(IllegalStateException.class, () -> {
                try {
                    GLSMMatrices.setModelViewMatrix(new Matrix4f().zero());
                    GLSMMatrices.setProjectionMatrix(new Matrix4f().zero());
                    GLSMMatrices.setTextureMatrix(2, new Matrix4f().zero());
                    GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
                    GLSMVertexInput.glBindVertexArray(0);
                    GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                    GLSMCurrentAttribs.setLightmapTextureCoords(GL13.GL_TEXTURE1, 240, 240);
                    throw failure;
                } finally {
                    child.restore();
                }
            }));
            assertEquals(inner, GLSMMatrices.getModelViewMatrix());
            assertEquals(projection, GLSMMatrices.getProjectionMatrix());
            assertEquals(texture, GLSMTextures.getTextures().getTextureUnitMatrix(2));
            assertEquals(GL11.GL_PROJECTION, GLSMMatrices.getMatrixMode().getMode());
            assertEquals(vao, GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));
            assertEquals(buffer, GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING));
            assertEquals(16, GLStateManager.getLastBrightnessX());
            parent.restore();
            assertEquals(outer, GLSMMatrices.getModelViewMatrix());
            assertEquals(GL11.GL_TEXTURE, GLSMMatrices.getMatrixMode().getMode());
            assertEquals(2, GLSMTextures.getActiveTextureUnit());
            assertEquals(48, OpenGlHelper.lastBrightnessX);
            child.capture();
            child.restoreForAtlas();
            assertEquals(0, GLSMTextures.getActiveTextureUnit());
            assertEquals(GL11.GL_TEXTURE, GLSMMatrices.getMatrixMode().getMode());
            child.restore();
            assertEquals(2, GLSMTextures.getActiveTextureUnit());
            assertEquals(0, GLSMAttribStack.getAttribDepth());
        } finally {
            GLSMVertexInput.glBindVertexArray(0);
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLSMVertexInput.glDeleteVertexArrays(vao);
            GLSMBuffers.glDeleteBuffers(buffer);
        }
    }

    private static void draw(int program, int id, float z, float alpha) {
        CapturedRenderingState.INSTANCE.setCurrentBlockEntity(id);
        GLSMCurrentAttribs.glVertexAttrib4f(15, 0, id / 65535.0f, 0, 0);
        GLSMPrograms.glUniform1f(GL20.glGetUniformLocation(program, "z"), z);
        GLSMPrograms.glUniform1f(GL20.glGetUniformLocation(program, "alpha"), alpha);
        GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private static void assertPixel(int r, int g, int b) {
        final ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GLSMFramebuffer.glReadPixels(4, 4, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        assertEquals(r, Byte.toUnsignedInt(pixel.get(0)), 1);
        assertEquals(g, Byte.toUnsignedInt(pixel.get(1)), 1);
        assertEquals(b, Byte.toUnsignedInt(pixel.get(2)), 1);
    }

    private static int texture(int r, int g, int b) {
        final int texture = GLSMTextures.glGenTextures();
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        final ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put((byte) r).put((byte) g).put((byte) b).put((byte) 255).flip();
        GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        return texture;
    }

    private static int target() {
        final int fbo = GLSMFramebuffer.glGenFramebuffers();
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        for (int attachment : new int[] {GL30.GL_COLOR_ATTACHMENT0, GL30.GL_DEPTH_ATTACHMENT}) {
            final int buffer = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, buffer);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER,
                attachment == GL30.GL_DEPTH_ATTACHMENT ? GL14.GL_DEPTH_COMPONENT24 : GL11.GL_RGBA8, 8, 8);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, attachment, GL30.GL_RENDERBUFFER, buffer);
        }
        assertEquals(GL30.GL_FRAMEBUFFER_COMPLETE, GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER));
        return fbo;
    }

    private static void deleteTarget(int fbo) {
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        for (int attachment : new int[] {GL30.GL_COLOR_ATTACHMENT0, GL30.GL_DEPTH_ATTACHMENT}) {
            GL30.glDeleteRenderbuffers(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER,
                attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME));
        }
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GLSMFramebuffer.glDeleteFramebuffers(fbo);
    }

    private static int program() {
        final int program = GLSMPrograms.glCreateProgram();
        final String[] sources = {"""
            #version 330
            layout(location = 15) in vec4 iris_Entity;
            uniform float z;
            flat out float id;
            void main() {
                gl_Position = vec4(gl_VertexID == 1 ? 3.0 : -1.0, gl_VertexID == 2 ? 3.0 : -1.0, z, 1);
                id = round(iris_Entity.y * 65535.0);
            }
            """, """
            #version 330
            uniform sampler2D tex;
            uniform float alpha;
            flat in float id;
            out vec4 color;
            void main() { color = vec4(texture(tex, vec2(0.5)).rg, id / 255.0, alpha); }
            """};
        for (int i = 0; i < 2; i++) {
            final int shader = GLSMPrograms.glCreateShader(i == 0 ? GL20.GL_VERTEX_SHADER : GL20.GL_FRAGMENT_SHADER);
            GLSMPrograms.glShaderSource(shader, sources[i]);
            GLSMPrograms.glCompileShader(shader);
            assertEquals(GL11.GL_TRUE, GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS), GL20.glGetShaderInfoLog(shader));
            GLSMPrograms.glAttachShader(program, shader);
            GLSMPrograms.glDeleteShader(shader);
        }
        GLSMPrograms.glLinkProgram(program);
        assertEquals(GL11.GL_TRUE, GL20.glGetProgrami(program, GL20.GL_LINK_STATUS), GL20.glGetProgramInfoLog(program));
        return program;
    }

    private static final class Pass extends FixedFunctionWorldRenderingPipeline {
        private WorldRenderingPhase phase;

        @Override
        public WorldRenderingPhase getPhase() { return phase; }

        @Override
        public void setPhase(WorldRenderingPhase phase) { this.phase = phase; }

        @Override
        public void rebindCurrentPass() {
            GLSMRasterizer.glViewport(0, 0, 1, 1);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE2);
        }
    }
}

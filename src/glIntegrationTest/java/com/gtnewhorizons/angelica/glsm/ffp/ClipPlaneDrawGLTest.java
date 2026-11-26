package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_CreateContext;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_DestroyContext;
import static org.lwjgl.sdl.SDLVideo.SDL_GL_MakeCurrent;

class ClipPlaneDrawGLTest extends GLStateManagerIntegrationFixture {
    @ParameterizedTest(name = "native clip distances = {0}")
    @ValueSource(booleans = {true, false})
    void displayListAndAttributeStackRestoreTheClippedHalf(boolean nativeDistances) {
        installDefaultGlsmConfig();
        int driverLimit = GL11.glGetInteger(GL30.GL_MAX_CLIP_DISTANCES);
        assumeTrue(!nativeDistances || driverLimit >= GLStateManager.MAX_CLIP_PLANES);
        GLStateManager.bindDisplayContext(new Object(), context);
        RenderSystem.initializeClipDistanceLimitForTesting(nativeDistances ? driverLimit : 4);
        GLStateManager.recreateDefaultVertexArray();
        ShaderManager.enable();
        int vao = GL30.glGenVertexArrays();
        int vbo = GL15.glGenBuffers();
        int list = GLSMDraws.glGenLists(1);
        int plane = GL11.GL_CLIP_PLANE0 + GLStateManager.MAX_CLIP_PLANES - 1;
        int bottomPlane = GL11.GL_CLIP_PLANE0;
        try {
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, new float[] {-1, -1, 3, -1, -1, 3}, GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0L);
            GL11.glViewport(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
            GLSMEnable.glDisable(GL11.GL_TEXTURE_2D);
            GLSMEnable.glDisable(GL11.GL_DEPTH_TEST);
            GLSMEnable.glDisable(GL11.GL_CULL_FACE);
            GLSMCurrentAttribs.glColor4f(1, 0, 0, 1);
            DoubleBuffer equation = BufferUtils.createDoubleBuffer(4);
            equation.put(new double[] {1, 0, 0, 0}).flip();

            GLSMDraws.glNewList(list, GL11.GL_COMPILE);
            GLSMRasterizer.glClipPlane(plane, equation);
            GLSMEnable.glEnable(plane);
            GLSMDraws.glEndList();
            assertCoverage(true, true);
            GLSMDraws.glCallList(list);
            assertCoverage(false, true);
            assertTrue(GLSMEnable.glIsEnabled(plane));
            assertEquals(GLStateManager.MAX_CLIP_PLANES, GLSMQueries.glGetInteger(GL11.GL_MAX_CLIP_PLANES));
            if (driverLimit >= GLStateManager.MAX_CLIP_PLANES) {
                assertEquals(nativeDistances, GL11.glIsEnabled(plane));
            }
            assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());

            equation.put(0, 0).put(1, 1);
            GLSMRasterizer.glClipPlane(bottomPlane, equation);
            assertCoverage(false, true);
            GLSMEnable.glEnable(bottomPlane);
            assertCoverage(false, false, false, true);
            GLSMEnable.glDisable(bottomPlane);
            assertCoverage(false, true);

            GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT);
            try {
                GLSMEnable.glDisable(plane);
                assertCoverage(true, true);
            } finally {
                GLSMAttribStack.glPopAttrib();
            }
            assertCoverage(false, true);
            GLSMAttribStack.glPushAttrib(GL11.GL_TRANSFORM_BIT);
            try {
                equation.put(0, -1).put(1, 0);
                GLSMRasterizer.glClipPlane(plane, equation);
                assertCoverage(true, false);
            } finally {
                GLSMAttribStack.glPopAttrib();
            }
            assertCoverage(false, true);
            GLSMEnable.glDisable(plane);
            assertCoverage(true, true);
        } finally {
            GLSMEnable.glDisable(bottomPlane);
            GLSMEnable.glDisable(plane);
            GLSMDraws.glDeleteLists(list, 1);
            ShaderManager.getInstance().deactivate();
            ShaderManager.disable();
            GL20.glUseProgram(0);
            GL30.glBindVertexArray(0);
            GL15.glDeleteBuffers(vbo);
            GL30.glDeleteVertexArrays(vao);
            RenderSystem.resetClipDistanceLimitForTesting();
        }
    }

    @Test
    void drawableContextKeepsItsOwnClipStateAndCapability() {
        installDefaultGlsmConfig();
        Object displayToken = new Object();
        Object drawableToken = new Object();
        GLStateManager.bindDisplayContext(displayToken, context);
        GLStateManager.recreateDefaultVertexArray();
        int plane = GL11.GL_CLIP_PLANE0;
        DoubleBuffer equation = BufferUtils.createDoubleBuffer(4).put(0, 1);
        long drawable = SDL_GL_CreateContext(window);
        assertNotEquals(0L, drawable);
        try {
            assertTrue(SDL_GL_MakeCurrent(window, context));
            GL.createCapabilities();
            RenderSystem.initializeClipDistanceLimitForTesting(4);
            GLSMRasterizer.glClipPlane(plane, equation);
            GLSMEnable.glEnable(plane);
            assertTrue(GLSMRasterizer.clipEmulationActive());

            assertTrue(SDL_GL_MakeCurrent(window, drawable));
            GL.createCapabilities();
            GLStateManager.bindDrawableContext(drawableToken, drawable);
            assertEquals(GL11.glGetInteger(GL30.GL_MAX_CLIP_DISTANCES) >= GLStateManager.MAX_CLIP_PLANES,
                RenderSystem.hasClipCullDistance());
            assertFalse(GLSMEnable.glIsEnabled(plane));
            equation.put(0, -1);
            GLSMRasterizer.glClipPlane(plane, equation);

            assertTrue(SDL_GL_MakeCurrent(window, context));
            GL.createCapabilities();
            GLStateManager.bindDisplayContext(displayToken, context);
            assertTrue(GLSMRasterizer.clipEmulationActive());
            GLSMRasterizer.glGetClipPlane(plane, equation);
            assertEquals(1.0, equation.get(0));
        } finally {
            assertTrue(SDL_GL_MakeCurrent(window, context));
            GL.createCapabilities();
            GLStateManager.bindDisplayContext(displayToken, context);
            GLSMEnable.glDisable(plane);
            RenderSystem.resetClipDistanceLimitForTesting();
            GLStateManager.retireDrawableContext(drawableToken);
            assertTrue(SDL_GL_DestroyContext(drawable));
        }
    }

    private static void assertCoverage(boolean left, boolean right) {
        assertCoverage(left, right, left, right);
    }

    private static void assertCoverage(boolean bottomLeft, boolean bottomRight, boolean topLeft, boolean topRight) {
        GL11.glClearColor(0, 0, 0, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        ShaderManager.getInstance().preDraw(0);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        ByteBuffer pixels = BufferUtils.createByteBuffer(WINDOW_WIDTH * WINDOW_HEIGHT * 4);
        GL11.glReadPixels(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        for (int y = 0; y < WINDOW_HEIGHT; y++) {
            boolean left = y < WINDOW_HEIGHT / 2 ? bottomLeft : topLeft;
            boolean right = y < WINDOW_HEIGHT / 2 ? bottomRight : topRight;
            for (int x = 0; x < WINDOW_WIDTH; x++) {
                boolean expected = x < WINDOW_WIDTH / 2 ? left : right;
                boolean actual = pixels.get((y * WINDOW_WIDTH + x) * 4) != 0;
                if (expected != actual) assertEquals(expected, actual, "pixel (" + x + ", " + y + ")");
            }
        }
    }
}

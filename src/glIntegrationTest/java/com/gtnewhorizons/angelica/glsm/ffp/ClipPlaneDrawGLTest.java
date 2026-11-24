package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClipPlaneDrawGLTest extends GLStateManagerIntegrationFixture {
    @ParameterizedTest(name = "native clip distances = {0}")
    @ValueSource(booleans = {true, false})
    void displayListAndAttributeStackRestoreTheClippedHalf(boolean nativeDistances) {
        installDefaultGlsmConfig();
        GLStateManager.bindDisplayContext(new Object(), context);
        GLStateManager.recreateDefaultVertexArray();
        boolean previous = RenderSystem.hasClipCullDistance();
        RenderSystem.initializeClipCullDistanceSupportForTesting(nativeDistances);
        ShaderManager.enable();
        int vao = GL30.glGenVertexArrays();
        int vbo = GL15.glGenBuffers();
        int list = GLSMDraws.glGenLists(1);
        int plane = GL11.GL_CLIP_PLANE0 + 7;
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
            assertEquals(nativeDistances, GL11.glIsEnabled(plane));

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
                equation.put(0, -1);
                GLSMRasterizer.glClipPlane(plane, equation);
                assertCoverage(true, false);
            } finally {
                GLSMAttribStack.glPopAttrib();
            }
            assertCoverage(false, true);
            GLSMEnable.glDisable(plane);
            assertCoverage(true, true);
        } finally {
            GLSMEnable.glDisable(plane);
            GLSMDraws.glDeleteLists(list, 1);
            ShaderManager.getInstance().deactivate();
            ShaderManager.disable();
            GL20.glUseProgram(0);
            GL30.glBindVertexArray(0);
            GL15.glDeleteBuffers(vbo);
            GL30.glDeleteVertexArrays(vao);
            RenderSystem.initializeClipCullDistanceSupportForTesting(previous);
        }
    }

    private static void assertCoverage(boolean left, boolean right) {
        GL11.glClearColor(0, 0, 0, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        ShaderManager.getInstance().preDraw(0);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        ByteBuffer pixels = BufferUtils.createByteBuffer(WINDOW_WIDTH * 4);
        GL11.glReadPixels(0, WINDOW_HEIGHT / 2, WINDOW_WIDTH, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        for (int x = 0; x < WINDOW_WIDTH; x++) {
            assertEquals(x < WINDOW_WIDTH / 2 ? left : right, pixels.get(x * 4) != 0, "x = " + x);
        }
    }
}

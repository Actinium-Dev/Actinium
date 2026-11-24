package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.DepthEcho;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfpCoverageDiscardGLTest extends GLStateManagerIntegrationFixture {
    @Test
    void echoAndReplayApplyCoverageOnlyToDepthSubmissions() {
        installDefaultGlsmConfig();
        GLStateManager.bindDisplayContext(new Object(), context);
        GLStateManager.recreateDefaultVertexArray();
        ShaderManager.enable();
        final ShaderManager ffp = ShaderManager.getInstance();
        final int vao = GL30.glGenVertexArrays();
        final int vbo = GL15.glGenBuffers();
        final int query = GL15.glGenQueries();
        final int echoTarget = GL30.glGenFramebuffers();
        try {
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, new float[] {-1, -1, 3, -1, -1, 3}, GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0L);
            GL11.glViewport(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
            GLSMEnable.glDisable(GL11.GL_TEXTURE_2D);
            GLSMEnable.glDisable(GL11.GL_ALPHA_TEST);
            GLSMEnable.glDisable(GL11.GL_DEPTH_TEST);
            GLSMEnable.glDisable(GL11.GL_CULL_FACE);
            GLSMEnable.glEnable(GL11.GL_BLEND);
            GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GLSMCurrentAttribs.glColor4f(1, 0, 0, 0);
            assertTrue(drawHasCoverage(ffp, query), "ordinary FFP keeps the unmodified fragments");
            final int ordinaryProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            GLSMEnable.glEnable(GL11.GL_DEPTH_TEST);
            GLSMDepthStencilAlpha.glDepthMask(false);
            DepthEcho.arm(echoTarget, 0);
            DepthEcho.beginTracking();
            assertTrue(drawHasCoverage(ffp, query), "a tracked color draw keeps fragments below the coverage threshold");
            final int echoProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            assertNotEquals(ordinaryProgram, echoProgram);
            DepthEcho.endTracking();

            assertFalse(submitHasCoverage(query, () -> ffp.withDepthEchoCoverage(FfpCoverageDiscardGLTest::drawTriangle)),
                "the same fragments are discarded in the immediate depth submission");
            assertTrue(submitHasCoverage(query, FfpCoverageDiscardGLTest::drawTriangle),
                "the next native draw keeps the original coverage without a new preDraw");
            assertThrows(IllegalStateException.class, () -> ffp.withDepthEchoCoverage(() -> {
                throw new IllegalStateException("failed native submission");
            }));
            assertTrue(submitHasCoverage(query, FfpCoverageDiscardGLTest::drawTriangle),
                "a failed native draw also restores original coverage");

            DepthEcho.beginReplay();
            assertFalse(drawHasCoverage(ffp, query), "alpha blending discards zero alpha");
            final int replayProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            assertNotEquals(ordinaryProgram, replayProgram);

            GLSMBlend.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
            assertTrue(drawHasCoverage(ffp, query), "unweighted additive blending keeps nonzero RGB");
            assertEquals(replayProgram, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
            GLSMCurrentAttribs.glColor4f(0, 0, 0, 1);
            assertFalse(drawHasCoverage(ffp, query), "unweighted additive blending discards black");

            GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GLSMCurrentAttribs.glColor4f(1, 0, 0, 0);
            assertFalse(drawHasCoverage(ffp, query), "weighted additive blending discards zero alpha");
            GLSMCurrentAttribs.glColor4f(0.05f, 0, 0, 0.05f);
            assertFalse(drawHasCoverage(ffp, query), "the weighted contribution can be subpixel even when both inputs pass individually");
            GLSMCurrentAttribs.glColor4f(1, 0, 0, 0.5f);
            assertTrue(drawHasCoverage(ffp, query), "weighted additive blending keeps visible fragments");

            GLSMEnable.glDisable(GL11.GL_BLEND);
            GLSMCurrentAttribs.glColor4f(0, 0, 0, 0);
            assertTrue(drawHasCoverage(ffp, query), "disabled blending switches off coverage discard");
            assertEquals(replayProgram, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));

            DepthEcho.disarm();
            assertTrue(drawHasCoverage(ffp, query));
            assertEquals(ordinaryProgram, GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        } finally {
            DepthEcho.disarm();
            GLSMDepthStencilAlpha.glDepthMask(true);
            ffp.deactivate();
            ShaderManager.disable();
            GL20.glUseProgram(0);
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL15.glDeleteQueries(query);
            GL15.glDeleteBuffers(vbo);
            GL30.glDeleteVertexArrays(vao);
            GL30.glDeleteFramebuffers(echoTarget);
        }
    }

    private static boolean drawHasCoverage(ShaderManager ffp, int query) {
        ffp.preDraw(0);
        return submitHasCoverage(query, FfpCoverageDiscardGLTest::drawTriangle);
    }

    private static boolean submitHasCoverage(int query, Runnable draw) {
        GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, query);
        try {
            draw.run();
        } finally {
            GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);
        }
        return GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT) > 0;
    }

    private static void drawTriangle() {
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }
}

package com.gtnewhorizons.angelica.lwjgl3;

import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class IndexedMultidrawProbeGLTest extends GLStateManagerIntegrationFixture {
    @Test
    void detectsIndirectDrawsAndRestoresBufferAndFramebufferState() {
        var caps = GL.getCapabilities();
        assumeTrue((caps.OpenGL43 || caps.GL_ARB_multi_draw_indirect) && caps.glMultiDrawElementsIndirect != 0);

        int read = GL30C.glGenFramebuffers();
        int draw = GL30C.glGenFramebuffers();
        int previousRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissorWasEnabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        try {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, read);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, draw);
            GL11C.glEnable(GL11C.GL_SCISSOR_TEST);

            assertTrue(IndexedMultidrawProbe.run(new IndirectMultidraw.Dispatch(caps.glMultiDrawElementsIndirect, true)));
            assertEquals(read, GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING));
            assertEquals(draw, GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING));
            assertTrue(GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST));
            assertEquals(GL11C.GL_NO_ERROR, GL11C.glGetError());
        } finally {
            if (!scissorWasEnabled) GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousRead);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL30C.glDeleteFramebuffers(read);
            GL30C.glDeleteFramebuffers(draw);
        }
    }
}

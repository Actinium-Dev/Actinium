package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.DepthEcho;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.tileentity.TileEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TesrGlowReplayGLTest extends GLStateManagerIntegrationFixture {
    @BeforeAll
    static void initializeGlsm() {
        installDefaultGlsmConfig();
        GLStateManager.bindDisplayContext(new Object(), context);
    }

    @Test
    void replayRestoresEachDispatchersLightmapAndCallerStateAfterFailure() {
        final int originalUnit = OpenGlHelper.lightmapTexUnit;
        final var original = Light.capture();
        OpenGlHelper.lightmapTexUnit = GL13.GL_TEXTURE1;
        try {
            final var dim = new Light(16, 32, 48, 32);
            final var bright = new Light(208, 224, 208, 224);
            final var caller = new Light(80, 96, 128, 144);
            dim.apply();
            GLSMDepthStencilAlpha.glDepthMask(false);
            final TesrGlowReplayState first = TesrGlowReplayState.capture();
            bright.apply();
            GLSMDepthStencilAlpha.glDepthMask(true);
            final TesrGlowReplayState second = TesrGlowReplayState.capture();
            caller.apply();

            first.run(() -> {
                assertEquals(dim, Light.capture());
                assertFalse(GLSMDepthStencilAlpha.getDepthState().isMaskEnabled());
            });
            assertEquals(caller, Light.capture());
            assertTrue(GLSMDepthStencilAlpha.getDepthState().isMaskEnabled());
            final class DrawFailure extends RuntimeException {}
            assertThrows(DrawFailure.class, () -> second.run(() -> {
                assertEquals(bright, Light.capture());
                assertTrue(GLSMDepthStencilAlpha.getDepthState().isMaskEnabled());
                GLSMDepthStencilAlpha.glDepthMask(false);
                throw new DrawFailure();
            }));
            assertEquals(caller, Light.capture());
            assertTrue(GLSMDepthStencilAlpha.getDepthState().isMaskEnabled());
        } finally {
            original.apply();
            OpenGlHelper.lightmapTexUnit = originalUnit;
        }
    }

    @Test
    void nextReplayStartsOnTheGlowTargetAfterACustomFramebufferLeaks() {
        final int oldUnit = OpenGlHelper.lightmapTexUnit;
        final int oldDraw = GLSMFramebuffer.getDrawFramebuffer();
        final int oldRead = GLSMFramebuffer.getReadFramebuffer();
        final var oldViewport = GLSMRasterizer.getViewportState().copy();
        final int glow = GLSMFramebuffer.glGenFramebuffers();
        final int read = GLSMFramebuffer.glGenFramebuffers();
        final int custom = GLSMFramebuffer.glGenFramebuffers();
        OpenGlHelper.lightmapTexUnit = GL13.GL_TEXTURE1;
        try {
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, glow);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GLSMRasterizer.glViewport(1, 2, WINDOW_WIDTH - 2, WINDOW_HEIGHT - 4);
            final var expectedViewport = GLSMRasterizer.getViewportState().copy();
            final TesrGlowReplayState state = TesrGlowReplayState.capture();
            final class DrawFailure extends RuntimeException {}
            assertThrows(DrawFailure.class, () -> state.run(() -> {
                GLSMFramebuffer.glBindFramebuffer(GL30.GL_FRAMEBUFFER, custom);
                GLSMRasterizer.glViewport(0, 0, 8, 8);
                throw new DrawFailure();
            }));
            state.run(() -> {
                assertEquals(glow, GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
                assertEquals(read, GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING));
                final int[] viewport = new int[4];
                GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                assertEquals(List.of(expectedViewport.x, expectedViewport.y, expectedViewport.width, expectedViewport.height),
                    List.of(viewport[0], viewport[1], viewport[2], viewport[3]));
            });
        } finally {
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
            GLSMRasterizer.glViewport(oldViewport.x, oldViewport.y, oldViewport.width, oldViewport.height);
            GLSMFramebuffer.glDeleteFramebuffers(custom);
            GLSMFramebuffer.glDeleteFramebuffers(read);
            GLSMFramebuffer.glDeleteFramebuffers(glow);
            OpenGlHelper.lightmapTexUnit = oldUnit;
        }
    }

    @Test
    void fastAndPendingGeometryEchoImmediatelyWithoutClaimingTheTrackedRenderer() {
        final int source = GLSMFramebuffer.glGenFramebuffers();
        final int glow = GLSMFramebuffer.glGenFramebuffers();
        final List<Integer> targets = new ArrayList<>();
        final Runnable draw = () -> DepthEcho.submit(() -> targets.add(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)));
        try {
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, source);
            GLSMEnable.glEnable(GL11.GL_BLEND);
            GLSMEnable.glEnable(GL11.GL_DEPTH_TEST);
            GLSMDepthStencilAlpha.glDepthMask(false);
            GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
            DepthEcho.arm(glow, source);
            DepthEcho.beginTracking();
            TesrGlowReplay.drawImmediate(draw);
            PendingTesrDraws.hold(new PendingTesrDraws.Pending() {
                @Override
                public boolean canWaitFor(TileEntity next) { return false; }

                @Override
                public void flush() { draw.run(); }
            });
            PendingTesrDraws.flush();
            assertFalse(DepthEcho.endTracking());
            assertEquals(List.of(source, glow, source, glow), targets);
            assertTrue(DepthEcho.takeEchoed());

            DepthEcho.beginTracking();
            draw.run();
            assertTrue(DepthEcho.endTracking());
            assertEquals(List.of(source, glow, source, glow, source), targets);
        } finally {
            DepthEcho.disarm();
            DepthEcho.takeEchoed();
            GLSMDepthStencilAlpha.glDepthMask(true);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            GLSMFramebuffer.glDeleteFramebuffers(source);
            GLSMFramebuffer.glDeleteFramebuffers(glow);
        }
    }

    private record Light(float glsmX, float glsmY, float forgeX, float forgeY) {
        static Light capture() {
            return new Light(GLStateManager.getLastBrightnessX(), GLStateManager.getLastBrightnessY(),
                OpenGlHelper.lastBrightnessX, OpenGlHelper.lastBrightnessY);
        }

        void apply() {
            GLSMCurrentAttribs.setLightmapTextureCoords(GL13.GL_TEXTURE1, glsmX, glsmY);
            OpenGlHelper.lastBrightnessX = forgeX;
            OpenGlHelper.lastBrightnessY = forgeY;
        }
    }
}

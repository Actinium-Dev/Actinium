package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.DeferredBlendHandler;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudCacheBlendOverrideIntegrationTest extends GLStateManagerIntegrationFixture {
    @BeforeEach
    void resetBlend() {
        GLStateManager.setHudCacheOverride(false);
        GLStateManager.tryBlendFuncSeparate(
            GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    @AfterEach
    void resetOverrides() {
        GLSMBlend.setLayerBlending(false);
        GLSMBlend.setHudCacheOverride(false);
    }

    @Test
    void overrideChangesDriverFactorsWithoutChangingRequestedCache() {
        GLStateManager.setHudCacheOverride(true);
        assertCachedFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        assertCachedFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GLStateManager.tryBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
        assertCachedFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
        assertDriverFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GLStateManager.setHudCacheOverride(false);
        assertDriverFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
    }

    @Test
    void popUnderOverrideRestoresRequestedFactorsAndReappliesOverride() {
        final int depth = GLStateManager.pushState(StateSet.BLEND);
        GLStateManager.setHudCacheOverride(true);
        GLSMBlend.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        GLStateManager.popStateTo(depth);

        assertCachedFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GLStateManager.setHudCacheOverride(false);
        assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    @Test
    void overrideForcesSourceAlphaEvenWhenDestinationAlphaAlreadyMatches() {
        GLStateManager.setHudCacheOverride(true);
        GLStateManager.tryBlendFuncSeparate(
            GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_ALPHA);

        assertCachedFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_ALPHA);
        assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Test
    void layerAccumulatesCoverageAndAdditiveDrawsPreserveIt() {
        GLSMBlend.setLayerBlending(true);
        assertTrue(GLSMBlend.isLayerBlending());
        assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        final int depth = GLStateManager.pushState(StateSet.BLEND);
        GLSMBlend.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
        assertCachedFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GLStateManager.popStateTo(depth);
        assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_COLOR, GL11.GL_ONE, GL11.GL_SRC_ALPHA, GL11.GL_ZERO);
        GLSMBlend.setHudCacheOverride(true);
        assertDriverFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
        GLSMBlend.setLayerBlending(false);
        assertDriverFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GLSMBlend.setHudCacheOverride(false);
        assertDriverFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE, GL11.GL_SRC_ALPHA, GL11.GL_ZERO);
    }

    @Test
    void shaderLockKeepsDriverFactorsUntilTheSameRequestedFactorsAreReapplied() {
        final DeferredBlendHandler previous = GLSMHooks.blendHandler;
        final var retainedOverride = new DeferredBlendHandler() {
            boolean locked = true;

            @Override public boolean isOverrideHeld() { return true; }
            @Override public boolean isBlendLocked() { return locked; }
            @Override public void deferBlendModeToggle(boolean enabled) { throw new AssertionError(); }
            @Override public void deferBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) { throw new AssertionError(); }
            @Override public void flushDeferredBlend() { throw new AssertionError(); }
        };
        GLSMHooks.blendHandler = retainedOverride;
        try {
            GLSMBlend.setLayerBlending(true);
            assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            retainedOverride.locked = false;
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            assertDriverFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } finally {
            GLSMHooks.blendHandler = previous;
        }
    }

    private static void assertDriverFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        assertEquals(srcRgb, GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB));
        assertEquals(dstRgb, GL11.glGetInteger(GL14.GL_BLEND_DST_RGB));
        assertEquals(srcAlpha, GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA));
        assertEquals(dstAlpha, GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
    }

    private static void assertCachedFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        assertEquals(srcRgb, GLSMQueries.glGetInteger(GL14.GL_BLEND_SRC_RGB));
        assertEquals(dstRgb, GLSMQueries.glGetInteger(GL14.GL_BLEND_DST_RGB));
        assertEquals(srcAlpha, GLSMQueries.glGetInteger(GL14.GL_BLEND_SRC_ALPHA));
        assertEquals(dstAlpha, GLSMQueries.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
    }
}

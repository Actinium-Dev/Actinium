package com.gtnewhorizons.angelica.rendering.tesr;

import net.minecraft.client.renderer.BufferBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowTesrDrawsTest {
    @Test
    void directUploadIsLimitedToTheInnermostForgeBuilderAndCallback() {
        final BufferBuilder parent = new BufferBuilder(256);
        final BufferBuilder child = new BufferBuilder(256);
        final BufferBuilder model = new BufferBuilder(256);
        final IllegalStateException failure = new IllegalStateException("child upload failed");

        assertFalse(ShadowTesrDraws.isForgeBatch(null));
        assertFalse(ShadowTesrDraws.isForgeBatch(parent));
        ShadowTesrDraws.drawForgeBatch(parent, () -> {
            assertTrue(ShadowTesrDraws.isForgeBatch(parent));
            assertFalse(ShadowTesrDraws.isForgeBatch(model));
            assertSame(failure, assertThrows(IllegalStateException.class,
                () -> ShadowTesrDraws.drawForgeBatch(child, () -> {
                    assertTrue(ShadowTesrDraws.isForgeBatch(child));
                    assertFalse(ShadowTesrDraws.isForgeBatch(parent));
                    assertFalse(ShadowTesrDraws.isForgeBatch(model));
                    throw failure;
                })));
            assertTrue(ShadowTesrDraws.isForgeBatch(parent));
            assertFalse(ShadowTesrDraws.isForgeBatch(child));
        });
        assertFalse(ShadowTesrDraws.isForgeBatch(parent));
        assertFalse(ShadowTesrDraws.isForgeBatch(child));
    }

    @Test
    void shadowDispatchDisablesDeferralAndRestoresItAfterFailure() {
        final IllegalStateException failure = new IllegalStateException("tile failed");
        PendingTesrDraws.beginPass();
        try {
            assertTrue(PendingTesrDraws.canDefer());
            assertSame(failure, assertThrows(IllegalStateException.class, () -> ShadowFastTesr.dispatch(() -> {
                assertFalse(PendingTesrDraws.canDefer());
                throw failure;
            })));
            assertFalse(ShadowFastTesr.active());
            assertTrue(PendingTesrDraws.canDefer());
        } finally {
            PendingTesrDraws.endPass();
        }
        assertFalse(PendingTesrDraws.canDefer());
    }
}

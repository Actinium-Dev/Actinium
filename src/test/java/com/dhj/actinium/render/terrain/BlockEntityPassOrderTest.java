package com.dhj.actinium.render.terrain;

import com.dhj.actinium.render.RenderWorldRecursionGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockEntityPassOrderTest {
    @Test
    void preparedViewCanDrawEarlyOnlyOnceAndSkipsLateDraw() {
        BlockEntityPassOrder order = new BlockEntityPassOrder();
        RenderWorldRecursionGuard.enter();
        try {
            long view = RenderWorldRecursionGuard.outerPassId();
            order.prepare(view);

            assertTrue(order.claimEarlyDraw(view));
            assertFalse(order.claimEarlyDraw(view));
            assertFalse(order.shouldDrawLate(view));
        } finally {
            RenderWorldRecursionGuard.exit();
        }
    }

    @Test
    void unclaimedEarlyDrawStaysLateAndCannotBeClaimedAfterward() {
        BlockEntityPassOrder order = new BlockEntityPassOrder();
        RenderWorldRecursionGuard.enter();
        try {
            long view = RenderWorldRecursionGuard.outerPassId();
            order.prepare(view);

            assertTrue(order.shouldDrawLate(view));
            assertFalse(order.claimEarlyDraw(view));
        } finally {
            RenderWorldRecursionGuard.exit();
        }
    }

    @Test
    void nestedAndUntrackedDrawsDoNotConsumePreparedOuterView() {
        BlockEntityPassOrder order = new BlockEntityPassOrder();
        long untrackedView = RenderWorldRecursionGuard.outerPassId();
        RenderWorldRecursionGuard.enter();
        try {
            long outerView = RenderWorldRecursionGuard.outerPassId();
            order.prepare(outerView);

            assertFalse(order.claimEarlyDraw(untrackedView));
            assertTrue(order.shouldDrawLate(untrackedView));

            RenderWorldRecursionGuard.enter();
            try {
                long nestedView = RenderWorldRecursionGuard.outerPassId();
                assertFalse(order.claimEarlyDraw(nestedView));
                assertTrue(order.shouldDrawLate(nestedView));
            } finally {
                RenderWorldRecursionGuard.exit();
            }

            assertTrue(order.claimEarlyDraw(RenderWorldRecursionGuard.outerPassId()));
            assertFalse(order.shouldDrawLate(outerView));
            assertTrue(order.shouldDrawLate(untrackedView));
        } finally {
            RenderWorldRecursionGuard.exit();
        }
    }

    @Test
    void nextOuterViewDoesNotInheritAnEarlyDraw() {
        BlockEntityPassOrder order = new BlockEntityPassOrder();
        long firstView;
        RenderWorldRecursionGuard.enter();
        try {
            firstView = RenderWorldRecursionGuard.outerPassId();
            order.prepare(firstView);
            assertTrue(order.claimEarlyDraw(firstView));
        } finally {
            RenderWorldRecursionGuard.exit();
        }

        RenderWorldRecursionGuard.enter();
        try {
            long nextView = RenderWorldRecursionGuard.outerPassId();
            assertNotEquals(firstView, nextView);
            assertFalse(order.claimEarlyDraw(nextView));
            assertTrue(order.shouldDrawLate(nextView));
        } finally {
            RenderWorldRecursionGuard.exit();
        }
    }
}

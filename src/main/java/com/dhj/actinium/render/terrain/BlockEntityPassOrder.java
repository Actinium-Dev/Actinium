package com.dhj.actinium.render.terrain;

/** Tracks ownership of the chunk TESR pass for one world renderer. */
final class BlockEntityPassOrder {
    private long preparedView;
    private long drawnEarlyView;

    void prepare(long view) {
        preparedView = view;
    }

    boolean claimEarlyDraw(long view) {
        if (view == 0 || preparedView != view || drawnEarlyView == view) {
            return false;
        }
        preparedView = 0;
        drawnEarlyView = view;
        return true;
    }

    boolean shouldDrawLate(long view) {
        if (view == 0) {
            return true;
        }
        if (preparedView == view) {
            preparedView = 0;
        }
        return drawnEarlyView != view;
    }
}

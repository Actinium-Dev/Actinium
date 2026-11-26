package com.dhj.actinium.texture;

import java.util.List;
import java.util.function.BiFunction;

/** Access to sprite pixels during mipmap generation. */
public final class SpriteMipPixels {
    private SpriteMipPixels() { }

    /**
     * Visits each non-null base mip in list order and passes the returned state to the next visit.
     * The editor may change pixel values in place. Its array is valid for the duration of the
     * call, including nested reads, and must not be retained. Edits are visible to mipmap
     * generation and to other frames that share that level. The list must not be restructured
     * during traversal. Frame-storage integrations can specialize this entry point.
     */
    public static <S> S editBaseLevels(List<int[][]> frames, S state, BiFunction<S, int[], S> editor) {
        for (int[][] frame : frames) {
            if (frame != null && frame.length != 0 && frame[0] != null) {
                state = editor.apply(state, frame[0]);
            }
        }
        return state;
    }
}

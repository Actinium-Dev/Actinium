package com.dhj.actinium.texture;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpriteMipPixelsTest {
    @Test
    void editsOnlyPresentBaseLevelsInFrameOrderAndPreservesAliases() {
        int[] shared = {10};
        int[] higher = {100};
        List<int[][]> frames = Arrays.asList(null, new int[0][], new int[][]{null, higher},
                new int[][]{shared, higher}, new int[][]{shared});

        int result = SpriteMipPixels.editBaseLevels(frames, 1, (state, pixels) -> pixels[0] += state);

        assertEquals(22, result);
        assertEquals(22, shared[0]);
        assertEquals(100, higher[0]);
    }
}

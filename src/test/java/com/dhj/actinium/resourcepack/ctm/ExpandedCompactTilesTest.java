package com.dhj.actinium.resourcepack.ctm;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ExpandedCompactTilesTest {
    @Test
    void usesMatchingQuadrantsFromTheRequestedAnimationFrame() {
        int[][] frameColors = {
                {0xff010101, 0xff030303}, {0xff111111, 0xff131313}, {0xff212121, 0xff232323},
                {0xff313131, 0xff333333}, {0xff414141, 0xff434343}
        };
        var sources = new BufferedImage[frameColors.length];
        for (int tile = 0; tile < sources.length; tile++) {
            sources[tile] = new BufferedImage(2, 4, BufferedImage.TYPE_INT_ARGB);
            for (int frame = 0; frame < 2; frame++) {
                int[] pixels = new int[4];
                Arrays.fill(pixels, frameColors[tile][frame]);
                sources[tile].setRGB(0, frame * 2, 2, 2, pixels, 0, 2);
            }
        }
        assertArrayEquals(new int[]{0xff030303, 0xff333333, 0xff232323, 0xff434343},
                ExpandedCompactTiles.compose(sources, 4, 1));
    }
}

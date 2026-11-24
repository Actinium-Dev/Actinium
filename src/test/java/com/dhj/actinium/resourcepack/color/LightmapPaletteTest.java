package com.dhj.actinium.resourcepack.color;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LightmapPaletteTest {
    @Test
    void interpolatesSunAndTorchIndependently() {
        var image = new BufferedImage(2, 32, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 9, 0x200000);
        image.setRGB(1, 9, 0x600000);
        image.setRGB(0, 16 + 2, 0x002000);
        image.setRGB(1, 16 + 2, 0x006000);
        int[] result = new int[256];
        new LightmapPalette(image).render(result, 0.5f, 1, 0, 0);
        assertEquals(0xff406000, result[9 * 16 + 2]);
        assertEquals(0xff000000, result[2 * 16 + 9]);
    }

    @Test
    void blendsNightVisionRowsAndHandlesAnUnlitPalette() {
        var image = new BufferedImage(1, 64, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 33, 0x0000fe);
        int[] result = new int[256];
        new LightmapPalette(image).render(result, 0, 0, 0.5f, 0);
        assertEquals(0xff00007f, result[16]);
        solid(32, 0).render(result, 0, 0, 1, 1);
        assertEquals(0xff000000, result[16]);
        assertThrows(IllegalArgumentException.class, () -> solid(48, 0));
    }

    private static LightmapPalette solid(int height, int color) {
        var image = new BufferedImage(1, height, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < 16; row++) image.setRGB(0, row, color);
        return new LightmapPalette(image);
    }
}

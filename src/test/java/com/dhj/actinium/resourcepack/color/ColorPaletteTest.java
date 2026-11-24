package com.dhj.actinium.resourcepack.color;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ColorPaletteTest {
    @Test
    void gridUsesBiomeAndAdjustedHeightAndKeepsItsOwnPixels() {
        var image = new BufferedImage(3, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(2, 1, 0x129af0);
        image.setRGB(2, 3, 0xff8700);
        var palette = new ColorPalette(ColorPalette.Format.GRID, image, -1, 0, 60);
        image.setRGB(2, 1, 0x00ff00);
        assertEquals(0x129af0, palette.sample(2, 0, 0, 0, 61, 0));
        assertEquals(0xff8700, palette.sample(20, 0, 0, 0, 90, 0));
    }

    @Test
    void vanillaSamplingUsesClimateInsteadOfCoordinatesOrBiomeId() {
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x20a800);
        image.setRGB(0, 3, 0xd0a800);
        var palette = new ColorPalette(ColorPalette.Format.VANILLA, image, -1, 0, 0);
        int dry = palette.sample(9, 1, 0, 7, 123, 14);
        assertNotEquals(dry, palette.sample(9, 1, 1, 7, 123, 14));
        assertEquals(dry, palette.sample(1, 1, 0, -9, 99, -2));
    }

    @Test
    void vanillaDefaultUsesTheImageCenterWithoutClimateSampling() {
        var image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        image.setRGB(128, 128, 0x147ca5);
        image.setRGB(127, 191, 0xc73310);
        var palette = new ColorPalette(ColorPalette.Format.VANILLA, image, -1, 0, 0);
        assertEquals(0x147ca5, palette.defaultColor());
        assertEquals(0xc73310, palette.sample(1, 0.5f, 0.5f, 0, 64, 0));
    }

    @Test
    void gridDefaultUsesThePlainsHeightSampleWithTheConfiguredOffset() {
        var image = new BufferedImage(4, 128, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 60, 0xe33a22);
        var palette = new ColorPalette(ColorPalette.Format.GRID, image, -1, 0, 4);
        assertEquals(0xe33a22, palette.defaultColor());
    }

    @Test
    void explicitDefaultColorDoesNotReplaceWorldSamples() {
        var image = new BufferedImage(4, 128, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 64, 0x1234de);
        image.setRGB(0, 0, 0x23a007);
        var grid = new ColorPalette(ColorPalette.Format.GRID, image, 0, 0, 0);
        var vanilla = new ColorPalette(ColorPalette.Format.VANILLA, image, 0, 0, 0);
        assertEquals(0, grid.defaultColor());
        assertEquals(0, vanilla.defaultColor());
        assertEquals(0x1234de, grid.sample(1, 1, 1, 0, 64, 0));
        assertEquals(0x23a007, vanilla.sample(1, 1, 1, 0, 64, 0));
    }
}

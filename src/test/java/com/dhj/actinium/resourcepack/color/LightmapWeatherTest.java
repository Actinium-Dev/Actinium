package com.dhj.actinium.resourcepack.color;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LightmapWeatherTest {
    @Test
    void weatherBlendUndoesTheWorldRainScalingOfThunder() {
        var weather = new LightmapPalette.Weather(palette(0xff0000), palette(0x00ff00), palette(0x0000ff));
        var pixels = new int[256];

        weather.render(pixels, 1, 0, 0, 0, 0.5f, 0.125f);
        for (int color : pixels) assertEquals(0xff804040, color);

        weather.render(pixels, 1, 0, 0, 0, 0.5f, 0.25f);
        for (int color : pixels) assertEquals(0xff800080, color);
    }

    @Test
    void thunderAboveRainWeightOmitsRainAndKeepsChannelsSeparate() {
        var weather = new LightmapPalette.Weather(palette(0x0000ff), palette(0x00ff00), palette(0xff00ff));
        var pixels = new int[256];

        weather.render(pixels, 1, 0, 0, 0, 0.5f, 0.375f);
        for (int color : pixels) assertEquals(0xffbf00ff, color);
    }

    private static LightmapPalette palette(int rgb) {
        var image = new BufferedImage(2, 32, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 2; x++) {
            for (int sky = 0; sky < 16; sky++) image.setRGB(x, sky, rgb);
        }
        return new LightmapPalette(image);
    }
}

package com.dhj.actinium.resourcepack.color;

import java.awt.image.BufferedImage;

/** Immutable sunlight and torchlight ramps, optionally including night vision. */
final class LightmapPalette {
    private final int width;
    private final boolean hasNightVision;
    private final int[] pixels;

    LightmapPalette(BufferedImage image) {
        width = image.getWidth();
        int height = image.getHeight();
        if (height != 32 && height != 64) {
            throw new IllegalArgumentException("Lightmap height must be 32 or 64 pixels");
        }
        hasNightVision = height == 64;
        pixels = image.getRGB(0, 0, width, height, null, 0, width);
    }

    private float channel(int row, float column, int shift) {
        float x = clamp(column) * (width - 1);
        int left = (int) x;
        int start = row * width;
        return lerp((pixels[start + left] >> shift) & 255,
                (pixels[start + Math.min(left + 1, width - 1)] >> shift) & 255, x - left) / 255;
    }

    private float mix(int sky, int block, float sun, float flicker, int shift, int rowOffset) {
        return clamp(channel(sky + rowOffset, sun, shift) + channel(block + rowOffset + 16, flicker, shift));
    }

    void render(int[] out, float sun, float flicker, float nightVision, float gamma) {
        for (int sky = 0; sky < 16; sky++) {
            for (int block = 0; block < 16; block++) {
                float r = mix(sky, block, sun, flicker, 16, 0);
                float g = mix(sky, block, sun, flicker, 8, 0);
                float b = mix(sky, block, sun, flicker, 0, 0);
                if (nightVision > 0) {
                    if (hasNightVision) {
                        r = lerp(r, mix(sky, block, sun, flicker, 16, 32), nightVision);
                        g = lerp(g, mix(sky, block, sun, flicker, 8, 32), nightVision);
                        b = lerp(b, mix(sky, block, sun, flicker, 0, 32), nightVision);
                    } else {
                        float max = Math.max(r, Math.max(g, b));
                        if (max > 0) {
                            float scale = lerp(1, 1 / max, nightVision);
                            r *= scale;
                            g *= scale;
                            b *= scale;
                        }
                    }
                }
                out[sky * 16 + block] = 0xff000000 | gamma(r, gamma) << 16 | gamma(g, gamma) << 8 | gamma(b, gamma);
            }
        }
    }

    private static int gamma(float value, float gamma) {
        float inverse = 1 - value;
        return (int) (255 * clamp(lerp(value, 1 - inverse * inverse * inverse * inverse, gamma)));
    }

    private static float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }

    private static float lerp(float from, float to, float delta) {
        return from + (to - from) * delta;
    }

    record Weather(LightmapPalette clear, LightmapPalette rain, LightmapPalette thunder) {
        void render(int[] out, float sun, float torch, float vision, float gamma, float rainStrength, float thunderStrength) {
            clear.render(out, sun, torch, vision, gamma);
            if (rainStrength <= 0 || (rain == null && thunder == null)) return;
            // Undo the rain scaling applied by World#getThunderStrength before weighting the weather ramps.
            float thunderWeight = thunderStrength / rainStrength;
            float rainWeight = Math.max(0, rainStrength - thunderWeight);
            LightmapPalette wet = rain == null ? clear : rain;
            LightmapPalette storm = thunder == null ? wet : thunder;
            int[] rainPixels = new int[out.length];
            int[] thunderPixels = new int[out.length];
            wet.render(rainPixels, sun, torch, vision, gamma);
            storm.render(thunderPixels, sun, torch, vision, gamma);
            for (int i = 0; i < out.length; i++) {
                int color = 0xff000000;
                for (int shift = 0; shift < 24; shift += 8) {
                    int channel = Math.round(((out[i] >> shift) & 255) * (1 - rainStrength)
                            + ((rainPixels[i] >> shift) & 255) * rainWeight
                            + ((thunderPixels[i] >> shift) & 255) * thunderWeight);
                    color |= Math.min(255, channel) << shift;
                }
                out[i] = color;
            }
        }
    }
}

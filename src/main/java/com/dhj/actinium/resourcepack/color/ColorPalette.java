package com.dhj.actinium.resourcepack.color;

import java.awt.image.BufferedImage;

/** Samples an immutable resource-pack colormap. */
final class ColorPalette {
    enum Format { VANILLA, GRID, FIXED }

    private final Format format;
    private final int width, height, variance, offset, defaultColor;
    private final int[] pixels;

    ColorPalette(Format format, BufferedImage image, int color, int variance, int offset) {
        this.format = format;
        this.variance = variance;
        this.offset = offset;
        width = image == null ? 0 : image.getWidth();
        height = image == null ? 0 : image.getHeight();
        pixels = image == null ? null : image.getRGB(0, 0, width, height, null, 0, width);
        defaultColor = color != -1 ? color : switch (format) {
            case FIXED -> 0xffffff;
            case GRID -> sample(1, 0, 0, 0, 64, 0);
            case VANILLA -> pixel(width / 2, height / 2);
        };
    }

    int sample(int biome, float temperature, float rainfall, int x, int y, int z) {
        return switch (format) {
            case FIXED -> defaultColor;
            case GRID -> {
                int noise = 0;
                if (variance > 0) {
                    int hash = x * 73428767 ^ z * 912931;
                    hash ^= hash >>> 16;
                    noise = (int) Math.floorMod((long) hash, 2L * variance + 1) - variance;
                }
                yield pixel(biome, (long) y - offset + noise);
            }
            case VANILLA -> {
                float t = Math.max(0, Math.min(1, temperature));
                float wet = Math.max(0, Math.min(1, rainfall)) * t;
                yield pixel((int) ((1 - t) * (width - 1)), (int) ((1 - wet) * (height - 1)));
            }
        };
    }

    int pixel(int x, long y) {
        return pixels[(int) Math.max(0, Math.min(height - 1, y)) * width + Math.max(0, Math.min(width - 1, x))] & 0xffffff;
    }

    int indexed(int index) {
        if (pixels == null) return defaultColor;
        return pixels[Math.max(0, Math.min(pixels.length - 1, index))] & 0xffffff;
    }

    int size() {
        return pixels == null ? 1 : pixels.length;
    }

    int defaultColor() {
        return defaultColor;
    }
}

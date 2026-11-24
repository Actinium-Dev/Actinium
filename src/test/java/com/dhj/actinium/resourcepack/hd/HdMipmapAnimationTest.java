package com.dhj.actinium.resourcepack.hd;

import com.dhj.actinium.resourcepack.hd.HdMipmapTextures.AnimatedImage;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HdMipmapAnimationTest {
    @Test
    void offsetAnimationUploadsEverySampledLevel() {
        var target = new Target(32, 32, 5, 0xff202020);
        int before = target.uploaded[5][0];

        target.paint(1, 0, 8, 8, 0xffffffff);

        assertEquals(List.of(new Region(0, 1, 0, 8, 8), new Region(1, 0, 0, 5, 4), new Region(2, 0, 0, 3, 2),
            new Region(3, 0, 0, 2, 1), new Region(4, 0, 0, 1, 1), new Region(5, 0, 0, 1, 1)), target.regions);
        assertNotEquals(before, target.uploaded[5][0]);
        target.assertMatchesFullRebuild();
    }

    @Test
    void adjacentAnimationsShareCoarseTexelsAndRetainTheLatestFrames() {
        var target = new Target(24, 16, 3, 0xff000000);

        target.paint(17, 9, 3, 7, 0xffff0000);
        target.paint(20, 9, 4, 7, 0xff0000ff);
        target.assertMatchesFullRebuild();
        target.paint(17, 9, 3, 7, 0xff00ff00);

        assertEquals(new Region(3, 2, 1, 1, 1), target.regions.getLast());
        target.assertMatchesFullRebuild();
    }

    @Test
    void replacementImageSamplesItsOwnBasePixels() {
        var previous = new Target(8, 8, 3, 0xff202020);
        previous.paint(1, 1, 3, 3, 0xffff0000);

        var replacement = new Target(8, 8, 3, 0xff606060);
        replacement.paint(2, 2, 1, 1, 0xff0000ff);

        replacement.assertMatchesFullRebuild();
        assertEquals(0xff606060, replacement.uploaded[0][9]);
    }

    private record Region(int level, int x, int y, int width, int height) {}

    private static final class Target {
        private final BufferedImage expectedBase;
        private final int[][] uploaded;
        private final AnimatedImage image;
        private final List<Region> regions = new ArrayList<>();

        Target(int width, int height, int levels, int color) {
            expectedBase = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            int[] base = pixels(width * height, color);
            expectedBase.setRGB(0, 0, width, height, base, 0, width);
            uploaded = HdMipmapTextures.generateMipmaps(base.clone(), width, levels);
            image = new AnimatedImage(width, HdMipmapTextures.generateMipmaps(base, width, levels));
        }

        void paint(int x, int y, int width, int height, int color) {
            int[] frame = pixels(width * height, color);
            expectedBase.setRGB(x, y, width, height, frame, 0, width);
            image.update(x, y, width, height, frame, this::upload);
        }

        private void upload(int level, int x, int y, int width, int height, int[] colors, int stride) {
            regions.add(new Region(level, x, y, width, height));
            for (int row = y; row < y + height; row++) {
                int offset = row * stride + x;
                System.arraycopy(colors, offset, uploaded[level], offset, width);
            }
        }

        void assertMatchesFullRebuild() {
            int width = expectedBase.getWidth();
            int[] base = expectedBase.getRGB(0, 0, width, expectedBase.getHeight(), null, 0, width);
            int[][] expected = HdMipmapTextures.generateMipmaps(base, width, uploaded.length - 1);
            for (int level = 0; level < expected.length; level++) {
                assertArrayEquals(expected[level], uploaded[level], "Level " + level);
            }
        }

        private static int[] pixels(int count, int color) {
            int[] pixels = new int[count];
            Arrays.fill(pixels, color);
            return pixels;
        }
    }
}

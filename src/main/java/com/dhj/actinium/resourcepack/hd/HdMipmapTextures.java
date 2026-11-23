package com.dhj.actinium.resourcepack.hd;

import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.image.BufferedImage;
import java.nio.IntBuffer;

/** Uploads complete mip chains and animation patches through the active render backend. */
public final class HdMipmapTextures {
    private HdMipmapTextures() {}

    /** Uploads all levels and retains their CPU pixels only when animation rules target the image. */
    public static AnimatedImage uploadImage(int id, BufferedImage image, boolean blur, boolean clamp, int levels,
                                            boolean animated) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        int[][] chain = generateMipmaps(pixels, width, levels);
        TextureUtil.allocateTextureImpl(id, levels, width, height);
        TextureUtil.uploadTextureMipmap(chain, width, height, 0, 0, blur, clamp);
        return animated ? new AnimatedImage(width, chain) : null;
    }

    static int[][] generateMipmaps(int[] pixels, int width, int levels) {
        int[][] data = new int[levels + 1][];
        data[0] = pixels;
        for (int level = 1; level <= levels; level++) {
            int childWidth = width >> 1;
            int[] child = new int[data[level - 1].length / 4];
            downsample(data[level - 1], child, childWidth, 0, 0, childWidth, child.length / childWidth);
            data[level] = child;
            width = childWidth;
        }
        return data;
    }

    private static void downsample(int[] parent, int[] child, int stride, int x, int y, int width, int height) {
        int parentStride = stride << 1;
        for (int row = y; row < y + height; row++) {
            int dest = row * stride + x;
            int source = (dest << 1) + row * parentStride;
            for (int end = dest + width; dest < end; dest++, source += 2) {
                child[dest] = blend(parent[source], parent[source + 1], parent[source + parentStride],
                    parent[source + parentStride + 1]);
            }
        }
    }

    private static int blend(int first, int second, int third, int fourth) {
        int a = first >>> 24, b = second >>> 24, c = third >>> 24, d = fourth >>> 24;
        int total = a + b + c + d;
        if (total == 0 || total == 4 * 255) {
            return mean(mean(first, fourth), mean(second, third));
        }
        // Preserve coverage when one or two opaque texels border fully transparent ones.
        boolean binaryAlpha = (a == 0 || a == 255) && (b == 0 || b == 255) && (c == 0 || c == 255) && (d == 0 || d == 255);
        int alpha = binaryAlpha && total <= 2 * 255 ? 255 : total / 4;
        int red = weightedChannel(first, second, third, fourth, a, b, c, d, total, 16);
        int green = weightedChannel(first, second, third, fourth, a, b, c, d, total, 8);
        int blue = weightedChannel(first, second, third, fourth, a, b, c, d, total, 0);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int mean(int first, int second) {
        return (first & second) + (((first ^ second) & 0xfefefefe) >>> 1);
    }

    private static int weightedChannel(int first, int second, int third, int fourth,
                                       int a, int b, int c, int d, int total, int shift) {
        return (a * (first >>> shift & 255) + b * (second >>> shift & 255)
            + c * (third >>> shift & 255) + d * (fourth >>> shift & 255)) / total;
    }

    static void uploadAnimation(HdAnimation animation, int[] pixels, IntBuffer buffer, AnimatedImage image) {
        if (image == null) {
            buffer.clear();
            buffer.put(pixels).flip();
            uploadRegion(0, animation.x(), animation.y(), animation.width(), animation.height(), buffer);
        } else {
            boolean anaglyph = Minecraft.getMinecraft().gameSettings.anaglyph;
            image.update(animation.x(), animation.y(), animation.width(), animation.height(), pixels,
                (level, x, y, width, height, data, stride) -> {
                    buffer.clear();
                    for (int row = y; row < y + height; row++) {
                        int offset = row * stride + x;
                        if (anaglyph) {
                            for (int col = 0; col < width; col++) buffer.put(TextureUtil.anaglyphColor(data[offset + col]));
                        } else {
                            buffer.put(data, offset, width);
                        }
                    }
                    buffer.flip();
                    uploadRegion(level, x, y, width, height, buffer);
                });
        }
    }

    private static void uploadRegion(int level, int x, int y, int width, int height, IntBuffer buffer) {
        GLSMTextures.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, x, y, width, height,
            GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
    }

    /** Owns the current CPU pixels for a single animated texture allocation. */
    public static final class AnimatedImage {
        private final int width;
        private final int[][] chain;
        // Playback tracks the upload identity without retaining the old pixel arrays.
        final Object uploadIdentity = new Object();

        AnimatedImage(int width, int[][] chain) {
            this.width = width;
            this.chain = chain;
        }

        void update(int x, int y, int patchWidth, int patchHeight, int[] pixels, RegionUpload upload) {
            for (int row = 0; row < patchHeight; row++) {
                System.arraycopy(pixels, row * patchWidth, chain[0], (y + row) * width + x, patchWidth);
            }
            upload.accept(0, x, y, patchWidth, patchHeight, chain[0], width);

            int right = x + patchWidth - 1;
            int bottom = y + patchHeight - 1;
            for (int level = 1; level < chain.length; level++) {
                int left = x >> level;
                int top = y >> level;
                int dirtyWidth = (right >> level) - left + 1;
                int dirtyHeight = (bottom >> level) - top + 1;
                int stride = width >> level;
                downsample(chain[level - 1], chain[level], stride, left, top, dirtyWidth, dirtyHeight);
                upload.accept(level, left, top, dirtyWidth, dirtyHeight, chain[level], stride);
            }
        }
    }

    /** Consumes a dirty rectangle from a mip level with the supplied row stride. */
    @FunctionalInterface
    interface RegionUpload {
        /** Reads the rectangle before the next mutation updates the image. */
        void accept(int level, int x, int y, int width, int height, int[] pixels, int stride);
    }
}

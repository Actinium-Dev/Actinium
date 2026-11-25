package com.gtnewhorizons.angelica.lwjgl3;

import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengles.GLES20;
import org.lwjgl.opengles.GLES30;
import org.lwjgl.opengles.GLES31;
import org.lwjgl.opengles.GLES32;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/** Reads a bound color texture through a temporary read framebuffer. */
final class GlesTextureReadback {
    private GlesTextureReadback() {}

    static void read(int target, int level, int format, int type, ByteBuffer pixels) {
        requirePackBuffer(false);
        read(target, level, format, type, MemoryUtil.memAddress(pixels), pixels.remaining());
    }

    static void read(int target, int level, int format, int type, IntBuffer pixels) {
        requirePackBuffer(false);
        read(target, level, format, type, MemoryUtil.memAddress(pixels), Math.multiplyExact(pixels.remaining(), Integer.BYTES));
    }

    static void read(int target, int level, int format, int type, long pointer) {
        requirePackBuffer(true);
        read(target, level, format, type, pointer, -1);
    }

    private static void requirePackBuffer(boolean offset) {
        final boolean bound = GLES20.glGetInteger(GLES30.GL_PIXEL_PACK_BUFFER_BINDING) != 0;
        if (offset != bound) throw new IllegalStateException("Texture readback destination does not match the pixel pack buffer binding");
    }

    private static void read(int target, int level, int format, int type, long pointer, int capacity) {
        final boolean cubeFace = target >= GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_X && target <= GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z;
        if (target != GLES20.GL_TEXTURE_2D && !cubeFace) throw GlesCalls.unsupported("glGetTexImage target 0x" + Integer.toHexString(target));

        final int binding = cubeFace ? GLES20.GL_TEXTURE_BINDING_CUBE_MAP : GLES20.GL_TEXTURE_BINDING_2D;
        final int texture = GLES20.glGetInteger(binding);
        final int width = GLES31.glGetTexLevelParameteri(target, level, GLES31.GL_TEXTURE_WIDTH);
        final int height = GLES31.glGetTexLevelParameteri(target, level, GLES31.GL_TEXTURE_HEIGHT);
        if (width == 0 || height == 0) return;

        final int readType = readType(format, type);
        final int previous = GLES20.glGetInteger(GLES30.GL_READ_FRAMEBUFFER_BINDING);
        final int framebuffer = GLES20.glGenFramebuffers();
        try {
            GLES20.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, framebuffer);
            GLES20.glFramebufferTexture2D(GLES30.GL_READ_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, target, texture, level);
            final int status = GLES20.glCheckFramebufferStatus(GLES30.GL_READ_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Texture read framebuffer incomplete: 0x" + Integer.toHexString(status));
            }
            GLES30.glReadBuffer(GLES20.GL_COLOR_ATTACHMENT0);
            if (capacity >= 0) {
                GLES32.nglReadnPixels(0, 0, width, height, format, readType, capacity, pointer);
            } else {
                GLES20.glReadPixels(0, 0, width, height, format, readType, pointer);
            }
        } finally {
            GLES20.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, previous);
            GLES20.glDeleteFramebuffers(framebuffer);
        }
    }

    private static int readType(int format, int type) {
        if (format == GLES20.GL_RGBA && type == GL12C.GL_UNSIGNED_INT_8_8_8_8_REV) {
            // A packed uint holding A:B:G:R has RGBA byte order on little-endian hosts.
            if (ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN) throw GlesCalls.unsupported("packed RGBA readback on a big-endian host");
            return GLES20.GL_UNSIGNED_BYTE;
        }
        return type;
    }
}

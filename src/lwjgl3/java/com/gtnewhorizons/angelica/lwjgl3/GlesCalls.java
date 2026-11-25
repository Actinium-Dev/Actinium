package com.gtnewhorizons.angelica.lwjgl3;

import org.lwjgl.opengles.EXTBufferStorage;
import org.lwjgl.opengles.EXTClearTexture;
import org.lwjgl.opengles.EXTPolygonOffsetClamp;
import org.lwjgl.opengles.EXTDisjointTimerQuery;
import org.lwjgl.opengles.EXTDrawElementsBaseVertex;
import org.lwjgl.opengles.EXTMultiDrawArrays;
import org.lwjgl.opengles.EXTMultiDrawIndirect;
import org.lwjgl.opengles.GLES;
import org.lwjgl.opengles.GLES20;
import org.lwjgl.opengles.GLES30;
import org.lwjgl.opengles.GLES32;
import org.lwjgl.opengles.NVPolygonMode;
import org.lwjgl.opengles.OESMapbuffer;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;

/** GLES operations whose signatures or availability differ from desktop OpenGL. */
public final class GlesCalls {
    private GlesCalls() {}

    public static UnsupportedOperationException unsupported(String function) {
        return new UnsupportedOperationException(function + " is unavailable on the current OpenGL ES context");
    }

    static void polygonMode(int face, int mode) {
        if (GLES.getCapabilities().GL_NV_polygon_mode) {
            NVPolygonMode.glPolygonModeNV(face, mode);
        } else if (mode != NVPolygonMode.GL_FILL_NV) {
            throw unsupported("glPolygonMode");
        }
    }

    static void polygonOffsetClamp(float factor, float units, float clamp) {
        if (GLES.getCapabilities().GL_EXT_polygon_offset_clamp) {
            EXTPolygonOffsetClamp.glPolygonOffsetClampEXT(factor, units, clamp);
        } else {
            GLES20.glPolygonOffset(factor, units);
        }
    }

    static void clearTexImage(int texture, int level, int format, int type) {
        if (!GLES.getCapabilities().GL_EXT_clear_texture) throw unsupported("glClearTexImageEXT");
        EXTClearTexture.glClearTexImageEXT(texture, level, format, type, (ByteBuffer) null);
    }

    static void getDoublev(int pname, DoubleBuffer params) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var values = stack.callocFloat(params.remaining());
            GLES20.glGetFloatv(pname, values);
            for (int i = 0; i < values.remaining(); i++) {
                params.put(params.position() + i, values.get(i));
            }
        }
    }

    static void getBufferSubData(int target, long offset, Buffer dest, int elementSize) {
        long bytes = (long) dest.remaining() * elementSize;
        if (bytes == 0) return;
        long address = GLES30.nglMapBufferRange(target, offset, bytes, GLES30.GL_MAP_READ_BIT);
        if (address == 0L) throw new IllegalStateException("Could not map buffer for readback");
        try {
            MemoryUtil.memCopy(address, MemoryUtil.memAddress(dest), bytes);
        } finally {
            if (!GLES30.glUnmapBuffer(target)) throw new IllegalStateException("Buffer became invalid during readback");
        }
    }

    static void clearBuffer(int target, int internalFormat, long offset, long size, int format, int type, ByteBuffer data) {
        if (internalFormat != GLES30.GL_R8 || format != GLES30.GL_RED || type != GL11C.GL_BYTE) {
            throw unsupported("glClearBufferSubData format");
        }
        if (size == 0) return;
        final byte value = data == null ? 0 : (byte) Math.max(0, (int) Math.round(data.get(data.position()) * (255.0 / 127.0)));
        final long address = GLES30.nglMapBufferRange(target, offset, size, GLES30.GL_MAP_WRITE_BIT | GLES30.GL_MAP_INVALIDATE_RANGE_BIT);
        if (address == 0L) throw new IllegalStateException("Could not map GL_R8 buffer range for clearing");
        try {
            MemoryUtil.memSet(address, value, size);
        } finally {
            if (!GLES30.glUnmapBuffer(target)) throw new IllegalStateException("Buffer became invalid during clear");
        }
    }

    static ByteBuffer mapBuffer(int target, int access) {
        return mapBuffer(target, access, null);
    }

    public static ByteBuffer mapBuffer(int target, int access, ByteBuffer oldBuffer) {
        if (!GLES.getCapabilities().GL_OES_mapbuffer) throw unsupported("glMapBufferOES");
        return OESMapbuffer.glMapBufferOES(target, access, oldBuffer);
    }

    static ByteBuffer mapBuffer(int target, int access, long length, ByteBuffer oldBuffer) {
        if (!GLES.getCapabilities().GL_OES_mapbuffer) throw unsupported("glMapBufferOES");
        return OESMapbuffer.glMapBufferOES(target, access, length, oldBuffer);
    }

    public static long mapBufferAddress(int target, int access) {
        if (!GLES.getCapabilities().GL_OES_mapbuffer) throw unsupported("glMapBufferOES");
        return OESMapbuffer.nglMapBufferOES(target, access);
    }

    static void bufferStorage(int target, ByteBuffer data, int flags) {
        if (!GLES.getCapabilities().GL_EXT_buffer_storage) throw unsupported("glBufferStorageEXT");
        EXTBufferStorage.glBufferStorageEXT(target, data, flags);
    }

    public static void bufferStorage(int target, long size, int flags) {
        if (!GLES.getCapabilities().GL_EXT_buffer_storage) throw unsupported("glBufferStorageEXT");
        EXTBufferStorage.glBufferStorageEXT(target, size, flags);
    }

    public static void multiDrawElementsBaseVertex(int mode, long counts, int type, long indices, int drawCount, long baseVertices) {
        var caps = GLES.getCapabilities();
        if (caps.GL_EXT_draw_elements_base_vertex && caps.GL_EXT_multi_draw_arrays && caps.glMultiDrawElementsBaseVertexEXT != 0L) {
            EXTDrawElementsBaseVertex.nglMultiDrawElementsBaseVertexEXT(mode, counts, type, indices, drawCount, baseVertices);
            return;
        }
        for (int i = 0; i < drawCount; i++) {
            GLES32.glDrawElementsBaseVertex(mode, MemoryUtil.memGetInt(counts + (long) i * Integer.BYTES), type,
                MemoryUtil.memGetAddress(indices + (long) i * Pointer.POINTER_SIZE),
                MemoryUtil.memGetInt(baseVertices + (long) i * Integer.BYTES));
        }
    }

    static void multiDrawArrays(int mode, IntBuffer firsts, IntBuffer counts) {
        if (GLES.getCapabilities().GL_EXT_multi_draw_arrays) {
            EXTMultiDrawArrays.glMultiDrawArraysEXT(mode, firsts, counts);
            return;
        }
        if (counts.remaining() < firsts.remaining()) throw new IllegalArgumentException("Missing multidraw counts");
        for (int i = 0; i < firsts.remaining(); i++) {
            GLES20.glDrawArrays(mode, firsts.get(firsts.position() + i), counts.get(counts.position() + i));
        }
    }

    static void multiDrawArrays(int mode, int[] firsts, int[] counts) {
        if (GLES.getCapabilities().GL_EXT_multi_draw_arrays) {
            EXTMultiDrawArrays.glMultiDrawArraysEXT(mode, firsts, counts);
            return;
        }
        if (counts.length < firsts.length) throw new IllegalArgumentException("Missing multidraw counts");
        for (int i = 0; i < firsts.length; i++) GLES20.glDrawArrays(mode, firsts[i], counts[i]);
    }

    static void multiDrawArraysIndirect(int mode, long indirect, int drawCount, int stride) {
        if (!GLES.getCapabilities().GL_EXT_multi_draw_indirect) throw unsupported("glMultiDrawArraysIndirectEXT");
        EXTMultiDrawIndirect.glMultiDrawArraysIndirectEXT(mode, indirect, drawCount, stride);
    }

    public static void multiDrawElementsIndirect(int mode, int type, long indirect, int drawCount, int stride) {
        if (!GLES.getCapabilities().GL_EXT_multi_draw_indirect) throw unsupported("glMultiDrawElementsIndirectEXT");
        EXTMultiDrawIndirect.glMultiDrawElementsIndirectEXT(mode, type, indirect, drawCount, stride);
    }

    public static void queryCounter(int id, int target) {
        if (!GLES.getCapabilities().GL_EXT_disjoint_timer_query) throw unsupported("glQueryCounterEXT");
        EXTDisjointTimerQuery.glQueryCounterEXT(id, target);
    }

    public static long getQueryObjectui64(int id, int pname) {
        if (!GLES.getCapabilities().GL_EXT_disjoint_timer_query) throw unsupported("glGetQueryObjectui64vEXT");
        return EXTDisjointTimerQuery.glGetQueryObjectui64EXT(id, pname);
    }
}

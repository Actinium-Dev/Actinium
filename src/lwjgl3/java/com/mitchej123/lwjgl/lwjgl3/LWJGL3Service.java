package com.mitchej123.lwjgl.lwjgl3;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.lwjgl3.GlesCalls;
import com.mitchej123.lwjgl.DebugExtension;
import org.lwjgl.opengles.GLES;
import org.lwjgl.opengles.GLES20;
import org.lwjgl.opengles.GLES30;
import org.lwjgl.opengles.GLES31;
import org.lwjgl.opengles.GLES32;
import org.lwjgl.opengles.GLESCapabilities;
import org.lwjgl.opengl.*;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import com.mitchej123.lwjgl.DebugMessageHandler;
import com.mitchej123.lwjgl.GLExtension;
import com.mitchej123.lwjgl.LWJGLService;
import com.mitchej123.lwjgl.MemoryStack;

import java.io.PrintStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.lwjgl3.Lwjgl3Context.isGlesContextCurrent;

/**
 * LWJGL3 implementation of {@link LWJGLService}.
 */
public record LWJGL3Service(
        VAOMode vaoMode,
        TimerQueryMode timerQueryMode,
        DebugMode debugMode,
        VertexAttribIMode vertexAttribIMode) implements LWJGLService {
    private static final Logger LOGGER = LogManager.getLogger("Celeritas/LWJGL3Service");
    private static final LWJGL3DebugSupport debugSupport = new LWJGL3DebugSupport();

    @Override
    public boolean isOpenGLVersionSupported(int major, int minor) {
        if (isGlesContextCurrent()) {
            GLESCapabilities caps = GLES.getCapabilities();
            return switch (major * 10 + minor) {
                case 30 -> caps.GLES30;
                case 31 -> caps.GLES31;
                case 32 -> caps.GLES32;
                default -> false;
            };
        }
        GLCapabilities caps = GL.getCapabilities();
        return switch (major * 10 + minor) {
            case 11 -> caps.OpenGL11;
            case 12 -> caps.OpenGL12;
            case 13 -> caps.OpenGL13;
            case 14 -> caps.OpenGL14;
            case 15 -> caps.OpenGL15;
            case 20 -> caps.OpenGL20;
            case 21 -> caps.OpenGL21;
            case 30 -> caps.OpenGL30;
            case 31 -> caps.OpenGL31;
            case 32 -> caps.OpenGL32;
            case 33 -> caps.OpenGL33;
            case 40 -> caps.OpenGL40;
            case 41 -> caps.OpenGL41;
            case 42 -> caps.OpenGL42;
            case 43 -> caps.OpenGL43;
            case 44 -> caps.OpenGL44;
            case 45 -> caps.OpenGL45;
            case 46 -> caps.OpenGL46;
            default -> false;
        };
    }

    @Override
    public boolean isExtensionSupported(GLExtension extension) {
        if (isGlesContextCurrent()) {
            GLESCapabilities caps = GLES.getCapabilities();
            return switch (extension) {
                case ARB_buffer_storage -> caps.GL_EXT_buffer_storage;
                case ARB_multi_draw_indirect -> caps.GL_EXT_multi_draw_indirect;
                case ARB_draw_elements_base_vertex -> caps.GLES32;
                case ARB_shader_storage_buffer_object, ARB_compute_shader -> caps.GLES31;
                case ARB_timer_query -> caps.GL_EXT_disjoint_timer_query;
                case KHR_debug -> caps.GLES32;
                case ARB_sync, ARB_uniform_buffer_object, ARB_vertex_array_object, ARB_map_buffer_range,
                     ARB_copy_buffer, ARB_texture_storage -> caps.GLES30;
                default -> false;
            };
        }
        GLCapabilities caps = GL.getCapabilities();
        return switch (extension) {
            case ARB_buffer_storage -> caps.GL_ARB_buffer_storage;
            case ARB_multi_draw_indirect -> caps.GL_ARB_multi_draw_indirect;
            case ARB_draw_elements_base_vertex -> caps.GL_ARB_draw_elements_base_vertex;
            case ARB_direct_state_access -> caps.GL_ARB_direct_state_access;
            case ARB_shader_storage_buffer_object -> caps.GL_ARB_shader_storage_buffer_object;
            case ARB_compute_shader -> caps.GL_ARB_compute_shader;
            case ARB_sync -> caps.GL_ARB_sync;
            case ARB_timer_query -> caps.GL_ARB_timer_query;
            case ARB_debug_output -> caps.GL_ARB_debug_output;
            case KHR_debug -> caps.GL_KHR_debug;
            case AMD_debug_output -> caps.GL_AMD_debug_output;
            case ARB_uniform_buffer_object -> caps.GL_ARB_uniform_buffer_object;
            case ARB_vertex_array_object -> caps.GL_ARB_vertex_array_object;
            case ARB_map_buffer_range -> caps.GL_ARB_map_buffer_range;
            case ARB_copy_buffer -> caps.GL_ARB_copy_buffer;
            case ARB_texture_storage -> caps.GL_ARB_texture_storage;
            case ARB_base_instance -> caps.GL_ARB_base_instance;
            case ARB_compatibility -> caps.GL_ARB_compatibility;
        };
    }

    @Override
    public int getPointerSize() {
        return Pointer.POINTER_SIZE;
    }

    @Override
    public int glGenBuffers() {
        if (isGlesContextCurrent()) {
            return GLES20.glGenBuffers();
        }
        return GL15C.glGenBuffers();
    }

    @Override
    public void glDeleteBuffers(int buffer) {
        if (isGlesContextCurrent()) {
            GLES20.glDeleteBuffers(buffer);
            return;
        }
        GL15C.glDeleteBuffers(buffer);
    }

    @Override
    public void glBindBuffer(int target, int buffer) {
        if (isGlesContextCurrent()) {
            GLES20.glBindBuffer(target, buffer);
            return;
        }
        GL15C.glBindBuffer(target, buffer);
    }

    @Override
    public void glBufferData(int target, long size, int usage) {
        if (isGlesContextCurrent()) {
            GLES20.glBufferData(target, size, usage);
            return;
        }
        GL15C.glBufferData(target, size, usage);
    }

    @Override
    public void glBufferData(int target, ByteBuffer data, int usage) {
        if (isGlesContextCurrent()) {
            GLES20.glBufferData(target, data, usage);
            return;
        }
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void glBufferData(int target, long size, long data, int usage) {
        if (isGlesContextCurrent()) {
            GLES20.nglBufferData(target, size, data, usage);
            return;
        }
        GL15C.nglBufferData(target, size, data, usage);
    }

    @Override
    public void glBufferStorage(int target, long size, int flags) {
        if (isGlesContextCurrent()) {
            GlesCalls.bufferStorage(target, size, flags);
            return;
        }
        GL44C.glBufferStorage(target, size, flags);
    }

    @Override
    public ByteBuffer glMapBufferRange(int target, long offset, long length, int flags) {
        if (isGlesContextCurrent()) {
            return GLES30.glMapBufferRange(target, offset, length, flags);
        }
        return GL30C.glMapBufferRange(target, offset, length, flags);
    }

    @Override
    public long nglMapBuffer(int target, int access) {
        if (isGlesContextCurrent()) {
            return GlesCalls.mapBufferAddress(target, access);
        }
        return GL15C.nglMapBuffer(target, access);
    }

    @Override
    public ByteBuffer glMapBuffer(int target, int access) {
        if (isGlesContextCurrent()) {
            return GlesCalls.mapBuffer(target, access, null);
        }
        return GL15C.glMapBuffer(target, access, null);
    }

    @Override
    public void glUnmapBuffer(int target) {
        if (isGlesContextCurrent()) {
            GLES30.glUnmapBuffer(target);
            return;
        }
        GL15C.glUnmapBuffer(target);
    }

    @Override
    public void glFlushMappedBufferRange(int target, long offset, long length) {
        if (isGlesContextCurrent()) {
            GLES30.glFlushMappedBufferRange(target, offset, length);
            return;
        }
        GL30C.glFlushMappedBufferRange(target, offset, length);
    }

    @Override
    public void glCopyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        if (isGlesContextCurrent()) {
            GLES30.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
            return;
        }
        GL31C.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }

    @Override
    public void glBindBufferBase(int target, int index, int buffer) {
        if (isGlesContextCurrent()) {
            GLES30.glBindBufferBase(target, index, buffer);
            return;
        }
        GL30C.glBindBufferBase(target, index, buffer);
    }

    private enum VAOMode {
        CORE {
            @Override public int gen() { return GL30C.glGenVertexArrays(); }
            @Override public void delete(int array) { GL30C.glDeleteVertexArrays(array); }
            @Override public void bind(int array) { GL30C.glBindVertexArray(array); }
        },
        ARB {
            @Override public int gen() { return ARBVertexArrayObject.glGenVertexArrays(); }
            @Override public void delete(int array) { ARBVertexArrayObject.glDeleteVertexArrays(array); }
            @Override public void bind(int array) { ARBVertexArrayObject.glBindVertexArray(array); }
        },
        APPLE {
            @Override public int gen() {
                try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                    java.nio.IntBuffer buf = stack.callocInt(1);
                    org.lwjgl.system.JNI.callPV(1, MemoryUtil.memAddress(buf), AppleVao.GENERATE);
                    return buf.get(0);
                }
            }
            @Override public void delete(int array) {
                try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                    java.nio.IntBuffer buf = stack.ints(array);
                    org.lwjgl.system.JNI.callPV(1, MemoryUtil.memAddress(buf), AppleVao.DELETE);
                }
            }
            @Override public void bind(int array) { org.lwjgl.system.JNI.callV(array, AppleVao.BIND); }
        },
        NONE {
            @Override public int gen() { throw new UnsupportedOperationException("VAO not supported"); }
            @Override public void delete(int array) { throw new UnsupportedOperationException("VAO not supported"); }
            @Override public void bind(int array) { throw new UnsupportedOperationException("VAO not supported"); }
        };

        public abstract int gen();
        public abstract void delete(int array);
        public abstract void bind(int array);
    }
    private enum TimerQueryMode { CORE, ARB, NONE }
    private enum DebugMode { KHR, NONE }
    private enum VertexAttribIMode { CORE, EXT, NONE }


    private static final class AppleVao {
        private static final long GENERATE = GL.getFunctionProvider().getFunctionAddress("glGenVertexArraysAPPLE");
        private static final long DELETE = GL.getFunctionProvider().getFunctionAddress("glDeleteVertexArraysAPPLE");
        private static final long BIND = GL.getFunctionProvider().getFunctionAddress("glBindVertexArrayAPPLE");
    }

    public static LWJGL3Service create() {
        if (isGlesContextCurrent()) {
            return new LWJGL3Service(VAOMode.NONE, TimerQueryMode.NONE, DebugMode.NONE, VertexAttribIMode.NONE);
        }
        GLCapabilities caps = GL.getCapabilities();

        VAOMode vaoMode;

        if (caps.OpenGL30) {
            vaoMode = VAOMode.CORE;
        } else if (caps.GL_ARB_vertex_array_object) {
            vaoMode = VAOMode.ARB;
        } else if (AppleVao.BIND != 0) {
            vaoMode = VAOMode.APPLE;
        } else {
            vaoMode = VAOMode.NONE;
        }

        TimerQueryMode timerQueryMode;

        if (caps.OpenGL33) {
            timerQueryMode = TimerQueryMode.CORE;
        } else if (caps.GL_ARB_timer_query) {
            timerQueryMode = TimerQueryMode.ARB;
        } else {
            timerQueryMode = TimerQueryMode.NONE;
            LOGGER.warn("ARB_timer_query extension not available - GPU profiling will be disabled");
        }

        DebugMode debugMode;

        if (caps.GL_KHR_debug || caps.OpenGL43) {
            debugMode = DebugMode.KHR;
        } else {
            debugMode = DebugMode.NONE;
        }

        VertexAttribIMode vertexAttribIMode;
        if (caps.OpenGL30) {
            vertexAttribIMode = VertexAttribIMode.CORE;
        } else if (caps.GL_EXT_gpu_shader4) {
            vertexAttribIMode = VertexAttribIMode.EXT;
        } else {
            vertexAttribIMode = VertexAttribIMode.NONE;
        }

        return new LWJGL3Service(vaoMode, timerQueryMode, debugMode, vertexAttribIMode);
    }

    @Override
    public int glGenVertexArrays() {
        if (isGlesContextCurrent()) {
            return GLES30.glGenVertexArrays();
        }
        return vaoMode.gen();
    }

    @Override
    public void glDeleteVertexArrays(int array) {
        if (isGlesContextCurrent()) {
            GLES30.glDeleteVertexArrays(array);
            return;
        }
        vaoMode.delete(array);
    }

    @Override
    public void glBindVertexArray(int array) {
        if (isGlesContextCurrent()) {
            GLES30.glBindVertexArray(array);
            return;
        }
        vaoMode.bind(array);
    }

    @Override
    public void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        if (isGlesContextCurrent()) {
            GLES20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
            return;
        }
        GL20C.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    @Override
    public void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        if (isGlesContextCurrent()) {
            GLES30.glVertexAttribIPointer(index, size, type, stride, pointer);
            return;
        }
        switch (vertexAttribIMode) {
            case CORE -> GL30C.glVertexAttribIPointer(index, size, type, stride, pointer);
            case EXT -> EXTGPUShader4.glVertexAttribIPointerEXT(index, size, type, stride, pointer);
            case NONE -> throw new UnsupportedOperationException("glVertexAttribIPointer not supported");
        }
    }

    @Override
    public void glEnableVertexAttribArray(int index) {
        if (isGlesContextCurrent()) {
            GLES20.glEnableVertexAttribArray(index);
            return;
        }
        GL20C.glEnableVertexAttribArray(index);
    }

    @Override
    public int glCreateShader(int type) {
        if (isGlesContextCurrent()) {
            return GLES20.glCreateShader(type);
        }
        return GL20C.glCreateShader(type);
    }

    @Override
    public void glShaderSource(int shader, CharSequence source) {
        if (isGlesContextCurrent()) {
            final int type = GLES20.glGetShaderi(shader, GLES20.GL_SHADER_TYPE);
            BackendManager.RENDER_BACKEND.shaderSource(shader, GLSMPrograms.prepareShaderSource(source.toString(), type, "shader_" + shader));
            return;
        }
        GL20C.glShaderSource(shader, source);
    }

    @Override
    public void glShaderSourceSafe(int shader, CharSequence source) {
        if (isGlesContextCurrent()) {
            glShaderSource(shader, source);
            return;
        }
        // AMD driver workaround: pass null for string length to force null-terminator reliance.
        // Some AMD drivers don't receive or interpret the length correctly, resulting in an
        // access violation when the driver tries to read past the string memory.
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.ByteBuffer sourceBuffer = MemoryUtil.memUTF8(source, true);
            org.lwjgl.PointerBuffer pointers = stack.mallocPointer(1);
            pointers.put(sourceBuffer);
            GL20C.nglShaderSource(shader, 1, pointers.address0(), 0);
            org.lwjgl.system.APIUtil.apiArrayFree(pointers.address0(), 1);
        }
    }

    @Override
    public void glCompileShader(int shader) {
        if (isGlesContextCurrent()) {
            GLES20.glCompileShader(shader);
            return;
        }
        GL20C.glCompileShader(shader);
    }

    @Override
    public String glGetShaderInfoLog(int shader, int maxLength) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetShaderInfoLog(shader);
        }
        // LWJGL3 doesn't need maxLength, but we accept it for API compatibility
        return GL20C.glGetShaderInfoLog(shader);
    }

    @Override
    public int glGetShaderi(int shader, int pname) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetShaderi(shader, pname);
        }
        return GL20C.glGetShaderi(shader, pname);
    }

    @Override
    public void glDeleteShader(int shader) {
        if (isGlesContextCurrent()) {
            GLES20.glDeleteShader(shader);
            return;
        }
        GL20C.glDeleteShader(shader);
    }

    @Override
    public int glCreateProgram() {
        if (isGlesContextCurrent()) {
            return GLES20.glCreateProgram();
        }
        return GL20C.glCreateProgram();
    }

    @Override
    public void glAttachShader(int program, int shader) {
        if (isGlesContextCurrent()) {
            GLES20.glAttachShader(program, shader);
            return;
        }
        GL20C.glAttachShader(program, shader);
    }

    @Override
    public void glLinkProgram(int program) {
        if (isGlesContextCurrent()) {
            GLES20.glLinkProgram(program);
            return;
        }
        GL20C.glLinkProgram(program);
    }

    @Override
    public String glGetProgramInfoLog(int program, int maxLength) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetProgramInfoLog(program);
        }
        return GL20C.glGetProgramInfoLog(program);
    }

    @Override
    public int glGetProgrami(int program, int pname) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetProgrami(program, pname);
        }
        return GL20C.glGetProgrami(program, pname);
    }

    @Override
    public void glUseProgram(int program) {
        if (isGlesContextCurrent()) {
            GLSMPrograms.glUseProgram(program);
            return;
        }
        GL20C.glUseProgram(program);
    }

    @Override
    public void glDeleteProgram(int program) {
        if (isGlesContextCurrent()) {
            GLES20.glDeleteProgram(program);
            return;
        }
        GL20C.glDeleteProgram(program);
    }

    @Override
    public void glBindAttribLocation(int program, int index, CharSequence name) {
        if (isGlesContextCurrent()) {
            GLES20.glBindAttribLocation(program, index, name);
            return;
        }
        GL20C.glBindAttribLocation(program, index, name);
    }

    @Override
    public void glBindFragDataLocation(int program, int colorNumber, CharSequence name) {
        if (isGlesContextCurrent()) {
            return;
        }
        GL30C.glBindFragDataLocation(program, colorNumber, name);
    }

    @Override
    public int glGetUniformLocation(int program, CharSequence name) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetUniformLocation(program, name);
        }
        return GL20C.glGetUniformLocation(program, name);
    }

    @Override
    public int glGetUniformBlockIndex(int program, CharSequence name) {
        if (isGlesContextCurrent()) {
            return GLES30.glGetUniformBlockIndex(program, name);
        }
        return GL31C.glGetUniformBlockIndex(program, name);
    }

    @Override
    public void glUniformBlockBinding(int program, int blockIndex, int blockBinding) {
        if (isGlesContextCurrent()) {
            GLES30.glUniformBlockBinding(program, blockIndex, blockBinding);
            return;
        }
        GL31C.glUniformBlockBinding(program, blockIndex, blockBinding);
    }

    @Override
    public void glUniform1f(int location, float v0) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform1f(location, v0);
            return;
        }
        GL20C.glUniform1f(location, v0);
    }

    @Override
    public void glUniform1i(int location, int v0) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform1i(location, v0);
            return;
        }
        GL20C.glUniform1i(location, v0);
    }

    @Override
    public void glUniform1fv(int location, FloatBuffer value) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform1fv(location, value);
            return;
        }
        GL20C.glUniform1fv(location, value);
    }

    @Override
    public void glUniform2i(int location, int v0, int v1) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform2i(location, v0, v1);
            return;
        }
        GL20C.glUniform2i(location, v0, v1);
    }

    @Override
    public void glUniform3i(int location, int v0, int v1, int v2) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform3i(location, v0, v1, v2);
            return;
        }
        GL20C.glUniform3i(location, v0, v1, v2);
    }

    @Override
    public void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform4i(location, v0, v1, v2, v3);
            return;
        }
        GL20C.glUniform4i(location, v0, v1, v2, v3);
    }

    @Override
    public void glUniform3f(int location, float v0, float v1, float v2) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform3f(location, v0, v1, v2);
            return;
        }
        GL20C.glUniform3f(location, v0, v1, v2);
    }

    @Override
    public void glUniform3fv(int location, FloatBuffer value) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform3fv(location, value);
            return;
        }
        GL20C.glUniform3fv(location, value);
    }

    @Override
    public void glUniform3fv(int location, float[] value) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform3fv(location, value);
            return;
        }
        GL20C.glUniform3fv(location, value);
    }

    @Override
    public void glUniform4fv(int location, FloatBuffer value) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform4fv(location, value);
            return;
        }
        GL20C.glUniform4fv(location, value);
    }

    @Override
    public void glUniform4fv(int location, float[] value) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform4fv(location, value);
            return;
        }
        GL20C.glUniform4fv(location, value);
    }

    @Override
    public void glUniformMatrix3fv(int location, boolean transpose, FloatBuffer value) {
        if (isGlesContextCurrent()) {
            GLES20.glUniformMatrix3fv(location, transpose, value);
            return;
        }
        GL20C.glUniformMatrix3fv(location, transpose, value);
    }

    @Override
    public void glUniformMatrix4fv(int location, boolean transpose, FloatBuffer value) {
        if (isGlesContextCurrent()) {
            GLES20.glUniformMatrix4fv(location, transpose, value);
            return;
        }
        GL20C.glUniformMatrix4fv(location, transpose, value);
    }

    @Override
    public void glDrawElementsBaseVertex(int mode, int count, int type, long indices, int basevertex) {
        if (isGlesContextCurrent()) {
            GLES32.glDrawElementsBaseVertex(mode, count, type, indices, basevertex);
            return;
        }
        GL32C.glDrawElementsBaseVertex(mode, count, type, indices, basevertex);
    }

    @Override
    public void glMultiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex) {
        if (isGlesContextCurrent()) {
            GlesCalls.multiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawcount, pBaseVertex);
            return;
        }
        GL32C.nglMultiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawcount, pBaseVertex);
    }

    @Override
    public void glMultiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        if (isGlesContextCurrent()) {
            GlesCalls.multiDrawElementsIndirect(mode, type, indirect, drawcount, stride);
            return;
        }
        GL43C.glMultiDrawElementsIndirect(mode, type, indirect, drawcount, stride);
    }

    @Override
    public long glFenceSync(int condition, int flags) {
        if (isGlesContextCurrent()) {
            return GLES30.glFenceSync(condition, flags);
        }
        return GL32C.glFenceSync(condition, flags);
    }

    @Override
    public int glClientWaitSync(long sync, int flags, long timeout) {
        if (isGlesContextCurrent()) {
            return GLES30.glClientWaitSync(sync, flags, timeout);
        }
        return GL32C.glClientWaitSync(sync, flags, timeout);
    }

    @Override
    public int glGetSynci(long sync, int pname, IntBuffer length) {
        if (isGlesContextCurrent()) {
            return GLES30.glGetSynci(sync, pname, length);
        }
        return GL32C.glGetSynci(sync, pname, length);
    }

    @Override
    public void glWaitSync(long sync, int flags, long timeout) {
        if (isGlesContextCurrent()) {
            GLES30.glWaitSync(sync, flags, timeout);
            return;
        }
        GL32C.glWaitSync(sync, flags, timeout);
    }

    @Override
    public void glDeleteSync(long sync) {
        if (isGlesContextCurrent()) {
            GLES30.glDeleteSync(sync);
            return;
        }
        GL32C.glDeleteSync(sync);
    }

    @Override
    public int glGenQueries() {
        if (isGlesContextCurrent()) {
            return GLES30.glGenQueries();
        }
        return GL15C.glGenQueries();
    }

    @Override
    public void glDeleteQueries(int query) {
        if (isGlesContextCurrent()) {
            GLES30.glDeleteQueries(query);
            return;
        }
        GL15C.glDeleteQueries(query);
    }

    @Override
    public void glQueryCounter(int id, int target) {
        if (isGlesContextCurrent()) {
            GlesCalls.queryCounter(id, target);
            return;
        }
        switch (timerQueryMode) {
            case CORE -> GL33C.glQueryCounter(id, target);
            case ARB -> ARBTimerQuery.glQueryCounter(id, target);
            case NONE -> { /* no-op */ }
        }
    }

    @Override
    public long glGetQueryObjectui64(int id, int pname) {
        if (isGlesContextCurrent()) {
            return GlesCalls.getQueryObjectui64(id, pname);
        }
        return switch (timerQueryMode) {
            case CORE -> GL33C.glGetQueryObjectui64(id, pname);
            case ARB -> ARBTimerQuery.glGetQueryObjectui64(id, pname);
            case NONE -> 0L;
        };
    }

    @Override
    public PrintStream getDebugStream() { return APIUtil.DEBUG_STREAM; }

    @Override
    public int setupDebugCallback(DebugMessageHandler handler) {
        if (isGlesContextCurrent()) {
            return BackendManager.RENDER_BACKEND.setupDebugOutput((source, type, id, severity, message) ->
                handler.handle(source, type, id, severity, message, DebugExtension.KHR_DEBUG));
        }
        return debugSupport.setupDebugCallback(handler);
    }

    @Override
    public void disableDebugCallback() {
        if (isGlesContextCurrent()) {
            BackendManager.RENDER_BACKEND.disableDebugOutput();
            return;
        }
        debugSupport.disableDebugCallback();
    }

    @Override
    public void glObjectLabel(int identifier, int name, CharSequence label) {
        if (isGlesContextCurrent()) {
            BackendManager.RENDER_BACKEND.objectLabel(identifier, name, label);
            return;
        }
        if (debugMode == DebugMode.KHR) {
            KHRDebug.glObjectLabel(identifier, name, label);
        }
    }

    @Override
    public void glPushDebugGroup(int source, int id, CharSequence message) {
        if (isGlesContextCurrent()) {
            BackendManager.RENDER_BACKEND.pushDebugGroup(source, id, message);
            return;
        }
        if (debugMode == DebugMode.KHR) {
            KHRDebug.glPushDebugGroup(source, id, message);
        }
    }

    @Override
    public void glPopDebugGroup() {
        if (isGlesContextCurrent()) {
            BackendManager.RENDER_BACKEND.popDebugGroup();
            return;
        }
        if (debugMode == DebugMode.KHR) {
            KHRDebug.glPopDebugGroup();
        }
    }

    @Override
    public int glGenTextures() {
        if (isGlesContextCurrent()) {
            return GLES20.glGenTextures();
        }
        return GL11C.glGenTextures();
    }

    @Override
    public void glGenTextures(int[] textures) {
        if (isGlesContextCurrent()) {
            GLES20.glGenTextures(textures);
            return;
        }
        GL11C.glGenTextures(textures);
    }

    @Override
    public void glDeleteTextures(int texture) {
        if (isGlesContextCurrent()) {
            GLES20.glDeleteTextures(texture);
            return;
        }
        GL11C.glDeleteTextures(texture);
    }

    @Override
    public void glDeleteTextures(int[] textures) {
        if (isGlesContextCurrent()) {
            GLES20.glDeleteTextures(textures);
            return;
        }
        GL11C.glDeleteTextures(textures);
    }

    @Override
    public void glBindTexture(int target, int texture) {
        if (isGlesContextCurrent()) {
            GLES20.glBindTexture(target, texture);
            return;
        }
        GL11C.glBindTexture(target, texture);
    }

    @Override
    public void glActiveTexture(int texture) {
        if (isGlesContextCurrent()) {
            GLES20.glActiveTexture(texture);
            return;
        }
        GL13C.glActiveTexture(texture);
    }

    @Override
    public int glGetTexLevelParameteri(int target, int level, int pname) {
        if (isGlesContextCurrent()) {
            return BackendManager.RENDER_BACKEND.getTexLevelParameteri(target, level, pname);
        }
        return GL11C.glGetTexLevelParameteri(target, level, pname);
    }

    @Override
    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset,
                                    int x, int y, int width, int height) {
        if (isGlesContextCurrent()) {
            GLES20.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
            return;
        }
        GL11C.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }

    @Override
    public void glPixelStorei(int pname, int param) {
        if (isGlesContextCurrent()) {
            GLES20.glPixelStorei(pname, param);
            return;
        }
        GL11C.glPixelStorei(pname, param);
    }

    @Override
    public void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        if (isGlesContextCurrent()) {
            GLES31.glBindImageTexture(unit, texture, level, layered, layer, access, format);
            return;
        }
        GL42C.glBindImageTexture(unit, texture, level, layered, layer, access, format);
    }

    @Override
    public int glGenFramebuffers() {
        if (isGlesContextCurrent()) {
            return GLES20.glGenFramebuffers();
        }
        return GL30C.glGenFramebuffers();
    }

    @Override
    public void glDeleteFramebuffers(int framebuffer) {
        if (isGlesContextCurrent()) {
            GLES20.glDeleteFramebuffers(framebuffer);
            return;
        }
        GL30C.glDeleteFramebuffers(framebuffer);
    }

    @Override
    public void glBindFramebuffer(int target, int framebuffer) {
        if (isGlesContextCurrent()) {
            GLES20.glBindFramebuffer(target, framebuffer);
            return;
        }
        GL30C.glBindFramebuffer(target, framebuffer);
    }

    @Override
    public int glCheckFramebufferStatus(int target) {
        if (isGlesContextCurrent()) {
            return GLES20.glCheckFramebufferStatus(target);
        }
        return GL30C.glCheckFramebufferStatus(target);
    }

    @Override
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        if (isGlesContextCurrent()) {
            GLES20.glFramebufferTexture2D(target, attachment, textarget, texture, level);
            return;
        }
        GL30C.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    @Override
    public void glEnable(int cap) {
        if (isGlesContextCurrent()) {
            GLES20.glEnable(cap);
            return;
        }
        GL11C.glEnable(cap);
    }

    @Override
    public void glDisable(int cap) {
        if (isGlesContextCurrent()) {
            GLES20.glDisable(cap);
            return;
        }
        GL11C.glDisable(cap);
    }

    @Override
    public void glBlendFunc(int sfactor, int dfactor) {
        if (isGlesContextCurrent()) {
            GLES20.glBlendFunc(sfactor, dfactor);
            return;
        }
        GL11C.glBlendFunc(sfactor, dfactor);
    }

    @Override
    public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        if (isGlesContextCurrent()) {
            GLES20.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
            return;
        }
        GL14C.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override
    public void glDepthFunc(int func) {
        if (isGlesContextCurrent()) {
            GLES20.glDepthFunc(func);
            return;
        }
        GL11C.glDepthFunc(func);
    }

    @Override
    public void glDepthMask(boolean flag) {
        if (isGlesContextCurrent()) {
            GLES20.glDepthMask(flag);
            return;
        }
        GL11C.glDepthMask(flag);
    }

    @Override
    public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        if (isGlesContextCurrent()) {
            GLES20.glColorMask(red, green, blue, alpha);
            return;
        }
        GL11C.glColorMask(red, green, blue, alpha);
    }

    @Override
    public void glViewport(int x, int y, int width, int height) {
        if (isGlesContextCurrent()) {
            GLES20.glViewport(x, y, width, height);
            return;
        }
        GL11C.glViewport(x, y, width, height);
    }

    @Override
    public void glClear(int mask) {
        if (isGlesContextCurrent()) {
            GLES20.glClear(mask);
            return;
        }
        GL11C.glClear(mask);
    }

    @Override
    public void glClearColor(float red, float green, float blue, float alpha) {
        if (isGlesContextCurrent()) {
            GLES20.glClearColor(red, green, blue, alpha);
            return;
        }
        GL11C.glClearColor(red, green, blue, alpha);
    }

    @Override
    public int glGetError() {
        if (isGlesContextCurrent()) {
            return GLES20.glGetError();
        }
        return GL11C.glGetError();
    }

    @Override
    public void glMatrixMode(int mode) {
        if (isGlesContextCurrent()) {
            GLStateManager.glMatrixMode(mode);
            return;
        }
        GL11.glMatrixMode(mode);
    }

    @Override
    public void glLoadMatrixf(FloatBuffer m) {
        if (isGlesContextCurrent()) {
            GLStateManager.glLoadMatrix(m);
            return;
        }
        GL11.glLoadMatrixf(m);
    }

    @Override
    public int glGetInteger(int pname) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetInteger(pname);
        }
        return GL11C.glGetInteger(pname);
    }

    @Override
    public void glGetIntegerv(int pname, int[] params) {
        if (isGlesContextCurrent()) {
            GLES20.glGetIntegerv(pname, params);
            return;
        }
        GL11C.glGetIntegerv(pname, params);
    }

    @Override
    public boolean glGetBoolean(int pname) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetBoolean(pname);
        }
        return GL11C.glGetBoolean(pname);
    }

    @Override
    public String glGetString(int pname) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetString(pname);
        }
        return GL11C.glGetString(pname);
    }

    @Override
    public int glGetAttribLocation(int program, CharSequence name) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetAttribLocation(program, name);
        }
        return GL20C.glGetAttribLocation(program, name);
    }

    @Override
    public MemoryStack stackPush() {
        return new LWJGL3MemoryStack(org.lwjgl.system.MemoryStack.stackPush());
    }

    @Override
    public long nmemAlloc(long size) {
        return MemoryUtil.nmemAlloc(size);
    }

    @Override
    public long nmemCalloc(long count, long size) {
        return MemoryUtil.nmemCalloc(count, size);
    }

    @Override
    public long nmemAlignedAlloc(long alignment, long size) {
        return MemoryUtil.nmemAlignedAlloc(alignment, size);
    }

    @Override
    public long nmemRealloc(long ptr, long size) {
        return MemoryUtil.nmemRealloc(ptr, size);
    }

    @Override
    public void nmemFree(long ptr) {
        MemoryUtil.nmemFree(ptr);
    }

    @Override
    public void nmemAlignedFree(long ptr) {
        MemoryUtil.nmemAlignedFree(ptr);
    }

    @Override
    public ByteBuffer memAlloc(int size) {
        return MemoryUtil.memAlloc(size);
    }

    @Override
    public ByteBuffer memCalloc(int size) {
        return MemoryUtil.memCalloc(size);
    }

    @Override
    public ByteBuffer memRealloc(ByteBuffer buffer, int size) {
        return MemoryUtil.memRealloc(buffer, size);
    }

    @Override
    public void memFree(Buffer buffer) {
        MemoryUtil.memFree(buffer);
    }

    @Override
    public ByteBuffer memByteBuffer(long address, int capacity) {
        return MemoryUtil.memByteBuffer(address, capacity);
    }

    @Override
    public long memAddress(Buffer buffer) {
        return MemoryUtil.memAddress(buffer);
    }

    @Override
    public long memAddress(Buffer buffer, int position) {
        // Generic Buffer doesn't have a positioned memAddress in LWJGL3, compute manually
        // Get base address and add position offset based on element size
        long base = MemoryUtil.memAddress(buffer);
        int elementSize;
        if (buffer instanceof java.nio.ByteBuffer) {
            elementSize = 1;
        } else if (buffer instanceof java.nio.ShortBuffer || buffer instanceof java.nio.CharBuffer) {
            elementSize = 2;
        } else if (buffer instanceof java.nio.IntBuffer || buffer instanceof java.nio.FloatBuffer) {
            elementSize = 4;
        } else if (buffer instanceof java.nio.LongBuffer || buffer instanceof java.nio.DoubleBuffer) {
            elementSize = 8;
        } else {
            throw new IllegalArgumentException("Unsupported buffer type: " + buffer.getClass());
        }
        return base + ((long) position * elementSize);
    }

    @Override
    public void memSet(long address, int value, long bytes) {
        MemoryUtil.memSet(address, value, bytes);
    }

    @Override
    public void memCopy(long src, long dst, long bytes) {
        MemoryUtil.memCopy(src, dst, bytes);
    }

    @Override
    public void memPutByte(long address, byte value) {
        MemoryUtil.memPutByte(address, value);
    }

    @Override
    public void memPutShort(long address, short value) {
        MemoryUtil.memPutShort(address, value);
    }

    @Override
    public void memPutInt(long address, int value) {
        MemoryUtil.memPutInt(address, value);
    }

    @Override
    public void memPutFloat(long address, float value) {
        MemoryUtil.memPutFloat(address, value);
    }

    @Override
    public void memPutLong(long address, long value) {
        MemoryUtil.memPutLong(address, value);
    }

    @Override
    public void memPutAddress(long address, long value) {
        MemoryUtil.memPutAddress(address, value);
    }

    @Override
    public byte memGetByte(long address) {
        return MemoryUtil.memGetByte(address);
    }

    @Override
    public short memGetShort(long address) {
        return MemoryUtil.memGetShort(address);
    }

    @Override
    public int memGetInt(long address) {
        return MemoryUtil.memGetInt(address);
    }

    @Override
    public float memGetFloat(long address) {
        return MemoryUtil.memGetFloat(address);
    }

    @Override
    public long memGetLong(long address) {
        return MemoryUtil.memGetLong(address);
    }

    @Override
    public long memGetAddress(long address) {
        return MemoryUtil.memGetAddress(address);
    }

    @Override
    public ByteBuffer memSlice(ByteBuffer buffer, int offset, int capacity) {
        return MemoryUtil.memSlice(buffer, offset, capacity);
    }
}

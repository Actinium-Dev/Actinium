package com.gtnewhorizons.angelica.lwjgl3;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.system.MemoryStack;

/** Verifies indexed multidraw with buffer-backed vertex attributes and offsets used by terrain batches. */
public final class IndexedMultidrawProbe {
    private static final Logger LOGGER = LogManager.getLogger(IndexedMultidrawProbe.class);
    private static final int[] BASE_VERTICES = {0, 65_536, 8, 65_540};
    private static final int[] FIRST_INDICES = {0, 0, 3, 0};
    private static final int[] COUNTS = {3, 3, 6, 3};
    private static final int[] RASTER_STATES = {
        GL11C.GL_SCISSOR_TEST, GL11C.GL_CULL_FACE, GL30C.GL_RASTERIZER_DISCARD
    };
    private static final int[] PACK_STATES = {GL11C.GL_PACK_ROW_LENGTH, GL11C.GL_PACK_SKIP_ROWS, GL11C.GL_PACK_SKIP_PIXELS};

    private IndexedMultidrawProbe() {}

    static boolean run(IndirectMultidraw.Dispatch dispatch) {
        try (var stack = MemoryStack.stackPush()) {
            int oldProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            int oldVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
            int oldArrayBuffer = GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
            int oldDrawFbo = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
            int oldReadFbo = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
            int oldRenderbuffer = GL11C.glGetInteger(GL30C.GL_RENDERBUFFER_BINDING);
            int oldPackBuffer = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
            int oldIndirect = GL11C.glGetInteger(GL40C.GL_DRAW_INDIRECT_BUFFER_BINDING);
            var viewport = stack.mallocInt(4);
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
            var colorMask = stack.malloc(4);
            GL30C.glGetBooleani_v(GL11C.GL_COLOR_WRITEMASK, 0, colorMask);
            boolean blend = GL30C.glIsEnabledi(GL11C.GL_BLEND, 0);
            boolean[] rasterState = new boolean[RASTER_STATES.length];
            int[] packState = new int[PACK_STATES.length];

            int program = GL20C.glCreateProgram();
            int vao = GL30C.glGenVertexArrays();
            int vertices = GL15C.glGenBuffers();
            int elements = GL15C.glGenBuffers();
            int indirect = GL15C.glGenBuffers();
            int fbo = GL30C.glGenFramebuffers();
            int color = GL30C.glGenRenderbuffers();
            for (int i = 0; i < RASTER_STATES.length; i++) {
                rasterState[i] = GL11C.glIsEnabled(RASTER_STATES[i]);
                GL11C.glDisable(RASTER_STATES[i]);
            }
            for (int i = 0; i < PACK_STATES.length; i++) {
                packState[i] = GL11C.glGetInteger(PACK_STATES[i]);
                GL11C.glPixelStorei(PACK_STATES[i], 0);
            }
            try {
                if (!link(program)) return false;
                GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
                GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, color);
                GL30C.glRenderbufferStorage(GL30C.GL_RENDERBUFFER, GL11C.GL_RGBA8, COUNTS.length, 1);
                GL30C.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL30C.GL_RENDERBUFFER, color);
                int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
                if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                    LOGGER.warn("Cannot verify indexed multidraw: probe framebuffer status {}", status);
                    return false;
                }

                GL11C.glViewport(0, 0, COUNTS.length, 1);
                GL30C.glColorMaski(0, true, true, true, true);
                GL30C.glDisablei(GL11C.GL_BLEND, 0);
                GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
                GL20C.glUseProgram(program);
                GL30C.glBindVertexArray(vao);
                GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vertices);
                GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, (65_540L + 4) * 2 * Float.BYTES, GL15C.GL_STATIC_DRAW);
                for (int i = 0; i < COUNTS.length; i++) {
                    float x = (2.0f * i + 1.0f) / COUNTS.length - 1.0f;
                    GL15C.glBufferSubData(GL15C.GL_ARRAY_BUFFER, (long) BASE_VERTICES[i] * 2 * Float.BYTES,
                        stack.floats(x - 0.24f, -0.9f, x + 0.24f, -0.9f, x,
                            FIRST_INDICES[i] == 0 ? 0.9f : -0.9f, x, 0.9f));
                }
                GL20C.glVertexAttribPointer(0, 2, GL11C.GL_FLOAT, false, 0, 0L);
                GL20C.glEnableVertexAttribArray(0);
                GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, elements);
                GL15C.glBufferData(GL15C.GL_ELEMENT_ARRAY_BUFFER, stack.ints(0, 1, 2, 0, 1, 3, 3, 3, 3), GL15C.GL_STATIC_DRAW);
                GL30C.glClearBufferfv(GL11C.GL_COLOR, 0, stack.floats(0, 0, 0, 1));
                GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, indirect);
                var commands = stack.mallocInt(COUNTS.length * IndirectMultidraw.COMMAND_WORDS);
                for (int i = 0; i < COUNTS.length; i++) {
                    commands.put(COUNTS[i]).put(1).put(FIRST_INDICES[i]).put(BASE_VERTICES[i]).put(0);
                }
                GL15C.glBufferData(GL40C.GL_DRAW_INDIRECT_BUFFER, commands.flip(), GL15C.GL_STATIC_DRAW);
                dispatch.draw(GL11C.GL_TRIANGLES, GL11C.GL_UNSIGNED_INT, 0L, COUNTS.length, 0);
                return allCommandsDrawn(stack);
            } finally {
                GL20C.glUseProgram(oldProgram);
                GL30C.glBindVertexArray(oldVao);
                GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, oldArrayBuffer);
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, oldDrawFbo);
                GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, oldReadFbo);
                GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, oldRenderbuffer);
                GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, oldPackBuffer);
                GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, oldIndirect);
                GL11C.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                GL30C.glColorMaski(0, colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0,
                    colorMask.get(3) != 0);
                if (blend) GL30C.glEnablei(GL11C.GL_BLEND, 0);
                for (int i = 0; i < RASTER_STATES.length; i++) {
                    if (rasterState[i]) GL11C.glEnable(RASTER_STATES[i]);
                }
                for (int i = 0; i < PACK_STATES.length; i++) GL11C.glPixelStorei(PACK_STATES[i], packState[i]);
                GL20C.glDeleteProgram(program);
                GL30C.glDeleteVertexArrays(vao);
                GL15C.glDeleteBuffers(vertices);
                GL15C.glDeleteBuffers(elements);
                GL15C.glDeleteBuffers(indirect);
                GL30C.glDeleteFramebuffers(fbo);
                GL30C.glDeleteRenderbuffers(color);
            }
        }
    }

    private static boolean allCommandsDrawn(MemoryStack stack) {
        var pixels = stack.calloc(COUNTS.length * 4);
        GL11C.glReadPixels(0, 0, COUNTS.length, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
        int drawn = 0;
        for (int i = 0; i < COUNTS.length; i++) {
            if (pixels.get(i * 4) == (byte) 255) drawn |= 1 << i;
        }
        if (drawn == (1 << COUNTS.length) - 1) return true;
        LOGGER.warn("Indexed indirect probe missed commands: drawnMask=0x{}", Integer.toHexString(drawn));
        return false;
    }

    private static boolean link(int program) {
        int vertex = GL20C.glCreateShader(GL20C.GL_VERTEX_SHADER);
        int fragment = GL20C.glCreateShader(GL20C.GL_FRAGMENT_SHADER);
        try {
            GL20C.glShaderSource(vertex, """
                #version 330 core
                layout(location = 0) in vec2 position;
                void main() {
                    gl_Position = vec4(position, 0.0, 1.0);
                }
                """);
            GL20C.glShaderSource(fragment, "#version 330 core\nlayout(location = 0) out vec4 color; void main() { color = vec4(1.0); }");
            for (int shader : new int[] {vertex, fragment}) {
                GL20C.glCompileShader(shader);
                if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
                    LOGGER.warn("Cannot verify indexed multidraw: {}", GL20C.glGetShaderInfoLog(shader));
                    return false;
                }
                GL20C.glAttachShader(program, shader);
            }
            GL20C.glLinkProgram(program);
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) != GL11C.GL_FALSE) return true;
            LOGGER.warn("Cannot verify indexed multidraw: {}", GL20C.glGetProgramInfoLog(program));
            return false;
        } finally {
            GL20C.glDeleteShader(vertex);
            GL20C.glDeleteShader(fragment);
        }
    }
}

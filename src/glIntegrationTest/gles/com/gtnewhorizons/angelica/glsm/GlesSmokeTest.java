package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackendCandidates;
import com.gtnewhorizons.angelica.glsm.backend.WindowBackends;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMInitConfig;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import com.gtnewhorizons.angelica.lwjgl3.Lwjgl3Context;
import com.gtnewhorizons.angelica.lwjgl3.Lwjgl3GLRenderBackend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengles.GLES;
import org.lwjgl.opengles.GLES20;
import org.lwjgl.opengles.GLES30;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.sdl.SDLInit.SDL_INIT_VIDEO;
import static org.lwjgl.sdl.SDLInit.SDL_Init;
import static org.lwjgl.sdl.SDLInit.SDL_Quit;

class GlesSmokeTest {
    private static final int SIZE = 64;
    private static final int BLACK = 0x000000ff;
    private static long window;
    private static long context;
    private static boolean glesLoaded;
    private static boolean glsmInitialized;
    private static int framebuffer;
    private static int colorAttachment;

    @BeforeAll
    static void initialize() {
        Configuration.OPENGL_EXPLICIT_INIT.set(true);
        WindowBackends.useSdl();
        assertTrue(SDL_Init(SDL_INIT_VIDEO), () -> sdlError("SDL_Init"));
        setContextAttribute(SDLVideo.SDL_GL_CONTEXT_PROFILE_MASK, SDLVideo.SDL_GL_CONTEXT_PROFILE_ES);
        setContextAttribute(SDLVideo.SDL_GL_CONTEXT_MAJOR_VERSION, 3);
        setContextAttribute(SDLVideo.SDL_GL_CONTEXT_MINOR_VERSION, 2);
        window = SDLVideo.SDL_CreateWindow("Actinium GLES 3.2 integration", SIZE, SIZE,
            SDLVideo.SDL_WINDOW_OPENGL | SDLVideo.SDL_WINDOW_HIDDEN);
        assertNotEquals(0L, window, () -> sdlError("SDL_CreateWindow (OpenGL ES 3.2)"));
        context = SDLVideo.SDL_GL_CreateContext(window);
        assertNotEquals(0L, context, () -> sdlError("SDL_GL_CreateContext (OpenGL ES 3.2)"));
        assertTrue(SDLVideo.SDL_GL_MakeCurrent(window, context), () -> sdlError("SDL_GL_MakeCurrent"));
        assertNotEquals(0L, SDLVideo.SDL_EGL_GetCurrentDisplay(), "The current context must use EGL");

        GL.create(SDLVideo::SDL_GL_GetProcAddress);
        GL.createCapabilities();
        Lwjgl3Context.bindGlesContext(context);
        glesLoaded = true;
        var capabilities = GLES.getCapabilities();
        String version = GLES20.glGetString(GLES20.GL_VERSION);
        assertTrue(version != null && version.startsWith("OpenGL ES ") && capabilities.GLES32,
            () -> "OpenGL ES 3.2 is required, got " + version);
        System.out.println(version + " / " + GLES20.glGetString(GLES20.GL_RENDERER));

        RenderBackendCandidates.register(Lwjgl3GLRenderBackend::new);
        assertInstanceOf(Lwjgl3GLRenderBackend.class, BackendManager.RENDER_BACKEND);
        // The test JVM does not run the launch transform that redirects legacy client arrays.
        VertexFormat.registerSetupBufferStateOverride((format, offset) -> {
            for (VertexFormatElement element : format.getElements()) {
                var usage = element.getUsage();
                int attribute = usage.getAttributeLocation();
                boolean normalized = usage == VertexFormatElement.Usage.COLOR || usage == VertexFormatElement.Usage.NORMAL;
                GLSMVertexInput.glEnableVertexAttribArray(attribute);
                GLSMVertexInput.glVertexAttribPointer(attribute, element.getCount(), element.getType().getGlType(),
                    normalized, format.getVertexSize(), offset);
                offset += element.getByteSize();
            }
            return true;
        });
        GLStateManager.initialize(GLSMInitConfig.builder().displaySize(SIZE, SIZE)
            .directDrawer(TessellatorStreamingDrawer::drawDirect).build());
        glsmInitialized = true;
        GLStateManager.setRunningSplash(false);
        GLStateManager.markSplashComplete("GlesSmokeTest");
        assertTrue(RenderSystem.isGLES());
        assertNoDriverError();

        colorAttachment = createTexture(SIZE, SIZE, null);
        framebuffer = GLSMFramebuffer.glGenFramebuffers();
        GLSMFramebuffer.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
        GLSMFramebuffer.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
            GL11C.GL_TEXTURE_2D, colorAttachment, 0);
        assertEquals(GL30C.GL_FRAMEBUFFER_COMPLETE, GLSMFramebuffer.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER));
        GLSMTextures.glBindTexture(GL11C.GL_TEXTURE_2D, 0);
        assertNoDriverError();
    }

    @AfterEach
    void checkDriverError() {
        assertNoDriverError();
    }

    private static int divisor(int attribute) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var value = stack.mallocInt(4);
            GLES20.glGetVertexAttribiv(attribute, GLES30.GL_VERTEX_ATTRIB_ARRAY_DIVISOR, value);
            return value.get(0);
        }
    }

    @AfterAll
    static void shutdown() {
        try {
            if (glsmInitialized) {
                GLSMFramebuffer.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
                GLSMFramebuffer.glDeleteFramebuffers(framebuffer);
                GLSMTextures.glDeleteTextures(colorAttachment);
                BackendManager.shutdown();
            }
        } finally {
            if (glesLoaded) {
                Lwjgl3Context.releaseGlesContext();
                GLES.destroy();
                GL.destroy();
            }
            try {
                if (context != 0L) {
                    assertTrue(SDLVideo.SDL_GL_DestroyContext(context), () -> sdlError("SDL_GL_DestroyContext"));
                }
            } finally {
                if (window != 0L) SDLVideo.SDL_DestroyWindow(window);
                SDL_Quit();
            }
        }
    }

    @Test
    void samplerParametersBindingAndDeletionReachDriver() {
        assertTrue(RenderSystem.supportsSamplerObjects());
        int sampler = RenderSystem.genSampler();
        assertNotEquals(0, sampler);
        try {
            RenderSystem.samplerParameteri(sampler, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            BackendManager.RENDER_BACKEND.samplerParameterf(sampler, GLES30.GL_TEXTURE_MAX_LOD, 4.5f);
            RenderSystem.bindSamplerToUnit(0, sampler);
            assertTrue(GLES30.glIsSampler(sampler));
            assertEquals(GLES20.GL_LINEAR, GLES30.glGetSamplerParameteri(sampler, GLES20.GL_TEXTURE_MIN_FILTER));
            assertEquals(4.5f, GLES30.glGetSamplerParameterf(sampler, GLES30.GL_TEXTURE_MAX_LOD));
            assertEquals(sampler, GLES20.glGetInteger(GLES30.GL_SAMPLER_BINDING));
        } finally {
            RenderSystem.destroySampler(sampler);
        }
        assertFalse(GLES30.glIsSampler(sampler));
        assertEquals(0, GLES20.glGetInteger(GLES30.GL_SAMPLER_BINDING));
    }

    @Test
    void instanceAttributeDivisorsBelongToTheBoundVao() {
        int first = GLSMVertexInput.glGenVertexArrays();
        int second = GLSMVertexInput.glGenVertexArrays();
        int attribute = 5;
        try {
            GLSMVertexInput.glBindVertexArray(first);
            GLSMVertexInput.glVertexAttribDivisor(attribute, 2);
            assertEquals(2, divisor(attribute));
            GLSMVertexInput.glBindVertexArray(second);
            assertEquals(0, divisor(attribute));
            GLSMVertexInput.glVertexAttribDivisor(attribute, 1);
            assertEquals(1, divisor(attribute));
            GLSMVertexInput.glBindVertexArray(first);
            assertEquals(2, divisor(attribute));
        } finally {
            GLSMVertexInput.glBindVertexArray(0);
            GLSMVertexInput.glDeleteVertexArrays(first);
            GLSMVertexInput.glDeleteVertexArrays(second);
        }
    }

    @Test
    void untexturedFixedFunctionQuadWritesVertexColor() {
        clearTarget();
        drawQuad(1.0f, 0.0f, 1.0f);
        assertEquals(0xff00ffff, readPixel(SIZE / 2, SIZE / 2));
    }

    @Test
    void texturedFixedFunctionQuadSamplesBothTexels() {
        clearTarget();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer texels = stack.bytes((byte) 255, (byte) 0, (byte) 0, (byte) 255,
                (byte) 0, (byte) 255, (byte) 255, (byte) 255);
            int texture = createTexture(2, 1, texels);
            try {
                GLSMEnable.glEnable(GL11C.GL_TEXTURE_2D);
                drawQuad(1.0f, 1.0f, 1.0f);
                assertEquals(0xff0000ff, readPixel(SIZE / 4, SIZE / 2), "Left texel");
                assertEquals(0x00ffffff, readPixel(3 * SIZE / 4, SIZE / 2), "Right texel");
            } finally {
                GLSMEnable.glDisable(GL11C.GL_TEXTURE_2D);
                GLSMTextures.glBindTexture(GL11C.GL_TEXTURE_2D, 0);
                GLSMTextures.glDeleteTextures(texture);
            }
        }
    }

    @Test
    void emulatedClipPlaneDiscardsOnlyTheEnabledHalfSpace() {
        boolean nativeClipDistance = RenderSystem.hasClipCullDistance();
        RenderSystem.initializeClipCullDistanceSupportForTesting(false);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            clearTarget();
            GLSMRasterizer.glClipPlane(GL30C.GL_CLIP_DISTANCE0 + 1, stack.doubles(0.0, 0.0, 0.0, -1.0));
            GLSMRasterizer.glClipPlane(GL30C.GL_CLIP_DISTANCE0, stack.doubles(1.0, 0.0, 0.0, 0.0));
            GLSMEnable.glEnable(GL30C.GL_CLIP_DISTANCE0);
            drawQuad(0.0f, 1.0f, 0.0f);
            assertEquals(BLACK, readPixel(SIZE / 4, SIZE / 2), "Pixels outside plane 0");
            assertEquals(0x00ff00ff, readPixel(3 * SIZE / 4, SIZE / 2), "Pixels inside plane 0, with plane 1 disabled");

            GLSMEnable.glDisable(GL30C.GL_CLIP_DISTANCE0);
            clearTarget();
            drawQuad(0.0f, 1.0f, 0.0f);
            assertEquals(0x00ff00ff, readPixel(SIZE / 4, SIZE / 2), "Pixels after disabling plane 0");
        } finally {
            GLSMEnable.glDisable(GL30C.GL_CLIP_DISTANCE0);
            RenderSystem.initializeClipCullDistanceSupportForTesting(nativeClipDistance);
        }
    }

    @Test
    void proxyTextureReportsDriverLimitAndClearsRejectedDimensions() {
        int maxSize = GLES20.glGetInteger(GLES20.GL_MAX_TEXTURE_SIZE);
        assertTrue(maxSize > 0);
        probeProxyTexture(maxSize);
        assertEquals(maxSize, proxyDimension(GL11C.GL_TEXTURE_WIDTH));
        assertEquals(1, proxyDimension(GL11C.GL_TEXTURE_HEIGHT));
        probeProxyTexture(maxSize + 1);
        assertEquals(0, proxyDimension(GL11C.GL_TEXTURE_WIDTH));
        assertEquals(0, proxyDimension(GL11C.GL_TEXTURE_HEIGHT));
    }

    private static void probeProxyTexture(int width) {
        GLSMTextures.glTexImage2D(GL11C.GL_PROXY_TEXTURE_2D, 0, GL11C.GL_RGBA8, width, 1, 0,
            GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
    }

    private static int proxyDimension(int pname) {
        return GLSMTextures.glGetTexLevelParameteri(GL11C.GL_PROXY_TEXTURE_2D, 0, pname);
    }

    private static int createTexture(int width, int height, ByteBuffer pixels) {
        int texture = GLSMTextures.glGenTextures();
        GLSMTextures.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
        GLSMTextures.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GLSMTextures.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GLSMTextures.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, width, height, 0,
            GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
        return texture;
    }

    private static void clearTarget() {
        GLSMFramebuffer.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
        GLSMRasterizer.glViewport(0, 0, SIZE, SIZE);
        GLSMTextures.glActiveTexture(GL13C.GL_TEXTURE0);
        GLSMEnable.glDisable(GL11C.GL_TEXTURE_2D);
        GLSMBlend.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLSMFramebuffer.glClear(GL11C.GL_COLOR_BUFFER_BIT);
    }

    private static void drawQuad(float red, float green, float blue) {
        GLSMDraws.glBegin(GL11C.GL_QUADS);
        GLSMCurrentAttribs.glColor4f(red, green, blue, 1.0f);
        quadVertex(-1.0f, -1.0f, 0.0f, 0.0f);
        quadVertex(1.0f, -1.0f, 1.0f, 0.0f);
        quadVertex(1.0f, 1.0f, 1.0f, 1.0f);
        quadVertex(-1.0f, 1.0f, 0.0f, 1.0f);
        GLSMDraws.glEnd();
    }

    private static void quadVertex(float x, float y, float u, float v) {
        GLSMCurrentAttribs.glTexCoord2f(u, v);
        GLSMDraws.glVertex3f(x, y, 0.0f);
    }

    private static int readPixel(int x, int y) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pixel = stack.malloc(4);
            GLSMFramebuffer.glReadPixels(x, y, 1, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixel);
            return (Byte.toUnsignedInt(pixel.get(0)) << 24) | (Byte.toUnsignedInt(pixel.get(1)) << 16)
                | (Byte.toUnsignedInt(pixel.get(2)) << 8) | Byte.toUnsignedInt(pixel.get(3));
        }
    }

    private static void assertNoDriverError() {
        int error = GLES20.glGetError();
        assertEquals(GLES20.GL_NO_ERROR, error, () -> "GLES driver error 0x" + Integer.toHexString(error));
    }

    private static void setContextAttribute(int attribute, int value) {
        assertTrue(SDLVideo.SDL_GL_SetAttribute(attribute, value), () -> sdlError("SDL_GL_SetAttribute " + attribute));
    }

    private static String sdlError(String operation) {
        return operation + " failed: " + SDL_GetError();
    }
}

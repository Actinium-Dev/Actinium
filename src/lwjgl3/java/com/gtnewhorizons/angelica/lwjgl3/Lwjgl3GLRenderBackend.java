package com.gtnewhorizons.angelica.lwjgl3;

import com.gtnewhorizons.angelica.glsm.GLESCaps;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.DebugMessageHandler;
import com.gtnewhorizons.angelica.glsm.backend.GLDebugMessageListener;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.gtnewhorizons.angelica.glsm.backend.WindowBackends;
import com.gtnewhorizons.angelica.glsm.texture.ProxyTexture;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import org.lwjgl.opengles.EXTClearTexture;
import org.lwjgl.system.Callback;
import org.lwjgl.opengles.GLES;
import org.lwjgl.opengles.GLES20;
import org.lwjgl.opengles.GLES30;
import org.lwjgl.opengles.GLES31;
import org.lwjgl.opengles.GLES32;
import org.lwjgl.opengles.GLESCapabilities;
import org.lwjgl.opengl.ARBClearTexture;
import org.lwjgl.opengl.ARBPolygonOffsetClamp;
import org.lwjgl.opengl.EXTDirectStateAccess;
import org.lwjgl.opengl.EXTPolygonOffsetClamp;
import org.lwjgl.opengl.GL;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.opengl.GL41C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL44C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.ShortBuffer;
import java.util.List;
import java.util.Locale;

import static com.gtnewhorizons.angelica.lwjgl3.Lwjgl3Context.isGlesContextCurrent;

/**
 * LWJGL3 GL implementation of {@link RenderBackend}.
 *
 * NOTE: This is _not_ redirected by lwjgl3ify at runtime and targets lwjgl3 directly
 */
public final class Lwjgl3GLRenderBackend extends RenderBackend {
    @Override
    public long getInteger64Indexed(int pname, int index) {
        if (isGlesContextCurrent()) {
            return GLES30.glGetInteger64i(pname, index);
        }
        return GL32C.glGetInteger64i(pname, index);
    }
    @Override
    public boolean isEnabledIndexed(int cap, int index) {
        if (isGlesContextCurrent()) {
            return GLES32.glIsEnabledi(cap, index);
        }
        return GL30C.glIsEnabledi(cap, index);
    }
    private GLCapabilities caps;
    private Callback debugCallback;
    private Callback appDebugCallback;
    private final ThreadLocal<ProxyTexture> proxyTextures = ThreadLocal.withInitial(() -> new ProxyTexture(
        GLES20.glGetInteger(GLES20.GL_MAX_TEXTURE_SIZE), GLES30.glGetInteger(GLES30.GL_MAX_3D_TEXTURE_SIZE),
        GLES20.glGetInteger(GLES20.GL_MAX_CUBE_MAP_TEXTURE_SIZE), GLES30.glGetInteger(GLES30.GL_MAX_ARRAY_TEXTURE_LAYERS)));
    private boolean debugOutputActive;
    private boolean amdShaderSourceWorkaround;

    @Override
    public void init() {
        if (isGlesContextCurrent()) {
            if (!GLES.getCapabilities().GLES32) throw new IllegalStateException("OpenGL ES 3.2 is required");
            return;
        }
        caps = GL.getCapabilities();
        // The Windows AMD driver truncates the source string LWJGL passes as a CharSequence;
        // handing it a null-terminated byte buffer through the raw entry point avoids that.
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            final String vendor = GL11C.glGetString(GL11C.GL_VENDOR);
            if (vendor != null) {
                final String normalizedVendor = vendor.toLowerCase(Locale.ROOT);
                amdShaderSourceWorkaround = normalizedVendor.startsWith("amd")
                    || normalizedVendor.startsWith("ati technologies")
                    || normalizedVendor.startsWith("advanced micro devices");
            }
        }
    }

    @Override
    public void shutdown() {
        proxyTextures.remove();
        stopFileDrop();
    }

    @Override
    public boolean supportsFileDrop() {
        return WindowBackends.get().supportsFileDrop();
    }

    @Override
    public void startFileDrop() {
        WindowBackends.get().startFileDrop();
    }

    @Override
    public void stopFileDrop() {
        WindowBackends.get().stopFileDrop();
    }

    @Override
    public List<String> pollDroppedFiles() {
        return WindowBackends.get().pollDroppedFiles();
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("org.lwjgl.opengl.GL31C");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public String getName() {
        return isGlesContextCurrent() ? "OpenGL ES (LWJGL3)" : "OpenGL (LWJGL3)";
    }

    @Override
    public boolean hasContext() {
        try {
            if (isGlesContextCurrent()) GLES.getCapabilities();
            else GL.getCapabilities();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    @Override
    public boolean isCurrent() {
        return hasContext();
    }

    @Override
    public int getPriority() { return 50; }

    @Override
    public int getMinGLSLVersion() { return 330; }

    @Override
    public boolean isAnisotropicSupported() {
        return !isGlesContextCurrent() || GLES.getCapabilities().GL_EXT_texture_filter_anisotropic;
    }

    @Override
    public boolean supportsGeometryShaders() {
        return !isGlesContextCurrent() || GLES.getCapabilities().GLES32;
    }

    @Override
    public void flush() {
        if (isGlesContextCurrent()) {
            GLES20.glFlush();
            return;
        }
        GL11C.glFlush();
    }

    @Override
    public void finish() {
        if (isGlesContextCurrent()) {
            GLES20.glFinish();
            return;
        }
        GL11C.glFinish();
    }

    private static boolean capAllowed(int cap) {
        return GLESCaps.isCapAllowed(cap, RenderSystem.isGLES(), RenderSystem.hasClipCullDistance());
    }

    @Override
    public void enable(int cap) {
        if (!capAllowed(cap)) return;
        if (isGlesContextCurrent()) GLES20.glEnable(cap);
        else GL11C.glEnable(cap);
    }

    @Override
    public void enablei(int cap, int index) {
        if (!capAllowed(cap)) return;
        if (isGlesContextCurrent()) GLES32.glEnablei(cap, index);
        else GL30C.glEnablei(cap, index);
    }

    @Override
    public void disable(int cap) {
        if (!capAllowed(cap)) return;
        if (isGlesContextCurrent()) GLES20.glDisable(cap);
        else GL11C.glDisable(cap);
    }

    @Override
    public void disablei(int cap, int index) {
        if (!capAllowed(cap)) return;
        if (isGlesContextCurrent()) GLES32.glDisablei(cap, index);
        else GL30C.glDisablei(cap, index);
    }

    @Override
    public void blendFuncSeparatei(int buf, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        if (isGlesContextCurrent()) {
            GLES32.glBlendFuncSeparatei(buf, srcRGB, dstRGB, srcAlpha, dstAlpha);
            return;
        }
        GL40C.glBlendFuncSeparatei(buf, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override
    public void blendFunc(int sfactor, int dfactor) {
        if (isGlesContextCurrent()) {
            GLES20.glBlendFunc(sfactor, dfactor);
            return;
        }
        GL11C.glBlendFunc(sfactor, dfactor);
    }

    @Override
    public void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        if (isGlesContextCurrent()) {
            GLES20.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
            return;
        }
        GL14C.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @Override
    public void blendEquation(int mode) {
        if (isGlesContextCurrent()) {
            GLES20.glBlendEquation(mode);
            return;
        }
        GL14C.glBlendEquation(mode);
    }

    @Override
    public void blendEquationSeparate(int modeRGB, int modeAlpha) {
        if (isGlesContextCurrent()) {
            GLES20.glBlendEquationSeparate(modeRGB, modeAlpha);
            return;
        }
        GL20C.glBlendEquationSeparate(modeRGB, modeAlpha);
    }

    @Override
    public void blendColor(float red, float green, float blue, float alpha) {
        if (isGlesContextCurrent()) {
            GLES20.glBlendColor(red, green, blue, alpha);
            return;
        }
        GL14C.glBlendColor(red, green, blue, alpha);
    }

    @Override
    public void depthFunc(int func) {
        if (isGlesContextCurrent()) {
            GLES20.glDepthFunc(func);
            return;
        }
        GL11C.glDepthFunc(func);
    }

    @Override
    public void depthMask(boolean flag) {
        if (isGlesContextCurrent()) {
            GLES20.glDepthMask(flag);
            return;
        }
        GL11C.glDepthMask(flag);
    }

    @Override
    public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        if (isGlesContextCurrent()) {
            GLES20.glColorMask(red, green, blue, alpha);
            return;
        }
        GL11C.glColorMask(red, green, blue, alpha);
    }

    @Override
    public void cullFace(int mode) {
        if (isGlesContextCurrent()) {
            GLES20.glCullFace(mode);
            return;
        }
        GL11C.glCullFace(mode);
    }

    @Override
    public void frontFace(int mode) {
        if (isGlesContextCurrent()) {
            GLES20.glFrontFace(mode);
            return;
        }
        GL11C.glFrontFace(mode);
    }

    @Override
    public void polygonMode(int face, int mode) {
        if (isGlesContextCurrent()) {
            GlesCalls.polygonMode(face, mode);
            return;
        }
        GL11C.glPolygonMode(face, mode);
    }

    @Override
    public void polygonOffset(float factor, float units) {
        if (isGlesContextCurrent()) {
            GLES20.glPolygonOffset(factor, units);
            return;
        }
        GL11C.glPolygonOffset(factor, units);
    }

    @Override
    public void polygonOffsetClamp(float factor, float units, float clamp) {
        if (isGlesContextCurrent()) {
            GlesCalls.polygonOffsetClamp(factor, units, clamp);
        } else if (caps == null) {
            super.polygonOffsetClamp(factor, units, clamp);
        } else if (caps.OpenGL46) {
            GL46C.glPolygonOffsetClamp(factor, units, clamp);
        } else if (caps.GL_ARB_polygon_offset_clamp) {
            ARBPolygonOffsetClamp.glPolygonOffsetClamp(factor, units, clamp);
        } else if (caps.GL_EXT_polygon_offset_clamp) {
            EXTPolygonOffsetClamp.glPolygonOffsetClampEXT(factor, units, clamp);
        } else {
            super.polygonOffsetClamp(factor, units, clamp);
        }
    }

    @Override
    public void stencilFunc(int func, int ref, int mask) {
        if (isGlesContextCurrent()) {
            GLES20.glStencilFunc(func, ref, mask);
            return;
        }
        GL11C.glStencilFunc(func, ref, mask);
    }

    @Override
    public void stencilOp(int sfail, int dpfail, int dppass) {
        if (isGlesContextCurrent()) {
            GLES20.glStencilOp(sfail, dpfail, dppass);
            return;
        }
        GL11C.glStencilOp(sfail, dpfail, dppass);
    }

    @Override
    public void stencilMask(int mask) {
        if (isGlesContextCurrent()) {
            GLES20.glStencilMask(mask);
            return;
        }
        GL11C.glStencilMask(mask);
    }

    @Override
    public void stencilFuncSeparate(int face, int func, int ref, int mask) {
        if (isGlesContextCurrent()) {
            GLES20.glStencilFuncSeparate(face, func, ref, mask);
            return;
        }
        GL20C.glStencilFuncSeparate(face, func, ref, mask);
    }

    @Override
    public void stencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        if (isGlesContextCurrent()) {
            GLES20.glStencilOpSeparate(face, sfail, dpfail, dppass);
            return;
        }
        GL20C.glStencilOpSeparate(face, sfail, dpfail, dppass);
    }

    @Override
    public void stencilMaskSeparate(int face, int mask) {
        if (isGlesContextCurrent()) {
            GLES20.glStencilMaskSeparate(face, mask);
            return;
        }
        GL20C.glStencilMaskSeparate(face, mask);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        if (isGlesContextCurrent()) {
            GLES20.glViewport(x, y, width, height);
            return;
        }
        GL11C.glViewport(x, y, width, height);
    }

    @Override
    public void depthRange(double nearVal, double farVal) {
        if (isGlesContextCurrent()) {
            GLES20.glDepthRangef((float) nearVal, (float) farVal);
            return;
        }
        GL11C.glDepthRange(nearVal, farVal);
    }

    @Override
    public void scissor(int x, int y, int width, int height) {
        if (isGlesContextCurrent()) {
            GLES20.glScissor(x, y, width, height);
            return;
        }
        GL11C.glScissor(x, y, width, height);
    }

    @Override
    public void clearColor(float red, float green, float blue, float alpha) {
        if (isGlesContextCurrent()) {
            GLES20.glClearColor(red, green, blue, alpha);
            return;
        }
        GL11C.glClearColor(red, green, blue, alpha);
    }

    @Override
    public void clearDepth(double depth) {
        if (isGlesContextCurrent()) {
            GLES20.glClearDepthf((float) depth);
            return;
        }
        GL11C.glClearDepth(depth);
    }

    @Override
    public void clearStencil(int s) {
        if (isGlesContextCurrent()) {
            GLES20.glClearStencil(s);
            return;
        }
        GL11C.glClearStencil(s);
    }

    @Override
    public void clear(int mask) {
        if (isGlesContextCurrent()) {
            GLES20.glClear(mask);
            return;
        }
        GL11C.glClear(mask);
    }

    @Override
    public void lineWidth(float width) {
        if (isGlesContextCurrent()) {
            GLES20.glLineWidth(width);
            return;
        }
        GL11C.glLineWidth(width);
    }

    @Override
    public void pointSize(float size) {
        if (isGlesContextCurrent()) {
            return;
        }
        GL11C.glPointSize(size);
    }

    @Override
    public void logicOp(int opcode) {
        if (isGlesContextCurrent()) {
            return;
        }
        GL11C.glLogicOp(opcode);
    }

    @Override
    public void hint(int target, int mode) {
        if (isGlesContextCurrent()) {
            GLES20.glHint(target, mode);
            return;
        }
        GL11C.glHint(target, mode);
    }

    @Override
    public void drawArrays(int mode, int first, int count) {
        if (isGlesContextCurrent()) {
            submitOpenGLDraw(() -> GLES20.glDrawArrays(mode, first, count));
            return;
        }
        submitOpenGLDraw(() -> GL11C.glDrawArrays(mode, first, count));
    }

    @Override
    public void drawElements(int mode, int count, int type, long indices) {
        if (isGlesContextCurrent()) {
            submitOpenGLDraw(() -> GLES20.glDrawElements(mode, count, type, indices));
            return;
        }
        submitOpenGLDraw(() -> GL11C.glDrawElements(mode, count, type, indices));
    }

    @Override
    public void drawElements(int mode, ByteBuffer indices) {
        if (isGlesContextCurrent()) {
            submitOpenGLDraw(() -> GLES20.glDrawElements(mode, indices));
            return;
        }
        submitOpenGLDraw(() -> GL11C.glDrawElements(mode, indices));
    }

    @Override
    public void drawElements(int mode, IntBuffer indices) {
        if (isGlesContextCurrent()) {
            submitOpenGLDraw(() -> GLES20.glDrawElements(mode, indices));
            return;
        }
        submitOpenGLDraw(() -> GL11C.glDrawElements(mode, indices));
    }

    @Override
    public void drawElements(int mode, ShortBuffer indices) {
        if (isGlesContextCurrent()) {
            submitOpenGLDraw(() -> GLES20.glDrawElements(mode, indices));
            return;
        }
        submitOpenGLDraw(() -> GL11C.glDrawElements(mode, indices));
    }

    @Override
    public void drawElements(int mode, int count, int type, ByteBuffer indices) {
        if (isGlesContextCurrent()) {
            submitOpenGLDraw(() -> GLES20.glDrawElements(mode, count, type, MemoryUtil.memAddress(indices)));
            return;
        }
        submitOpenGLDraw(() -> GL11C.glDrawElements(mode, count, type, MemoryUtil.memAddress(indices)));
    }

    @Override
    public void drawElementsInstanced(int mode, int count, int type, long indices, int primcount) {
        if (isGlesContextCurrent()) {
            submitOpenGLDraw(() -> GLES30.glDrawElementsInstanced(mode, count, type, indices, primcount));
            return;
        }
        submitOpenGLDraw(() -> GL31C.glDrawElementsInstanced(mode, count, type, indices, primcount));
    }

    @Override
    public boolean supportsInstancedDrawing() {
        return isGlesContextCurrent() ? GLES.getCapabilities().GLES30 : GL.getCapabilities().OpenGL33;
    }

    @Override
    public void drawArraysInstanced(int mode, int first, int count, int primcount) {
        if (isGlesContextCurrent()) {
            submitOpenGLDraw(() -> GLES30.glDrawArraysInstanced(mode, first, count, primcount));
            return;
        }
        submitOpenGLDraw(() -> GL31C.glDrawArraysInstanced(mode, first, count, primcount));
    }

    @Override
    public void provokingVertex(int provokeMode) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glProvokingVertex");
        }
        GL32C.glProvokingVertex(provokeMode);
    }

    @Override
    public void multiDrawElementsIndirect(int mode, int type, long indirect, int drawcount, int stride) {
        if (isGlesContextCurrent()) {
            submitOpenGLDraw(() -> GlesCalls.multiDrawElementsIndirect(mode, type, indirect, drawcount, stride));
            return;
        }
        submitOpenGLDraw(() -> GL43C.glMultiDrawElementsIndirect(mode, type, indirect, drawcount, stride));
    }

    @Override
    public void multiDrawArraysIndirect(int mode, long indirect, int drawcount, int stride) {
        if (isGlesContextCurrent()) {
            submitOpenGLDraw(() -> GlesCalls.multiDrawArraysIndirect(mode, indirect, drawcount, stride));
            return;
        }
        submitOpenGLDraw(() -> GL43C.glMultiDrawArraysIndirect(mode, indirect, drawcount, stride));
    }

    @Override
    public void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        if (isGlesContextCurrent()) {
            GLES30.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
            return;
        }
        GL31C.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }

    @Override
    public void drawElementsBaseVertex(int mode, int count, int type, long indices, int baseVertex) {
        if (isGlesContextCurrent()) {
            submitOpenGLDraw(() -> GLES32.glDrawElementsBaseVertex(mode, count, type, indices, baseVertex));
            return;
        }
        submitOpenGLDraw(() -> GL32C.glDrawElementsBaseVertex(mode, count, type, indices, baseVertex));
    }

    @Override
    public void multiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawcount, long pBaseVertex) {
        if (isGlesContextCurrent()) {
            submitOpenGLDraw(() -> GlesCalls.multiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawcount, pBaseVertex));
            return;
        }
        submitOpenGLDraw(() -> GL32C.nglMultiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawcount, pBaseVertex));
    }

    @Override
    public void multiDrawArrays(int mode, IntBuffer firsts, IntBuffer counts) {
        if (isGlesContextCurrent()) {
            submitOpenGLDraw(() -> GlesCalls.multiDrawArrays(mode, firsts, counts));
            return;
        }
        submitOpenGLDraw(() -> GL14C.glMultiDrawArrays(mode, firsts, counts));
    }

    @Override
    public void multiDrawArrays(int mode, int[] firsts, int[] counts) {
        if (isGlesContextCurrent()) {
            submitOpenGLDraw(() -> GlesCalls.multiDrawArrays(mode, firsts, counts));
            return;
        }
        submitOpenGLDraw(() -> GL14C.glMultiDrawArrays(mode, firsts, counts));
    }

    @Override
    public void primitiveRestartIndex(int index) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glPrimitiveRestartIndex");
        }
        GL31C.glPrimitiveRestartIndex(index);
    }

    @Override
    public void pointParameterf(int pname, float param) {
        if (isGlesContextCurrent()) {
            return;
        }
        GL14C.glPointParameterf(pname, param);
    }

    @Override
    public void pointParameteri(int pname, int param) {
        if (isGlesContextCurrent()) {
            return;
        }
        GL14C.glPointParameteri(pname, param);
    }

    @Override
    public void drawBuffer(int mode) {
        if (isGlesContextCurrent()) {
            GLES30.glDrawBuffers(mode);
            return;
        }
        GL11C.glDrawBuffer(mode);
    }

    @Override
    public void dispatchCompute(int numGroupsX, int numGroupsY, int numGroupsZ) {
        if (isGlesContextCurrent()) {
            GLES31.glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
            return;
        }
        GL43C.glDispatchCompute(numGroupsX, numGroupsY, numGroupsZ);
    }

    @Override
    public void dispatchComputeIndirect(long offset) {
        if (isGlesContextCurrent()) {
            GLES31.glDispatchComputeIndirect(offset);
            return;
        }
        GL43C.glDispatchComputeIndirect(offset);
    }

    @Override
    public int genTextures() {
        if (isGlesContextCurrent()) {
            return GLES20.glGenTextures();
        }
        return GL11C.glGenTextures();
    }

    @Override
    public void genTextures(IntBuffer textures) {
        if (isGlesContextCurrent()) {
            GLES20.glGenTextures(textures);
            return;
        }
        GL11C.glGenTextures(textures);
    }

    @Override
    public void deleteTextures(int texture) {
        if (isGlesContextCurrent()) {
            GLES20.glDeleteTextures(texture);
            return;
        }
        GL11C.glDeleteTextures(texture);
    }

    @Override
    public void bindTexture(int target, int texture) {
        if (isGlesContextCurrent()) {
            GLES20.glBindTexture(target, texture);
            return;
        }
        GL11C.glBindTexture(target, texture);
    }

    @Override
    public void activeTexture(int texture) {
        if (isGlesContextCurrent()) {
            GLES20.glActiveTexture(texture);
            return;
        }
        GL13C.glActiveTexture(texture);
    }

    private boolean recordProxy(int target, int level, int internalFormat, int width, int height, int depth) {
        if (!TextureInfoCache.isProxyTarget(target)) return false;
        proxyTextures.get().record(target, level, internalFormat, width, height, depth, target != GL11C.GL_PROXY_TEXTURE_1D);
        return true;
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            if (!recordProxy(target, level, internalFormat, width, height, 1)) {
                GLES20.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
            }
            return;
        }
        GL11C.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, DoubleBuffer pixels) {
        if (isGlesContextCurrent()) {
            if (!recordProxy(target, level, internalFormat, width, height, 1)) {
                throw GlesCalls.unsupported("glTexImage2D with GL_DOUBLE");
            }
            return;
        }
        GL11C.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, FloatBuffer pixels) {
        if (isGlesContextCurrent()) {
            if (!recordProxy(target, level, internalFormat, width, height, 1)) {
                GLES20.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
            }
            return;
        }
        GL11C.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        if (isGlesContextCurrent()) {
            if (!recordProxy(target, level, internalFormat, width, height, 1)) {
                GLES20.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
            }
            return;
        }
        GL11C.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, long pixelBufferOffset) {
        if (isGlesContextCurrent()) {
            if (!recordProxy(target, level, internalFormat, width, height, 1)) {
                GLES20.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixelBufferOffset);
            }
            return;
        }
        GL11C.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixelBufferOffset);
    }

    @Override
    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            GLES20.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
            return;
        }
        GL11C.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override
    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        if (isGlesContextCurrent()) {
            GLES20.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
            return;
        }
        GL11C.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override
    public void copyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        if (isGlesContextCurrent()) {
            GLES20.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
            return;
        }
        GL11C.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }

    @Override
    public void texImage1D(int target, int level, int internalFormat, int width, int border, int format, int type, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            if (!recordProxy(target, level, internalFormat, width, 1, 1)) {
                throw GlesCalls.unsupported("glTexImage1D");
            }
            return;
        }
        GL11C.glTexImage1D(target, level, internalFormat, width, border, format, type, pixels);
    }

    @Override
    public void texImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            if (!recordProxy(target, level, internalFormat, width, height, depth)) {
                GLES30.glTexImage3D(target, level, internalFormat, width, height, depth, border, format, type, pixels);
            }
            return;
        }
        GL12C.glTexImage3D(target, level, internalFormat, width, height, depth, border, format, type, pixels);
    }

    @Override
    public void texImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, int format, int type, IntBuffer pixels) {
        if (isGlesContextCurrent()) {
            if (!recordProxy(target, level, internalFormat, width, height, depth)) {
                GLES30.glTexImage3D(target, level, internalFormat, width, height, depth, border, format, type, pixels);
            }
            return;
        }
        GL12C.glTexImage3D(target, level, internalFormat, width, height, depth, border, format, type, pixels);
    }

    @Override
    public void texSubImage1D(int target, int level, int xoffset, int width, int format, int type, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glTexSubImage1D");
        }
        GL11C.glTexSubImage1D(target, level, xoffset, width, format, type, pixels);
    }

    @Override
    public void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, long pboOffset) {
        if (isGlesContextCurrent()) {
            GLES20.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pboOffset);
            return;
        }
        GL11C.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pboOffset);
    }

    @Override
    public void texSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type,
            ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            GLES30.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, pixels);
            return;
        }
        GL12C.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, pixels);
    }

    @Override
    public void copyTexImage1D(int target, int level, int internalFormat, int x, int y, int width, int border) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glCopyTexImage1D");
        }
        GL11C.glCopyTexImage1D(target, level, internalFormat, x, y, width, border);
    }

    @Override
    public void copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        if (isGlesContextCurrent()) {
            GLES20.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
            return;
        }
        GL11C.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
    }

    @Override
    public void copyTexSubImage1D(int target, int level, int xoffset, int x, int y, int width) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glCopyTexSubImage1D");
        }
        GL11C.glCopyTexSubImage1D(target, level, xoffset, x, y, width);
    }

    @Override
    public void copyTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {
        if (isGlesContextCurrent()) {
            GLES30.glCopyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height);
            return;
        }
        GL12C.glCopyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height);
    }

    @Override
    public void texParameteri(int target, int pname, int param) {
        if (isGlesContextCurrent()) {
            GLES20.glTexParameteri(target, pname, param);
            return;
        }
        GL11C.glTexParameteri(target, pname, param);
    }

    @Override
    public void texParameterf(int target, int pname, float param) {
        if (isGlesContextCurrent()) {
            GLES20.glTexParameterf(target, pname, param);
            return;
        }
        GL11C.glTexParameterf(target, pname, param);
    }

    @Override
    public void texParameteriv(int target, int pname, IntBuffer params) {
        if (isGlesContextCurrent()) {
            GLES20.glTexParameteriv(target, pname, params);
            return;
        }
        GL11C.glTexParameteriv(target, pname, params);
    }

    @Override
    public void texParameterfv(int target, int pname, FloatBuffer params) {
        if (isGlesContextCurrent()) {
            GLES20.glTexParameterfv(target, pname, params);
            return;
        }
        GL11C.glTexParameterfv(target, pname, params);
    }

    @Override
    public int getTexParameteri(int target, int pname) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetTexParameteri(target, pname);
        }
        return GL11C.glGetTexParameteri(target, pname);
    }

    @Override
    public float getTexParameterf(int target, int pname) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetTexParameterf(target, pname);
        }
        return GL11C.glGetTexParameterf(target, pname);
    }

    @Override
    public int getTexLevelParameteri(int target, int level, int pname) {
        if (isGlesContextCurrent()) {
            if (TextureInfoCache.isProxyTarget(target)) return proxyTextures.get().levelParameter(target, level, pname);
            return GLES31.glGetTexLevelParameteri(target, level, pname);
        }
        return GL11C.glGetTexLevelParameteri(target, level, pname);
    }

    @Override
    public void generateMipmap(int target) {
        if (isGlesContextCurrent()) {
            GLES20.glGenerateMipmap(target);
            return;
        }
        GL30C.glGenerateMipmap(target);
    }

    @Override
    public void pixelStorei(int pname, int param) {
        if (isGlesContextCurrent()) {
            GLES20.glPixelStorei(pname, param);
            return;
        }
        GL11C.glPixelStorei(pname, param);
    }

    @Override
    public void pixelStoref(int pname, float param) {
        if (isGlesContextCurrent()) {
            GLES20.glPixelStorei(pname, (int) Math.floor(param + 0.5f));
            return;
        }
        GL11C.glPixelStoref(pname, param);
    }

    @Override
    public void sampleCoverage(float value, boolean invert) {
        if (isGlesContextCurrent()) {
            GLES20.glSampleCoverage(value, invert);
            return;
        }
        GL13C.glSampleCoverage(value, invert);
    }

    @Override
    public int genSamplers() {
        if (isGlesContextCurrent()) {
            return GLES30.glGenSamplers();
        }
        return GL33C.glGenSamplers();
    }

    @Override
    public void deleteSamplers(int sampler) {
        if (isGlesContextCurrent()) {
            GLES30.glDeleteSamplers(sampler);
            return;
        }
        GL33C.glDeleteSamplers(sampler);
    }

    @Override
    public void bindSampler(int unit, int sampler) {
        if (isGlesContextCurrent()) {
            GLES30.glBindSampler(unit, sampler);
            return;
        }
        GL33C.glBindSampler(unit, sampler);
    }

    @Override
    public void samplerParameteri(int sampler, int pname, int param) {
        if (isGlesContextCurrent()) {
            GLES30.glSamplerParameteri(sampler, pname, param);
            return;
        }
        GL33C.glSamplerParameteri(sampler, pname, param);
    }

    @Override
    public void samplerParameterf(int sampler, int pname, float param) {
        if (isGlesContextCurrent()) {
            GLES30.glSamplerParameterf(sampler, pname, param);
            return;
        }
        GL33C.glSamplerParameterf(sampler, pname, param);
    }

    @Override
    public int genFramebuffers() {
        if (isGlesContextCurrent()) {
            return GLES20.glGenFramebuffers();
        }
        return GL30C.glGenFramebuffers();
    }

    @Override
    public void deleteFramebuffers(int framebuffer) {
        if (isGlesContextCurrent()) {
            GLES20.glDeleteFramebuffers(framebuffer);
            return;
        }
        GL30C.glDeleteFramebuffers(framebuffer);
    }

    @Override
    public void bindFramebuffer(int target, int framebuffer) {
        if (isGlesContextCurrent()) {
            GLES20.glBindFramebuffer(target, framebuffer);
            return;
        }
        GL30C.glBindFramebuffer(target, framebuffer);
    }

    @Override
    public void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        if (isGlesContextCurrent()) {
            GLES20.glFramebufferTexture2D(target, attachment, textarget, texture, level);
            return;
        }
        GL30C.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    @Override
    public void framebufferTexture1D(int target, int attachment, int textarget, int texture, int level) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glFramebufferTexture1D");
        }
        GL30C.glFramebufferTexture1D(target, attachment, textarget, texture, level);
    }

    @Override
    public void framebufferTexture3D(int target, int attachment, int textarget, int texture, int level, int zoffset) {
        if (isGlesContextCurrent()) {
            GLES30.glFramebufferTextureLayer(target, attachment, texture, level, zoffset);
            return;
        }
        GL30C.glFramebufferTexture3D(target, attachment, textarget, texture, level, zoffset);
    }

    @Override
    public void framebufferTextureLayer(int target, int attachment, int texture, int level, int layer) {
        if (isGlesContextCurrent()) {
            GLES30.glFramebufferTextureLayer(target, attachment, texture, level, layer);
            return;
        }
        GL30C.glFramebufferTextureLayer(target, attachment, texture, level, layer);
    }

    @Override
    public void framebufferTexture(int target, int attachment, int texture, int level) {
        if (isGlesContextCurrent()) {
            GLES32.glFramebufferTexture(target, attachment, texture, level);
            return;
        }
        GL32C.glFramebufferTexture(target, attachment, texture, level);
    }

    @Override
    public int genRenderbuffers() {
        if (isGlesContextCurrent()) {
            return GLES20.glGenRenderbuffers();
        }
        return GL30C.glGenRenderbuffers();
    }

    @Override
    public void deleteRenderbuffers(int renderbuffer) {
        if (isGlesContextCurrent()) {
            GLES20.glDeleteRenderbuffers(renderbuffer);
            return;
        }
        GL30C.glDeleteRenderbuffers(renderbuffer);
    }

    @Override
    public void bindRenderbuffer(int target, int renderbuffer) {
        if (isGlesContextCurrent()) {
            GLES20.glBindRenderbuffer(target, renderbuffer);
            return;
        }
        GL30C.glBindRenderbuffer(target, renderbuffer);
    }

    @Override
    public void renderbufferStorage(int target, int internalformat, int width, int height) {
        if (isGlesContextCurrent()) {
            GLES20.glRenderbufferStorage(target, internalformat, width, height);
            return;
        }
        GL30C.glRenderbufferStorage(target, internalformat, width, height);
    }

    @Override
    public void renderbufferStorageMultisample(int target, int samples, int internalformat, int width, int height) {
        if (isGlesContextCurrent()) {
            GLES30.glRenderbufferStorageMultisample(target, samples, internalformat, width, height);
            return;
        }
        GL30C.glRenderbufferStorageMultisample(target, samples, internalformat, width, height);
    }

    @Override
    public void framebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
        if (isGlesContextCurrent()) {
            GLES20.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer);
            return;
        }
        GL30C.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer);
    }

    @Override
    public int checkFramebufferStatus(int target) {
        if (isGlesContextCurrent()) {
            return GLES20.glCheckFramebufferStatus(target);
        }
        return GL30C.glCheckFramebufferStatus(target);
    }

    @Override
    public void blitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        if (isGlesContextCurrent()) {
            GLES30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
            return;
        }
        GL30C.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    @Override
    public void drawBuffers(int buffer) {
        if (isGlesContextCurrent()) {
            GLES30.glDrawBuffers(buffer);
            return;
        }
        GL20C.glDrawBuffers(buffer);
    }

    @Override
    public void drawBuffers(IntBuffer bufs) {
        if (isGlesContextCurrent()) {
            GLES30.glDrawBuffers(bufs);
            return;
        }
        GL20C.glDrawBuffers(bufs);
    }

    @Override
    public void drawBuffers(int[] bufs) {
        if (isGlesContextCurrent()) {
            GLES30.glDrawBuffers(bufs);
            return;
        }
        GL20C.glDrawBuffers(bufs);
    }

    @Override
    public void readBuffer(int mode) {
        if (isGlesContextCurrent()) {
            GLES30.glReadBuffer(mode);
            return;
        }
        GL11C.glReadBuffer(mode);
    }

    @Override
    public void readPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            GLES20.glReadPixels(x, y, width, height, format, type, pixels);
            return;
        }
        GL11C.glReadPixels(x, y, width, height, format, type, pixels);
    }

    @Override
    public void readPixels(int x, int y, int width, int height, int format, int type, FloatBuffer pixels) {
        if (isGlesContextCurrent()) {
            GLES20.glReadPixels(x, y, width, height, format, type, pixels);
            return;
        }
        GL11C.glReadPixels(x, y, width, height, format, type, pixels);
    }

    @Override
    public void readPixels(int x, int y, int width, int height, int format, int type, IntBuffer pixels) {
        if (isGlesContextCurrent()) {
            GLES20.glReadPixels(x, y, width, height, format, type, pixels);
            return;
        }
        GL11C.glReadPixels(x, y, width, height, format, type, pixels);
    }

    @Override
    public void readPixels(int x, int y, int width, int height, int format, int type, long pixelBufferOffset) {
        if (isGlesContextCurrent()) {
            GLES20.glReadPixels(x, y, width, height, format, type, pixelBufferOffset);
            return;
        }
        GL11C.glReadPixels(x, y, width, height, format, type, pixelBufferOffset);
    }

    @Override
    public void getTexImage(int target, int level, int format, int type, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            GlesTextureReadback.read(target, level, format, type, pixels);
            return;
        }
        GL11C.glGetTexImage(target, level, format, type, pixels);
    }

    @Override
    public void getTexImage(int target, int level, int format, int type, IntBuffer pixels) {
        if (isGlesContextCurrent()) {
            GlesTextureReadback.read(target, level, format, type, pixels);
            return;
        }
        GL11C.glGetTexImage(target, level, format, type, pixels);
    }

    @Override
    public void getTexImage(int target, int level, int format, int type, long pixelBufferOffset) {
        if (isGlesContextCurrent()) {
            GlesTextureReadback.read(target, level, format, type, pixelBufferOffset);
            return;
        }
        GL11C.glGetTexImage(target, level, format, type, pixelBufferOffset);
    }

    @Override
    public int getFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetFramebufferAttachmentParameteri(target, attachment, pname);
        }
        return GL30C.glGetFramebufferAttachmentParameteri(target, attachment, pname);
    }

    @Override
    public int createShader(int type) {
        if (isGlesContextCurrent()) {
            return GLES20.glCreateShader(type);
        }
        return GL20C.glCreateShader(type);
    }

    @Override
    public void deleteShader(int shader) {
        if (isGlesContextCurrent()) {
            GLES20.glDeleteShader(shader);
            return;
        }
        GL20C.glDeleteShader(shader);
    }

    @Override
    public void shaderSource(int shader, CharSequence source) {
        if (isGlesContextCurrent()) {
            GLES20.glShaderSource(shader, source);
            return;
        }
        if (!amdShaderSourceWorkaround) {
            GL20C.glShaderSource(shader, source);
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final ByteBuffer encodedSource = MemoryUtil.memUTF8(source, true);
            try {
                final PointerBuffer pointers = stack.mallocPointer(1);
                pointers.put(encodedSource);
                GL20C.nglShaderSource(shader, 1, pointers.address0(), 0L);
            } finally {
                MemoryUtil.memFree(encodedSource);
            }
        }
    }

    @Override
    public void compileShader(int shader) {
        if (isGlesContextCurrent()) {
            GLES20.glCompileShader(shader);
            return;
        }
        GL20C.glCompileShader(shader);
    }

    @Override
    public int createProgram() {
        if (isGlesContextCurrent()) {
            return GLES20.glCreateProgram();
        }
        return GL20C.glCreateProgram();
    }

    @Override
    public void deleteProgram(int program) {
        if (isGlesContextCurrent()) {
            GLES20.glDeleteProgram(program);
            return;
        }
        GL20C.glDeleteProgram(program);
    }

    @Override
    public void attachShader(int program, int shader) {
        if (isGlesContextCurrent()) {
            GLES20.glAttachShader(program, shader);
            return;
        }
        GL20C.glAttachShader(program, shader);
    }

    @Override
    public void detachShader(int program, int shader) {
        if (isGlesContextCurrent()) {
            GLES20.glDetachShader(program, shader);
            return;
        }
        GL20C.glDetachShader(program, shader);
    }

    @Override
    public void linkProgram(int program) {
        if (isGlesContextCurrent()) {
            GLES20.glLinkProgram(program);
            return;
        }
        GL20C.glLinkProgram(program);
    }

    @Override
    public void useProgram(int program) {
        if (isGlesContextCurrent()) {
            GLES20.glUseProgram(program);
            return;
        }
        GL20C.glUseProgram(program);
    }

    @Override
    public String getShaderInfoLog(int shader, int maxLength) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetShaderInfoLog(shader, maxLength);
        }
        return GL20C.glGetShaderInfoLog(shader, maxLength);
    }

    @Override
    public void getShaderInfoLog(int shader, IntBuffer length, ByteBuffer infoLog) {
        if (isGlesContextCurrent()) {
            GLES20.glGetShaderInfoLog(shader, length, infoLog);
            return;
        }
        GL20C.glGetShaderInfoLog(shader, length, infoLog);
    }

    @Override
    public void getShaderInfoLog(int shader, int[] length, ByteBuffer infoLog) {
        if (isGlesContextCurrent()) {
            GLES20.glGetShaderInfoLog(shader, length, infoLog);
            return;
        }
        GL20C.glGetShaderInfoLog(shader, length, infoLog);
    }

    @Override
    public String getProgramInfoLog(int program, int maxLength) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetProgramInfoLog(program, maxLength);
        }
        return GL20C.glGetProgramInfoLog(program, maxLength);
    }

    @Override
    public void getProgramInfoLog(int program, IntBuffer length, ByteBuffer infoLog) {
        if (isGlesContextCurrent()) {
            GLES20.glGetProgramInfoLog(program, length, infoLog);
            return;
        }
        GL20C.glGetProgramInfoLog(program, length, infoLog);
    }

    @Override
    public void getProgramInfoLog(int program, int[] length, ByteBuffer infoLog) {
        if (isGlesContextCurrent()) {
            GLES20.glGetProgramInfoLog(program, length, infoLog);
            return;
        }
        GL20C.glGetProgramInfoLog(program, length, infoLog);
    }

    @Override
    public int getShaderi(int shader, int pname) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetShaderi(shader, pname);
        }
        return GL20C.glGetShaderi(shader, pname);
    }

    @Override
    public int getProgrami(int program, int pname) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetProgrami(program, pname);
        }
        return GL20C.glGetProgrami(program, pname);
    }

    @Override
    public void getProgramiv(int program, int pname, IntBuffer params) {
        if (isGlesContextCurrent()) {
            GLES20.glGetProgramiv(program, pname, params);
            return;
        }
        GL20C.glGetProgramiv(program, pname, params);
    }

    @Override
    public String getActiveUniform(int program, int index, int maxLength, IntBuffer sizeType) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final IntBuffer size = stack.mallocInt(1);
            final IntBuffer type = stack.mallocInt(1);
            final String name = isGlesContextCurrent()
                ? GLES20.glGetActiveUniform(program, index, maxLength, size, type)
                : GL20C.glGetActiveUniform(program, index, maxLength, size, type);
            sizeType.put(0, size.get(0));
            sizeType.put(1, type.get(0));
            return name;
        }
    }

    @Override
    public void getActiveUniform(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        if (isGlesContextCurrent()) {
            GLES20.glGetActiveUniform(program, index, length, size, type, name);
            return;
        }
        GL20C.glGetActiveUniform(program, index, length, size, type, name);
    }

    @Override
    public void getActiveUniform(int program, int index, int[] length, int[] size, int[] type, ByteBuffer name) {
        if (isGlesContextCurrent()) {
            GLES20.glGetActiveUniform(program, index, length, size, type, name);
            return;
        }
        GL20C.glGetActiveUniform(program, index, length, size, type, name);
    }

    @Override
    public void getActiveAttrib(int program, int index, IntBuffer length, IntBuffer size, IntBuffer type, ByteBuffer name) {
        if (isGlesContextCurrent()) {
            GLES20.glGetActiveAttrib(program, index, length, size, type, name);
            return;
        }
        final String nameStr = GL20C.glGetActiveAttrib(program, index, size, type);
        if (name != null && nameStr != null) {
            final byte[] bytes = nameStr.getBytes(StandardCharsets.UTF_8);
            final int n = Math.min(bytes.length, name.remaining());
            name.put(bytes, 0, n);
            if (length != null && length.remaining() > 0) length.put(0, n);
        }
    }

    @Override
    public void getActiveAttrib(int program, int index, int[] length, int[] size, int[] type, ByteBuffer name) {
        if (isGlesContextCurrent()) {
            GLES20.glGetActiveAttrib(program, index, length, size, type, name);
            return;
        }
        GL20C.glGetActiveAttrib(program, index, length, size, type, name);
    }

    @Override
    public String getActiveAttrib(int program, int index, int maxLength, IntBuffer sizeType) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final IntBuffer size = stack.callocInt(1);
            final IntBuffer type = stack.callocInt(1);
            final String name = isGlesContextCurrent()
                ? GLES20.glGetActiveAttrib(program, index, maxLength, size, type)
                : GL20C.glGetActiveAttrib(program, index, size, type);
            if (sizeType != null && sizeType.remaining() >= 2) {
                sizeType.put(0, size.get(0));
                sizeType.put(1, type.get(0));
            }
            return name;
        }
    }

    @Override
    public void bindFragDataLocation(int program, int colorNumber, CharSequence name) {
        if (isGlesContextCurrent()) {
            return;
        }
        GL30C.glBindFragDataLocation(program, colorNumber, name);
    }

    @Override
    public void bindAttribLocation(int program, int index, CharSequence name) {
        if (isGlesContextCurrent()) {
            GLES20.glBindAttribLocation(program, index, name);
            return;
        }
        GL20C.glBindAttribLocation(program, index, name);
    }

    @Override
    public int getAttribLocation(int program, CharSequence name) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetAttribLocation(program, name);
        }
        return GL20C.glGetAttribLocation(program, name);
    }

    @Override
    public int getAttribLocation(int program, ByteBuffer name) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetAttribLocation(program, StandardCharsets.UTF_8.decode(name.slice()).toString().trim());
        }
        return GL20C.glGetAttribLocation(program, StandardCharsets.UTF_8.decode(name.slice()).toString().trim());
    }

    @Override
    public int getUniformLocation(int program, CharSequence name) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetUniformLocation(program, name);
        }
        return GL20C.glGetUniformLocation(program, name);
    }

    @Override
    public int getUniformLocation(int program, ByteBuffer name) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetUniformLocation(program, StandardCharsets.UTF_8.decode(name.slice()).toString().trim());
        }
        return GL20C.glGetUniformLocation(program, StandardCharsets.UTF_8.decode(name.slice()).toString().trim());
    }

    @Override
    public boolean isShader(int obj) {
        if (isGlesContextCurrent()) {
            return GLES20.glIsShader(obj);
        }
        return GL20C.glIsShader(obj);
    }

    @Override
    public boolean isProgram(int obj) {
        if (isGlesContextCurrent()) {
            return GLES20.glIsProgram(obj);
        }
        return GL20C.glIsProgram(obj);
    }

    @Override
    public boolean supportsProgramBinary() {
        if (isGlesContextCurrent()) return GLES30.glGetInteger(GLES30.GL_NUM_PROGRAM_BINARY_FORMATS) > 0;
        return !RenderSystem.isGLES() && (caps.OpenGL41 || caps.GL_ARB_get_program_binary)
            && GL11C.glGetInteger(GL41C.GL_NUM_PROGRAM_BINARY_FORMATS) > 0;
    }

    @Override
    public void programParameteri(int program, int pname, int value) {
        if (isGlesContextCurrent()) {
            GLES30.glProgramParameteri(program, pname, value);
            return;
        }
        GL41C.glProgramParameteri(program, pname, value);
    }

    @Override
    public void getProgramBinary(int program, IntBuffer length, IntBuffer binaryFormat, ByteBuffer binary) {
        if (isGlesContextCurrent()) {
            GLES30.glGetProgramBinary(program, length, binaryFormat, binary);
            return;
        }
        GL41C.glGetProgramBinary(program, length, binaryFormat, binary);
    }

    @Override
    public void programBinary(int program, int binaryFormat, ByteBuffer binary) {
        if (isGlesContextCurrent()) {
            GLES30.glProgramBinary(program, binaryFormat, binary);
            return;
        }
        GL41C.glProgramBinary(program, binaryFormat, binary);
    }

    @Override
    public void validateProgram(int program) {
        if (isGlesContextCurrent()) {
            GLES20.glValidateProgram(program);
            return;
        }
        GL20C.glValidateProgram(program);
    }

    @Override
    public void getAttachedShaders(int program, IntBuffer count, IntBuffer shaders) {
        if (isGlesContextCurrent()) {
            GLES20.glGetAttachedShaders(program, count, shaders);
            return;
        }
        GL20C.glGetAttachedShaders(program, count, shaders);
    }

    @Override
    public void getAttachedShaders(int program, int[] count, int[] shaders) {
        if (isGlesContextCurrent()) {
            GLES20.glGetAttachedShaders(program, count, shaders);
            return;
        }
        GL20C.glGetAttachedShaders(program, count, shaders);
    }

    @Override
    public String getShaderSource(int shader, int maxLength) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetShaderSource(shader, maxLength);
        }
        return GL20C.glGetShaderSource(shader, maxLength);
    }

    @Override
    public void getShaderSource(int shader, IntBuffer length, ByteBuffer source) {
        if (isGlesContextCurrent()) {
            GLES20.glGetShaderSource(shader, length, source);
            return;
        }
        GL20C.glGetShaderSource(shader, length, source);
    }

    @Override
    public void getShaderSource(int shader, int[] length, ByteBuffer source) {
        if (isGlesContextCurrent()) {
            GLES20.glGetShaderSource(shader, length, source);
            return;
        }
        GL20C.glGetShaderSource(shader, length, source);
    }

    @Override
    public void uniform1i(int location, int v0) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform1i(location, v0);
            return;
        }
        GL20C.glUniform1i(location, v0);
    }

    @Override
    public void uniform1ui(int location, int v0) {
        if (isGlesContextCurrent()) {
            GLES30.glUniform1ui(location, v0);
            return;
        }
        GL30C.glUniform1ui(location, v0);
    }

    @Override
    public void uniform2ui(int location, int v0, int v1) {
        if (isGlesContextCurrent()) {
            GLES30.glUniform2ui(location, v0, v1);
            return;
        }
        GL30C.glUniform2ui(location, v0, v1);
    }

    @Override
    public void uniform3ui(int location, int v0, int v1, int v2) {
        if (isGlesContextCurrent()) {
            GLES30.glUniform3ui(location, v0, v1, v2);
            return;
        }
        GL30C.glUniform3ui(location, v0, v1, v2);
    }

    @Override
    public void uniform4ui(int location, int v0, int v1, int v2, int v3) {
        if (isGlesContextCurrent()) {
            GLES30.glUniform4ui(location, v0, v1, v2, v3);
            return;
        }
        GL30C.glUniform4ui(location, v0, v1, v2, v3);
    }

    @Override
    public void uniform1d(int location, double v0) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glUniform1d");
        }
        GL40C.glUniform1d(location, v0);
    }

    @Override
    public void uniform1f(int location, float v0) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform1f(location, v0);
            return;
        }
        GL20C.glUniform1f(location, v0);
    }

    @Override
    public void uniform1fv(int location, FloatBuffer values) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform1fv(location, values);
            return;
        }
        GL20C.glUniform1fv(location, values);
    }

    @Override
    public void uniform1fv(int location, float[] values) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform1fv(location, values);
            return;
        }
        GL20C.glUniform1fv(location, values);
    }

    @Override
    public void uniform2f(int location, float v0, float v1) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform2f(location, v0, v1);
            return;
        }
        GL20C.glUniform2f(location, v0, v1);
    }

    @Override
    public void uniform2i(int location, int v0, int v1) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform2i(location, v0, v1);
            return;
        }
        GL20C.glUniform2i(location, v0, v1);
    }

    @Override
    public void uniform3f(int location, float v0, float v1, float v2) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform3f(location, v0, v1, v2);
            return;
        }
        GL20C.glUniform3f(location, v0, v1, v2);
    }

    @Override
    public void uniform3i(int location, int v0, int v1, int v2) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform3i(location, v0, v1, v2);
            return;
        }
        GL20C.glUniform3i(location, v0, v1, v2);
    }

    @Override
    public void uniform4f(int location, float v0, float v1, float v2, float v3) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform4f(location, v0, v1, v2, v3);
            return;
        }
        GL20C.glUniform4f(location, v0, v1, v2, v3);
    }

    @Override
    public void uniform4i(int location, int v0, int v1, int v2, int v3) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform4i(location, v0, v1, v2, v3);
            return;
        }
        GL20C.glUniform4i(location, v0, v1, v2, v3);
    }

    @Override
    public void uniform2(int location, FloatBuffer value) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform2fv(location, value);
            return;
        }
        GL20C.glUniform2fv(location, value);
    }

    @Override
    public void uniform3(int location, FloatBuffer value) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform3fv(location, value);
            return;
        }
        GL20C.glUniform3fv(location, value);
    }

    @Override
    public void uniform4(int location, FloatBuffer value) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform4fv(location, value);
            return;
        }
        GL20C.glUniform4fv(location, value);
    }

    @Override
    public void uniform1iv(int location, IntBuffer value) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform1iv(location, value);
            return;
        }
        GL20C.glUniform1iv(location, value);
    }

    @Override
    public void uniform2iv(int location, IntBuffer value) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform2iv(location, value);
            return;
        }
        GL20C.glUniform2iv(location, value);
    }

    @Override
    public void uniform3iv(int location, IntBuffer value) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform3iv(location, value);
            return;
        }
        GL20C.glUniform3iv(location, value);
    }

    @Override
    public void uniform4iv(int location, IntBuffer value) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform4iv(location, value);
            return;
        }
        GL20C.glUniform4iv(location, value);
    }

    @Override
    public void uniform3fv(int location, float[] values) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform3fv(location, values);
            return;
        }
        GL20C.glUniform3fv(location, values);
    }

    @Override
    public void uniform4fv(int location, float[] values) {
        if (isGlesContextCurrent()) {
            GLES20.glUniform4fv(location, values);
            return;
        }
        GL20C.glUniform4fv(location, values);
    }

    @Override
    public void uniformMatrix3(int location, boolean transpose, FloatBuffer value) {
        if (isGlesContextCurrent()) {
            GLES20.glUniformMatrix3fv(location, transpose, value);
            return;
        }
        GL20C.glUniformMatrix3fv(location, transpose, value);
    }

    @Override
    public void uniformMatrix3(int location, boolean transpose, float[] matrices) {
        if (isGlesContextCurrent()) {
            GLES20.glUniformMatrix3fv(location, transpose, matrices);
            return;
        }
        GL20C.glUniformMatrix3fv(location, transpose, matrices);
    }

    @Override
    public void uniformMatrix4(int location, boolean transpose, FloatBuffer value) {
        if (isGlesContextCurrent()) {
            GLES20.glUniformMatrix4fv(location, transpose, value);
            return;
        }
        GL20C.glUniformMatrix4fv(location, transpose, value);
    }

    @Override
    public void uniformMatrix4(int location, boolean transpose, float[] matrices) {
        if (isGlesContextCurrent()) {
            GLES20.glUniformMatrix4fv(location, transpose, matrices);
            return;
        }
        GL20C.glUniformMatrix4fv(location, transpose, matrices);
    }

    @Override
    public void uniformMatrix2(int location, boolean transpose, FloatBuffer matrices) {
        if (isGlesContextCurrent()) {
            GLES20.glUniformMatrix2fv(location, transpose, matrices);
            return;
        }
        GL20C.glUniformMatrix2fv(location, transpose, matrices);
    }

    @Override
    public void getUniformfv(int program, int location, FloatBuffer params) {
        if (isGlesContextCurrent()) {
            GLES20.glGetUniformfv(program, location, params);
            return;
        }
        GL20C.glGetUniformfv(program, location, params);
    }

    @Override
    public void getUniformiv(int program, int location, IntBuffer params) {
        if (isGlesContextCurrent()) {
            GLES20.glGetUniformiv(program, location, params);
            return;
        }
        GL20C.glGetUniformiv(program, location, params);
    }

    @Override
    public void vertexAttrib2f(int index, float v0, float v1) {
        if (isGlesContextCurrent()) {
            GLES20.glVertexAttrib2f(index, v0, v1);
            return;
        }
        GL20C.glVertexAttrib2f(index, v0, v1);
    }

    @Override
    public void vertexAttrib3f(int index, float v0, float v1, float v2) {
        if (isGlesContextCurrent()) {
            GLES20.glVertexAttrib3f(index, v0, v1, v2);
            return;
        }
        GL20C.glVertexAttrib3f(index, v0, v1, v2);
    }

    @Override
    public void vertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
        if (isGlesContextCurrent()) {
            GLES20.glVertexAttrib4f(index, v0, v1, v2, v3);
            return;
        }
        GL20C.glVertexAttrib4f(index, v0, v1, v2, v3);
    }

    @Override
    public int genBuffers() {
        if (isGlesContextCurrent()) {
            return GLES20.glGenBuffers();
        }
        return GL15C.glGenBuffers();
    }

    @Override
    public void deleteBuffers(int buffer) {
        if (isGlesContextCurrent()) {
            GLES20.glDeleteBuffers(buffer);
            return;
        }
        GL15C.glDeleteBuffers(buffer);
    }

    @Override
    public void deleteBuffers(IntBuffer buffers) {
        if (isGlesContextCurrent()) {
            GLES20.glDeleteBuffers(buffers);
            return;
        }
        GL15C.glDeleteBuffers(buffers);
    }

    @Override
    public void bindBuffer(int target, int buffer) {
        if (isGlesContextCurrent()) {
            GLES20.glBindBuffer(target, buffer);
            return;
        }
        GL15C.glBindBuffer(target, buffer);
    }

    @Override
    public void bindBufferBase(int target, int index, int buffer) {
        if (isGlesContextCurrent()) {
            GLES30.glBindBufferBase(target, index, buffer);
            return;
        }
        GL30C.glBindBufferBase(target, index, buffer);
    }

    @Override
    public void bindBufferRange(int target, int index, int buffer, long offset, long size) {
        if (isGlesContextCurrent()) {
            GLES30.glBindBufferRange(target, index, buffer, offset, size);
            return;
        }
        GL30C.glBindBufferRange(target, index, buffer, offset, size);
    }

    @Override
    public int getUniformBlockIndex(int program, CharSequence name) {
        if (isGlesContextCurrent()) {
            return GLES30.glGetUniformBlockIndex(program, name);
        }
        return GL31C.glGetUniformBlockIndex(program, name);
    }

    @Override
    public int getUniformBlockIndex(int program, ByteBuffer name) {
        if (isGlesContextCurrent()) {
            return GLES30.glGetUniformBlockIndex(program, name);
        }
        return GL31C.glGetUniformBlockIndex(program, name);
    }

    @Override
    public void uniformBlockBinding(int program, int blockIndex, int binding) {
        if (isGlesContextCurrent()) {
            GLES30.glUniformBlockBinding(program, blockIndex, binding);
            return;
        }
        GL31C.glUniformBlockBinding(program, blockIndex, binding);
    }

    @Override
    public int getIndexedBufferBinding(int target, int index) {
        final int pname = (target == GL43C.GL_SHADER_STORAGE_BUFFER) ? GL43C.GL_SHADER_STORAGE_BUFFER_BINDING : GL31C.GL_UNIFORM_BUFFER_BINDING;
        return getIntegerIndexed(pname, index);
    }

    @Override
    public void bufferData(int target, long size, int usage) {
        if (isGlesContextCurrent()) {
            GLES20.glBufferData(target, size, usage);
            return;
        }
        GL15C.glBufferData(target, size, usage);
    }

    @Override
    public void bufferData(int target, ByteBuffer data, int usage) {
        if (isGlesContextCurrent()) {
            GLES20.glBufferData(target, data, usage);
            return;
        }
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, FloatBuffer data, int usage) {
        if (isGlesContextCurrent()) {
            GLES20.glBufferData(target, data, usage);
            return;
        }
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, ShortBuffer data, int usage) {
        if (isGlesContextCurrent()) {
            GLES20.glBufferData(target, data, usage);
            return;
        }
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, IntBuffer data, int usage) {
        if (isGlesContextCurrent()) {
            GLES20.glBufferData(target, data, usage);
            return;
        }
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, DoubleBuffer data, int usage) {
        if (isGlesContextCurrent()) {
            GLES20.nglBufferData(target, (long) data.remaining() * Double.BYTES, MemoryUtil.memAddress(data), usage);
            return;
        }
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, int[] data, int usage) {
        if (isGlesContextCurrent()) {
            GLES20.glBufferData(target, data, usage);
            return;
        }
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void bufferData(int target, float[] data, int usage) {
        if (isGlesContextCurrent()) {
            GLES20.glBufferData(target, data, usage);
            return;
        }
        GL15C.glBufferData(target, data, usage);
    }

    @Override
    public void bufferSubData(int target, long offset, ByteBuffer data) {
        if (isGlesContextCurrent()) {
            GLES20.glBufferSubData(target, offset, data);
            return;
        }
        GL15C.glBufferSubData(target, offset, data);
    }

    @Override
    public void bufferSubData(int target, long offset, ShortBuffer data) {
        if (isGlesContextCurrent()) {
            GLES20.glBufferSubData(target, offset, data);
            return;
        }
        GL15C.glBufferSubData(target, offset, data);
    }

    @Override
    public void bufferSubData(int target, long offset, IntBuffer data) {
        if (isGlesContextCurrent()) {
            GLES20.glBufferSubData(target, offset, data);
            return;
        }
        GL15C.glBufferSubData(target, offset, data);
    }

    @Override
    public void bufferSubData(int target, long offset, FloatBuffer data) {
        if (isGlesContextCurrent()) {
            GLES20.glBufferSubData(target, offset, data);
            return;
        }
        GL15C.glBufferSubData(target, offset, data);
    }

    @Override
    public void bufferSubData(int target, long offset, DoubleBuffer data) {
        if (isGlesContextCurrent()) {
            GLES20.nglBufferSubData(target, offset, (long) data.remaining() * Double.BYTES, MemoryUtil.memAddress(data));
            return;
        }
        GL15C.glBufferSubData(target, offset, data);
    }

    @Override
    public ByteBuffer mapBuffer(int target, int access) {
        if (isGlesContextCurrent()) {
            return GlesCalls.mapBuffer(target, access);
        }
        return GL15C.glMapBuffer(target, access);
    }

    @Override
    public ByteBuffer mapBuffer(int target, int access, long length, ByteBuffer old_buffer) {
        if (isGlesContextCurrent()) {
            return GlesCalls.mapBuffer(target, access, length, old_buffer);
        }
        return GL15C.glMapBuffer(target, access, length, old_buffer);
    }

    @Override
    public boolean unmapBuffer(int target) {
        if (isGlesContextCurrent()) {
            return GLES30.glUnmapBuffer(target);
        }
        return GL15C.glUnmapBuffer(target);
    }

    @Override
    public void bufferStorage(int target, ByteBuffer data, int flags) {
        if (isGlesContextCurrent()) {
            GlesCalls.bufferStorage(target, data, flags);
            return;
        }
        GL44C.glBufferStorage(target, data, flags);
    }

    @Override
    public void bufferStorage(int target, long size, int flags) {
        if (isGlesContextCurrent()) {
            GlesCalls.bufferStorage(target, size, flags);
            return;
        }
        GL44C.glBufferStorage(target, size, flags);
    }

    @Override
    public void getBufferSubData(int target, long offset, ByteBuffer data) {
        if (isGlesContextCurrent()) {
            GlesCalls.getBufferSubData(target, offset, data, 1);
            return;
        }
        GL15C.glGetBufferSubData(target, offset, data);
    }

    @Override
    public void getBufferSubData(int target, long offset, ShortBuffer data) {
        if (isGlesContextCurrent()) {
            GlesCalls.getBufferSubData(target, offset, data, 2);
            return;
        }
        GL15C.glGetBufferSubData(target, offset, data);
    }

    @Override
    public void getBufferSubData(int target, long offset, IntBuffer data) {
        if (isGlesContextCurrent()) {
            GlesCalls.getBufferSubData(target, offset, data, 4);
            return;
        }
        GL15C.glGetBufferSubData(target, offset, data);
    }

    @Override
    public void getBufferSubData(int target, long offset, FloatBuffer data) {
        if (isGlesContextCurrent()) {
            GlesCalls.getBufferSubData(target, offset, data, 4);
            return;
        }
        GL15C.glGetBufferSubData(target, offset, data);
    }

    @Override
    public void getBufferSubData(int target, long offset, DoubleBuffer data) {
        if (isGlesContextCurrent()) {
            GlesCalls.getBufferSubData(target, offset, data, 8);
            return;
        }
        GL15C.glGetBufferSubData(target, offset, data);
    }

    @Override
    public int getBufferParameteri(int target, int pname) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetBufferParameteri(target, pname);
        }
        return GL15C.glGetBufferParameteri(target, pname);
    }

    @Override
    public boolean isBuffer(int buffer) {
        if (isGlesContextCurrent()) {
            return GLES20.glIsBuffer(buffer);
        }
        return GL15C.glIsBuffer(buffer);
    }

    @Override
    public boolean isTexture(int texture) {
        if (isGlesContextCurrent()) {
            return GLES20.glIsTexture(texture);
        }
        return GL11C.glIsTexture(texture);
    }

    @Override
    public boolean isFramebuffer(int framebuffer) {
        if (isGlesContextCurrent()) {
            return GLES20.glIsFramebuffer(framebuffer);
        }
        return GL30C.glIsFramebuffer(framebuffer);
    }

    @Override
    public boolean isRenderbuffer(int renderbuffer) {
        if (isGlesContextCurrent()) {
            return GLES20.glIsRenderbuffer(renderbuffer);
        }
        return GL30C.glIsRenderbuffer(renderbuffer);
    }

    @Override
    public boolean isSampler(int sampler) {
        if (isGlesContextCurrent()) {
            return GLES30.glIsSampler(sampler);
        }
        return GL33C.glIsSampler(sampler);
    }

    @Override
    public boolean isQuery(int query) {
        if (isGlesContextCurrent()) {
            return GLES30.glIsQuery(query);
        }
        return GL15C.glIsQuery(query);
    }

    @Override
    public ByteBuffer mapBufferRange(int target, long offset, long length, int access) {
        if (isGlesContextCurrent()) {
            return GLES30.glMapBufferRange(target, offset, length, access);
        }
        return GL30C.glMapBufferRange(target, offset, length, access);
    }

    @Override
    public void flushMappedBufferRange(int target, long offset, long length) {
        if (isGlesContextCurrent()) {
            GLES30.glFlushMappedBufferRange(target, offset, length);
            return;
        }
        GL30C.glFlushMappedBufferRange(target, offset, length);
    }

    @Override
    public int genVertexArrays() {
        if (isGlesContextCurrent()) {
            return GLES30.glGenVertexArrays();
        }
        return GL30C.glGenVertexArrays();
    }

    @Override
    public long getContextHandle() {
        return WindowBackends.get().currentContextHandle();
    }

    @Override
    public void deleteVertexArrays(int array) {
        if (isGlesContextCurrent()) {
            GLES30.glDeleteVertexArrays(array);
            return;
        }
        GL30C.glDeleteVertexArrays(array);
    }

    @Override
    public void bindVertexArray(int array) {
        if (isGlesContextCurrent()) {
            GLES30.glBindVertexArray(array);
            return;
        }
        GL30C.glBindVertexArray(array);
    }

    @Override
    public boolean isVertexArray(int array) {
        if (isGlesContextCurrent()) {
            return GLES30.glIsVertexArray(array);
        }
        return GL30C.glIsVertexArray(array);
    }

    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        if (isGlesContextCurrent()) {
            GLES20.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
            return;
        }
        GL20C.glVertexAttribPointer(index, size, type, normalized, stride, pointer);
    }

    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, ByteBuffer pointer) {
        if (isGlesContextCurrent()) {
            GLES20.glVertexAttribPointer(index, size, type, normalized, stride, MemoryUtil.memAddress(pointer));
            return;
        }
        GL20C.glVertexAttribPointer(index, size, type, normalized, stride, MemoryUtil.memAddress(pointer));
    }

    @Override
    public void vertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        if (isGlesContextCurrent()) {
            GLES30.glVertexAttribIPointer(index, size, type, stride, pointer);
            return;
        }
        GL30C.glVertexAttribIPointer(index, size, type, stride, pointer);
    }

    @Override
    public void vertexAttribIPointer(int index, int size, int type, int stride, ByteBuffer pointer) {
        if (isGlesContextCurrent()) {
            GLES30.glVertexAttribIPointer(index, size, type, stride, MemoryUtil.memAddress(pointer));
            return;
        }
        GL30C.glVertexAttribIPointer(index, size, type, stride, MemoryUtil.memAddress(pointer));
    }

    @Override
    public void enableVertexAttribArray(int index) {
        if (isGlesContextCurrent()) {
            GLES20.glEnableVertexAttribArray(index);
            return;
        }
        GL20C.glEnableVertexAttribArray(index);
    }

    @Override
    public void disableVertexAttribArray(int index) {
        if (isGlesContextCurrent()) {
            GLES20.glDisableVertexAttribArray(index);
            return;
        }
        GL20C.glDisableVertexAttribArray(index);
    }

    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        if (isGlesContextCurrent()) {
            GLES30.glVertexAttribDivisor(index, divisor);
            return;
        }
        GL33C.glVertexAttribDivisor(index, divisor);
    }

    @Override
    public void bindVertexBuffer(int bindingindex, int buffer, long offset, int stride) {
        if (isGlesContextCurrent()) {
            GLES31.glBindVertexBuffer(bindingindex, buffer, offset, stride);
            return;
        }
        GL43C.glBindVertexBuffer(bindingindex, buffer, offset, stride);
    }

    @Override
    public void vertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset) {
        if (isGlesContextCurrent()) {
            GLES31.glVertexAttribFormat(attribindex, size, type, normalized, relativeoffset);
            return;
        }
        GL43C.glVertexAttribFormat(attribindex, size, type, normalized, relativeoffset);
    }

    @Override
    public void vertexAttribIFormat(int attribindex, int size, int type, int relativeoffset) {
        if (isGlesContextCurrent()) {
            GLES31.glVertexAttribIFormat(attribindex, size, type, relativeoffset);
            return;
        }
        GL43C.glVertexAttribIFormat(attribindex, size, type, relativeoffset);
    }

    @Override
    public void vertexAttribBinding(int attribindex, int bindingindex) {
        if (isGlesContextCurrent()) {
            GLES31.glVertexAttribBinding(attribindex, bindingindex);
            return;
        }
        GL43C.glVertexAttribBinding(attribindex, bindingindex);
    }

    @Override
    public int createTextures(int target) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glCreateTextures");
        }
        return GL45C.glCreateTextures(target);
    }

    @Override
    public void bindTextureUnit(int unit, int texture) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glBindTextureUnit");
        }
        GL45C.glBindTextureUnit(unit, texture);
    }

    @Override
    public void textureParameteri(int texture, int target, int pname, int param) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glTextureParameteri");
        }
        GL45C.glTextureParameteri(texture, pname, param);
    }

    @Override
    public void textureParameterf(int texture, int target, int pname, float param) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glTextureParameterf");
        }
        GL45C.glTextureParameterf(texture, pname, param);
    }

    @Override
    public void textureParameteriv(int texture, int target, int pname, IntBuffer params) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glTextureParameteriv");
        }
        GL45C.glTextureParameteriv(texture, pname, params);
    }

    @Override
    public void texStorage2D(int target, int levels, int internalFormat, int width, int height) {
        if (isGlesContextCurrent()) {
            GLES30.glTexStorage2D(target, levels, internalFormat, width, height);
            return;
        }
        GL42C.glTexStorage2D(target, levels, internalFormat, width, height);
    }

    @Override
    public void textureStorage2D(int texture, int levels, int internalFormat, int width, int height) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glTextureStorage2D");
        }
        GL45C.glTextureStorage2D(texture, levels, internalFormat, width, height);
    }

    @Override
    public void texStorage1D(int target, int levels, int internalFormat, int width) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glTexStorage1D");
        }
        GL42C.glTexStorage1D(target, levels, internalFormat, width);
    }

    @Override
    public void texParameterIiv(int target, int pname, IntBuffer params) {
        if (isGlesContextCurrent()) {
            GLES32.glTexParameterIiv(target, pname, params);
            return;
        }
        GL30C.glTexParameterIiv(target, pname, params);
    }

    @Override
    public void texParameterIuiv(int target, int pname, IntBuffer params) {
        if (isGlesContextCurrent()) {
            GLES32.glTexParameterIuiv(target, pname, params);
            return;
        }
        GL30C.glTexParameterIuiv(target, pname, params);
    }

    @Override
    public void getQueryObjecti(int id, int pname, IntBuffer params) {
        if (isGlesContextCurrent()) {
            GLES30.glGetQueryObjectuiv(id, pname, params);
            return;
        }
        GL15C.glGetQueryObjectiv(id, pname, params);
    }

    @Override
    public void getVertexAttribi(int index, int pname, IntBuffer params) {
        if (isGlesContextCurrent()) {
            GLES20.glGetVertexAttribiv(index, pname, params);
            return;
        }
        GL20C.glGetVertexAttribiv(index, pname, params);
    }

    @Override
    public void texStorage3D(int target, int levels, int internalFormat, int width, int height, int depth) {
        if (isGlesContextCurrent()) {
            GLES30.glTexStorage3D(target, levels, internalFormat, width, height, depth);
            return;
        }
        GL42C.glTexStorage3D(target, levels, internalFormat, width, height, depth);
    }

    @Override
    public void compressedTexImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            GLES30.glCompressedTexImage3D(target, level, internalFormat, width, height, depth, border, pixels);
            return;
        }
        GL13C.glCompressedTexImage3D(target, level, internalFormat, width, height, depth, border, pixels);
    }

    @Override
    public void compressedTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            GLES30.glCompressedTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, pixels);
            return;
        }
        GL13C.glCompressedTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, pixels);
    }

    @Override
    public void getCompressedTexImage(int target, int level, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glGetCompressedTexImage");
        }
        GL13C.glGetCompressedTexImage(target, level, pixels);
    }

    @Override
    public void compressedTexImage2D(int target, int level, int internalFormat, int width, int height, int border, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            GLES20.glCompressedTexImage2D(target, level, internalFormat, width, height, border, pixels);
            return;
        }
        GL13C.glCompressedTexImage2D(target, level, internalFormat, width, height, border, pixels);
    }

    @Override
    public void compressedTexImage2D(int target, int level, int internalFormat, int width, int height, int border, int imageSize, long data) {
        if (isGlesContextCurrent()) {
            GLES20.glCompressedTexImage2D(target, level, internalFormat, width, height, border, imageSize, data);
            return;
        }
        GL13C.glCompressedTexImage2D(target, level, internalFormat, width, height, border, imageSize, data);
    }

    @Override
    public void textureStorage1D(int texture, int levels, int internalFormat, int width) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glTextureStorage1D");
        }
        GL45C.glTextureStorage1D(texture, levels, internalFormat, width);
    }

    @Override
    public void textureStorage3D(int texture, int levels, int internalFormat, int width, int height, int depth) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glTextureStorage3D");
        }
        GL45C.glTextureStorage3D(texture, levels, internalFormat, width, height, depth);
    }

    @Override
    public void generateTextureMipmap(int texture) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glGenerateTextureMipmap");
        }
        GL45C.glGenerateTextureMipmap(texture);
    }

    @Override
    public void textureImage2DEXT(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glTextureImage2DEXT");
        }
        EXTDirectStateAccess.glTextureImage2DEXT(texture, target, level, internalformat, width, height, border, format, type, pixels);
    }

    @Override
    public void textureImage2DEXT(int texture, int target, int level, int internalformat, int width, int height, int border, int format, int type, IntBuffer pixels) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glTextureImage2DEXT");
        }
        EXTDirectStateAccess.glTextureImage2DEXT(texture, target, level, internalformat, width, height, border, format, type, pixels);
    }

    @Override
    public void textureSubImage2D(int texture, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glTextureSubImage2D");
        }
        GL45C.glTextureSubImage2D(texture, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override
    public void textureSubImage2D(int texture, int level, int xoffset, int yoffset, int width, int height, int format, int type, IntBuffer pixels) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glTextureSubImage2D");
        }
        GL45C.glTextureSubImage2D(texture, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    @Override
    public int createFramebuffers() {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glCreateFramebuffers");
        }
        return GL45C.glCreateFramebuffers();
    }

    @Override
    public void namedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glNamedFramebufferTexture");
        }
        GL45C.glNamedFramebufferTexture(framebuffer, attachment, texture, level);
    }

    @Override
    public void namedFramebufferTextureLayer(int framebuffer, int attachment, int texture, int level, int layer) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glNamedFramebufferTextureLayer");
        }
        GL45C.glNamedFramebufferTextureLayer(framebuffer, attachment, texture, level, layer);
    }

    @Override
    public void namedFramebufferRenderbuffer(int framebuffer, int attachment, int renderbuffertarget, int renderbuffer) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glNamedFramebufferRenderbuffer");
        }
        GL45C.glNamedFramebufferRenderbuffer(framebuffer, attachment, renderbuffertarget, renderbuffer);
    }

    @Override
    public void namedFramebufferReadBuffer(int framebuffer, int mode) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glNamedFramebufferReadBuffer");
        }
        GL45C.glNamedFramebufferReadBuffer(framebuffer, mode);
    }

    @Override
    public void namedFramebufferDrawBuffers(int framebuffer, IntBuffer bufs) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glNamedFramebufferDrawBuffers");
        }
        GL45C.glNamedFramebufferDrawBuffers(framebuffer, bufs);
    }

    @Override
    public void blitNamedFramebuffer(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glBlitNamedFramebuffer");
        }
        GL45C.glBlitNamedFramebuffer(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    @Override
    public int createBuffers() {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glCreateBuffers");
        }
        return GL45C.glCreateBuffers();
    }

    @Override
    public void namedBufferData(int buffer, ByteBuffer data, int usage) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glNamedBufferData");
        }
        GL45C.glNamedBufferData(buffer, data, usage);
    }

    @Override
    public void namedBufferData(int buffer, FloatBuffer data, int usage) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glNamedBufferData");
        }
        GL45C.glNamedBufferData(buffer, data, usage);
    }

    @Override
    public void namedBufferData(int buffer, long size, int usage) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glNamedBufferData");
        }
        GL45C.glNamedBufferData(buffer, size, usage);
    }

    @Override
    public void namedBufferSubData(int buffer, long offset, ByteBuffer data) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glNamedBufferSubData");
        }
        GL45C.glNamedBufferSubData(buffer, offset, data);
    }

    @Override
    public void copyTextureSubImage2D(int texture, int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glCopyTextureSubImage2D");
        }
        GL45C.glCopyTextureSubImage2D(texture, level, xoffset, yoffset, x, y, width, height);
    }

    @Override
    public int getTextureParameteri(int texture, int target, int pname) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glGetTextureParameteri");
        }
        return GL45C.glGetTextureParameteri(texture, pname);
    }

    @Override
    public float getTextureParameterf(int texture, int target, int pname) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glGetTextureParameterf");
        }
        return GL45C.glGetTextureParameterf(texture, pname);
    }

    @Override
    public int getTextureLevelParameteri(int texture, int level, int pname) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glGetTextureLevelParameteri");
        }
        return GL45C.glGetTextureLevelParameteri(texture, level, pname);
    }

    @Override
    public void textureSubImage3D(int texture, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glTextureSubImage3D");
        }
        GL45C.glTextureSubImage3D(texture, level, xoffset, yoffset, zoffset, width, height, depth, format, type, pixels);
    }

    @Override
    public void compressedTextureSubImage3D(int texture, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glCompressedTextureSubImage3D");
        }
        GL45C.glCompressedTextureSubImage3D(texture, level, xoffset, yoffset, zoffset, width, height, depth, format, pixels);
    }

    @Override
    public void copyTextureSubImage3D(int texture, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glCopyTextureSubImage3D");
        }
        GL45C.glCopyTextureSubImage3D(texture, level, xoffset, yoffset, zoffset, x, y, width, height);
    }

    @Override
    public void clearTexSubImage(int texture, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, ByteBuffer data) {
        if (isGlesContextCurrent()) {
            if (!GLES.getCapabilities().GL_EXT_clear_texture) throw GlesCalls.unsupported("glClearTexSubImage");
            EXTClearTexture.glClearTexSubImageEXT(texture, level, xoffset, yoffset, zoffset, width, height, depth, format, type, data);
            return;
        }
        GL45C.glClearTexSubImage(texture, level, xoffset, yoffset, zoffset, width, height, depth, format, type, data);
    }

    @Override
    public void textureParameterfv(int texture, int target, int pname, FloatBuffer params) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glTextureParameterfv");
        }
        GL45C.glTextureParameterfv(texture, pname, params);
    }

    @Override
    public void textureParameterIiv(int texture, int target, int pname, IntBuffer params) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glTextureParameterIiv");
        }
        GL45C.glTextureParameterIiv(texture, pname, params);
    }

    @Override
    public void textureParameterIuiv(int texture, int target, int pname, IntBuffer params) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glTextureParameterIuiv");
        }
        GL45C.glTextureParameterIuiv(texture, pname, params);
    }

    @Override
    public void getTextureImage(int texture, int level, int format, int type, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glGetTextureImage");
        }
        GL45C.glGetTextureImage(texture, level, format, type, pixels);
    }

    @Override
    public void getCompressedTextureImage(int texture, int level, ByteBuffer pixels) {
        if (isGlesContextCurrent()) {
            throw GlesCalls.unsupported("glGetCompressedTextureImage");
        }
        GL45C.glGetCompressedTextureImage(texture, level, pixels);
    }

    @Override
    public int getInteger(int pname) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetInteger(pname == GL11C.GL_DRAW_BUFFER ? GLES30.GL_DRAW_BUFFER0 : pname);
        }
        return GL11C.glGetInteger(pname);
    }

    @Override
    public void getInteger(int pname, IntBuffer params) {
        if (isGlesContextCurrent()) {
            GLES20.glGetIntegerv(pname == GL11C.GL_DRAW_BUFFER ? GLES30.GL_DRAW_BUFFER0 : pname, params);
            return;
        }
        GL11C.glGetIntegerv(pname, params);
    }

    @Override
    public int getIntegerIndexed(int pname, int index) {
        if (isGlesContextCurrent()) {
            return GLES30.glGetIntegeri(pname, index);
        }
        return GL30C.glGetIntegeri(pname, index);
    }

    @Override
    public float getFloat(int pname) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetFloat(pname);
        }
        return GL11C.glGetFloat(pname);
    }

    @Override
    public void getFloat(int pname, FloatBuffer params) {
        if (isGlesContextCurrent()) {
            GLES20.glGetFloatv(pname, params);
            return;
        }
        GL11C.glGetFloatv(pname, params);
    }

    @Override
    public double getDouble(int pname) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetFloat(pname);
        }
        return GL11C.glGetDouble(pname);
    }

    @Override
    public void getDouble(int pname, DoubleBuffer params) {
        if (isGlesContextCurrent()) {
            GlesCalls.getDoublev(pname, params);
            return;
        }
        GL11C.glGetDoublev(pname, params);
    }

    @Override
    public boolean getBoolean(int pname) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetBoolean(pname);
        }
        return GL11C.glGetBoolean(pname);
    }

    @Override
    public void getBoolean(int pname, ByteBuffer params) {
        if (isGlesContextCurrent()) {
            GLES20.glGetBooleanv(pname, params);
            return;
        }
        GL11C.glGetBooleanv(pname, params);
    }

    @Override
    public String getString(int pname) {
        if (isGlesContextCurrent()) {
            return GLES20.glGetString(pname);
        }
        return GL11C.glGetString(pname);
    }

    @Override
    public String getStringi(int name, int index) {
        if (isGlesContextCurrent()) {
            return GLES30.glGetStringi(name, index);
        }
        return GL30C.glGetStringi(name, index);
    }

    @Override
    public int getError() {
        if (isGlesContextCurrent()) {
            return GLES20.glGetError();
        }
        return GL11C.glGetError();
    }

    @Override
    public long fenceSync(int condition, int flags) {
        if (isGlesContextCurrent()) {
            return GLES30.glFenceSync(condition, flags);
        }
        return GL32C.glFenceSync(condition, flags);
    }

    @Override
    public int clientWaitSync(long sync, int flags, long timeout) {
        if (isGlesContextCurrent()) {
            return GLES30.glClientWaitSync(sync, flags, timeout);
        }
        return GL32C.glClientWaitSync(sync, flags, timeout);
    }

    @Override
    public void deleteSync(long sync) {
        if (isGlesContextCurrent()) {
            GLES30.glDeleteSync(sync);
            return;
        }
        GL32C.glDeleteSync(sync);
    }

    @Override
    public void waitSync(long sync, int flags, long timeout) {
        if (isGlesContextCurrent()) {
            GLES30.glWaitSync(sync, flags, timeout);
            return;
        }
        GL32C.glWaitSync(sync, flags, timeout);
    }

    @Override
    public int getSynci(long sync, int pname, IntBuffer length) {
        if (isGlesContextCurrent()) {
            return GLES30.glGetSynci(sync, pname, length);
        }
        return GL32C.glGetSynci(sync, pname, length);
    }

    @Override
    public void genQueries(IntBuffer ids) {
        if (isGlesContextCurrent()) {
            GLES30.glGenQueries(ids);
            return;
        }
        GL15C.glGenQueries(ids);
    }

    @Override
    public int genQueries() {
        if (isGlesContextCurrent()) {
            return GLES30.glGenQueries();
        }
        return GL15C.glGenQueries();
    }

    @Override
    public void deleteQueries(int id) {
        if (isGlesContextCurrent()) {
            GLES30.glDeleteQueries(id);
            return;
        }
        GL15C.glDeleteQueries(id);
    }

    @Override
    public void beginQuery(int target, int id) {
        if (isGlesContextCurrent()) {
            GLES30.glBeginQuery(target, id);
            return;
        }
        GL15C.glBeginQuery(target, id);
    }

    @Override
    public void endQuery(int target) {
        if (isGlesContextCurrent()) {
            GLES30.glEndQuery(target);
            return;
        }
        GL15C.glEndQuery(target);
    }

    @Override
    public void getQueryObjectui(int id, int pname, IntBuffer params) {
        if (isGlesContextCurrent()) {
            GLES30.glGetQueryObjectuiv(id, pname, params);
            return;
        }
        GL15C.glGetQueryObjectuiv(id, pname, params);
    }

    @Override
    public int getQueryObjectui(int id, int pname) {
        if (isGlesContextCurrent()) {
            return GLES30.glGetQueryObjectui(id, pname);
        }
        return GL15C.glGetQueryObjectui(id, pname);
    }

    @Override
    public int getQueryObjecti(int id, int pname) {
        if (isGlesContextCurrent()) {
            return GLES30.glGetQueryObjectui(id, pname);
        }
        return GL15C.glGetQueryObjecti(id, pname);
    }

    @Override
    public void queryCounter(int id, int target) {
        if (isGlesContextCurrent()) {
            GlesCalls.queryCounter(id, target);
            return;
        }
        GL33C.glQueryCounter(id, target);
    }

    @Override
    public long getQueryObjectui64(int id, int pname) {
        if (isGlesContextCurrent()) {
            return GlesCalls.getQueryObjectui64(id, pname);
        }
        return GL33C.glGetQueryObjectui64(id, pname);
    }

    @Override
    public void clearBufferSubData(int target, int internalFormat, long offset, long size, int format, int type, ByteBuffer data) {
        if (isGlesContextCurrent()) {
            GlesCalls.clearBuffer(target, internalFormat, offset, size, format, type, data);
            return;
        }
        GL43C.glClearBufferSubData(target, internalFormat, offset, size, format, type, data);
    }

    @Override
    public void clearBufferData(int target, int internalformat, int format, int type, ByteBuffer data) {
        if (isGlesContextCurrent()) {
            final long size = GLES30.glGetBufferParameteri64(target, GLES20.GL_BUFFER_SIZE);
            GlesCalls.clearBuffer(target, internalformat, 0L, size, format, type, data);
            return;
        }
        GL43C.glClearBufferData(target, internalformat, format, type, data);
    }

    @Override
    public void clearTexImage(int texture, int level, int format, int type) {
        if (isGlesContextCurrent()) {
            GlesCalls.clearTexImage(texture, level, format, type);
        } else if (caps.OpenGL44) {
            GL44C.glClearTexImage(texture, level, format, type, (ByteBuffer) null);
        } else {
            ARBClearTexture.glClearTexImage(texture, level, format, type, (ByteBuffer) null);
        }
    }

    @Override
    public void bindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        if (isGlesContextCurrent()) {
            GLES31.glBindImageTexture(unit, texture, level, layered, layer, access, format);
            return;
        }
        GL42C.glBindImageTexture(unit, texture, level, layered, layer, access, format);
    }

    @Override
    public void memoryBarrier(int barriers) {
        if (isGlesContextCurrent()) {
            GLES31.glMemoryBarrier(barriers);
            return;
        }
        GL42C.glMemoryBarrier(barriers);
    }

    @Override
    public void copyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ,
                                  int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ,
                                  int srcWidth, int srcHeight, int srcDepth) {
        if (isGlesContextCurrent()) {
            GLES32.glCopyImageSubData(srcName, srcTarget, srcLevel, srcX, srcY, srcZ,
            dstName, dstTarget, dstLevel, dstX, dstY, dstZ,
            srcWidth, srcHeight, srcDepth);
            return;
        }
        GL43C.glCopyImageSubData(srcName, srcTarget, srcLevel, srcX, srcY, srcZ,
            dstName, dstTarget, dstLevel, dstX, dstY, dstZ,
            srcWidth, srcHeight, srcDepth);
    }

    @Override
    public boolean supportsDebugOutput() {
        return isGlesContextCurrent() ? GLES.getCapabilities().GLES32 : caps != null && caps.OpenGL43;
    }

    @Override
    public int setupDebugOutput(DebugMessageHandler handler) {
        if (isGlesContextCurrent()) {
            final var callback = org.lwjgl.opengles.GLDebugMessageCallback.create((source, type, id, severity, length, message, userParam) ->
                handler.handleMessage(source, type, id, severity, GLDebugMessageCallback.getMessage(length, message)));
            GLES32.glDebugMessageControl(GLES20.GL_DONT_CARE, GLES20.GL_DONT_CARE, GLES32.GL_DEBUG_SEVERITY_HIGH, (IntBuffer) null, true);
            GLES32.glDebugMessageControl(GLES20.GL_DONT_CARE, GLES20.GL_DONT_CARE, GLES32.GL_DEBUG_SEVERITY_MEDIUM, (IntBuffer) null, false);
            GLES32.glDebugMessageControl(GLES20.GL_DONT_CARE, GLES20.GL_DONT_CARE, GLES32.GL_DEBUG_SEVERITY_LOW, (IntBuffer) null, false);
            GLES32.glDebugMessageControl(GLES20.GL_DONT_CARE, GLES20.GL_DONT_CARE, GLES32.GL_DEBUG_SEVERITY_NOTIFICATION, (IntBuffer) null, false);
            GLES32.glDebugMessageCallback(callback, 0L);
            debugCallback = callback;
            GLES20.glEnable(GLES32.GL_DEBUG_OUTPUT_SYNCHRONOUS);
            GLES20.glEnable(GLES32.GL_DEBUG_OUTPUT);
            debugOutputActive = true;
            return 1;
        }
        final var callback = GLDebugMessageCallback.create(
            (source, type, id, severity, length, message, userParam) -> {
                final String msg = GLDebugMessageCallback.getMessage(length, message);
                handler.handleMessage(source, type, id, severity, msg);
            });

        // Enable high severity only
        GL43C.glDebugMessageControl(GL11C.GL_DONT_CARE, GL11C.GL_DONT_CARE, GL43C.GL_DEBUG_SEVERITY_HIGH, (IntBuffer) null, true);
        GL43C.glDebugMessageControl(GL11C.GL_DONT_CARE, GL11C.GL_DONT_CARE, GL43C.GL_DEBUG_SEVERITY_MEDIUM, (IntBuffer) null, false);
        GL43C.glDebugMessageControl(GL11C.GL_DONT_CARE, GL11C.GL_DONT_CARE, GL43C.GL_DEBUG_SEVERITY_LOW, (IntBuffer) null, false);
        GL43C.glDebugMessageControl(GL11C.GL_DONT_CARE, GL11C.GL_DONT_CARE, GL43C.GL_DEBUG_SEVERITY_NOTIFICATION, (IntBuffer) null, false);

        GL43C.glDebugMessageCallback(callback, 0L);
        debugCallback = callback;
        GL11C.glEnable(GL43C.GL_DEBUG_OUTPUT_SYNCHRONOUS);

        final int contextFlags = GL11C.glGetInteger(GL30C.GL_CONTEXT_FLAGS);
        if ((contextFlags & GL43C.GL_CONTEXT_FLAG_DEBUG_BIT) == 0) {
            GL11C.glEnable(GL43C.GL_DEBUG_OUTPUT);
            debugOutputActive = true;
            return 2; // non-debug context
        }
        debugOutputActive = true;
        return 1;
    }

    @Override
    public int disableDebugOutput() {
        if (isGlesContextCurrent()) GLES32.glDebugMessageCallback(null, 0L);
        else GL43C.glDebugMessageCallback(null, 0L);
        if (debugCallback != null) {
            debugCallback.free();
            debugCallback = null;
        }
        if (appDebugCallback != null) {
            appDebugCallback.free();
            appDebugCallback = null;
        }
        if (debugOutputActive) {
            if (isGlesContextCurrent()) GLES20.glDisable(GLES32.GL_DEBUG_OUTPUT);
            else GL11C.glDisable(GL43C.GL_DEBUG_OUTPUT);
            debugOutputActive = false;
        }
        return 1;
    }

    @Override
    public void debugMessageCallback(GLDebugMessageListener listener, long userParam) {
        Callback previous = appDebugCallback;
        if (isGlesContextCurrent()) {
            var callback = listener == null ? null : org.lwjgl.opengles.GLDebugMessageCallback.create(listener::onMessage);
            GLES32.glDebugMessageCallback(callback, userParam);
            appDebugCallback = callback;
        } else {
            var callback = listener == null ? null : GLDebugMessageCallback.create(listener::onMessage);
            GL43C.glDebugMessageCallback(callback, userParam);
            appDebugCallback = callback;
        }
        if (previous != null) previous.free();
    }

    @Override
    public void objectLabel(int identifier, int name, CharSequence label) {
        if (isGlesContextCurrent()) {
            GLES32.glObjectLabel(identifier, name, label);
            return;
        }
        GL43C.glObjectLabel(identifier, name, label);
    }

    @Override
    public String getObjectLabel(int identifier, int name) {
        if (isGlesContextCurrent()) {
            return GLES32.glGetObjectLabel(identifier, name, 256);
        }
        return GL43C.glGetObjectLabel(identifier, name, 256);
    }

    @Override
    public void pushDebugGroup(int source, int id, CharSequence message) {
        if (isGlesContextCurrent()) {
            GLES32.glPushDebugGroup(source, id, message);
            return;
        }
        GL43C.glPushDebugGroup(source, id, message);
    }

    @Override
    public void popDebugGroup() {
        if (isGlesContextCurrent()) {
            GLES32.glPopDebugGroup();
            return;
        }
        GL43C.glPopDebugGroup();
    }

    @Override
    public void debugMessageInsert(int source, int type, int id, int severity, CharSequence message) {
        if (isGlesContextCurrent()) {
            GLES32.glDebugMessageInsert(source, type, id, severity, message);
            return;
        }
        GL43C.glDebugMessageInsert(source, type, id, severity, message);
    }

}

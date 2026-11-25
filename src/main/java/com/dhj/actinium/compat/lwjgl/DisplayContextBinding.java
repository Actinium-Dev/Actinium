package com.dhj.actinium.compat.lwjgl;

import com.dhj.actinium.startup.CoreProfileDisplayCreator;
import com.dhj.actinium.startup.SdlGpuBootstrap;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.WindowBackends;
import com.gtnewhorizons.angelica.glsm.context.NativeContextHandle;
import com.gtnewhorizons.angelica.lwjgl3.Lwjgl3Context;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.ContextGL;
import org.lwjgl.opengl.DrawableGL;

/** Binds LWJGL native context lifetime events to GLSM's per-drawable state. */
public final class DisplayContextBinding {
    private DisplayContextBinding() {
    }

    /** Uses the drawable API so the GLES context and its function table stay on the calling thread. */
    public static boolean changeGlesDisplayContext(DrawableGL drawable, boolean bind) {
        if (!CoreProfileDisplayCreator.usesGlesDisplay()) return false;
        if (drawable == null) return true;
        try {
            if (bind) drawable.makeCurrent();
            else drawable.releaseContext();
        } catch (LWJGLException failure) {
            throw new IllegalStateException("Could not change the OpenGL ES display context", failure);
        }
        return true;
    }

    public static void bindDisplayContextIfNativeCurrent(DrawableGL drawable) {
        if (SdlGpuBootstrap.isActive()) return;
        final Object contextToken = drawable == null ? null : drawable.getContext();
        final long contextHandle = contextToken == null
            ? 0L
            : ((NativeContextHandle) contextToken).actinium$nativeHandle();
        final long nativeCurrent = WindowBackends.get().currentContextHandle();
        if (nativeCurrent == 0L) {
            releaseContext();
            return;
        }
        if (contextToken == null || contextHandle != nativeCurrent) {
            assertCurrentBindingMatches(nativeCurrent);
            return;
        }
        bindGlesContext(contextToken);
        GLStateManager.bindDisplayContext(contextToken, nativeCurrent);
    }

    public static ContextGL captureDisplayContextBeforeDestroy(DrawableGL drawable, boolean displayCreated) {
        if (SdlGpuBootstrap.isActive()) return null;
        return displayCreated && drawable != null ? drawable.getContext() : null;
    }

    public static void bindIfNativeCurrent(Object contextToken, long contextHandle) {
        if (SdlGpuBootstrap.isActive()) return;
        final long nativeCurrent = WindowBackends.get().currentContextHandle();
        if (nativeCurrent == 0L) {
            releaseContext();
            return;
        }
        if (contextToken == null || contextHandle != nativeCurrent) {
            assertCurrentBindingMatches(nativeCurrent);
            return;
        }
        bindGlesContext(contextToken);
        GLStateManager.bindDrawableContext(contextToken, nativeCurrent);
    }

    public static void releaseIfNativeUnbound() {
        if (SdlGpuBootstrap.isActive()) return;
        final long nativeCurrent = WindowBackends.get().currentContextHandle();
        if (nativeCurrent != 0L) {
            assertCurrentBindingMatches(nativeCurrent);
            return;
        }
        releaseContext();
    }

    private static void bindGlesContext(Object contextToken) {
        if (CoreProfileDisplayCreator.usesGlesDisplay()) Lwjgl3Context.bindGlesContext(contextToken);
    }

    private static void releaseContext() {
        if (CoreProfileDisplayCreator.usesGlesDisplay()) Lwjgl3Context.releaseGlesContext();
        GLStateManager.releaseBoundContext();
    }

    public static void registerSharedContext(Object contextToken, long nativeHandle, Object sharedWithToken) {
        if (SdlGpuBootstrap.isActive() && nativeHandle == 0L) return;
        GLStateManager.registerDrawableContext(contextToken);
    }

    public static void abortBeforeContextDestroy(Object contextToken, boolean shared, long nativeHandle) {
        // Display-list scopes keep their existing process-wide lifetime in this context model.
    }

    public static void retireDestroyedContext(Object contextToken, long nativeHandle) {
        if (nativeHandle <= 0L) {
            GLStateManager.retireDrawableContext(contextToken);
        }
    }

    public static void retireDestroyedDisplayContext(Object contextToken) {
        if (contextToken != null) {
            GLStateManager.retireDisplayContext(contextToken);
        }
    }

    private static void assertCurrentBindingMatches(long nativeCurrent) {
        if (GLStateManager.getBoundContextHandle() != nativeCurrent) {
            throw new IllegalStateException(
                "LWJGL native context 0x" + Long.toHexString(nativeCurrent)
                    + " does not match the GLSM thread binding");
        }
    }
}

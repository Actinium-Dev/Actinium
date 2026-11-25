package com.gtnewhorizons.angelica.lwjgl3;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengles.GLES;
import org.lwjgl.opengles.GLESCapabilities;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.JNI;
import org.lwjgl.system.ThreadLocalUtil;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Selects the LWJGL function table associated with the current host context. */
public final class Lwjgl3Context {
    private static final ThreadLocal<Boolean> GLES_CURRENT = new ThreadLocal<>();
    private static final Map<Object, GLESCapabilities> GLES_CAPABILITIES = new WeakHashMap<>();
    private static boolean glesProviderInitialized;

    private Lwjgl3Context() {}

    /** Installs GLES capabilities after Outlands has made an ES context current. */
    public static synchronized void bindGlesContext(Object contextToken) {
        Objects.requireNonNull(contextToken, "Missing native GLES context token");
        if (!glesProviderInitialized) {
            Configuration.OPENGLES_EXPLICIT_INIT.set(true);
            // LWJGL 3.4.3 uses the same default JNI function table for GL and GLES.
            ThreadLocalUtil.setFunctionMissingAddresses(0);
            GLES.create(GL.getFunctionProvider());
            glesProviderInitialized = true;
        }

        GLESCapabilities caps = GLES_CAPABILITIES.get(contextToken);
        if (caps == null) {
            // Outlands builds desktop capabilities first, probing GL_CONTEXT_PROFILE_MASK on ES.
            final int error = JNI.callI(GL.getCapabilities().glGetError);
            if (error != GL11C.GL_NO_ERROR && error != GL11C.GL_INVALID_ENUM) {
                throw new IllegalStateException("Unexpected GL error after capability probe: 0x" + Integer.toHexString(error));
            }
            caps = GLES.createCapabilities();
            if (!caps.GLES32) {
                GLES.setCapabilities(null);
                GLES_CURRENT.remove();
                throw new IllegalStateException("OpenGL ES 3.2 is required");
            }
            GLES_CAPABILITIES.put(contextToken, caps);
        } else {
            GLES.setCapabilities(caps);
        }
        GLES_CURRENT.set(Boolean.TRUE);
    }

    /** Releases the current thread's GLES function table after the native context is unbound. */
    public static void releaseGlesContext() {
        if (isGlesContextCurrent()) {
            GLES.setCapabilities(null);
            GLES_CURRENT.remove();
        }
    }

    /** Returns whether the current thread should use the GLES bindings. */
    public static boolean isGlesContextCurrent() {
        return GLES_CURRENT.get() == Boolean.TRUE;
    }
}

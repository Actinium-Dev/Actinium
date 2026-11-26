package com.gtnewhorizons.angelica.lwjgl3;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.JNI;

import java.util.function.BooleanSupplier;

/** Selects and caches the indexed indirect draw entry point for the current GL context. */
public final class IndirectMultidraw {
    private static final Logger LOGGER = LogManager.getLogger(IndirectMultidraw.class);
    private static final String EXT_MDI = "GL_EXT_multi_draw_indirect";
    private static final ThreadLocal<Binding> CURRENT = new ThreadLocal<>();

    private IndirectMultidraw() {}

    public static boolean supported() {
        return address() != 0;
    }

    public static void draw(int mode, int type, long offset, int count, int stride) {
        long address = address();
        if (address == 0) throw new IllegalStateException("Indexed indirect multidraw is unavailable on this context");
        JNI.callPV(mode, type, offset, count, stride, address);
    }

    private static long address() {
        GLCapabilities caps = GL.getCapabilities();
        Binding cached = CURRENT.get();
        if (cached != null && cached.context() == caps) return cached.address();

        long address = resolve(caps.OpenGL43 || caps.GL_ARB_multi_draw_indirect, caps.glMultiDrawElementsIndirect,
            () -> caps.glGetStringi != 0 && hasExtension(EXT_MDI), GL.getFunctionProvider());
        CURRENT.set(new Binding(caps, address));
        return address;
    }

    static long resolve(boolean coreOrArbAdvertised, long coreAddress, BooleanSupplier extAdvertised,
                        FunctionProvider functions) {
        if (coreOrArbAdvertised && coreAddress != 0) return coreAddress;
        if (!extAdvertised.getAsBoolean()) return 0;

        long address = functions.getFunctionAddress("glMultiDrawElementsIndirectEXT");
        if (address == 0) LOGGER.warn("{} was advertised without glMultiDrawElementsIndirectEXT", EXT_MDI);
        return address;
    }

    private static boolean hasExtension(String extension) {
        int count = GL11C.glGetInteger(GL30C.GL_NUM_EXTENSIONS);
        for (int index = 0; index < count; index++) {
            if (extension.equals(GL30C.glGetStringi(GL11C.GL_EXTENSIONS, index))) return true;
        }
        return false;
    }

    private record Binding(GLCapabilities context, long address) {}
}

package com.gtnewhorizons.angelica.lwjgl3;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.JNI;

import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Selects and caches the indexed indirect draw entry point for the current GL context. */
public final class IndirectMultidraw {
    static final int COMMAND_WORDS = 5;
    private static final Logger LOGGER = LogManager.getLogger(IndirectMultidraw.class);
    private static final String EXT_MDI = "GL_EXT_multi_draw_indirect";
    private static final String BACKEND_QUERY = "GL_MG_backend_string_getter_access";
    private static final String CORE_ENTRY = "glMultiDrawElementsIndirect";
    private static final Pattern DESKTOP_DRAW_INDIRECT_VERSION = Pattern.compile("(?:[4-9]|[1-9][0-9]+)\\.[0-9]+(?:\\.[0-9]+)?(?: .*|$)");
    private static final ThreadLocal<Binding> CURRENT = new ThreadLocal<>();

    private IndirectMultidraw() {}

    public static boolean supported() {
        return dispatch() != null;
    }

    public static void draw(int mode, int type, long offset, int count, int stride) {
        Dispatch dispatch = dispatch();
        if (dispatch == null) throw new IllegalStateException("Indexed indirect drawing is unavailable on this context");
        dispatch.draw(mode, type, offset, count, stride);
    }

    private static Dispatch dispatch() {
        GLCapabilities caps = GL.getCapabilities();
        Binding cached = CURRENT.get();
        if (cached != null && cached.context() == caps) return cached.dispatch();

        long declaredAddress = caps.OpenGL43 || caps.GL_ARB_multi_draw_indirect ? caps.glMultiDrawElementsIndirect : 0;
        FunctionProvider functions = GL.getFunctionProvider();
        Dispatch dispatch = resolve(declaredAddress,
            () -> singleIndirectAddress(caps.OpenGL40 || caps.GL_ARB_draw_indirect, GL11C.glGetString(GL11C.GL_VERSION), functions),
            extension -> caps.glGetStringi != 0 && hasExtension(extension), functions);
        CURRENT.set(new Binding(caps, dispatch));
        return dispatch;
    }

    static Dispatch resolve(long declaredAddress, LongSupplier drawIndirect, Predicate<String> hasExtension, FunctionProvider functions) {
        boolean discoverFrontend = hasExtension.test(BACKEND_QUERY);
        if (declaredAddress != 0) {
            var core = new Dispatch(declaredAddress, true);
            if (!discoverFrontend || verify(CORE_ENTRY, core)) return core;
        }

        boolean ext = hasExtension.test(EXT_MDI);
        if (ext) {
            String entry = CORE_ENTRY + "EXT";
            long address = functions.getFunctionAddress(entry);
            if (address == 0) {
                LOGGER.warn("{} was advertised without {}", EXT_MDI, entry);
            } else {
                var extension = new Dispatch(address, true);
                if (verify(entry, extension)) return extension;
            }
        }

        if (!discoverFrontend && !ext) return null;
        long singleAddress = drawIndirect.getAsLong();
        if (singleAddress == 0) return null;
        if (discoverFrontend && declaredAddress == 0) {
            long address = functions.getFunctionAddress(CORE_ENTRY);
            if (address != 0) {
                var frontend = new Dispatch(address, true);
                if (verify(CORE_ENTRY, frontend)) return frontend;
            }
        }

        var individual = new Dispatch(singleAddress, false);
        return verify("glDrawElementsIndirect per command", individual) ? individual : null;
    }

    static long singleIndirectAddress(boolean advertised, String reportedVersion, FunctionProvider functions) {
        // Integer and string version reports can differ. Require a callable frontend entry before probing.
        return (advertised || (reportedVersion != null && DESKTOP_DRAW_INDIRECT_VERSION.matcher(reportedVersion).matches()))
            ? functions.getFunctionAddress("glDrawElementsIndirect") : 0;
    }

    private static boolean verify(String entry, Dispatch dispatch) {
        boolean supported = IndexedMultidrawProbe.run(dispatch);
        if (supported) {
            LOGGER.info("Verified indexed indirect multidraw through {}", entry);
        } else {
            LOGGER.warn("{} failed the indexed indirect multidraw draw test", entry);
        }
        return supported;
    }

    private static boolean hasExtension(String extension) {
        int count = GL11C.glGetInteger(GL30C.GL_NUM_EXTENSIONS);
        for (int index = 0; index < count; index++) {
            if (extension.equals(GL30C.glGetStringi(GL11C.GL_EXTENSIONS, index))) return true;
        }
        return false;
    }

    record Dispatch(long address, boolean batched) {
        void draw(int mode, int type, long offset, int count, int stride) {
            if (batched) {
                JNI.callPV(mode, type, offset, count, stride, address);
                return;
            }
            long commandStride = stride == 0 ? COMMAND_WORDS * Integer.BYTES : stride;
            for (int i = 0; i < count; i++) {
                JNI.callPV(mode, type, offset + i * commandStride, address);
            }
        }
    }

    private record Binding(GLCapabilities context, Dispatch dispatch) {}
}

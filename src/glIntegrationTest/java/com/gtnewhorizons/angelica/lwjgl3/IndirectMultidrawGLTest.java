package com.gtnewhorizons.angelica.lwjgl3;

import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.Callback;
import org.lwjgl.system.CallbackI;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libffi.LibFFI;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class IndirectMultidrawGLTest extends GLStateManagerIntegrationFixture {
    private static final String BACKEND_QUERY = "GL_MG_backend_string_getter_access";

    @Test
    void acceptsWorkingFrontendEntryWithOnlyBackendQueryAdvertised() {
        var caps = GL.getCapabilities();
        assumeTrue(caps.OpenGL43 || caps.GL_ARB_multi_draw_indirect);

        var functions = GL.getFunctionProvider();
        var dispatch = IndirectMultidraw.resolve(0,
            () -> IndirectMultidraw.singleIndirectAddress(false, "4.0.0 Frontend", functions),
            BACKEND_QUERY::equals, functions);

        assertEquals(new IndirectMultidraw.Dispatch(caps.glMultiDrawElementsIndirect, true), dispatch);
    }

    @Test
    void skipsSymbolLookupWithoutAnAdvertisedDiscoveryPath() {
        assertNull(IndirectMultidraw.resolve(0, () -> fail("Unexpected single draw lookup"), extension -> false,
            symbol -> fail("Unexpected symbol lookup")));
        assertNull(IndirectMultidraw.resolve(0,
            () -> IndirectMultidraw.singleIndirectAddress(false, "4.0.0 Frontend", symbol -> 0), BACKEND_QUERY::equals,
            symbol -> fail("Unexpected symbol lookup without indirect drawing")));
    }

    @Test
    void replacesNonfunctionalBatchEntryWithVerifiedSingleIndirectDraws() {
        var caps = GL.getCapabilities();
        assumeTrue(caps.OpenGL40 || caps.GL_ARB_draw_indirect);

        var called = new AtomicBoolean();
        MultidrawStub noOp = (mode, type, offset, count, stride) -> called.set(true);
        long address = noOp.address();
        try {
            var fallback = IndirectMultidraw.resolve(0, () -> caps.glDrawElementsIndirect, BACKEND_QUERY::equals, symbol -> address);
            assertEquals(caps.glDrawElementsIndirect, fallback.address());
            assertFalse(fallback.batched());
            assertTrue(called.get());
        } finally {
            Callback.free(address);
        }
    }

    @FunctionalInterface
    private interface MultidrawStub extends CallbackI {
        Callback.Descriptor DESCRIPTOR = new Callback.Descriptor(MultidrawStub.class, MethodHandles.lookup(),
            APIUtil.apiCreateCIF(APIUtil.apiStdcall(), LibFFI.ffi_type_void, LibFFI.ffi_type_uint32, LibFFI.ffi_type_uint32,
                LibFFI.ffi_type_pointer, LibFFI.ffi_type_sint32, LibFFI.ffi_type_sint32));

        void invoke(int mode, int type, long offset, int count, int stride);

        @Override
        default Callback.Descriptor getDescriptor() {
            return DESCRIPTOR;
        }

        @Override
        default void callback(long ret, long args) {
            int pointerSize = POINTER_SIZE;
            invoke(MemoryUtil.memGetInt(MemoryUtil.memGetAddress(args)),
                MemoryUtil.memGetInt(MemoryUtil.memGetAddress(args + pointerSize)),
                MemoryUtil.memGetAddress(MemoryUtil.memGetAddress(args + 2L * pointerSize)),
                MemoryUtil.memGetInt(MemoryUtil.memGetAddress(args + 3L * pointerSize)),
                MemoryUtil.memGetInt(MemoryUtil.memGetAddress(args + 4L * pointerSize)));
        }
    }
}

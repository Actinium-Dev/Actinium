package dhj.embeddedt.embeddium.impl.render.culling;

import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import com.mitchej123.lwjgl.GLExtension;
import dhj.embeddedt.embeddium.impl.render.chunk.MultiDrawMode;
import org.lwjgl.opengl.ContextCapabilities;

import static com.mitchej123.lwjgl.LWJGLServiceProvider.LWJGL;

/**
 * Entry point of the compute cull backend: the selected mode, whether the current context can provide it, and the
 * shared section mirror and compute program every terrain renderer uses.
 *
 * <p>The terrain cull shader targets GLSL 4.30 and requires a GL 4.3 context, backend compute dispatch,
 * shader storage buffers, and indirect multidraw. Other draw modes use the CPU emitter.
 */
public final class GpuCulling {

    private static final SectionMetaBuffer SECTION_META = new SectionMetaBuffer();

    private static volatile GpuCullingMode activeMode = GpuCullingMode.CPU_ONLY;

    private static GpuDrivenChunkCuller culler;

    private GpuCulling() {
    }

    /**
     * Why the compute cull path is or is not available; reported once when a renderer is created.
     */
    public enum Availability {
        AVAILABLE,
        NO_BACKEND_COMPUTE,
        NO_GL43,
        NO_SSBO,
        NO_MULTI_DRAW_INDIRECT,
        MULTI_DRAW_NOT_INDIRECT
    }

    public static GpuCullingMode mode() {
        return activeMode;
    }

    /**
     * Selects the cull mode. Entering a mode that keeps the CPU emitter releases the section mirror and the shared
     * compute program here, so a client on {@link GpuCullingMode#CPU_ONLY} holds no GPU resource of the cull backend;
     * selecting a compute mode again re-creates them through {@link #culler()}.
     */
    public static void setMode(GpuCullingMode mode) {
        final GpuCullingMode previous = activeMode;
        activeMode = mode;
        if (previous != mode && !mode.computeEnabled()) {
            shutdown();
        }
    }

    public static Availability availability() {
        Availability hardware = hardwareAvailability();
        if (hardware == Availability.AVAILABLE && MultiDrawModeResolver.resolve() != MultiDrawMode.INDIRECT) {
            return Availability.MULTI_DRAW_NOT_INDIRECT;
        }
        return hardware;
    }

    /** Checks backend capabilities independently of the user's draw mode and any temporary override. */
    public static Availability hardwareAvailability() {
        final RenderBackend backend = BackendManager.RENDER_BACKEND;
        if (!backend.supportsGpuDrivenCulling()) {
            return Availability.NO_BACKEND_COMPUTE;
        }

        if (!supportsGl43()) {
            return Availability.NO_GL43;
        }
        if (!supportsShaderStorageBuffers()) {
            return Availability.NO_SSBO;
        }
        if (!MultiDrawModeResolver.supportsMultiDrawIndirect()) {
            return Availability.NO_MULTI_DRAW_INDIRECT;
        }
        return Availability.AVAILABLE;
    }

    private static boolean supportsGl43() {
        final ContextCapabilities capabilities = GLStateManager.capabilities;
        return capabilities != null ? capabilities.OpenGL43 : LWJGL.isOpenGLVersionSupported(4, 3);
    }

    /**
     * {@return whether the context provides shader storage buffers}, taken from the GLSM capability cache once it is
     * installed and from the live context before that.
     */
    private static boolean supportsShaderStorageBuffers() {
        if (GLStateManager.capabilities != null) {
            return RenderSystem.supportsSSBO();
        }
        return LWJGL.isOpenGLVersionSupported(4, 3)
                || LWJGL.isExtensionSupported(GLExtension.ARB_shader_storage_buffer_object);
    }

    /**
     * {@return whether the current context can run the compute cull path}
     */
    public static boolean isAvailable() {
        return availability() == Availability.AVAILABLE;
    }

    public static SectionMetaBuffer sectionMeta() {
        return SECTION_META;
    }

    /**
     * {@return the shared compute cull instance, creating it after a release}
     */
    public static GpuDrivenChunkCuller culler() {
        if (culler == null) {
            culler = new GpuDrivenChunkCuller();
        }
        return culler;
    }

    /**
     * Drops every published section, so a new world does not inherit slots of the previous one.
     */
    public static void onWorldUnload() {
        SECTION_META.reset();
    }

    /**
     * Releases the section mirror and the compute program with its buffers. The configured mode is untouched, so the
     * caller decides whether the next frame rebuilds the backend through {@link #culler()}.
     */
    public static void shutdown() {
        if (culler != null) {
            culler.shutdown();
            culler = null;
        }
        SECTION_META.shutdown();
    }
}

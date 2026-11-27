package com.dhj.actinium.startup;

import com.dhj.actinium.runtime.ActiniumRuntime;
import com.gtnewhorizons.angelica.glsm.backend.BackendOptions;
import com.gtnewhorizons.angelica.glsm.backend.BackendStartGuard;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import com.gtnewhorizons.angelica.glsm.loading.DependencyVerifier;
import com.gtnewhorizons.angelica.glsm.loading.DependencyVerifier.Check;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUDisplayBridge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.Drawable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.PixelFormat;
import org.lwjgl.opengl.SharedDrawable;
import dhj.embeddedt.embeddium.impl.gui.SodiumGameOptions;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** Reads the renderer choice before window creation and isolates optional host APIs. */
public final class SdlGpuBootstrap {
    private static final Logger LOGGER = LogManager.getLogger("Actinium");
    private static final Check MISSING_HOST_REQUIREMENT = DependencyVerifier.firstMissing(SdlGpuBootstrap.class, List.of(
        new Check("/net/minecraftforge/client/render/RenderBackendSelection.class", "Host render backend API"),
        new Check("/org/lwjgl/opengl/DisplayEvents.class", "Window lifecycle callbacks"),
        new Check("/org/lwjgl/MainThread.class", "Main-thread API"),
        new Check("/org/lwjgl/sdl/SDLGPU.class", "LWJGL SDL GPU bindings")
    ));
    private static boolean initialized;
    private static boolean requested;
    private static volatile StartupSettings startupSettings;

    private SdlGpuBootstrap() {}

    public static boolean isSupported() {
        return MISSING_HOST_REQUIREMENT == null;
    }

    /** Reports the first missing dependency without registering the backend or creating a device. */
    public static String missingRequirement() {
        return isSupported() ? Outlands.missingRequirement() : MISSING_HOST_REQUIREMENT.errorMessage();
    }

    /** Compares the applied renderer settings with those consumed before window creation. */
    public static boolean restartRequired(SodiumGameOptions.AdvancedSettings current) {
        StartupSettings startup = startupSettings;
        return startup != null && startup.differsFrom(current);
    }

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        var options = ActiniumRuntime.options();
        var advanced = options.advanced;
        boolean guardTripped = BackendStartGuard.begin(new File("config/actinium"), advanced.renderBackendSdlGpu,
            SystemProperties.SDL_GPU_OVERRIDE != null, SDLGPUGate.REQUIREMENTS_REVISION,
            BackendOptions.sdlGpuDriver(advanced.renderBackendDriver));
        if (guardTripped) {
            advanced.renderBackendSdlGpu = false;
            try {
                SodiumGameOptions.writeToDisk(options);
            } catch (IOException failure) {
                LOGGER.error("Could not save the OpenGL backend selection after repeated SDL startup failures", failure);
            }
        }
        BackendOptions.latch(advanced.renderBackendSdlGpu, advanced.renderBackendDriver, advanced.glVersion);
        GLSMConfig.sdlGpuFramesInFlight = advanced.sdlGpuFramesInFlight;
        GLSMConfig.sdlGpuPresenterThread = advanced.sdlGpuPresenterThread;
        GLSMConfig.sdlGpuInitializeFragmentOutputs = advanced.sdlGpuInitializeFragmentOutputs;
        requested = BackendOptions.sdlGpuRequested();
        startupSettings = StartupSettings.capture(advanced);
        if (!requested) return;
        if (isSupported()) {
            Outlands.initialize();
        } else {
            LOGGER.warn("Using OpenGL for this launch because SDL GPU is missing {}", MISSING_HOST_REQUIREMENT.errorMessage());
        }
    }

    public static boolean isActive() { return requested && isSupported() && Outlands.isActive(); }

    public static void ensureDrawableInstalled() {
        if (isActive()) Outlands.ensureDrawableInstalled();
    }

    public static void onLoadingComplete() {
        if (isActive()) BackendStartGuard.latch();
    }

    public static boolean createDisplay(PixelFormat format) {
        initialize();
        if (!requested || !isSupported() || !Outlands.isSelected()) return false;
        Display.create(format);
        return true;
    }

    public static boolean isSharedDrawable(Object drawable) {
        return isActive() && Outlands.isSharedDrawable(drawable);
    }

    public static void makeCurrent(Drawable drawable) throws LWJGLException { Outlands.makeCurrent(drawable); }
    public static void releaseContext(Drawable drawable) throws LWJGLException { Outlands.releaseContext(drawable); }
    public static boolean isCurrent(Drawable drawable) { return Outlands.isCurrent(drawable); }
    public static void destroySharedDrawable(SharedDrawable drawable) throws LWJGLException { Outlands.destroySharedDrawable(drawable); }
    public static void present() { Outlands.present(); }
    public static void setSwapInterval(int interval) { Outlands.setSwapInterval(interval); }
    public static String adapter() { return Outlands.adapter(); }
    public static String version() { return Outlands.version(); }
    public static void checkGLError() { GLStateManager.checkGLError(); }
    public static void initContext(float red, float green, float blue) {
        GLStateManager.glClearColor(red, green, blue, 0.0f);
        GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    record StartupSettings(boolean sdlGpu, String driver, int glVersion, int framesInFlight, boolean presenterThread,
                           boolean initializeFragmentOutputs) {
        static StartupSettings capture(SodiumGameOptions.AdvancedSettings options) {
            return new StartupSettings(options.renderBackendSdlGpu, options.renderBackendDriver, options.glVersion,
                options.sdlGpuFramesInFlight, options.sdlGpuPresenterThread, options.sdlGpuInitializeFragmentOutputs);
        }

        boolean differsFrom(SodiumGameOptions.AdvancedSettings current) {
            return !this.equals(capture(current));
        }
    }

    private static final class Outlands {
        private static String missingRequirement() { return SDLGPUGate.missingRequirement(); }
        private static void initialize() { SDLGPUGate.registerBackend(); }
        private static boolean isActive() { return SDLGPUGate.isActive(); }
        private static boolean isSelected() { return SDLGPUGate.isSelected(); }
        private static boolean isSharedDrawable(Object drawable) { return SDLGPUDisplayBridge.isSdlSharedDrawable(drawable); }
        private static void ensureDrawableInstalled() { SDLGPUGate.ensureDrawableInstalled(); }
        private static void makeCurrent(Drawable drawable) throws LWJGLException { SDLGPUDisplayBridge.makeCurrent(drawable); }
        private static void releaseContext(Drawable drawable) throws LWJGLException { SDLGPUDisplayBridge.releaseContext(drawable); }
        private static boolean isCurrent(Drawable drawable) { return SDLGPUDisplayBridge.isCurrent(drawable); }
        private static void destroySharedDrawable(SharedDrawable drawable) throws LWJGLException { SDLGPUDisplayBridge.destroySharedDrawable(drawable); }
        private static void present() { SDLGPUDisplayBridge.present(); }
        private static void setSwapInterval(int interval) { SDLGPUDisplayBridge.setSwapInterval(interval); }
        private static String adapter() { return SDLGPUDisplayBridge.adapter(); }
        private static String version() { return SDLGPUDisplayBridge.version(); }
    }
}

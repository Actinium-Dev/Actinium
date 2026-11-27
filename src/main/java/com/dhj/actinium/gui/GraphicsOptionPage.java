package com.dhj.actinium.gui;

import com.dhj.actinium.debug.OpenGlVersion;
import com.dhj.actinium.startup.CoreProfileDisplayCreator;
import com.dhj.actinium.startup.SdlGpuBootstrap;
import com.gtnewhorizons.angelica.config.GLVersionChoice;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendOptions;
import com.gtnewhorizons.angelica.glsm.backend.BackendStartGuard;
import com.gtnewhorizons.angelica.glsm.backend.MoltenVK;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import dhj.embeddedt.embeddium.api.options.OptionIdentifier;
import dhj.embeddedt.embeddium.api.options.control.ControlValueFormatter;
import dhj.embeddedt.embeddium.api.options.control.CyclingControl;
import dhj.embeddedt.embeddium.api.options.control.ExternalButtonControl;
import dhj.embeddedt.embeddium.api.options.control.ReadOnlyTextControl;
import dhj.embeddedt.embeddium.api.options.control.SliderControl;
import dhj.embeddedt.embeddium.api.options.control.TickBoxControl;
import dhj.embeddedt.embeddium.api.options.structure.Option;
import dhj.embeddedt.embeddium.api.options.structure.OptionFlag;
import dhj.embeddedt.embeddium.api.options.structure.OptionGroup;
import dhj.embeddedt.embeddium.api.options.structure.OptionImpl;
import dhj.embeddedt.embeddium.api.options.structure.OptionPage;
import dhj.embeddedt.embeddium.api.options.structure.StandardOptions;
import dhj.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import dhj.embeddedt.embeddium.impl.gui.SodiumGameOptions.SdlGpuDriver;
import dhj.embeddedt.embeddium.impl.gui.framework.TextComponent;
import me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen;
import net.minecraft.util.Util;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** Builds the startup graphics settings and the status of the running backend. */
public final class GraphicsOptionPage {
    private GraphicsOptionPage() {
    }

    static OptionGroup link(SodiumGameOptions options) {
        return OptionGroup.createBuilder()
                .setId(OptionIdentifier.create("actinium", "graphics_link"))
                .add(OptionImpl.createBuilder(Void.class, options)
                        .setId(OptionIdentifier.create("actinium", "open_graphics", Void.class))
                        .setName(TextComponent.translatable("options.actinium.graphics.link"))
                        .setTooltip(TextComponent.translatable("options.actinium.graphics.link.tooltip"))
                        .setControl(option -> new ExternalButtonControl(option, screen -> {
                            if (screen instanceof SodiumVideoOptionsScreen videoOptions) {
                                videoOptions.showPage(StandardOptions.Pages.GRAPHICS);
                            }
                        }, TextComponent.literal(BackendManager.RENDER_BACKEND.getName())))
                        .setBinding((opts, value) -> { }, opts -> null)
                        .build())
                .build();
    }

    public static OptionPage create(SodiumGameOptions options) {
        Util.EnumOS platform = Util.getOSType();
        String missingRequirement = SdlGpuBootstrap.missingRequirement();
        boolean sdlAvailable = missingRequirement == null;
        Boolean backendOverride = SystemProperties.SDL_GPU_OVERRIDE;
        String driverOverride = SystemProperties.SDL_GPU_DRIVER_OVERRIDE;
        boolean launcherControlsGlContext = CoreProfileDisplayCreator.launcherControlsGlContext();
        int glOverride = launcherControlsGlContext ? -1 : CoreProfileDisplayCreator.glVersionOverride();
        int glCeiling = CoreProfileDisplayCreator.glVersionCeiling();
        boolean backendEditable = sdlAvailable && backendOverride == null;
        boolean driverEditable = sdlAvailable && driverOverride == null;
        boolean glEditable = glOverride < 0;

        OptionGroup settings = OptionGroup.createBuilder()
                .setId(OptionIdentifier.create("actinium", "render_backend"))
                .add(OptionImpl.createBuilder(boolean.class, options)
                        .setId(OptionIdentifier.create("actinium", "sdl_gpu", boolean.class))
                        .setName(TextComponent.translatable("options.actinium.sdl_gpu"))
                        .setTooltip(tooltip("options.actinium.sdl_gpu.tooltip", missingRequirement,
                                SystemProperties.KEY_USE_SDL_GPU, backendOverride))
                        .setControl(option -> new CyclingControl<>(option, new Boolean[]{false, true},
                                new TextComponent[]{TextComponent.literal("OpenGL"),
                                        TextComponent.translatable("options.actinium.graphics.backend.sdl_gpu")}))
                        .setBinding((opts, value) -> opts.advanced.renderBackendSdlGpu = value,
                                opts -> backendOverride != null ? backendOverride : opts.advanced.renderBackendSdlGpu)
                        .setEnabled(backendEditable)
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .setDefaultValue(backendEditable ? Boolean.FALSE : null)
                        .build())
                .add(OptionImpl.createBuilder(SdlGpuDriver.class, options)
                        .setId(OptionIdentifier.create("actinium", "sdl_driver", SdlGpuDriver.class))
                        .setName(TextComponent.translatable("options.actinium.sdl_driver"))
                        .setTooltip(tooltip("options.actinium.sdl_driver.tooltip", missingRequirement,
                                SystemProperties.KEY_SDL_GPU_DRIVER, driverOverride))
                        .setControl(option -> driverControl(option, platform, driverOverride))
                        .setBinding((opts, value) -> opts.advanced.renderBackendDriver = value.id(),
                                opts -> shownDriver(opts.advanced.renderBackendDriver, driverOverride))
                        .setEnabled(driverEditable)
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .setDefaultValue(driverEditable ? SdlGpuDriver.AUTO : null)
                        .build())
                .add(OptionImpl.createBuilder(int.class, options)
                        .setId(OptionIdentifier.create("actinium", "sdl_frames_in_flight", int.class))
                        .setName(TextComponent.translatable("options.actinium.sdl_frames_in_flight"))
                        .setTooltip(tooltip("options.actinium.sdl_frames_in_flight.tooltip", missingRequirement, null, null))
                        .setControl(option -> new SliderControl(option, 1, 3, 1, ControlValueFormatter.number()))
                        .setBinding((opts, value) -> opts.advanced.sdlGpuFramesInFlight = value,
                                opts -> opts.advanced.sdlGpuFramesInFlight)
                        .setEnabled(sdlAvailable)
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .setDefaultValue(sdlAvailable ? 2 : null)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, options)
                        .setId(OptionIdentifier.create("actinium", "sdl_presenter_thread", boolean.class))
                        .setName(TextComponent.translatable("options.actinium.sdl_presenter_thread"))
                        .setTooltip(tooltip("options.actinium.sdl_presenter_thread.tooltip", missingRequirement, null, null))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.advanced.sdlGpuPresenterThread = value,
                                opts -> opts.advanced.sdlGpuPresenterThread)
                        .setEnabled(sdlAvailable)
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .setDefaultValue(sdlAvailable ? Boolean.TRUE : null)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, options)
                        .setId(OptionIdentifier.create("actinium", "sdl_initialize_fragment_outputs", boolean.class))
                        .setName(TextComponent.translatable("options.actinium.sdl_initialize_fragment_outputs"))
                        .setTooltip(tooltip("options.actinium.sdl_initialize_fragment_outputs.tooltip", missingRequirement, null, null))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.advanced.sdlGpuInitializeFragmentOutputs = value,
                                opts -> opts.advanced.sdlGpuInitializeFragmentOutputs)
                        .setEnabled(sdlAvailable)
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .setDefaultValue(sdlAvailable ? Boolean.FALSE : null)
                        .build())
                .addConditionally(!launcherControlsGlContext, () -> OptionImpl.createBuilder(GLVersionChoice.class, options)
                        .setId(OptionIdentifier.create("actinium", "gl_version", GLVersionChoice.class))
                        .setName(TextComponent.translatable("options.actinium.graphics.gl_version"))
                        .setTooltip(tooltip("options.actinium.graphics.gl_version.tooltip", null,
                                SystemProperties.KEY_GL_PROFILE, glEditable ? null : SystemProperties.GL_PROFILE))
                        .setControl(option -> glVersionControl(option, glCeiling))
                        .setBinding((opts, value) -> opts.advanced.glVersion = value.version(),
                                opts -> GLVersionChoice.of(glEditable ? opts.advanced.glVersion : glOverride, glCeiling))
                        .setEnabled(glEditable)
                        .setFlags(OptionFlag.REQUIRES_GAME_RESTART)
                        .setDefaultValue(glEditable ? GLVersionChoice.AUTO : null)
                        .build())
                .build();

        OptionGroup status = OptionGroup.createBuilder()
                .setId(OptionIdentifier.create("actinium", "graphics_runtime"))
                .add(readOnly(options, "active", () -> ActiveBackend.DESCRIPTION))
                .add(readOnly(options, "status", () -> status(options, sdlAvailable, !glEditable)))
                .addConditionally(platform == Util.EnumOS.OSX, () -> readOnly(options, "moltenvk",
                        () -> TextComponent.translatable("options.actinium.graphics.moltenvk."
                                + MoltenVK.source().name().toLowerCase(Locale.ROOT))))
                .build();

        return new OptionPage(StandardOptions.Pages.GRAPHICS,
                TextComponent.translatable("options.actinium.graphics.page"), List.of(settings, status));
    }

    static SdlGpuDriver[] allowedDrivers(Util.EnumOS platform) {
        return switch (platform) {
            case WINDOWS -> new SdlGpuDriver[]{SdlGpuDriver.AUTO, SdlGpuDriver.VULKAN, SdlGpuDriver.DIRECT3D12};
            case OSX -> new SdlGpuDriver[]{SdlGpuDriver.AUTO, SdlGpuDriver.METAL, SdlGpuDriver.VULKAN};
            default -> new SdlGpuDriver[]{SdlGpuDriver.AUTO, SdlGpuDriver.VULKAN};
        };
    }

    private static CyclingControl<SdlGpuDriver> driverControl(Option<SdlGpuDriver> option, Util.EnumOS platform, String override) {
        if (override == null) {
            return new CyclingControl<>(option, SdlGpuDriver.class, allowedDrivers(platform));
        }

        SdlGpuDriver displayed = option.getAppliedValue();
        TextComponent label = displayed.id().equalsIgnoreCase(override)
                ? displayed.getLocalizedName()
                : TextComponent.literal(override);
        return new CyclingControl<>(option, new SdlGpuDriver[]{displayed}, new TextComponent[]{label});
    }

    private static SdlGpuDriver shownDriver(String configured, String override) {
        if (override != null) {
            for (SdlGpuDriver driver : SdlGpuDriver.values()) {
                if (driver.id().equalsIgnoreCase(override)) {
                    return driver;
                }
            }
            return SdlGpuDriver.AUTO;
        }
        return SdlGpuDriver.fromId(configured);
    }

    private static CyclingControl<GLVersionChoice> glVersionControl(Option<GLVersionChoice> option, int ceiling) {
        GLVersionChoice[] values = Arrays.stream(GLVersionChoice.values())
                .filter(version -> version.version() <= ceiling)
                .toArray(GLVersionChoice[]::new);
        TextComponent[] names = Arrays.stream(values)
                .map(version -> version == GLVersionChoice.AUTO
                        ? TextComponent.translatable("options.actinium.sdl_driver.auto")
                        : TextComponent.literal(version.label()))
                .toArray(TextComponent[]::new);
        return new CyclingControl<>(option, values, names);
    }

    private static TextComponent tooltip(String key, String missing, String property, Object override) {
        TextComponent text = TextComponent.translatable(key);
        if (missing != null) {
            text = TextComponent.translatable("options.actinium.graphics.unavailable", text, missing);
        }
        if (override != null) {
            text = TextComponent.translatable("options.actinium.graphics.overridden", text, property, override);
        }
        return text;
    }

    private static Option<Void> readOnly(SodiumGameOptions options, String id, Supplier<TextComponent> value) {
        String key = "options.actinium.graphics." + id;
        return OptionImpl.createBuilder(Void.class, options)
                .setId(OptionIdentifier.create("actinium", "graphics_" + id, Void.class))
                .setName(TextComponent.translatable(key))
                .setTooltip(TextComponent.translatable(key + ".tooltip"))
                .setControl(option -> new ReadOnlyTextControl(option, value))
                .setBinding((opts, ignored) -> { }, opts -> null)
                .build();
    }

    private static TextComponent activeBackend() {
        RenderBackend backend = BackendManager.RENDER_BACKEND;
        String name = backend.getName();
        if (!backend.isSDLGPU() && backend.hasContext()) {
            String versionString = backend.getString(GL11.GL_VERSION);
            if (versionString != null && versionString.startsWith("OpenGL ES")) {
                name += " / " + versionString;
            } else {
                OpenGlVersion version = OpenGlVersion.parse(versionString);
                name += " " + version.major() + "." + version.minor();
            }
        }
        return TextComponent.literal(name);
    }

    private static final class ActiveBackend {
        private static final TextComponent DESCRIPTION = activeBackend();
    }

    private static TextComponent status(SodiumGameOptions options, boolean sdlAvailable, boolean glOverridden) {
        String status;
        if (SdlGpuBootstrap.restartRequired(options.advanced)) {
            status = "restart";
        } else if (BackendStartGuard.tripped() && !options.advanced.renderBackendSdlGpu) {
            status = "reset";
        } else if (BackendOptions.sdlGpuRequested() && !BackendManager.RENDER_BACKEND.isSDLGPU()) {
            status = sdlAvailable ? "failed" : "unavailable";
        } else if (SystemProperties.SDL_GPU_OVERRIDE != null || SystemProperties.SDL_GPU_DRIVER_OVERRIDE != null || glOverridden) {
            status = "forced";
        } else {
            status = "running";
        }
        return TextComponent.translatable("options.actinium.graphics.status." + status);
    }
}

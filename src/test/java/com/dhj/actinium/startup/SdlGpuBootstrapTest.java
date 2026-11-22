package com.dhj.actinium.startup;

import com.dhj.actinium.startup.SdlGpuBootstrap.StartupSettings;
import dhj.embeddedt.embeddium.impl.gui.SodiumGameOptions.AdvancedSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdlGpuBootstrapTest {
    @Test
    void applyingAnyGraphicsSettingRequiresRestartUntilTheStartupValueIsRestored() {
        List<Consumer<AdvancedSettings>> changes = List.of(
            options -> options.renderBackendSdlGpu = true,
            options -> options.renderBackendDriver = "vulkan",
            options -> options.glVersion = 45,
            options -> options.sdlGpuFramesInFlight = 1,
            options -> options.sdlGpuPresenterThread = false
        );

        for (Consumer<AdvancedSettings> apply : changes) {
            AdvancedSettings options = new AdvancedSettings();
            StartupSettings startup = StartupSettings.capture(options);
            assertFalse(startup.differsFrom(options));

            apply.accept(options);
            assertTrue(startup.differsFrom(options), () -> "Changed settings: " + StartupSettings.capture(options));
            assertFalse(startup.differsFrom(new AdvancedSettings()));
        }
    }
}

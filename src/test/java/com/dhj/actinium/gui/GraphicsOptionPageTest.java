package com.dhj.actinium.gui;

import dhj.embeddedt.embeddium.impl.gui.SodiumGameOptions.SdlGpuDriver;
import net.minecraft.util.Util;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphicsOptionPageTest {
    @Test
    void driverChoicesIncludeCommonDriversAndPermitNativeDriversOnlyOnTheirPlatforms() {
        List<SdlGpuDriver> windows = List.of(GraphicsOptionPage.allowedDrivers(Util.EnumOS.WINDOWS));
        List<SdlGpuDriver> mac = List.of(GraphicsOptionPage.allowedDrivers(Util.EnumOS.OSX));
        List<SdlGpuDriver> linux = List.of(GraphicsOptionPage.allowedDrivers(Util.EnumOS.LINUX));

        for (List<SdlGpuDriver> choices : List.of(windows, mac, linux)) {
            assertTrue(choices.containsAll(List.of(SdlGpuDriver.AUTO, SdlGpuDriver.VULKAN)));
        }
        assertTrue(windows.contains(SdlGpuDriver.DIRECT3D12));
        assertFalse(windows.contains(SdlGpuDriver.METAL));
        assertTrue(mac.contains(SdlGpuDriver.METAL));
        assertFalse(mac.contains(SdlGpuDriver.DIRECT3D12));
        assertFalse(linux.contains(SdlGpuDriver.DIRECT3D12));
        assertFalse(linux.contains(SdlGpuDriver.METAL));
    }
}

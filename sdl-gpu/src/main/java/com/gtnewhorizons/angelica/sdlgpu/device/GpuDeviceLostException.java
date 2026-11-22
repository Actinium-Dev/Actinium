package com.gtnewhorizons.angelica.sdlgpu.device;

public final class GpuDeviceLostException extends Error {

    public GpuDeviceLostException(String operation, String sdlError) {
        super("GPU device lost during " + operation + ": " + sdlError
            + ". Actinium cannot continue rendering after device loss. Report this issue at "
            + "https://github.com/Actinium-Dev/Actinium/issues and attach logs/latest.log and the crash report.");
    }
}

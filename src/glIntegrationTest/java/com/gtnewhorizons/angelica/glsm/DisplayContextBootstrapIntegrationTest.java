package com.gtnewhorizons.angelica.glsm;

import com.dhj.actinium.compat.lwjgl.DisplayContextBinding;
import com.gtnewhorizons.angelica.glsm.backend.WindowBackends;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.DrawableGL;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DisplayContextBootstrapIntegrationTest {
    @Test
    void firstContextCallbackBindsThePrimaryDisplayState() throws Exception {
        WindowBackends.useSdl();
        Display.setDisplayMode(new DisplayMode(64, 64));
        Display.create();
        try {
            var drawable = (DrawableGL) Display.getDrawable();
            var context = drawable.getContext();
            long handle = WindowBackends.get().currentContextHandle();
            assertNotEquals(0L, handle);

            DisplayContextBinding.bindIfNativeCurrent(context, handle);

            assertSame(GLStateManager.primaryContext(), GLStateManager.ctx());
        } finally {
            Display.destroy();
        }
    }
}

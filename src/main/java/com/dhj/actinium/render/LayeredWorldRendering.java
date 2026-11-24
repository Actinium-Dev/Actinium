package com.dhj.actinium.render;

import com.dhj.actinium.render.translucency.TranslucencyLayers;
import com.dhj.actinium.render.translucency.TranslucentLayer;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;

/** Scopes vanilla world draws that contribute to the translucency composite. */
public final class LayeredWorldRendering {
    private LayeredWorldRendering() {
    }

    /** Records a draw while preserving the caller's depth write mask. */
    public static void draw(TranslucentLayer layer, boolean forceDepthWrite, Runnable draw) {
        boolean depthMask = GLSMDepthStencilAlpha.getDepthState().isMaskEnabled();
        TranslucencyLayers.INSTANCE.begin(layer);
        try {
            if (forceDepthWrite) {
                GLSMDepthStencilAlpha.glDepthMask(true);
            }
            draw.run();
        } finally {
            try {
                TranslucencyLayers.INSTANCE.end();
            } finally {
                GLSMDepthStencilAlpha.glDepthMask(depthMask);
            }
        }
    }
}

package com.dhj.actinium.render.iris;

import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import org.lwjgl.opengl.GL11;

/** Runs precipitation against the scene produced by the deferred and translucent passes. */
public final class IrisWeatherPass {
    private static final int STATE_MASK = GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
        | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_FOG_BIT | GL11.GL_TEXTURE_BIT;

    private IrisWeatherPass() {}

    public static void render(WorldRenderingPipeline pipeline, Runnable setupFog, Runnable drawWeather) {
        if (!pipeline.shouldRenderWeather()) {
            return;
        }

        WorldRenderingPhase previousPhase = pipeline.getPhase();
        GLSMAttribStack.glPushAttrib(STATE_MASK);
        try {
            setupFog.run();
            GLSMDepthStencilAlpha.glDepthMask(pipeline.shouldWriteRainAndSnowToDepthBuffer());
            pipeline.setPhase(WorldRenderingPhase.RAIN_SNOW);
            drawWeather.run();
        } finally {
            try {
                GLSMAttribStack.glPopAttrib();
            } finally {
                pipeline.setPhase(previousPhase);
            }
        }
    }
}

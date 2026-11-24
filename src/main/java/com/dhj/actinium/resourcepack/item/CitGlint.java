package com.dhj.actinium.resourcepack.item;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.GlintClock;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

record CitGlint(ResourceLocation texture, int layer, Blend blend, float speed, float rotation, float duration) {
    enum Method { AVERAGE, LAYERED, CYCLE }

    record Settings(boolean vanilla, Method method, int cap, float fade) {
        static Settings parse(Properties properties) {
            return new Settings(Boolean.parseBoolean(properties.getProperty("useGlint", "true").trim()),
                Method.valueOf(properties.getProperty("method", "average").trim().toUpperCase(Locale.ROOT)),
                Math.max(0, Integer.parseInt(properties.getProperty("cap", "99").trim())),
                Math.max(0, decimal(properties, "fade", 0.5f)));
        }
    }

    enum Blend {
        ADD(GL11.GL_SRC_ALPHA, GL11.GL_ONE), ALPHA(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA),
        MULTIPLY(GL11.GL_DST_COLOR, GL11.GL_ONE_MINUS_SRC_ALPHA), DODGE(GL11.GL_ONE, GL11.GL_ONE),
        BURN(GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR), SCREEN(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_COLOR),
        OVERLAY(GL11.GL_DST_COLOR, GL11.GL_SRC_COLOR), SUBTRACT(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ZERO),
        REPLACE(GL11.GL_ONE, GL11.GL_ZERO), GLINT(GL11.GL_SRC_COLOR, GL11.GL_ONE);

        final int source;
        final int destination;

        Blend(int source, int destination) {
            this.source = source;
            this.destination = destination;
        }
    }

    static CitGlint parse(Properties properties, ResourceLocation texture) {
        if (texture == null) throw new IllegalArgumentException("Enchantment rule requires a texture");
        float duration = decimal(properties, "duration", 1);
        if (duration <= 0) throw new IllegalArgumentException("Glint duration must be positive");
        return new CitGlint(texture, Integer.parseInt(properties.getProperty("layer", "0").trim()),
            Blend.valueOf(properties.getProperty("blend", "add").trim().toUpperCase(Locale.ROOT)),
            decimal(properties, "speed", 0), decimal(properties, "rotation", 0), duration);
    }

    private static float decimal(Properties properties, String name, float fallback) {
        float value = Float.parseFloat(properties.getProperty(name, Float.toString(fallback)).trim());
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Invalid " + name);
        return value;
    }

    static void draw(List<CitGlint> effects, Settings settings, Consumer<ResourceLocation> bind, Runnable render) {
        if (effects.isEmpty()) return;
        int matrix = GLStateManager.glGetInteger(GL11.GL_MATRIX_MODE);
        GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GLStateManager.glActiveTexture(OpenGlHelper.defaultTexUnit);
            GLStateManager.glEnable(GL11.GL_BLEND);
            GLStateManager.glDisable(GL11.GL_LIGHTING);
            GLStateManager.glDepthMask(false);
            GLStateManager.glDepthFunc(GL11.GL_EQUAL);
            double seconds = GlintClock.millis() / 1000.0;
            double cycle = effects.stream().mapToDouble(effect -> effect.duration + 2 * settings.fade).sum();
            double offset = seconds - Math.floor(seconds / cycle) * cycle;
            for (CitGlint effect : effects) {
                float intensity = switch (settings.method) {
                    case AVERAGE -> 1f / effects.size();
                    case LAYERED -> 1f;
                    case CYCLE -> cycleIntensity(offset, effect.duration, settings.fade);
                };
                offset -= effect.duration + 2 * settings.fade;
                if (intensity <= 0) continue;
                bind.accept(effect.texture);
                GLStateManager.glBlendFunc(effect.blend.source, effect.blend.destination);
                GLStateManager.glColor4f(1, 1, 1, intensity);
                GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
                GLStateManager.glPushMatrix();
                try {
                    GLStateManager.glLoadIdentity();
                    double phase = GlintClock.millis() * (double) effect.speed / 3000.0;
                    GLStateManager.glTranslatef((float) (phase - Math.floor(phase)), 0, 0);
                    GLStateManager.glRotatef(effect.rotation, 0, 0, 1);
                    GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
                    render.run();
                } finally {
                    GLStateManager.glActiveTexture(OpenGlHelper.defaultTexUnit);
                    GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
                    GLStateManager.glPopMatrix();
                }
            }
        } finally {
            GLStateManager.glMatrixMode(matrix);
            GLStateManager.glPopAttrib();
        }
    }

    private static float cycleIntensity(double offset, float duration, float fade) {
        if (offset < 0 || offset >= duration + 2 * fade) return 0;
        if (fade == 0) return 1;
        return (float) Math.min(1, Math.min(offset, duration + 2 * fade - offset) / fade);
    }
}

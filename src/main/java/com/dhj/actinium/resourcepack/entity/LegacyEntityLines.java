package com.dhj.actinium.resourcepack.entity;

import com.dhj.actinium.resourcepack.ResourcePackIndex;
import com.dhj.actinium.resourcepack.ResourcePackReload;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.io.IOException;
import java.util.Properties;

/** Replaces vanilla fishing lines and leads when their legacy resource-pack textures are present. */
public final class LegacyEntityLines {
    private static final Logger LOGGER = LogManager.getLogger("Actinium/LegacyEntityLines");
    private static final int STATE = GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_LIGHTING_BIT;
    private static ResourcePackReload.Handle<Snapshot> handle;

    private LegacyEntityLines() {}

    /** Registers the lines under the same random-entity option as the other legacy mob features. */
    public static void register() {
        handle = ResourcePackReload.register(new Module());
    }

    /** Draws a fishing line from the bobber along the displacement computed by vanilla. */
    public static boolean drawFishingLine(double x, double y, double z, double dx, double dy, double dz) {
        Snapshot snapshot = handle == null ? null : handle.get();
        return snapshot != null && snapshot.fishing != null && draw(snapshot.fishing, x, y, z, dx, dy, dz);
    }

    /** Draws a lead from the mob's adjusted leash origin along the displacement computed by vanilla. */
    public static boolean drawLead(double x, double y, double z, double dx, double dy, double dz) {
        Snapshot snapshot = handle == null ? null : handle.get();
        return snapshot != null && snapshot.lead != null && draw(snapshot.lead, x, y, z, dx, dy, dz);
    }

    private static boolean draw(Line line, double x, double y, double z, double dx, double dy, double dz) {
        int previousTexture = GLStateManager.getBoundTextureForServerState(0);
        GLStateManager.pushState(STATE);
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            boolean textureEnabled = GLStateManager.glIsEnabled(GL11.GL_TEXTURE_2D);
            try {
                var manager = Minecraft.getMinecraft().getTextureManager();
                var texture = manager.getTexture(line.texture);
                if (texture == null) {
                    texture = new SimpleTexture(line.texture);
                    if (!manager.loadTexture(line.texture, texture)) return false;
                }
                if (texture == TextureUtil.MISSING_TEXTURE) return false;

                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture.getGlTextureId());
                if (!textureEnabled) GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
                GLStateManager.glDisable(GL11.GL_LIGHTING);
                GLStateManager.glDisable(GL11.GL_CULL_FACE);

                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder buffer = tessellator.getBuffer();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
                line.style.emit(x, y, z, dx, dy, dz,
                    (vx, vy, vz, u, v) -> buffer.pos(vx, vy, vz).tex(u, v).color(255, 255, 255, 255).endVertex());
                tessellator.draw();
                return true;
            } finally {
                GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
                // Restore through GLSM hooks so Iris also restores the sampler and PBR inputs.
                GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
                if (!textureEnabled) GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
            }
        } finally {
            GLStateManager.popState();
        }
    }

    private record Line(ResourceLocation texture, LegacyLineStyle style) {}

    private record Snapshot(Line fishing, Line lead) {}

    private static final class Module implements ResourcePackReload.Module<Snapshot, Snapshot> {
        @Override public String option() { return "randomEntities"; }

        @Override
        public Snapshot prepare(ResourcePackIndex index) {
            return new Snapshot(load(index, "fishingline", 0.0075, 0, 0.25, 16),
                load(index, "lead", 0.025, 4.0 / 3.0, 0.125, 24));
        }

        @Override
        public Snapshot finish(Snapshot prepared, ResourcePackIndex index, TextureMap atlas) {
            return prepared;
        }

        private static Line load(ResourcePackIndex index, String name, double width, double a, double b, int segments) {
            String path = "mcpatcher/line/" + name;
            ResourceLocation texture = new ResourceLocation(path + ".png");
            if (!index.exists(texture)) return null;

            ResourceLocation file = new ResourceLocation(path + ".properties");
            Properties properties = new Properties();
            if (index.exists(file)) {
                try {
                    properties = index.readProperties(file);
                } catch (IOException | IllegalArgumentException e) {
                    LOGGER.warn("Cannot read {} in {}; using line defaults", file, index.packName(file), e);
                }
            }
            return new Line(texture, LegacyLineStyle.read(properties, width, a, b, segments,
                invalid -> LOGGER.warn("Invalid line property {} in {} from {}; using the default", invalid, file, index.packName(file))));
        }
    }
}

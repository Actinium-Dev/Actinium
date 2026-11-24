package com.dhj.actinium.resourcepack.entity;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Draws the legacy Random Mobs overlay sheets at the vanilla attachment transforms. */
public final class LegacyEntityOverlays {
    private static final ResourceLocation MOOSHROOM = new ResourceLocation("mcpatcher/mob/cow/mooshroom_overlay.png");
    private static final ResourceLocation SNOWMAN = new ResourceLocation("mcpatcher/mob/snowman_overlay.png");

    private LegacyEntityOverlays() {
    }

    public static boolean drawMushroom(int index) {
        return draw(MOOSHROOM, buffer -> {
            double u = index / 3.0;
            double u1 = (index + 1) / 3.0;
            float lo = 0.05f;
            float hi = 0.95f;
            quad(buffer, lo, 1, lo, lo, 0, lo, hi, 0, hi, hi, 1, hi,
                    u, 0, u1, 1, -0.7071f, 0, 0.7071f);
            quad(buffer, lo, 1, hi, lo, 0, hi, hi, 0, lo, hi, 1, lo,
                    u, 0, u1, 1, 0.7071f, 0, 0.7071f);
        }, true);
    }

    public static boolean drawSnowmanHead() {
        return draw(SNOWMAN, buffer -> {
            float a = -.5f;
            float b = .5f;
            cubeFace(buffer, 0, b, a, a, b, a, b, a, a, b, a, a, a, 0, -1, 0);
            cubeFace(buffer, 1, a, b, a, a, b, b, b, b, b, b, b, a, 0, 1, 0);
            cubeFace(buffer, 2, a, b, b, a, b, a, a, a, a, a, a, b, -1, 0, 0);
            cubeFace(buffer, 3, a, b, a, b, b, a, b, a, a, a, a, a, 0, 0, -1);
            cubeFace(buffer, 4, b, b, a, b, b, b, b, a, b, b, a, a, 1, 0, 0);
            cubeFace(buffer, 5, b, b, b, a, b, b, a, a, b, b, a, b, 0, 0, 1);
        }, false);
    }

    private static boolean draw(ResourceLocation texture, Geometry geometry, boolean doubleSided) {
        if (!ResourcePackEntities.hasOverlay(texture)) {
            return false;
        }
        GLStateManager.glPushAttrib(GL11.GL_TEXTURE_BIT | GL11.GL_ENABLE_BIT);
        try {
            if (doubleSided) {
                GLStateManager.glDisable(GL11.GL_CULL_FACE);
            }
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_NORMAL);
            geometry.write(buffer);
            tessellator.draw();
        } finally {
            GLStateManager.glPopAttrib();
        }
        return true;
    }

    private static void cubeFace(BufferBuilder buffer, int tile, float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz) {
        double u = (tile % 3) / 3.0;
        double v = (tile / 3) * .5;
        buffer.pos(ax, ay, az).tex(u, v).normal(nx, ny, nz).endVertex();
        buffer.pos(bx, by, bz).tex(u + 1.0 / 3.0, v).normal(nx, ny, nz).endVertex();
        buffer.pos(cx, cy, cz).tex(u + 1.0 / 3.0, v + .5).normal(nx, ny, nz).endVertex();
        buffer.pos(dx, dy, dz).tex(u, v + .5).normal(nx, ny, nz).endVertex();
    }

    private static void quad(BufferBuilder buffer, float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz, double u, double v, double u1, double v1,
            float nx, float ny, float nz) {
        buffer.pos(ax, ay, az).tex(u, v).normal(nx, ny, nz).endVertex();
        buffer.pos(bx, by, bz).tex(u, v1).normal(nx, ny, nz).endVertex();
        buffer.pos(cx, cy, cz).tex(u1, v1).normal(nx, ny, nz).endVertex();
        buffer.pos(dx, dy, dz).tex(u1, v).normal(nx, ny, nz).endVertex();
    }

    @FunctionalInterface
    private interface Geometry {
        void write(BufferBuilder buffer);
    }
}

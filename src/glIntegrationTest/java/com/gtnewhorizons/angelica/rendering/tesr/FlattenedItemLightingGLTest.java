package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.states.LightModelState;
import com.gtnewhorizons.angelica.glsm.states.LightState;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlattenedItemLightingGLTest extends GLStateManagerIntegrationFixture {
    @Test
    void detectsFlatteningAfterRotationWithoutIncludingOrdinarySmallItems() {
        Matrix4f rotation = new Matrix4f().rotateY(0.7f).rotateX(0.4f);
        assertTrue(FlattenedItemLighting.isFlattened(new Matrix4f(rotation).scale(0.0625f, -0.0625f, 0.00001f)));
        assertFalse(FlattenedItemLighting.isFlattened(new Matrix4f(rotation).scale(0.0625f, -0.0625f, 0.0625f)));
    }

    @Test
    void rotatedDrawerIconKeepsTheFaceLightingOfItsUnflattenedModel() {
        VertexFormat format = DefaultVertexFormats.ITEM;
        ByteBuffer unflattened = cubeFaceVertices(format);
        ByteBuffer flattened = cubeFaceVertices(format);
        LightState light = new LightState(GL11.GL_LIGHT0);
        light.position.set(-0.1191f, 0.7852f, 0.6077f, 0.0f);
        light.diffuse.set(0.6f, 0.6f, 0.6f, 1.0f);
        LightModelState model = new LightModelState();
        model.ambient.set(0.4f, 0.4f, 0.4f, 1.0f);
        BooleanStateStack[] enabled = {new BooleanStateStack(GL11.GL_LIGHT0, true), new BooleanStateStack(GL11.GL_LIGHT1, false)};
        LightState[] lights = {light, new LightState(GL11.GL_LIGHT1)};
        Matrix4f view = new Matrix4f().rotateX(0.2f).rotateY(1.1f);
        Matrix4f item = new Matrix4f().rotateX(0.52f).rotateY(3.93f);

        FlattenedItemLighting.shade(unflattened, 0, 6, format,
            new Matrix4f(view).scale(0.0625f, -0.0625f, 0.0625f).mul(item), model, enabled, lights);
        FlattenedItemLighting.shade(flattened, 0, 6, format,
            new Matrix4f(view).scale(0.0625f, -0.0625f, 0.00001f).mul(item), model, enabled, lights);

        assertArrayEquals(unflattened.array(), flattened.array());
        assertTrue(Byte.toUnsignedInt(flattened.get(format.getColorOffset()))
            != Byte.toUnsignedInt(flattened.get(2 * format.getSize() + format.getColorOffset())));
    }

    private static ByteBuffer cubeFaceVertices(VertexFormat format) {
        ByteBuffer vertices = ByteBuffer.allocate(format.getSize() * 6);
        for (int i = 0; i < 6; i++) {
            int offset = i * format.getSize();
            int color = offset + format.getColorOffset();
            vertices.put(color, (byte) 140).put(color + 1, (byte) 70).put(color + 2, (byte) 30)
                .put(color + 3, (byte) 120);
            vertices.put(offset + format.getNormalOffset() + i / 2, (byte) (i % 2 == 0 ? 127 : -127));
        }
        return vertices;
    }

    @Test
    void flattenedOpposingFacesKeepDirectionalContrastAndVertexTint() {
        VertexFormat format = DefaultVertexFormats.ITEM;
        ByteBuffer vertices = ByteBuffer.allocate(format.getSize() * 3);
        for (int i = 0; i < 3; i++) {
            int offset = i * format.getSize() + format.getColorOffset();
            vertices.put(offset, (byte) 140).put(offset + 1, (byte) 70).put(offset + 2, (byte) 30)
                .put(offset + 3, (byte) 120);
        }
        vertices.put(format.getSize() + format.getNormalOffset() + 2, (byte) 127);
        vertices.put(2 * format.getSize() + format.getNormalOffset() + 2, (byte) -127);
        LightState frontLight = new LightState(GL11.GL_LIGHT0);
        frontLight.position.set(0, 0, 1, 0);
        frontLight.diffuse.set(0.6f, 0.6f, 0.6f, 1.0f);
        LightState reverseLight = new LightState(GL11.GL_LIGHT1);
        reverseLight.position.set(0, 0, -1, 0);
        reverseLight.diffuse.set(1, 1, 1, 1);

        FlattenedItemLighting.shade(vertices, 1, 3, format,
            new Matrix4f().scaling(0.0625f, -0.0625f, 0.00001f), new LightModelState(),
            new BooleanStateStack[] { new BooleanStateStack(GL11.GL_LIGHT0, true), new BooleanStateStack(GL11.GL_LIGHT1, false) },
            new LightState[] { frontLight, reverseLight });

        int lit = format.getSize() + format.getColorOffset();
        int shaded = 2 * format.getSize() + format.getColorOffset();
        assertTrue(Byte.toUnsignedInt(vertices.get(lit)) > Byte.toUnsignedInt(vertices.get(shaded)));
        assertEquals(Byte.toUnsignedInt(vertices.get(lit)), 2 * Byte.toUnsignedInt(vertices.get(lit + 1)));
        assertEquals(120, Byte.toUnsignedInt(vertices.get(lit + 3)));
        assertEquals(120, Byte.toUnsignedInt(vertices.get(shaded + 3)));
        assertEquals(140, Byte.toUnsignedInt(vertices.get(format.getColorOffset())));
    }
}

package com.dhj.actinium.render.frustum;

import dhj.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClippingHelperStateTest {
    @Test
    void cameraRelativeViewportMatchesVanillaAcrossCameraPoses() {
        ClippingHelperState state = new ClippingHelperState();
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70), 1, 0.05f, 128);
        Vector3d feet = new Vector3d(10, 64, 20);
        for (float distance : new float[] {0.05f, 4, 12}) {
            for (float yaw : new float[] {0, 37, 180, 271}) {
                for (float pitch : new float[] {-65, 0, 48}) {
                    Matrix4f raw = new Matrix4f().translate(0, 0, -distance)
                        .rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw))
                        .translate(0, -1.62f, 0);
                    state.update(projection.get(new float[16]), raw.get(new float[16]));
                    Viewport viewport = state.createViewport(feet.x, feet.y, feet.z);
                    FrustumIntersection reference = new FrustumIntersection(new Matrix4f(projection).mul(raw));
                    Matrix4f inverse = new Matrix4f(raw).invert();
                    for (float depth : new float[] {1, 20, 125, 140}) {
                        for (float side : new float[] {-1, -0.3f, 0, 0.3f, 1}) {
                            Vector3f relative = inverse.transformPosition(new Vector3f(depth * side, 0, -depth));
                            double x = feet.x + relative.x;
                            double y = feet.y + relative.y;
                            double z = feet.z + relative.z;
                            boolean expected = reference.testAab(relative.x - 0.02f, relative.y - 0.02f,
                                relative.z - 0.02f, relative.x + 0.02f, relative.y + 0.02f, relative.z + 0.02f);
                            String pose = "distance=" + distance + ", yaw=" + yaw + ", pitch=" + pitch
                                + ", depth=" + depth + ", side=" + side;
                            assertEquals(expected, viewport.isBoxVisible(x - 0.02, y - 0.02, z - 0.02,
                                x + 0.02, y + 0.02, z + 0.02), pose);
                            assertEquals(expected, ClippingHelperState.isBoxInFrustum(state.getVanillaFrustum(),
                                feet.x, feet.y, feet.z, x - 0.02, y - 0.02, z - 0.02,
                                x + 0.02, y + 0.02, z + 0.02), pose);
                        }
                    }
                }
            }
        }
    }

    @Test
    void thirdPersonFarPlaneAppliesCameraRetreatOnceAndRefreshesWithMatrices() {
        ClippingHelperState state = new ClippingHelperState();
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70), 1, 0.05f, 128);
        Matrix4f raw = new Matrix4f().translation(0, 0, -4).translate(0, -1.62f, 0);
        state.update(projection, raw);
        Viewport viewport = state.createViewport(10, 64, 20);
        assertTrue(viewport.isBoxVisible(9.95, 65.57, -102.05, 10.05, 65.67, -101.95));
        assertFalse(viewport.isBoxVisible(9.95, 65.57, -110.05, 10.05, 65.67, -109.95));

        state.update(projection, raw.translation(0, -1.62f, 0));
        Viewport firstPerson = state.createViewport(10, 64, 20);
        assertEquals(20, firstPerson.getTransform().z, 0.0001);
        assertEquals(65.62, firstPerson.getTransform().y, 0.0001);
        assertTrue(firstPerson.isBoxVisible(9.95, 65.57, -106.05, 10.05, 65.67, -105.95));
    }
}

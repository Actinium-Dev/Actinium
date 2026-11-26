package com.gtnewhorizons.angelica.compat.mojang;

import net.coderbot.iris.gl.MatrixStack;
import net.coderbot.iris.shadow.ShadowMatrices;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraCoordinatesTest {
    @Test
    void firstAndThirdPersonWorldReconstructionMatchesShadowDrawing() {
        for (float distance : new float[] {0, 4, 12}) {
            for (float yaw : new float[] {0, 0.7f, 3.14f}) {
                Matrix4f raw = new Matrix4f().translation(0.02f, 0.03f, -distance)
                    .rotateX(0.3f).rotateY(yaw).scale(1.01f, 0.99f, 1)
                    .translate(0, -1.62f, 0);
                CameraCoordinates camera = new CameraCoordinates();
                camera.update(raw, 10, 64, 20, 1.62);
                Vector3d world = new Vector3d(14, 67, 8);
                Vector3f vanillaView = raw.transformPosition(new Vector3f(4, 3, -12));
                Vector3f relative = new Matrix4f(camera.getModelView()).invert().transformPosition(vanillaView);
                Vector3d reconstructed = new Vector3d(relative).add(camera.getPosition());
                assertVector(world, reconstructed, 0.00001);

                MatrixStack shadow = new MatrixStack();
                Vector3d origin = camera.getPosition();
                ShadowMatrices.createModelViewMatrix(shadow, 0.37f, 2, 15, origin.x, origin.y, origin.z);
                Vector3f direct = shadow.peek().getModel().transformPosition(new Vector3f((float) (world.x - origin.x), (float) (world.y - origin.y), (float) (world.z - origin.z)));
                Vector3f sampled = shadow.peek().getModel().transformPosition(relative);
                assertEquals(direct.x, sampled.x, 0.00002);
                assertEquals(direct.y, sampled.y, 0.00002);
                assertEquals(direct.z, sampled.z, 0.00002);
            }
        }
    }

    @Test
    void inverseViewAlreadyIncludesEyeHeight() {
        CameraCoordinates camera = new CameraCoordinates();
        camera.update(new Matrix4f().translation(0, -1.62f, -4), 10, 64, 20, 1.62f);
        assertVector(new Vector3d(10, 65.62, 24), camera.getPosition(), 0.00001);
        assertVector(new Vector3d(10, 65.62, 20), camera.getEyePosition(), 0.00001);
        assertEquals(0, camera.getOffset().y, 0.00001);
        assertEquals(4, camera.getOffset().z, 0.00001);
    }

    private static void assertVector(Vector3d expected, Vector3d actual, double delta) {
        assertEquals(expected.x, actual.x, delta);
        assertEquals(expected.y, actual.y, delta);
        assertEquals(expected.z, actual.z, delta);
    }
}

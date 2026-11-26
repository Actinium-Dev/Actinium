package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.compat.mojang.Camera;
import com.gtnewhorizons.angelica.rendering.RenderingState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatrixUniformsTest {
    @Test
    void gbufferSupplierRetainsCapturedMainViewDuringShadowStateChanges() {
        Matrix4f savedRaw = new Matrix4f(RenderingState.INSTANCE.getModelViewMatrix());
        Matrix4f raw = new Matrix4f().translation(0, 0, -4).rotateX(0.3f).rotateY(0.6f).translate(0, -1.62f, 0);
        try {
            Camera.INSTANCE.getCoordinates().update(raw, 10, 64, 20, 1.62);
            Matrix4f expectedMainView = new Matrix4f(raw).m30(0).m31(0).m32(0);
            assertEquals(expectedMainView, MatrixUniforms.getGbufferModelView());
            RenderingState.INSTANCE.setModelViewMatrix(new Matrix4f().rotationX(1.3f));
            assertEquals(expectedMainView, MatrixUniforms.getGbufferModelView());
            Vector3f worldRelative = new Vector3f(5, 2, -8);
            Vector3f view = MatrixUniforms.getGbufferModelView().transformPosition(new Vector3f(worldRelative));
            Vector3f recovered = new Matrix4f(MatrixUniforms.getGbufferModelView()).invert().transformPosition(view);
            assertEquals(worldRelative.x, recovered.x, 0.00001);
            assertEquals(worldRelative.y, recovered.y, 0.00001);
            assertEquals(worldRelative.z, recovered.z, 0.00001);
            assertEquals(0f, MatrixUniforms.getGbufferModelView().m30());
            assertEquals(0f, MatrixUniforms.getGbufferModelView().m31());
            assertEquals(0f, MatrixUniforms.getGbufferModelView().m32());
        } finally {
            RenderingState.INSTANCE.setModelViewMatrix(savedRaw);
            Camera.INSTANCE.getCoordinates().update(new Matrix4f(), 0, 0, 0, 0);
        }
    }

    @Test
    void previousMatrixAdvancesOnlyOncePerFrame() {
        SystemTimeUniforms.COUNTER.reset();
        try {
            final Matrix4f first = new Matrix4f().translation(1.0f, 2.0f, 3.0f);
            final Matrix4f second = new Matrix4f().translation(4.0f, 5.0f, 6.0f);
            final AtomicReference<Matrix4f> current = new AtomicReference<>(first);
            final MatrixUniforms.Previous previous = new MatrixUniforms.Previous(current::get);

            assertEquals(new Matrix4f(), previous.get());
            assertEquals(new Matrix4f(), previous.get());

            current.set(second);
            assertEquals(new Matrix4f(), previous.get());

            SystemTimeUniforms.COUNTER.beginFrame();
            assertEquals(first, previous.get());
            assertEquals(first, previous.get());

            SystemTimeUniforms.COUNTER.beginFrame();
            assertEquals(second, previous.get());
        } finally {
            SystemTimeUniforms.COUNTER.reset();
        }
    }
}

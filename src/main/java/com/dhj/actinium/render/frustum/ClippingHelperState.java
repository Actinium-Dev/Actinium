package com.dhj.actinium.render.frustum;

import dhj.embeddedt.embeddium.impl.render.viewport.Viewport;
import dhj.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** Keeps vanilla and camera-relative clipping in their respective coordinate systems. */
public final class ClippingHelperState {
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f modelView = new Matrix4f();
    private final Matrix4f inverseModelView = new Matrix4f();
    private final Matrix4f combined = new Matrix4f();
    private final Matrix4f vpMatrix = new Matrix4f();
    private final FrustumIntersection vanillaFrustum = new FrustumIntersection();
    private final FrustumIntersection cameraFrustum = new FrustumIntersection();
    private final Frustum cameraBoxTest = cameraFrustum::testAab;
    private final Vector3f cameraOffset = new Vector3f();

    /** Captures the matrices belonging to this clipping helper without allocating frame scratch objects. */
    public void update(float[] projection, float[] modelView) {
        update(this.projection.set(projection), this.modelView.set(modelView));
    }

    /** Rebuilds both frusta and the camera offset once per matrix capture. */
    public void update(Matrix4fc projection, Matrix4fc modelView) {
        vanillaFrustum.set(combined.set(projection).mul(modelView), true);
        inverseModelView.set(modelView).invert().transformPosition(cameraOffset.set(0, 0, 0));
        combined.set(modelView).m30(0).m31(0).m32(0);
        vpMatrix.set(projection).mul(combined);
        cameraFrustum.set(vpMatrix, true);
    }

    /** Supplies clipping planes for world boxes expressed relative to the entity's feet. */
    public FrustumIntersection getVanillaFrustum() {
        return vanillaFrustum;
    }

    /** Supplies camera-relative projection for raster occlusion. */
    public Matrix4f getVpMatrix() {
        return vpMatrix;
    }

    /** Creates a viewport whose box coordinates and clipping planes share the actual camera origin. */
    public Viewport createViewport(double feetX, double feetY, double feetZ) {
        return new Viewport(cameraBoxTest,
            new Vector3d(feetX + cameraOffset.x, feetY + cameraOffset.y, feetZ + cameraOffset.z), vpMatrix);
    }

    /** Tests vanilla boxes while preserving its convention for unbounded renderers. */
    public static boolean isBoxInFrustum(FrustumIntersection frustum, double x, double y, double z,
                                         double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ) {
        if (Double.isInfinite(minX) || Double.isInfinite(minY) || Double.isInfinite(minZ)
            || Double.isInfinite(maxX) || Double.isInfinite(maxY) || Double.isInfinite(maxZ)) {
            return true;
        }
        return frustum.testAab((float) (minX - x), (float) (minY - y), (float) (minZ - z),
            (float) (maxX - x), (float) (maxY - y), (float) (maxZ - z));
    }
}

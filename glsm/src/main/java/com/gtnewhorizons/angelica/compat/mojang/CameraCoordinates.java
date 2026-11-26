package com.gtnewhorizons.angelica.compat.mojang;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Captures one view's world origin and matching camera-relative matrix without per-draw allocation. */
public final class CameraCoordinates {
    private final Matrix4f inverse = new Matrix4f();
    private final Matrix4f modelView = new Matrix4f();
    private final Vector3d position = new Vector3d();
    private final Vector3d eyePosition = new Vector3d();
    private final Vector3f offset = new Vector3f();
    private final Vector3f forwardDirection = new Vector3f(0, 0, -1);

    /** Converts vanilla's feet-relative view to coordinates relative to the physical camera. */
    public void update(Matrix4fc raw, double feetX, double feetY, double feetZ, double eyeHeight) {
        inverse.set(raw).invert();
        inverse.transformDirection(0, 0, -1, forwardDirection);
        position.set(inverse.m30(), inverse.m31(), inverse.m32()).add(feetX, feetY, feetZ);
        eyePosition.set(feetX, feetY + eyeHeight, feetZ);
        offset.set(position.x - eyePosition.x, position.y - eyePosition.y, position.z - eyePosition.z);
        modelView.set(raw).m30(0).m31(0).m32(0);
    }

    /** Returns the world-space camera origin, including third-person displacement and view effects. */
    public Vector3d getPosition() {
        return position;
    }

    /** Returns the interpolated entity eye position used for player-relative uniforms. */
    public Vector3d getEyePosition() {
        return eyePosition;
    }

    /** Returns the displacement from the entity eyes to the physical camera. */
    public Vector3f getOffset() {
        return offset;
    }

    /** Returns the world-space direction of the rendered view, including front-facing third person. */
    public Vector3fc getForwardDirection() {
        return forwardDirection;
    }

    /** Returns the captured main-view matrix for vertices relative to {@link #getPosition()}. */
    public Matrix4fc getModelView() {
        return modelView;
    }
}

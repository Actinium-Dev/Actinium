package com.gtnewhorizons.angelica.glsm.states;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.stacks.CowDepths;
import com.gtnewhorizons.angelica.glsm.stacks.CowStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.StackIdAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;

/** Eye-space clip equations saved by the legacy GL_TRANSFORM_BIT attribute group. */
public class ClipPlaneState implements CowStateStack<ClipPlaneState> {
    private final float[] eyePlanes = new float[GLStateManager.MAX_CLIP_PLANES * 4];
    private final float[][] savedPlanes = new float[GLStateManager.STATE_SLOTS][];
    private final CowDepths cow = new CowDepths();
    private final Vector4f tempPlane = new Vector4f();
    private final Matrix4f tempInverse = new Matrix4f();

    public ClipPlaneState() {
        cow.id = StackIdAllocator.nextId();
    }

    public void setPlane(int index, double a, double b, double c, double d, Matrix4f modelView) {
        modelView.invert(tempInverse);
        tempPlane.set((float) a, (float) b, (float) c, (float) d);
        tempInverse.transformTranspose(tempPlane);
        int offset = index * 4;
        eyePlanes[offset] = tempPlane.x;
        eyePlanes[offset + 1] = tempPlane.y;
        eyePlanes[offset + 2] = tempPlane.z;
        eyePlanes[offset + 3] = tempPlane.w;
    }

    public void putEyePlane(int index, FloatBuffer buf) {
        buf.put(eyePlanes, index * 4, 4);
    }

    /** An enabled plane contributes its signed distance. A zero equation keeps every fragment. */
    public void putEffectivePlanes(int enabledMask, FloatBuffer buf) {
        for (int i = 0; i < GLStateManager.MAX_CLIP_PLANES; i++) {
            if ((enabledMask & (1 << i)) != 0) {
                putEyePlane(i, buf);
            } else {
                buf.put(0f).put(0f).put(0f).put(0f);
            }
        }
    }

    public void getEyePlane(int index, DoubleBuffer buf) {
        final int offset = index * 4;
        final int pos = buf.position();
        for (int i = 0; i < 4; i++) {
            buf.put(pos + i, eyePlanes[offset + i]);
        }
    }

    @Override
    public CowDepths cowDepths() {
        return cow;
    }

    @Override
    public boolean slotChanged(int slot) {
        return !Arrays.equals(eyePlanes, savedPlanes[slot]);
    }

    @Override
    public void captureSlot(int slot) {
        if (savedPlanes[slot] == null) savedPlanes[slot] = new float[eyePlanes.length];
        System.arraycopy(eyePlanes, 0, savedPlanes[slot], 0, eyePlanes.length);
    }

    @Override
    public void restoreSlot(int slot) {
        System.arraycopy(savedPlanes[slot], 0, eyePlanes, 0, eyePlanes.length);
        cow.owner.clipPlaneGeneration++;
    }
}

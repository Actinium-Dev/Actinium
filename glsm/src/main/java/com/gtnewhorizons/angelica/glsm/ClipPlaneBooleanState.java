package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import org.lwjgl.opengl.GL11;

public final class ClipPlaneBooleanState extends BooleanStateStack {
    private final GLContextState owner;
    private final int index;

    ClipPlaneBooleanState(GLContextState owner, int index, int glCap) {
        super(glCap);
        this.owner = owner;
        this.index = index;
    }

    static boolean isClipPlane(int cap) {
        return cap >= GL11.GL_CLIP_PLANE0 && cap < GL11.GL_CLIP_PLANE0 + GLStateManager.MAX_CLIP_PLANES;
    }

    @Override
    protected boolean tracksStateWhenCacheBypassed() {
        // Both shader paths use the mask to select the plane equations to upload.
        return true;
    }

    @Override
    protected boolean forwardsToBackend() {
        return !RenderSystem.isClipDistanceEmulated();
    }

    @Override
    protected void onEnabledChanged() {
        final int before = owner.clipPlaneEnabledMask;
        if (isEnabled()) {
            owner.clipPlaneEnabledMask |= 1 << index;
        } else {
            owner.clipPlaneEnabledMask &= ~(1 << index);
        }
        if (before != owner.clipPlaneEnabledMask) {
            owner.clipPlaneGeneration++;
            if ((before == 0 || owner.clipPlaneEnabledMask == 0) && RenderSystem.isClipDistanceEmulated()
                && GLSMHooks.CLIP_PLANES_IN_USE.hasListeners()) {
                GLSMHooks.CLIP_PLANES_IN_USE.post(GLSMHooks.clipPlanesInUseEvent);
            }
        }
    }
}

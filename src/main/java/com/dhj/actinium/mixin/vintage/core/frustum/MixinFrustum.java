package com.dhj.actinium.mixin.vintage.core.frustum;

import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.Frustum;
import dhj.embeddedt.embeddium.impl.render.viewport.Viewport;
import dhj.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.spongepowered.asm.mixin.*;
import com.dhj.actinium.render.frustum.IClippingHelper;
import com.dhj.actinium.render.frustum.ClippingHelperState;

@Mixin(Frustum.class)
public class MixinFrustum implements ViewportProvider {
    @Shadow
    @Final
    private ClippingHelper clippingHelper;

    @Shadow
    private double x, y, z;

    @Overwrite
    public boolean isBoxInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return ClippingHelperState.isBoxInFrustum(((IClippingHelper) clippingHelper).celeritas$getJomlFrustum(),
            this.x, this.y, this.z, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public Viewport sodium$createViewport() {
        return ((IClippingHelper) clippingHelper).celeritas$createViewport(this.x, this.y, this.z);
    }
}

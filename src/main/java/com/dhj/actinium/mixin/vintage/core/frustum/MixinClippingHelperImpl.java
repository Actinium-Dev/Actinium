package com.dhj.actinium.mixin.vintage.core.frustum;

import net.minecraft.client.renderer.culling.ClippingHelper;
import com.dhj.actinium.render.frustum.ClippingHelperState;
import dhj.embeddedt.embeddium.impl.render.viewport.Viewport;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.dhj.actinium.render.frustum.IClippingHelper;

@Mixin(ClippingHelperImpl.class)
public abstract class MixinClippingHelperImpl extends ClippingHelper implements IClippingHelper {
    @Unique
    private final ClippingHelperState celeritas$state = new ClippingHelperState();

    @Inject(method = "init", at = @At("RETURN"))
    private void updateJoml(CallbackInfo ci) {
        this.celeritas$state.update(projectionMatrix, modelviewMatrix);
    }

    @Override
    public FrustumIntersection celeritas$getJomlFrustum() {
        return this.celeritas$state.getVanillaFrustum();
    }

    @Override
    public Matrix4f celeritas$getVpMatrix() {
        return this.celeritas$state.getVpMatrix();
    }

    @Override
    public Viewport celeritas$createViewport(double feetX, double feetY, double feetZ) {
        return this.celeritas$state.createViewport(feetX, feetY, feetZ);
    }
}

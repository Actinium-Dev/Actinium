package com.dhj.actinium.test.shadow.fixture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Driver calls made by Forge's drawBatch have no device in the dispatcher process. */
@Mixin(GlStateManager.class)
abstract class ShadowFastTesrDriver {
    @Inject(method = {"blendFunc(II)V", "enableBlend", "disableCull", "shadeModel"}, at = @At("HEAD"), cancellable = true)
    private static void omitDriverCall(CallbackInfo ci) {
        ci.cancel();
    }
}

@Mixin(RenderHelper.class)
abstract class ShadowFastTesrLighting {
    @Inject(method = {"enableStandardItemLighting", "disableStandardItemLighting"}, at = @At("HEAD"), cancellable = true)
    private static void omitFixedFunctionLighting(CallbackInfo ci) {
        ci.cancel();
    }
}

@Mixin(Minecraft.class)
abstract class ShadowFastTesrAmbientOcclusion {
    @Inject(method = "isAmbientOcclusionEnabled", at = @At("HEAD"), cancellable = true)
    private static void useFlatShading(CallbackInfoReturnable<Boolean> ci) {
        ci.setReturnValue(false);
    }
}

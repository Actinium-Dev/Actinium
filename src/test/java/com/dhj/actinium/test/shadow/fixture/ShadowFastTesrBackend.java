package com.dhj.actinium.test.shadow.fixture;

import com.gtnewhorizons.angelica.lwjgl3.Lwjgl3GLRenderBackend;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies the device limit needed to construct GLSM's real CPU state stacks. */
@Mixin(value = Lwjgl3GLRenderBackend.class, remap = false)
abstract class ShadowFastTesrBackend {
    @Inject(method = "getInteger(I)I", at = @At("HEAD"), cancellable = true)
    private void textureUnits(int name, CallbackInfoReturnable<Integer> ci) {
        if (name != GL20.GL_MAX_TEXTURE_IMAGE_UNITS) {
            throw new AssertionError("Unexpected driver query: " + name);
        }
        ci.setReturnValue(8);
    }

    @Inject(method = "bindVertexArray", at = @At("HEAD"), cancellable = true)
    private void restoreCpuVertexArrays(int array, CallbackInfo ci) {
        if (array != 0) throw new AssertionError("Unexpected device VAO: " + array);
        ci.cancel();
    }
}

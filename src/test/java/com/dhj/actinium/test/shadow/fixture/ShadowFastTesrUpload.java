package com.dhj.actinium.test.shadow.fixture;

import com.dhj.actinium.test.shadow.ShadowFastTesrProbe;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the finished CPU buffer in place of the driver's vertex upload. */
@Mixin(WorldVertexBufferUploader.class)
abstract class ShadowFastTesrUpload {
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void recordUpload(BufferBuilder buffer, CallbackInfo ci) {
        ShadowFastTesrProbe.upload(buffer);
        buffer.reset();
        ci.cancel();
    }
}

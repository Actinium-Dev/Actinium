package com.dhj.actinium.mixin.mod.mmce;

import com.dhj.actinium.compat.mmce.MmceControllerRenderCompat;
import github.kasuminova.mmce.client.renderer.ControllerModelRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.BiConsumer;

// Older Modular Machinery versions share the mod ID without providing this renderer.
@Pseudo
@Mixin(value = ControllerModelRenderManager.class, remap = false)
public abstract class MixinControllerModelRenderManager {
    @Shadow public abstract void draw();
    @Shadow public abstract void checkControllerState();

    @Unique private boolean actinium$cleanupAfterBloom;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void actinium$registerControllerBatch(CallbackInfo ci) {
        MmceControllerRenderCompat.register(this::draw, this::checkControllerState, () -> this.actinium$cleanupAfterBloom);
    }

    // The public build iterates here and defers controller cleanup to its native bloom callback.
    @Inject(
        method = "draw()V",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V", remap = false),
        require = 0,
        expect = 0,
        allow = 1
    )
    private void actinium$markBloomCleanupLayout(CallbackInfo ci) {
        this.actinium$cleanupAfterBloom = true;
    }

    // The public build iterates in draw(), while the pack build extracts the same loop into drawBuffers().
    @ModifyArg(
        method = "/^draw(Buffers)?$/ desc=/^\\(\\)V$/",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V", remap = false),
        index = 0,
        require = 1,
        allow = 1
    )
    private BiConsumer<Enum<?>, Map<?, ?>> actinium$setTranslucentGroupState(BiConsumer<Enum<?>, Map<?, ?>> drawGroup) {
        return MmceControllerRenderCompat.withShaderTranslucency(drawGroup);
    }
}

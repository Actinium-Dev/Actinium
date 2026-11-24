package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.material.MapColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.MapItemRenderer$Instance")
abstract class MixinMapTexture {
    @Unique private Object actinium$colorRevision;

    @Shadow protected abstract void updateMapTexture();

    @Inject(method = "render", at = @At("HEAD"))
    private void actinium$refreshMap(boolean noOverlayRendering, CallbackInfo ci) {
        if (actinium$colorRevision != ResourcePackColors.revision()) updateMapTexture();
    }

    @Inject(method = "updateMapTexture", at = @At("HEAD"))
    private void actinium$rememberColors(CallbackInfo ci) {
        actinium$colorRevision = ResourcePackColors.revision();
    }

    @WrapOperation(method = "updateMapTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/material/MapColor;getMapColor(I)I"))
    private int actinium$customMapColor(MapColor color, int shade, Operation<Integer> original) {
        return ResourcePackColors.map(color.colorIndex, shade, original.call(color, shade));
    }
}

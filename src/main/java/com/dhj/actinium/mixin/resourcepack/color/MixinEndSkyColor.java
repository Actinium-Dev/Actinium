package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderGlobal.class)
abstract class MixinEndSkyColor {
    @WrapOperation(method = "renderSkyEnd()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BufferBuilder;color(IIII)Lnet/minecraft/client/renderer/BufferBuilder;"))
    private BufferBuilder actinium$colorEndSky(BufferBuilder buffer, int red, int green, int blue, int alpha,
                                               Operation<BufferBuilder> original) {
        int rgb = ResourcePackColors.color("sky.end", red << 16 | green << 8 | blue);
        return original.call(buffer, rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha);
    }
}
